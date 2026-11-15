package com.google.ai.edge.gallery.ui.roleplay

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import java.io.File

/**
 * Resumen del personaje (al tocar su tarjeta): estadísticas, acciones, descripción, escenario,
 * elección de saludo y los botones "Continuar chat" / "Nuevo chat".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailSheet(
    character: RoleplayCharacter,
    detail: RoleplayViewModel.CharacterDetail?,
    memoriesCount: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMemories: () -> Unit,
    /** Motores guardados disponibles y el elegido para este personaje ("" = el de Configuración). */
    enginePresets: List<com.google.ai.edge.gallery.data.characters.EnginePreset> = emptyList(),
    enginePresetId: String = "",
    onPickEngine: (String) -> Unit = {},
    onContinue: () -> Unit,
    /** greeting = null → uno al azar. */
    onNewChat: (greeting: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // -1 = saludo aleatorio; si no, índice en detail.greetings.
    var greetingIdx by remember { mutableIntStateOf(-1) }
    val avatar = remember(character.avatarPath) {
        if (character.avatarPath.isEmpty()) null
        else runCatching {
            val f = File(character.avatarPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
                ?.asImageBitmap() else null
        }.getOrNull()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        if (avatar != null) {
                            Image(avatar, character.name, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                        } else {
                            Text(character.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Column {
                        Text(character.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (character.creator.isNotBlank()) {
                            Text("por ${character.creator}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (enginePresets.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Motor: " + (enginePresets.firstOrNull { it.id == enginePresetId }?.name ?: "el de Configuración"))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("El de Configuración") },
                                onClick = { menu = false; onPickEngine("") },
                            )
                            enginePresets.forEach { pr ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(pr.name) },
                                    onClick = { menu = false; onPickEngine(pr.id) },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${detail?.messages ?: 0}", "Mensajes · ${detail?.chats ?: 0} chats", Modifier.weight(1f))
                    StatCard("$memoriesCount", "Recuerdos", Modifier.weight(1f), onClick = onMemories)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Editar")
                    }
                    FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Compartir")
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Borrar")
                    }
                }

                ExpandableBlock("Descripción", character.description)
                ExpandableBlock("Personalidad", character.personality)
                ExpandableBlock("Escenario actual", character.scenario, emptyText = "No hay escenario establecido.")

                val greetings = detail?.greetings.orEmpty()
                if (greetings.isNotEmpty()) {
                    Text("SELECCIONAR SALUDO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    GreetingOption("Saludo aleatorio", selected = greetingIdx == -1) { greetingIdx = -1 }
                    greetings.forEachIndexed { i, g ->
                        GreetingOption(g, selected = greetingIdx == i) { greetingIdx = i }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onContinue,
                    enabled = detail?.latestConversationId != null,
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Continuar chat") }
                Button(
                    onClick = { onNewChat(greetings.getOrNull(greetingIdx)) },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Nuevo chat") }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpandableBlock(title: String, text: String, emptyText: String = "Sin datos.") {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text.ifBlank { emptyText },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.length > 180) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Mostrar menos" else "Mostrar más") }
            }
        }
    }
}

@Composable
private fun GreetingOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
