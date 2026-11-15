package com.google.ai.edge.gallery.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.google.ai.edge.gallery.data.characters.EnginePreset
import com.google.ai.edge.gallery.data.characters.EnginePresetStore
import com.google.ai.edge.gallery.data.characters.SamplerMode
import com.google.ai.edge.gallery.data.characters.SamplerSettings
import com.google.ai.edge.gallery.data.characters.SamplerSettingsStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.MmprojFiles
import com.google.ai.edge.gallery.ui.roleplay.PersonaManagerDialog
import com.google.ai.edge.gallery.ui.roleplay.RoleplayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Model.isGguf() = downloadFileName.endsWith(".gguf", ignoreCase = true)

/**
 * Configuración de inferencia: se elige aquí lo que usan todos los chats (modelo, visión y
 * persona) antes de hablar con nadie. Los ajustes finos siguen en Configuración.
 */
@Composable
fun BoxInferenceScreen(
  models: List<Model>,
  selectedModel: String?,
  onSelectModel: (String) -> Unit,
  onAddModel: () -> Unit,
  onOpenHf: () -> Unit = {},
  onOpenImageGen: () -> Unit,
  onBack: () -> Unit,
  viewModel: RoleplayViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val perfStore = remember { EnginePerfStore(context) }
  var mmproj by remember { mutableStateOf(perfStore.load().mmproj) }
  var mmprojFiles by remember { mutableStateOf(MmprojFiles.list(context)) }
  var importing by remember { mutableStateOf(false) }
  var filter by remember { mutableStateOf("todos") }
  var showPersonas by remember { mutableStateOf(false) }
  val presetStore = remember { EnginePresetStore(context) }
  val samplerStore = remember { SamplerSettingsStore(context) }
  var presets by remember { mutableStateOf(presetStore.all()) }
  var showSavePreset by remember { mutableStateOf(false) }
  var presetName by remember { mutableStateOf("") }
  var note by remember { mutableStateOf<String?>(null) }
  val personas by viewModel.personas.collectAsState()

  val mmprojPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        importing = true
        scope.launch {
          val name = withContext(Dispatchers.IO) { runCatching { MmprojFiles.import(context, uri) }.getOrNull() }
          importing = false
          if (name == null) note = "No se pudo importar: debe ser un .gguf (mmproj)"
          else {
            mmprojFiles = MmprojFiles.list(context)
            mmproj = name
            perfStore.save(perfStore.load().copy(mmproj = name))
            note = "Visión lista: $name. Vuelve a cargar el modelo para aplicarla."
          }
        }
      }
    }

  if (showSavePreset) {
    AlertDialog(
      onDismissRequest = { showSavePreset = false },
      title = { Text("Guardar motor") },
      text = {
        Column {
          Text(
            "Guarda el modelo, la visión, la persona y el muestreo actuales para volver a ellos con un toque.",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = presetName, onValueChange = { presetName = it },
            placeholder = { Text("p. ej., Rápido para el móvil") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = presetName.isNotBlank(),
          onClick = {
            presetStore.save(
              EnginePreset(
                name = presetName,
                model = selectedModel ?: "",
                mmproj = mmproj,
                personaId = personas.firstOrNull { it.isActive }?.id ?: "",
                samplerMode = samplerStore.mode().id,
                samplerCustom = samplerStore.custom()?.let { SamplerSettings.encode(it) } ?: "",
              )
            )
            presets = presetStore.all(); showSavePreset = false; presetName = ""
          },
        ) { Text("Guardar") }
      },
      dismissButton = { TextButton(onClick = { showSavePreset = false }) { Text("Cancelar") } },
    )
  }

  if (showPersonas) {
    PersonaManagerDialog(
      personas = personas,
      onDismiss = { showPersonas = false },
      onSelect = { viewModel.selectPersona(it) },
      onSave = { viewModel.savePersona(it) },
      onDelete = { viewModel.deletePersona(it) },
    )
  }

  BoxScaffold(
    title = "Configuración de inferencia",
    subtitle = "Elige el modelo, la visión y tu persona para todos los chats",
    onBack = onBack,
  ) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
      BoxSectionLabel("Motores guardados")
      BoxActionCard(
        icon = Icons.Rounded.Bookmark,
        title = "Guardar combinación actual",
        subtitle = "Modelo, visión, persona y muestreo en un perfil",
        onClick = { showSavePreset = true },
        dashed = true,
      )
      presets.forEach { pr ->
        BoxActionCard(
          icon = Icons.Rounded.Bookmark,
          title = pr.name,
          subtitle = listOfNotNull(
            pr.model.takeIf { it.isNotBlank() }?.let { n -> models.firstOrNull { it.name == n }?.let { m -> m.displayName.ifBlank { m.name } } ?: n },
            pr.mmproj.takeIf { it.isNotBlank() }?.let { "visión" },
            personas.firstOrNull { it.id == pr.personaId }?.name,
            pr.samplerMode.takeIf { it.isNotBlank() },
          ).joinToString(" · ").ifBlank { "Vacío" },
          onClick = {
            val missing = mutableListOf<String>()
            if (pr.model.isNotBlank()) {
              if (models.any { it.name == pr.model }) onSelectModel(pr.model) else missing += "modelo"
            }
            if (pr.mmproj.isNotBlank() && pr.mmproj !in mmprojFiles) missing += "visión"
            else {
              mmproj = pr.mmproj
              perfStore.save(perfStore.load().copy(mmproj = pr.mmproj))
            }
            if (pr.personaId.isNotBlank()) {
              val persona = personas.firstOrNull { it.id == pr.personaId }
              if (persona != null) viewModel.selectPersona(persona) else missing += "persona"
            }
            if (pr.samplerMode.isNotBlank()) {
              samplerStore.save(SamplerMode.fromId(pr.samplerMode), pr.samplerCustom.takeIf { it.isNotBlank() }?.let { SamplerSettings.decodeOrNull(it) })
            }
            note = "Motor «${pr.name}» aplicado" + if (missing.isEmpty()) "" else " (no se encontró: ${missing.joinToString()})"
          },
          trailing = {
            IconButton(onClick = { presetStore.delete(pr.id); presets = presetStore.all() }) {
              Icon(Icons.Rounded.Delete, "Borrar motor", tint = MaterialTheme.colorScheme.error)
            }
          },
        )
      }

      BoxSectionLabel("Mis modelos")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        listOf("todos" to "TODOS", "gguf" to "GGUF", "litert" to "LITERT").forEach { (k, label) ->
          FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
        }
      }
      BoxActionCard(
        icon = Icons.Rounded.Download,
        title = "Descargar de Hugging Face",
        subtitle = "Busca modelos .gguf y descárgalos directo al teléfono",
        onClick = onOpenHf,
      )
      BoxActionCard(
        icon = Icons.Rounded.Add,
        title = "Añadir modelo personalizado",
        subtitle = "Importa un archivo .gguf o .litertlm de tu teléfono",
        onClick = onAddModel,
        dashed = true,
      )
      val shown =
        models.filter {
          when (filter) {
            "gguf" -> it.isGguf()
            "litert" -> !it.isGguf()
            else -> true
          }
        }
      if (shown.isEmpty()) {
        Text(
          "No hay modelos descargados o importados con este filtro.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(8.dp),
        )
      }
      shown.forEach { m ->
        val isSel = m.name == selectedModel
        BoxActionCard(
          icon = Icons.Rounded.Psychology,
          title = m.displayName.ifBlank { m.name },
          subtitle = (if (m.isGguf()) "GGUF · llama.cpp" else "LiteRT-LM") + (if (isSel) " · en uso" else ""),
          onClick = { onSelectModel(m.name) },
          selected = isSel,
          trailing =
            if (isSel) {
              { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
            } else null,
        )
      }

      BoxSectionLabel("Visión LLM")
      BoxActionCard(
        icon = Icons.Rounded.Add,
        title = if (importing) "Importando…" else "Añadir modelo de visión",
        subtitle = "Proyector multimodal (mmproj .gguf) que combine con tu modelo GGUF",
        onClick = { if (!importing) mmprojPicker.launch(arrayOf("*/*")) },
        dashed = true,
      )
      BoxActionCard(
        icon = Icons.Rounded.RemoveRedEye,
        title = "Sin visión",
        subtitle = "El modelo solo lee texto",
        onClick = {
          mmproj = ""
          perfStore.save(perfStore.load().copy(mmproj = ""))
        },
        selected = mmproj.isBlank(),
      )
      mmprojFiles.forEach { f ->
        BoxActionCard(
          icon = Icons.Rounded.RemoveRedEye,
          title = f,
          subtitle = "Debe ser de la misma familia y tamaño que el modelo",
          onClick = {
            mmproj = f
            perfStore.save(perfStore.load().copy(mmproj = f))
            note = "Visión elegida. Vuelve a cargar el modelo para aplicarla."
          },
          selected = f == mmproj,
        )
      }

      BoxSectionLabel("Generación de imágenes")
      BoxActionCard(
        icon = Icons.Rounded.Image,
        title = "Generador de imágenes",
        subtitle = "Elige o importa un modelo de generación de imágenes",
        onClick = onOpenImageGen,
      )

      BoxSectionLabel("Mis personas")
      BoxActionCard(
        icon = Icons.Rounded.Person,
        title = personas.firstOrNull { it.isActive }?.name ?: "Añadir persona",
        subtitle =
          personas.firstOrNull { it.isActive }?.description?.takeIf { it.isNotBlank() }
            ?: "Tu perfil en el chat: añade, edita o cambia de persona",
        onClick = { showPersonas = true },
        selected = personas.any { it.isActive },
      )

      note?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      }
      Spacer(Modifier.height(28.dp))
    }
  }
}
