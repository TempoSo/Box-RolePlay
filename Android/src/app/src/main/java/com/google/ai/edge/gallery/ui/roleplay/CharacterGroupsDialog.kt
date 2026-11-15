package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.google.ai.edge.gallery.data.characters.CharacterGroupStore
import com.google.ai.edge.gallery.data.characters.CharacterGroups

/** En qué grupos está un personaje; permite crear y borrar grupos. */
@Composable
fun CharacterGroupsDialog(characterId: String, characterName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CharacterGroupStore(context) }
    val version by CharacterGroupStore.changes.collectAsState()
    val groups = remember(version) { store.all() }
    val mine = remember(groups) { CharacterGroups.groupIdsOf(groups, characterId) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grupos de $characterName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) {
                    Text("Aún no hay grupos. Crea uno abajo (p. ej., «Asistentes»).", style = MaterialTheme.typography.bodySmall)
                }
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = g.id in mine, onCheckedChange = { store.setMember(g.id, characterId, it) })
                        Text(g.name + "  (${g.characterIds.size})", Modifier.weight(1f))
                        IconButton(onClick = { store.delete(g.id) }) {
                            Icon(Icons.Rounded.Delete, "Borrar grupo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("Nuevo grupo") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newName.isNotBlank(),
                        onClick = { store.create(newName); newName = "" },
                    ) { Icon(Icons.Rounded.Add, "Crear grupo") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}
