package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.ai.edge.gallery.data.local.entities.MemoryEdge
import com.google.ai.edge.gallery.data.local.entities.MemoryNode

/** Acceso al grafo incremental de cada personaje. Los IDs son deterministas, así que IGNORE basta. */
@Dao
interface MemoryGraphDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNodes(nodes: List<MemoryNode>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<MemoryEdge>)

    @Query("SELECT * FROM memory_nodes WHERE characterId = :characterId ORDER BY type, label")
    suspend fun nodes(characterId: String): List<MemoryNode>

    @Query("SELECT * FROM memory_edges WHERE characterId = :characterId")
    suspend fun edges(characterId: String): List<MemoryEdge>

    @Query("DELETE FROM memory_nodes WHERE characterId = :characterId")
    suspend fun deleteNodes(characterId: String)

    @Query("DELETE FROM memory_edges WHERE characterId = :characterId")
    suspend fun deleteEdges(characterId: String)

    /** Limpia el grafo de un recuerdo al editarlo o borrarlo, sin tocar el de los demás. */
    @Query(
        "DELETE FROM memory_edges WHERE characterId = :characterId " +
            "AND (sourceId IN (:ids) OR targetId IN (:ids))",
    )
    suspend fun deleteEdgesFor(characterId: String, ids: List<String>)

    @Query("DELETE FROM memory_nodes WHERE characterId = :characterId AND id IN (:ids)")
    suspend fun deleteNodesFor(characterId: String, ids: List<String>)
}
