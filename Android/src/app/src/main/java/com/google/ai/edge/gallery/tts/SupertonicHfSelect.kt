package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile

/** Un archivo a bajar y su ruta dentro de la carpeta del modelo. */
data class SupertonicPick(val file: HfFile, val localPath: String)

/** Un modelo Supertonic completo dentro de un repo de Hugging Face. */
data class SupertonicVariant(val folder: String, val files: List<SupertonicPick>) {
  val totalBytes: Long get() = files.sumOf { it.file.size }
  val label: String get() = if (folder.isEmpty()) "Supertonic" else "Supertonic · " + folder.substringAfterLast('/')
}

/**
 * Detecta, en el listado completo de un repo, las carpetas que contienen un modelo Supertonic
 * entero (los cuatro .onnx, tts.json, unicode_indexer.bin y voice.bin). Lógica pura.
 */
object SupertonicHfSelect {
  private val ONNX_BASES = listOf("duration_predictor", "text_encoder", "vector_estimator", "vocoder")
  private val OTHER_NAMES =
    listOf(SupertonicModelFiles.TTS_JSON, SupertonicModelFiles.UNICODE_INDEXER, SupertonicModelFiles.VOICE_STYLE)

  fun variants(all: List<HfFile>): List<SupertonicVariant> =
    all
      .groupBy { it.path.substringBeforeLast('/', "") }
      .mapNotNull { (folder, files) ->
        fun rel(f: HfFile) = if (folder.isEmpty()) f.path else f.path.removePrefix("$folder/")
        val top = files.filter { !rel(it).contains('/') }
        val picks = mutableListOf<SupertonicPick>()
        for (base in ONNX_BASES) {
          val int8 = top.firstOrNull { it.name == "$base.int8.onnx" }
          val plain = top.firstOrNull { it.name == "$base.onnx" }
          val model = int8 ?: plain ?: return@mapNotNull null
          picks.add(SupertonicPick(model, int8?.let { "$base.int8.onnx" } ?: "$base.onnx"))
        }
        for (name in OTHER_NAMES) {
          val f = top.firstOrNull { it.name == name } ?: return@mapNotNull null
          picks.add(SupertonicPick(f, name))
        }
        SupertonicVariant(folder, picks)
      }
      .sortedBy { it.folder }
}
