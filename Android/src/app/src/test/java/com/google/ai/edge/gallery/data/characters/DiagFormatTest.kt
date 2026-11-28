package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagFormatTest {
    @Test fun motivoDeParada() {
        assertEquals("fin natural del modelo (EOS)", DiagFormat.stopReason(false, null, null, 100, 512, 300, 4096))
        assertTrue(DiagFormat.stopReason(false, null, null, 512, 512, 300, 4096).startsWith("TOPE"))
        assertTrue(DiagFormat.stopReason(false, null, null, 100, 512, 4050, 4096).startsWith("CONTEXTO LLENO"))
        assertTrue(DiagFormat.stopReason(false, null, "<|im_end|>", 100, 512, 300, 4096).contains("<|im_end|>"))
        assertTrue(DiagFormat.stopReason(true, null, null, 10, 512, 300, 4096).startsWith("CANCELADA"))
        assertTrue(DiagFormat.stopReason(false, "boom", null, 10, 512, 300, 4096).startsWith("ERROR"))
        // sin tope (-1) nunca se declara tope
        assertEquals("fin natural del modelo (EOS)", DiagFormat.stopReason(false, null, null, 9999, -1, 10, 4096))
    }

    @Test fun motivoDeParadaDelMotorNativo() {
        // BUG-029: el motivo que informa el nativo manda sobre la deducción por cuentas.
        assertEquals(
            "fin de turno del modelo (token EOG)",
            DiagFormat.stopReason(false, null, null, 100, -1, 300, 4096, "token"),
        )
        // Los marcadores de la plantilla son fin de turno del modelo, no un stop nuestro.
        assertEquals(
            "fin de turno del modelo (marcador «<turn|»)",
            DiagFormat.stopReason(false, null, null, 100, -1, 300, 4096, "stop:<turn|"),
        )
        // «###» sí es un stop propio: es el que se sospecha en BUG-029.
        assertEquals(
            "secuencia de parada propia «###» (la detectó el motor nativo)",
            DiagFormat.stopReason(false, null, null, 41, -1, 279, 4096, "stop:###"),
        )
        assertTrue(DiagFormat.stopReason(false, null, null, 40, -1, 63, 4096, "max_tokens").startsWith("TOPE"))
        // Cancelación y error siguen mandando sobre el motivo del motor.
        assertTrue(DiagFormat.stopReason(true, null, null, 10, -1, 300, 4096, "token").startsWith("CANCELADA"))
        assertTrue(DiagFormat.stopReason(false, "boom", null, 10, -1, 300, 4096, "token").startsWith("ERROR"))
        // Sin motivo nativo (motor viejo) se mantiene el texto de antes.
        assertEquals("fin natural del modelo (EOS)", DiagFormat.stopReason(false, null, null, 100, 512, 300, 4096, null))
        assertEquals(null, DiagFormat.describeNativeStop(""))
    }

    @Test fun recortaTextoLargoConservandoPrincipioYFinal() {
        val t = "A".repeat(100) + "Z".repeat(100)
        val c = DiagFormat.clip(t, 100)
        assertTrue(c.startsWith("A".repeat(50)))
        assertTrue(c.endsWith("Z".repeat(50)))
        assertTrue(c.contains("recortado: 100"))
        assertEquals("corto", DiagFormat.clip("corto", 100))
    }

    @Test fun renderIncluyeLoImportante() {
        val s = DiagFormat.render(
            DiagRecord("GGUF", "m.gguf", "ctx=4096", "plantilla Gemma 4", "<start_of_turn>user\nhola", "adiós", "TOPE", 50, 2f, 20, 4f, 300, 6000, 70, 4096),
            "2026-11-22 14:00:00",
        )
        assertTrue(s.contains("MOTIVO DE PARADA: TOPE"))
        assertTrue(s.contains("25.0 t/s"))
        assertTrue(s.contains("5.00 t/s"))
        assertTrue(s.contains("<start_of_turn>user\nhola"))
        assertTrue(s.contains("adiós"))
    }
}
