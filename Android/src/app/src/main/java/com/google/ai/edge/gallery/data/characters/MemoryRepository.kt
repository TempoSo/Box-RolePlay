package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.dao.MemoryDao
import com.google.ai.edge.gallery.data.local.dao.MemoryGraphDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Roleplay: recuerdos a largo plazo por personaje. */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val graphDao: MemoryGraphDao,
) {
    fun observe(characterId: String): Flow<List<CharacterMemory>> = memoryDao.observe(characterId)

    suspend fun getAll(characterId: String): List<CharacterMemory> =
        withContext(Dispatchers.IO) { memoryDao.getAll(characterId) }

    suspend fun getForRetrieval(characterId: String): List<CharacterMemory> =
        withContext(Dispatchers.IO) { memoryDao.getForRetrieval(characterId) }

    /** Grafo completo del personaje, para la vista 2D y para el refuerzo de recuperación. */
    suspend fun grafo(characterId: String): MemoryGraphBuilder.Grafo =
        withContext(Dispatchers.IO) {
            MemoryGraphBuilder.Grafo(graphDao.nodes(characterId), graphDao.edges(characterId))
        }

    suspend fun graphBoosts(characterId: String, consulta: String): Map<String, Double> =
        withContext(Dispatchers.IO) {
            MemoryGraphScorer.boosts(graphDao.nodes(characterId), graphDao.edges(characterId), consulta)
        }

    suspend fun bumpAccess(ids: List<String>, now: Long) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) memoryDao.bumpAccess(ids, now)
    }

    suspend fun supersede(id: String, now: Long) = withContext(Dispatchers.IO) { memoryDao.markSuperseded(id, now) }

    /** Deja de inyectar los originales ya resumidos; se conservan en la BD y en la vista. */
    suspend fun markConsolidated(ids: List<String>, now: Long) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) memoryDao.markConsolidated(ids, now)
    }

    /** Refinamiento del grafo: quita los nodos y aristas de los recuerdos ya consolidados. */
    suspend fun refinarGrafo(characterId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        val nodos = MemoryConsolidation.nodosAEliminar(ids)
        if (nodos.isNotEmpty()) {
            graphDao.deleteEdgesFor(characterId, nodos)
            graphDao.deleteNodesFor(characterId, nodos)
        }
    }

    /**
     * Guarda un recuerdo si no hay otro con el mismo contenido normalizado para el personaje.
     * Devuelve false si estaba vacío o ya existía (dedupe on write).
     */
    suspend fun save(memory: CharacterMemory): Boolean = withContext(Dispatchers.IO) {
        val text = memory.content.trim()
        if (text.isEmpty()) return@withContext false
        val key = MemoryDedupe.key(text)
        if (key.isNotEmpty() && memoryDao.getAll(memory.characterId).any { MemoryDedupe.key(it.content) == key }) {
            return@withContext false
        }
        memoryDao.insert(memory.copy(content = text))
        true
    }

    /**
     * Guarda el recuerdo y, en el mismo momento, suma sus nodos y relaciones al grafo del
     * personaje. Si el recuerdo ya existía no se añade nada: los IDs del grafo son deterministas.
     */
    suspend fun saveStructured(
        memory: CharacterMemory,
        rawText: String,
        userName: String,
        charName: String,
        anteriorId: String = "",
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!save(memory)) return@withContext false
            deleteGraphOf(memory.characterId, memory.id)
            val grafo =
                MemoryGraphBuilder.construir(
                    characterId = memory.characterId,
                    memoria = memory,
                    rawText = rawText.ifBlank { memory.content },
                    userName = userName,
                    charName = charName,
                    anteriorId = anteriorId,
                )
            graphDao.insertNodes(grafo.nodes)
            graphDao.insertEdges(grafo.edges)
            true
        }

    private suspend fun deleteGraphOf(characterId: String, memoryId: String) {
        val ids = listOf("m|$memoryId", "r|$memoryId")
        graphDao.deleteEdgesFor(characterId, ids)
        graphDao.deleteNodesFor(characterId, ids)
    }

    suspend fun delete(memory: CharacterMemory) =
        withContext(Dispatchers.IO) {
            memoryDao.delete(memory)
            deleteGraphOf(memory.characterId, memory.id)
        }

    suspend fun deleteAll(characterId: String) =
        withContext(Dispatchers.IO) {
            memoryDao.deleteAll(characterId)
            graphDao.deleteEdges(characterId)
            graphDao.deleteNodes(characterId)
        }
}
