package com.google.ai.edge.gallery.tts

import cn.enaium.rnnoise.createRnnoise
import kotlin.math.roundToInt

/**
 * Reduce el ruido estacionario con RNNoise antes de limpiar la muestra. RNNoise trabaja a 48 kHz en
 * bloques de 10 ms, así que se sube y se baja la tasa con el remuestreador del propio limpiador.
 * Si la librería nativa no está disponible, devuelve la señal original: la limpieza básica sigue
 * funcionando.
 */
object VoiceDenoiser {
  const val TARGET_RATE = 48000

  data class Result(val samples: FloatArray, val speechProbability: Float, val applied: Boolean)

  fun denoise(input: FloatArray, sampleRate: Int): Result {
    val up = VoiceCleaner.resample(input, sampleRate, TARGET_RATE)
    val limited = VoiceCleaner.bandlimit(up, TARGET_RATE, 7500f)
    val out = FloatArray(limited.size)
    var probability = 0f
    var frames = 0
    try {
      createRnnoise().use { denoiser ->
        val block = denoiser.frameSize
        val frame = FloatArray(block)
        val clean = FloatArray(block)
        var i = 0
        while (i < limited.size) {
          val n = minOf(block, limited.size - i)
          frame.fill(0f)
          System.arraycopy(limited, i, frame, 0, n)
          val p = denoiser.processFrame(frame, clean)
          if (n == block) {
            probability += p
            frames++
          }
          System.arraycopy(clean, 0, out, i, n)
          i += n
        }
      }
    } catch (_: Throwable) {
      return Result(input, 0f, false)
    }
    // Otra vez a 24 kHz con limitación de ancho de banda para no introducir aliasing al bajar.
    val back = VoiceCleaner.resample(VoiceCleaner.bandlimit(out, TARGET_RATE, 7500f), TARGET_RATE, sampleRate)
    val mean = if (frames == 0) 0f else (probability / frames).coerceIn(0f, 1f)
    return Result(back, mean, true)
  }

  /** Probabilidad media de voz, para el informe (0 = silencio/ruido, 1 = voz continua). */
  fun describe(speechProbability: Float): String = "Voz detectada en el ${(speechProbability * 100).roundToInt()} % de los bloques."
}
