package com.google.ai.edge.gallery.tts

import android.content.res.AssetManager

/**
 * Motor de voz Pocket TTS sobre sherpa-onnx. Se usa por reflexión a propósito: así la app
 * compila aunque la librería (AAR) todavía no esté añadida, y avisa con un mensaje claro en vez
 * de romper la compilación. Nombres de clases y campos tomados de la API Kotlin de sherpa-onnx
 * (Tts.kt): OfflineTts, OfflineTtsConfig, OfflineTtsModelConfig, OfflineTtsPocketModelConfig,
 * GenerationConfig.
 */
class SherpaPocketEngine(private val files: PocketModelFiles, private val threads: Int) {
  private val pkg = "com.k2fsa.sherpa.onnx."
  private var tts: Any? = null

  var sampleRate: Int = 0
    private set

  companion object {
    /** true si la librería sherpa-onnx (con soporte Pocket) está dentro de la app. */
    fun isLibraryAvailable(): Boolean =
      runCatching {
          Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
          Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig")
          true
        }
        .getOrDefault(false)
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

  @Synchronized
  fun load() {
    if (tts != null) return
    if (!isLibraryAvailable()) {
      error(
        "Falta la librería sherpa-onnx con soporte de Pocket TTS. Añade el .aar en app/libs/sherpa-onnx.aar " +
          "(ver las instrucciones de la pantalla de voz)."
      )
    }
    val config = cls("OfflineTtsConfig").getDeclaredConstructor().newInstance()
    val model = config.get("model")
    val pocket = model.get("pocket")
    pocket.set("lmFlow", files.lmFlow.absolutePath)
    pocket.set("lmMain", files.lmMain.absolutePath)
    pocket.set("encoder", files.encoder.absolutePath)
    pocket.set("decoder", files.decoder.absolutePath)
    pocket.set("textConditioner", files.textConditioner.absolutePath)
    pocket.set("vocabJson", files.vocabJson.absolutePath)
    pocket.set("tokenScoresJson", files.tokenScoresJson.absolutePath)
    model.set("numThreads", threads.coerceAtLeast(1))
    model.set("provider", "cpu")
    config.set("maxNumSentences", 1)

    val ctor = cls("OfflineTts").getConstructor(AssetManager::class.java, cls("OfflineTtsConfig"))
    val engine = ctor.newInstance(null, config)
    tts = engine
    sampleRate = engine.javaClass.getMethod("sampleRate").invoke(engine) as Int
  }

  /**
   * Genera la voz de [text] clonando [referenceAudio]. [onSamples] recibe trozos de audio según
   * se van generando; devuelve false para parar. Bloquea hasta terminar.
   */
  fun generate(
    text: String,
    referenceAudio: FloatArray,
    referenceSampleRate: Int,
    speed: Float,
    numSteps: Int,
    onSamples: (FloatArray) -> Boolean,
  ) {
    val engine = tts ?: error("El motor de voz no está cargado")
    val gen = cls("GenerationConfig").getDeclaredConstructor().newInstance()
    gen.set("speed", speed)
    gen.set("referenceAudio", referenceAudio)
    gen.set("referenceSampleRate", referenceSampleRate)
    gen.set("numSteps", numSteps)
    gen.set("extra", mapOf("max_reference_audio_len" to "12"))

    val callback: (FloatArray) -> Int = { samples -> if (onSamples(samples)) 1 else 0 }
    val method =
      engine.javaClass.methods.firstOrNull { it.name == "generateWithConfigAndCallback" && it.parameterCount == 3 }
        ?: error("sherpa-onnx: falta generateWithConfigAndCallback (¿versión de la librería antigua?)")
    method.invoke(engine, text, gen, callback)
  }

  @Synchronized
  fun release() {
    val engine = tts ?: return
    tts = null
    runCatching { engine.javaClass.getMethod("release").invoke(engine) }
  }
}
