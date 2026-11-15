package com.google.ai.edge.gallery.ui.modelmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.characters.HfApi
import com.google.ai.edge.gallery.data.characters.HfFileKind
import com.google.ai.edge.gallery.data.characters.HfFit
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.home.BoxSectionLabel

private fun fmtSize(b: Long): String =
  when {
    b <= 0 -> "?"
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    else -> "${b / (1L shl 20)} MB"
  }

private fun fmtCount(n: Long): String =
  when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", n / 1_000.0)
    else -> "$n"
  }

/** Buscar en Hugging Face (o pegar "usuario/repo" / un enlace), ver archivos y descargar. */
@Composable
fun HfBrowserScreen(
  onBack: () -> Unit,
  onModelReady: (fileName: String, size: Long) -> Unit,
  viewModel: HfBrowserViewModel = hiltViewModel(),
) {
  val loading by viewModel.loading.collectAsState()
  val results by viewModel.results.collectAsState()
  val repo by viewModel.repo.collectAsState()
  val files by viewModel.files.collectAsState()
  val message by viewModel.message.collectAsState()
  val downloads by viewModel.downloads.collectAsState()
  var query by remember { mutableStateOf("") }

  BoxScaffold(
    title = "Hugging Face",
    subtitle = repo ?: "Busca modelos .gguf o pega un enlace",
    onBack = { if (repo != null) viewModel.closeRepo() else onBack() },
  ) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
      if (repo == null) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("p. ej., qwen 3b, o usuario/repositorio") },
          leadingIcon = { Icon(Icons.Rounded.Search, null) },
          trailingIcon = {
            IconButton(enabled = query.isNotBlank() && !loading, onClick = { viewModel.searchOrOpen(query) }) {
              Icon(Icons.Rounded.Search, "Buscar")
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
      }
      message?.let {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
        }
      }
      if (loading) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
      }
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repo == null) {
          items(results, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { viewModel.openRepo(r.id) }) {
              Column(Modifier.padding(12.dp)) {
                Text(r.id, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                  "↓ ${fmtCount(r.downloads)}   ♥ ${fmtCount(r.likes)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          item {
            BoxSectionLabel("Archivos")
            Text(
              "Tu teléfono tiene ${fmtSize(viewModel.ramBytes)} de RAM. Los modelos que caben con holgura salen marcados. Los \"mmproj\" son la visión.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          items(files, key = { it.path }) { f ->
            val kind = HfApi.kind(f)
            val progress = downloads[f.path]
            val fit = HfApi.fit(f.size, viewModel.ramBytes)
            Card(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp)) {
                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    listOfNotNull(
                      fmtSize(f.size),
                      HfApi.quant(f.name),
                      when (kind) {
                        HfFileKind.MMPROJ -> "visión"
                        HfFileKind.SPLIT -> "partido en varios archivos"
                        else -> null
                      },
                      when (fit) {
                        HfFit.GOOD -> if (kind == HfFileKind.MODEL) "✔ cabe bien" else null
                        HfFit.TIGHT -> if (kind == HfFileKind.MODEL) "justo" else null
                        HfFit.TOO_BIG -> if (kind == HfFileKind.MODEL) "demasiado grande" else null
                        HfFit.UNKNOWN -> null
                      },
                    ).joinToString(" · "),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (progress == null) {
                    Button(
                      enabled = kind != HfFileKind.SPLIT,
                      onClick = { viewModel.download(f, onModelReady) },
                    ) {
                      Icon(Icons.Rounded.Download, null)
                      Text(" Descargar")
                    }
                  } else {
                    TextButton(onClick = { viewModel.cancel(f) }) { Text("Cancelar") }
                  }
                }
                if (progress != null) {
                  val frac = if (progress.total > 0) (progress.received.toFloat() / progress.total).coerceIn(0f, 1f) else 0f
                  LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                  Text(
                    "${fmtSize(progress.received)} de ${fmtSize(progress.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                  )
                }
              }
            }
          }
          item {
            Text(
              "Mantén la app abierta mientras descarga. Si se corta, vuelve a pulsar Descargar y continúa donde iba.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
        }
      }
    }
  }
}
