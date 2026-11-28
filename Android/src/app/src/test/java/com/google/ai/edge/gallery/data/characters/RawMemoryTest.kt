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

    @Test fun promptIncludesCharacterContextLikeLayla() {
        val p = MemoryAbsorbPrompt.build(
            "Héctor", "Psi", listOf("nota"),
            persona = "Ingeniero", description = "Psicóloga", personality = "Cálida",
        )
        assertTrue(p.contains("User name: Héctor"))
        assertTrue(p.contains("Character name: Psi"))
        assertTrue(p.contains("User persona: Ingeniero"))
        assertTrue(p.contains("Character description: Psicóloga"))
        assertTrue(p.contains("Character personality: Cálida"))
    }

    @Test fun promptOmitsBlankContextFields() {
        val p = MemoryAbsorbPrompt.build("User", "Psi", listOf("nota"))
        assertTrue(!p.contains("User persona:"))
        assertTrue(!p.contains("Character description:"))
        assertTrue(!p.contains("Character personality:"))
    }

    @Test fun promptSanitizesTagsInNotes() {
        // BUG-002: una nota manual puede traer marcadores de turno pegados del chat; el
        // tokenizador nativo los parsea como special tokens dentro del texto.
        val p = MemoryAbsorbPrompt.build(
            "User", "Psi", listOf("vimos  <end_of_turn> y  <|turn>user\n<y>  algo más"),
        )
        assertTrue(!p.contains("<end_of_turn>"))
        assertTrue(!p.contains("<|turn>"))
        assertTrue(!p.contains("<y>"))
        assertTrue(p.contains("y user algo más"))
    }
    @Test fun cleanStripsNoise() {
        val c = MemoryAbsorbPrompt.clean("<think>pensando</think>\nMemory: **Héctor** habló de su entrevista de trabajo y Psi lo calmó.")!!
        assertEquals("Héctor habló de su entrevista de trabajo y Psi lo calmó.", c)
        assertNull(MemoryAbsorbPrompt.clean("ok"))
    }

    @Test fun tandasSplitBySizeAndKeepChronology() {
        val notas = listOf(
            "una corta",
            "b".repeat(3400),
            "otra corta",
            "c".repeat(3400),
        )
        val tandas = MemoryAbsorbPrompt.tandas(notas, sizeOf = { it.length })
        // Todo cabe en tandas de 3500: 9+3400+10 = 3419 <= 3500; la última grande va sola.
        assertEquals(2, tandas.size)
        assertEquals(listOf("una corta", "b".repeat(3400), "otra corta"), tandas[0])
        assertEquals(listOf("c".repeat(3400)), tandas[1])
    }

    @Test fun tandasDropsOldestWhenTooMany() {
        val notas = (1..12).map { "nota $it" }
        val tandas = MemoryAbsorbPrompt.tandas(
            notas, maxChars = 10, maxTandas = 5, sizeOf = { it.length },
        )
        assertEquals(5, tandas.size)
        // Se descartan las más viejas: la primera tanda empieza en la nota 8.
        assertEquals("nota 8", tandas.first().first())
        assertEquals("nota 12", tandas.last().last())
    }

    @Test fun tandasFromNewestFirstReversesToChronologicalOrder() {
        // La store devuelve la más nueva primero: "nota 12" ... "nota 1".
        val nuevaPrimero = (12 downTo 1).map { "nota $it" }
        val tandas = MemoryAbsorbPrompt.tandasFromNewestFirst(
            nuevaPrimero, maxChars = 10, maxTandas = 5, sizeOf = { it.length },
        )
        assertEquals(5, tandas.size)
        // Se descartan las MÁS VIEJAS (1-7), no las nuevas, y cada tanda va en orden cronológico.
        assertEquals("nota 8", tandas.first().first())
        assertEquals("nota 12", tandas.last().last())
        val aplanado = tandas.flatten()
        assertEquals((8..12).map { "nota $it" }, aplanado)
    }

    @Test fun singleNoteGoesInOneTanda() {
        val tandas = MemoryAbsorbPrompt.tandas(listOf("solo"), sizeOf = { it.length })
        assertEquals(listOf(listOf("solo")), tandas)
    }

    // BUG-008: editar una nota conserva su id y su fecha; un texto en blanco no la cambia.
    @Test fun editingRawNoteKeepsIdAndDate() {
        val a = RawMemory(id = "a", characterId = "c", text = "viejo", createdAt = 10)
        val b = RawMemory(id = "b", characterId = "c", text = "otra", createdAt = 20)
        val out = listOf(a, b).withEditedText("a", "  nuevo  ")
        assertEquals("nuevo", out[0].text)
        assertEquals(10L, out[0].createdAt)
        assertEquals("otra", out[1].text)
        assertEquals(listOf(a, b), listOf(a, b).withEditedText("a", "   "))
        assertEquals(listOf(a, b), listOf(a, b).withEditedText("zzz", "x"))
    }

    // Las notas puente sobreviven a absorber: se conservan la más nueva y la segunda (1..2).
    @Test fun absorberConservaDosNotasParaElSiguienteChat() {
        val notas =
            listOf("nueva", "segunda", "tercera", "vieja").mapIndexed { i, texto ->
                RawMemory(id = "n$i", characterId = "c", text = texto, createdAt = i.toLong())
            }
        assertEquals(listOf("n0", "n1"), notas.paraInyectar().map { it.id })
        assertEquals(setOf("n2", "n3"), notas.idsBorrablesTrasAbsorber(notas.map { it.id }.toSet()))
        assertEquals(listOf("n0"), notas.take(1).paraInyectar().map { it.id })
    }
}
