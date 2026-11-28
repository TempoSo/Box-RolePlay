package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.MemoryEdge
import com.google.ai.edge.gallery.data.local.entities.MemoryNode

/**
 * Refuerzo de recuperación a partir del grafo: un recuerdo sube de prioridad cuando sus entidades
 * (personas, conceptos y emociones) coinciden con la consulta. Es puro y determinista: no lee la BD,
 * no llama al modelo y no cambia el texto que se inyecta en el prompt, solo la selección.
 */
object MemoryGraphScorer {
    /** Máximo de coincidencias que cuentan; dos ya bastan para dar el refuerzo completo. */
    private const val MAX_COINCIDENCIAS = 2

    fun boosts(
        nodes: List<MemoryNode>,
        edges: List<MemoryEdge>,
        consulta: String,
    ): Map<String, Double> {
        val terminos = MemoryRetriever.tokens(consulta)
        if (terminos.isEmpty()) return emptyMap()

        val porId = nodes.associateBy { it.id }
        val labelsPorRecuerdo = HashMap<String, MutableSet<String>>()
        edges.forEach { arista ->
            val memoria = arista.sourceId.takeIf { it.startsWith("m|") }?.removePrefix("m|") ?: return@forEach
            if (arista.relation !in setOf("about", "mentions")) return@forEach
            val nodo = porId[arista.targetId] ?: return@forEach
            if (nodo.type !in setOf("person", "concept", "emotion")) return@forEach
            labelsPorRecuerdo.getOrPut(memoria) { HashSet() }.add(nodo.label)
        }

        return labelsPorRecuerdo.mapValues { (_, labels) ->
            val tokensEntidades = labels.flatMap(MemoryRetriever::tokens).toSet()
            val coincidencias = terminos.toSet().intersect(tokensEntidades).size
            (coincidencias.toDouble() / MAX_COINCIDENCIAS).coerceIn(0.0, 1.0)
        }.filterValues { it > 0.0 }
    }
}
