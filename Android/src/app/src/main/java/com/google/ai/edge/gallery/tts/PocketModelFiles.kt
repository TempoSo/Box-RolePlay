package com.google.ai.edge.gallery.tts

import java.io.File

/**
 * Archivos de un modelo Pocket TTS para sherpa-onnx. Acepta las variantes int8 y normales
 * (lm_flow.int8.onnx / lm_flow.onnx, etc.). Lógica pura.
 */
data class PocketModelFiles(
  val lmFlow: File,
  val lmMain: File,
  val encoder: File,
  val decoder: File,
  val textConditioner: File,
  val vocabJson: File,
  val tokenScoresJson: File,
) {
  companion object {
    /** Nombres (sin extensión) de los modelos .onnx que hacen falta, más los dos .json. */
    private val ONNX_PARTS = listOf("lm_flow", "lm_main", "encoder", "decoder", "text_conditioner")
    private val JSON_PARTS = listOf("vocab.json", "token_scores.json")

    /** Si el nombre de un archivo es una de las piezas del modelo. */
    fun isPart(fileName: String): Boolean =
      fileName in JSON_PARTS ||
        (fileName.endsWith(".onnx") && ONNX_PARTS.any { fileName == "$it.onnx" || fileName.startsWith("$it.") })

    /** Piezas que debe tener la carpeta, con los nombres exactos (BUG-014: para el mensaje de error). */
    val EXPECTED_NAMES: List<String> = ONNX_PARTS.map { "$it.onnx" } + JSON_PARTS

    /**
     * Mensaje cuando la carpeta elegida no contiene ninguna pieza reconocible. Lista los archivos
     * esperados y, si se reconocen nombres del formato v2 (`flow_lm_*`, `mimi_*`, `tokenizer.model`),
     * explica que esa carpeta cruda no vale y que hay que descargar el modelo desde la app.
     */
    fun noPartsMessage(foundNames: List<String> = emptyList()): String {
      val v2 = foundNames.any { it.startsWith("flow_lm_") || it.startsWith("mimi_") || it == "tokenizer.model" || it == "bundle.json" }
      val expected = EXPECTED_NAMES.joinToString(", ")
      return if (v2) {
        "Esa carpeta usa nombres del formato v2 (flow_lm_*, mimi_*, tokenizer.model) y no se puede importar tal cual. " +
          "Usa «Descargar de Hugging Face» (convierte el tokenizador solo) o renombra a: $expected."
      } else {
        "No se encontraron archivos de Pocket TTS en esa carpeta. Debe contener: $expected " +
          "(vale también la variante .int8.onnx)."
      }
    }

    /** Piezas que faltan en [dir] (vacío = el modelo está completo). */
    fun missing(dir: File): List<String> {
      val names = dir.list()?.toList() ?: emptyList()
      val out = mutableListOf<String>()
      for (p in ONNX_PARTS) {
        if (names.none { it == "$p.onnx" || (it.startsWith("$p.") && it.endsWith(".onnx")) }) out.add("$p.onnx")
      }
      for (j in JSON_PARTS) if (j !in names) out.add(j)
      return out
    }

    fun resolve(dir: File): PocketModelFiles? {
      if (missing(dir).isNotEmpty()) return null
      fun onnx(p: String): File {
        val names = dir.list()!!.sorted()
        // Prefiere la versión int8 si existe (más ligera).
        val pick =
          names.firstOrNull { it == "$p.int8.onnx" } ?: names.first { it == "$p.onnx" || (it.startsWith("$p.") && it.endsWith(".onnx")) }
        return File(dir, pick)
      }
      return PocketModelFiles(
        lmFlow = onnx("lm_flow"),
        lmMain = onnx("lm_main"),
        encoder = onnx("encoder"),
        decoder = onnx("decoder"),
        textConditioner = onnx("text_conditioner"),
        vocabJson = File(dir, "vocab.json"),
        tokenScoresJson = File(dir, "token_scores.json"),
      )
    }
  }
}
