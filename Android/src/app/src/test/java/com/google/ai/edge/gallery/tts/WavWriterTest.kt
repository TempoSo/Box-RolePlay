package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.WavReader
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavWriterTest {
  @Test fun convierteMuestrasFloatAWavMono() {
    val samples = FloatArray(2400) { (0.25 * kotlin.math.sin(2 * kotlin.math.PI * 200 * it / 24000.0)).toFloat() }
    val wav = WavWriter.pcm16Mono(samples, 24000)
    val pcm = WavReader.parse(wav)
    assertNotNull(pcm)
    assertEquals(24000, pcm!!.sampleRate)
    assertEquals(samples.size, pcm.samples.size)
    assertTrue(pcm.samples.indices.all { abs(pcm.samples[it] - samples[it]) < 0.01f })
  }
}
