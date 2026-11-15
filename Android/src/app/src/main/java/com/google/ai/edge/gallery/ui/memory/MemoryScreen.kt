package com.google.ai.edge.gallery.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.characters.MemoryRepository
import com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.home.BoxSectionLabel
import com.google.ai.edge.gallery.ui.home.relativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
  characterRepository: CharacterRepository,
  private val memoryRepository: MemoryRepository,
) : ViewModel() {
  val characters: StateFlow<List<RoleplayCharacter>> =
    characterRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  private val selectedId = MutableStateFlow("")
  fun select(id: String) { selectedId.value = id }
  val memories: StateFlow<List<CharacterMemory>> =
    selectedId.flatMapLatest { id -> if (id.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else memoryRepository.observe(id) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun save(m: CharacterMemory) { viewModelScope.launch { memoryRepository.save(m) } }
  fun delete(m: CharacterMemory) { viewModelScope.launch { memoryRepository.delete(m) } }
  fun clear(characterId: String) { viewModelScope.launch { memoryRepository.deleteAll(characterId) } }
}

/** Memoria a largo plazo: recuerdos por personaje, búsqueda, edición y ajustes. */
@Composable
fun MemoryScreen(onBack: () -> Unit, viewModel: MemoryViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val store = remember { RoleplaySettingsStore(context) }
  val characters by viewModel.characters.collectAsState()
  val memories by viewModel.memories.collectAsState()
  var selected by remember { mutableStateOf("") }
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<CharacterMemory?>(null) }
  var editText by remember { mutableStateOf("") }
  var confirmClear by remember { mutableStateOf(false) }
  var enabled by remember(selected) { mutableStateOf(selected.isBlank() || store.memoryEnabled(selected)) }

  if (selected.isBlank() && characters.isNotEmpty()) {
    selected = characters.first().id
    viewModel.select(selected)
  }
  val current = characters.firstOrNull { it.id == selected }
  val shown = memories.filter { query.isBlank() || it.content.contains(query, true) }

  BoxScaffold(title = "Memoria a largo plazo", subtitle = "Ver y editar lo que recuerda cada personaje", onBack = onBack) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
      if (characters.isEmpty()) {
        Text("Aún no hay personajes.", modifier = Modifier.padding(16.dp))
        return@Column
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        items(characters, key = { it.id }) { c ->
          FilterChip(selected = c.id == selected, onClick = { selected = c.id; viewModel.select(c.id) }, label = { Text(c.name) })
        }
      }
      Text("Recuerdos de ${current?.name ?: ""}", style = MaterialTheme.typography.titleLarge)
      OutlinedTextField(
        value = query, onValueChange = { query = it },
        placeholder = { Text("Buscar recuerdos…") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown.isEmpty()) item { Text("Sin recuerdos todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown, key = { it.id }) { m ->
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { editing = m; editText = m.content }) { Icon(Icons.Rounded.Edit, "Editar") }
              Column(Modifier.weight(1f)) {
                Text(relativeTime(m.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(m.content, maxLines = 6)
              }
              IconButton(onClick = { viewModel.delete(m) }) { Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.error) }
            }
          }
        }
        item {
          BoxSectionLabel("Ajustes por personaje")
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Usar memoria con este personaje", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; store.setMemoryEnabled(selected, it) })
          }
          Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Número total de recuerdos: ${memories.size}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmClear = true }, enabled = memories.isNotEmpty()) { Text("Borrar todos") }
          }
        }
      }
    }
  }

  editing?.let { target ->
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text("Editar recuerdo") },
      text = { OutlinedTextField(value = editText, onValueChange = { editText = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
      confirmButton = { TextButton(onClick = { viewModel.save(target.copy(content = editText)); editing = null }) { Text("Guardar") } },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
    )
  }
  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text("¿Borrar todos los recuerdos de ${current?.name ?: ""}?") },
      confirmButton = { TextButton(onClick = { viewModel.clear(selected); confirmClear = false }) { Text("Borrar") } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
  }
}
