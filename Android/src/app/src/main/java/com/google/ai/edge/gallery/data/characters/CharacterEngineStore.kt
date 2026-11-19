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

/** Aplica de un [EnginePreset] lo que no necesita la base de datos: visión, rendimiento y muestreo. */
object EngineApply {
    fun applyBasics(context: Context, p: EnginePreset) {
        val perf = EnginePerfStore(context)
        var next = perf.load().overriddenBy(p)
        // El mmproj del preset siempre se aplica (vacío = quitar visión), salvo que el archivo ya no exista.
        if (p.mmproj.isBlank() || p.mmproj in MmprojFiles.list(context)) next = next.copy(mmproj = p.mmproj)
        perf.save(next)
        if (p.samplerMode.isNotBlank()) {
            SamplerSettingsStore(context).save(
                SamplerMode.fromId(p.samplerMode),
                p.samplerCustom.takeIf { it.isNotBlank() }?.let { SamplerSettings.decodeOrNull(it) },
            )
        }
    }
}
