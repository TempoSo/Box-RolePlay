package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile

/** Un juego completo de piezas de Pocket TTS dentro de un repo de Hugging Face (una carpeta = una variante/idioma). */
data class PocketVariant(val folder: String, val files: List<HfFile>) {
  val totalBytes: Long get() = files.sumOf { it.size }
  val label: String get() = if (folder.isEmpty()) "Raíz del repositorio" else folder
}

/** Elige, a partir del listado de un repo, qué carpetas contienen un modelo Pocket TTS completo. Lógica pura. */
object PocketHfSelect {
  private val ONNX_PARTS = listOf("lm_flow", "lm_main", "encoder", "decoder", "text_conditioner")
  private val JSON_PARTS = listOf("vocab.json", "token_scores.json")

  private fun partOf(name: String): String? {
    if (name in JSON_PARTS) return name
    if (!name.endsWith(".onnx")) return null
    return ONNX_PARTS.firstOrNull { name == "$it.onnx" || name.startsWith("$it.") }
  }

  fun variants(all: List<HfFile>): List<PocketVariant> =
    all
      .filter { partOf(it.name) != null }
      .groupBy { it.path.substringBeforeLast('/', "") }
      .mapNotNull { (folder, files) ->
        val byPart = files.groupBy { partOf(it.name)!! }
        if ((ONNX_PARTS + JSON_PARTS).any { it !in byPart }) return@mapNotNull null
        // Si hay int8 y normal de la misma pieza, se baja solo la int8 (más ligera).
        val chosen = byPart.values.map { opts -> opts.firstOrNull { it.name.contains(".int8.") } ?: opts.minBy { it.size } }
        PocketVariant(folder, chosen)
      }
      .sortedBy { it.folder }
}
