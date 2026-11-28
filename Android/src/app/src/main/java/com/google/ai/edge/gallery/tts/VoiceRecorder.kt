package com.google.ai.edge.gallery.tts

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Grabación de referencias para Pocket TTS: 24 kHz, mono, PCM16 y hasta 30 s. */
object VoiceRecorder {
  const val SAMPLE_RATE = 24000
  const val MAX_SECONDS = 30

  /** Devuelve un WAV legible, o null si no se pudo grabar o salió demasiado corto. */
  @SuppressLint("MissingPermission")
  fun recordWav(stop: AtomicBoolean, onSecond: (Int) -> Unit = {}): ByteArray? {
    val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (min <= 0) return null
    val rec =
      runCatching {
        AudioRecord(
          MediaRecorder.AudioSource.MIC,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          maxOf(min, SAMPLE_RATE),
        )
      }.getOrNull() ?: return null
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
      rec.release()
      return null
    }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    val maxBytes = SAMPLE_RATE * 2 * MAX_SECONDS
    var lastSecond = 0
    try {
      rec.startRecording()
      while (!stop.get() && out.size() < maxBytes) {
        val n = rec.read(buf, 0, buf.size)
        if (n > 0) out.write(buf, 0, n) else if (n < 0) return null
        val sec = out.size() / (SAMPLE_RATE * 2)
        if (sec != lastSecond) {
          lastSecond = sec
          onSecond(sec)
        }
      }
    } finally {
      runCatching { rec.stop() }
      rec.release()
    }
    return if (out.size() < SAMPLE_RATE / 2) null else WavWriter.pcm16Mono(out.toByteArray(), SAMPLE_RATE)
  }
}
