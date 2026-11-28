package com.google.ai.edge.gallery.data.downloads

import android.content.Context
import androidx.core.content.edit
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fuente de verdad de la lista unificada. Vive a nivel de proceso (no de pantalla): el worker
 * escribe el progreso aquí y la UI solo observa. Todo se guarda en SharedPreferences para que los
 * estados sobrevivan a rotaciones, cambio de pestaña y reinicios.
 */
object DownloadLinkStore {
  private const val PREFS_NAME = "model_links"
  private const val KEY_LINKS = "links"
  private const val KEY_CATALOG_VERSION = "catalog_version"
  private const val IMPORTS_DIR = "__imports"

  private val json = Json { ignoreUnknownKeys = true }
  private val _links = MutableStateFlow<List<DownloadLink>>(emptyList())
  val links: StateFlow<List<DownloadLink>> = _links

  @Volatile
  private var ready = false
  private var prefs: android.content.SharedPreferences? = null

  /** Se llama desde la app, el ViewModel y el worker; idempotente. */
  fun attach(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val stored = load()
      val needsCatalogRefresh =
        prefs?.getInt(KEY_CATALOG_VERSION, 0) != DownloadLinkCatalog.VERSION
      _links.value =
        if (needsCatalogRefresh) {
          // El catálogo cambió (F16, nuevos modelos…): las entradas fijas se sustituyen, pero los
          // enlaces que el usuario añadió desde HF se conservan.
          val custom = stored.filter { !it.pinned }
          DownloadLinkCatalog.base + custom
        } else {
          val byId = stored.associateBy { it.id }
          DownloadLinkCatalog.base.map { byId[it.id] ?: it } + stored.filter { !it.pinned }
        }
      if (needsCatalogRefresh) {
        prefs?.edit { putInt(KEY_CATALOG_VERSION, DownloadLinkCatalog.VERSION) }
      }
      ready = true
    }
  }

  private fun load(): List<DownloadLink> {
    val raw = prefs?.getString(KEY_LINKS, "") ?: return emptyList()
    if (raw.isBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<DownloadLink>>(raw) }.getOrDefault(emptyList())
  }

  private fun save() {
    prefs?.edit { putString(KEY_LINKS, json.encodeToString(_links.value)) }
  }

  fun snapshot(): List<DownloadLink> = _links.value

  fun upsert(link: DownloadLink) {
    attachIfNeeded()
    synchronized(this) {
      val current = _links.value.toMutableList()
      val index = current.indexOfFirst { it.id == link.id }
      if (index >= 0) current[index] = link else current.add(link)
      _links.value = current
      save()
    }
  }

  fun update(id: String, transform: (DownloadLink) -> DownloadLink) {
    attachIfNeeded()
    synchronized(this) {
      var changed = false
      val current =
        _links.value.map { link ->
          if (link.id == id) {
            changed = true
            transform(link)
          } else link
        }
      if (changed) {
        _links.value = current
        save()
      }
    }
  }

  fun remove(id: String) {
    attachIfNeeded()
    synchronized(this) {
      _links.value = _links.value.filterNot { it.id == id }
      save()
    }
  }

  /** Un COMPLETED cuyo archivo ya no está pasa a IDLE: evita tarjetas "descargadas" falsas. */
  fun reconcileFiles(context: Context) {
    attach(context)
    val imports = File(context.getExternalFilesDir(null), IMPORTS_DIR)
    val mmproj = File(context.filesDir, "mmproj")
    snapshot()
      .filter { it.state == LinkState.COMPLETED }
      .forEach { link ->
        val dir = if (link.target == LinkTarget.MMPROJ) mmproj else imports
        if (!File(dir, link.fileName).exists()) {
          update(link.id) { it.copy(state = LinkState.IDLE, receivedBytes = 0L, error = "") }
        }
      }
  }

  fun pathFor(context: Context, link: DownloadLink): File {
    val dir =
      if (link.target == LinkTarget.MMPROJ) {
        File(context.filesDir, "mmproj")
      } else {
        File(context.getExternalFilesDir(null), IMPORTS_DIR)
      }
    return File(dir, link.fileName)
  }

  private fun attachIfNeeded() {
    check(ready) { "DownloadLinkStore necesita attach(context) antes de usarse" }
  }
}
