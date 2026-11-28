package com.google.ai.edge.gallery.ui.common.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreePolicyTest {
  @Test fun reiniciaCuandoElTurnoTermino() {
    assertTrue(
      HandsFreePolicy.debeReiniciar(
        activo = true,
        esperandoRespuesta = true,
        generando = false,
        hablando = false,
      )
    )
  }

  @Test fun noReiniciaMientrasElModeloGenera() {
    assertFalse(
      HandsFreePolicy.debeReiniciar(
        activo = true,
        esperandoRespuesta = true,
        generando = true,
        hablando = false,
      )
    )
  }

  @Test fun noReiniciaMientrasElTtsHabla() {
    assertFalse(
      HandsFreePolicy.debeReiniciar(
        activo = true,
        esperandoRespuesta = true,
        generando = false,
        hablando = true,
      )
    )
  }

  @Test fun noReiniciaSiNoEstaActivoONoHuboTranscripcion() {
    assertFalse(
      HandsFreePolicy.debeReiniciar(
        activo = false,
        esperandoRespuesta = true,
        generando = false,
        hablando = false,
      )
    )
    assertFalse(
      HandsFreePolicy.debeReiniciar(
        activo = true,
        esperandoRespuesta = false,
        generando = false,
        hablando = false,
      )
    )
  }
}
