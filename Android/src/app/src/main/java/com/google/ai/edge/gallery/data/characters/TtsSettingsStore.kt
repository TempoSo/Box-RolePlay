package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Ajustes de voz (TTS). La voz de cada personaje se guarda aparte, sin tocar la base de datos. */
class TtsSettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("roleplay_tts", Context.MODE_PRIVATE)

  /** Leer en voz alta cada respuesta nueva al terminar de generarse. */
  fun autoRead(): Boolean = prefs.getBoolean("auto_read", false)

  fun setAutoRead(v: Boolean) = prefs.edit().putBoolean("auto_read", v).apply()

  /** Empezar a hablar con la primera frase completa, sin esperar a que termine la respuesta. */
  fun streamWhileGenerating(): Boolean = prefs.getBoolean("stream_live", false)

  fun setStreamWhileGenerating(v: Boolean) = prefs.edit().putBoolean("stream_live", v).apply()

  fun scope(): SpeechScope =
    runCatching { SpeechScope.valueOf(prefs.getString("scope", null) ?: "") }.getOrDefault(SpeechScope.ALL)

  fun setScope(s: SpeechScope) = prefs.edit().putString("scope", s.name).apply()

  fun speed(): Float = prefs.getFloat("speed", 1.0f).coerceIn(0.5f, 2.0f)

  fun setSpeed(v: Float) = prefs.edit().putFloat("speed", v.coerceIn(0.5f, 2.0f)).apply()

  /** Pasos de difusión: menos = más rápido, más = mejor calidad. */
  fun numSteps(): Int = prefs.getInt("num_steps", 3).coerceIn(1, 10)

  fun setNumSteps(v: Int) = prefs.edit().putInt("num_steps", v.coerceIn(1, 10)).apply()

  /** Ruta del WAV de la voz predeterminada (copiado a la app), o null. */
  fun defaultVoicePath(): String? = prefs.getString("default_voice", null)?.takeIf { it.isNotBlank() }

  fun setDefaultVoicePath(p: String?) = prefs.edit().putString("default_voice", p ?: "").apply()

  fun characterVoicePath(characterId: String): String? =
    prefs.getString("voice_$characterId", null)?.takeIf { it.isNotBlank() }

  fun setCharacterVoicePath(characterId: String, p: String?) =
    prefs.edit().putString("voice_$characterId", p ?: "").apply()

  /** Voz de Kokoro por defecto (número de voz); -1 = automática (Dora si el modelo es multilingüe). */
  fun kokoroDefaultSid(): Int = prefs.getInt("kokoro_sid", -1)

  fun setKokoroDefaultSid(sid: Int) = prefs.edit().putInt("kokoro_sid", sid).apply()

  /** Voz de Supertonic por defecto (sid dentro de voice.bin). */
  fun supertonicDefaultSid(): Int = prefs.getInt("supertonic_sid", 0).coerceAtLeast(0)

  fun setSupertonicDefaultSid(sid: Int) = prefs.edit().putInt("supertonic_sid", sid.coerceAtLeast(0)).apply()

  /** Idioma de Supertonic. Se toma del dispositivo si es uno de los que trae el modelo. */
  fun supertonicLang(): String {
    val saved = prefs.getString("supertonic_lang", null)?.lowercase().orEmpty()
    if (saved.isNotBlank()) return saved
    val device = java.util.Locale.getDefault().language.lowercase()
    return if (device in SUPERTONIC_LANGS) device else "en"
  }

  fun setSupertonicLang(lang: String) = prefs.edit().putString("supertonic_lang", lang.lowercase()).apply()

  companion object {
    /** Idiomas confirmados en el modelo Supertonic de 5 idiomas; otros modelos pueden aceptar más. */
    val SUPERTONIC_LANGS = setOf("es", "en", "fr", "pt", "ko")
  }
}
