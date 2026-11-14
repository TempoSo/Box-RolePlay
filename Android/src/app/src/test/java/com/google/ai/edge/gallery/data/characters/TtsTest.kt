package com.google.ai.edge.gallery.data.characters

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TtsTest {
  private fun le(v: Int, n: Int) = ByteArray(n) { ((v shr (8 * it)) and 0xFF).toByte() }

  private fun wav16(rate: Int, ch: Int, samples: ShortArray): ByteArray {
    val data = ByteArrayOutputStream()
    for (s in samples) data.write(le(s.toInt(), 2))
    val o = ByteArrayOutputStream()
    o.write("RIFF".toByteArray()); o.write(le(36 + data.size(), 4)); o.write("WAVE".toByteArray())
    o.write("fmt ".toByteArray()); o.write(le(16, 4)); o.write(le(1, 2)); o.write(le(ch, 2))
    o.write(le(rate, 4)); o.write(le(rate * ch * 2, 4)); o.write(le(ch * 2, 2)); o.write(le(16, 2))
    o.write("LIST".toByteArray()); o.write(le(4, 4)); o.write("abcd".toByteArray())
    o.write("data".toByteArray()); o.write(le(data.size(), 4)); o.write(data.toByteArray())
    return o.toByteArray()
  }

  @Test fun monoPcm16() {
    val p = WavReader.parse(wav16(24000, 1, shortArrayOf(0, 16384, -32768, 32767)))!!
    assertEquals(24000, p.sampleRate)
    assertEquals(4, p.samples.size)
    assertEquals(0.5f, p.samples[1], 1e-4f)
    assertEquals(-1f, p.samples[2], 1e-4f)
  }

  @Test fun stereoMixesToMono() {
    val p = WavReader.parse(wav16(16000, 2, shortArrayOf(16384, -16384, 32767, 0)))!!
    assertEquals(2, p.samples.size)
    assertEquals(0f, p.samples[0], 1e-4f)
    assertEquals(0.5f, p.samples[1], 1e-3f)
  }

  @Test fun rejectsGarbage() {
    assertNull(WavReader.parse(ByteArray(10)))
    assertNull(WavReader.parse(ByteArray(100) { 7 }))
  }

  private fun dir(vararg names: String): File {
    val d = File.createTempFile("pocket", "").apply { delete(); mkdirs() }
    names.forEach { File(d, it).writeText("x") }
    return d
  }

  @Test fun modelComplete() {
    val d = dir("decoder.int8.onnx", "encoder.onnx", "lm_flow.int8.onnx", "lm_main.int8.onnx",
      "text_conditioner.onnx", "token_scores.json", "vocab.json", "README.md")
    assertTrue(PocketModelFiles.missing(d).isEmpty())
    val f = PocketModelFiles.resolve(d)!!
    assertEquals("lm_main.int8.onnx", f.lmMain.name)
    assertEquals("encoder.onnx", f.encoder.name)
  }

  @Test fun modelMissingAndPrefersInt8() {
    val d = dir("lm_flow.onnx", "lm_flow.int8.onnx", "lm_main.onnx")
    assertEquals(listOf("encoder.onnx", "decoder.onnx", "text_conditioner.onnx", "vocab.json", "token_scores.json"),
      PocketModelFiles.missing(d))
    assertNull(PocketModelFiles.resolve(d))
  }

  @Test fun isPart() {
    assertTrue(PocketModelFiles.isPart("lm_flow.int8.onnx"))
    assertTrue(PocketModelFiles.isPart("vocab.json"))
    assertFalse(PocketModelFiles.isPart("README.md"))
    assertFalse(PocketModelFiles.isPart("other.onnx"))
  }
}
