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

    suspend fun save(memory: CharacterMemory) = withContext(Dispatchers.IO) {
        val text = memory.content.trim()
        if (text.isNotEmpty()) memoryDao.insert(memory.copy(content = text))
    }

    suspend fun delete(memory: CharacterMemory) = withContext(Dispatchers.IO) { memoryDao.delete(memory) }

    suspend fun deleteAll(characterId: String) = withContext(Dispatchers.IO) { memoryDao.deleteAll(characterId) }

    /**
     * "Absorber": junta los recuerdos viejos en uno solo y deja intactos los [keepRecent] más
     * recientes. Devuelve cuántos recuerdos se fusionaron (0 si no había nada que juntar).
     */
    suspend fun consolidate(characterId: String, keepRecent: Int = 5): Int = withContext(Dispatchers.IO) {
        val old = memoryDao.getAll(characterId).drop(keepRecent) // de nuevo a viejo
        if (old.size < 2) return@withContext 0
        val merged = AutoMemory.merge(old.asReversed().map { it.content })
        old.forEach { memoryDao.delete(it) }
        memoryDao.insert(CharacterMemory(characterId = characterId, content = merged, createdAt = old.first().createdAt))
        old.size
    }
}
