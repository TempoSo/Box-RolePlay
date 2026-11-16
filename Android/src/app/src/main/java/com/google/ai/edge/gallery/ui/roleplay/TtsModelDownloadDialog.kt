package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.HfApi
import com.google.ai.edge.gallery.data.characters.HfClient
import com.google.ai.edge.gallery.tts.PocketHfSelect
import com.google.ai.edge.gallery.tts.PocketModelFiles
import com.google.ai.edge.gallery.tts.PocketVariant
import com.google.ai.edge.gallery.tts.SentencePieceModel
import com.google.ai.edge.gallery.tts.TtsFiles
import com.google.ai.edge.gallery.tts.TtsManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_REPO = "KevinAHM/pocket-tts-onnx"

/**
 * Descarga el modelo Pocket TTS directo de Hugging Face. Se escribe el repo (o un enlace), se leen sus
 * carpetas con un modelo completo (p. ej. una por idioma) y se elige cuál bajar.
 */
@Composable
fun TtsModelDownloadDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var repoInput by remember { mutableStateOf(DEFAULT_REPO) }
  var variants by remember { mutableStateOf<List<PocketVariant>>(emptyList()) }
  var picked by remember { mutableStateOf<PocketVariant?>(null) }
  var status by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0f) }
  var cancelled by remember { mutableStateOf(false) }

  fun search() {
    val repo = HfApi.parseRepoId(repoInput)
    if (repo == null) { status = "Escribe un repo como usuario/nombre o pega el enlace de huggingface.co"; return }
    busy = true; status = "Leyendo $repo…"; variants = emptyList(); picked = null
    scope.launch {
      val res = withContext(Dispatchers.IO) { runCatching { PocketHfSelect.variants(HfClient.files(repo)) } }
      busy = false
      res.onSuccess {
        variants = it
        picked = it.firstOrNull { v -> v.folder.isNotEmpty() && repoInput.contains("/" + v.folder) } ?: it.firstOrNull()
        status = if (it.isEmpty()) "No encontré ninguna carpeta con un modelo Pocket TTS completo en ese repositorio."
        else "${it.size} modelo(s) compatible(s) encontrado(s). Elige idioma:"
      }.onFailure { status = "No se pudo leer el repo: ${it.message}" }
    }
  }

  fun download() {
    val repo = HfApi.parseRepoId(repoInput) ?: return
    val v = picked ?: return
    busy = true; cancelled = false; progress = 0f
    scope.launch {
      val total = v.totalBytes.coerceAtLeast(1)
      var base = 0L
      val stage = File(TtsFiles.modelDir(context).parentFile, "pocket_dl").apply { mkdirs() }
      val res = withContext(Dispatchers.IO) {
        runCatching {
          for (pk in v.files) {
            status = "Descargando ${pk.file.name}…"
            HfClient.download(
              HfApi.downloadUrl(repo, pk.file.path), File(stage, pk.localName), pk.file.size,
              onProgress = { done, _ -> progress = ((base + done).toFloat() / total).coerceIn(0f, 1f) },
              isCancelled = { cancelled },
            )
            base += pk.file.size
          }
          // El formato v2 trae tokenizer.model: se convierte a los dos JSON que usa sherpa-onnx.
          val tok = File(stage, PocketHfSelect.TOKENIZER)
          if (tok.exists()) {
            status = "Preparando el tokenizador…"
            val pieces = SentencePieceModel.parse(tok.readBytes())
            File(stage, "vocab.json").writeText(SentencePieceModel.vocabJson(pieces))
            File(stage, "token_scores.json").writeText(SentencePieceModel.scoresJson(pieces))
          }
          // Todo bajado: se reemplaza el modelo anterior por el nuevo.
          val dir = TtsFiles.modelDir(context)
          dir.listFiles()?.filter { PocketModelFiles.isPart(it.name) || it.name == PocketHfSelect.TOKENIZER }?.forEach { it.delete() }
          stage.listFiles()?.filter { !it.name.endsWith(".part") }?.forEach { f ->
            val dest = File(dir, f.name)
            if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = true); f.delete() }
          }
        }
      }
      busy = false
      res.onSuccess {
        TtsManager.release()
        status = "Modelo descargado."
        onDone()
        onDismiss()
      }.onFailure { status = "Falló: ${it.message}. Vuelve a intentarlo para reanudar." }
    }
  }

  LaunchedEffect(Unit) { search() }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("Descargar modelo de voz") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = repoInput, onValueChange = { repoInput = it }, enabled = !busy, singleLine = true,
          label = { Text("Repositorio de Hugging Face") }, modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "Se buscan solas las carpetas compatibles del repo (idiomas y variantes). Para otro repo, pega su nombre o enlace y vuelve a buscar.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { search() }, enabled = !busy) { Text("Buscar archivos") }
        variants.forEach { v ->
          Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { picked = v }.padding(vertical = 2.dp)) {
            RadioButton(selected = picked == v, onClick = { picked = v }, enabled = !busy)
            Column {
              Text(v.label, style = MaterialTheme.typography.bodyMedium)
              Text("${v.totalBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
        if (busy && progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
          "Revisa la licencia del modelo en su página (el de KevinAHM es de uso no comercial).",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { download() }, enabled = !busy && picked != null) { Text("Descargar") }
    },
    dismissButton = {
      TextButton(onClick = { if (busy) cancelled = true else onDismiss() }) { Text(if (busy) "Cancelar" else "Cerrar") }
    },
  )
}
