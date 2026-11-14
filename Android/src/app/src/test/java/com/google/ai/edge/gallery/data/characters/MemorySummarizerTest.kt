package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySummarizerTest {
    private val msgs = listOf(
        true to "Hola. Llegamos a la ciudad de Quito con Marta el 12 de mayo.",
        false to "Me alegra verte de nuevo. Marta sonríe y te entrega la llave de la torre.",
        true to "ok",
    )

    @Test fun emptyGivesEmpty() = assertEquals("", MemorySummarizer.summarize(emptyList(), 500))

    @Test fun keepsNamesAndHeader() {
        val s = MemorySummarizer.summarize(msgs, 500)
        assertTrue(s.startsWith(MemorySummarizer.HEADER))
        assertTrue(s.contains("Quito"))
        assertTrue(s.contains("Marta"))
    }

    @Test fun respectsBudget() {
        val many = (1..200).map { (it % 2 == 0) to "Evento número $it en la aldea de Cuenca con Pedro." }
        val s = MemorySummarizer.summarize(many, 300)
        assertTrue(ContextWindowPlanner.estimateTokens(s) <= 330)
        assertTrue(s.contains("200"))
    }

    @Test fun deterministic() =
        assertEquals(MemorySummarizer.summarize(msgs, 500), MemorySummarizer.summarize(msgs, 500))

    @Test fun withSummaryAppends() {
        assertEquals("P", MemorySummarizer.withSummary("P", ""))
        assertTrue(MemorySummarizer.withSummary("P", "S").endsWith("S"))
    }
}
