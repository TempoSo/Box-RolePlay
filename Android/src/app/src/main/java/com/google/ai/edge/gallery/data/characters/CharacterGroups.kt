package com.google.ai.edge.gallery.data.characters

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Grupo de personajes ("Asistentes", "Fantasía"…). Un personaje puede estar en varios. */
data class CharacterGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val characterIds: List<String> = emptyList(),
)

/** Operaciones puras sobre la lista de grupos y su JSON. */
object CharacterGroups {
    fun encode(list: List<CharacterGroup>): String =
        buildJsonArray {
            list.forEach { g ->
                add(buildJsonObject {
                    put("id", g.id); put("name", g.name)
                    put("characterIds", buildJsonArray { g.characterIds.forEach { add(JsonPrimitive(it)) } })
                })
            }
        }.toString()

    fun decode(text: String): List<CharacterGroup> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CharacterGroup(id, name, o["characterIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList())
            }
        }.getOrDefault(emptyList())

    /** Crea un grupo; si ya hay uno con ese nombre (sin importar mayúsculas) no hace nada. */
    fun create(list: List<CharacterGroup>, name: String): List<CharacterGroup> {
        val n = name.trim().take(30)
        if (n.isEmpty() || list.any { it.name.equals(n, ignoreCase = true) }) return list
        return (list + CharacterGroup(name = n)).sortedBy { it.name.lowercase() }
    }

    fun delete(list: List<CharacterGroup>, groupId: String): List<CharacterGroup> = list.filterNot { it.id == groupId }

    fun setMember(list: List<CharacterGroup>, groupId: String, characterId: String, member: Boolean): List<CharacterGroup> =
        list.map { g ->
            if (g.id != groupId) g
            else g.copy(characterIds = if (member) (g.characterIds - characterId) + characterId else g.characterIds - characterId)
        }

    fun groupIdsOf(list: List<CharacterGroup>, characterId: String): Set<String> =
        list.filter { characterId in it.characterIds }.map { it.id }.toSet()

    /** Quita de los grupos a los personajes que ya no existen. */
    fun prune(list: List<CharacterGroup>, existing: Set<String>): List<CharacterGroup> =
        list.map { g -> g.copy(characterIds = g.characterIds.filter { it in existing }) }
}
