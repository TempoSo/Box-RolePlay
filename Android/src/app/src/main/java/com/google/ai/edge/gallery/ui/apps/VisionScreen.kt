package com.google.ai.edge.gallery.ui.apps

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.MmprojFiles
import com.google.ai.edge.gallery.data.characters.VisionSettings
import com.google.ai.edge.gallery.data.characters.VisionStore
import com.google.ai.edge.gallery.runtime.LlamaCppModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App «Visión»: una sola pantalla en vez de «Ask Image» + lista de modelos + chat. Inspirada en la
 * miniapp de visión de Layla (solo como guía de funciones; código propio):
 *  01 Imagen de prueba (elegir, ver, «Procesar imagen» y la respuesta con sus tiempos)
 *  02 Archivos del modelo (modelo de texto y proyector mmproj)
 *  03 Plantilla del prompt (sistema e instrucción de imagen)
 *  04 Longitud de respuesta (tokens máximos)
 * Reutiliza el mismo motor, la misma carga de modelo y el modo diagnóstico del resto de la app, así
 * que cada prueba deja sus números en «Exportar logs».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onBack: () -> Unit,
  viewModel: LlmAskImageViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val task = remember { modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_ASK_IMAGE)!! }
  val visionStore = remember { VisionStore(context) }
  val perfStore = remember { EnginePerfStore(context) }

  var settings by remember { mutableStateOf(visionStore.load()) }
  var tokensText by remember { mutableStateOf(settings.maxResponseTokens.toString()) }
  var mmproj by remember { mutableStateOf(perfStore.load().mmproj) }
  var mmprojFiles by remember { mutableStateOf(MmprojFiles.list(context)) }
  var importing by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  var promptOpen by remember { mutableStateOf(false) }

  var bitmap by remember { mutableStateOf<Bitmap?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var reloadTick by remember { mutableIntStateOf(0) }

  val modelUi by modelManagerViewModel.uiState.collectAsState()
  val chatUi by viewModel.uiState.collectAsState()
  val perfLine by viewModel.perfLine.collectAsState()

  // Modelos con visión de esta tarea (los importados GGUF/LiteRT que la admiten).
  val models: List<Model> = task.models.distinctBy { it.name }
  var selectedName by remember { mutableStateOf(visionStore.lastModel()) }
  val model: Model? = models.firstOrNull { it.name == selectedName } ?: models.firstOrNull()
  val isGguf = model?.runtimeHelper is LlamaCppModelHelper

  fun persist(next: VisionSettings) {
    settings = next.sanitized()
    visionStore.save(settings)
  }

  // Carga (o recarga) el modelo elegido; si cambió el mmproj se fuerza la recarga.
  var lastReload by remember { mutableIntStateOf(0) }
  LaunchedEffect(model?.name, reloadTick) {
    val m = model ?: return@LaunchedEffect
    modelManagerViewModel.selectModel(m)
    val force = reloadTick != lastReload
    lastReload = reloadTick
    modelManagerViewModel.initializeModel(context = context, task = task, model = m, force = force)
  }
  LaunchedEffect(settings.systemPrompt) { viewModel.updateSystemPrompt(settings.systemPrompt) }

  val initStatus = model?.let { modelUi.modelInitializationStatus[it.name]?.status }
  val busy = chatUi.inProgress

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          bitmap = withContext(Dispatchers.IO) { runCatching { decodeScaled(context, uri) }.getOrNull() }
          if (bitmap == null) note = "No se pudo abrir esa imagen."
          error = null
          model?.let { viewModel.clearAllMessages(it) }
        }
      }
    }
  val mmprojPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        importing = true
        scope.launch {
          val name = withContext(Dispatchers.IO) { runCatching { MmprojFiles.import(context, uri) }.getOrNull() }
          importing = false
          if (name == null) note = "No se pudo importar: debe ser un .gguf (mmproj)"
          else {
            mmprojFiles = MmprojFiles.list(context)
            mmproj = name
            perfStore.save(perfStore.load().copy(mmproj = name))
            reloadTick++
            note = "Proyector «$name» elegido: se recarga el modelo."
          }
        }
      }
    }

  fun process() {
    val m = model ?: return
    val bmp = bitmap ?: return
    error = null
    viewModel.clearAllMessages(m)
    viewModel.addMessage(m, ChatMessageText(content = settings.imageInstruction, side = ChatSide.USER))
    viewModel.generateResponse(
      model = m,
      input = settings.imageInstruction,
      images = listOf(bmp),
      onError = { msg -> error = msg },
      allowThinking = false,
      persistInput = false,
      onModelStuck = { modelManagerViewModel.initializeModel(context = context, task = task, model = m) },
      maxResponseTokens = settings.maxResponseTokens.takeIf { it > 0 },
    )
  }

  val answer =
    model
      ?.let { chatUi.messagesByModel[it.name] }
      .orEmpty()
      .lastOrNull { it is ChatMessageText && it.side == ChatSide.AGENT } as? ChatMessageText

  BoxScaffold(
    title = "Visión",
    subtitle = "Describe una imagen con un modelo local · todo en una pantalla",
    onBack = onBack,
  ) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // ── 01 Imagen de prueba ────────────────────────────────────────────────
      VisionSection("01", "Imagen de prueba", "Elige una foto y pulsa «Procesar imagen».") {
        val bmp = bitmap
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
          )
        } else {
          Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              "Aún no hay imagen",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.AddPhotoAlternate, null)
            Text(if (bmp == null) "Elegir imagen" else "Cambiar")
          }
          if (busy) {
            Button(onClick = { model?.let { viewModel.stopResponse(it) } }, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.Stop, null)
              Text("Parar")
            }
          } else {
            Button(
              onClick = { process() },
              enabled = bmp != null && model != null && initStatus == ModelInitializationStatusType.INITIALIZED,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.PlayArrow, null)
              Text("Procesar imagen")
            }
          }
        }
        Text(
          when {
            model == null -> "No hay ningún modelo con visión. Impórtalo en Configuración de inferencia."
            initStatus == ModelInitializationStatusType.INITIALIZING -> "⏳ Cargando el modelo…"
            initStatus == ModelInitializationStatusType.ERROR -> "No se pudo cargar el modelo (revisa los archivos de abajo)."
            initStatus == ModelInitializationStatusType.INITIALIZED -> "Modelo listo."
            else -> "Preparando el modelo…"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("RESPUESTA DEL MODELO", style = MaterialTheme.typography.labelMedium)
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown =
              error?.let { "Error: $it" }
                ?: answer?.content?.takeIf { it.isNotBlank() }
                ?: if (busy) "⏳ Procesando la imagen… la primera respuesta puede tardar varios minutos."
                else "La descripción aparecerá aquí después de procesar la imagen."
            Text(shown, style = MaterialTheme.typography.bodyMedium)
            if (perfLine.isNotBlank()) {
              Text(perfLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        Text(
          "Los tiempos exactos (prompt, tokens/s, motivo de parada) quedan en el modo diagnóstico: Ajustes > Exportar logs.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // ── 02 Archivos del modelo ─────────────────────────────────────────────
      VisionSection("02", "Archivos del modelo", "Modelo de texto y proyector de visión (mmproj).") {
        Text("Modelo", style = MaterialTheme.typography.labelMedium)
        if (models.isEmpty()) {
          Text(
            "No hay modelos con visión. Importa uno en Configuración de inferencia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          models.forEach { m ->
            FilterChip(
              selected = m.name == model?.name,
              enabled = !busy,
              onClick = {
                selectedName = m.name
                visionStore.setLastModel(m.name)
              },
              label = { Text(m.displayName.ifBlank { m.name }.take(34)) },
            )
          }
        }
        if (isGguf) {
          Text("Proyector de visión (mmproj)", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = mmproj.isBlank(),
              enabled = !busy,
              onClick = {
                mmproj = ""
                perfStore.save(perfStore.load().copy(mmproj = ""))
                reloadTick++
              },
              label = { Text("Sin visión") },
            )
            mmprojFiles.forEach { f ->
              FilterChip(
                selected = f == mmproj,
                enabled = !busy,
                onClick = {
                  mmproj = f
                  perfStore.save(perfStore.load().copy(mmproj = f))
                  reloadTick++
                },
                label = { Text(f.take(34)) },
              )
            }
          }
          OutlinedButton(
            onClick = { if (!importing) mmprojPicker.launch(arrayOf("*/*")) },
            enabled = !busy && !importing,
          ) { Text(if (importing) "Importando…" else "Añadir proyector (.gguf)") }
          Text(
            "Consejo de velocidad: un proyector F16 o Q8_0 suele ir más rápido que BF16, y Gemma 4 E2B es más ligero que E4B. " +
              "Al cambiar de archivo el modelo se recarga solo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else if (model != null) {
          Text(
            "Este modelo (LiteRT) lleva la visión integrada: no necesita proyector.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
      }

      // ── 03 Plantilla del prompt ────────────────────────────────────────────
      VisionSection("03", "Plantilla del prompt", "Qué se le pide al modelo.") {
        TextButton(onClick = { promptOpen = !promptOpen }) { Text(if (promptOpen) "Ocultar" else "Editar") }
        if (promptOpen) {
          OutlinedTextField(
            value = settings.systemPrompt,
            onValueChange = { persist(settings.copy(systemPrompt = it)) },
            label = { Text("Prompt del sistema") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = settings.imageInstruction,
            onValueChange = { persist(settings.copy(imageInstruction = it)) },
            label = { Text("Instrucción de imagen") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
          TextButton(
            onClick = {
              val d = VisionSettings()
              persist(settings.copy(systemPrompt = d.systemPrompt, imageInstruction = d.imageInstruction))
            }
          ) { Text("Restablecer") }
        } else {
          Text(
            settings.imageInstruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // ── 04 Longitud de respuesta ───────────────────────────────────────────
      VisionSection("04", "Longitud de respuesta", "Tokens máximos por descripción (-1 = sin límite).") {
        OutlinedTextField(
          value = tokensText,
          onValueChange = {
            tokensText = it.filter { c -> c.isDigit() || c == '-' }.take(5)
            tokensText.toIntOrNull()?.let { v -> persist(settings.copy(maxResponseTokens = v)) }
          },
          label = { Text("Tokens máx.") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(256, 512, 1024, 2048, 4096).forEach { preset ->
            FilterChip(
              selected = settings.maxResponseTokens == preset,
              onClick = {
                tokensText = preset.toString()
                persist(settings.copy(maxResponseTokens = preset))
              },
              label = { Text("$preset") },
            )
          }
        }
        Text(
          "Bajarlo acorta lo que tarda en escribir; no cambia lo que tarda en «ver» la imagen.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun VisionSection(
  index: String,
  title: String,
  subtitle: String,
  content: @Composable () -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(index, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
      }
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      content()
    }
  }
}

/** Decodifica la imagen respetando la rotación EXIF y reduciéndola (lado mayor ≤ 1536 px). */
private fun decodeScaled(context: android.content.Context, uri: Uri): Bitmap {
  val source = ImageDecoder.createSource(context.contentResolver, uri)
  return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    val longest = maxOf(info.size.width, info.size.height)
    if (longest > 1536) {
      val scale = 1536f / longest
      decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }
  }
}
