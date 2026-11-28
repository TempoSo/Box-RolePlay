package com.google.ai.edge.gallery.data.characters

import android.content.Context
import java.io.File

/** Archivo privado con los motores guardados (no toca la base de datos cifrada). */
class EnginePresetStore(context: Context) {
    private val file = File(context.filesDir, "engine_presets.json")

    @Synchronized
    fun all(): List<EnginePreset> = if (file.exists()) EnginePresetJson.decode(file.readText()) else emptyList()

    @Synchronized
    fun save(p: EnginePreset) { file.writeText(EnginePresetJson.encode(EnginePresetJson.upsert(all(), p))) }

    @Synchronized
    fun delete(id: String) { file.writeText(EnginePresetJson.encode(all().filterNot { it.id == id })) }
}
