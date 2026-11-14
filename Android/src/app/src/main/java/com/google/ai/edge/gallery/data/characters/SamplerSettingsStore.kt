package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Guarda el modo de muestreo elegido y los valores personalizados (preferencias de la app). */
class SamplerSettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("roleplay_sampler", Context.MODE_PRIVATE)

  fun mode(): SamplerMode = SamplerMode.fromId(prefs.getString(KEY_MODE, null))

  fun custom(): SamplerSettings? = prefs.getString(KEY_CUSTOM, null)?.let { SamplerSettings.decodeOrNull(it) }

  /** Ajustes que se usan ahora mismo. */
  fun current(): SamplerSettings = SamplerPresets.resolve(mode(), custom()).sanitized()

  fun save(mode: SamplerMode, custom: SamplerSettings?) {
    prefs.edit().putString(KEY_MODE, mode.id).apply {
      if (custom != null) putString(KEY_CUSTOM, SamplerSettings.encode(custom.sanitized()))
    }.apply()
  }

  /** Presets con nombre creados por el usuario (nombre -> ajustes), en orden alfabético. */
  fun namedPresets(): Map<String, SamplerSettings> {
    val raw = prefs.getString(KEY_NAMED, null) ?: return emptyMap()
    return runCatching {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        o.entries
          .mapNotNull { (k, v) ->
            SamplerSettings.decodeOrNull((v as kotlinx.serialization.json.JsonPrimitive).content)?.let { k to it }
          }
          .sortedBy { it.first.lowercase() }
          .toMap()
      }
      .getOrDefault(emptyMap())
  }

  fun saveNamedPreset(name: String, settings: SamplerSettings) {
    val n = name.trim().take(40)
    if (n.isEmpty()) return
    writeNamed(namedPresets() + (n to settings.sanitized()))
  }

  fun deleteNamedPreset(name: String) = writeNamed(namedPresets() - name)

  private fun writeNamed(all: Map<String, SamplerSettings>) {
    val json =
      kotlinx.serialization.json.buildJsonObject {
        all.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(SamplerSettings.encode(v))) }
      }
    prefs.edit().putString(KEY_NAMED, json.toString()).apply()
  }

  private companion object {
    const val KEY_NAMED = "named_presets"
    const val KEY_MODE = "mode"
    const val KEY_CUSTOM = "custom"
  }
}
