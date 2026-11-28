package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.dao.MemoryDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Roleplay: recuerdos a largo plazo por personaje. */
@Singleton
class MemoryRepository @Inject constructor(private val memoryDao: MemoryDao) {
    fun observe(characterId: String): Flow<List<CharacterMemory>> = memoryDao.observe(characterId)

    suspend fun getAll(characterId: String): List<CharacterMemory> =
        withContext(Dispatchers.IO) { memoryDao.getAll(characterId) }

    suspend fun getForRetrieval(characterId: String): List<CharacterMemory> =
        withContext(Dispatchers.IO) { memoryDao.getForRetrieval(characterId) }

    suspend fun bumpAccess(ids: List<String>, now: Long) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) memoryDao.bumpAccess(ids, now)
    }

    suspend fun supersede(id: String, now: Long) = withContext(Dispatchers.IO) { memoryDao.markSuperseded(id, now) }

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

    suspend fun delete(memory: CharacterMemory) = withContext(Dispatchers.IO) { memoryDao.delete(memory) }

    suspend fun deleteAll(characterId: String) = withContext(Dispatchers.IO) { memoryDao.deleteAll(characterId) }
}
