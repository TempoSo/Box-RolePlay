package com.google.ai.edge.gallery.data.characters

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Una escena de roleplay: nombre, prompt, personajes participantes y opciones. */
data class Scenario(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val prompt: String = "",
    val tags: List<String> = emptyList(),
    /** Ids de personajes de la biblioteca. */
    val participantIds: List<String> = emptyList(),
    val narrator: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Codificación JSON de las escenas (sin Room: no toca la base de datos cifrada). */
object ScenarioJson {
    fun encode(list: List<Scenario>): String =
        buildJsonArray {
            list.forEach { s ->
                add(
                    buildJsonObject {
                        put("id", s.id)
                        put("name", s.name)
                        put("prompt", s.prompt)
                        put("tags", buildJsonArray { s.tags.forEach { add(JsonPrimitive(it)) } })
                        put("participants", buildJsonArray { s.participantIds.forEach { add(JsonPrimitive(it)) } })
                        put("narrator", s.narrator)
                        put("updatedAt", s.updatedAt)
                    }
                )
            }
        }.toString()

    fun decode(text: String): List<Scenario> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                fun strings(key: String) =
                    (o[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                Scenario(
                    id = id,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    prompt = o["prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                    tags = strings("tags"),
                    participantIds = strings("participants"),
                    narrator = o["narrator"]?.jsonPrimitive?.boolean ?: true,
                    updatedAt = o["updatedAt"]?.jsonPrimitive?.long ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
}

/** Guarda las escenas en un archivo privado de la app. */
class ScenarioStore(context: Context) {
    private val file = File(context.filesDir, "scenarios.json")

    @Synchronized
    fun all(): List<Scenario> =
        (if (file.exists()) ScenarioJson.decode(file.readText()) else emptyList())
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun get(id: String): Scenario? = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(s: Scenario) {
        val next = all().filterNot { it.id == s.id } + s.copy(updatedAt = System.currentTimeMillis())
        file.writeText(ScenarioJson.encode(next))
    }

    @Synchronized
    fun delete(id: String) {
        file.writeText(ScenarioJson.encode(all().filterNot { it.id == id }))
    }
}
