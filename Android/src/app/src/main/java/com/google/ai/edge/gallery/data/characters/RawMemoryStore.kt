package com.google.ai.edge.gallery.data.characters

import android.content.Context
import java.io.File

/** Archivo privado con los recuerdos sin procesar (no toca la base de datos cifrada). */
class RawMemoryStore(context: Context) {
    private val file = File(context.filesDir, "raw_memories.json")

    @Synchronized
    private fun load(): List<RawMemory> = if (file.exists()) RawMemoryJson.decode(file.readText()) else emptyList()

    @Synchronized
    fun forCharacter(characterId: String): List<RawMemory> =
        load().filter { it.characterId == characterId }.sortedByDescending { it.createdAt }

    @Synchronized
    fun add(m: RawMemory) { file.writeText(RawMemoryJson.encode(load() + m)) }

    @Synchronized
    fun remove(ids: Set<String>) { file.writeText(RawMemoryJson.encode(load().filterNot { it.id in ids })) }
}
