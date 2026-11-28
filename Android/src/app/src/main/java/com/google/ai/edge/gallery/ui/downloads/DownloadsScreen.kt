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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.google.ai.edge.gallery.data.downloads.DownloadVariant
import com.google.ai.edge.gallery.data.downloads.DeviceProfile
import com.google.ai.edge.gallery.data.downloads.LinkKind
import com.google.ai.edge.gallery.data.downloads.LinkState
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Lista unificada de modelos enlazados. Cada familia (modelo + proyector) ocupa una sola tarjeta
 * con su selector de cuantización. Las descargas siguen aunque se cambie de pestaña: el estado sale
 * de [DownloadsViewModel.links], que es persistente y no depende de esta pantalla.
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

  // El proyector se pinta dentro de la tarjeta de su modelo, no como tarjeta suelta.
  val cards = links.filter { it.groupId.isBlank() || it.kind == LinkKind.MODEL }

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
            Icon(Icons.Rounded.Search, null)
            Text("  Buscar y enlazar desde Hugging Face")
          }
          Text(
            "Elige cuantización según tu RAM: el cálculo incluye el proyector cuando hay visión. " +
              "Pausa y reanuda conserva lo ya bajado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
          )
        }
      }
      items(cards, key = { it.id }) { link ->
        val projector =
          links.firstOrNull {
            it.groupId.isNotBlank() && it.groupId == link.groupId && it.kind == LinkKind.MMPROJ
          }
        val totalBytes = device.groupBytes(link, links)
        DownloadLinkCard(
          link = link,
          projector = projector,
          device = device,
          groupBytes = totalBytes,
          warning = device.warningFor(link, totalBytes),
          onSelectVariant = { viewModel.selectVariant(link, it) },
          onDownload = { viewModel.download(link) },
          onPause = { viewModel.pause(link) },
          onCancel = { viewModel.cancel(link) },
          onRetry = { viewModel.download(link) },
          onDeleteFile = { viewModel.deleteFile(link) },
          onRemove = { viewModel.removeLink(link) },
          onUse = {
            modelManagerViewModel.syncDownloadLinks()
            onUseModel(link.fileName)
          },
          onProjectorAction = { action ->
            projector?.let { p ->
              when (action) {
                "download", "retry" -> viewModel.download(p)
                "pause" -> viewModel.pause(p)
                "cancel" -> viewModel.cancel(p)
                "delete" -> viewModel.deleteFile(p)
                "use" -> onUseVision()
              }
            }
          },
        )
      }
    }
  }
}

@Composable
private fun DownloadLinkCard(
  link: DownloadLink,
  projector: DownloadLink?,
  device: DeviceProfile,
  groupBytes: Long,
  warning: String?,
  onSelectVariant: (DownloadVariant) -> Unit,
  onDownload: () -> Unit,
  onPause: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onDeleteFile: () -> Unit,
  onRemove: () -> Unit,
  onUse: () -> Unit,
  onProjectorAction: (String) -> Unit,
) {
  val extraBytes = projector?.sizeBytes ?: 0L
  val currentQuant = link.variants.firstOrNull { it.fileName == link.fileName }?.quant
  val recommended = device.recommendedVariant(link, extraBytes)

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        if (currentQuant == null) link.label else "$link.label · $currentQuant",
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        listOfNotNull(
          DownloadLinkFormat.mb(link.sizeBytes),
          device.fitLabel(link.sizeBytes + extraBytes),
          if (link.vision) "visión" else null,
          if (projector != null) "total ${DownloadLinkFormat.mb(groupBytes)}" else null,
          stateLabel(link.state),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (link.variants.isNotEmpty()) {
        Text(
          if (recommended == null) {
            "Cuantización: ninguna cabe con holgura en este dispositivo."
          } else {
            "Cuantización · recomendada: ${recommended.quant} (${device.fitLabel(recommended.sizeBytes + extraBytes)})"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          link.variants.forEach { variant ->
            FilterChip(
              selected = variant.fileName == link.fileName,
              onClick = { onSelectVariant(variant) },
              label = { Text("${variant.quant} · ${device.fitLabel(variant.sizeBytes + extraBytes)}") },
            )
          }
        }
      }

      if (warning != null && link.state != LinkState.COMPLETED) {
        Text("⚠ $warning", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB300))
      }
      ProgressOrError(link)

      LinkActionButtons(
        state = link.state,
        onDownload = onDownload,
        onPause = onPause,
        onCancel = onCancel,
        onRetry = onRetry,
        onDeleteFile = onDeleteFile,
        onUse = onUse,
      )
      if (!link.pinned) TextButton(onClick = onRemove) { Text("Quitar enlace") }

      if (projector != null) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(projector.label, style = MaterialTheme.typography.labelLarge)
        Text(
          "${DownloadLinkFormat.mb(projector.sizeBytes)} · ${stateLabel(projector.state)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgressOrError(projector)
        LinkActionButtons(
          state = projector.state,
          onDownload = { onProjectorAction("download") },
          onPause = { onProjectorAction("pause") },
          onCancel = { onProjectorAction("cancel") },
          onRetry = { onProjectorAction("retry") },
          onDeleteFile = { onProjectorAction("delete") },
          onUse = { onProjectorAction("use") },
        )
      }
    }
  }
}

@Composable
private fun ProgressOrError(link: DownloadLink) {
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
}

@Composable
private fun LinkActionButtons(
  state: LinkState,
  onDownload: () -> Unit,
  onPause: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onDeleteFile: () -> Unit,
  onUse: () -> Unit,
) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    when (state) {
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
