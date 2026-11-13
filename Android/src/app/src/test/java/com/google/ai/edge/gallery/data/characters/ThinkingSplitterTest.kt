package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingSplitterTest {
    private fun run(vararg pieces: String, limit: Int = 12000): Pair<String, String> {
        val s = ThinkingSplitter(thoughtLimit = limit)
        val answer = StringBuilder()
        val thought = StringBuilder()
        for (p in pieces) {
            val o = s.feed(p)
            answer.append(o.answer)
            o.thought?.let { thought.append(it) }
        }
        val f = s.finish()
        answer.append(f.answer)
        f.thought?.let { thought.append(it) }
        return answer.toString() to thought.toString()
    }

    @Test fun answerStreamsLiveFromFirstToken() {
        val s = ThinkingSplitter()
        assertEquals("Hola", s.feed("Hola").answer)
        assertEquals(" mundo", s.feed(" mundo").answer)
    }

    @Test fun leadingWhitespaceIsHeldUntilDecided() {
        val s = ThinkingSplitter()
        assertTrue(s.feed("\n").isEmpty)
        assertEquals("\nHola", s.feed("Hola").answer)
    }

    @Test fun nativeThoughtBlockIsSplit() {
        val (a, t) = run("<|channel>", "thought\n", "Analizo la entrada.", "<channel|>", "Gwen te mira.")
        assertEquals("Gwen te mira.", a)
        assertEquals("Analizo la entrada.", t)
    }

    @Test fun longThoughtNeverLeaksIntoAnswer() {
        val thought = "Analyze the input. ".repeat(125) // ~2400 chars
        val pieces = listOf("<|channel>", "thought\n") + thought.chunked(8) + listOf("<channel|>", "Hola, Héctor.")
        val (a, t) = run(*pieces.toTypedArray())
        assertEquals("Hola, Héctor.", a)
        assertEquals(thought.trim(), t)
    }

    @Test fun emptyThoughtChannelGivesNullThought() {
        val s = ThinkingSplitter()
        s.feed("<|channel>")
        s.feed("thought\n")
        val o = s.feed("<channel|>Respuesta")
        assertNull(o.thought)
        assertEquals("Respuesta", o.answer)
    }

    @Test fun unclosedThoughtIsReleasedOnFinish() {
        val (a, t) = run("<|channel>", "thought\n", "sin cerrar")
        assertEquals("sin cerrar", a)
        assertEquals("", t)
    }

    @Test fun safetyLimitReleasesRunawayThought() {
        val (a, _) = run("<|channel>", "x".repeat(50), limit = 20)
        assertTrue(a.isNotEmpty())
    }

    @Test fun strayMarkersAreRemovedFromAnswer() {
        val (a, _) = run("Hola", "<channel|>", " mundo")
        assertEquals("Hola mundo", a)
    }

    @Test fun appliesOnlyToGemma4() {
        assertTrue(ThinkingSplitter.appliesTo("gemma-4-E4B-PURE.gguf"))
        assertFalse(ThinkingSplitter.appliesTo("llama-3.2-3b.gguf"))
        assertFalse(ThinkingSplitter.appliesTo("gemma-3n-E2B"))
    }
}
