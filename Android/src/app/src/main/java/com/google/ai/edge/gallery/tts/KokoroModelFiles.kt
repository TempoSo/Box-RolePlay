package com.google.ai.edge.gallery.tts

import java.io.File

/**
 * Archivos de un modelo Kokoro para sherpa-onnx: model.onnx, voices.bin (todas las voces), tokens.txt y la
 * carpeta espeak-ng-data; los lexicon-*.txt son opcionales (inglés y chino). Lógica pura.
 */
data class KokoroModelFiles(
  val model: File,
  val voices: File,
  val tokens: File,
  val dataDir: File,
  val lexicons: List<File>,
  /** Carpeta `dict/` (jieba, para chino), si existe. */
  val dictDir: File? = null,
  /** Reglas `*.fst` de normalización (fechas, números, teléfonos en chino), si existen. */
  val ruleFsts: List<File> = emptyList(),
) {
  companion object {
    const val VOICES = "voices.bin"
    const val TOKENS = "tokens.txt"
    const val DATA_DIR = "espeak-ng-data"

    /** Nº aproximado de voces según el tamaño de voices.bin (~0,5 MB por voz). */
    fun estimatedVoices(dir: File): Int = (File(dir, VOICES).length() / 522_240L).toInt()

    /** Si la carpeta es de un modelo Kokoro (tiene voices.bin). */
    fun looksLike(dir: File): Boolean = File(dir, VOICES).exists()

    private fun modelName(names: List<String>): String? =
      names.firstOrNull { it == "model.int8.onnx" } ?: names.firstOrNull { it == "model.onnx" }
        ?: names.sorted().firstOrNull { it.endsWith(".onnx") }

    /** Piezas que faltan en [dir] (vacío = completo). */
    fun missing(dir: File): List<String> {
      val names = dir.list()?.toList() ?: emptyList()
      val out = mutableListOf<String>()
      if (modelName(names) == null) out.add("model.onnx")
      if (VOICES !in names) out.add(VOICES)
      if (TOKENS !in names) out.add(TOKENS)
      if (!File(dir, "$DATA_DIR/phontab").exists()) out.add("$DATA_DIR/")
      return out
    }

    fun resolve(dir: File): KokoroModelFiles? {
      if (missing(dir).isNotEmpty()) return null
      val names = dir.list()!!.toList()
      return KokoroModelFiles(
        model = File(dir, modelName(names)!!),
        voices = File(dir, VOICES),
        tokens = File(dir, TOKENS),
        dataDir = File(dir, DATA_DIR),
        lexicons = names.filter { it.startsWith("lexicon") && it.endsWith(".txt") }.sorted().map { File(dir, it) },
        dictDir = File(dir, "dict").takeIf { it.isDirectory },
        ruleFsts = names.filter { it.endsWith(".fst") }.sorted().map { File(dir, it) },
      )
    }
  }
}

/** Una voz de Kokoro: número (sid), nombre, idioma y género. */
data class KokoroVoice(val sid: Int, val id: String, val langCode: String, val language: String, val female: Boolean) {
  val displayName: String get() = id.substringAfter('_').replaceFirstChar { it.uppercase() }
  val subtitle: String get() = "$language · ${if (female) "mujer" else "hombre"}"
}

/**
 * Voces del modelo multilingüe kokoro-multi-lang-v1_0 (54, en este orden). Otros modelos Kokoro tienen menos;
 * solo se muestran las que caben según el número de voces que informa el modelo.
 */
object KokoroVoices {
  private val IDS =
    listOf(
      "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river",
      "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx",
      "am_puck", "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george",
      "bm_lewis", "ef_dora", "em_alex", "ff_siwis", "hf_alpha", "hf_beta", "hm_omega", "hm_psi", "if_sara",
      "im_nicola", "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo", "pf_dora", "pm_alex",
      "pm_santa", "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi", "zm_yunjian", "zm_yunxi", "zm_yunxia",
      "zm_yunyang", "em_santa",
    )

  /** Código de idioma (para espeak) y nombre por la primera letra del id. */
  private val LANGS =
    mapOf(
      'a' to ("en-us" to "Inglés (EE. UU.)"), 'b' to ("en-gb" to "Inglés (R. Unido)"), 'e' to ("es" to "Español"),
      'f' to ("fr" to "Francés"), 'h' to ("hi" to "Hindi"), 'i' to ("it" to "Italiano"), 'j' to ("ja" to "Japonés"),
      'p' to ("pt-br" to "Portugués (Brasil)"), 'z' to ("zh" to "Chino"),
    )

  const val MULTILANG_COUNT = 54

  val all: List<KokoroVoice> =
    IDS.mapIndexed { sid, id ->
      val (code, lang) = LANGS[id[0]] ?: ("en-us" to "Inglés")
      KokoroVoice(sid, id, code, lang, female = id[1] == 'f')
    }

  /** Voces disponibles en un modelo con [numSpeakers] voces (los modelos solo-inglés traen menos). */
  fun available(numSpeakers: Int): List<KokoroVoice> =
    if (numSpeakers >= MULTILANG_COUNT) all else all.take(numSpeakers.coerceAtLeast(0))

  fun byIndex(sid: Int): KokoroVoice? = all.getOrNull(sid)

  /** Frase de prueba en el idioma de la voz. */
  fun sample(langCode: String): String =
    when (langCode) {
      "es" -> "Hola, esta es mi voz. ¿Qué te parece cómo suena?"
      "fr" -> "Bonjour, voici ma voix. Qu'en penses-tu ?"
      "it" -> "Ciao, questa è la mia voce. Che ne pensi?"
      "pt-br" -> "Olá, esta é a minha voz. O que você achou?"
      "zh" -> "你好,这是我的声音。你觉得怎么样?"
      "ja" -> "こんにちは、これは私の声です。"
      "hi" -> "नमस्ते, यह मेरी आवाज़ है।"
      else -> "Hello, this is my voice. How does it sound?"
    }

  /** Voz por defecto: Dora (español) si el modelo es multilingüe; si no, la primera. */
  fun defaultSid(numSpeakers: Int): Int = if (numSpeakers >= MULTILANG_COUNT) 28 else 0
}
