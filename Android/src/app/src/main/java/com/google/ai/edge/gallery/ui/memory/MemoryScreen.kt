package com.google.ai.edge.gallery.ui.memory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
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
import com.google.ai.edge.gallery.data.characters.MemoryAbsorbPrompt
import com.google.ai.edge.gallery.data.characters.MemoryConsolidation
import com.google.ai.edge.gallery.data.characters.MemoryGraphBuilder
import com.google.ai.edge.gallery.data.characters.MemoryRepository
import com.google.ai.edge.gallery.data.characters.PersonaRepository
import com.google.ai.edge.gallery.data.characters.RawMemory
import com.google.ai.edge.gallery.data.characters.RawMemoryStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
  @ApplicationContext context: Context,
  private val characterRepository: CharacterRepository,
  private val memoryRepository: MemoryRepository,
  private val personaRepository: PersonaRepository,
) : ViewModel() {
  private val rawStore = RawMemoryStore(context)
  private val _raw = MutableStateFlow<List<RawMemory>>(emptyList())
  val raw: StateFlow<List<RawMemory>> = _raw.asStateFlow()
  private fun refreshRaw() { _raw.value = rawStore.forCharacter(selectedId.value) }
  fun editRaw(id: String, text: String) { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { rawStore.updateText(id, text); refreshRaw() } }
  fun deleteRaw(id: String) { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { rawStore.remove(setOf(id)); refreshRaw() } }

  /**
   * Fase 3: «Absorber» pasa a ser «Consolidar». En una sola pasada destila las notas ligeras,
   * re-resume los recuerdos antiguos por tema, invalida los originales, limpia sus nodos del grafo
   * y borra **todas** las notas puente. Si el modelo no rinde nada, no se borra nada. La firma del
   * bloque inyectado sigue siendo ids|día|notas, así que la caché de prefijo no se rompe.
   */
  fun consolidate(
    character: RoleplayCharacter,
    absorber: MemoryAbsorber,
    preferredModel: String?,
    onMessage: (String) -> Unit,
  ) {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val notes = rawStore.forCharacter(character.id)
      val ahora = System.currentTimeMillis()
      val cohortes = MemoryConsolidation.cohortes(memoryRepository.getAll(character.id), ahora)
      if (notes.isEmpty() && cohortes.isEmpty()) {
        onMessage("No hay notas ni recuerdos antiguos suficientes para consolidar (3 recuerdos de al menos 7 días).")
        return@launch
      }
      val persona = personaRepository.getActive()
      val userName = persona?.name ?: "User"
      // BUG-002: por tandas, para no desbordar el contexto del modelo local.
      val tandas = MemoryAbsorbPrompt.tandasFromNewestFirst(notes, sizeOf = { it.text.length })
      val prompts =
        tandas.map { tanda ->
          MemoryAbsorbPrompt.build(
            userName,
            character.name,
            tanda.map { it.text },
            persona = persona?.description ?: "",
            description = character.description,
            personality = character.personality,
          )
        } + cohortes.map { cohorte ->
          MemoryConsolidation.prompt(
            cohorte,
            userName,
            character.name,
            persona = persona?.description ?: "",
            description = character.description,
            personality = character.personality,
          )
        }
      absorber.run(preferredModel, prompts,
        onProgress = { done, total ->
          onMessage(if (total <= 1) "Consolidando…" else "Consolidando tanda ${done + 1} de $total…")
        },
        onResult = { outputs ->
          val extraidos = outputs.map { MemoryAbsorbPrompt.parse(it) }
          if (extraidos.all { it == null }) {
            onMessage("El modelo no generó un resumen útil. No se ha borrado nada.")
            return@run
          }
          viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var destilados = 0
            var consolidados = 0
            val idsInvalidados = mutableSetOf<String>()
            var anteriorId = memoryRepository.getAll(character.id).firstOrNull()?.id.orEmpty()
            extraidos.forEachIndexed { i, extraido ->
              if (extraido == null) return@forEachIndexed
              if (i < tandas.size) {
                val keysJson =
                  extraido.keys.joinToString(",", "[", "]") {
                    "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                  }
                val guardado =
                  memoryRepository.saveStructured(
                    memory =
                      CharacterMemory(
                        characterId = character.id,
                        content = extraido.resumen,
                        topic = extraido.topic,
                        keysJson = keysJson,
                        importance = extraido.importance,
                        updatedAt = ahora,
                        sourceConversationId = "",
                      ),
                    rawText = tandas[i].joinToString("\n") { it.text },
                    userName = userName,
                    charName = character.name,
                    anteriorId = anteriorId,
                  )
                if (guardado) {
                  anteriorId = memoryRepository.getAll(character.id).firstOrNull()?.id.orEmpty()
                  destilados++
                }
              } else {
                val cohorte = cohortes.getOrNull(i - tandas.size) ?: return@forEachIndexed
                val nuevo = MemoryConsolidation.unir(cohorte, extraido, ahora) ?: return@forEachIndexed
                val guardado =
                  memoryRepository.saveStructured(
                    memory = nuevo,
                    rawText = cohorte.joinToString("\n") { it.content },
                    userName = userName,
                    charName = character.name,
                  )
                val plan = MemoryConsolidation.plan(nuevo, cohorte, guardado)
                if (plan != null) {
                  idsInvalidados.addAll(plan.idsAInvalidar)
                  consolidados++
                }
              }
            }
            if (idsInvalidados.isNotEmpty()) {
              memoryRepository.markConsolidated(idsInvalidados.toList(), ahora)
              memoryRepository.refinarGrafo(character.id, idsInvalidados.toList())
            }
            // Limpieza de la Fase 3: se van TODAS las notas, incluidas las 2 puente.
            val borradas = notes.size
            if (borradas > 0) { rawStore.remove(notes.map { it.id }.toSet()); refreshRaw() }
            onMessage(
              "$destilados recuerdo(s) destilado(s), $consolidados consolidado(s); " +
                "${idsInvalidados.size} original(es) dejado(s) de inyectar y $borradas nota(s) eliminada(s).",
            )
          }
        },
        onError = { onMessage(it) })
    }
  }

  val characters: StateFlow<List<RoleplayCharacter>> =
    characterRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  private val selectedId = MutableStateFlow("")
  fun select(id: String) { selectedId.value = id; refreshRaw() }
  val memories: StateFlow<List<CharacterMemory>> =
    selectedId.flatMapLatest { id -> if (id.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else memoryRepository.observe(id) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  suspend fun grafo(characterId: String) = memoryRepository.grafo(characterId)

  fun save(m: CharacterMemory) {
    viewModelScope.launch {
      val charName = characterRepository.getById(m.characterId)?.name.orEmpty()
      val userName = personaRepository.getActive()?.name.orEmpty()
      memoryRepository.saveStructured(
        memory = m,
        rawText = m.content,
        userName = userName,
        charName = charName,
      )
    }
  }
  fun delete(m: CharacterMemory) { viewModelScope.launch { memoryRepository.delete(m) } }
  fun clear(characterId: String) { viewModelScope.launch { memoryRepository.deleteAll(characterId) } }
}

/** Memoria a largo plazo: recuerdos por personaje, búsqueda, edición y ajustes. */
@Composable
fun MemoryScreen(
  onBack: () -> Unit,
  absorber: MemoryAbsorber,
  preferredModel: String?,
  viewModel: MemoryViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val store = remember { RoleplaySettingsStore(context) }
  val characters by viewModel.characters.collectAsState()
  val memories by viewModel.memories.collectAsState()
  val rawNotes by viewModel.raw.collectAsState()
  var consolidating by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf("") }
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<CharacterMemory?>(null) }
  var editText by remember { mutableStateOf("") }
  // BUG-008: edición de los recuerdos "sin procesar".
  var editingRaw by remember { mutableStateOf<RawMemory?>(null) }
  var editRawText by remember { mutableStateOf("") }
  var confirmClear by remember { mutableStateOf(false) }
  var mostrarGrafo by remember { mutableStateOf(false) }
  var grafo by remember { mutableStateOf<MemoryGraphBuilder.Grafo?>(null) }
  var filtroGrafo by remember { mutableStateOf(FiltroGrafo.TODO) }
  LaunchedEffect(mostrarGrafo, selected) {
    if (mostrarGrafo && selected.isNotBlank()) grafo = viewModel.grafo(selected)
  }
  var enabled by remember(selected) { mutableStateOf(selected.isBlank() || store.memoryEnabled(selected)) }
  var auto by remember(selected) { mutableStateOf(selected.isBlank() || store.autoMemory(selected)) }
  var note by remember { mutableStateOf<String?>(null) }

  androidx.compose.runtime.LaunchedEffect(characters) {
    if (selected.isBlank() && characters.isNotEmpty()) {
      selected = characters.first().id
      viewModel.select(selected)
    }
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
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Recuerdos de ${current?.name ?: ""}",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { mostrarGrafo = true }) { Text("Ver grafo") }
      }
      OutlinedTextField(
        value = query, onValueChange = { query = it },
        placeholder = { Text("Buscar recuerdos…") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown.isEmpty()) item { Text("Sin recuerdos todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown, key = { it.id }) { m ->
          MemoryCard(
            memory = m,
            onEdit = { editing = m; editText = m.content },
            onDelete = { viewModel.delete(m) },
          )
        }
        item {
          BoxSectionLabel("Ajustes por personaje")
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Usar memoria con este personaje", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; store.setMemoryEnabled(selected, it) })
          }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recopilar recuerdos automáticamente", Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { auto = it; store.setAutoMemory(selected, it) })
          }
          BoxSectionLabel("Recuerdos sin procesar (${rawNotes.size})")
          Text(
            note ?: "Notas ligeras que se guardan al instante mientras chateas. Cada una ya nace como recuerdo estructurado y suma nodos al grafo; «Consolidar» las destila, re-resume los recuerdos antiguos y elimina todas las notas, incluidas las 2 puente.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = {
              current?.let { c ->
                consolidating = true; note = "Consolidando… (puede tardar si el modelo debe cargarse; no salgas de esta pantalla)"
                viewModel.consolidate(c, absorber, preferredModel) { msg -> note = msg; consolidating = false }
              }
            },
            enabled = (rawNotes.isNotEmpty() || memories.size >= 3) && !consolidating,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          ) { Text(if (consolidating) "Consolidando…" else "Consolidar (absorber + limpiar)") }
          Text(
            "«Consolidar» sustituye a «Absorber»: destila notas, re-resume recuerdos antiguos, limpia sus nodos del grafo y elimina TODAS las notas puente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          rawNotes.take(30).forEach { r ->
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(relativeTime(r.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(r.text, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { editingRaw = r; editRawText = r.text }) { Icon(Icons.Rounded.Edit, "Editar nota") }
                IconButton(onClick = { viewModel.deleteRaw(r.id) }) { Icon(Icons.Rounded.Delete, "Borrar nota", tint = MaterialTheme.colorScheme.error) }
              }
            }
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
  editingRaw?.let { target ->
    AlertDialog(
      onDismissRequest = { editingRaw = null },
      title = { Text("Editar nota") },
      text = { OutlinedTextField(value = editRawText, onValueChange = { editRawText = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(
          onClick = { viewModel.editRaw(target.id, editRawText); editingRaw = null },
          enabled = editRawText.isNotBlank(),
        ) { Text("Guardar") }
      },
      dismissButton = { TextButton(onClick = { editingRaw = null }) { Text("Cancelar") } },
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
  if (mostrarGrafo) {
    AlertDialog(
      onDismissRequest = { mostrarGrafo = false },
      title = { Text("Grafo de ${current?.name ?: ""}") },
      text = {
        Column {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
          ) {
            FiltroGrafo.entries.forEach { filtro ->
              FilterChip(
                selected = filtro == filtroGrafo,
                onClick = { filtroGrafo = filtro },
                label = { Text(filtro.etiqueta) },
              )
            }
          }
          val g = grafo
          if (g == null) {
            Text("Cargando grafo…", style = MaterialTheme.typography.bodySmall)
          } else {
            Text(
              "${g.nodes.size} nodos · ${g.edges.size} relaciones",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
              Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
            ) { MemoryGraphView(g, filtroGrafo) }
          }
        }
      },
      confirmButton = { TextButton(onClick = { mostrarGrafo = false }) { Text("Cerrar") } },
    )
  }
}
