package com.google.ai.edge.gallery.data.characters

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/** Archivo privado con los grupos de personajes (no toca la base de datos cifrada). */
class CharacterGroupStore(context: Context) {
    private val file = File(context.filesDir, "character_groups.json")

    @Synchronized
    fun all(): List<CharacterGroup> = if (file.exists()) CharacterGroups.decode(file.readText()) else emptyList()

    @Synchronized
    fun write(list: List<CharacterGroup>) {
        file.writeText(CharacterGroups.encode(list))
        changes.value = changes.value + 1
    }

    fun create(name: String) = write(CharacterGroups.create(all(), name))
    fun delete(id: String) = write(CharacterGroups.delete(all(), id))
    fun setMember(groupId: String, characterId: String, member: Boolean) =
        write(CharacterGroups.setMember(all(), groupId, characterId, member))

    companion object {
        /** Sube cada vez que cambian los grupos, para que la biblioteca se refresque. */
        val changes = MutableStateFlow(0)
    }
}
