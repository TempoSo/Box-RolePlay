package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPolishTest {
    @Test fun emojiRemoved() =
        assertEquals("Hola mundo", SpeechText.stripFormatting("Hola 😀 mundo ❤️").replace("  ", " ").trim())

    @Test fun repeatedPunctuation() {
        assertEquals("No! Vamos?", SpeechText.stripFormatting("No!!! Vamos???"))
        assertEquals("Bien...", SpeechText.stripFormatting("Bien......"))
    }

    @Test fun shortSentencesMerge() {
        val c = SpeechText.chunks("Hola. Sí. Claro que sí, amigo mío. Adiós.")
        assertEquals(1, c.size)
    }

    @Test fun longStaysSplit() {
        val long = (1..10).joinToString(" ") { "Esta es una frase bastante larga número $it para probar." }
        val c = SpeechText.chunks(long)
        assertTrue(c.size > 1)
        assertTrue(c.all { it.length <= 220 })
        assertFalse(c.any { it.isBlank() })
    }
}
