package com.google.ai.edge.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.HfClient
import com.google.ai.edge.gallery.data.downloads.DownloadLinkStore
import com.google.ai.edge.gallery.data.downloads.LinkKind
import com.google.ai.edge.gallery.data.downloads.LinkState
import com.google.ai.edge.gallery.data.downloads.LinkTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AGDownloadLinkWorker"
private const val CHANNEL_ID = "nexo_link_download"

const val KEY_LINK_ID = "link_id"
const val KEY_LINK_URL = "link_url"
const val KEY_LINK_FILE = "link_file"
const val KEY_LINK_SIZE = "link_size"
const val KEY_LINK_KIND = "link_kind"
const val KEY_LINK_TARGET = "link_target"
const val KEY_LINK_LABEL = "link_label"

/**
 * Descarga una entrada de la lista enlazada. Es un WorkManager worker, así que sigue corriendo al
 * cambiar de pestaña, abrir un chat o salir de la pantalla de Descargas. Escribe estado y progreso
 * en [DownloadLinkStore], que es la fuente de verdad persistente.
 */
class DownloadLinkWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  private val notificationManager =
    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      val store = DownloadLinkStore
      store.attach(applicationContext)
      val id = inputData.getString(KEY_LINK_ID).orEmpty()
      val url = inputData.getString(KEY_LINK_URL).orEmpty()
      val fileName = inputData.getString(KEY_LINK_FILE).orEmpty()
      val size = inputData.getLong(KEY_LINK_SIZE, 0L)
      val label = inputData.getString(KEY_LINK_LABEL) ?: fileName
      val kind =
        runCatching { LinkKind.valueOf(inputData.getString(KEY_LINK_KIND) ?: "MODEL") }
          .getOrDefault(LinkKind.MODEL)
      val target =
        runCatching { LinkTarget.valueOf(inputData.getString(KEY_LINK_TARGET) ?: "IMPORTS") }
          .getOrDefault(LinkTarget.IMPORTS)

      if (id.isBlank() || url.isBlank() || fileName.isBlank()) {
        return@withContext Result.failure(Data.Builder().putString("error", "Enlace incompleto").build())
      }

      try {
        com.google.ai.edge.gallery.security.OfflineMode.assertOnlineOrThrow()
      } catch (e: com.google.ai.edge.gallery.security.OfflineMode.OfflineModeException) {
        store.update(id) { it.withState(LinkState.FAILED, e.message ?: "Modo sin conexión activo") }
        return@withContext Result.failure(Data.Builder().putString("error", e.message).build())
      }

      val link = store.snapshot().firstOrNull { it.id == id }
      if (link == null) return@withContext Result.failure()

      createChannel()
      setForeground(foregroundInfo(label))

      store.update(id) { it.copy(state = LinkState.DOWNLOADING, error = "") }
      var windowStart = System.currentTimeMillis()
      var windowBytes = link.receivedBytes

      try {
        HfClient.download(
          url = url,
          dest = store.pathFor(applicationContext, link),
          expectedSize = if (size > 0) size else link.sizeBytes,
          onProgress = { received, total ->
            val now = System.currentTimeMillis()
            val elapsed = (now - windowStart).coerceAtLeast(1L)
            val speed = ((received - windowBytes) * 1000L) / elapsed
            if (now - windowStart >= 1000L || received >= total) {
              windowStart = now
              windowBytes = received
            }
            val remaining = if (speed > 0 && total > received) ((total - received) * 1000L) / speed else 0L
            store.update(id) { it.withProgress(received, speed, remaining).copy(state = LinkState.DOWNLOADING) }
          },
          isCancelled = { isStopped },
        )
        // BUG-046: el proyector descargado se selecciona solo para que Visión lo vea de inmediato.
        if (kind == LinkKind.MMPROJ && target == LinkTarget.MMPROJ) {
          EnginePerfStore(applicationContext).setMmproj(fileName)
        }
        store.update(id) {
          it.copy(
            state = LinkState.COMPLETED,
            receivedBytes = if (it.sizeBytes > 0) it.sizeBytes else it.receivedBytes,
            bytesPerSecond = 0L,
            remainingMs = 0L,
            error = "",
          )
        }
        Log.d(TAG, "Descarga completada: $fileName")
        Result.success()
      } catch (e: Exception) {
        val message = e.message?.takeIf { it.isNotBlank() } ?: "La descarga falló"
        if (isStopped) {
          // Pausa o cancelación: se conserva el .part y el progreso para reanudar.
          store.update(id) { it.copy(state = LinkState.PAUSED, bytesPerSecond = 0L, remainingMs = 0L) }
        } else {
          store.update(id) { it.withState(LinkState.FAILED, message) }
        }
        Log.w(TAG, "Descarga fallida: $message")
        Result.failure(Data.Builder().putString("error", message).build())
      }
    }

  private fun createChannel() {
    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
      notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Descargas de modelos", NotificationManager.IMPORTANCE_LOW)
      )
    }
  }

  private fun foregroundInfo(label: String): ForegroundInfo {
    val notification =
      NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Descargando modelo")
        .setContentText(label)
        .setOngoing(true)
        .build()
    return ForegroundInfo(
      label.hashCode() and 0x7fffffff,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }
}
