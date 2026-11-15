package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile
import com.google.ai.edge.gallery.tts.PocketHfSelect
import org.junit.Test
import org.junit.Assert.assertEquals
class PocketHfSelectTest {
  private fun set(dir: String, int8: Boolean) = listOf("lm_flow","lm_main","encoder","decoder","text_conditioner").map {
    HfFile((if (dir.isEmpty()) "" else "$dir/") + it + (if (int8 && it.startsWith("lm") || it=="decoder") ".int8" else "") + ".onnx", 10L) } +
    listOf("vocab.json","token_scores.json").map { HfFile((if (dir.isEmpty()) "" else "$dir/") + it, 1L) }
  @Test fun two_languages() {
    val v = PocketHfSelect.variants(set("en", true) + set("es", true) + HfFile("README.md", 1) + HfFile("incompleta/vocab.json", 1))
    assertEquals(listOf("en","es"), v.map { it.folder }); assertEquals(7, v[0].files.size)
  }
  @Test fun prefers_int8() {
    val v = PocketHfSelect.variants(set("", true) + HfFile("lm_main.onnx", 999))
    assertEquals(1, v.size); assertEquals(true, v[0].files.any { it.name == "lm_main.int8.onnx" }); assertEquals(false, v[0].files.any { it.name == "lm_main.onnx" })
  }
}
