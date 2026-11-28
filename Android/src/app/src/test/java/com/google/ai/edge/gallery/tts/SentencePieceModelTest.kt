package com.google.ai.edge.gallery.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePieceModelTest {
  private fun varint(v: Int): ByteArray {
    val o = ArrayList<Byte>(); var x = v
    while (x >= 0x80) { o.add(((x and 0x7F) or 0x80).toByte()); x = x ushr 7 }
    o.add(x.toByte()); return o.toByteArray()
  }
  private fun piece(text: String, score: Float, type: Int = 1): ByteArray {
    val t = text.toByteArray()
    val bits = score.toRawBits()
    val inner = byteArrayOf(0x0A) + varint(t.size) + t + byteArrayOf(0x15) +
      byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte()) + byteArrayOf(0x18, type.toByte())
    return byteArrayOf(0x0A) + varint(inner.size) + inner
  }

  @Test fun parses_pieces_in_order_and_skips_other_fields() {
    val trainer = byteArrayOf(0x12, 0x02, 0x08, 0x01) // campo 2 (trainer_spec) que debe ignorarse
    val model = piece("<unk>", 0f, 2) + piece("▁hola", -3.5f) + trainer + piece("ñ", -7.25f)
    val p = SentencePieceModel.parse(model)
    assertEquals(listOf("<unk>", "▁hola", "ñ"), p.map { it.text })
    assertEquals(-3.5f, p[1].score, 0f)
    assertEquals("""{"<unk>":0,"▁hola":1,"ñ":2}""", SentencePieceModel.vocabJson(p))
    assertEquals("""{"<unk>":0.0,"▁hola":-3.5,"ñ":-7.25}""", SentencePieceModel.scoresJson(p))
  }

  @Test(expected = IllegalArgumentException::class) fun rejects_garbage() {
    SentencePieceModel.parse(byteArrayOf(0x12, 0x00))
  }

  @Test fun parses_hf_tokenizer_json() {
    val j = """{"version":"1.0","model":{"type":"Unigram","unk_id":0,"vocab":[["<unk>",0.0],["▁hola",-3.5],["ñ",-7.25]]}}"""
    val p = SentencePieceModel.parseHfJson(j)
    assertEquals(listOf("<unk>", "▁hola", "ñ"), p.map { it.text })
    assertEquals(-7.25f, p[2].score, 0f)
  }

  @Test(expected = IllegalArgumentException::class) fun rejects_non_unigram_json() {
    SentencePieceModel.parseHfJson("""{"model":{"type":"BPE","vocab":{"a":0}}}""")
  }
}
