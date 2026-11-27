package com.google.ai.edge.gallery.tts

import android.content.res.AssetManager
import android.util.Log

/**
 * Motor de voz Kokoro sobre sherpa-onnx (por reflexión, igual que [SherpaPocketEngine], para compilar aunque
 * la librería no esté). Las voces vienen dentro del modelo (voices.bin): se elige una por su número (sid).
 * El idioma (para espeak-ng) se fija al cargar, así que cambiar a una voz de otro idioma recarga el motor.
 */
class SherpaKokoroEngine(
  private val files: KokoroModelFiles,
  private val threads: Int,
  /** Código espeak del idioma ("es", "fr"…); vacío para inglés y chino, que usan los lexicon. */
  private val lang: String,
  /** La voz es china (activa las reglas de normalización de números/fechas en chino). */
  private val chinese: Boolean = false,
) {
  private val pkg = "com.k2fsa.sherpa.onnx."
  private var tts: Any? = null

  var sampleRate: Int = 0
    private set
  var numSpeakers: Int = 0
    private set

  companion object {
    fun isLibraryAvailable(): Boolean =
      runCatching {
          Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
          Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig")
          true
        }
        .getOrDefault(false)

    /** Idioma de espeak para una voz; "" para inglés (EE. UU.) y chino, que van con los lexicon. */
    fun langFor(voice: KokoroVoice?): String =
      when (voice?.langCode) {
        null, "en-us", "zh" -> ""
        else -> voice.langCode
      }
  }

  private fun cls(name: String): Class<*> = Class.forName(pkg + name)

  private fun Any.set(property: String, value: Any?) {
    val setter = "set" + property.replaceFirstChar { it.uppercase() }
    val m = javaClass.methods.firstOrNull { it.name == setter && it.parameterCount == 1 }
      ?: error("sherpa-onnx: falta $setter en ${javaClass.simpleName} (¿versión de la librería antigua?)")
    m.invoke(this, value)
  }

  private fun Any.get(property: String): Any {
    val getter = "get" + property.replaceFirstChar { it.uppercase() }
    return javaClass.getMethod(getter).invoke(this)!!
  }

  /** Cómo se cargó (para el registro y para saber si hay que reintentar). */
  var mode: String = ""
    private set

  @Synchronized
  fun load() {
    if (tts != null) return
    if (!isLibraryAvailable()) error("Falta la librería sherpa-onnx con soporte de Kokoro (app/libs/sherpa-onnx.aar).")
    // Voces de otros idiomas (es, fr, it, pt…): todo el texto va por espeak-ng con su idioma. Si además se
    // pasan los lexicon de inglés, las palabras que existen en inglés ("me", "a", "sal", "no"…) se leen con
    // fonemas ingleses. Si esta configuración no la acepta la librería, se reintenta con los lexicon.
    val attempts = if (lang.isNotEmpty()) listOf(false, true) else listOf(true)
    var last: Throwable? = null
    for (withLexicon in attempts) {
      try {
        create(withLexicon)
        mode = if (withLexicon) "lexicon" else "espeak"
        Log.i(
          "AGLTts",
          "Kokoro cargado: sr=$sampleRate voces=$numSpeakers lang='$lang' modo=$mode lexicons=${files.lexicons.size} " +
            "dict=${files.dictDir != null} fsts=${files.ruleFsts.size}",
        )
        return
      } catch (e: Throwable) {
        last = e.cause ?: e
        Log.w("AGLTts", "Kokoro: fallo al cargar (lexicon=$withLexicon): $last")
        tts = null
      }
    }
    throw IllegalStateException("No se pudo cargar Kokoro: ${last?.message ?: last}", last)
  }

  private fun create(withLexicon: Boolean) {
    val config = cls("OfflineTtsConfig").getDeclaredConstructor().newInstance()
    val model = config.get("model")
    val k = model.get("kokoro")
    k.set("model", files.model.absolutePath)
    k.set("voices", files.voices.absolutePath)
    k.set("tokens", files.tokens.absolutePath)
    k.set("dataDir", files.dataDir.absolutePath)
    if (withLexicon) k.set("lexicon", files.lexicons.joinToString(",") { it.absolutePath })
    if (lang.isNotEmpty()) k.set("lang", lang)
    files.dictDir?.let { d -> runCatching { k.set("dictDir", d.absolutePath) } }
    // Las reglas de normalización son de chino: con otros idiomas cambiarían cómo se leen los números.
    if (lang.isEmpty() && files.ruleFsts.isNotEmpty() && chinese) {
      runCatching { config.set("ruleFsts", files.ruleFsts.joinToString(",") { it.absolutePath }) }
    }
    model.set("numThreads", threads.coerceAtLeast(1))
    model.set("provider", "cpu")
    config.set("maxNumSentences", 1)

    val ctor = cls("OfflineTts").getConstructor(AssetManager::class.java, cls("OfflineTtsConfig"))
    val engine = ctor.newInstance(null, config)
    tts = engine
    sampleRate = engine.javaClass.getMethod("sampleRate").invoke(engine) as Int
    numSpeakers = engine.javaClass.getMethod("numSpeakers").invoke(engine) as Int
  }

  /** Genera [text] con la voz [sid]; [onSamples] recibe trozos de audio y devuelve false para parar. */
  fun generate(text: String, sid: Int, speed: Float, onSamples: (FloatArray) -> Boolean) {
    val engine = tts ?: error("El motor de voz no está cargado")
    val safeSid = if (numSpeakers > 0) sid.coerceIn(0, numSpeakers - 1) else sid
    val method =
      engine.javaClass.methods.firstOrNull { it.name == "generateWithCallback" && it.parameterCount == 4 }
        ?: error("sherpa-onnx: falta generateWithCallback (¿versión de la librería antigua?)")
    var total = 0
    val t0 = System.currentTimeMillis()
    method.invoke(engine, text, safeSid, speed, ChunkCallback { samples -> total += samples.size; onSamples(samples) })
    Log.i("AGLTts", "Kokoro: ${text.length} car → ${total} muestras (${total * 1000L / sampleRate.coerceAtLeast(1)} ms de audio) en ${System.currentTimeMillis() - t0} ms, sid=$safeSid, modo=$mode")
    if (total == 0) Log.w("AGLTts", "Kokoro no produjo audio para: «${text.take(80)}»")
  }

  /** BUG-026: el callback no puede ser una lambda Kotlin (ver SherpaPocketEngine.ChunkCallback). */
  private class ChunkCallback(private val onSamples: (FloatArray) -> Boolean) : Function1<FloatArray, Int?> {
    override fun invoke(samples: FloatArray): Int? = if (onSamples(samples)) 1 else 0
  }

  @Synchronized
  fun release() {
    val engine = tts ?: return
    tts = null
    runCatching { engine.javaClass.getMethod("release").invoke(engine) }
  }
}
