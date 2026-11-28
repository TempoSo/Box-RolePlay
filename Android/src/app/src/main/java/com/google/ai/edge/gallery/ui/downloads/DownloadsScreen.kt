package com.google.ai.edge.gallery.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.downloads.DownloadLink
import com.google.ai.edge.gallery.data.downloads.DownloadLinkFormat
import com.google.ai.edge.gallery.data.downloads.LinkState
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Lista unificada de modelos enlazados. Las descargas siguen aunque se cambie de pestaña: el
 * estado sale de [DownloadsViewModel.links], que es persistente y no depende de esta pantalla.
 */
@Composable
fun DownloadsScreen(
  onBack: () -> Unit,
  onSearchHf: () -> Unit,
  onUseModel: (String) -> Unit,
  onUseVision: () -> Unit,
  modelManagerViewModel: ModelManagerViewModel,
  viewModel: DownloadsViewModel = hiltViewModel(),
) {
  val links by viewModel.links.collectAsState()
  val device = viewModel.device

  // Los modelos que acaban de descargarse se registran en la app aunque nadie esté mirando esta
  // pantalla; al volver a abrirla se reconcilian con la lista de ModelManager.
  val completed = links.filter { it.state == LinkState.COMPLETED }.map { it.id }
  LaunchedEffect(completed) {
    if (completed.isNotEmpty()) modelManagerViewModel.syncDownloadLinks()
  }

  BoxScaffold(
    title = "Descargas",
    subtitle = "Modelos enlazados · la descarga continúa al cambiar de pantalla",
    onBack = onBack,
  ) {
    LazyColumn(
      Modifier.fillMaxSize().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Column(Modifier.padding(top = 8.dp)) {
          Text(
            "Dispositivo: ${device.soc} · ${DownloadLinkFormat.mb(device.ramBytes)} de RAM. " +
              "Recomendado hasta ${DownloadLinkFormat.mb(device.recommendedMaxBytes)} por modelo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = onSearchHf, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            androidx.compose.material3.Icon(Icons.Rounded.Search, null)
            Text("  Buscar y enlazar desde Hugging Face")
          }
          Text(
            "Los enlaces de Hugging Face se añaden desde el buscador. Pausa y reanuda conserva lo ya bajado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
          )
        }
      }
      items(links, key = { it.id }) { link ->
        val totalBytes = device.groupBytes(link, links)
        DownloadLinkCard(
          link = link,
          groupBytes = totalBytes,
          warning = device.warningFor(link, totalBytes),
          onDownload = { viewModel.download(link) },
          onPause = { viewModel.pause(link) },
          onCancel = { viewModel.cancel(link) },
          onRetry = { viewModel.download(link) },
          onDeleteFile = { viewModel.deleteFile(link) },
          onRemove = { viewModel.removeLink(link) },
          onUse = {
            modelManagerViewModel.syncDownloadLinks()
            if (link.kind == com.google.ai.edge.gallery.data.downloads.LinkKind.MMPROJ) onUseVision()
            else onUseModel(link.fileName)
          },
        )
      }
    }
  }
}

@Composable
private fun DownloadLinkCard(
  link: DownloadLink,
  groupBytes: Long,
  warning: String?,
  onDownload: () -> Unit,
  onPause: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onDeleteFile: () -> Unit,
  onRemove: () -> Unit,
  onUse: () -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        link.label,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        listOfNotNull(
          DownloadLinkFormat.mb(link.sizeBytes),
          if (link.vision) "visión" else null,
          if (groupBytes > link.sizeBytes) "total ${DownloadLinkFormat.mb(groupBytes)} con visión" else null,
          if (link.kind == com.google.ai.edge.gallery.data.downloads.LinkKind.MMPROJ) "proyector" else null,
          stateLabel(link.state),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (warning != null && link.state != LinkState.COMPLETED) {
        Text(
          "⚠ $warning",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFFFFB300),
        )
      }
      if (link.state == LinkState.DOWNLOADING || link.state == LinkState.PAUSED) {
        LinearProgressIndicator(progress = { link.fraction }, modifier = Modifier.fillMaxWidth())
        Text(DownloadLinkFormat.progress(link), style = MaterialTheme.typography.bodySmall)
      }
      if (link.error.isNotBlank()) {
        Text(
          "Error: ${link.error}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (link.state) {
          LinkState.IDLE -> Button(onClick = onDownload) { Text("Descargar") }
          LinkState.DOWNLOADING -> {
            OutlinedButton(onClick = onPause) { Text("Pausar") }
            TextButton(onClick = onCancel) { Text("Cancelar") }
          }
          LinkState.PAUSED -> {
            Button(onClick = onRetry) { Text("Reanudar") }
            TextButton(onClick = onCancel) { Text("Cancelar") }
          }
          LinkState.FAILED -> {
            Button(onClick = onRetry) { Text("Reintentar") }
            TextButton(onClick = onCancel) { Text("Cancelar") }
          }
          LinkState.COMPLETED -> {
            Button(onClick = onUse) { Text("Usar") }
            TextButton(onClick = onDeleteFile) { Text("Borrar archivo") }
          }
        }
        if (!link.pinned) TextButton(onClick = onRemove) { Text("Quitar enlace") }
      }
    }
  }
}

private fun stateLabel(state: LinkState): String =
  when (state) {
    LinkState.IDLE -> "sin descargar"
    LinkState.DOWNLOADING -> "descargando"
    LinkState.PAUSED -> "en pausa"
    LinkState.FAILED -> "fallido"
    LinkState.COMPLETED -> "listo"
  }
