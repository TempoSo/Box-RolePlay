package com.google.ai.edge.gallery.tts

import java.io.File

/**
 * Referencia a una voz guardada en los ajustes (por defecto o de un personaje). Una muestra de Pocket TTS
 * es la ruta de su .wav; una voz de Kokoro es «kokoro:N»; una de Supertonic es «supertonic:N» (con
 * idioma opcional «supertonic:N@es»). Lógica pura.
 */
object TtsVoiceRef {
  private const val KOKORO = "kokoro:"
  private const val SUPERTONIC = "supertonic:"

  fun kokoro(sid: Int): String = "$KOKORO$sid"

  fun kokoroSid(ref: String?): Int? =
    ref?.takeIf { it.startsWith(KOKORO) }?.removePrefix(KOKORO)?.toIntOrNull()?.takeIf { it >= 0 }

  fun isKokoro(ref: String?): Boolean = kokoroSid(ref) != null

  fun supertonic(sid: Int, lang: String = ""): String =
    "$SUPERTONIC$sid" + if (lang.isBlank()) "" else "@${lang.lowercase()}"

  fun supertonicSid(ref: String?): Int? =
    ref?.takeIf { it.startsWith(SUPERTONIC) }
      ?.removePrefix(SUPERTONIC)
      ?.substringBefore('@')
      ?.toIntOrNull()
      ?.takeIf { it >= 0 }

  /** Idioma incrustado en la referencia (opcional); null si no se especificó. */
  fun supertonicLang(ref: String?): String? =
    ref?.takeIf { it.startsWith(SUPERTONIC) && it.contains('@') }
      ?.substringAfter('@')
      ?.trim()
      ?.takeIf { it.isNotBlank() }
      ?.lowercase()

  fun isSupertonic(ref: String?): Boolean = supertonicSid(ref) != null

  /** Motor al que pertenece la referencia; null si no es utilizable. */
  fun typeOf(ref: String?): TtsModelType? =
    when {
      isKokoro(ref) -> TtsModelType.KOKORO
      isSupertonic(ref) -> TtsModelType.SUPERTONIC
      ref != null && File(ref).exists() -> TtsModelType.POCKET
      else -> null
    }

  /** La voz sigue siendo utilizable: una referencia de voz válida, o una muestra que exista. */
  fun usable(ref: String?): Boolean = typeOf(ref) != null
}
