package com.google.ai.edge.gallery.ui.modelmanager

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.IMPORTS_DIR
import com.google.ai.edge.gallery.data.characters.HfApi
import com.google.ai.edge.gallery.data.characters.HfClient
import com.google.ai.edge.gallery.data.characters.HfFile
import com.google.ai.edge.gallery.data.characters.HfFileKind
import com.google.ai.edge.gallery.data.characters.HfRepo
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.MmprojFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HfDownload(val received: Long, val total: Long)

/** Buscar modelos en Hugging Face, ver sus archivos y descargarlos (solo repositorios públicos). */
@HiltViewModel
class HfBrowserViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _results = MutableStateFlow<List<HfRepo>>(emptyList())
    val results: StateFlow<List<HfRepo>> = _results.asStateFlow()
    private val _repo = MutableStateFlow<String?>(null)
    val repo: StateFlow<String?> = _repo.asStateFlow()
    private val _files = MutableStateFlow<List<HfFile>>(emptyList())
    val files: StateFlow<List<HfFile>> = _files.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _downloads = MutableStateFlow<Map<String, HfDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, HfDownload>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()

    val ramBytes: Long by lazy {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        mi.totalMem
    }

    fun clearMessage() { _message.value = null }

    /** Texto que parece "usuario/repo" o un enlace abre ese repositorio; si no, es una búsqueda. */
    fun searchOrOpen(input: String) {
        val q = input.trim()
        if (q.isEmpty()) return
        HfApi.parseRepoId(q)?.takeIf { q.contains('/') && !q.contains(' ') }?.let { openRepo(it); return }
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true; _repo.value = null
            try { _results.value = HfClient.search(q).also { if (it.isEmpty()) _message.value = "Sin resultados para «$q»." } }
            catch (e: Exception) { _message.value = "No se pudo buscar: ${e.message ?: "sin conexión"}" }
            finally { _loading.value = false }
        }
    }

    fun openRepo(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val f = HfClient.files(id)
                _files.value = f; _repo.value = id
                if (f.isEmpty()) _message.value = "Este repositorio no tiene archivos .gguf ni .litertlm."
            } catch (e: Exception) { _message.value = "No se pudo abrir $id: ${e.message ?: "sin conexión"}" }
            finally { _loading.value = false }
        }
    }

    fun closeRepo() { _repo.value = null }

    fun cancel(file: HfFile) { cancelled += file.path }

    /**
     * [onModel]: se llama al terminar (nombre, tamaño y si debe aparecer en Visión) para registrarlo.
     */
    fun download(
        file: HfFile,
        onModel: (fileName: String, size: Long, supportImage: Boolean) -> Unit,
        supportImage: Boolean = false,
    ) {
        val repoId = _repo.value ?: return
        val kind = HfApi.kind(file)
        if (kind == HfFileKind.SPLIT) {
            _message.value = "Este modelo viene partido en varios archivos y todavía no se puede descargar así. Busca una versión de un solo archivo."
            return
        }
        if (jobs[file.path]?.isActive == true) return
        val name = HfApi.safeFileName(file.path)
        val dest =
            if (kind == HfFileKind.MMPROJ) File(MmprojFiles.dir(context), name)
            else File(File(context.getExternalFilesDir(null), IMPORTS_DIR), name)
        cancelled -= file.path
        _downloads.update { it + (file.path to HfDownload(0, file.size)) }
        jobs[file.path] = viewModelScope.launch(Dispatchers.IO) {
            try {
                HfClient.download(
                    url = HfApi.downloadUrl(repoId, file.path),
                    dest = dest,
                    expectedSize = file.size,
                    onProgress = { r, t -> _downloads.update { it + (file.path to HfDownload(r, t)) } },
                    isCancelled = { file.path in cancelled },
                )
                if (kind == HfFileKind.MMPROJ) {
                    // BUG-046: seleccionar el proyector al bajarlo evita que Visión siga sin verlo.
                    EnginePerfStore(context).setMmproj(name)
                    _message.value = "Visión descargada y seleccionada: $name"
                } else {
                    onModel(name, dest.length(), supportImage)
                    _message.value = "Modelo listo: $name${if (supportImage) " (disponible en Visión)" else ""}"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "La descarga falló"
            } finally {
                _downloads.update { it - file.path }
            }
        }
    }
}
