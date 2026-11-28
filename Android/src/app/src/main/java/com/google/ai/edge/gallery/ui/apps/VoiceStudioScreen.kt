package com.google.ai.edge.gallery.ui.apps

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.characters.TtsSettingsStore
import com.google.ai.edge.gallery.data.characters.WavReader
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.tts.VoiceCleaner
import com.google.ai.edge.gallery.tts.VoiceDenoiser
import com.google.ai.edge.gallery.tts.VoiceQuality
import com.google.ai.edge.gallery.tts.VoiceRecorder
import com.google.ai.edge.gallery.tts.VoiceStore
import com.google.ai.edge.gallery.tts.WavWriter
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.roleplay.TtsSettingsPanel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StudioSample(val bytes: ByteArray, val name: String, val seconds: Float)

private data class StudioResult(
  val wav: ByteArray,
  val report: VoiceCleaner.Report,
  val quality: VoiceQuality.Assessment,
  val denoised: Boolean,
  val extraNotes: List<String>,
)

/** Reproductor desechable para comparar la muestra original con la limpia. */
private class WavPlayer {
  private var mp: MediaPlayer? = null

  fun play(path: String, onDone: () -> Unit) {
    stop()
    val player = MediaPlayer()
    runCatching {
      player.setDataSource(path)
      player.setOnCompletionListener { onDone() }
      player.start()
      mp = player
    }.onFailure { player.release() }
  }

  fun stop() {
    runCatching { mp?.stop() }
    runCatching { mp?.release() }
    mp = null
  }
}

/**
 * Voice Studio: prepara una muestra de voz para Pocket TTS sin modelos nuevos en la generación.
 * Mide la calidad, permite reducir ruido con RNNoise solo en la muestra, compara A/B y guarda el
 * resultado en la biblioteca. Debajo quedan los ajustes habituales del motor.
 */
@Composable
fun VoiceStudioScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val store = remember { VoiceStore(context) }
  val settings = remember { TtsSettingsStore(context) }
  var text by remember { mutableStateOf("") }
  val speaking by TtsManager.speaking.collectAsState()

  var source by remember { mutableStateOf<StudioSample?>(null) }
  var result by remember { mutableStateOf<StudioResult?>(null) }
  var voiceName by remember { mutableStateOf("") }
  var useRnnoise by remember { mutableStateOf(true) }
  var useAsDefault by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var recording by remember { mutableStateOf(false) }
  var recSeconds by remember { mutableIntStateOf(0) }
  var playing by remember { mutableStateOf<String?>(null) }
  val stopFlag = remember { AtomicBoolean(false) }
  val player = remember { WavPlayer() }

  DisposableEffect(Unit) {
    // Leer siempre con la voz predeterminada, no con la del último personaje abierto.
    TtsManager.currentCharacterId = ""
    onDispose {
      stopFlag.set(true)
      player.stop()
    }
  }

  fun adopt(bytes: ByteArray, name: String) {
    val pcm = WavReader.parse(bytes)
    if (pcm == null) {
      Toast.makeText(context, "Ese archivo no es un WAV legible (PCM de 16/24/32 bits).", Toast.LENGTH_LONG).show()
      return
    }
    player.stop()
    playing = null
    source = StudioSample(bytes, name, pcm.seconds)
    result = null
    voiceName = name.substringBeforeLast('.').ifBlank { "Voz de estudio" }
  }

  val pickWav =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        val bytes =
          withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
          }
        val display =
          runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
              if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
            }
          }.getOrNull() ?: uri.lastPathSegment ?: "muestra.wav"
        if (bytes != null) adopt(bytes, display) else Toast.makeText(context, "No se pudo leer ese archivo.", Toast.LENGTH_SHORT).show()
      }
    }

  fun startRecording() {
    stopFlag.set(false)
    recording = true
    recSeconds = 0
    scope.launch {
      val wav = withContext(Dispatchers.IO) { VoiceRecorder.recordWav(stopFlag) { recSeconds = it } }
      recording = false
      if (wav == null) Toast.makeText(context, "No se pudo grabar (¿micrófono en uso?).", Toast.LENGTH_LONG).show()
      else adopt(wav, "Voz grabada")
    }
  }

  val askMic =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
      if (ok) startRecording()
      else Toast.makeText(context, "Sin permiso de micrófono no se puede grabar.", Toast.LENGTH_SHORT).show()
    }

  fun analyze() {
    val sample = source ?: return
    busy = true
    scope.launch {
      val clean =
        withContext(Dispatchers.IO) {
          val pcm = WavReader.parse(sample.bytes) ?: return@withContext null
          var samples = pcm.samples
          var rate = pcm.sampleRate
          val notes = mutableListOf<String>()
          if (useRnnoise) {
            val denoised = VoiceDenoiser.denoise(samples, rate)
            if (denoised.applied) {
              samples = denoised.samples
              notes.add(VoiceDenoiser.describe(denoised.speechProbability))
            } else {
              notes.add("RNNoise no está disponible en este dispositivo; se aplicó solo la limpieza básica.")
            }
          }
          val cleaned = VoiceCleaner.clean(samples, rate)
          StudioResult(
            wav = WavWriter.pcm16Mono(cleaned.samples, cleaned.sampleRate),
            report = cleaned.report,
            quality = VoiceQuality.assess(cleaned.report),
            denoised = useRnnoise,
            extraNotes = notes,
          )
        }
      busy = false
      if (clean == null) Toast.makeText(context, "No se pudo analizar la muestra.", Toast.LENGTH_LONG).show()
      else {
        result = clean
        player.stop()
        playing = null
      }
    }
  }

  fun play(kind: String) {
    val bytes = if (kind == "original") source?.bytes else result?.wav
    if (bytes == null) return
    if (playing == kind) {
      player.stop()
      playing = null
      return
    }
    val file = File(context.cacheDir, "voice_studio_$kind.wav")
    file.writeBytes(bytes)
    playing = kind
    player.play(file.absolutePath) { playing = null }
  }

  BoxScaffold(
    title = "Voice Studio",
    subtitle = "Muestras limpias para Pocket TTS · todo en el teléfono",
    onBack = onBack,
  ) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("1 · Muestra de voz", style = MaterialTheme.typography.titleSmall)
      Text(
        "Pocket TTS clona la muestra tal cual suena. Graba 5-12 s en silencio, sin saturar y con el mismo tono que quieres oír. Todo se procesa aquí; el audio no sale del teléfono.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickWav.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*")) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.AudioFile, null)
          Text("Elegir WAV")
        }
        OutlinedButton(
          onClick = {
            if (recording) stopFlag.set(true)
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
            else askMic.launch(Manifest.permission.RECORD_AUDIO)
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
          Text(if (recording) "Detener (${recSeconds}s)" else "Grabar")
        }
      }

      source?.let { sample ->
        Text("Muestra: ${sample.name} · %.1f s".format(sample.seconds), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Reducir ruido con RNNoise", style = MaterialTheme.typography.titleSmall)
            Text(
              "Solo afecta a la muestra (48 kHz, bloques de 10 ms), no a la velocidad del chat.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = useRnnoise, onCheckedChange = { useRnnoise = it })
        }
        Button(onClick = { analyze() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
          Text(if (busy) "Analizando…" else "Analizar y limpiar")
        }
      }

      result?.let { clean ->
        HorizontalDivider()
        Text("2 · Resultado", style = MaterialTheme.typography.titleSmall)
        Text(
          "Calidad: ${clean.quality.level.label} (${clean.quality.score}/100)",
          style = MaterialTheme.typography.titleMedium,
          color =
            when (clean.quality.level) {
              VoiceQuality.Level.BUENA -> MaterialTheme.colorScheme.primary
              VoiceQuality.Level.MEJORABLE -> MaterialTheme.colorScheme.tertiary
              VoiceQuality.Level.MALA -> MaterialTheme.colorScheme.error
            },
        )
        Text("Duración: %.1f s → %.1f s".format(clean.report.inSeconds, clean.report.outSeconds), style = MaterialTheme.typography.bodySmall)
        val snr = clean.report.snrDb
        Text(
          "Voz/ruido: ${snr?.let { "%.0f dB".format(it) } ?: "n/d"}" +
            clean.report.snrAfterDb?.let { " → %.0f dB".format(it) }.orEmpty() +
            " · Saturación: %.1f %%".format(clean.report.clippedPct),
          style = MaterialTheme.typography.bodySmall,
        )
        (clean.report.notes + clean.extraNotes).forEach { note ->
          Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { play("original") }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PlayArrow, null)
            Text(if (playing == "original") "Detener" else "Original")
          }
          OutlinedButton(onClick = { play("clean") }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PlayArrow, null)
            Text(if (playing == "clean") "Detener" else "Limpia")
          }
        }
        OutlinedTextField(
          value = voiceName,
          onValueChange = { voiceName = it },
          label = { Text("Nombre de la voz") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Usar como voz predeterminada", style = MaterialTheme.typography.titleSmall)
            Text("Se aplica al leer texto y a los personajes sin voz propia.", style = MaterialTheme.typography.bodySmall)
          }
          Switch(checked = useAsDefault, onCheckedChange = { useAsDefault = it })
        }
        Button(
          onClick = {
            val saved = store.add(clean.wav, voiceName.ifBlank { "Voz de estudio" }, emptyList(), clean = false)
            if (saved == null) Toast.makeText(context, "No se pudo guardar la voz.", Toast.LENGTH_LONG).show()
            else {
              if (useAsDefault) settings.setDefaultVoicePath(store.pathOf(saved.id))
              Toast.makeText(context, "Voz guardada en la biblioteca.", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar voz limpia") }
        HorizontalDivider()
      }

      Text("3 · Leer y ajustar el motor", style = MaterialTheme.typography.titleSmall)
      TtsSettingsPanel(
        textArea = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = text,
              onValueChange = { text = it },
              label = { Text("Escribe lo que quieres escuchar") },
              modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(enabled = text.isNotBlank() && !speaking, onClick = { TtsManager.speak(context, text) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, null)
                Text("Leer")
              }
              OutlinedButton(enabled = speaking, onClick = { TtsManager.stop() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Stop, null)
                Text("Parar")
              }
            }
          }
        },
      )
    }
  }
}
