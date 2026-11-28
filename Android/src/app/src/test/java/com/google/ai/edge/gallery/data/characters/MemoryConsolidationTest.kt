package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationTest {
    private val ahora = 1_800_000_000_000L
    private val dia = 24L * 60 * 60 * 1000

    private fun vieja(
        id: String,
        topic: String = "escuela",
        dias: Long = 10,
        importance: Int = 5,
        valid: Boolean = true,
        kind: String = "fact",
    ) =
        CharacterMemory(
            id = id,
            characterId = "c",
            content = "Recuerdo sobre $id en la escuela de Claire",
            createdAt = ahora - dias * dia,
            topic = topic,
            keysJson = "[\"$id\"]",
            importance = importance,
            valid = valid,
            kind = kind,
        )

    @Test fun cohortesSoloAgrupaRecuerdosVigentesAntiguosYSuficientes() {
        val memorias =
            listOf(
                vieja("a"),
                vieja("b"),
                vieja("c"),
                vieja("d", dias = 2), // demasiado reciente
                vieja("e", valid = false), // ya invalidado
                vieja("f", kind = "summary"), // ya consolidado
                vieja("g", topic = "trabajo"), // tema distinto
                vieja("h", topic = "trabajo"),
                vieja("i", topic = "trabajo"),
            )
        val cohortes = MemoryConsolidation.cohortes(memorias, ahora)
        assertEquals(2, cohortes.size)
        assertEquals(listOf("a", "b", "c"), cohortes.first().map { it.id })
        assertEquals(
            listOf("g", "h", "i"),
            cohortes.last().map { it.id },
        )
    }

    @Test fun cohortesRespetaIdempotenciaYCantidadMinima() {
        val pocas = listOf(vieja("a"), vieja("b"), vieja("x", kind = "summary"))
        assertTrue(MemoryConsolidation.cohortes(pocas, ahora).isEmpty())
        assertTrue(MemoryConsolidation.cohortes(pocas, ahora, minItems = 2).size == 1)
    }

    @Test fun unirConservaMetadatosDeterministasYAntiEco() {
        val cohorte = listOf(vieja("a", importance = 5), vieja("b", importance = 9, dias = 8))
        val extraido =
            MemoryAbsorbPrompt.RecuerdoExtraido(
                resumen = "Claire recuerda la escuela y a sus amigos con cariño, y guarda una promesa importante.",
                topic = "escuela",
                keys = listOf("escuela", "promesa"),
                importance = 4,
            )
        val nuevo = MemoryConsolidation.unir(cohorte, extraido, ahora)!!
        assertEquals("summary", nuevo.kind)
        assertEquals("derived", nuevo.source)
        assertEquals("", nuevo.sourceConversationId)
        assertEquals("escuela", nuevo.topic)
        assertEquals(9, nuevo.importance)
        assertEquals("b", nuevo.supersedesId)
        assertEquals(ahora, nuevo.createdAt)
        assertTrue(MemoryConsolidation.claves(nuevo).contains("promesa"))
        assertTrue(MemoryConsolidation.claves(nuevo).contains("a"))
    }

    @Test fun unirRechazaSalidaVacia() {
        val cohorte = listOf(vieja("a"), vieja("b"), vieja("c"))
        val extraido = MemoryAbsorbPrompt.RecuerdoExtraido(resumen = "   ")
        assertNull(MemoryConsolidation.unir(cohorte, extraido, ahora))
    }

    @Test fun planSoloInvalidaSiSeGuardo() {
        val cohorte = listOf(vieja("a"), vieja("b"), vieja("c"))
        val nuevo = vieja("nuevo").copy(kind = "summary")
        assertNull(MemoryConsolidation.plan(nuevo, cohorte, guardado = false))
        val plan = MemoryConsolidation.plan(nuevo, cohorte, guardado = true)!!
        assertEquals(listOf("a", "b", "c"), plan.idsAInvalidar)
        assertFalse(plan.idsAInvalidar.contains(nuevo.id))
    }

    @Test fun nodosAEliminarLimpiaMemoriaYRelaciones() {
        assertEquals(
            listOf("m|a", "r|a", "m|b", "r|b"),
            MemoryConsolidation.nodosAEliminar(listOf("a", "b")),
        )
        assertTrue(MemoryConsolidation.nodosAEliminar(emptyList()).isEmpty())
    }

    @Test fun promptIncluyeLosRecuerdosEnOrdenYSinEtiquetas() {
        val cohorte = listOf(vieja("a"), vieja("b").copy(content = "<think>secreto</think>Texto limpio sobre Claire"))
        val prompt = MemoryConsolidation.prompt(cohorte, "Héctor", "Claire")
        assertTrue(prompt.indexOf("Recuerdo sobre a") < prompt.indexOf("Texto limpio"))
        assertFalse(prompt.contains("<think>"))
        assertFalse(prompt.contains("secreto"))
    }
}
