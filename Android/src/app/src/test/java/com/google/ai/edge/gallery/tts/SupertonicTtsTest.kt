package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.HfFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupertonicTtsTest {
  private fun dir(): java.io.File =
    Files.createTempDirectory("supertonic-test").toFile().also { it.deleteOnExit() }

  private val nombres =
    listOf(
      "duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx",
      "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin",
    )

  @Test fun detectaModeloCompletoYPrefiereInt8() {
    val d = dir()
    assertTrue(SupertonicModelFiles.missing(d).size == nombres.size)
    nombres.forEach { java.io.File(d, it).createNewFile() }
    assertTrue(SupertonicModelFiles.looksLike(d))
    assertTrue(SupertonicModelFiles.missing(d).isEmpty())
    val files = SupertonicModelFiles.resolve(d)
    assertNotNull(files)
    assertEquals("duration_predictor.int8.onnx", files!!.durationPredictor.name)
    assertEquals("voice.bin", files.voiceStyle.name)
  }

  @Test fun aceptaVarianteSinInt8YRechazaIncompleto() {
    val d = dir()
    nombres.map { it.replace(".int8.onnx", ".onnx") }.forEach { java.io.File(d, it).createNewFile() }
    assertTrue(SupertonicModelFiles.missing(d).isEmpty())
    assertEquals("vocoder.onnx", SupertonicModelFiles.resolve(d)!!.vocoder.name)
    java.io.File(d, "voice.bin").delete()
    assertFalse(SupertonicModelFiles.looksLike(d))
    assertNull(SupertonicModelFiles.resolve(d))
    assertEquals(listOf("voice.bin"), SupertonicModelFiles.missing(d))
  }

  @Test fun seleccionaVarianteDeHuggingFace() {
    val completos =
      nombres.map { HfFile("modelo/$it", 10L) } +
        listOf(HfFile("modelo/extra.txt", 1L), HfFile("otro/tts.json", 1L))
    val variants = SupertonicHfSelect.variants(completos)
    assertEquals(1, variants.size)
    assertEquals("modelo", variants.first().folder)
    assertEquals(7, variants.first().files.size)
    assertEquals(70L, variants.first().totalBytes)
    assertTrue(variants.first().files.any { it.localPath == "voice.bin" })
    assertTrue(SupertonicHfSelect.variants(completos.dropLast(7)).isEmpty())
  }

  @Test fun referenciasDeVozPorMotor() {
    assertEquals(TtsModelType.KOKORO, TtsVoiceRef.typeOf(TtsVoiceRef.kokoro(3)))
    assertEquals(3, TtsVoiceRef.kokoroSid(TtsVoiceRef.kokoro(3)))
    assertEquals(TtsModelType.SUPERTONIC, TtsVoiceRef.typeOf(TtsVoiceRef.supertonic(6, "ES")))
    assertEquals(6, TtsVoiceRef.supertonicSid("supertonic:6@es"))
    assertEquals("es", TtsVoiceRef.supertonicLang("supertonic:6@es"))
    assertNull(TtsVoiceRef.supertonicLang("supertonic:6"))
    assertFalse(TtsVoiceRef.usable("supertonic:-1"))
    assertNull(TtsVoiceRef.typeOf("kokoro:abc"))
    val wav = java.io.File(dir(), "voz.wav").also { it.createNewFile() }
    assertEquals(TtsModelType.POCKET, TtsVoiceRef.typeOf(wav.absolutePath))
    assertNull(TtsVoiceRef.typeOf(java.io.File(dir(), "no.wav").absolutePath))
  }
}
