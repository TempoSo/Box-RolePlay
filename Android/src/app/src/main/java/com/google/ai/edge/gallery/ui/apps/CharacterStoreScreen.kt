package com.google.ai.edge.gallery.ui.apps

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.store.StoreCard
import com.google.ai.edge.gallery.store.StoreImages
import com.google.ai.edge.gallery.store.StoreSort
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tienda de personajes: busca tarjetas en Chub, o pega un enlace de otras fuentes, y añádelas a tus personajes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterStoreScreen(onBack: () -> Unit, viewModel: CharacterStoreViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val text by viewModel.text.collectAsState()
  val sort by viewModel.sort.collectAsState()
  val nsfw by viewModel.nsfw.collectAsState()
  val results by viewModel.results.collectAsState()
  val loading by viewModel.loading.collectAsState()
  val hasMore by viewModel.hasMore.collectAsState()
  val added by viewModel.added.collectAsState()
  val busyId by viewModel.busyId.collectAsState()
  val message by viewModel.message.collectAsState()
  val pendingLore by viewModel.pendingLore.collectAsState()
  val characters by viewModel.characters.collectAsState()
  var link by remember { mutableStateOf("") }
  var confirmNsfw by remember { mutableStateOf(false) }

  LaunchedEffect(message) {
    message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessage() }
  }

  pendingLore?.let { lore ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissLore() },
      title = { Text("Lorebook: ${lore.name}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("${lore.entryCount} entradas. Elige el personaje al que se añaden (las entradas que ya tiene se conservan).", style = MaterialTheme.typography.bodySmall)
          if (characters.isEmpty()) Text("Aún no tienes personajes.", style = MaterialTheme.typography.bodyMedium)
          LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(characters, key = { it.id }) { c ->
              TextButton(onClick = { viewModel.attachLore(c.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = { viewModel.dismissLore() }) { Text("Cancelar") } },
    )
  }

  if (confirmNsfw) {
    AlertDialog(
      onDismissRequest = { confirmNsfw = false },
      title = { Text("Contenido para adultos") },
      text = {
        Text(
          "Se mostrarán tarjetas marcadas como NSFW (+18) por sus autores. Son contenido de terceros, sin revisar por Box. " +
            "Actívalo solo si eres mayor de edad."
        )
      },
      confirmButton = { TextButton(onClick = { confirmNsfw = false; viewModel.setNsfw(true) }) { Text("Soy mayor de 18, activar") } },
      dismissButton = { TextButton(onClick = { confirmNsfw = false }) { Text("Cancelar") } },
    )
  }

  BoxScaffold(title = "Tienda de personajes", subtitle = "Tarjetas de Chub y otras fuentes", onBack = onBack) {
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Las tarjetas son de terceros y se descargan de internet. Se añaden a «Personajes».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = text,
            onValueChange = { viewModel.text.value = it },
            label = { Text("Buscar personajes en Chub") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoreSort.entries.forEach { s ->
              FilterChip(selected = sort == s, onClick = { viewModel.setSort(s) }, label = { Text(s.label) })
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Contenido NSFW (+18)", style = MaterialTheme.typography.titleSmall)
              Text(
                if (nsfw) "Se muestran tarjetas para adultos." else "Oculto por defecto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = nsfw, onCheckedChange = { if (it) confirmNsfw = true else viewModel.setNsfw(false) })
          }
          Text("Importar por enlace", style = MaterialTheme.typography.titleSmall)
          Text(
            "Pega un enlace o ID: Chub (usuario/proyecto), lorebook de Chub (se añade a un personaje), Pygmalion, RisuRealm, AI Character Cards (AICC/autor/tarjeta), Perchance (experimental), JannyAI (puede fallar por su protección anti-bots) o un enlace directo a .png/.json.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = link,
              onValueChange = { link = it },
              label = { Text("Enlace o ID") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Button(enabled = link.isNotBlank() && busyId == null, onClick = { viewModel.addFromLink(link); link = "" }) {
              Text(if (busyId == "link") "…" else "Añadir")
            }
          }
        }
      }
      items(results, key = { it.source + it.id }) { card ->
        StoreCardRow(card, added = card.id in added, busy = busyId == card.id, anyBusy = busyId != null) { viewModel.add(card) }
      }
      item {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
          when {
            loading -> CircularProgressIndicator()
            results.isEmpty() -> Text("Sin resultados.", style = MaterialTheme.typography.bodyMedium)
            hasMore -> OutlinedButton(onClick = { viewModel.loadMore() }) { Text("Cargar más") }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCardRow(card: StoreCard, added: Boolean, busy: Boolean, anyBusy: Boolean, onAdd: () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      RemoteImage(card.avatarUrl, 72.dp)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          "@${card.author}" + (if (card.stars > 0) " · ★ ${card.stars}" else "") + (if (card.tokens > 0) " · ${card.tokens} tokens" else ""),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (card.tagline.isNotBlank()) {
          Text(card.tagline, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (card.tags.isNotEmpty()) {
          Text(
            card.tags.take(5).joinToString(" · ") + if (card.nsfw) "" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (card.nsfw) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(onClick = onAdd, enabled = !added && !anyBusy) {
          Text(if (busy) "Descargando…" else if (added) "Añadido ✓" else "Añadir a mis personajes")
        }
      }
    }
  }
}

@Composable
private fun RemoteImage(url: String, size: Dp) {
  val bitmap by produceState<Bitmap?>(initialValue = StoreImages.cached(url), url) {
    if (value == null && url.isNotBlank()) value = withContext(Dispatchers.IO) { StoreImages.load(url) }
  }
  Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
    bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
  }
}
