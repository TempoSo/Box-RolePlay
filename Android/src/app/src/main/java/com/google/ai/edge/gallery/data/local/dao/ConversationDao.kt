package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.ai.edge.gallery.data.local.entities.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversationsSync(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE taskType = :taskType ORDER BY updatedAt DESC")
    fun getConversationsByTask(taskType: String): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE characterId = :characterId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForCharacter(characterId: String): Conversation?

    @Query("SELECT COUNT(*) FROM conversations WHERE characterId = :characterId")
    suspend fun countForCharacter(characterId: String): Int

    @Query("SELECT COALESCE(SUM(messageCount), 0) FROM conversations WHERE characterId = :characterId")
    suspend fun messagesForCharacter(characterId: String): Int

    @Query("SELECT * FROM conversations WHERE modelName = :modelName ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForModel(modelName: String): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
