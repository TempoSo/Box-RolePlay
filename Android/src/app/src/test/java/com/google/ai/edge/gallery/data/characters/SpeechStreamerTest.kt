package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechStreamerTest {
    private val text =
        "*Ella sonríe y te mira.* \"Hola, Héctor. Me alegra verte por aquí.\" Hoy es un buen día para empezar algo nuevo. " +
            "Sí. No. Vamos a caminar un rato por el parque, si te parece bien, y luego hablamos de todo lo que pasó ayer en el trabajo… ¿Qué opinas?"

    private fun streamed(scope: SpeechScope, step: Int): List<String> {
        val s = SpeechStreamer(scope)
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) { i = minOf(text.length, i + step); out += s.push(text.substring(0, i)) }
        out += s.finish(text)
        return out
    }

    @Test fun streamingEqualsWholeForAnyStepAndScope() {
        for (scope in SpeechScope.entries) {
            val whole = SpeechText.chunks(SpeechText.extract(text, scope))
            for (step in listOf(1, 3, 7, 25, 1000)) assertEquals("scope=$scope step=$step", whole, streamed(scope, step))
        }
    }
    @Test fun nothingBeforeFirstSentenceEnds() {
        assertTrue(SpeechStreamer(SpeechScope.ALL).push("Hola, esto aún no termina").isEmpty())
    }
    @Test fun lastChunkIsHeldBack() {
        val s = SpeechStreamer(SpeechScope.ALL)
        val a = "Esta es una primera frase bastante larga para que no se una con otra cosa. "
        val b = "Esta es la segunda frase, también larga, y por eso se queda separada de la primera. "
        assertTrue(s.push(a).isEmpty())
        assertEquals(1, s.push(a + b).size)
    }
}
