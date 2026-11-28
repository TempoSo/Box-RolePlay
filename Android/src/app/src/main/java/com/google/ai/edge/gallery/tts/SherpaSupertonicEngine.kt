package com.google.ai.edge.gallery.tts

import android.content.res.AssetManager

/**
 * Motor de voz Supertonic TTS sobre sherpa-onnx, por reflexión como los motores Pocket y Kokoro:
 * la app compila aunque el AAR no esté presente y avisa con un mensaje claro. Supertonic no clona
 * una muestra: sus voces son índices (`sid`) dentro de voice.bin y el idioma se pasa en `extra`.
 */
class SherpaSupertonicEngine(private val files: SupertonicModelFiles, private val threads: Int) {
  private val pkg = "com.k2fsa.sherpa.onnx."
  private var tts: Any? = null

  var sampleRate: Int = 0
    private set

  companion object {
    fun isLibraryAvailable(): Boolean =
      runCatching {
        Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
        Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig")
        Class.forName("com.k2fsa.sherpa.onnx.GenerationConfig")
        true
      }.getOrDefault(false)
  }

  private fun cls(name: String): Class<*> = Class.forName(pkg + name)

  private fun Any.set(property: String, value: Any?) {
    val setter = "set" + property.replaceFirstChar { it.uppercase() }
    val m = javaClass.methods.firstOrNull { it.name == setter && it.parameterCount == 1 }
      ?: error("sherpa-onnx: falta $setter en ${javaClass.simpleName} (¿versión de la librería antigua?)")
    m.invoke(this, value)
  }

  private fun Any.get(property: String): Any = javaClass.getMethod("get" + property.replaceFirstChar { it.uppercase() }).invoke(this)!!

  @Synchronized
  fun load() {
    if (tts != null) return
    if (!isLibraryAvailable()) {
      error("Falta la librería sherpa-onnx con soporte de Supertonic TTS. Añade el .aar en app/libs/sherpa-onnx.aar.")
    }
    val config = cls("OfflineTtsConfig").getDeclaredConstructor().newInstance()
    val model = config.get("model")
    val supertonic = model.get("supertonic")
    supertonic.set("durationPredictor", files.durationPredictor.absolutePath)
    supertonic.set("textEncoder", files.textEncoder.absolutePath)
    supertonic.set("vectorEstimator", files.vectorEstimator.absolutePath)
    supertonic.set("vocoder", files.vocoder.absolutePath)
    supertonic.set("ttsJson", files.ttsJson.absolutePath)
    supertonic.set("unicodeIndexer", files.unicodeIndexer.absolutePath)
    supertonic.set("voiceStyle", files.voiceStyle.absolutePath)
    model.set("numThreads", threads.coerceAtLeast(1))
    model.set("provider", "cpu")
    config.set("maxNumSentences", 1)

    val ctor = cls("OfflineTts").getConstructor(AssetManager::class.java, cls("OfflineTtsConfig"))
    val engine = ctor.newInstance(null, config)
    tts = engine
    sampleRate = engine.javaClass.getMethod("sampleRate").invoke(engine) as Int
  }

  /** Genera [text] con la voz [sid]; [onSamples] devuelve false para parar. Bloquea hasta terminar. */
  fun generate(
    text: String,
    sid: Int,
    speed: Float,
    numSteps: Int,
    lang: String,
    onSamples: (FloatArray) -> Boolean,
  ) {
    val engine = tts ?: error("El motor de voz no está cargado")
    val gen = cls("GenerationConfig").getDeclaredConstructor().newInstance()
    gen.set("speed", speed)
    gen.set("sid", sid)
    gen.set("numSteps", numSteps.coerceIn(1, 32))
    gen.set("extra", mapOf("lang" to lang.lowercase()))
    val method =
      engine.javaClass.methods.firstOrNull { it.name == "generateWithConfigAndCallback" && it.parameterCount == 3 }
        ?: error("sherpa-onnx: falta generateWithConfigAndCallback (¿versión de la librería antigua?)")
    method.invoke(engine, text, gen, ChunkCallback(onSamples))
  }

  /** BUG-026: el callback JNI debe ser una clase normal, no una lambda Kotlin (invoke de instancia). */
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
