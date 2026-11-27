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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.google.ai.edge.gallery.store.StorePager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

  val page by viewModel.page.collectAsState()
  val totalPages by viewModel.totalPages.collectAsState()
  val pageToken by viewModel.pageToken.collectAsState()
  var showLink by remember { mutableStateOf(false) }
  val gridState = rememberLazyGridState()
  LaunchedEffect(pageToken) { if (pageToken > 0) gridState.scrollToItem(0) }

  if (showLink) {
    AlertDialog(
      onDismissRequest = { showLink = false },
      title = { Text("Importar por enlace o ID") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Chub (usuario/proyecto), lorebook de Chub (se añade a un personaje), Pygmalion, RisuRealm, AI Character Cards (AICC/autor/tarjeta), Perchance (experimental), JannyAI (puede fallar por su protección anti-bots) o un enlace directo a .png/.json.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Enlace o ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        TextButton(enabled = link.isNotBlank() && busyId == null, onClick = { viewModel.addFromLink(link); link = ""; showLink = false }) { Text("Añadir") }
      },
      dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancelar") } },
    )
  }

  BoxScaffold(title = "Tienda de personajes", subtitle = "Tarjetas de Chub y otras fuentes", onBack = onBack) {
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = text,
          onValueChange = { viewModel.text.value = it },
          placeholder = { Text("Buscar personajes en Chub") },
          leadingIcon = { Icon(Icons.Rounded.Search, null) },
          singleLine = true,
          shape = RoundedCornerShape(28.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { viewModel.random() }, enabled = !loading) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
        IconButton(onClick = { showLink = true }) { Icon(Icons.Rounded.Link, "Importar por enlace") }
      }
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(StoreSort.entries.toList()) { s ->
          FilterChip(selected = sort == s, onClick = { viewModel.setSort(s) }, label = { Text(s.label) })
        }
        item {
          FilterChip(
            selected = nsfw,
            onClick = { if (nsfw) viewModel.setNsfw(false) else confirmNsfw = true },
            label = { Text("+18") },
          )
        }
      }
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        gridItems(results, key = { it.source + it.id }) { card ->
          StoreCardTile(card, added = card.id in added, busy = busyId == card.id, anyBusy = busyId != null) { viewModel.add(card) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
          Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            when {
              loading -> CircularProgressIndicator()
              results.isEmpty() -> Text("Sin resultados.", style = MaterialTheme.typography.bodyMedium)
              else -> PageBar(page, totalPages, hasMore, onGo = { viewModel.goTo(it) })
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageBar(page: Int, totalPages: Int?, hasMore: Boolean, onGo: (Int) -> Unit) {
  val items = StorePager.items(page, totalPages, hasMore)
  val canNext = if (totalPages != null) page < totalPages else hasMore
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    IconButton(onClick = { onGo(page - 1) }, enabled = page > 1) { Icon(Icons.Rounded.ChevronLeft, "Anterior") }
    items.forEach { p ->
      if (p == null) {
        Text("…", Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        val sel = p == page
        Box(
          Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !sel) { onGo(p) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "$p",
            style = MaterialTheme.typography.labelLarge,
            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    IconButton(onClick = { onGo(page + 1) }, enabled = canNext) { Icon(Icons.Rounded.ChevronRight, "Siguiente") }
  }
}

private fun compact(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000f) else "$n"

@Composable
private fun StoreCardTile(card: StoreCard, added: Boolean, busy: Boolean, anyBusy: Boolean, onAdd: () -> Unit) {
  Box(
    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant),
  ) {
    RemoteImage(card.avatarUrl, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
    Box(
      Modifier.fillMaxSize().background(
        Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
      )
    )
    if (card.stars > 0) {
      Row(
        Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(compact(card.stars), color = Color.White, style = MaterialTheme.typography.labelSmall)
      }
    }
    if (card.nsfw) {
      Text(
        "+18",
        Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xCCB3261E)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
      )
    }
    Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 54.dp, bottom = 10.dp)) {
      Text(card.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text("@${card.author}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (card.tags.isNotEmpty()) {
        Text(
          card.tags.take(2).joinToString(" · "),
          color = Color.White.copy(alpha = 0.6f),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    FilledIconButton(
      onClick = onAdd,
      enabled = !added && !anyBusy,
      modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp),
    ) {
      when {
        busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        added -> Icon(Icons.Rounded.Check, "Añadido")
        else -> Icon(Icons.Rounded.Download, "Añadir a mis personajes")
      }
    }
  }
}

@Composable
private fun RemoteImage(url: String, modifier: Modifier, shape: RoundedCornerShape) {
  val bitmap by produceState<Bitmap?>(initialValue = StoreImages.cached(url), url) {
    if (value == null && url.isNotBlank()) value = withContext(Dispatchers.IO) { StoreImages.load(url) }
  }
  Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
    bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
  }
}
