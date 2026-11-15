package com.google.ai.edge.gallery.data.characters

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * "Motor guardado": un perfil con lo que se elige en Configuración de inferencia (modelo,
 * proyector de visión, persona y muestreo) para cambiar de combinación con un toque.
 * Campos vacíos = "no cambiar" al aplicarlo.
 */
data class EnginePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val model: String = "",
    val mmproj: String = "",
    val personaId: String = "",
    val samplerMode: String = "",
    val samplerCustom: String = "",
)

object EnginePresetJson {
    fun encode(list: List<EnginePreset>): String =
        buildJsonArray {
            list.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id); put("name", p.name); put("model", p.model)
                    put("mmproj", p.mmproj); put("personaId", p.personaId)
                    put("samplerMode", p.samplerMode); put("samplerCustom", p.samplerCustom)
                })
            }
        }.toString()

    fun decode(text: String): List<EnginePreset> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""
                if (s("id").isEmpty() || s("name").isEmpty()) null
                else EnginePreset(s("id"), s("name"), s("model"), s("mmproj"), s("personaId"), s("samplerMode"), s("samplerCustom"))
            }
        }.getOrDefault(emptyList())

    /** Añade o reemplaza (mismo id, o mismo nombre sin importar mayúsculas) y ordena por nombre. */
    fun upsert(list: List<EnginePreset>, p: EnginePreset): List<EnginePreset> {
        val name = p.name.trim().take(40)
        if (name.isEmpty()) return list
        val clean = p.copy(name = name)
        return (list.filterNot { it.id == clean.id || it.name.equals(name, ignoreCase = true) } + clean)
            .sortedBy { it.name.lowercase() }
    }
}
