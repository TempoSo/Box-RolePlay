package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphBuilderTest {
    private val memoria = CharacterMemory(id = "mem-1", characterId = "c", content = "Héctor se siente tímido")

    @Test fun cadaRecuerdoAportaNodosYElTextoDeOrigen() {
        val grafo =
            MemoryGraphBuilder.construir(
                characterId = "c",
                memoria = memoria,
                rawText = "Hector: siento timidez.\nClaire: valida tu miedo.",
                userName = "Hector",
                charName = "Claire",
            )
        val tipos = grafo.nodes.map { it.type }.toSet()
        assertTrue(tipos.contains("memory"))
        assertTrue(tipos.contains("raw"))
        assertTrue(tipos.contains("person"))
        assertTrue(grafo.nodes.any { it.type == "emotion" && it.label == "timidez" && it.topic == "T1" })
        assertTrue(grafo.edges.any { it.relation == "derived_from" && it.linkType == "raw" })
        assertTrue(grafo.edges.any { it.relation == "mentions" })
    }

    @Test fun extraeTripletasSujetoRelacionObjeto() {
        val grafo =
            MemoryGraphBuilder.construir(
                characterId = "c",
                memoria = memoria,
                rawText = "Hector: siento timidez.\nClaire: valida tu miedo.",
                userName = "Hector",
                charName = "Claire",
            )
        val emocion = grafo.nodes.first { it.label == "timidez" }.id
        val hector = grafo.nodes.first { it.label == "Hector" }.id
        val claire = grafo.nodes.first { it.label == "Claire" }.id
        assertTrue(grafo.edges.any { it.sourceId == hector && it.relation == "siente" && it.targetId == emocion })
        assertTrue(grafo.edges.any { it.sourceId == claire && it.relation == "validates" && it.targetId == hector })
    }

    @Test fun idsDeterministasNoDuplicanNodosAlRepetir() {
        val args =
            listOf("c", "Hector", "Claire")
        fun build() =
            MemoryGraphBuilder.construir(
                characterId = args[0],
                memoria = memoria,
                rawText = "Hector: timidez y curiosidad.",
                userName = args[1],
                charName = args[2],
            )
        val a = build()
        val b = build()
        assertEquals(a.nodes.map { it.id }.toSet(), b.nodes.map { it.id }.toSet())
        assertEquals(a.edges.map { it.id }.toSet(), b.edges.map { it.id }.toSet())
        assertEquals(a.nodes.size, a.nodes.map { it.id }.toSet().size)
    }

    @Test fun enlazaLaVersionAnteriorConAristaTemporal() {
        val grafo =
            MemoryGraphBuilder.construir(
                characterId = "c",
                memoria = memoria,
                rawText = "Hector: habló de su trabajo.",
                userName = "Hector",
                charName = "Claire",
                anteriorId = "mem-0",
            )
        assertTrue(
            grafo.edges.any {
                it.sourceId == "m|mem-0" && it.targetId == "m|mem-1" &&
                    it.relation == "next" && it.linkType == "temporal"
            },
        )
    }

    @Test fun temaDerivadoDelTexto() {
        assertEquals("Timidez", MemoryGraphBuilder.sugerirTopic("Hector habla de timidez y miedo"))
    }
}
