package com.google.ai.edge.gallery.data.characters

/**
 * Lector mínimo de WAV (PCM de 8/16/24/32 bits o float de 32 bits) para las muestras de voz de
 * Pocket TTS. Mezcla a mono y devuelve muestras en [-1, 1]. Lógica pura.
 */
object WavReader {
  class Pcm(val samples: FloatArray, val sampleRate: Int) {
    val seconds: Float get() = if (sampleRate > 0) samples.size.toFloat() / sampleRate else 0f
  }

  private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

  private fun u32(b: ByteArray, o: Int) =
    (b[o].toLong() and 0xFF) or
      ((b[o + 1].toLong() and 0xFF) shl 8) or
      ((b[o + 2].toLong() and 0xFF) shl 16) or
      ((b[o + 3].toLong() and 0xFF) shl 24)

  /** null si no es un WAV que se pueda leer. */
  fun parse(b: ByteArray): Pcm? {
    if (b.size < 44) return null
    if (String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
    var format = 0
    var channels = 0
    var rate = 0
    var bits = 0
    var dataStart = -1
    var dataLen = 0
    var pos = 12
    while (pos + 8 <= b.size) {
      val id = String(b, pos, 4, Charsets.US_ASCII)
      val len = u32(b, pos + 4)
      val body = pos + 8
      if (id == "fmt " && body + 16 <= b.size) {
        format = u16(b, body)
        channels = u16(b, body + 2)
        rate = u32(b, body + 4).toInt()
        bits = u16(b, body + 14)
        // WAVE_FORMAT_EXTENSIBLE: el formato real va en los primeros 2 bytes del subformato.
        if (format == 0xFFFE && len >= 26 && body + 26 <= b.size) format = u16(b, body + 24)
      } else if (id == "data") {
        dataStart = body
        dataLen = minOf(len, (b.size - body).toLong()).toInt()
        break
      }
      pos = body + len.toInt() + (len.toInt() and 1)
      if (len < 0 || pos < 0) return null
    }
    if (dataStart < 0 || channels < 1 || rate <= 0) return null
    val bytesPer = bits / 8
    if (bytesPer < 1 || !((format == 1 && bits in intArrayOf(8, 16, 24, 32)) || (format == 3 && bits == 32))) {
      return null
    }
    val frames = dataLen / (bytesPer * channels)
    if (frames <= 0) return null
    val out = FloatArray(frames)
    for (f in 0 until frames) {
      var sum = 0f
      for (c in 0 until channels) {
        val o = dataStart + (f * channels + c) * bytesPer
        sum +=
          when {
            format == 3 -> java.lang.Float.intBitsToFloat(u32(b, o).toInt())
            bits == 8 -> ((b[o].toInt() and 0xFF) - 128) / 128f
            bits == 16 -> (u16(b, o).toShort().toInt()) / 32768f
            bits == 24 -> {
              val v = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16)
              v / 8388608f
            }
            else -> u32(b, o).toInt() / 2147483648f
          }
      }
      out[f] = (sum / channels).coerceIn(-1f, 1f)
    }
    return Pcm(out, rate)
  }
}
