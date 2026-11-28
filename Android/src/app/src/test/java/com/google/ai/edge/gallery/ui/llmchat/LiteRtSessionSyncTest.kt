package com.google.ai.edge.gallery.ui.llmchat

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtSessionSyncTest {
  private val instance = Any()
  private val engine = Any()

  private fun state(
    prompt: String = "personaje",
    previousSize: Int = 5,
    expectedDelta: Int = 2,
    dropped: Int = 0,
    inst: Any = instance,
  ) = LiteRtSessionSync.State(inst, prompt, previousSize, expectedDelta, dropped)

  private fun reason(
    previous: LiteRtSessionSync.State?,
    prompt: String = "personaje",
    previousSize: Int = 7,
    cancelled: Boolean = false,
    extraDrop: Int = 0,
    inst: Any = instance,
  ) =
    LiteRtSessionSync.reason(
      previous = previous,
      instance = inst,
      prompt = prompt,
      previousSize = previousSize,
      cancelled = cancelled,
      extraDrop = extraDrop,
      hasPromptOrHistory = prompt.isNotBlank() || previousSize > 0,
    )

  @Test
  fun reutilizaLaConversacionCuandoElHistorialCreceComoSeEsperaba() {
    assertEquals(LiteRtSessionSync.Reason.NONE, reason(state()))
  }

  @Test
  fun unTurnoSinMensajeDeUsuarioNoFuerzaUnReset() {
    // Escenas / Aleatorio: el turno anterior sólo añadió la burbuja del asistente.
    val st = state(previousSize = 5, expectedDelta = 1)
    assertEquals(LiteRtSessionSync.Reason.NONE, reason(st, previousSize = 6))
  }

  @Test
  fun unCambioInesperadoDeTamanoSiFuerzaUnReset() {
    val st = state(previousSize = 5, expectedDelta = 2)
    assertEquals(LiteRtSessionSync.Reason.SIZE_MISMATCH, reason(st, previousSize = 6))
  }

  @Test
  fun elPrimerTurnoDeUnChatConHistorialSiembraLaConversacion() {
    assertEquals(LiteRtSessionSync.Reason.FIRST_TURN, reason(null))
  }

  @Test
  fun unChatVaciaSinPromptNoNecesitaSembrarNada() {
    assertEquals(
      LiteRtSessionSync.Reason.NONE,
      LiteRtSessionSync.reason(
        previous = null,
        instance = instance,
        prompt = "",
        previousSize = 0,
        cancelled = false,
        extraDrop = 0,
        hasPromptOrHistory = false,
      ),
    )
  }

  @Test
  fun unNuevoMotorSiempreRecreaLaConversacion() {
    assertEquals(LiteRtSessionSync.Reason.NEW_INSTANCE, reason(state(), inst = engine))
  }

  @Test
  fun cambiarElPromptDelPersonajeRecreaLaConversacion() {
    assertEquals(LiteRtSessionSync.Reason.PROMPT_CHANGED, reason(state(), prompt = "otro personaje"))
  }

  @Test
  fun cancelarORecortarLaVentanaRecreaLaConversacion() {
    assertEquals(LiteRtSessionSync.Reason.CANCELLED, reason(state(), cancelled = true))
    assertEquals(LiteRtSessionSync.Reason.WINDOW_DROP, reason(state(), extraDrop = 2))
  }

  @Test
  fun unaCancelacionGanaSobreElRestoDeMotivos() {
    assertEquals(
      LiteRtSessionSync.Reason.CANCELLED,
      reason(state(), cancelled = true, extraDrop = 2, prompt = "otro personaje"),
    )
  }
}
