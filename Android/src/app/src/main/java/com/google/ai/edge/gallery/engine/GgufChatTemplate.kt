package com.google.ai.edge.gallery.engine

/**
 * Plantilla de turnos de Gemma 4 construida en Kotlin (BUG-015), con el formato canonico
 * <|turn>rol ... <turn|>. Replicado de chatfmt::formatGemma4 (smollm/src/main/cpp/ChatFormats.h):
 *
 *   <|turn>system\n{sistema}<turn|>\n
 *   <|turn>user\n{mensaje}<turn|>\n
 *   <|turn>model\n{respuesta}<turn|>\n
 *   ...
 *   <|turn>model\n        <- el modelo empieza a escribir aqui
 *
 * Si el primer turno tras el sistema es del asistente (el saludo del personaje), se antepone un
 * turno de usuario vacio para que los roles alternen (igual que hace el nativo y Layla).
 * El BOS lo agrega el tokenizador nativo (add_special); no se escribe aqui.
 *
 * Al mandar el prompt ya formateado, el nativo (LLMInference::startCompletion) detecta los
 * marcadores de turno y tokeniza el texto tal cual: se salta el formateo nativo y
 * _applyContextWindow(), que era la ruta del crash de BUG-001 (SIGSEGV en llama_vocab::tokenize).
 * El recorte del historial lo hace la app (ContextWindowPlanner) antes de sembrar el turno.
 */
object GgufChatTemplate {

    /** true para modelos Gemma 4 detectados por nombre (mismos patrones que ThinkingSplitter). */
    fun isGemma4(modelName: String): Boolean {
        val n = modelName.lowercase().replace(' ', '-').replace('_', '-')
        return n.contains("gemma-4") || n.contains("gemma4")
    }

    /**
     * [systemPrompt]: prompt del personaje (puede ir con el resumen de memoria ya inyectado).
     * [history]: turnos anteriores como pares (rol, contenido) con roles "user"/"assistant";
     * el contenido vacio se omite (no se tokenizan cadenas vacias, BUG-001).
     * [query]: mensaje nuevo del usuario; no debe llegar vacio (lo guarda el que llama).
     * [thinking]: inyecta <|think|> en el turno de sistema para que el modelo razone antes de
     * responder (canonico de Gemma 4; en el nativo lo hacia formatGemma4 con enableThinking).
     */
    fun build(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        query: String,
        thinking: Boolean,
    ): String {
        val out = StringBuilder()
        var lastWasUser = false

        if (systemPrompt.isNotBlank()) {
            out.append("<|turn>system\n")
            if (thinking) out.append("<|think|>\n")
            out.append(systemPrompt.trim()).append("<turn|>\n")
        } else if (thinking) {
            // Sin turno de sistema se crea uno vacio para poder inyectar <|think|> (como el nativo).
            out.append("<|turn>system\n<|think|>\n<turn|>\n")
        }

        for ((role, content) in history) {
            val text = content.trim()
            if (text.isEmpty()) continue
            if (role == "user") {
                out.append("<|turn>user\n").append(text).append("<turn|>\n")
                lastWasUser = true
            } else {
                if (!lastWasUser) out.append("<|turn>user\n<turn|>\n")
                out.append("<|turn>model\n").append(text).append("<turn|>\n")
                lastWasUser = false
            }
        }

        out.append("<|turn>user\n").append(query.trim()).append("<turn|>\n")
        out.append("<|turn>model\n")
        return out.toString()
    }
}
