/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmchat

import androidx.hilt.navigation.compose.hiltViewModel

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.characters.VisionStore
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.apps.VisionSettingsDialog
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatView
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.emptyStateContent
import com.google.ai.edge.gallery.ui.theme.emptyStateTitle

private const val TAG = "AGLlmChatScreen"

@Composable
fun LlmChatScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  taskId: String = BuiltInTaskId.LLM_CHAT,
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onSkillClicked: () -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model) -> Unit)? = null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  viewModel: LlmChatViewModel = hiltViewModel(),
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  aboveInputComposable: @Composable (Model) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  conversationId: String? = null,
  autoResumeConversation: Boolean = true,
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = taskId,
    navigateUp = navigateUp,
    modifier = modifier,
    onSkillClicked = onSkillClicked,
    onFirstToken = onFirstToken,
    onGenerateResponseDone = onGenerateResponseDone,
    onResetSessionClickedOverride = onResetSessionClickedOverride,
    composableBelowMessageList = composableBelowMessageList,
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = onSystemPromptChanged,
    emptyStateComposable = emptyStateComposable,
    aboveInputComposable = aboveInputComposable,
    sendMessageTrigger = sendMessageTrigger,
    showImagePicker = showImagePicker,
    showAudioPicker = showAudioPicker,
    conversationId = conversationId,
    autoResumeConversation = autoResumeConversation,
  )
}

@Composable
fun LlmAskImageScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmAskImageViewModel = hiltViewModel(),
) {
  // Ajustes propios de la app de visión (Layla / MobileVLM): no afectan al chat ni al roleplay.
  val context = LocalContext.current
  val visionStore = remember { VisionStore(context) }
  var visionSettings by remember { mutableStateOf(visionStore.load()) }
  var showVisionSettings by remember { mutableStateOf(false) }

  // Cada pantalla tiene su propio ViewModel, así que este prompt del sistema no toca el de los
  // personajes ni el de las escenas.
  LaunchedEffect(visionSettings.systemPrompt) {
    viewModel.updateSystemPrompt(visionSettings.systemPrompt)
  }

  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_ASK_IMAGE,
    navigateUp = navigateUp,
    modifier = modifier,
    showImagePicker = true,
    showAudioPicker = false,
    allowImageOnlySend = true,
    imageOnlyPrompt = visionSettings.imageInstruction,
    maxResponseTokens = visionSettings.maxResponseTokens.takeIf { it > 0 },
    aboveInputComposable = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { showVisionSettings = true }) {
          val tope =
            if (visionSettings.maxResponseTokens > 0) "${visionSettings.maxResponseTokens} tok"
            else "sin tope"
          Text("Ajustes de visión · $tope")
        }
      }
    },
    emptyStateComposable = { model ->
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier =
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).padding(bottom = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(stringResource(R.string.askimage_emptystate_title), style = emptyStateTitle)
          val contentRes =
            if (model.runtimeType == RuntimeType.AICORE) R.string.askimage_emptystate_content_aicore
            else R.string.askimage_emptystate_content
          Text(
            stringResource(contentRes),
            style = emptyStateContent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    },
  )

  if (showVisionSettings) {
    VisionSettingsDialog(
      initial = visionSettings,
      onDismiss = { showVisionSettings = false },
      onSaved = { saved ->
        visionSettings = saved
        showVisionSettings = false
      },
    )
  }
}

@Composable
fun LlmAskAudioScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmAskAudioViewModel = hiltViewModel(),
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_ASK_AUDIO,
    navigateUp = navigateUp,
    modifier = modifier,
    showImagePicker = false,
    showAudioPicker = true,
    emptyStateComposable = {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier =
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).padding(bottom = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(stringResource(R.string.askaudio_emptystate_title), style = emptyStateTitle)
          Text(
            stringResource(R.string.askaudio_emptystate_content),
            style = emptyStateContent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    },
  )
}

@Composable
fun ChatViewWrapper(
  viewModel: LlmChatViewModelBase,
  modelManagerViewModel: ModelManagerViewModel,
  taskId: String,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onSkillClicked: () -> Unit = {},
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model) -> Unit)? = null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  aboveInputComposable: @Composable (Model) -> Unit = {},
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  conversationId: String? = null,
  autoResumeConversation: Boolean = true,
  // App de visión dedicada: envío con solo imagen, instrucción de imagen y tope de tokens.
  allowImageOnlySend: Boolean = false,
  imageOnlyPrompt: String? = null,
  maxResponseTokens: Int? = null,
) {
  val context = LocalContext.current
  val task = modelManagerViewModel.getTaskById(id = taskId)!!
  val allowThinking = task.allowThinking()

  // Wire per-conversation system prompt from viewModel state.
  val vmSystemPrompt by viewModel.currentSystemPrompt.collectAsState()
  val effectiveCurSystemPrompt = vmSystemPrompt.ifEmpty { curSystemPrompt }
  val effectiveOnSystemPromptChanged: (String) -> Unit = { sp ->
    viewModel.updateSystemPrompt(sp)
    onSystemPromptChanged(sp)
  }

  // Escenas de roleplay: con quién escribe el usuario y botón "Aleatorio".
  var sceneNames by remember(conversationId) { mutableStateOf<List<String>>(emptyList()) }
  var sceneSpeaker by remember(conversationId) { mutableStateOf<String?>(null) }
  var lastRandomSpeaker by remember(conversationId) { mutableStateOf<String?>(null) }
  // BUG-033/035: opciones de la escena (narrador, persona del usuario, si el modelo responde al enviar).
  var sceneNarrator by remember(conversationId) { mutableStateOf(true) }
  var sceneUsesPersona by remember(conversationId) { mutableStateOf(true) }
  var sceneReplies by remember(conversationId) { mutableStateOf(false) }
  var localTrigger by remember { mutableStateOf<SendMessageTrigger?>(null) }
  androidx.compose.runtime.LaunchedEffect(conversationId) {
    sceneNames =
      if (conversationId == null) emptyList()
      else runCatching { viewModel.sceneMemberNames(conversationId) }.getOrDefault(emptyList())
    val options = if (conversationId == null) null else runCatching { viewModel.sceneOptions(conversationId) }.getOrNull()
    if (options != null) {
      sceneNarrator = options.narrator
      sceneUsesPersona = options.usePersona
      sceneReplies = options.replyToUser
      // Sin persona no existe «Yo»: se empieza escribiendo como el narrador o el primer personaje.
      if (!options.usePersona && sceneSpeaker == null) {
        sceneSpeaker = if (options.narrator) com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.NARRATOR else sceneNames.firstOrNull()
      }
    }
  }

  ChatView(
    task = task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    onSendMessage = { model, rawMessages ->
      // BUG-003: "Aleatorio" viaja en el trigger (instrucción oculta) y no crea mensajes visibles.
      val trigger = localTrigger?.takeIf { it.model.name == model.name }
      if (trigger != null) localTrigger = null
      val hasPayload = rawMessages.any { !(it is ChatMessageText && it.content.isBlank()) }
      // BUG-034: enviar con el campo vacío y un hablante elegido = ese hablante escribe el siguiente
      // mensaje (sin azar). Se relanza como turno forzado, igual que «Aleatorio».
      if (trigger == null && sceneNames.isNotEmpty() && !hasPayload) {
        val who = sceneSpeaker
        if (who != null) {
          val forced =
            com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.restriction(
              speaker =
                if (who == com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.NARRATOR) com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.Speaker.Narrator
                else com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.Speaker.Character(who),
              names = sceneNames,
              narrator = sceneNarrator,
            )
          if (forced != null) {
            localTrigger =
              SendMessageTrigger(model = model, messages = emptyList(), promptSuffix = forced.instruction, restriction = forced)
          }
        }
        return@ChatView
      }
      // BUG-003/004: si escribes como el narrador o un personaje, la IA contesta como los demás.
      // La restricción se inyecta al prompt del turno, nunca como mensaje ni se persiste.
      val restriction =
        trigger?.restriction
          ?: if (sceneNames.isEmpty()) null
          else
            com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.userTurnRestriction(
              speaker = sceneSpeaker,
              names = sceneNames,
              narrator = sceneNarrator,
            )
      val promptSuffix = trigger?.promptSuffix ?: restriction?.instruction
      // Escena: si escribes como el narrador o un personaje, tu texto lleva su nombre delante.
      val speakerPrefix =
        sceneSpeaker?.let { if (it == com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.NARRATOR) "Narrator: " else "$it: " }
      val messages =
        if (sceneNames.isEmpty() || speakerPrefix == null) rawMessages
        else
          rawMessages.map { m ->
            if (m is ChatMessageText && m.side == com.google.ai.edge.gallery.ui.common.chat.ChatSide.USER &&
              !m.content.startsWith("(Next message")
            ) ChatMessageText(content = speakerPrefix + m.content, side = m.side)
            else m
          }
      // BUG-033: en una escena lo que escribes es contexto: se guarda y se ve, pero el modelo no
      // responde (habla quien tú elijas con «Aleatorio» o enviando vacío). El texto llega al motor
      // dentro del siguiente turno.
      if (trigger == null && sceneNames.isNotEmpty() && !sceneReplies && messages.all { it is ChatMessageText }) {
        for (message in messages) {
          viewModel.addMessage(model = model, message = message)
          if (message is ChatMessageText && message.content.isNotBlank()) viewModel.saveContextMessage(model, message.content)
        }
        return@ChatView
      }
      for (message in messages) {
        viewModel.addMessage(model = model, message = message)
      }

      var text = ""
      val images: MutableList<Bitmap> = mutableListOf()
      val audioMessages: MutableList<ChatMessageAudioClip> = mutableListOf()
      for (message in messages) {
        if (message is ChatMessageText) {
          text = message.content
        } else if (message is ChatMessageImage) {
          images.addAll(message.bitmaps)
        } else if (message is ChatMessageAudioClip) {
          audioMessages.add(message)
        }
      }
      // App de visión: si se envía solo una imagen, el turno lleva la instrucción configurada
      // como texto (equivalente al botón «Procesar imagen» de Layla).
      val imageOnlyText =
        if (text.isEmpty() && images.isNotEmpty() && !imageOnlyPrompt.isNullOrBlank()) imageOnlyPrompt
        else null
      val effectiveText = imageOnlyText ?: text
      if (effectiveText.isNotEmpty() || audioMessages.isNotEmpty() || trigger != null) {
        if (imageOnlyText != null) {
          viewModel.addMessage(
            model = model,
            message = ChatMessageText(content = imageOnlyText, side = ChatSide.USER),
          )
        }
        if (text.isNotEmpty()) {
          modelManagerViewModel.addTextInputHistory(text)
        }
        viewModel.generateResponse(
          model = model,
          input = effectiveText,
          images = images,
          audioMessages = audioMessages,
          onFirstToken = onFirstToken,
          onDone = { onGenerateResponseDone(model) },
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
          },
          allowThinking = allowThinking,
          // Con "Aleatorio" no hay texto: el sufijo es todo el prompt del turno.
          promptSuffix = promptSuffix,
          sceneRestriction = restriction,
          // BUG-016: si el modelo no arranca solo, el ViewModel nos pide relanzar la carga.
          onModelStuck = {
            modelManagerViewModel.initializeModel(context = context, task = task, model = model)
          },
          maxResponseTokens = maxResponseTokens,
        )

        firebaseAnalytics?.logEvent(
          GalleryEvent.GENERATE_ACTION.id,
          bundleOf("capability_name" to task.id, "model_id" to model.name),
        )
      }
    },
    onRunAgainClicked = { model, message ->
      if (message is ChatMessageText) {
        viewModel.runAgain(
          model = model,
          message = message,
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
          },
          allowThinking = allowThinking,
        )
      }
    },
    onBenchmarkClicked = { _, _, _, _ -> },
    onResetSessionClicked = { model ->
      if (onResetSessionClickedOverride != null) {
        onResetSessionClickedOverride(task, model)
      } else {
        viewModel.resetSession(
          task = task,
          model = model,
          supportImage = showImagePicker,
          supportAudio = showAudioPicker,
        )
      }
    },
    showStopButtonInInputWhenInProgress = true,
    onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
    onSkillClicked = onSkillClicked,
    navigateUp = navigateUp,
    modifier = modifier,
    composableBelowMessageList = composableBelowMessageList,
    showImagePicker = showImagePicker,
    allowEmptySend = sceneNames.isNotEmpty() && sceneSpeaker != null,
    allowImageOnlySend = allowImageOnlySend,
    emptyStateComposable = emptyStateComposable,
    aboveInputComposable = { model ->
      ContextWindowIndicator(model = model, viewModel = viewModel)
      if (sceneNames.isNotEmpty()) {
        val busy = viewModel.uiState.collectAsState().value.inProgress
        SceneSpeakerBar(
          names = sceneNames,
          narrator = sceneNarrator,
          showMe = sceneUsesPersona,
          selected = sceneSpeaker,
          onSelect = { sceneSpeaker = it },
          enabled = !busy,
          onRandom = {
            val pool = sceneNames.filter { it != sceneSpeaker }
            val pick =
              com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.pickRandom(pool, lastRandomSpeaker)
            if (pick != null) {
              lastRandomSpeaker = pick
              // BUG-003/004: turno sin mensaje visible; la instrucción y el filtro van al motor.
              val restriction =
                com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.restriction(
                  speaker =
                    com.google.ai.edge.gallery.data.characters.ScenePromptBuilder.Speaker.Character(
                      pick
                    ),
                  names = sceneNames,
                  narrator = sceneNarrator,
                )
              localTrigger =
                SendMessageTrigger(
                  model = model,
                  messages = emptyList(),
                  promptSuffix = restriction?.instruction,
                  restriction = restriction,
                )
            }
          },
        )
      }
      aboveInputComposable(model)
    },
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = effectiveCurSystemPrompt,
    onSystemPromptChanged = effectiveOnSystemPromptChanged,
    sendMessageTrigger = localTrigger ?: sendMessageTrigger,
    showAudioPicker = showAudioPicker,
    conversationId = conversationId,
    autoResumeConversation = autoResumeConversation,
  )
}

@Composable
private fun ContextWindowIndicator(model: Model, viewModel: LlmChatViewModelBase) {
  val uiState by viewModel.uiState.collectAsState()
  val messages = uiState.messagesByModel[model.name] ?: return
  if (messages.none { it.type == ChatMessageType.TEXT }) return

  // Se recalcula cuando cambian los mensajes. Cuenta solo lo que el motor ve ahora: los mensajes
  // que ya salieron de la ventana deslizante no suman, y llenarse no es un error (se desliza).
  val usage = viewModel.contextUsage(model)
  if (usage.usedTokens == 0) return

  val color =
    if (usage.fraction > 0.9f) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary
  val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
  val perf by viewModel.perfLine.collectAsState()
  var showInfo by remember { mutableStateOf(false) }

  if (showInfo) {
    val summary = viewModel.lastMemorySummary
    AlertDialog(
      onDismissRequest = { showInfo = false },
      confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Cerrar") } },
      title = { Text("Información del chat") },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Text("Contexto: ~${usage.usedTokens} / ${usage.maxTokens} tokens (${usage.percent}%)")
          Text(
            if (usage.windowActive) "Ventana deslizante: activa. Los mensajes viejos se resumen."
            else "Ventana deslizante: aún no hace falta.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
          )
          Text("\nMemoria (resumen de lo ya olvidado):", style = MaterialTheme.typography.titleSmall)
          Text(
            if (summary.isBlank()) "Todavía no hay nada que resumir." else summary,
            style = MaterialTheme.typography.bodySmall,
          )
          Text("\nPrompt del personaje:", style = MaterialTheme.typography.titleSmall)
          Text(
            viewModel.currentSystemPrompt.value.ifBlank { "(vacío)" },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      },
    )
  }

  Column(
    modifier =
      Modifier.fillMaxWidth().clickable { showInfo = true }
        .padding(horizontal = 16.dp, vertical = 2.dp),
  ) {
    LinearProgressIndicator(
      progress = { usage.fraction },
      modifier = Modifier.fillMaxWidth(),
      color = color,
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        if (usage.windowActive) "Contexto ${usage.percent}% · memoria deslizante activa"
        else "Contexto ${usage.percent}%",
        style = MaterialTheme.typography.labelSmall,
        color = muted,
      )
      Text(
        "~${usage.usedTokens} / ${usage.maxTokens}",
        style = MaterialTheme.typography.labelSmall,
        color = muted,
      )
    }
    if (perf.isNotBlank()) {
      Text(perf, style = MaterialTheme.typography.labelSmall, color = muted)
    }
  }
}
