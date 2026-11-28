package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM character_memories WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun observe(characterId: String): Flow<List<CharacterMemory>>

    @Query("SELECT * FROM character_memories WHERE characterId = :characterId ORDER BY createdAt DESC")
    suspend fun getAll(characterId: String): List<CharacterMemory>

    /** Recuperación: solo hechos vigentes; los obsoletos se conservan pero no se inyectan. */
    @Query("SELECT * FROM character_memories WHERE characterId = :characterId AND valid = 1 ORDER BY createdAt DESC")
    suspend fun getForRetrieval(characterId: String): List<CharacterMemory>

    /** Refuerzo por uso de los recuerdos que entraron en el prompt. */
    @Query("UPDATE character_memories SET accessCount = accessCount + 1, lastAccess = :now WHERE id IN (:ids)")
    suspend fun bumpAccess(ids: List<String>, now: Long)

    /** Un hecho nuevo deja sin efecto al anterior sin borrarlo del historial. */
    @Query("UPDATE character_memories SET valid = 0, updatedAt = :now WHERE id = :id")
    suspend fun markSuperseded(id: String, now: Long)

    /** Consolidación: varios recuerdos se resumen en uno; los originales dejan de inyectarse. */
    @Query("UPDATE character_memories SET valid = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun markConsolidated(ids: List<String>, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: CharacterMemory)

    @Delete
    suspend fun delete(memory: CharacterMemory)

    @Query("DELETE FROM character_memories WHERE characterId = :characterId")
    suspend fun deleteAll(characterId: String)
}
