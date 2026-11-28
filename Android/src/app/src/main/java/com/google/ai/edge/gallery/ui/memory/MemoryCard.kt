package com.google.ai.edge.gallery.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.detalleParaUI
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.ui.home.relativeTime

/**
 * Tarjeta de un recuerdo: tema legible, resumen, claves e importancia en vez del JSON crudo que
 * dejaron las absorciones antiguas. La edición sigue trabajando sobre el contenido original.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MemoryCard(
  memory: CharacterMemory,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val detalle = remember(memory) { memory.detalleParaUI() }
  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          detalle.topic.ifBlank { "Recuerdo" },
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        Text(
          relativeTime(memory.createdAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Editar") }
        IconButton(onClick = onDelete) {
          Icon(Icons.Rounded.Delete, "Borrar", tint = MaterialTheme.colorScheme.error)
        }
      }
      Text(
        detalle.summary,
        maxLines = 8,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
      )
      if (detalle.keys.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(top = 8.dp),
        ) {
          detalle.keys.forEach { clave ->
            AssistChip(onClick = {}, label = { Text(clave, style = MaterialTheme.typography.labelSmall) })
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
          "Importancia ${detalle.importance}/10",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
          progress = { detalle.importance / 10f },
          modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        )
      }
    }
  }
}
