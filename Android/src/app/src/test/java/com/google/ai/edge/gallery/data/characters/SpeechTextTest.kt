package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    private val msg = "*Gwen te mira de reojo.* \"¿Algo? ¡No pasa nada!\" *Cruza los brazos.* “Idiota.”"

    @Test fun dialogueOnly() {
        assertEquals("¿Algo? ¡No pasa nada! Idiota.", SpeechText.extract(msg, SpeechScope.DIALOGUE_ONLY))
    }

    @Test fun allStripsAsterisks() {
        val t = SpeechText.extract(msg, SpeechScope.ALL)
        assertTrue(!t.contains("*"))
        assertTrue(t.contains("Gwen te mira de reojo."))
        assertTrue(t.contains("Cruza los brazos."))
    }

    @Test fun noQuotesGivesEmptyDialogue() {
        assertEquals("", SpeechText.extract("*Se va.*", SpeechScope.DIALOGUE_ONLY))
    }

    @Test fun stripsMarkdownLinksAndTags() {
        assertEquals("Hola mundo ok", SpeechText.stripFormatting("## Hola [mundo](http://x.y) <channel|> ok"))
    }

    @Test fun chunksBySentence() {
        // Las frases cortas se unen para no arrancar el motor de voz por cada una.
        assertEquals(listOf("Hola. ¿Qué tal? Bien!"), SpeechText.chunks("Hola. ¿Qué tal? Bien!"))
        // Las largas siguen separadas por frase.
        val a = "Esta es la primera frase, que es bastante larga para no unirse con otra."
        val b = "Y esta es la segunda frase, también lo bastante larga como para ir sola."
        assertEquals(listOf(a, b), SpeechText.chunks("$a $b", maxLen = 100))
    }

    @Test fun longSentenceIsCutNearComma() {
        val text = "uno dos tres cuatro, cinco seis siete ocho nueve diez once doce trece catorce quince"
        val c = SpeechText.chunks(text, maxLen = 40)
        assertTrue(c.all { it.length <= 40 })
        assertEquals(text.replace(Regex("\\s+"), " "), c.joinToString(" "))
    }

    @Test fun dropsPunctuationOnlyChunks() {
        assertEquals(listOf("Hola."), SpeechText.chunks("Hola. ... \n ?"))
    }
}
