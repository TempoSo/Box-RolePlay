package com.google.ai.edge.gallery.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLinkTest {
  private val link =
    DownloadLink(
      id = "x",
      label = "modelo",
      url = "https://huggingface.co/a/b/resolve/main/x.gguf",
      fileName = "x.gguf",
      sizeBytes = 1_000L,
    )

  @Test
  fun `el progreso se limita entre cero y uno`() {
    assertEquals(0f, link.fraction, 0f)
    assertEquals(0.5f, link.copy(receivedBytes = 500L).fraction, 0f)
    assertEquals(1f, link.copy(receivedBytes = 5_000L).fraction, 0f)
  }

  @Test
  fun `el estado conserva el motivo del fallo y limpia velocidad`() {
    val failed = link.withProgress(400L, 900L, 5_000L).withState(LinkState.FAILED, "sin conexión")
    assertEquals(LinkState.FAILED, failed.state)
    assertEquals("sin conexión", failed.error)
    assertEquals(0L, failed.bytesPerSecond)
    assertEquals(400L, failed.receivedBytes)
  }

  @Test
  fun `el texto de progreso incluye porcentaje MB velocidad y ETA`() {
    val running = link.withProgress(750L, 4 * 1024 * 1024L, 65_000L).copy(state = LinkState.DOWNLOADING)
    val text = DownloadLinkFormat.progress(running)
    assertTrue(text.startsWith("75%"))
    assertTrue(text.contains("4.0 MB/s"))
    assertTrue(text.contains("ETA 1 min 5 s"))
  }

  @Test
  fun `el catalogo trae dos litertlm y los dos Unsloth con su proyector`() {
    val base = DownloadLinkCatalog.base
    assertEquals(6, base.size)
    assertEquals(2, base.count { it.fileName.endsWith(".litertlm") })
    assertEquals(
      2,
      base.count { it.label.contains("Unsloth") && it.kind == LinkKind.MODEL },
    )
    assertTrue(base.all { it.url.startsWith("https://huggingface.co/") })
    assertEquals(2, base.count { it.kind == LinkKind.MMPROJ && it.target == LinkTarget.MMPROJ })
  }

  @Test
  fun `un archivo del navegador se convierte en enlace con destino correcto`() {
    val model = DownloadLinkCatalog.fromHfFile("unsloth/qwen", "gguf/q4.gguf", 100L, LinkKind.MODEL, true)
    assertEquals("hf:unsloth/qwen:gguf/q4.gguf", model.id)
    assertEquals("q4.gguf", model.fileName)
    assertEquals(LinkTarget.IMPORTS, model.target)
    assertTrue(model.vision)

    val mmproj =
      DownloadLinkCatalog.fromHfFile("unsloth/qwen", "mmproj-F16.gguf", 10L, LinkKind.MMPROJ, true)
    assertEquals(LinkTarget.MMPROJ, mmproj.target)
    assertFalse(mmproj.vision)
  }

  @Test
  fun `el aviso de RAM senala modelos pesados y calla con los ligeros`() {
    val gb = 1024L * 1024L * 1024L
    val smallPhone = DeviceProfile(ramBytes = 4 * gb, soc = "sm7750")
    val heavy = DownloadLinkCatalog.base.first { it.id == "catalog-gemma4-e4b" }
    val light = DownloadLinkCatalog.base.first { it.id == "catalog-gemma3-1b" }
    assertTrue(smallPhone.warningFor(heavy)!!.contains("superan"))
    assertNull(smallPhone.warningFor(light))

    val bigPhone = DeviceProfile(ramBytes = 12 * gb, soc = "sm8750")
    assertTrue(bigPhone.sizeExceedsRecommendation(heavy))
    assertFalse(bigPhone.sizeExceedsRecommendation(light))
  }
}
