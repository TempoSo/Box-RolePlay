package com.google.ai.edge.gallery.data.downloads

import com.google.ai.edge.gallery.data.characters.HfApi
import com.google.ai.edge.gallery.data.characters.HfFit

/**
 * Catálogo pre-enlazado. Dos `.litertlm` ligeros del allowlist y cuatro familias GGUF de Unsloth
 * (Gemma 4 E4B/E2B, Qwen3 VL 4B y Llama 3.2 3B). Cada familia trae sus cuantizaciones verificadas
 * en la API de HF, con el número de bytes exacto, para que el selector no tenga que consultar nada
 * en ejecución. Los modelos con visión llevan su mmproj F16 enlazado aparte.
 *
 * [VERSION] se sube cuando cambia cualquier enlace del catálogo: así los teléfonos que ya tenían
 * guardada la lista reciben la definición nueva (por ejemplo el paso de BF16 a F16).
 */
object DownloadLinkCatalog {
  const val VERSION = 2

  private const val GEMMA3_COMMIT = "42d538a932e8d5b12e6b3b455f5572560bd60b2c"
  private const val QWEN_COMMIT = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"
  private const val UNSLOTH_E4B = "unsloth/gemma-4-E4B-it-GGUF"
  private const val UNSLOTH_E2B = "unsloth/gemma-4-E2B-it-GGUF"
  private const val UNSLOTH_QWEN3_VL = "unsloth/Qwen3-VL-4B-Instruct-GGUF"
  private const val UNSLOTH_LLAMA = "unsloth/Llama-3.2-3B-Instruct-GGUF"

  /** Construye las variantes de una familia con nombres de archivo predecibles de Unsloth. */
  private fun variants(
    repo: String,
    stem: String,
    sizes: List<Pair<String, Long>>,
  ): List<DownloadVariant> =
    sizes.map { (quant, size) ->
      val fileName = "$stem-$quant.gguf"
      DownloadVariant(
        quant = quant,
        fileName = fileName,
        url = "https://huggingface.co/$repo/resolve/main/$fileName?download=true",
        sizeBytes = size,
      )
    }

  private val E4B =
    variants(
      UNSLOTH_E4B,
      "gemma-4-E4B-it",
      listOf(
        "UD-IQ2_M" to 3_545_083_008L,
        "Q3_K_M" to 4_058_137_728L,
        "Q4_K_S" to 4_844_850_304L,
        "Q4_K_M" to 4_977_171_584L,
        "Q5_K_M" to 5_481_798_784L,
      ),
    )
  private val E2B =
    variants(
      UNSLOTH_E2B,
      "gemma-4-E2B-it",
      listOf(
        "UD-IQ2_M" to 2_290_860_128L,
        "Q3_K_M" to 2_536_786_016L,
        "Q4_K_S" to 3_043_934_304L,
        "Q4_K_M" to 3_106_738_272L,
        "Q5_K_M" to 3_356_037_216L,
      ),
    )
  private val QWEN3_VL =
    variants(
      UNSLOTH_QWEN3_VL,
      "Qwen3-VL-4B-Instruct",
      listOf(
        "Q2_K" to 1_669_501_216L,
        "Q3_K_M" to 2_075_619_616L,
        "Q4_K_S" to 2_383_311_136L,
        "Q4_K_M" to 2_497_282_336L,
        "Q5_K_M" to 2_889_515_296L,
      ),
    )
  private val LLAMA =
    variants(
      UNSLOTH_LLAMA,
      "Llama-3.2-3B-Instruct",
      listOf(
        "Q2_K" to 1_363_935_680L,
        "Q3_K_M" to 1_687_159_232L,
        "Q4_K_S" to 1_928_200_640L,
        "Q4_K_M" to 2_019_377_600L,
        "Q5_K_M" to 2_322_153_920L,
      ),
    )

  private fun DownloadVariant.asLink(
    id: String,
    label: String,
    groupId: String,
    vision: Boolean,
    allVariants: List<DownloadVariant>,
  ) =
    DownloadLink(
      id = id,
      label = label,
      url = url,
      fileName = fileName,
      sizeBytes = sizeBytes,
      vision = vision,
      pinned = true,
      groupId = groupId,
      variants = allVariants,
    )

  private fun mmproj(repo: String, fileName: String, size: Long, groupId: String, label: String) =
    DownloadLink(
      id = "catalog-$groupId-mmproj",
      label = label,
      url = "https://huggingface.co/$repo/resolve/main/mmproj-F16.gguf?download=true",
      fileName = fileName,
      sizeBytes = size,
      kind = LinkKind.MMPROJ,
      target = LinkTarget.MMPROJ,
      pinned = true,
      groupId = groupId,
    )

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
      E4B.first { it.quant == "Q4_K_M" }.asLink(
        id = "catalog-gemma4-e4b",
        label = "Gemma 4 E4B · Unsloth",
        groupId = "gemma4-e4b",
        vision = true,
        allVariants = E4B,
      ),
      mmproj(
        repo = UNSLOTH_E4B,
        fileName = "mmproj-gemma-4-E4B-it-F16.gguf",
        size = 990_372_672L,
        groupId = "gemma4-e4b",
        label = "Visión Gemma 4 E4B (mmproj F16)",
      ),
      E2B.first { it.quant == "Q4_K_M" }.asLink(
        id = "catalog-gemma4-e2b",
        label = "Gemma 4 E2B · Unsloth",
        groupId = "gemma4-e2b",
        vision = true,
        allVariants = E2B,
      ),
      mmproj(
        repo = UNSLOTH_E2B,
        fileName = "mmproj-gemma-4-E2B-it-F16.gguf",
        size = 985_654_080L,
        groupId = "gemma4-e2b",
        label = "Visión Gemma 4 E2B (mmproj F16)",
      ),
      QWEN3_VL.first { it.quant == "Q4_K_M" }.asLink(
        id = "catalog-qwen3-vl-4b",
        label = "Qwen3 VL 4B · Unsloth",
        groupId = "qwen3-vl-4b",
        vision = true,
        allVariants = QWEN3_VL,
      ),
      mmproj(
        repo = UNSLOTH_QWEN3_VL,
        fileName = "mmproj-Qwen3-VL-4B-Instruct-F16.gguf",
        size = 836_180_640L,
        groupId = "qwen3-vl-4b",
        label = "Visión Qwen3 VL 4B (mmproj F16)",
      ),
      LLAMA.first { it.quant == "Q4_K_M" }.asLink(
        id = "catalog-llama3p2-3b",
        label = "Llama 3.2 3B · Unsloth",
        groupId = "llama3p2-3b",
        vision = false,
        allVariants = LLAMA,
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

  /**
   * Aviso de la tarjeta. [totalBytes] debe incluir el proyector cuando el modelo tiene Visión: así
   * el enlazado deja claro el coste real y no solo el peso del `.gguf`.
   */
  fun warningFor(link: DownloadLink, totalBytes: Long = link.sizeBytes): String? {
    // El proyector no se usa solo: avisar de que "supera lo recomendado" por su propio tamaño sería
    // falso en un teléfono grande. El aviso se calcula sobre el grupo modelo + proyector.
    if (link.kind == LinkKind.MMPROJ) return null
    if (recommendedMaxBytes > 0 && totalBytes > recommendedMaxBytes) return heavyWarning(totalBytes)
    return when (HfApi.fit(totalBytes, ramBytes)) {
      HfFit.TIGHT -> "Justo para $soc con ${ramBytes / GB} GB de RAM: puede ir lento."
      HfFit.TOO_BIG -> "Pesado para $soc con ${ramBytes / GB} GB de RAM: mejor usa un modelo más pequeño."
      else -> null
    }
  }

  /**
   * Cuantización que cabe con holgura contando el proyector. Devuelve null si ninguna entra en el
   * presupuesto (la tarjeta lo avisa en vez de fingir una recomendación).
   */
  fun recommendedVariant(link: DownloadLink, extraBytes: Long = 0L): DownloadVariant? =
    link.variants.sortedBy { it.sizeBytes }.lastOrNull { it.sizeBytes + extraBytes <= recommendedMaxBytes }

  /** Etiqueta corta para cada cuantización del selector. */
  fun fitLabel(bytes: Long): String =
    when (HfApi.fit(bytes, ramBytes)) {
      HfFit.GOOD -> "cabe bien"
      HfFit.TIGHT -> "justo"
      HfFit.TOO_BIG -> "demasiado grande"
      HfFit.UNKNOWN -> "sin datos"
    }

  /** Suma del modelo y su proyector cuando comparten [DownloadLink.groupId]. */
  fun groupBytes(link: DownloadLink, links: List<DownloadLink>): Long =
    if (link.groupId.isBlank()) {
      link.sizeBytes
    } else {
      links.filter { it.groupId == link.groupId }.sumOf { it.sizeBytes }
    }

  fun sizeExceedsRecommendation(link: DownloadLink): Boolean =
    recommendedMaxBytes > 0 && link.sizeBytes > recommendedMaxBytes

  private fun heavyWarning(bytes: Long): String =
    "${DownloadLinkFormat.mb(bytes)} superan los ${DownloadLinkFormat.mb(recommendedMaxBytes)} " +
      "recomendados para $soc con ${ramBytes / GB} GB de RAM."
}
