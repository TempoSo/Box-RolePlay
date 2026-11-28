package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.MemoryEdge
import com.google.ai.edge.gallery.data.local.entities.MemoryNode

/**
 * Convierte cada recuerdo, en el momento de guardarlo, en nodos y relaciones del grafo del
 * personaje. Es determinista y no llama al modelo: la estructura mínima útil (quién, qué, emoción y
 * texto de origen) nace con el recuerdo y el grafo se va acumulando con cada entrada.
 */
object MemoryGraphBuilder {
    data class Grafo(val nodes: List<MemoryNode>, val edges: List<MemoryEdge>)

    private const val MAX_ENTIDADES = 6
    private const val MAX_TRIPLAS = 4

    // El tokenizador quita tildes, así que las claves van sin acento.
    private val EMOCIONES =
        setOf(
            "timidez", "miedo", "alegria", "tristeza", "ira", "amor", "odio", "confianza",
            "inseguridad", "curiosidad", "orgullo", "culpa", "rabia", "calma", "ansiedad",
            "deseo", "esperanza",
        )
    private val VERBOS =
        mapOf(
            "siente" to "feels", "valida" to "validates", "explica" to "explains",
            "ensena" to "instruct", "instruye" to "instruct", "dice" to "says",
            "prefiere" to "prefers", "recuerda" to "remembers", "quiere" to "wants",
            "teme" to "fears", "ama" to "loves", "odia" to "hates", "trabaja" to "works",
            "vive" to "lives", "promete" to "promises", "confia" to "trusts",
        )

    /** Tema corto derivado del texto, para no dejar la columna `topic` vacía en memorias nuevas. */
    fun sugerirTopic(rawText: String): String =
        MemoryRetriever.tokens(rawText)
            .sortedByDescending { it.length }
            .firstOrNull()
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()
            .take(60)

    fun construir(
        characterId: String,
        memoria: CharacterMemory,
        rawText: String,
        userName: String,
        charName: String,
        anteriorId: String = "",
    ): Grafo {
        val texto = rawText.trim()
        val userLabel = userName.ifBlank { "User" }
        val charLabel = charName.ifBlank { "Character" }
        val nodes = LinkedHashMap<String, MemoryNode>()
        val edges = LinkedHashMap<String, MemoryEdge>()

        fun node(n: MemoryNode) {
            nodes.putIfAbsent(n.id, n)
        }

        fun edge(sourceId: String, relation: String, targetId: String, linkType: String) {
            val id = "a|$sourceId|$relation|$targetId|$linkType"
            edges.putIfAbsent(id, MemoryEdge(id, characterId, sourceId, targetId, relation, linkType))
        }

        // El recuerdo y su texto sin procesar son dos nodos distintos, unidos por derived_from.
        val memNode = "m|${memoria.id}"
        node(MemoryNode(memNode, characterId, memoria.content.trim().take(80), "memory", memoria.topic))
        val rawNode = "r|${memoria.id}"
        if (texto.isNotEmpty()) {
            node(MemoryNode(rawNode, characterId, texto.take(1200), "raw", ""))
            edge(memNode, "derived_from", rawNode, "raw")
        }

        val userNode = "e|$characterId|person|${MemoryRetriever.normaliza(userLabel)}"
        val charNode = "e|$characterId|person|${MemoryRetriever.normaliza(charLabel)}"
        node(MemoryNode(userNode, characterId, userLabel, "person", "T0"))
        node(MemoryNode(charNode, characterId, charLabel, "person", "T0"))

        val entidades = LinkedHashMap<String, String>()
        MemoryRetriever.tokens(texto)
            .sortedByDescending { it.length }
            .take(MAX_ENTIDADES)
            .forEach { token ->
                val tipo = if (token in EMOCIONES) "emotion" else "concept"
                val id = "e|$characterId|$tipo|$token"
                node(MemoryNode(id, characterId, token, tipo, "T1"))
                entidades[token] = id
            }

        edge(memNode, "mentions", userNode, "semantic")
        edge(memNode, "mentions", charNode, "semantic")
        entidades.values.forEach { edge(memNode, "about", it, "semantic") }

        // Tripletas sujeto → relación → objeto detectadas en cada línea del intercambio.
        var triples = 0
        texto.lineSequence().take(8).forEach { linea ->
            val norm = MemoryRetriever.normaliza(linea)
            if (norm.isBlank() || triples >= MAX_TRIPLAS) return@forEach
            val speaker =
                when {
                    MemoryRetriever.normaliza(userLabel).let { it.isNotEmpty() && norm.startsWith(it) } -> userNode
                    MemoryRetriever.normaliza(charLabel).let { it.isNotEmpty() && norm.startsWith(it) } -> charNode
                    else -> return@forEach
                }
            entidades.keys
                .filter { it in EMOCIONES && norm.contains(it) }
                .take(2)
                .forEach { token ->
                    if (triples < MAX_TRIPLAS) {
                        edge(speaker, "siente", entidades.getValue(token), "semantic")
                        triples++
                    }
                }
            VERBOS.forEach { (token, relation) ->
                if (triples >= MAX_TRIPLAS || !norm.contains(token)) return@forEach
                val objeto =
                    when (speaker) {
                        userNode -> charNode
                        charNode -> userNode
                        else -> entidades.values.firstOrNull { it != speaker }
                    } ?: return@forEach
                edge(speaker, relation, objeto, "semantic")
                triples++
            }
        }

        // Cadena temporal entre versiones del mismo tema: permite el filtro "Temporal" del grafo.
        if (anteriorId.isNotBlank()) edge("m|$anteriorId", "next", memNode, "temporal")

        return Grafo(nodes.values.toList(), edges.values.toList())
    }
}
