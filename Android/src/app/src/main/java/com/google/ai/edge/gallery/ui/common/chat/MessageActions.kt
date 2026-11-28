package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Acción de un mensaje. `etiqueta` se usa en accesibilidad y en el menú desplegable. */
data class MessageAction(
  val etiqueta: String,
  val icono: ImageVector,
  val onClick: () -> Unit,
)

/**
 * Fila compacta de acciones del chat: solo las dos primeras se ven como iconos; el resto se esconde
 * detrás de los tres puntos. Así el roleplay no llena la pantalla de chips con etiquetas, sin perder
 * ninguna acción (el menú sí muestra texto).
 */
@Composable
fun MessageActions(acciones: List<MessageAction>, modifier: Modifier = Modifier) {
  if (acciones.isEmpty()) return
  var menuAbierto by remember { mutableStateOf(false) }
  val visibles = acciones.take(2)
  val ocultas = acciones.drop(2)

  Row(
    modifier = modifier.padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    visibles.forEach { accion -> ActionIcon(accion) }
    if (ocultas.isNotEmpty()) {
      Box {
        ActionIcon(
          MessageAction("Más acciones", Icons.Rounded.MoreVert) { menuAbierto = true },
        )
        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
          ocultas.forEach { accion ->
            DropdownMenuItem(
              text = { Text(accion.etiqueta) },
              leadingIcon = { Icon(accion.icono, contentDescription = null) },
              onClick = {
                menuAbierto = false
                accion.onClick()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActionIcon(accion: MessageAction) {
  Box(
    modifier =
      Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .clickable { accion.onClick() }
        .semantics(mergeDescendants = true) { contentDescription = accion.etiqueta },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      accion.icono,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}
