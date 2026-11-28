package com.google.ai.edge.gallery.tts

import java.io.File

/**
 * Archivos de un modelo Supertonic TTS para sherpa-onnx: cuatro .onnx, tts.json,
 * unicode_indexer.bin y voice.bin (las voces van dentro del modelo, no se clona un WAV).
 * Acepta las variantes int8 y normales de los cuatro .onnx. Lógica pura.
 */
data class SupertonicModelFiles(
  val durationPredictor: File,
  val textEncoder: File,
  val vectorEstimator: File,
  val vocoder: File,
  val ttsJson: File,
  val unicodeIndexer: File,
  val voiceStyle: File,
) {
  companion object {
    const val DURATION_PREDICTOR = "duration_predictor.int8.onnx"
    const val TEXT_ENCODER = "text_encoder.int8.onnx"
    const val VECTOR_ESTIMATOR = "vector_estimator.int8.onnx"
    const val VOCODER = "vocoder.int8.onnx"
    const val TTS_JSON = "tts.json"
    const val UNICODE_INDEXER = "unicode_indexer.bin"
    const val VOICE_STYLE = "voice.bin"

    private val ONNX_PARTS = listOf("duration_predictor", "text_encoder", "vector_estimator", "vocoder")
    private val OTHER_PARTS = listOf(TTS_JSON, UNICODE_INDEXER, VOICE_STYLE)

    /** Nombres que acepta la importación de carpeta (sin subcarpetas). */
    val EXPECTED_NAMES: List<String> = ONNX_PARTS.map { "$it.onnx" } + OTHER_PARTS

    fun isPart(fileName: String): Boolean =
      fileName in OTHER_PARTS ||
        (fileName.endsWith(".onnx") && ONNX_PARTS.any { fileName == "$it.onnx" || fileName == "$it.int8.onnx" })

    /** Si la carpeta parece de Supertonic (tiene el índice y las voces). */
    fun looksLike(dir: File): Boolean = File(dir, UNICODE_INDEXER).exists() && File(dir, VOICE_STYLE).exists()

    private fun onnxName(names: List<String>, base: String): String? =
      names.firstOrNull { it == "$base.int8.onnx" } ?: names.firstOrNull { it == "$base.onnx" }

    /** Piezas que faltan en [dir] (vacío = completo). */
    fun missing(dir: File): List<String> {
      val names = dir.list()?.toList() ?: emptyList()
      val out = mutableListOf<String>()
      ONNX_PARTS.forEach { if (onnxName(names, it) == null) out.add("$it.onnx") }
      OTHER_PARTS.forEach { if (it !in names) out.add(it) }
      return out
    }

    fun resolve(dir: File): SupertonicModelFiles? {
      if (missing(dir).isNotEmpty()) return null
      val names = dir.list()!!.toList()
      fun onnx(base: String) = File(dir, onnxName(names, base)!!)
      return SupertonicModelFiles(
        durationPredictor = onnx("duration_predictor"),
        textEncoder = onnx("text_encoder"),
        vectorEstimator = onnx("vector_estimator"),
        vocoder = onnx("vocoder"),
        ttsJson = File(dir, TTS_JSON),
        unicodeIndexer = File(dir, UNICODE_INDEXER),
        voiceStyle = File(dir, VOICE_STYLE),
      )
    }
  }
}

/** Voz de Supertonic: es un índice (`sid`) dentro de voice.bin; el idioma se elige aparte. */
object SupertonicVoices {
  /** Supertonic 3 trae 10 voces; el modelo original trae menos, pero el AAR informa el número real. */
  const val COUNT = 10

  val languages: List<Pair<String, String>> =
    listOf("es" to "Español", "en" to "Inglés", "fr" to "Francés", "pt" to "Portugués", "ko" to "Coreano")

  fun sample(lang: String): String =
    when (lang) {
      "es" -> "Hola, esta es mi voz. ¿Qué te parece cómo suena?"
      "fr" -> "Bonjour, voici ma voix. Qu'en penses-tu ?"
      "pt" -> "Olá, esta é a minha voz. O que você achou?"
      "ko" -> "안녕하세요, 이것은 제 목소리입니다."
      else -> "Hello, this is my voice. How does it sound?"
    }
}
