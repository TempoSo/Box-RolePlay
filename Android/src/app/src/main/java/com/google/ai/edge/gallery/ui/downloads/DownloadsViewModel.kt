package com.google.ai.edge.gallery.ui.downloads

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.ai.edge.gallery.data.downloads.DownloadLink
import com.google.ai.edge.gallery.data.downloads.DownloadLinkStore
import com.google.ai.edge.gallery.data.downloads.DeviceProfile
import com.google.ai.edge.gallery.data.downloads.LinkState
import com.google.ai.edge.gallery.worker.KEY_LINK_FILE
import com.google.ai.edge.gallery.worker.KEY_LINK_ID
import com.google.ai.edge.gallery.worker.KEY_LINK_KIND
import com.google.ai.edge.gallery.worker.KEY_LINK_LABEL
import com.google.ai.edge.gallery.worker.KEY_LINK_SIZE
import com.google.ai.edge.gallery.worker.KEY_LINK_TARGET
import com.google.ai.edge.gallery.worker.KEY_LINK_URL
import com.google.ai.edge.gallery.worker.DownloadLinkWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Controla la lista unificada; las descargas viven en WorkManager, no en este ViewModel. */
@HiltViewModel
class DownloadsViewModel @Inject constructor(@ApplicationContext private val context: Context) :
  ViewModel() {
  private val workManager = WorkManager.getInstance(context)

  val links: StateFlow<List<DownloadLink>> = DownloadLinkStore.links
  val device: DeviceProfile by lazy {
    val memory = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
    DeviceProfile(ramBytes = memory.totalMem, soc = android.os.Build.HARDWARE.ifBlank { "SoC" })
  }

  init {
    DownloadLinkStore.attach(context)
    DownloadLinkStore.reconcileFiles(context)
  }

  fun download(link: DownloadLink) {
    DownloadLinkStore.update(link.id) { it.copy(state = LinkState.DOWNLOADING, error = "") }
    val data =
      Data.Builder()
        .putString(KEY_LINK_ID, link.id)
        .putString(KEY_LINK_URL, link.url)
        .putString(KEY_LINK_FILE, link.fileName)
        .putLong(KEY_LINK_SIZE, link.sizeBytes)
        .putString(KEY_LINK_KIND, link.kind.name)
        .putString(KEY_LINK_TARGET, link.target.name)
        .putString(KEY_LINK_LABEL, link.label)
        .build()
    val request =
      OneTimeWorkRequestBuilder<DownloadLinkWorker>()
        .setInputData(data)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag("nexo_link:${link.id}")
        .build()
    workManager.enqueueUniqueWork(link.id, ExistingWorkPolicy.KEEP, request)
  }

  /** Pausa conservando el `.part`: al reanudar se envía el rango HTTP desde ahí. */
  fun pause(link: DownloadLink) {
    workManager.cancelUniqueWork(link.id)
    DownloadLinkStore.update(link.id) {
      it.copy(state = LinkState.PAUSED, bytesPerSecond = 0L, remainingMs = 0L)
    }
  }

  fun cancel(link: DownloadLink) {
    workManager.cancelUniqueWork(link.id)
    File(DownloadLinkStore.pathFor(context, link).path + ".part").delete()
    DownloadLinkStore.update(link.id) {
      it.copy(
        state = LinkState.IDLE,
        receivedBytes = 0L,
        bytesPerSecond = 0L,
        remainingMs = 0L,
        error = "",
      )
    }
  }

  /** Borra el archivo final y el parcial; el enlace sigue en la lista para volver a bajarlo. */
  fun deleteFile(link: DownloadLink) {
    workManager.cancelUniqueWork(link.id)
    DownloadLinkStore.pathFor(context, link).delete()
    File(DownloadLinkStore.pathFor(context, link).path + ".part").delete()
    DownloadLinkStore.update(link.id) {
      it.copy(
        state = LinkState.IDLE,
        receivedBytes = 0L,
        bytesPerSecond = 0L,
        remainingMs = 0L,
        error = "",
      )
    }
  }

  fun removeLink(link: DownloadLink) {
    workManager.cancelUniqueWork(link.id)
    DownloadLinkStore.pathFor(context, link).delete()
    File(DownloadLinkStore.pathFor(context, link).path + ".part").delete()
    DownloadLinkStore.remove(link.id)
  }

  fun clearError(link: DownloadLink) {
    DownloadLinkStore.update(link.id) { it.copy(state = LinkState.IDLE, receivedBytes = 0L, error = "") }
  }
}
