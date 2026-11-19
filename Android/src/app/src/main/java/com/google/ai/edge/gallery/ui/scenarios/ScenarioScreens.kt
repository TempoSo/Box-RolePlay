package com.google.ai.edge.gallery.ui.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.characters.Scenario
import com.google.ai.edge.gallery.data.characters.RoleplayModelResolver
import com.google.ai.edge.gallery.data.characters.toStringList
import com.google.ai.edge.gallery.ui.common.TagChips
import com.google.ai.edge.gallery.ui.home.BoxActionCard
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.home.BoxSectionLabel

/** Lista de escenas: buscar, crear, editar, borrar y empezar una. */
@Composable
fun ScenariosScreen(
    onBack: () -> Unit,
    availableModels: List<String>,
    defaultModel: String?,
    onEdit: (String) -> Unit,
    onOpenChat: (modelName: String, conversationId: String) -> Unit,
    onNeedModel: () -> Unit,
    viewModel: ScenariosViewModel = hiltViewModel(),
) {
    val scenarios by viewModel.scenarios.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Scenario?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) { message?.let { note = it; viewModel.clearMessage() } }

    val shown = scenarios.filter {
        query.isBlank() || it.name.contains(query, true) || it.prompt.contains(query, true) ||
            it.tags.any { t -> t.contains(query, true) }
    }

    BoxScaffold(
        title = "Escenarios de roleplay",
        subtitle = "Varios personajes en una escena: tú controlas a uno y la IA al resto",
        onBack = onBack,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar escenarios, etiquetas…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            BoxSectionLabel("Todos los escenarios (${scenarios.size})")
            BoxActionCard(
                icon = Icons.Rounded.Add,
                title = "Crear un escenario",
                subtitle = "Crea tu propio mundo, elenco y trama",
                onClick = { onEdit("new") },
                dashed = true,
            )
            shown.forEach { s ->
                val names = characters.filter { it.id in s.participantIds }.map { it.name }
                BoxActionCard(
                    icon = Icons.Rounded.Groups,
                    title = s.name.ifBlank { "Escena sin nombre" },
                    subtitle = (s.prompt.ifBlank { "Sin descripción" }) +
                        "\n" + (if (names.isEmpty()) "Sin personajes" else names.joinToString(", ")) +
                        (if (s.tags.isNotEmpty()) "  ·  " + s.tags.joinToString(" ") { "#$it" } else ""),
                    onClick = {
                        val model = RoleplayModelResolver.resolve(characterModel = null, defaultModel = defaultModel, available = availableModels)
                        if (model == null) onNeedModel()
                        else viewModel.startScene(s, model) { convId -> onOpenChat(model, convId) }
                    },
                    trailing = {
                        Row {
                            IconButton(onClick = { onEdit(s.id) }) { Icon(Icons.Rounded.Edit, "Editar") }
                            IconButton(onClick = { toDelete = s }) { Icon(Icons.Rounded.Delete, "Borrar") }
                        }
                    },
                )
            }
            if (scenarios.isEmpty()) {
                Text(
                    "Aún no tienes escenarios. Crea uno y elige qué personajes de tu biblioteca participan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar «${s.name.ifBlank { "escena" }}»?") },
            text = { Text("Los chats ya creados con esta escena se conservan.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(s.id); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Crear o editar una escena ("new" = nueva). */
@Composable
fun ScenarioEditorScreen(
    scenarioId: String,
    onBack: () -> Unit,
    viewModel: ScenariosViewModel = hiltViewModel(),
) {
    val characters by viewModel.characters.collectAsState()
    val scenarios by viewModel.scenarios.collectAsState()
    // BUG-006: etiquetas ya usadas en otras escenas y en personajes de la biblioteca.
    val tagSuggestions =
        remember(scenarios, characters) {
            TagChips.collect(scenarios.flatMap { it.tags }, characters.flatMap { it.tagsJson.toStringList() })
        }
    val existing = remember(scenarioId) { if (scenarioId == "new") null else viewModel.get(scenarioId) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var prompt by remember { mutableStateOf(existing?.prompt ?: "") }
    var tagText by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var narrator by remember { mutableStateOf(existing?.narrator ?: true) }
    var participants by remember { mutableStateOf(existing?.participantIds ?: emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    BoxScaffold(title = if (existing == null) "Nuevo roleplay" else "Editar roleplay", subtitle = "Crea una escena para que tus personajes la habiten", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxSectionLabel("Nombre del roleplay")
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = false },
                    placeholder = { Text("p. ej., La última linterna de Eldermoor") },
                    isError = nameError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                BoxSectionLabel("Prompt del escenario")
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    placeholder = { Text("Define la escena. ¿Dónde estamos? ¿Qué acaba de ocurrir? ¿Cuál es el ambiente?") },
                    minLines = 5, modifier = Modifier.fillMaxWidth(),
                )
                Text("Esto se muestra a la IA al inicio de cada conversación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                BoxSectionLabel("Etiquetas")
                Text(
                    "Toca una etiqueta para añadirla o quitarla; también puedes escribir otras nuevas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TagChips(suggestions = tagSuggestions, value = tagText, onValueChange = { tagText = it })
                OutlinedTextField(
                    value = tagText, onValueChange = { tagText = it },
                    placeholder = { Text("fantasía, romance, ciencia ficción") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                BoxSectionLabel("Participantes (${participants.size})")
                BoxActionCard(
                    icon = Icons.Rounded.Groups,
                    title = if (participants.isEmpty()) "Elegir personajes de la biblioteca" else "Cambiar personajes",
                    subtitle = if (participants.isEmpty()) "Añade al menos un personaje para dar vida a la escena"
                    else characters.filter { it.id in participants }.joinToString(", ") { it.name },
                    onClick = { showPicker = true },
                    dashed = participants.isEmpty(),
                )

                BoxSectionLabel("Opciones de escena")
                BoxActionCard(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Incluir narrador",
                    subtitle = "La IA también puede narrar el entorno, los eventos y las acciones",
                    onClick = { narrator = !narrator },
                    trailing = { Switch(checked = narrator, onCheckedChange = { narrator = it }) },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        if (name.isBlank()) { nameError = true; return@Button }
                        viewModel.save(
                            Scenario(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                prompt = prompt.trim(),
                                tags = tagText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                participantIds = participants,
                                narrator = narrator,
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }

    if (showPicker) {
        var picked by remember { mutableStateOf(participants.toSet()) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Seleccionar personajes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (characters.isEmpty()) {
                        Text("Aún no tienes personajes. Créalos o impórtalos en «Personajes».")
                    }
                    characters.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = c.id in picked,
                                onCheckedChange = { on -> picked = if (on) picked + c.id else picked - c.id },
                            )
                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { participants = characters.map { it.id }.filter { it in picked }; showPicker = false }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        )
    }
}
