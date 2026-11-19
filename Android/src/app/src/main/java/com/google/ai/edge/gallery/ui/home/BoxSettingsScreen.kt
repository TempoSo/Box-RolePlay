package com.google.ai.edge.gallery.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.characters.LogExporter
import com.google.ai.edge.gallery.ui.roleplay.EnginePerfDialog
import com.google.ai.edge.gallery.ui.roleplay.RoleplayViewModel
import com.google.ai.edge.gallery.ui.roleplay.SamplerDialog
import com.google.ai.edge.gallery.ui.roleplay.TtsSettingsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Configuración: cada fila abre su pantalla o su ajuste. */
@Composable
fun BoxSettingsScreen(
  onBack: () -> Unit,
  onOpenInference: () -> Unit,
  viewModel: RoleplayViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showTts by remember { mutableStateOf(false) }
  var showSampler by remember { mutableStateOf(false) }
  var showPerf by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }
  // BUG-023: el backend de restauración ya existe; solo faltaba la UI. Estado propio de Ajustes.
  var restoreStatus by remember { mutableStateOf<String?>(null) }
  val restoreLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch { restoreStatus = viewModel.importFromSettings(uri) }
      }
    }

  if (showTts) TtsSettingsDialog(onDismiss = { showTts = false })
  if (showSampler) SamplerDialog(onDismiss = { showSampler = false })
  if (showPerf) EnginePerfDialog(onDismiss = { showPerf = false })

  BoxScaffold(title = "Configuración", subtitle = "Preferencias, copias de seguridad y soporte", onBack = onBack) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
      BoxActionCard(
        icon = Icons.Rounded.Memory,
        title = "Configuración de inferencia",
        subtitle = "Modelo, visión, personas y motores guardados",
        onClick = onOpenInference,
      )
      BoxActionCard(
        icon = Icons.Rounded.RecordVoiceOver,
        title = "Configuración de texto a voz",
        subtitle = "Pocket TTS, voces y lectura automática",
        onClick = { showTts = true },
      )
      BoxActionCard(
        icon = Icons.Rounded.Tune,
        title = "Muestreo (presets)",
        subtitle = "Temperatura, Min-P, XTC, DRY y tus presets",
        onClick = { showSampler = true },
      )
      BoxActionCard(
        icon = Icons.Rounded.Speed,
        title = "Rendimiento del motor",
        subtitle = "Hilos, batch, flash attention y visión",
        onClick = { showPerf = true },
      )

      BoxSectionLabel("Soporte y datos")
      BoxActionCard(
        icon = Icons.Rounded.Backup,
        title = "Copia de seguridad de personajes",
        subtitle = status ?: "Exporta todos tus personajes en un .zip",
        onClick = {
          scope.launch {
            runCatching {
              val zip = viewModel.backupZip()
              val dir = java.io.File(context.cacheDir, "backup").apply { mkdirs() }
              dir.listFiles()?.forEach { it.delete() }
              val f = java.io.File(dir, "box-roleplay-personajes.zip").apply { writeBytes(zip) }
              val uri =
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
              val send =
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                  type = "application/zip"
                  putExtra(android.content.Intent.EXTRA_STREAM, uri)
                  addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
              context.startActivity(android.content.Intent.createChooser(send, "Guardar copia de seguridad"))
            }
              .onFailure { status = "No se pudo crear la copia: ${it.message}" }
          }
        },
      )
      BoxActionCard(
        icon = Icons.Rounded.Backup,
        title = "Restaurar copia",
        subtitle = restoreStatus ?: "Importa personajes de un .zip o una tarjeta .json/.png",
        onClick = {
          restoreLauncher.launch(arrayOf("application/zip", "application/json", "image/png"))
        },
      )
      BoxActionCard(
        icon = Icons.Rounded.BugReport,
        title = "Exportar logs",
        subtitle = "Para reportar errores: logcat, equipo y último cierre",
        onClick = {
          scope.launch(Dispatchers.IO) { runCatching { LogExporter.share(context) } }
        },
      )
      androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
    }
  }
}
