package com.google.ai.edge.gallery.data.downloads

import com.google.ai.edge.gallery.data.characters.HfApi
import com.google.ai.edge.gallery.data.characters.HfFit

/**
 * Catálogo pre-enlazado. Los dos primeros son `.litertlm` ligeros verificados en el allowlist de
 * la app; los otros dos son GGUF de Unsloth con su proyector de visión (mmproj) enlazado aparte.
 * Todos los enlaces son de Hugging Face y se descargan con el mismo worker.
 */
object DownloadLinkCatalog {
  private const val GEMMA3_COMMIT = "42d538a932e8d5b12e6b3b455f5572560bd60b2c"
  private const val QWEN_COMMIT = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"
  private const val UNSLOTH_E4B = "unsloth/gemma-4-E4B-it-GGUF"
  private const val UNSLOTH_E2B = "unsloth/gemma-4-E2B-it-GGUF"

  val base: List<DownloadLink> =
    listOf(
      DownloadLink(
        id = "catalog-gemma3-1b",
        label = "Gemma 3 1B · LiteRT",
        url =
          "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/$GEMMA3_COMMIT/" +
            "gemma3-1b-it-int4.litertlm?download=true",
        fileName = "gemma3-1b-it-int4.litertlm",
        sizeBytes = 584_417_280L,
        pinned = true,
      ),
      DownloadLink(
        id = "catalog-qwen-1p5b",
        label = "Qwen2.5 1.5B · LiteRT",
        url =
          "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/$QWEN_COMMIT/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_597_931_520L,
        pinned = true,
      ),
      DownloadLink(
        id = "catalog-gemma4-e4b",
        label = "Gemma 4 E4B Q4_K_M · Unsloth",
        url =
          "https://huggingface.co/$UNSLOTH_E4B/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf?download=true",
        fileName = "gemma-4-E4B-it-Q4_K_M.gguf",
        sizeBytes = 4_977_171_584L,
        vision = true,
        pinned = true,
        groupId = "gemma4-e4b",
      ),
      DownloadLink(
        id = "catalog-gemma4-e4b-mmproj",
        label = "Visión Gemma 4 E4B (mmproj BF16)",
        url = "https://huggingface.co/$UNSLOTH_E4B/resolve/main/mmproj-BF16.gguf?download=true",
        fileName = "mmproj-gemma-4-E4B-it-BF16.gguf",
        sizeBytes = 991_552_320L,
        kind = LinkKind.MMPROJ,
        target = LinkTarget.MMPROJ,
        pinned = true,
        groupId = "gemma4-e4b",
      ),
      DownloadLink(
        id = "catalog-gemma4-e2b",
        label = "Gemma 4 E2B Q4_K_M · Unsloth",
        url =
          "https://huggingface.co/$UNSLOTH_E2B/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf?download=true",
        fileName = "gemma-4-E2B-it-Q4_K_M.gguf",
        sizeBytes = 3_106_738_272L,
        vision = true,
        pinned = true,
        groupId = "gemma4-e2b",
      ),
      DownloadLink(
        id = "catalog-gemma4-e2b-mmproj",
        label = "Visión Gemma 4 E2B (mmproj BF16)",
        url = "https://huggingface.co/$UNSLOTH_E2B/resolve/main/mmproj-BF16.gguf?download=true",
        fileName = "mmproj-gemma-4-E2B-it-BF16.gguf",
        sizeBytes = 986_833_728L,
        kind = LinkKind.MMPROJ,
        target = LinkTarget.MMPROJ,
        pinned = true,
        groupId = "gemma4-e2b",
      ),
    )

  /** Convierte un archivo del navegador HF en enlace de la lista unificada. */
  fun fromHfFile(
    repoId: String,
    path: String,
    sizeBytes: Long,
    kind: LinkKind,
    vision: Boolean = false,
  ): DownloadLink {
    val fileName = HfApi.safeFileName(path)
    val mmproj = kind == LinkKind.MMPROJ
    return DownloadLink(
      id = "hf:$repoId:$path",
      label = "$repoId/${path.substringAfterLast('/')}",
      url = HfApi.downloadUrl(repoId, path),
      fileName = fileName,
      sizeBytes = sizeBytes,
      kind = kind,
      target = if (mmproj) LinkTarget.MMPROJ else LinkTarget.IMPORTS,
      vision = vision && !mmproj,
      addedAtMs = System.currentTimeMillis(),
    )
  }
}

/** Perfil del dispositivo: RAM y pista de SoC para recomendar modelos y avisar de los pesados. */
data class DeviceProfile(val ramBytes: Long, val soc: String) {
  companion object {
    private const val GB = 1024L * 1024L * 1024L
  }

  /** Tamaño máximo recomendado para un modelo en este dispositivo. */
  val recommendedMaxBytes: Long
    get() =
      when {
        ramBytes <= 0 -> 0L
        ramBytes < 6 * GB -> 900L * 1024L * 1024L
        ramBytes < 10 * GB -> 2 * GB
        else -> 4 * GB
      }

  /** Aviso visible en la tarjeta; null cuando el modelo entra holgado. */
  fun warningFor(link: DownloadLink): String? {
    if (link.kind == LinkKind.MMPROJ || sizeExceedsRecommendation(link)) return heavyWarning(link)
    return when (HfApi.fit(link.sizeBytes, ramBytes)) {
      HfFit.TIGHT -> "Justo para $soc con ${ramBytes / GB} GB de RAM: puede ir lento."
      HfFit.TOO_BIG -> "Pesado para $soc con ${ramBytes / GB} GB de RAM: mejor usa un modelo más pequeño."
      else -> null
    }
  }

  fun sizeExceedsRecommendation(link: DownloadLink): Boolean =
    recommendedMaxBytes > 0 && link.sizeBytes > recommendedMaxBytes

  private fun heavyWarning(link: DownloadLink): String =
    "${DownloadLinkFormat.mb(link.sizeBytes)} superan los ${DownloadLinkFormat.mb(recommendedMaxBytes)} " +
      "recomendados para $soc con ${ramBytes / GB} GB de RAM."
}
