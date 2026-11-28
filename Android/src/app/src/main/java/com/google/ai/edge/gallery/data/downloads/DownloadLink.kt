package com.google.ai.edge.gallery.data.downloads

import kotlinx.serialization.Serializable

/** Ciclo de vida de un modelo enlazado. Persiste en SharedPreferences, no en la pantalla. */
enum class LinkState {
  IDLE,
  DOWNLOADING,
  PAUSED,
  FAILED,
  COMPLETED,
}

/** Tipo de archivo enlazado (determina cómo se registra al terminar). */
enum class LinkKind {
  MODEL,
  MMPROJ,
}

/** Directorio destino dentro del almacenamiento privado de Nexo. */
enum class LinkTarget {
  IMPORTS,
  MMPROJ,
}

/** Una cuantización alternativa del mismo modelo del catálogo (tamaños verificados en HF). */
@Serializable
data class DownloadVariant(
  val quant: String,
  val fileName: String,
  val url: String,
  val sizeBytes: Long,
)

/**
 * Un modelo enlazado: entra en la lista desde el catálogo o desde Hugging Face y se queda ahí
 * aunque se cambie de pestaña o se cierre la pantalla. El worker escribe el progreso aquí.
 */
@Serializable
data class DownloadLink(
  val id: String,
  val label: String,
  val url: String,
  val fileName: String,
  val sizeBytes: Long,
  val kind: LinkKind = LinkKind.MODEL,
  val target: LinkTarget = LinkTarget.IMPORTS,
  val state: LinkState = LinkState.IDLE,
  val receivedBytes: Long = 0L,
  val bytesPerSecond: Long = 0L,
  val remainingMs: Long = 0L,
  val error: String = "",
  /** true si el modelo sirve también para Visión (se usa al registrar el archivo descargado). */
  val vision: Boolean = false,
  /** true en las entradas del catálogo base (no se pueden quitar de la lista). */
  val pinned: Boolean = false,
  /** Modelo y proyector que se descargan juntos. */
  val groupId: String = "",
  /** Cuantizaciones disponibles de esta familia; vacío si el enlace viene de una búsqueda. */
  val variants: List<DownloadVariant> = emptyList(),
  val addedAtMs: Long = 0L,
) {
  val fraction: Float
    get() = if (sizeBytes > 0) (receivedBytes.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f) else 0f

  fun withProgress(received: Long, speed: Long, remainingMs: Long): DownloadLink =
    copy(
      receivedBytes = received.coerceAtLeast(0L),
      bytesPerSecond = speed.coerceAtLeast(0L),
      remainingMs = remainingMs.coerceAtLeast(0L),
    )

  fun withState(state: LinkState, error: String = ""): DownloadLink =
    copy(state = state, error = error, bytesPerSecond = 0L, remainingMs = 0L)

  /** Cambia la cuantización: el progreso anterior ya no sirve porque es otro archivo. */
  fun withVariant(variant: DownloadVariant): DownloadLink =
    copy(
      url = variant.url,
      fileName = variant.fileName,
      sizeBytes = variant.sizeBytes,
      state = LinkState.IDLE,
      receivedBytes = 0L,
      bytesPerSecond = 0L,
      remainingMs = 0L,
      error = "",
    )
}

/** Formateo puro del progreso de la tarjeta (MB, velocidad y ETA). */
object DownloadLinkFormat {
  private val locale = java.util.Locale.US

  fun mb(bytes: Long): String = String.format(locale, "%.0f MB", bytes / (1024f * 1024f))

  fun speed(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0) "—" else String.format(locale, "%.1f MB/s", bytesPerSecond / (1024f * 1024f))

  fun eta(remainingMs: Long): String {
    if (remainingMs <= 0) return "—"
    val seconds = (remainingMs / 1000L).coerceAtLeast(1L)
    return when {
      seconds < 60 -> "$seconds s"
      seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
      else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }
  }

  /** Texto de la tarjeta: "42% · 1.240 de 2.583 MB · 8,2 MB/s · ETA 3 min". */
  fun progress(link: DownloadLink): String =
    buildString {
      val percent = (link.fraction * 100f).toInt()
      append("$percent% · ${mb(link.receivedBytes)} de ${mb(link.sizeBytes)}")
      if (link.state == LinkState.DOWNLOADING) {
        append(" · ${speed(link.bytesPerSecond)} · ETA ${eta(link.remainingMs)}")
      }
    }
}
