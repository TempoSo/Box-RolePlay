package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile

/** Un archivo a bajar y su ruta dentro de la carpeta del modelo (puede llevar subcarpeta: espeak-ng-data/…). */
data class KokoroPick(val file: HfFile, val localPath: String)

/** Un modelo Kokoro completo dentro de un repo de Hugging Face. */
data class KokoroVariant(val folder: String, val files: List<KokoroPick>) {
  val totalBytes: Long get() = files.sumOf { it.file.size }
  val label: String get() = if (folder.isEmpty()) "Kokoro" else "Kokoro · " + folder.substringAfterLast('/')
}

/**
 * Detecta, en el listado completo de un repo, las carpetas que contienen un modelo Kokoro entero
 * (model.onnx, voices.bin, tokens.txt y espeak-ng-data/). Lógica pura.
 */
object KokoroHfSelect {
  fun variants(all: List<HfFile>): List<KokoroVariant> {
    val byFolder = all.groupBy { f ->
      val p = f.path
      val i = p.indexOf("/${KokoroModelFiles.DATA_DIR}/")
      when {
        i >= 0 -> p.substring(0, i)
        p.startsWith("${KokoroModelFiles.DATA_DIR}/") -> ""
        else -> p.substringBeforeLast('/', "")
      }
    }
    return byFolder
      .mapNotNull { (folder, files) ->
        fun rel(f: HfFile) = if (folder.isEmpty()) f.path else f.path.removePrefix("$folder/")
        val top = files.filter { !rel(it).contains('/') }
        val espeak = files.filter { rel(it).startsWith("${KokoroModelFiles.DATA_DIR}/") }
        val model =
          top.firstOrNull { it.name == "model.int8.onnx" } ?: top.firstOrNull { it.name == "model.onnx" }
            ?: top.filter { it.name.endsWith(".onnx") }.minByOrNull { it.name }
        val voices = top.firstOrNull { it.name == KokoroModelFiles.VOICES }
        val tokens = top.firstOrNull { it.name == KokoroModelFiles.TOKENS }
        if (model == null || voices == null || tokens == null || espeak.none { rel(it).endsWith("/phontab") }) {
          return@mapNotNull null
        }
        val lex = top.filter { it.name.startsWith("lexicon") && it.name.endsWith(".txt") }
        val picks =
          (listOf(model, voices, tokens) + lex + espeak).map { f ->
            // El modelo se guarda siempre como model.onnx o model.int8.onnx.
            KokoroPick(f, if (f === model) (if (f.name.contains("int8")) "model.int8.onnx" else "model.onnx") else rel(f))
          }
        KokoroVariant(folder, picks)
      }
      .sortedBy { it.folder }
  }
}
