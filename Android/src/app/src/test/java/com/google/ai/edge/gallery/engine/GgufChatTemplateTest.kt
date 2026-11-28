package com.google.ai.edge.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufChatTemplateTest {

    @Test fun systemAndUserTurns() {
        val p = GgufChatTemplate.build("Eres Layla.", emptyList(), "Hola", thinking = false)
        assertEquals("<|turn>system\nEres Layla.<turn|>\n<|turn>user\nHola<turn|>\n<|turn>model\n", p)
    }

    @Test fun historyAlternatesAndAssistantFirstAddsEmptyUserTurn() {
        val p = GgufChatTemplate.build(
            "Sistema",
            listOf("assistant" to "Hola, soy Gwen.", "user" to "¿Qué tal?"),
            "Bien",
            thinking = false,
        )
        assertEquals(
            "<|turn>system\nSistema<turn|>\n" +
                "<|turn>user\n<turn|>\n" +
                "<|turn>model\nHola, soy Gwen.<turn|>\n" +
                "<|turn>user\n¿Qué tal?<turn|>\n" +
                "<|turn>user\nBien<turn|>\n" +
                "<|turn>model\n",
            p,
        )
    }

    @Test fun blankHistoryEntriesAreSkipped() {
        val p = GgufChatTemplate.build(
            "Sistema",
            listOf("user" to "  ", "assistant" to "respuesta", "user" to "otra"),
            "fin",
            thinking = false,
        )
        // El turno vacio de usuario no entra; el de asistente si (con su turno de usuario vacio
        // de alternancia delante).
        assertTrue(p.contains("<|turn>model\nrespuesta<turn|>\n"))
        assertTrue(p.contains("<|turn>user\notra<turn|>\n"))
        assertFalse(p.contains("<|turn>user\n  <turn|>\n"))
    }

    @Test fun thinkingInjectsThinkTokenInSystemTurn() {
        val p = GgufChatTemplate.build("Sistema", emptyList(), "Hola", thinking = true)
        assertTrue(p.startsWith("<|turn>system\n<|think|>\nSistema<turn|>\n"))
    }

    @Test fun thinkingWithoutSystemCreatesEmptySystemTurn() {
        val p = GgufChatTemplate.build("", emptyList(), "Hola", thinking = true)
        assertTrue(p.startsWith("<|turn>system\n<|think|>\n<turn|>\n"))
    }

    @Test fun noThinkingMeansNoThinkToken() {
        val p = GgufChatTemplate.build("", emptyList(), "Hola", thinking = false)
        assertFalse(p.contains("<|think|>"))
    }

    @Test fun gemma4DetectionByName() {
        assertTrue(GgufChatTemplate.isGemma4("gemma-4-E4B-PURE.gguf"))
        assertTrue(GgufChatTemplate.isGemma4("Gemma 4 E4B"))
        assertTrue(GgufChatTemplate.isGemma4("gemma4"))
        assertFalse(GgufChatTemplate.isGemma4("gemma-3n-E2B"))
        assertFalse(GgufChatTemplate.isGemma4("qwen2.5-7b-instruct"))
    }
}
