package com.google.ai.edge.gallery.data.characters

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class AutoMemoryTest {
    @Test fun keepsOriginalLanguageAndNames() {
        val m = AutoMemory.fromExchange("Héctor", "Psicólogo", "Hola, hoy me siento nervioso por el trabajo. Mañana tengo una entrevista.", "Entiendo, Héctor. **Respira hondo** 🌱 y cuéntame más. Estoy aquí.")!!
        assertTrue(m.startsWith("Héctor: Hola, hoy me siento nervioso"))
        assertTrue(m.contains("Psicólogo: Entiendo, Héctor. Respira hondo"))
        assertTrue(!m.contains("*") && !m.contains("🌱"))
    }
    @Test fun tinyExchangeIsSkipped() { assertNull(AutoMemory.fromExchange("A", "B", "ok", "si")) }
    @Test fun longTextIsCapped() {
        val long = "Frase larga número uno que sigue y sigue sin parar nunca jamás. ".repeat(10)
        val m = AutoMemory.fromExchange("A", "B", long, long)!!
        assertTrue(m.length < 420)
    }
    @Test fun duplicates() { assertTrue(AutoMemory.isDuplicate("Hola  Mundo", listOf("hola mundo"))) }
    @Test fun mergeKeepsNewest() {
        val r = AutoMemory.merge(listOf("a".repeat(500), "b".repeat(500), "c".repeat(500)), 1100)
        assertEquals("b".repeat(500) + "\n" + "c".repeat(500), r)
    }
}
