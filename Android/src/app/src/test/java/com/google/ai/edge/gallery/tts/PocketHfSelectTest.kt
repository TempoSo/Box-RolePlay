package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHfSelectTest {
  private fun old(dir: String) =
    listOf("lm_flow.int8", "lm_main.int8", "encoder", "decoder.int8", "text_conditioner").map { HfFile("$dir/$it.onnx", 10L) } +
      listOf("vocab.json", "token_scores.json").map { HfFile("$dir/$it", 1L) }

  private fun v2(dir: String): List<HfFile> {
    val p = if (dir.isEmpty()) "" else "$dir/"
    return listOf(
      "flow_lm_flow.onnx" to 39L, "flow_lm_flow_int8.onnx" to 10L, "flow_lm_main.onnx" to 303L, "flow_lm_main_int8.onnx" to 76L,
      "mimi_decoder.onnx" to 41L, "mimi_decoder_int8.onnx" to 22L, "mimi_encoder.onnx" to 39L, "mimi_encoder_int8.onnx" to 20L,
      "text_conditioner.onnx" to 16L, "text_conditioner_int8.onnx" to 16L, "tokenizer.model" to 1L, "bundle.json" to 1L,
      "bos_before_voice.safetensors" to 1L,
    ).map { HfFile(p + it.first, it.second) }
  }

  @Test fun old_format_two_languages() {
    val v = PocketHfSelect.variants(old("en") + old("es") + HfFile("README.md", 1) + HfFile("incompleta/vocab.json", 1))
    assertEquals(listOf("en", "es"), v.map { it.folder })
    assertEquals(7, v[0].files.size)
  }

  @Test fun v2_bundles_prefer_int8_and_canonical_names() {
    val v = PocketHfSelect.variants(v2("onnx/spanish") + v2("onnx/spanish_24l") + v2("onnx"))
    assertEquals(listOf("onnx", "onnx/spanish", "onnx/spanish_24l"), v.map { it.folder })
    val names = v[1].files.map { it.localName }.toSet()
    assertEquals(setOf("lm_flow.int8.onnx", "lm_main.int8.onnx", "decoder.int8.onnx", "encoder.int8.onnx", "text_conditioner.int8.onnx", "tokenizer.model"), names)
    assertEquals(10L + 76 + 22 + 20 + 16 + 1, v[1].totalBytes)
  }

  @Test fun friendly_labels() {
    assertEquals("Español", PocketHfSelect.friendly("onnx/spanish"))
    assertEquals("Alemán (24l)", PocketHfSelect.friendly("onnx/german_24l"))
    assertEquals("Inglés (2026-04)", PocketHfSelect.friendly("onnx/english_2026-04"))
    assertTrue(PocketHfSelect.friendly("").startsWith("Principal"))
  }
}
