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

    /** Todas las notas (para la copia de seguridad). */
    @Synchronized
    fun all(): List<RawMemory> = load()

    /** BUG-038: fusiona las de una copia (misma id = se sobrescribe; el resto se conserva). */
    @Synchronized
    fun merge(incoming: List<RawMemory>) {
        file.writeText(RawMemoryJson.encode(BackupFormat.mergeById(load(), incoming) { it.id }))
    }

    @Synchronized
    fun add(m: RawMemory) { file.writeText(RawMemoryJson.encode(load() + m)) }

    /** BUG-008: edita el texto de una nota conservando su fecha. */
    @Synchronized
    fun updateText(id: String, text: String) { file.writeText(RawMemoryJson.encode(load().withEditedText(id, text))) }

    @Synchronized
    fun remove(ids: Set<String>) { file.writeText(RawMemoryJson.encode(load().filterNot { it.id in ids })) }
}
