package com.google.ai.edge.gallery.data.characters

/**
 * Separa el "pensamiento" de la respuesta en modelos Gemma 4 (GGUF) siguiendo el formato oficial:
 * con thinking activado el modelo escribe `<|channel>thought\n...<channel|>` y despues la
 * respuesta. Se decide con el primer texto no vacio:
 *  - empieza con `<|channel>`: es pensamiento; se retiene hasta `<channel|>` y se entrega aparte;
 *  - cualquier otra cosa: es respuesta y fluye en vivo desde el primer token (sin retener nada).
 * Los marcadores sueltos que aparezcan despues se quitan de la respuesta. Logica pura.
 */
class ThinkingSplitter(private val thoughtLimit: Int = 12000) {

    /** Lo que hay que mostrar ahora. `thought` null = nada nuevo de pensamiento. */
    data class Out(val answer: String = "", val thought: String? = null) {
        val isEmpty: Boolean get() = answer.isEmpty() && thought == null
    }

    private enum class Mode { UNDECIDED, THOUGHT, ANSWER }

    private var mode = Mode.UNDECIDED
    private val held = StringBuilder()

    fun feed(piece: String): Out {
        if (mode == Mode.ANSWER) return Out(answer = clean(piece))
        held.append(piece)
        val text = held.toString()
        if (mode == Mode.UNDECIDED) {
            val t = text.trimStart()
            if (t.isEmpty() || (t.length < OPEN.length && OPEN.startsWith(t))) return Out() // aun no se sabe
            if (t.startsWith(OPEN)) mode = Mode.THOUGHT
            else return release(text)
        }
        val end = text.indexOf(CLOSE)
        if (end >= 0) {
            mode = Mode.ANSWER
            held.clear()
            val thought = clean(text.substring(0, end)).trim()
            val answer = clean(text.substring(end + CLOSE.length)).trimStart()
            return Out(answer = answer, thought = thought.ifEmpty { null })
        }
        if (text.length > thoughtLimit) return release(text) // seguridad: marcador que nunca llega
        return Out()
    }

    /** Al terminar: libera lo retenido (pensamiento sin cerrar o texto muy corto). */
    fun finish(): Out {
        if (mode == Mode.ANSWER || held.isEmpty()) return Out()
        return release(held.toString())
    }

    private fun release(text: String): Out {
        mode = Mode.ANSWER
        held.clear()
        return Out(answer = clean(text))
    }

    private fun clean(s: String): String =
        s.replace(OPEN, "").replace(CLOSE, "").replace(THOUGHT_HEADER, "")

    companion object {
        const val OPEN = "<|channel>"
        const val CLOSE = "<channel|>"
        private const val THOUGHT_HEADER = "thought\n"

        fun appliesTo(modelName: String): Boolean {
            val n = modelName.lowercase()
            return n.contains("gemma-4") || n.contains("gemma4") || n.contains("gemma_4")
        }
    }
}
