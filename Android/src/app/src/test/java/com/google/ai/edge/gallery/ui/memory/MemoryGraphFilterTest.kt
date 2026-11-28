package com.google.ai.edge.gallery.ui.memory

import com.google.ai.edge.gallery.data.characters.MemoryGraphBuilder
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.MemoryNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphFilterTest {
    private val grafo =
        MemoryGraphBuilder.construir(
            characterId = "c",
            memoria = CharacterMemory(id = "m1", characterId = "c", content = "Héctor se siente tímido"),
            rawText = "Hector: siento timidez.\nClaire: valida tu miedo.",
            userName = "Hector",
            charName = "Claire",
            anteriorId = "m0",
        ).let { g ->
            // En una BD real el recuerdo anterior ya existe como nodo; aquí lo añadimos para probar.
            g.copy(nodes = g.nodes + MemoryNode("m|m0", "c", "Recuerdo anterior", "memory", ""))
        }

    @Test fun todoIncluyeRecuerdoTextoYEntidades() {
        val nodos = MemoryGraphFilter.nodos(grafo, FiltroGrafo.TODO)
        assertTrue(nodos.any { it.type == "person" })
        assertTrue(nodos.any { it.type == "memory" })
        assertTrue(nodos.any { it.type == "raw" })
        assertEquals(grafo.edges.size, MemoryGraphFilter.aristas(grafo, FiltroGrafo.TODO, nodos).size)
    }

    @Test fun t0MuestraPersonasYRecuerdos() {
        val nodos = MemoryGraphFilter.nodos(grafo, FiltroGrafo.T0)
        assertTrue(nodos.all { it.type in setOf("person", "memory") })
        assertTrue(nodos.any { it.type == "person" })
    }

    @Test fun t1MuestraConceptosYRecuerdos() {
        val nodos = MemoryGraphFilter.nodos(grafo, FiltroGrafo.T1)
        assertTrue(nodos.all { it.type in setOf("concept", "emotion", "memory") })
        assertTrue(nodos.any { it.type == "emotion" })
    }

    @Test fun temporalSoloMuestraLaCadenaDeRecuerdos() {
        val nodos = MemoryGraphFilter.nodos(grafo, FiltroGrafo.TEMPORAL)
        assertTrue(nodos.all { it.type == "memory" })
        val aristas = MemoryGraphFilter.aristas(grafo, FiltroGrafo.TEMPORAL, nodos)
        assertTrue(aristas.isNotEmpty())
        assertTrue(aristas.all { it.linkType == "temporal" })
    }
}
