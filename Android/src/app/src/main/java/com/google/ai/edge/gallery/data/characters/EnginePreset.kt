package com.google.ai.edge.gallery.data.characters

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * "Motor guardado": un perfil con lo que se elige en Configuración de inferencia (modelo,
 * proyector de visión, persona, muestreo y rendimiento) para cambiar de combinación con un toque.
 * Campos vacíos = "no cambiar" al aplicarlo.
 *
 * BUG-018: los campos de rendimiento son anulables: `null` significa "no guardado" (presets
 * antiguos) y al aplicarlos se deja el valor actual. Así los perfiles escritos por versiones
 * anteriores siguen cargando y no pisan los ajustes del teléfono.
 */
data class EnginePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val model: String = "",
    val mmproj: String = "",
    val personaId: String = "",
    val samplerMode: String = "",
    val samplerCustom: String = "",
    val threads: Int? = null,
    val nBatch: Int? = null,
    val nUBatch: Int? = null,
    val flashAttn: Int? = null,
    val kvType: Int? = null,
    val noMmap: Boolean? = null,
    val repack: Boolean? = null,
    val contextLength: Int? = null,
    val maxResponse: Int? = null,
    val imageMaxSide: Int? = null,
)

object EnginePresetJson {
    fun encode(list: List<EnginePreset>): String =
        buildJsonArray {
            list.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id); put("name", p.name); put("model", p.model)
                    put("mmproj", p.mmproj); put("personaId", p.personaId)
                    put("samplerMode", p.samplerMode); put("samplerCustom", p.samplerCustom)
                    // Solo se escribe lo guardado: un valor ausente se decodifica como "no cambiar".
                    p.threads?.let { put("threads", it) }
                    p.nBatch?.let { put("nBatch", it) }
                    p.nUBatch?.let { put("nUBatch", it) }
                    p.flashAttn?.let { put("flashAttn", it) }
                    p.kvType?.let { put("kvType", it) }
                    p.noMmap?.let { put("noMmap", it) }
                    p.repack?.let { put("repack", it) }
                    p.contextLength?.let { put("contextLength", it) }
                    p.maxResponse?.let { put("maxResponse", it) }
                    p.imageMaxSide?.let { put("imageMaxSide", it) }
                })
            }
        }.toString()

    fun decode(text: String): List<EnginePreset> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""
                fun i(k: String) = o[k]?.jsonPrimitive?.intOrNull
                fun b(k: String) = o[k]?.jsonPrimitive?.booleanOrNull
                if (s("id").isEmpty() || s("name").isEmpty()) null
                else EnginePreset(
                    id = s("id"),
                    name = s("name"),
                    model = s("model"),
                    mmproj = s("mmproj"),
                    personaId = s("personaId"),
                    samplerMode = s("samplerMode"),
                    samplerCustom = s("samplerCustom"),
                    threads = i("threads"),
                    nBatch = i("nBatch"),
                    nUBatch = i("nUBatch"),
                    flashAttn = i("flashAttn"),
                    kvType = i("kvType"),
                    noMmap = b("noMmap"),
                    repack = b("repack"),
                    contextLength = i("contextLength"),
                    maxResponse = i("maxResponse"),
                    imageMaxSide = i("imageMaxSide"),
                )
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
