package com.google.ai.edge.gallery.data.characters

/**
 * Parte un mensaje que todavía se está generando en frases listas para la voz. Solo entrega frases
 * ya terminadas (hasta el último . ! ? … o salto de línea) y retiene la última, porque podría
 * unirse con la siguiente; el resto no cambia aunque llegue más texto. Lógica pura.
 */
class SpeechStreamer(private val scope: SpeechScope) {
    private var emitted = 0

    private fun chunksOf(text: String): List<String> = SpeechText.chunks(SpeechText.extract(text, scope))

    /** [fullText]: todo lo generado hasta ahora. Devuelve las frases nuevas y estables. */
    fun push(fullText: String): List<String> {
        val end = fullText.indexOfLast { it == '.' || it == '!' || it == '?' || it == '…' || it == '\n' }
        if (end < 0) return emptyList()
        val chunks = chunksOf(fullText.substring(0, end + 1))
        val stable = (chunks.size - 1).coerceAtLeast(0)
        if (stable <= emitted) return emptyList()
        return chunks.subList(emitted, stable).also { emitted = stable }
    }

    /** Al terminar: lo que falta de [fullText]. */
    fun finish(fullText: String): List<String> {
        val chunks = chunksOf(fullText)
        if (chunks.size <= emitted) return emptyList()
        return chunks.subList(emitted, chunks.size).also { emitted = chunks.size }
    }
}
