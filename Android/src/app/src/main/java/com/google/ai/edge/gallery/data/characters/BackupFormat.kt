package com.google.ai.edge.gallery.data.characters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Qué partes de la app puede llevar una copia de seguridad (BUG-038). */
enum class BackupSection(val id: String, val label: String) {
    CHARACTERS("characters", "Personajes"),
    MEMORIES("memories", "Recuerdos"),
    PERSONAS("personas", "Personas"),
    PRESETS("presets", "Presets (motor y muestreo)"),
    SCENES("scenes", "Escenas"),
}

data class MemoryDto(val id: String, val characterId: String, val content: String, val createdAt: Long)

data class PersonaDto(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Formato y reglas de fusión de la copia de seguridad (lógica pura, sin Android).
 *
 * Reglas (BUG-038): **fusionar, no reemplazar**. Lo que trae la copia sobrescribe el elemento que
 * tenga el mismo id (o el mismo nombre en los presets de muestreo); lo que ya existe y no está en la
 * copia se conserva intacto; nunca se borra nada.
 */
object BackupFormat {
    const val VERSION = 2
    const val MANIFEST = "manifest.json"
    const val CHARACTERS_DIR = "characters/"
    const val MEMORIES = "memories.json"
    const val RAW_MEMORIES = "raw_memories.json"
    const val PERSONAS = "personas.json"
    const val ENGINE_PRESETS = "engine_presets.json"
    const val SAMPLER_PRESETS = "sampler_presets.json"
    const val SCENES = "scenes.json"

    private val KNOWN_FILES = setOf(MANIFEST, MEMORIES, RAW_MEMORIES, PERSONAS, ENGINE_PRESETS, SAMPLER_PRESETS, SCENES)

    /** Un `.zip` sin manifiesto es de la versión 1: solo tarjetas sueltas en la raíz. */
    fun isLegacy(entryNames: Collection<String>): Boolean = MANIFEST !in entryNames.map { it.trimStart('/') }

    /** Secciones que de verdad contiene el `.zip`, según los nombres de sus entradas. */
    fun sectionsOf(entryNames: Collection<String>): Set<BackupSection> {
        val names = entryNames.map { it.trimStart('/') }
        val out = linkedSetOf<BackupSection>()
        if (isLegacy(names)) {
            if (names.any { it.endsWith(".json") && it !in KNOWN_FILES }) out += BackupSection.CHARACTERS
            return out
        }
        if (names.any { characterId(it) != null }) out += BackupSection.CHARACTERS
        if (MEMORIES in names || RAW_MEMORIES in names) out += BackupSection.MEMORIES
        if (PERSONAS in names) out += BackupSection.PERSONAS
        if (ENGINE_PRESETS in names || SAMPLER_PRESETS in names) out += BackupSection.PRESETS
        if (SCENES in names) out += BackupSection.SCENES
        return out
    }

    fun characterEntry(id: String) = "$CHARACTERS_DIR$id.json"
    fun characterMetaEntry(id: String) = "$CHARACTERS_DIR$id.meta.json"
    fun characterAvatarEntry(id: String) = "$CHARACTERS_DIR$id.avatar.png"

    /** Id del personaje si la entrada es `characters/<id>.json` (no `.meta.json`). */
    fun characterId(entryName: String): String? {
        val n = entryName.trimStart('/')
        if (!n.startsWith(CHARACTERS_DIR) || !n.endsWith(".json") || n.endsWith(".meta.json")) return null
        val id = n.removePrefix(CHARACTERS_DIR).removeSuffix(".json")
        return id.takeIf { it.isNotEmpty() && '/' !in it }
    }

    fun manifest(sections: Collection<BackupSection>, createdAt: Long): String =
        buildJsonObject {
            put("app", "box-roleplay")
            put("version", VERSION)
            put("createdAt", createdAt)
            put("sections", buildJsonArray { sections.forEach { add(JsonPrimitive(it.id)) } })
        }.toString()

    // --- Fusión ---

    /** El elemento entrante sustituye al de mismo id en su sitio; los nuevos van al final; nada se borra. */
    fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> String): List<T> {
        val inc = incoming.associateBy(id)
        val replaced = existing.map { inc[id(it)] ?: it }
        val known = existing.map(id).toSet()
        return replaced + incoming.filter { id(it) !in known }.distinctBy(id)
    }

    fun <V> mergeByName(existing: Map<String, V>, incoming: Map<String, V>): Map<String, V> = existing + incoming

    /** Como [mergeById], pero la persona activa sigue siendo la que ya lo era (la de la copia solo se activa si no había ninguna). */
    fun mergePersonas(existing: List<PersonaDto>, incoming: List<PersonaDto>): List<PersonaDto> {
        val activeId = existing.firstOrNull { it.isActive }?.id ?: incoming.firstOrNull { it.isActive }?.id
        return mergeById(existing, incoming) { it.id }.map { it.copy(isActive = it.id == activeId) }
    }

    // --- JSON ---

    fun encodeMemories(list: List<MemoryDto>): String =
        buildJsonArray {
            list.forEach { m ->
                add(buildJsonObject {
                    put("id", m.id); put("characterId", m.characterId); put("content", m.content); put("createdAt", m.createdAt)
                })
            }
        }.toString()

    fun decodeMemories(text: String): List<MemoryDto> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                MemoryDto(
                    id = o.str("id") ?: return@mapNotNull null,
                    characterId = o.str("characterId") ?: return@mapNotNull null,
                    content = o.str("content") ?: return@mapNotNull null,
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())

    fun encodePersonas(list: List<PersonaDto>): String =
        buildJsonArray {
            list.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id); put("name", p.name); put("description", p.description)
                    put("isActive", p.isActive); put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
                })
            }
        }.toString()

    fun decodePersonas(text: String): List<PersonaDto> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                PersonaDto(
                    id = o.str("id") ?: return@mapNotNull null,
                    name = o.str("name") ?: return@mapNotNull null,
                    description = o.str("description") ?: "",
                    isActive = o["isActive"]?.jsonPrimitive?.booleanOrNull ?: false,
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    updatedAt = o["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())

    /** Presets de muestreo con nombre: nombre -> ajustes ya codificados. */
    fun encodeStringMap(map: Map<String, String>): String =
        buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString()

    fun decodeStringMap(text: String): Map<String, String> =
        runCatching {
            (Json.parseToJsonElement(text) as JsonObject).entries
                .mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }
                .toMap()
        }.getOrDefault(emptyMap())

    /** Metadatos de un personaje que no caben en la tarjeta (modelo propio y fechas). */
    fun encodeCharacterMeta(modelName: String, createdAt: Long, updatedAt: Long): String =
        buildJsonObject { put("modelName", modelName); put("createdAt", createdAt); put("updatedAt", updatedAt) }.toString()

    class CharacterMeta(val modelName: String, val createdAt: Long?, val updatedAt: Long?)

    fun decodeCharacterMeta(text: String): CharacterMeta? =
        runCatching {
            val o = Json.parseToJsonElement(text).jsonObject
            CharacterMeta(
                modelName = o.str("modelName") ?: "",
                createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull,
                updatedAt = o["updatedAt"]?.jsonPrimitive?.longOrNull,
            )
        }.getOrNull()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
