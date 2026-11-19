package com.google.ai.edge.gallery.data.characters

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/**
 * Guarda las escenas en un archivo privado de la app.
 *
 * BUG-005: la lista y el editor son destinos de navegación distintos y cada uno recibe su propio
 * ViewModel, así que el estado no puede vivir en un `MutableStateFlow` de cada ViewModel. Este
 * store es un singleton de Hilt y es la única fuente observable: [scenarios] se recarga tras cada
 * `save`/`delete`, de modo que la lista se entera aunque el guardado lo haga el editor.
 */
@Singleton
class ScenarioStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "scenarios.json")
    private val lock = Any()

    @Volatile private var cached: List<Scenario> = emptyList()

    private val _scenarios = MutableStateFlow<List<Scenario>>(emptyList())

    /** Todas las escenas (más recientes primero). Se actualiza tras cada escritura. */
    val scenarios: StateFlow<List<Scenario>> = _scenarios.asStateFlow()

    init {
        reload()
    }

    fun all(): List<Scenario> = cached

    fun get(id: String): Scenario? = all().firstOrNull { it.id == id }

    fun save(s: Scenario) {
        synchronized(lock) {
            val next = all().filterNot { it.id == s.id } + s.copy(updatedAt = System.currentTimeMillis())
            file.writeText(ScenarioJson.encode(next))
        }
        reload()
    }

    fun delete(id: String) {
        synchronized(lock) {
            file.writeText(ScenarioJson.encode(all().filterNot { it.id == id }))
        }
        reload()
    }

    /** Vuelve a leer el archivo y publica el resultado en [scenarios]. */
    fun reload() {
        val list =
            synchronized(lock) {
                (if (file.exists()) ScenarioJson.decode(file.readText()) else emptyList())
                    .sortedByDescending { it.updatedAt }
            }
        cached = list
        _scenarios.value = list
    }
}
