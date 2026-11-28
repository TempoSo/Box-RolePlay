package com.google.ai.edge.gallery.ui.common.chat

/**
 * Regla pura del modo manos libres: solo se vuelve a escuchar cuando el turno anterior terminó
 * del todo, es decir, cuando el modelo ya no está generando y el TTS ya no está hablando.
 * Así una respuesta muy larga no se solapa con la escucha del siguiente mensaje.
 */
object HandsFreePolicy {
  fun debeReiniciar(
    activo: Boolean,
    esperandoRespuesta: Boolean,
    generando: Boolean,
    hablando: Boolean,
  ): Boolean = activo && esperandoRespuesta && !generando && !hablando
}
