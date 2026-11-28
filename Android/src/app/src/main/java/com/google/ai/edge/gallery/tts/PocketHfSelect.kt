package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile

/** Un archivo a bajar y el nombre con el que se guarda en la app. */
data class PocketPick(val file: HfFile, val localName: String)

/** Un juego completo de piezas de Pocket TTS dentro de un repo de Hugging Face (una carpeta = una variante/idioma). */
data class PocketVariant(val folder: String, val files: List<PocketPick>) {
  val totalBytes: Long get() = files.sumOf { it.file.size }
  val label: String get() = PocketHfSelect.friendly(folder)
}

/**
 * Elige, a partir del listado de un repo, qué carpetas contienen un modelo Pocket TTS completo. Entiende los
 * dos formatos de nombres: el antiguo (lm_flow, lm_main, encoder, decoder + vocab.json y token_scores.json) y el
 * v2 (flow_lm_flow, flow_lm_main, mimi_encoder, mimi_decoder + tokenizer.model). Lógica pura.
 */
object PocketHfSelect {
  const val TOKENIZER = "tokenizer.model"
  const val TOKENIZER_JSON = "tokenizer.json"
  private val ALIASES =
    mapOf(
      "lm_flow" to listOf("lm_flow", "flow_lm_flow"),
      "lm_main" to listOf("lm_main", "flow_lm_main"),
      "encoder" to listOf("encoder", "mimi_encoder"),
      "decoder" to listOf("decoder", "mimi_decoder"),
      "text_conditioner" to listOf("text_conditioner"),
    )
  private val JSONS = listOf("vocab.json", "token_scores.json")

  private fun isInt8(name: String) = name.contains("int8")

  /** Clave canónica de la pieza a la que corresponde el archivo, o null si no es una pieza. */
  fun partOf(name: String): String? {
    if (name in JSONS || name == TOKENIZER || name == TOKENIZER_JSON) return name
    if (!name.endsWith(".onnx")) return null
    val base = name.removeSuffix(".onnx").removeSuffix(".int8").removeSuffix("_int8")
    return ALIASES.entries.firstOrNull { base in it.value }?.key
  }

  fun variants(all: List<HfFile>): List<PocketVariant> =
    all
      .filter { partOf(it.name) != null }
      .groupBy { it.path.substringBeforeLast('/', "") }
      .mapNotNull { (folder, files) ->
        val byPart = files.groupBy { partOf(it.name)!! }
        if (ALIASES.keys.any { it !in byPart }) return@mapNotNull null
        val hasJsons = JSONS.all { it in byPart }
        val tok = listOf(TOKENIZER, TOKENIZER_JSON).firstOrNull { it in byPart }
        if (!hasJsons && tok == null) return@mapNotNull null
        val keys = ALIASES.keys.toList() + if (hasJsons) JSONS else listOf(tok!!)
        val picks =
          keys.map { key ->
            val opts = byPart.getValue(key)
            if (key.endsWith(".json") || key == TOKENIZER) PocketPick(opts.first(), key)
            else {
              // Si hay int8 y normal de la misma pieza, se baja solo la int8 (más ligera).
              val f = opts.firstOrNull { isInt8(it.name) } ?: opts.minBy { it.size }
              PocketPick(f, if (isInt8(f.name)) "$key.int8.onnx" else "$key.onnx")
            }
          }
        PocketVariant(folder, picks)
      }
      .sortedBy { it.folder }

  private val LANGS =
    mapOf(
      "english" to "Inglés", "spanish" to "Español", "french" to "Francés", "german" to "Alemán",
      "italian" to "Italiano", "portuguese" to "Portugués",
    )

  /** Nombre legible de una carpeta: «spanish_24l» → «Español (24l)». */
  fun friendly(folder: String): String {
    if (folder.isEmpty()) return "Principal (raíz del repositorio)"
    val leaf = folder.substringAfterLast('/')
    val lang = leaf.substringBefore('_').lowercase()
    val rest = leaf.substringAfter('_', "").replace('_', ' ')
    val name = LANGS[lang] ?: return leaf
    return if (rest.isEmpty()) name else "$name ($rest)"
  }
}
