package com.google.ai.edge.gallery.data.characters

/**
 * Memoria automática SIN modelo: toma el último intercambio (usuario + respuesta) y guarda las
 * frases clave tal cual, en el idioma original. Es instantánea y no inventa nada, a diferencia de
 * un resumen generado. La consolidación ([merge]) agrupa recuerdos viejos en uno solo.
 */
object AutoMemory {
    private val TAGS = Regex("<[^>]{1,40}>")
    private val MARKUP = Regex("[*_`#~>]+")
    private val SPACES = Regex("\\s+")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?…])\\s+")

    private fun clean(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val emoji = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
                cp == 0xFE0F || cp == 0x200D
            if (!emoji) sb.appendCodePoint(cp)
        }
        return sb.toString().replace(TAGS, " ").replace(MARKUP, "").replace(SPACES, " ").trim()
    }

    /** Primeras [count] frases con sentido, sin pasar de [maxChars]. */
    private fun keySentences(text: String, count: Int, maxChars: Int): String {
        val sentences = clean(text).split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val meaningful = sentences.filter { it.length >= 12 }.ifEmpty { sentences }
        val joined = meaningful.take(count).joinToString(" ")
        if (joined.length <= maxChars) return joined
        val cut = joined.take(maxChars)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '…'))
        return if (end >= maxChars / 2) cut.take(end + 1) else cut.trimEnd() + "…"
    }

    /** Recuerdo del intercambio, o null si no hay nada que valga la pena guardar. */
    fun fromExchange(userName: String, charName: String, userText: String, reply: String): String? {
        val u = keySentences(userText, 2, 150)
        val r = keySentences(reply, 2, 200)
        if (u.length + r.length < 25) return null
        val parts = buildList {
            if (u.isNotEmpty()) add("${userName.ifBlank { "User" }}: $u")
            if (r.isNotEmpty()) add("${charName.ifBlank { "Character" }}: $r")
        }
        return parts.joinToString("\n")
    }

    fun isDuplicate(candidate: String, recent: List<String>): Boolean {
        val n = candidate.lowercase().replace(SPACES, " ").trim()
        return recent.any { it.lowercase().replace(SPACES, " ").trim() == n }
    }

    /** Junta recuerdos (de viejo a nuevo) en uno solo; si no caben se descartan los más viejos. */
    fun merge(oldestFirst: List<String>, maxChars: Int = 1200): String {
        val kept = ArrayDeque<String>()
        var size = 0
        for (m in oldestFirst.asReversed()) {
            val t = m.trim()
            if (t.isEmpty()) continue
            if (size + t.length + 1 > maxChars && kept.isNotEmpty()) break
            kept.addFirst(t.take(maxChars))
            size += t.length + 1
        }
        return kept.joinToString("\n")
    }
}
