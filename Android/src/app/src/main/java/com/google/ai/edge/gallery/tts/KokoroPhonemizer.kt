package com.google.ai.edge.gallery.tts

import android.content.Context
import android.util.Log
import java.text.Normalizer
import java.util.Locale

/**
 * Fonemizador español para Kokoro. sherpa-onnx >= 2.0 ya no lleva espeak-ng, así que las voces que no
 * tienen lexicon en el modelo (es, fr, it, pt, hi, ja) se quedan mudas. Este objeto convierte el texto
 * en símbolos IPA con los mismos códigos de tokens.txt y se los pasa a la librería mediante
 * `GenerationConfig.phonemeCodepoints`.
 *
 * Usa un diccionario compacto (palabras frecuentes, IPA exacto de espeak-ng) y, para las palabras que
 * no están en él, reglas G2P del español. El diccionario se lee una vez desde assets/tts.
 */
object KokoroPhonemizer {
  private const val TAG = "AGLTts"
  private const val ASSET = "tts/kokoro_es_lexicon.txt"

  private val ACCENT = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')
  private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'ü')
  private val STRONG = setOf('a', 'e', 'o')

  @Volatile private var dictionary: Map<String, String>? = null

  /** Carga el diccionario; se puede llamar muchas veces (solo la primera hace trabajo). */
  fun initialize(context: Context) {
    if (dictionary != null) return
    synchronized(this) {
      if (dictionary != null) return
      val map = HashMap<String, String>(7000)
      context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.forEach { line ->
          val tab = line.indexOf('\t')
          if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
        }
      }
      dictionary = map
      Log.i(TAG, "Kokoro: diccionario fonético español cargado (${map.size} palabras)")
    }
  }

  fun supports(lang: String): Boolean = lang == "es" && dictionary != null

  /** Devuelve los puntos de código IPA de [text], o null si no se puede fonemizar. */
  fun phonemize(text: String, lang: String): IntArray? {
    val dict = dictionary ?: return null
    if (!supports(lang)) return null
    val clean = normalize(text)
    if (clean.isEmpty()) return null

    val out = StringBuilder()
    val tokens = Regex("[\\p{L}\\p{N}]+|[.,!?;:]").findAll(clean)
    for (match in tokens) {
      val token = match.value
      if (token.length == 1 && token[0] in ".,!?;:") {
        out.append(token)
        continue
      }
      val word = token.lowercase(Locale.ROOT)
      val ipa = dict[word]?.ifEmpty { null } ?: g2p(word)
      if (ipa.isEmpty()) continue
      // Entre palabras va el token espacio; después de una coma no (así lo hace el lexicon de sherpa).
      if (out.isNotEmpty() && out.last() != ' ' && out.last() !in ".,!?;:") out.append(' ')
      out.append(ipa)
    }
    if (out.isEmpty()) return null
    return IntArray(out.length) { out[it].code }
  }

  private fun normalize(text: String): String {
    var s = Normalizer.normalize(text, Normalizer.Form.NFC)
    // El modelo no tiene tokens para ¿/¡/' ni para guiones largos.
    s = s.replace("¿", "").replace("¡", "").replace("'", "")
    s = s.replace("…", ".").replace("—", ",").replace("–", ",")
    s = s.replace(Regex("\\d+(?:[.,]\\d+)?")) { m -> numberToWords(m.value) }
    return s.replace(Regex("[ \\t\\r\\n]+"), " ").trim()
  }

  // --- Números: el modelo no tiene tokens para dígitos. ---

  private fun numberToWords(raw: String): String {
    val parts = raw.replace('.', ',').split(',', limit = 2)
    val n = parts[0].toLongOrNull() ?: return parts[0].map { digit(it.code - '0'.code) }.joinToString(" ")
    val first = integerToWords(n)
    if (parts.size == 1) return first
    return "$first punto " + parts[1].map { digit(it.code - '0'.code) }.joinToString(" ")
  }

  private fun digit(n: Int): String =
    when (n) {
      0 -> "cero"; 1 -> "uno"; 2 -> "dos"; 3 -> "tres"; 4 -> "cuatro"; 5 -> "cinco"
      6 -> "seis"; 7 -> "siete"; 8 -> "ocho"; else -> "nueve"
    }

  private fun integerToWords(n: Long): String {
    if (n > 999_999L) return n.toString().map { digit(it.code - '0'.code) }.joinToString(" ")
    if (n == 0L) return "cero"
    fun under100(v: Int): String =
      when (v) {
        0 -> ""
        1 -> "uno"; 2 -> "dos"; 3 -> "tres"; 4 -> "cuatro"; 5 -> "cinco"
        6 -> "seis"; 7 -> "siete"; 8 -> "ocho"; 9 -> "nueve"
        10 -> "diez"; 11 -> "once"; 12 -> "doce"; 13 -> "trece"; 14 -> "catorce"
        15 -> "quince"; 16 -> "dieciséis"; 17 -> "diecisiete"; 18 -> "dieciocho"; 19 -> "diecinueve"
        20 -> "veinte"
        else ->
          if (v < 30) "veinti${digit(v - 20)}"
          else {
            val tens =
              when (v / 10) { 3 -> "treinta"; 4 -> "cuarenta"; 5 -> "cincuenta"; 6 -> "sesenta"
                7 -> "setenta"; 8 -> "ochenta"; else -> "noventa" }
            if (v % 10 == 0) tens else "$tens y ${digit(v % 10)}"
          }
      }
    fun under1000(v: Int): String {
      if (v < 100) return under100(v)
      val h =
        when (v / 100) { 1 -> "ciento"; 2 -> "doscientos"; 3 -> "trescientos"; 4 -> "cuatrocientos"
          5 -> "quinientos"; 6 -> "seiscientos"; 7 -> "setecientos"; 8 -> "ochocientos"
          else -> "novecientos" }
      val rest = under100(v % 100)
      return if (rest.isEmpty()) h else "$h $rest"
    }
    val thousand = (n / 1000).toInt()
    val rest = (n % 1000).toInt()
    val head = if (thousand == 1) "mil" else if (thousand > 1) "${under1000(thousand)} mil" else ""
    val tail = if (thousand > 0 && rest == 0) "" else under1000(rest)
    return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
  }

  // --- Reglas G2P para palabras fuera del diccionario. ---

  private fun base(c: Char): Char = ACCENT[c] ?: c
  private fun isVowel(c: Char): Boolean = c in VOWELS || c in ACCENT
  private fun isFront(c: Char?): Boolean = c == 'e' || c == 'é' || c == 'i' || c == 'í'

  /** Grupos de vocales contiguas (diptongos); dos fuertes o una acentuada rompen el grupo. */
  private fun vowelGroups(w: String): List<IntRange> {
    val groups = mutableListOf<IntRange>()
    var start = -1
    var end = -1
    for (i in w.indices) {
      if (!isVowel(w[i])) continue
      if (start < 0) {
        start = i; end = i; continue
      }
      val prev = w[end]
      val cur = w[i]
      val hiatus =
        (base(prev) in STRONG && base(cur) in STRONG) || prev in ACCENT || cur in ACCENT ||
          end - start >= 1
      if (i != end + 1 || hiatus) {
        groups.add(start..end)
        start = i; end = i
      } else end = i
    }
    if (start >= 0) groups.add(start..end)
    return groups
  }

  private fun g2p(raw: String): String {
    val w = Normalizer.normalize(raw, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    if (w.isEmpty()) return ""
    if (w == "y") return "i"
    val groups = vowelGroups(w)
    if (groups.isEmpty()) return ""

    val explicit = w.indexOfFirst { it in ACCENT }
    val stress: Int
    val secondary: Int
    if (explicit >= 0) {
      stress = explicit
      secondary = -1
    } else {
      val last = w.last()
      val gi =
        if (isVowel(last) || last == 'n' || last == 's') groups.size - 2 else groups.size - 1
      val g = groups[gi.coerceIn(0, groups.lastIndex)]
      var k = g.first
      for (i in g.first..g.last) if (base(w[i]) in STRONG) { k = i; break }
      stress = k
      secondary = if (groups.size >= 3 && gi > 0) groups[0].first else -1
    }

    val out = StringBuilder()
    // Último sonido real (ignora las marcas de acento).
    fun prevSound(): Char {
      for (k in out.length - 1 downTo 0) {
        val ch = out[k]
        if (ch != 'ˈ' && ch != 'ˌ') return ch
      }
      return ' '
    }
    fun mark(i: Int): String = if (i == stress) "ˈ" else if (i == secondary) "ˌ" else ""

    var i = 0
    while (i < w.length) {
      val c = w[i]
      val n = w.getOrNull(i + 1)
      when {
        c == 'c' && n == 'h' -> { out.append("tʃ"); i += 2; continue }
        c == 'l' && n == 'l' -> { out.append('ʎ'); i += 2; continue }
        c == 'r' && n == 'r' -> { out.append('r'); i += 2; continue }
        c == 'q' && n == 'u' -> { out.append('k'); i += 2; continue }
        c == 'g' && n == 'ü' -> { out.append("ɡw"); i += 2; continue }
        c == 'g' && n == 'u' && isFront(w.getOrNull(i + 2)) -> { out.append('ɡ'); i += 2; continue }
        c == 'h' -> { i++; continue }
        c == 'ñ' -> { out.append('ɲ'); i++; continue }
        c == 'c' -> { out.append(if (isFront(n)) 'θ' else 'k'); i++; continue }
        c == 'z' -> { out.append('θ'); i++; continue }
        c == 'j' -> { out.append('x'); i++; continue }
        c == 'g' -> {
          val inter = isVowel(prevSound()) && n != null && isVowel(n)
          out.append(if (isFront(n)) 'x' else if (inter) 'ɣ' else 'ɡ'); i++; continue
        }
        c == 'x' -> { out.append("ks"); i++; continue }
        c == 'y' -> {
          out.append(if (prevSound() == 'u' && n == null) 'j' else if (n != null && isVowel(n)) 'ʝ' else 'ɪ')
          i++; continue
        }
        c == 'b' || c == 'v' -> {
          val inter = isVowel(prevSound()) && n != null && isVowel(n)
          out.append(if (inter) 'β' else c); i++; continue
        }
        c == 'd' -> {
          val inter = isVowel(prevSound()) && n != null && isVowel(n)
          out.append(if (inter) 'ð' else 'd'); i++; continue
        }
        c == 'n' -> {
          out.append(when (n) { 'g', 'k', 'ɡ' -> 'ŋ'; 'p', 'b' -> 'm'; else -> 'n' })
          i++; continue
        }
        c == 'r' -> { out.append(if (i == 0) 'r' else 'ɾ'); i++; continue }
        c == 'w' -> { out.append('w'); i++; continue }
        c in setOf('p', 't', 'm', 'l', 's', 'f', 'k') -> { out.append(c); i++; continue }
        isVowel(c) -> {
          val b = base(c)
          var ph = b
          if (b == 'i' || b == 'u') {
            val following = n?.let { base(it) }
            when {
              following != null && following in STRONG -> ph = if (b == 'i') 'j' else 'w'
              isVowel(prevSound()) && prevSound() !in "j w ɪ ʊ" -> ph = if (b == 'i') 'ɪ' else 'ʊ'
            }
          }
          out.append(mark(i))
          out.append(ph)
          i++
        }
        else -> { i++; continue }
      }
    }
    return out.toString()
  }
}
