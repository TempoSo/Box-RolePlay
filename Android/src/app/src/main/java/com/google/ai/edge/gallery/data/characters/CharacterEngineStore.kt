package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Qué motor guardado ([EnginePreset]) usa cada personaje ("" = el de Configuración de inferencia). */
class CharacterEngineStore(context: Context) {
    private val prefs = context.getSharedPreferences("character_engine", Context.MODE_PRIVATE)

    fun get(characterId: String): String = prefs.getString(characterId, "") ?: ""

    fun set(characterId: String, presetId: String) {
        prefs.edit().apply { if (presetId.isBlank()) remove(characterId) else putString(characterId, presetId) }.apply()
    }
}

/** Aplica de un [EnginePreset] lo que no necesita la base de datos: visión y muestreo. */
object EngineApply {
    fun applyBasics(context: Context, p: EnginePreset) {
        if (p.mmproj.isBlank() || p.mmproj in MmprojFiles.list(context)) {
            val perf = EnginePerfStore(context)
            perf.save(perf.load().copy(mmproj = p.mmproj))
        }
        if (p.samplerMode.isNotBlank()) {
            SamplerSettingsStore(context).save(
                SamplerMode.fromId(p.samplerMode),
                p.samplerCustom.takeIf { it.isNotBlank() }?.let { SamplerSettings.decodeOrNull(it) },
            )
        }
    }
}
