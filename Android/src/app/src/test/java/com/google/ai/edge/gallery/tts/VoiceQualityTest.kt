package com.google.ai.edge.gallery.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceQualityTest {
  private fun report(
    inSeconds: Float = 9f,
    outSeconds: Float = 9f,
    snrDb: Float? = 30f,
    clippedPct: Float = 0f,
    snrAfterDb: Float? = null,
  ) =
    VoiceCleaner.Report(
      inSeconds = inSeconds,
      outSeconds = outSeconds,
      snrDb = snrDb,
      clippedPct = clippedPct,
      snrAfterDb = snrAfterDb,
      notes = emptyList(),
    )

  @Test fun muestraBuenaDaConsejoPositivo() {
    val a = VoiceQuality.assess(report())
    assertEquals(100, a.score)
    assertEquals(VoiceQuality.Level.BUENA, a.level)
    assertTrue(a.tips.single().contains("Duración"))
  }

  @Test fun avisaDeRuidoYSaturacion() {
    val a = VoiceQuality.assess(report(snrDb = 8f, clippedPct = 2f))
    assertTrue(a.score <= 50)
    assertEquals(VoiceQuality.Level.MALA, a.level)
    assertTrue(a.tips.any { "ruido" in it })
    assertTrue(a.tips.any { "satur" in it })
  }

  @Test fun avisaDeMuestraCorta() {
    val a = VoiceQuality.assess(report(outSeconds = 2f))
    assertTrue(a.tips.any { "corta" in it || "Solo quedan" in it })
    assertTrue(a.score <= 70)
  }

  @Test fun laLimpiezaPuedeSubirLaNota() {
    val antes = VoiceQuality.assess(report(snrDb = 20f)).score
    val despues = VoiceQuality.assess(report(snrDb = 20f, snrAfterDb = 28f)).score
    assertEquals(antes + 5, despues)
  }
}
