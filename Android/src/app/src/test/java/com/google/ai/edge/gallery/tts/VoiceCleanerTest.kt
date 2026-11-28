package com.google.ai.edge.gallery.tts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCleanerTest {
  private val sr = 24000

  /** 1 s de ruido, 3 s de voz, 2 s de pausa, 3 s de voz, 1 s de ruido, con ruido y nivel DC. */
  private fun sample(): FloatArray {
    val r = java.util.Random(1)
    return FloatArray(sr * 10) { i ->
      val t = i / sr.toFloat()
      val voice = if (t in 1f..4f || t in 6f..9f) (0.3 * sin(2 * PI * 180 * t) * (0.6 + 0.4 * sin(2 * PI * 3 * t)) + 0.1 * sin(2 * PI * 900 * t)).toFloat() else 0f
      voice + 0.02f * r.nextGaussian().toFloat() + 0.05f
    }
  }

  @Test fun recortaSilenciosYLimitaPicos() {
    val res = VoiceCleaner.clean(sample(), sr)
    assertTrue(res.report.outSeconds in 5.5f..8.5f)
    assertTrue(res.samples.all { abs(it) <= 0.97f && !it.isNaN() })
  }

  @Test fun noSeInventaVozEnSilencio() {
    val res = VoiceCleaner.clean(FloatArray(sr * 5), sr)
    assertFalse(res.report.notes.isEmpty())
  }

  @Test fun fftIdaYVuelta() {
    val re = FloatArray(1024) { sin(it * 0.1f) }
    val im = FloatArray(1024)
    val o = re.copyOf()
    VoiceCleaner.fft(re, im, false); VoiceCleaner.fft(re, im, true)
    for (i in o.indices) assertEquals(o[i], re[i], 1e-3f)
  }

  @Test fun avisaSiSaturada() {
    val res = VoiceCleaner.clean(FloatArray(sr * 4) { if (it % 2 == 0) 1f else -1f }, sr)
    assertTrue(res.report.notes.any { "saturada" in it })
  }

  @Test fun convierteSiempreAWavMonoA24k() {
    val src = FloatArray(48000) { (0.3 * sin(2 * PI * 180 * it / 48000f)).toFloat() }
    val resampled = VoiceCleaner.resample(src, 48000, 24000)
    assertEquals(24000, resampled.size)
    assertTrue(resampled.all { !it.isNaN() })
    assertEquals(24000, VoiceCleaner.clean(sample(), sr).sampleRate)
  }

  @Test fun eligeElMejorTramoYNoPasaDe12Segundos() {
    val r = java.util.Random(2)
    val long =
      FloatArray(sr * 20) { i ->
        val t = i / sr.toFloat()
        val voice = if (t >= 2f) (0.3 * sin(2 * PI * 180 * t) + 0.1 * sin(2 * PI * 900 * t)).toFloat() else 0f
        voice + 0.02f * r.nextGaussian().toFloat() + 0.05f
      }
    val res = VoiceCleaner.clean(long, sr)
    assertTrue(res.report.outSeconds <= 12.1f)
    assertTrue(res.report.notes.any { "tramo" in it })
    assertTrue(res.samples.all { !it.isNaN() })
  }
}
