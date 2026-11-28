package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionSettingsTest {
  @Test fun defaultIs256LikeLayla() {
    assertEquals(256, VisionSettings().maxResponseTokens)
    assertEquals(VisionSettings().sanitized(), VisionSettings())
  }

  @Test fun zeroOrNegativeMeansNoLimit() {
    assertEquals(-1, VisionSettings(maxResponseTokens = 0).sanitized().maxResponseTokens)
    assertEquals(-1, VisionSettings(maxResponseTokens = -5).sanitized().maxResponseTokens)
  }

  @Test fun clampsHugeValues() {
    assertEquals(8192, VisionSettings(maxResponseTokens = 99999).sanitized().maxResponseTokens)
  }

  @Test fun blankInstructionFallsBackToDefault() {
    val s = VisionSettings(imageInstruction = "   ").sanitized()
    assertEquals(VisionSettings.DEFAULT_IMAGE_INSTRUCTION, s.imageInstruction)
  }

  @Test fun truncatesLongTexts() {
    val s =
      VisionSettings(
          systemPrompt = "a".repeat(VisionSettings.MAX_PROMPT_CHARS + 10),
          imageInstruction = "b".repeat(VisionSettings.MAX_INSTRUCTION_CHARS + 10),
        )
        .sanitized()
    assertEquals(VisionSettings.MAX_PROMPT_CHARS, s.systemPrompt.length)
    assertEquals(VisionSettings.MAX_INSTRUCTION_CHARS, s.imageInstruction.length)
  }
}
