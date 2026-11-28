package com.google.ai.edge.gallery.data.characters

import android.content.Context

/**
 * Ajustes de la app «Visión» (inspirados en Layla / MobileVLM).
 *
 * Solo afectan a la app de visión dedicada: el chat normal, el roleplay y las escenas no los usan.
 * La selección de modelo y de mmproj sigue viviendo en la configuración de inferencia
 * ([EnginePerfSettings.mmproj]); aquí solo se ajusta cómo se pide la descripción.
 */
data class VisionSettings(
  /**
   * Tokens máximos por descripción. -1 = sin límite. Por defecto 256, como Layla: acota el tiempo
   * de generación de la visión sin recortar descripciones normales.
   */
  val maxResponseTokens: Int = 256,
  /** Prompt del sistema de la app de visión (comportamiento del modelo). */
  val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
  /** Instrucción que se usa al enviar solo una imagen, sin escribir texto. */
  val imageInstruction: String = DEFAULT_IMAGE_INSTRUCTION,
) {
  fun sanitized(): VisionSettings =
    copy(
      maxResponseTokens = if (maxResponseTokens <= 0) -1 else maxResponseTokens.coerceAtMost(8192),
      systemPrompt = systemPrompt.take(MAX_PROMPT_CHARS),
      imageInstruction =
        imageInstruction.take(MAX_INSTRUCTION_CHARS).ifBlank { DEFAULT_IMAGE_INSTRUCTION },
    )

  companion object {
    const val MAX_PROMPT_CHARS = 4000
    const val MAX_INSTRUCTION_CHARS = 2000

    const val DEFAULT_SYSTEM_PROMPT =
      "Eres un asistente que describe y responde preguntas sobre imágenes con precisión y detalle."

    const val DEFAULT_IMAGE_INSTRUCTION = "Describe esta imagen en detalle."
  }
}

/** Almacén de [VisionSettings] en SharedPreferences (solo para la app de visión). */
class VisionStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE)

  fun load(): VisionSettings {
    val d = VisionSettings()
    return VisionSettings(
        maxResponseTokens = prefs.getInt("max_response_tokens", d.maxResponseTokens),
        systemPrompt = prefs.getString("system_prompt", d.systemPrompt) ?: d.systemPrompt,
        imageInstruction =
          prefs.getString("image_instruction", d.imageInstruction) ?: d.imageInstruction,
      )
      .sanitized()
  }

  /** Último modelo elegido en la pantalla de Visión. */
  fun lastModel(): String? = prefs.getString("last_model", null)

  fun setLastModel(name: String) {
    prefs.edit().putString("last_model", name).apply()
  }

  fun save(s: VisionSettings) {
    val v = s.sanitized()
    prefs
      .edit()
      .putInt("max_response_tokens", v.maxResponseTokens)
      .putString("system_prompt", v.systemPrompt)
      .putString("image_instruction", v.imageInstruction)
      .apply()
  }
}
