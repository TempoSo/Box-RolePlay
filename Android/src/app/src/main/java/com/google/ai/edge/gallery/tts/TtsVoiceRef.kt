package com.google.ai.edge.gallery.tts

import java.io.File

/**
 * Referencia a una voz guardada en los ajustes (por defecto o de un personaje). Una muestra de Pocket TTS
 * es la ruta de su .wav (como siempre); una voz de Kokoro es «kokoro:<número de voz>». Lógica pura.
 */
object TtsVoiceRef {
  private const val KOKORO = "kokoro:"

  fun kokoro(sid: Int): String = "$KOKORO$sid"

  fun kokoroSid(ref: String?): Int? = ref?.takeIf { it.startsWith(KOKORO) }?.removePrefix(KOKORO)?.toIntOrNull()?.takeIf { it >= 0 }

  fun isKokoro(ref: String?): Boolean = kokoroSid(ref) != null

  /** La voz sigue siendo utilizable: una de Kokoro siempre; una muestra, mientras exista su archivo. */
  fun usable(ref: String?): Boolean = ref != null && (isKokoro(ref) || File(ref).exists())
}
