package com.google.ai.edge.gallery.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.CharacterCardException
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.store.CharacterStore
import com.google.ai.edge.gallery.store.LinkTarget
import com.google.ai.edge.gallery.store.LorebookConvert
import com.google.ai.edge.gallery.store.StoreLorebook
import com.google.ai.edge.gallery.store.StoreCard
import com.google.ai.edge.gallery.store.StorePrefs
import com.google.ai.edge.gallery.store.StoreQuery
import com.google.ai.edge.gallery.store.StoreSort
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CharacterStoreViewModel
@Inject
constructor(
  private val repository: CharacterRepository,
  @ApplicationContext context: Context,
) : ViewModel() {
  private val prefs = StorePrefs(context)

  val text = MutableStateFlow("")
  private val _sort = MutableStateFlow(StoreSort.POPULAR)
  val sort: StateFlow<StoreSort> = _sort.asStateFlow()
  private val _nsfw = MutableStateFlow(prefs.nsfw)
  val nsfw: StateFlow<Boolean> = _nsfw.asStateFlow()
  private val _results = MutableStateFlow<List<StoreCard>>(emptyList())
  val results: StateFlow<List<StoreCard>> = _results.asStateFlow()
  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()
  private val _hasMore = MutableStateFlow(false)
  val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
  private val _added = MutableStateFlow(prefs.added())
  val added: StateFlow<Set<String>> = _added.asStateFlow()
  private val _busyId = MutableStateFlow<String?>(null)
  val busyId: StateFlow<String?> = _busyId.asStateFlow()
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  private val _pendingLore = MutableStateFlow<StoreLorebook?>(null)
  /** Lorebook descargado que espera a que se elija a qué personaje se añade. */
  val pendingLore: StateFlow<StoreLorebook?> = _pendingLore.asStateFlow()
  val characters: StateFlow<List<RoleplayCharacter>> =
    repository.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private var page = 1

  init {
    search()
  }

  fun clearMessage() { _message.value = null }

  fun setSort(s: StoreSort) { _sort.value = s; search() }

  fun setNsfw(on: Boolean) { prefs.nsfw = on; _nsfw.value = on; search() }

  fun search() = load(reset = true)

  fun loadMore() = load(reset = false)

  private fun load(reset: Boolean) {
    if (_loading.value) return
    if (reset) page = 1 else page++
    _loading.value = true
    val q = StoreQuery(text = text.value, sort = _sort.value, page = page, nsfw = _nsfw.value)
    viewModelScope.launch {
      try {
        val list = withContext(Dispatchers.IO) { CharacterStore.search(q) }
        _results.value = if (reset) list else _results.value + list.filter { n -> _results.value.none { it.id == n.id } }
        _hasMore.value = list.size >= q.pageSize / 2
      } catch (e: Exception) {
        if (!reset) page--
        _message.value = "No se pudo buscar: ${e.message ?: "sin conexión"}"
      } finally {
        _loading.value = false
      }
    }
  }

  fun add(card: StoreCard) {
    if (_busyId.value != null) return
    _busyId.value = card.id
    viewModelScope.launch {
      try {
        val bytes = withContext(Dispatchers.IO) { CharacterStore.download(card) }
        val c = repository.importFromBytes(bytes)
        prefs.markAdded(card.id)
        _added.value = prefs.added()
        _message.value = "Añadido: ${c.name}"
      } catch (e: CharacterCardException) {
        _message.value = e.message ?: "La tarjeta no es válida"
      } catch (e: Exception) {
        _message.value = "No se pudo descargar: ${e.message ?: "error"}"
      } finally {
        _busyId.value = null
      }
    }
  }

  fun addFromLink(link: String) {
    if (_busyId.value != null || link.isBlank()) return
    _busyId.value = "link"
    viewModelScope.launch {
      try {
        val first = CharacterStore.resolve(link)
        if (first.site == LinkTarget.Site.CHUB_LORE) {
          _pendingLore.value = withContext(Dispatchers.IO) { CharacterStore.downloadLorebook(first.id) }
        } else {
          val (target, bytes) = withContext(Dispatchers.IO) { CharacterStore.downloadLink(link) }
          val c = repository.importFromBytes(bytes)
          _message.value = "Añadido desde ${target.site.label}: ${c.name}"
        }
      } catch (e: CharacterCardException) {
        _message.value = e.message ?: "La tarjeta no es válida"
      } catch (e: Exception) {
        _message.value = "No se pudo descargar: ${e.message ?: "error"}"
      } finally {
        _busyId.value = null
      }
    }
  }

  fun dismissLore() { _pendingLore.value = null }

  /** Añade las entradas del lorebook pendiente al personaje elegido (sin duplicar las que ya tiene). */
  fun attachLore(characterId: String) {
    val lore = _pendingLore.value ?: return
    _pendingLore.value = null
    viewModelScope.launch {
      try {
        val c = repository.getById(characterId) ?: return@launch
        repository.save(c.copy(characterBookJson = LorebookConvert.merge(c.characterBookJson, lore)))
        _message.value = "Lorebook «${lore.name}» (${lore.entryCount} entradas) añadido a ${c.name}"
      } catch (e: Exception) {
        _message.value = "No se pudo añadir el lorebook: ${e.message ?: "error"}"
      }
    }
  }
}
