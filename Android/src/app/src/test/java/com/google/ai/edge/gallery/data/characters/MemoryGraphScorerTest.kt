package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphScorerTest {
    private val memoria = CharacterMemory(id = "mem-1", characterId = "c", content = "Héctor se siente tímido")

    private fun grafo() =
        MemoryGraphBuilder.construir(
            characterId = "c",
            memoria = memoria,
            rawText = "Hector: siento timidez.\nClaire: valida tu miedo.",
            userName = "Hector",
            charName = "Claire",
        )

    @Test fun consultaCoincidenteRefuerzaElRecuerdo() {
        val g = grafo()
        val boosts = MemoryGraphScorer.boosts(g.nodes, g.edges, "timidez")
        assertTrue(boosts["mem-1"] ?: 0.0 > 0.0)
    }

    @Test fun consultaAjenaNoDaRefuerzo() {
        val g = grafo()
        assertTrue(MemoryGraphScorer.boosts(g.nodes, g.edges, "avión").isEmpty())
    }

    @Test fun dosCoincidenciasDanElRefuerzoCompleto() {
        val g = grafo()
        val boosts = MemoryGraphScorer.boosts(g.nodes, g.edges, "timidez miedo")
        assertEquals(1.0, boosts["mem-1"] ?: 0.0, 0.0001)
    }

    @Test fun elGrafoSoloAfinaLaSeleccionSinCambiarFirmaNiBloque() {
        val a =
            CharacterMemory(
                id = "a", characterId = "c", content = "Café",
                keysJson = "[\"cafe\"]", createdAt = 1_000L,
            )
        val b = a.copy(id = "b")
        val opciones =
            mapOf(
                "memorias" to listOf(a, b),
                "nombre" to "Ana",
                "consulta" to "cafe",
                "ahora" to 2_000L,
                "conversacion" to "",
            )
        @Suppress("UNCHECKED_CAST")
        fun seleccionar(boosts: Map<String, Double>) =
            MemoryRetriever.seleccionar(
                memorias = opciones["memorias"] as List<CharacterMemory>,
                nombrePersonaje = opciones["nombre"] as String,
                textoConsulta = opciones["consulta"] as String,
                now = opciones["ahora"] as Long,
                conversacionActual = opciones["conversacion"] as String,
                ajustes = MemoryInjectionSettings(maxInjections = 1),
                boostsPorId = boosts,
            )
        val sinGrafo = seleccionar(emptyMap())
        val conGrafo = seleccionar(mapOf("b" to 1.0))
        assertEquals(listOf("a"), sinGrafo.ids)
        assertEquals(listOf("b"), conGrafo.ids)
        assertEquals(sinGrafo.bloque, conGrafo.bloque)
        assertEquals("a|0", sinGrafo.firma)
        assertEquals("b|0", conGrafo.firma)
    }
}
