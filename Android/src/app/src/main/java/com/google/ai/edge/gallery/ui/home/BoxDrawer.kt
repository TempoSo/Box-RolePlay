package com.google.ai.edge.gallery.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.characters.PersonaRepository
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.entities.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un grupo del cajón: un personaje (o "Chat libre"/"Escenas") con sus conversaciones. */
data class DrawerGroup(
  val key: String,
  val name: String,
  val avatarPath: String,
  val chats: List<Conversation>,
)

@HiltViewModel
class BoxDrawerViewModel @Inject constructor(
  private val chatRepository: ChatRepository,
  characterRepository: CharacterRepository,
  personaRepository: PersonaRepository,
) : ViewModel() {
  val groups: StateFlow<List<DrawerGroup>> =
    combine(chatRepository.getAllConversations(), characterRepository.observeAll()) { convs, chars ->
      val byId = chars.associateBy { it.id }
      convs.groupBy { it.characterId }.map { (cid, list) ->
        when {
          cid.isBlank() -> DrawerGroup("", "Chat libre", "", list)
          cid.startsWith("scene:") -> DrawerGroup(cid, "Escenas", "", list)
          else -> DrawerGroup(cid, byId[cid]?.name ?: "Personaje borrado", byId[cid]?.avatarPath ?: "", list)
        }
      }.sortedByDescending { g -> g.chats.maxOf { it.updatedAt } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val personaName: StateFlow<String> =
    personaRepository.observeAll().map { list -> (list.firstOrNull { it.isActive } ?: list.firstOrNull())?.name ?: "Usuario" }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Usuario")

  fun delete(c: Conversation) {
    viewModelScope.launch { chatRepository.deleteConversation(c) }
  }
}

fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
  val m = ((now - ts) / 60_000).coerceAtLeast(0)
  return when {
    m < 1 -> "ahora"
    m < 60 -> "${m}m"
    m < 60 * 24 -> "${m / 60}h"
    else -> "${m / (60 * 24)}d"
  }
}

@Composable
fun BoxAvatar(path: String, name: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
  val bmp = remember(path) { if (path.isBlank()) null else runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
  if (bmp != null) {
    Image(bmp, name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
  } else {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
      Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
  }
}

/** Contenido del cajón lateral (estilo Layla): buscar chats, personajes, apps, chats por personaje y persona. */
@Composable
fun BoxDrawerContent(
  onCharacters: () -> Unit,
  onApps: () -> Unit,
  onMemory: () -> Unit,
  onSettings: () -> Unit,
  onOpenConversation: (Conversation) -> Unit,
  viewModel: BoxDrawerViewModel = hiltViewModel(),
) {
  val groups by viewModel.groups.collectAsState()
  val persona by viewModel.personaName.collectAsState()
  var query by remember { mutableStateOf("") }
  var expanded by remember { mutableStateOf<String?>(null) }

  Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
    Text("Box RolePlay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    OutlinedTextField(
      value = query, onValueChange = { query = it },
      placeholder = { Text("Buscar chats") },
      leadingIcon = { Icon(Icons.Rounded.Search, null) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
    DrawerRow(Icons.Rounded.People, "Personajes", onCharacters)
    DrawerRow(Icons.Rounded.Apps, "Apps", onApps)
    DrawerRow(Icons.Rounded.Memory, "Memoria a largo plazo", onMemory)

    Text("Chats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
    val filtered = groups.map { g ->
      g.copy(chats = g.chats.filter { query.isBlank() || it.title.contains(query, true) || g.name.contains(query, true) })
    }.filter { it.chats.isNotEmpty() }

    LazyColumn(Modifier.weight(1f)) {
      items(filtered, key = { it.key.ifBlank { "free" } }) { g ->
        val open = expanded == g.key || query.isNotBlank()
        Row(
          Modifier.fillMaxWidth().clickable { expanded = if (expanded == g.key) null else g.key }.padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          BoxAvatar(g.avatarPath, g.name)
          Text(g.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
          Text("${g.chats.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) g.chats.forEach { c ->
          Row(
            Modifier.fillMaxWidth().clickable { onOpenConversation(c) }.padding(start = 68.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(relativeTime(c.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = { viewModel.delete(c) }) { Icon(Icons.Rounded.Delete, "Borrar chat", tint = MaterialTheme.colorScheme.error) }
          }
        }
      }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      BoxAvatar("", persona, 44.dp)
      Column(Modifier.weight(1f)) {
        Text(persona, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Persona", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
      }
      IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Configuración") }
    }
  }
}

@Composable
private fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(icon, null)
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}
