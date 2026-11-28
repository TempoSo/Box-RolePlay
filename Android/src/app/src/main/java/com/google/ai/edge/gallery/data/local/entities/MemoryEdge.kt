package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Arista del grafo de memoria. `relation` es la relación sujeto → objeto (siente, valida, about…)
 * y `linkType` separa las relaciones semánticas de las temporales y del texto de origen.
 */
@Entity(tableName = "memory_edges")
data class MemoryEdge(
    @PrimaryKey val id: String,
    val characterId: String,
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val linkType: String,
)
