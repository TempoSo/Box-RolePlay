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
import com.google.ai.edge.gallery.tts.KokoroHfSelect
import com.google.ai.edge.gallery.tts.KokoroVariant
import com.google.ai.edge.gallery.tts.PocketHfSelect
import com.google.ai.edge.gallery.tts.PocketModelFiles
import com.google.ai.edge.gallery.tts.PocketVariant
import com.google.ai.edge.gallery.tts.SentencePieceModel
import com.google.ai.edge.gallery.tts.SupertonicHfSelect
import com.google.ai.edge.gallery.tts.SupertonicVariant
import com.google.ai.edge.gallery.tts.TtsFiles
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.tts.TtsModelType
import com.google.ai.edge.gallery.tts.TtsModels
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// BUG-013: los bundles v2 de KevinAHM necesitan insertar bos_before_voice.npy, que sherpa-onnx
// 2.0.1 no hace (audio de ruido o cortado a 0,2 s). Este repo ya trae el BOS en encoder.onnx.
private const val DEFAULT_REPO = "haex-space/pocket-tts-sherpa-onnx"

// Repo de Kokoro multilingüe preparado para sherpa-onnx (54 voces, entre ellas 3 en español).
private const val KOKORO_REPO = "csukuangfj/kokoro-multi-lang-v1_0"

// Supertonic 3: 31 idiomas y 10 voces incluidas en voice.bin (24 kHz, archivos int8 en la raíz).
private const val SUPERTONIC_REPO = "csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11"

/** Una opción descargable: un modelo Pocket, Kokoro o Supertonic. */
private class Choice(
  val type: TtsModelType,
  val label: String,
  val bytes: Long,
  val pocket: PocketVariant?,
  val kokoro: KokoroVariant?,
  val supertonic: SupertonicVariant? = null,
)

/**
 * Descarga el modelo Pocket TTS directo de Hugging Face. Se escribe el repo (o un enlace), se leen sus
 * carpetas con un modelo completo (p. ej. una por idioma) y se elige cuál bajar.
 */
@Composable
fun TtsModelDownloadDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var repoInput by remember { mutableStateOf(DEFAULT_REPO) }
  var variants by remember { mutableStateOf<List<Choice>>(emptyList()) }
  var picked by remember { mutableStateOf<Choice?>(null) }
  var status by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0f) }
  var cancelled by remember { mutableStateOf(false) }

  fun search() {
    val repo = HfApi.parseRepoId(repoInput)
    if (repo == null) { status = "Escribe un repo como usuario/nombre o pega el enlace de huggingface.co"; return }
    busy = true; status = "Leyendo $repo…"; variants = emptyList(); picked = null
    scope.launch {
      // BUG-013: el TTS necesita el árbol sin filtrar; HfClient.files() solo ve .gguf/.litertlm.
      val res =
        withContext(Dispatchers.IO) {
          runCatching {
            val tree = HfClient.treeFiles(repo)
            PocketHfSelect.variants(tree).map { Choice(TtsModelType.POCKET, it.label, it.totalBytes, it, null) } +
              KokoroHfSelect.variants(tree).map { Choice(TtsModelType.KOKORO, it.label, it.totalBytes, null, it) } +
              SupertonicHfSelect.variants(tree).map { Choice(TtsModelType.SUPERTONIC, it.label, it.totalBytes, null, null, it) }
          }
        }
      busy = false
      res.onSuccess {
        variants = it
        picked = it.firstOrNull { v -> v.pocket?.folder?.let { f -> f.isNotEmpty() && repoInput.contains("/$f") } == true } ?: it.firstOrNull()
        status = if (it.isEmpty()) "No encontré ningún modelo de voz compatible (Pocket TTS, Kokoro o Supertonic) en ese repositorio."
        else "${it.size} modelo(s) compatible(s) encontrado(s). Elige uno:"
      }.onFailure { status = "No se pudo leer el repo: ${it.message}" }
    }
  }

  fun download() {
    val repo = HfApi.parseRepoId(repoInput) ?: return
    val v = picked ?: return
    busy = true; cancelled = false; progress = 0f
    scope.launch {
      val total = v.bytes.coerceAtLeast(1)
      var base = 0L
      val stage = TtsFiles.stagingDir(context)
      val res = withContext(Dispatchers.IO) {
        runCatching {
          // Lista común: archivo remoto → ruta local (Kokoro trae subcarpetas: espeak-ng-data/…).
          val jobs: List<Triple<com.google.ai.edge.gallery.data.characters.HfFile, String, String>> =
            when {
              v.pocket != null -> v.pocket.files.map { Triple(it.file, it.localName, it.localName) }
              v.kokoro != null -> v.kokoro.files.map { Triple(it.file, it.localPath, it.localPath) }
              else -> v.supertonic!!.files.map { Triple(it.file, it.localPath, it.localPath) }
            }
          for ((i, j) in jobs.withIndex()) {
            status = if (jobs.size > 12) "Descargando ${v.label}… (${i + 1}/${jobs.size})" else "Descargando ${j.first.name}…"
            HfClient.download(
              HfApi.downloadUrl(repo, j.first.path), File(stage, j.second), j.first.size,
              onProgress = { done, _ -> progress = ((base + done).toFloat() / total).coerceIn(0f, 1f) },
              isCancelled = { cancelled },
            )
            base += j.first.size
          }
          if (v.type == TtsModelType.POCKET) {
            // El formato v2 trae tokenizer.model: se convierte a los dos JSON que usa sherpa-onnx.
            val tokM = File(stage, PocketHfSelect.TOKENIZER)
            val tokJ = File(stage, PocketHfSelect.TOKENIZER_JSON)
            if (tokM.exists() || tokJ.exists()) {
              status = "Preparando el tokenizador…"
              val pieces =
                if (tokM.exists()) SentencePieceModel.parse(tokM.readBytes())
                else SentencePieceModel.parseHfJson(tokJ.readText())
              File(stage, "vocab.json").writeText(SentencePieceModel.vocabJson(pieces))
              File(stage, "token_scores.json").writeText(SentencePieceModel.scoresJson(pieces))
            }
          }
          // Todo bajado: se instala como un modelo más (no borra los otros) y pasa a ser el activo.
          val repoName = repo.substringAfterLast('/')
          val model = TtsModels.create(context, "${v.label} · $repoName")
          // Solo las piezas de este modelo (en la carpeta de trabajo pueden quedar restos de otra descarga).
          val wanted = jobs.map { it.third }.toSet() + if (v.type == TtsModelType.POCKET) setOf("vocab.json", "token_scores.json") else emptySet()
          for (rel in wanted) {
            val f = File(stage, rel)
            if (!f.exists()) continue
            val dest = File(model.dir, rel).also { it.parentFile?.mkdirs() }
            if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = true); f.delete() }
          }
          stage.deleteRecursively()
          TtsModels.setActive(context, model.id)
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
          "«Pocket»: clona la voz de una muestra tuya. «Kokoro»: decenas de voces ya hechas (~330 MB). «Supertonic»: 10 voces y 31 idiomas con voice.bin incluido, más ligero. Para otro repo, pega su nombre o enlace y pulsa Buscar.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { repoInput = DEFAULT_REPO; search() }, enabled = !busy) { Text("Pocket") }
          OutlinedButton(onClick = { repoInput = KOKORO_REPO; search() }, enabled = !busy) { Text("Kokoro") }
          OutlinedButton(onClick = { repoInput = SUPERTONIC_REPO; search() }, enabled = !busy) { Text("Supertonic") }
          OutlinedButton(onClick = { search() }, enabled = !busy) { Text("Buscar") }
        }
        variants.forEach { v ->
          Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { picked = v }.padding(vertical = 2.dp)) {
            RadioButton(selected = picked == v, onClick = { picked = v }, enabled = !busy)
            Column {
              Text(v.label, style = MaterialTheme.typography.bodyMedium)
              Text("${v.type.label} · ${v.bytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
        if (busy && progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
          "Revisa la licencia del modelo en su página antes de usarlo.",
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
