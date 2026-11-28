package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Roleplay: un recuerdo a largo plazo de un personaje. `content` sigue siendo el resumen editable;
 * el resto de campos permiten recuperarlo por relevancia en lugar de volcar los más recientes.
 */
@Entity(tableName = "character_memories", indices = [Index("characterId"), Index("characterId", "valid")])
data class CharacterMemory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** fact / preference / impression / session. Lo derivado nunca sustituye al canon de la tarjeta. */
    val kind: String = "fact",
    /** Clúster temático del grafo; en recuerdos nuevos se deriva del texto al guardar. */
    val topic: String = "",
    /** user / character / derived. */
    val source: String = "derived",
    /** Para no recuperar como recuerdo lo que acaba de pasar en el chat abierto (anti-eco). */
    val sourceConversationId: String = "",
    /** JSON de palabras clave; si está vacío se derivan del contenido al recuperar. */
    val keysJson: String = "[]",
    /** 1..10, por defecto 5. */
    val importance: Int = 5,
    val accessCount: Int = 0,
    val lastAccess: Long = 0,
    val updatedAt: Long = 0,
    /** Un hecho obsoleto se marca inválido, no se borra. */
    val valid: Boolean = true,
    /** Id del recuerdo que este dejó sin efecto. */
    val supersedesId: String = "",
)
