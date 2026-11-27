package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuationTest {
  @Test fun cortaPorLaUltimaFraseCompleta() {
    val texto = "Abrió la puerta sin hacer ruido. Dentro, la luz seguía encendida. Nadie respondió."
    assertEquals(
      "Abrió la puerta sin hacer ruido. Dentro, la luz seguía encendida.",
      Continuation.cutForContinuation(texto),
    )
  }

  @Test fun sinPuntuacionCortaPorPalabra() {
    val texto = "El narrador describió la habitación con todo detalle y luego dejó de hablar durante un buen rato"
    val recorte = Continuation.cutForContinuation(texto)!!
    assertTrue(recorte.length < texto.length)
    assertTrue(texto.startsWith(recorte))
    assertTrue(recorte.last() != ' ')
    assertEquals(' ', texto[recorte.length])
  }

  @Test fun textosCortosNoSeRecortan() {
    assertNull(Continuation.cutForContinuation("Muy corto."))
    assertNull(Continuation.cutForContinuation(""))
  }
}
