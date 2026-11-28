package com.google.ai.edge.gallery.tts

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Una voz guardada de la biblioteca: su muestra vive en `tts/voices/<id>.wav`. */
data class Voice(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val durationMs: Long = 0L,
)

/** Lógica pura de la biblioteca de voces (BUG-041): nombres, búsqueda, JSON y migración. */
object VoiceLibrary {
    val FIXED_TAGS = listOf("hombre", "mujer", "narrador", "personaje", "calmado", "enérgico", "grave", "suave", "robótico", "cálido")

    /** «Mi voz.wav» -> «Mi voz» (el nombre propuesto al subir un archivo). */
    fun nameFromFileName(fileName: String?): String {
        val base = fileName.orEmpty().trim().substringAfterLast('/').substringBeforeLast('.', fileName.orEmpty().trim().substringAfterLast('/'))
        return base.trim().ifBlank { "Voz" }
    }

    /** Evita dos voces con el mismo nombre: «Ana», «Ana (2)», «Ana (3)»… */
    fun uniqueName(existing: List<Voice>, wanted: String): String {
        val base = wanted.trim().ifBlank { "Voz" }
        val taken = existing.map { it.name.lowercase(Locale.ROOT) }.toSet()
        if (base.lowercase(Locale.ROOT) !in taken) return base
        var n = 2
        while ("$base ($n)".lowercase(Locale.ROOT) in taken) n++
        return "$base ($n)"
    }

    fun cleanTags(tags: Collection<String>): List<String> =
        tags.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

    /** Busca por nombre o etiqueta (sin distinguir mayúsculas); varias palabras = todas deben aparecer. */
    fun search(list: List<Voice>, query: String): List<Voice> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { v ->
            val hay = (listOf(v.name) + v.tags).joinToString(" ").lowercase(Locale.ROOT)
            words.all { it in hay }
        }
    }

    fun withVoice(list: List<Voice>, v: Voice): List<Voice> = list.filterNot { it.id == v.id } + v

    fun without(list: List<Voice>, id: String): List<Voice> = list.filterNot { it.id == id }

    /** «0:10», «1:05». */
    fun formatDuration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 500) / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /**
     * Migración: los WAV sueltos de versiones anteriores (`default.wav`, `char_<id>.wav`) que aún no
     * están en la biblioteca pasan a ser voces con nombre. Devuelve (id, nombre) de cada uno.
     */
    fun legacyVoices(knownIds: Set<String>, fileStems: List<String>): List<Pair<String, String>> =
        fileStems
            .filter { it !in knownIds && (it == "default" || it.startsWith("char_")) }
            .sorted()
            .map { stem ->
                stem to
                    if (stem == "default") "Predeterminada"
                    else "Voz de personaje " + stem.removePrefix("char_").take(4)
            }

    fun encode(list: List<Voice>): String =
        buildJsonArray {
            list.forEach { v ->
                add(
                    buildJsonObject {
                        put("id", v.id)
                        put("name", v.name)
                        put("createdAt", v.createdAt)
                        put("durationMs", v.durationMs)
                        put("tags", buildJsonArray { v.tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    }
                )
            }
        }.toString()

    fun decode(text: String): List<Voice> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Voice(
                    id = id,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Voz",
                    tags = o["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    durationMs = o["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
}
