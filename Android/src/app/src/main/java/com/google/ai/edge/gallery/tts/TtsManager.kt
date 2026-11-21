package com.google.ai.edge.gallery.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.ai.edge.gallery.data.characters.SpeechStreamer
import com.google.ai.edge.gallery.data.characters.SpeechText
import com.google.ai.edge.gallery.data.characters.TtsSettingsStore
import com.google.ai.edge.gallery.data.characters.WavReader
import com.jegly.offlineLLM.smollm.SmolLM
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lee en voz alta los mensajes de roleplay con Pocket TTS. Un solo hilo de trabajo: genera frase
 * a frase y reproduce el audio mientras se sigue generando. Un nuevo mensaje corta el anterior.
 */
object TtsManager {
  private const val TAG = "AGLTts"
  private val executor = Executors.newSingleThreadExecutor()
  private val generation = AtomicInteger(0)
  private val main = Handler(Looper.getMainLooper())

  private val _speaking = MutableStateFlow(false)
  val speaking: StateFlow<Boolean> = _speaking

  /** Contexto de la app, para la lectura automática desde el ViewModel (que no tiene Context). */
  @Volatile var appContext: Context? = null

  /** Lee la respuesta si la lectura automática está activada (llamado al terminar de generar). */
  fun autoSpeak(message: String) {
    val ctx = appContext ?: return
    if (TtsSettingsStore(ctx).autoRead()) speak(ctx, message)
  }

  /** Personaje del chat abierto (para elegir su voz). */
  @Volatile var currentCharacterId: String = ""

  @Volatile private var engine: SherpaPocketEngine? = null
  @Volatile private var engineKey: String = ""
  @Volatile private var track: AudioTrack? = null

  private const val IDLE_RELEASE_MS = 2L * 60 * 1000
  private val releaseTask = Runnable { release() }
  @Volatile private var focusRequest: AudioFocusRequest? = null

  private fun requestFocus(app: Context) {
    runCatching {
      val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          // Si entra una llamada u otra app con prioridad, la voz se calla.
          .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
          }
          .build()
      am.requestAudioFocus(req)
      focusRequest = req
    }
  }

  private fun abandonFocus() {
    val app = appContext ?: return
    runCatching {
      focusRequest?.let { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
      focusRequest = null
    }
  }

  private fun stopPlayback() {
    generation.incrementAndGet()
    runCatching { track?.pause(); track?.flush() }
    _speaking.value = false
    abandonFocus()
  }

  fun stop() {
    stopPlayback()
    // El motor ocupa RAM: se libera solo tras 2 min sin hablar.
    main.removeCallbacks(releaseTask)
    main.postDelayed(releaseTask, IDLE_RELEASE_MS)
  }

  /** Libera el motor (RAM) cuando no se necesita. */
  fun release() {
    main.removeCallbacks(releaseTask)
    stopPlayback()
    executor.execute {
      engine?.release()
      engine = null
      engineKey = ""
    }
  }

  fun isModelReady(context: Context): Boolean = PocketModelFiles.missing(TtsFiles.modelDir(context)).isEmpty()

  private fun toast(context: Context, msg: String) {
    main.post { Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show() }
  }

  private class Inputs(val voicePath: String, val files: PocketModelFiles)

  /** Voz concreta para una prueba (biblioteca de voces); solo vale durante la llamada a [speakAs]. */
  @Volatile private var voiceOverride: String? = null

  /** Lee [message] con la muestra [voicePath] en vez de la del personaje o la predeterminada. */
  fun speakAs(context: Context, message: String, voicePath: String) {
    voiceOverride = voicePath
    try {
      speak(context, message)
    } finally {
      voiceOverride = null
    }
  }

  /** Comprueba voz y modelo; si falta algo avisa con un mensaje y devuelve null. */
  private fun resolveInputs(app: Context, settings: TtsSettingsStore): Inputs? {
    val voicePath =
      voiceOverride?.takeIf { File(it).exists() }
        ?: settings.characterVoicePath(currentCharacterId)?.takeIf { File(it).exists() }
        ?: settings.defaultVoicePath()?.takeIf { File(it).exists() }
    if (voicePath == null) {
      toast(app, "Elige una muestra de voz (.wav) en Roleplay > Voz, o en el personaje.")
      return null
    }
    val files = PocketModelFiles.resolve(TtsFiles.modelDir(app))
    if (files == null) {
      toast(app, "Falta el modelo Pocket TTS: impórtalo en Roleplay > Voz.")
      return null
    }
    return Inputs(voicePath, files)
  }

  /** Fin de la cola de frases. */
  private const val END = "\u0000END"

  /** Lee [message] con la voz del personaje actual (o la predeterminada). */
  fun speak(context: Context, message: String) {
    val app = context.applicationContext
    val settings = TtsSettingsStore(app)
    val text = SpeechText.extract(message, settings.scope())
    val chunks = SpeechText.chunks(text)
    if (chunks.isEmpty()) {
      toast(app, "No hay texto para leer con este alcance (¿solo diálogo y no hay comillas?).")
      return
    }
    val inputs = resolveInputs(app, settings) ?: return
    val queue = LinkedBlockingQueue<String>()
    chunks.forEach { queue.put(it) }
    queue.put(END)
    launchJob(app, settings, inputs, queue)
  }

  // --- Voz en vivo: empieza a hablar con la primera frase completa mientras el modelo sigue escribiendo.
  private class Live(val queue: LinkedBlockingQueue<String>, val streamer: SpeechStreamer)
  @Volatile private var live: Live? = null

  /** Interruptor "hablar mientras se genera" (la lectura automática debe estar activa). */
  fun liveEnabled(): Boolean {
    val ctx = appContext ?: return false
    val st = TtsSettingsStore(ctx)
    return st.autoRead() && st.streamWhileGenerating()
  }

  /** Empieza una lectura en vivo; devuelve false si no está activada o falta voz/modelo. */
  fun liveBegin(): Boolean {
    live = null
    if (!liveEnabled()) return false
    val app = appContext ?: return false
    val settings = TtsSettingsStore(app)
    val inputs = resolveInputs(app, settings) ?: return false
    val l = Live(LinkedBlockingQueue(), SpeechStreamer(settings.scope()))
    live = l
    launchJob(app, settings, inputs, l.queue)
    return true
  }

  /** [fullText]: todo lo generado hasta ahora. */
  fun liveFeed(fullText: String) {
    val l = live ?: return
    l.streamer.push(fullText).forEach { l.queue.put(it) }
  }

  /** Termina la lectura en vivo con el texto final. */
  fun liveEnd(fullText: String) {
    val l = live ?: return
    live = null
    l.streamer.finish(fullText).forEach { l.queue.put(it) }
    l.queue.put(END)
  }

  /** Cancela la lectura en vivo (p. ej. el usuario detuvo la respuesta). */
  fun liveCancel() {
    val l = live ?: return
    live = null
    l.queue.put(END)
  }

  private fun launchJob(app: Context, settings: TtsSettingsStore, inputs: Inputs, queue: LinkedBlockingQueue<String>) {
    val voicePath = inputs.voicePath
    val files = inputs.files
    val myGen = generation.incrementAndGet()
    live?.takeIf { it.queue !== queue }?.let { live = null }
    main.removeCallbacks(releaseTask)
    appContext = app
    requestFocus(app)
    _speaking.value = true
    val speed = settings.speed()
    val steps = settings.numSteps()
    executor.execute {
      var out: AudioTrack? = null
      try {
        if (generation.get() != myGen) return@execute
        val wav = WavReader.parse(File(voicePath).readBytes()) ?: error("La muestra de voz no es un WAV válido")

        val key = files.lmMain.absolutePath
        val existing = engine
        val eng: SherpaPocketEngine =
          if (existing != null && engineKey == key) existing
          else {
            existing?.release()
            SherpaPocketEngine(files, SmolLM.performanceCoreCount()).also {
              val t0 = System.currentTimeMillis()
              it.load()
              Log.i(TAG, "Pocket TTS cargado en ${System.currentTimeMillis() - t0} ms (sr=${it.sampleRate})")
              engine = it
              engineKey = key
            }
          }

        val sr = eng.sampleRate
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, sr * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        out = t
        track = t
        t.play()

        var totalFrames = 0L
        var lastActivity = System.currentTimeMillis()
        while (generation.get() == myGen) {
          val chunk = queue.poll(250, TimeUnit.MILLISECONDS)
          if (chunk == null) {
            // El modelo de chat puede tardar en escribir la siguiente frase; no esperar para siempre.
            if (System.currentTimeMillis() - lastActivity > 120_000) break
            continue
          }
          if (chunk == END) break
          lastActivity = System.currentTimeMillis()
          eng.generate(chunk, wav.samples, wav.sampleRate, speed, steps) { samples ->
            if (generation.get() != myGen) return@generate false
            t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            totalFrames += samples.size
            true
          }
        }
        if (generation.get() == myGen) {
          t.stop() // deja que termine de sonar lo ya escrito
          val deadline = System.currentTimeMillis() + (totalFrames * 1000L / sr) + 3000
          while (generation.get() == myGen && t.playbackHeadPosition < totalFrames && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
          }
        }
      } catch (e: Throwable) {
        Log.e(TAG, "TTS failed", e)
        toast(app, "Voz: ${e.message ?: e.javaClass.simpleName}")
      } finally {
        runCatching { out?.release() }
        if (track === out) track = null
        if (generation.get() == myGen) {
          main.post {
            _speaking.value = false
            abandonFocus()
            main.removeCallbacks(releaseTask)
            main.postDelayed(releaseTask, IDLE_RELEASE_MS)
          }
        }
      }
    }
  }
}
