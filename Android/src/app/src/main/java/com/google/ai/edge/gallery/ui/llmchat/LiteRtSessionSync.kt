package com.google.ai.edge.gallery.ui.llmchat

/**
 * Decide si la `Conversation` viva de LiteRT-LM todavía sirve para el turno que empieza.
 *
 * LiteRT guarda la caché KV **dentro** de la conversación: `sendMessage` conserva el historial y
 * sólo prefilla el mensaje nuevo. Recrear la conversación obliga a volver a prefillar todos los
 * mensajes sembrados, que es justo lo que la caché en disco de GGUF evita. Por eso se separan los
 * motivos de recreación y se tolera el turno que no añadió mensaje de usuario (escenas / contexto).
 */
internal object LiteRtSessionSync {
  internal enum class Reason {
    NONE,
    CANCELLED,
    WINDOW_DROP,
    FIRST_TURN,
    NEW_INSTANCE,
    PROMPT_CHANGED,
    SIZE_MISMATCH,
  }

  internal data class State(
    val instance: Any,
    val prompt: String,
    val previousSize: Int,
    /** Mensajes de texto que añadió el turno anterior: 2 normal, 1 si no hubo mensaje nuevo. */
    val expectedDelta: Int,
    val dropped: Int,
  )

  /**
   * Devuelve por qué hay que recrear la conversación, o [Reason.NONE] si se puede reutilizar.
   * [extraDrop] se pasa aparte porque depende de saber cuántos mensajes se descartaron antes.
   */
  internal fun reason(
    previous: State?,
    instance: Any,
    prompt: String,
    previousSize: Int,
    cancelled: Boolean,
    extraDrop: Int,
    hasPromptOrHistory: Boolean,
  ): Reason {
    if (cancelled) return Reason.CANCELLED
    if (extraDrop > 0) return Reason.WINDOW_DROP
    val state = previous ?: return if (hasPromptOrHistory) Reason.FIRST_TURN else Reason.NONE
    if (state.instance !== instance) return Reason.NEW_INSTANCE
    if (state.prompt != prompt) return Reason.PROMPT_CHANGED
    if (previousSize != state.previousSize + state.expectedDelta.coerceAtLeast(0)) {
      return Reason.SIZE_MISMATCH
    }
    return Reason.NONE
  }
}
