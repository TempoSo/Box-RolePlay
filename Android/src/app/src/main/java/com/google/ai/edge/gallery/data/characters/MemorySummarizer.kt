package com.google.ai.edge.gallery.data.characters

/**
 * Memoria larga sin gastar una generación extra: cuando la ventana deslizante borra mensajes
 * viejos, se condensan en un resumen extractivo (las frases más informativas: nombres, lugares,
 * cifras) que se añade al prompt de sistema. Así el personaje no "olvida" por completo lo que
 * pasó antes. Es determinista (mismos mensajes -> mismo resumen), así la caché de prefijo de
 * llama.cpp solo se invalida cuando el resumen cambia de verdad. Lógica pura.
 */
object MemorySummarizer {
    const val HEADER = "[Resumen de lo ocurrido antes en esta historia]"

    /** Frases candidatas de un mensaje, sin acotaciones vacías. */
    internal fun sentences(text: String): List<String> =
        text.replace('\n', ' ')
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim().trim('*', '_', '"') }
            .filter { it.length >= 12 }

    /** Puntaje simple: mayúsculas internas (nombres propios), números y longitud moderada. */
    internal fun score(sentence: String): Int {
        val words = sentence.split(' ').filter { it.isNotEmpty() }
        val names = words.drop(1).count { w -> w.firstOrNull()?.isUpperCase() == true && w.length > 2 }
        val digits = words.count { w -> w.any { it.isDigit() } }
        val lenBonus = if (sentence.length in 30..180) 2 else 0
        return names * 3 + digits * 2 + lenBonus
    }

    /**
     * @param dropped mensajes ya fuera de la ventana: pares (esUsuario, texto) en orden.
     * @param budgetTokens tope del resumen en tokens estimados.
     * @return texto listo para el prompt, o "" si no hay nada que resumir.
     */
    fun summarize(dropped: List<Pair<Boolean, String>>, budgetTokens: Int): String {
        if (dropped.isEmpty() || budgetTokens < 40) return ""
        // Una frase por mensaje: la mejor; si sobran, se reparten uniformemente.
        val picks =
            dropped.mapNotNull { (isUser, text) ->
                val best = sentences(text).maxByOrNull { score(it) } ?: return@mapNotNull null
                val cut = if (best.length > 200) best.take(197).trimEnd() + "…" else best
                (if (isUser) "Usuario: " else "Personaje: ") + cut
            }
        if (picks.isEmpty()) return ""
        val headerCost = ContextWindowPlanner.estimateTokens(HEADER)
        var chosen = picks
        fun cost(l: List<String>) = headerCost + l.sumOf { ContextWindowPlanner.estimateTokens(it) }
        while (chosen.size > 1 && cost(chosen) > budgetTokens) {
            // Quita la línea más antigua de cada par de vecinas hasta caber (mantiene lo reciente).
            val keepEvery = chosen.filterIndexed { i, _ -> i % 2 == 1 || i == chosen.lastIndex }
            chosen = if (keepEvery.size < chosen.size) keepEvery else chosen.drop(1)
        }
        if (cost(chosen) > budgetTokens) return ""
        return HEADER + "\n" + chosen.joinToString("\n")
    }

    /** Añade el resumen al prompt de sistema (si hay). */
    fun withSummary(prompt: String, summary: String): String =
        if (summary.isBlank()) prompt else prompt.trimEnd() + "\n\n" + summary
}
