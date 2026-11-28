package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Nodo del grafo de memoria: persona (T0), concepto/emoción (T1), el recuerdo en sí o el texto sin
 * procesar del que salió. La tabla ya existe desde la migración 7→8; no hace falta una nueva.
 */
@Entity(tableName = "memory_nodes")
data class MemoryNode(
    @PrimaryKey val id: String,
    val characterId: String,
    val label: String,
    val type: String,
    val topic: String,
)
