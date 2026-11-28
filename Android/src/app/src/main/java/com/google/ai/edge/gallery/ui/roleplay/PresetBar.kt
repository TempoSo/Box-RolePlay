package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Un preset de la barra: [deletable] = false para los que vienen de fábrica. */
data class PresetBarItem(val key: String, val label: String, val deletable: Boolean = true)

/**
 * Barra de presets compartida (Muestreo y Rendimiento): fichas para cargar, botón «Nuevo» para guardar
 * los valores actuales con un nombre y un lápiz que activa el modo edición. Borrar solo es posible en
 * modo edición y pide confirmación, para que un toque no se lleve un preset por error.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetBar(
    items: List<PresetBarItem>,
    selectedKey: String?,
    statusText: String,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<PresetBarItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PRESETS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { editing = !editing }) {
                Icon(
                    if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = if (editing) "Terminar edición" else "Editar presets (borrar)",
                    tint = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { newName = ""; creating = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Text(" Nuevo")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { it ->
                val askDelete = editing && it.deletable
                FilterChip(
                    selected = it.key == selectedKey,
                    onClick = { if (askDelete) deleting = it else onPick(it.key) },
                    label = { Text(it.label) },
                    trailingIcon =
                        if (askDelete) {
                            { Icon(Icons.Rounded.Close, "Borrar ${it.label}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                        } else null,
                )
            }
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nuevo preset") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { onNew(newName.trim()); creating = false },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar «${item.label}»?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { onDelete(item.key); deleting = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}
