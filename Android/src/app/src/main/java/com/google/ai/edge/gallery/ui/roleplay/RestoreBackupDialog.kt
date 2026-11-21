package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.google.ai.edge.gallery.data.characters.BackupContents
import com.google.ai.edge.gallery.data.characters.BackupSection

/** BUG-038: elige qué partes de la copia restaurar. Siempre se fusiona con lo que ya hay. */
@Composable
fun RestoreBackupDialog(contents: BackupContents, onConfirm: (Set<BackupSection>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(contents.sections) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué quieres restaurar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Se fusiona con lo que ya tienes: si el personaje es idéntico no se duplica; si cambió, " +
                        "se conserva tu versión y la de la copia se añade como personaje nuevo. Nada se borra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupSection.entries.filter { it in contents.sections }.forEach { sec ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sec in selected,
                            onCheckedChange = { on -> selected = if (on) selected + sec else selected - sec },
                        )
                        Text("${sec.label} (${contents.counts[sec] ?: 0})")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
