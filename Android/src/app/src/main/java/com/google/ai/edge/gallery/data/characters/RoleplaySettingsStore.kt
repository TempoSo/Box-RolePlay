package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Ajustes propios de Roleplay (no afectan a los chats normales de Box). */
class RoleplaySettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("roleplay_settings", Context.MODE_PRIVATE)

  /** Nombre del modelo con el que se abren los chats de personaje, o null si no se ha elegido. */
  fun defaultModel(): String? = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }

  fun setDefaultModel(name: String?) {
    prefs.edit().putString(KEY_MODEL, name ?: "").apply()
  }

  /** Si el personaje usa su memoria a largo plazo al abrir un chat nuevo (por defecto sí). */
  fun memoryEnabled(characterId: String): Boolean = prefs.getBoolean("memory_$characterId", true)

  fun setMemoryEnabled(characterId: String, enabled: Boolean) {
    prefs.edit().putBoolean("memory_$characterId", enabled).apply()
  }

  /** Si se guardan recuerdos automáticamente mientras chateas con este personaje (por defecto sí). */
  fun autoMemory(characterId: String): Boolean = prefs.getBoolean("automemory_$characterId", true)

  fun setAutoMemory(characterId: String, enabled: Boolean) {
    prefs.edit().putBoolean("automemory_$characterId", enabled).apply()
  }

  /** Cuántos recuerdos como máximo se inyectan por turno (antes eran los N más recientes). */
  fun memoryMaxInjections(): Int = prefs.getInt(KEY_MEMORY_MAX, 2).coerceIn(0, 20)

  fun setMemoryMaxInjections(n: Int) {
    prefs.edit().putInt(KEY_MEMORY_MAX, n.coerceIn(0, 20)).apply()
  }

  /** Ajustes de recuperación por turno, con los mismos nombres conceptuales que Layla. */
  fun memoryInjectionSettings(characterId: String = ""): MemoryInjectionSettings =
    MemoryInjectionSettings(
      enabled = if (characterId.isBlank()) true else memoryEnabled(characterId),
      maxInjections = memoryMaxInjections(),
      minScore = prefs.getFloat(KEY_MEMORY_MIN_SCORE, 0.60f).toDouble().coerceIn(0.0, 1.0),
      includeRelativeTime = prefs.getBoolean(KEY_MEMORY_RELATIVE_TIME, true),
      injectLastOnNewChat = prefs.getBoolean(KEY_MEMORY_LAST_NEW_CHAT, true),
      excludeActiveSession = prefs.getBoolean(KEY_MEMORY_EXCLUDE_ACTIVE, true),
    )

  fun setMinMemoryScore(value: Float) {
    prefs.edit().putFloat(KEY_MEMORY_MIN_SCORE, value.coerceIn(0f, 1f)).apply()
  }

  fun setIncludeRelativeTime(value: Boolean) {
    prefs.edit().putBoolean(KEY_MEMORY_RELATIVE_TIME, value).apply()
  }

  fun setInjectLastOnNewChat(value: Boolean) {
    prefs.edit().putBoolean(KEY_MEMORY_LAST_NEW_CHAT, value).apply()
  }

  fun setExcludeActiveSession(value: Boolean) {
    prefs.edit().putBoolean(KEY_MEMORY_EXCLUDE_ACTIVE, value).apply()
  }

  /** Cuántas entradas del lorebook (como máximo) se añaden al prompt por mensaje. */
  fun loreMaxEntries(): Int = prefs.getInt(KEY_LORE_MAX, 3).coerceIn(0, 20)

  fun setLoreMaxEntries(n: Int) {
    prefs.edit().putInt(KEY_LORE_MAX, n.coerceIn(0, 20)).apply()
  }

  /** Decodificación especulativa de LiteRT-LM (más rápida si el modelo la trae; por defecto no). */
  fun speculativeDecoding(): Boolean = prefs.getBoolean(KEY_SPECULATIVE, false)

  fun setSpeculativeDecoding(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SPECULATIVE, enabled).apply()
  }

  /**
   * Si el modelo cargado reportó soporte de decodificación especulativa
   * (`ModelInfo.Llm.hasSpeculativeDecodingSupport`). `null` = todavía no se ha comprobado.
   */
  fun speculativeSupported(modelName: String?): Boolean? {
    if (modelName.isNullOrBlank()) return null
    val key = KEY_SPECULATIVE_SUPPORTED_PREFIX + modelName
    return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
  }

  fun setSpeculativeSupported(modelName: String, supported: Boolean) {
    prefs.edit().putBoolean(KEY_SPECULATIVE_SUPPORTED_PREFIX + modelName, supported).apply()
  }

  private companion object {
    const val KEY_SPECULATIVE = "speculative_decoding"
    const val KEY_SPECULATIVE_SUPPORTED_PREFIX = "speculative_supported_"
    const val KEY_LORE_MAX = "lore_max_entries"
    const val KEY_MEMORY_MAX = "memory_max_injections"
    const val KEY_MEMORY_MIN_SCORE = "memory_min_score"
    const val KEY_MEMORY_RELATIVE_TIME = "memory_relative_time"
    const val KEY_MEMORY_LAST_NEW_CHAT = "memory_inject_last_new_chat"
    const val KEY_MEMORY_EXCLUDE_ACTIVE = "memory_exclude_active_session"
    const val KEY_MODEL = "default_model"
  }
}
