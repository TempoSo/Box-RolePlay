package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawMemoryTest {
    @Test fun jsonRoundTrip() {
        val l = listOf(RawMemory("1", "c", "Héctor: hola \"x\"\nPsi: ok", 5L), RawMemory("2", "d", "t", 9L))
        assertEquals(l, RawMemoryJson.decode(RawMemoryJson.encode(l)))
        assertTrue(RawMemoryJson.decode("basura").isEmpty())
    }
    @Test fun promptKeepsNewestWhenTooLong() {
        val notes = listOf("viejo " + "a".repeat(5000), "medio " + "b".repeat(5000), "nuevo final")
        val p = MemoryAbsorbPrompt.build("Héctor", "Psi", notes)
        assertTrue(p.contains("nuevo final")); assertTrue(p.contains("medio")); assertTrue(!p.contains("viejo"))
        assertTrue(p.contains("Héctor") && p.endsWith("Memory:"))
    }
    @Test fun cleanStripsNoise() {
        val c = MemoryAbsorbPrompt.clean("<think>pensando</think>\nMemory: **Héctor** habló de su entrevista de trabajo y Psi lo calmó.")!!
        assertEquals("Héctor habló de su entrevista de trabajo y Psi lo calmó.", c)
        assertNull(MemoryAbsorbPrompt.clean("ok"))
    }
}
