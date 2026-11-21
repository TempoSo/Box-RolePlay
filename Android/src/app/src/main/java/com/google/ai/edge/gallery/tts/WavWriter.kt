package com.google.ai.edge.gallery.tts

/** Escribe un WAV PCM de 16 bits y un solo canal (para guardar lo grabado con el micrófono). */
object WavWriter {
    fun pcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArray(44 + pcm.size)
        fun str(o: Int, s: String) = s.forEachIndexed { i, c -> out[o + i] = c.code.toByte() }
        fun le32(o: Int, v: Int) { for (i in 0..3) out[o + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun le16(o: Int, v: Int) { out[o] = (v and 0xFF).toByte(); out[o + 1] = ((v shr 8) and 0xFF).toByte() }
        str(0, "RIFF"); le32(4, 36 + pcm.size); str(8, "WAVE")
        str(12, "fmt "); le32(16, 16); le16(20, 1); le16(22, 1)
        le32(24, sampleRate); le32(28, sampleRate * 2); le16(32, 2); le16(34, 16)
        str(36, "data"); le32(40, pcm.size)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }
}
