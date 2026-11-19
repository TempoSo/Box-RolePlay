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

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.DEFAULT_VISION_ACCELERATOR
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.CleanUpListener
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope

private const val TAG = "AGLlmChatModelHelper"

/** I-6: espera máxima a que el hilo nativo termine el turno anterior antes de reutilizar el motor. */
private const val AWAIT_GENERATION_MS = 10_000L

/**
 * I-6: una respuesta que termina antes de este tiempo y sin ningún fragmento es, en la práctica,
 * el fallo conocido del turno cancelado (la conversación quedó inservible). Se trata como error.
 */
private const val EMPTY_RESPONSE_MS = 750L

data class LlmModelInstance(val engine: Engine, var conversation: Conversation)

object LlmChatModelHelper : LlmModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  /**
   * BUG-021: generación en curso por modelo; se completa en onDone/onError del callback nativo.
   * Al destruir el motor se cancela y se espera (acotado) antes de cerrar conversación y motor.
   */
  private val pendingGenerations:
    MutableMap<String, java.util.concurrent.CompletableFuture<Unit>> =
    java.util.concurrent.ConcurrentHashMap()

  /**
   * I-6: candado por modelo, equivalente al `stateLock` de `engine/LlamaCppEngine.kt` (nativeGate).
   * Serializa reset, envío, limpieza y recreación de la conversación para que nunca se cierre una
   * conversación con un turno nativo en curso (era la causa de las respuestas vacías tras cancelar).
   */
  private val modelGates = java.util.concurrent.ConcurrentHashMap<String, Any>()

  /** I-6: peticiones canceladas a mano por el usuario, para no confundir su final con un error. */
  private val manualStops = java.util.concurrent.ConcurrentHashMap<String, AtomicBoolean>()

  /** I-6: evita dos limpiezas simultáneas del mismo modelo. */
  private val cleaningUp = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  /** I-6: la espera/cierre de la limpieza nunca debe correr en el hilo principal. */
  private val cleanupExecutor =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
      Thread(r, "litert-cleanup").apply { isDaemon = true }
    }

  private fun gateFor(name: String): Any = modelGates.getOrPut(name) { Any() }

  /**
   * I-6: espera acotada a que el callback nativo cierre la generación anterior. Si el callback no
   * llega (motor atascado), se sigue adelante para no bloquear el chat indefinidamente.
   */
  private fun awaitPreviousGeneration(name: String, timeoutMs: Long) {
    val pending = pendingGenerations[name] ?: return
    if (pending.isDone) return
    try {
      pending.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
      Log.w(TAG, "La generación anterior de '$name' no terminó en ${timeoutMs} ms: ${e.message}")
    }
  }

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  override fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    coroutineScope: CoroutineScope?,
  ) {
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val visionAccelerator =
      model.getStringConfigValue(
        key = ConfigKeys.VISION_ACCELERATOR,
        defaultValue = DEFAULT_VISION_ACCELERATOR.label,
      )
    val visionBackend =
      when (visionAccelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label, Accelerator.TPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.GPU()
      }
    val shouldEnableImage = supportImage
    val shouldEnableAudio = supportAudio
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label, Accelerator.TPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
      }
    Log.d(TAG, "Preferred backend: $preferredBackend")

    val modelPath = model.getPath(context = context)
    fun configFor(withImage: Boolean, withAudio: Boolean) =
      EngineConfig(
        modelPath = modelPath,
        backend = preferredBackend,
        visionBackend = if (withImage) visionBackend else null, // must be GPU for Gemma 3n
        audioBackend = if (withAudio) Backend.CPU() else null, // must be CPU for Gemma 3n
        maxNumTokens = maxTokens,
        cacheDir =
          if (modelPath.startsWith("/data/local/tmp"))
            context.getExternalFilesDir(null)?.absolutePath
          else null,
      )

    // Create an instance of LiteRT LM engine and conversation.
    try {
      // Si el motor no puede cargar la visión o el audio del modelo (p. ej. "The Vision Encoder
      // model must have exactly one signature but got 3"), se reintenta solo con texto: para
      // roleplay es mejor un modelo que carga sin imágenes que un modelo que no carga.
      var created: Engine? = null
      var firstError: Exception? = null
      // Decodificación especulativa: solo si el usuario la activó y el archivo del modelo la trae.
      var supportsSpeculative = false
      var modelInfoLog = "ModelInfo no disponible"
      var modelInfoRead = false
      val settingsStore =
        com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore(context)
      val wantsSpeculative = settingsStore.speculativeDecoding()
      try {
        ModelInfo.from(modelPath).use { modelInfo ->
          modelInfoRead = true
          modelInfoLog = "class=${modelInfo::class.java.simpleName}"
          if (modelInfo is ModelInfo.Llm) {
            supportsSpeculative = modelInfo.hasSpeculativeDecodingSupport()
            modelInfoLog += " speculative=$supportsSpeculative"
          }
        }
      } catch (e: Exception) {
        // Si no se puede leer, se asume que no la soporta, pero se deja rastro del motivo.
        Log.w(TAG, "No se pudo leer ModelInfo de '${model.name}'", e)
      }
      // Se guarda para que RoleplaySettingsDialog pueda avisar/deshabilitar el interruptor.
      // Solo se guarda si ModelInfo se pudo leer: un fallo de lectura no debe bloquear el interruptor.
      if (modelInfoRead) settingsStore.setSpeculativeSupported(model.name, supportsSpeculative)
      val useSpeculative = supportsSpeculative && wantsSpeculative
      Log.i(
        TAG,
        "Speculative decoding: model='${model.name}' supported=$supportsSpeculative " +
          "requested=$wantsSpeculative active=$useSpeculative info=[$modelInfoLog]",
      )
      if (wantsSpeculative && !supportsSpeculative) {
        Log.w(
          TAG,
          "Decodificación especulativa activada en ajustes pero '${model.name}' no la soporta; " +
            "se ejecuta sin ella",
        )
      }

      val attempts = buildList {
        add(Triple(shouldEnableImage, shouldEnableAudio, useSpeculative))
        if (useSpeculative) add(Triple(shouldEnableImage, shouldEnableAudio, false))
        if (shouldEnableImage || shouldEnableAudio) add(Triple(false, false, false))
      }
      for ((withImage, withAudio, withSpeculative) in attempts) {
        var candidate: Engine? = null
        try {
          Log.d(
            TAG,
            "Creating engine: image=$withImage audio=$withAudio speculative=$withSpeculative",
          )
          ExperimentalFlags.enableSpeculativeDecoding = withSpeculative
          candidate = Engine(configFor(withImage, withAudio))
          try {
            candidate.initialize()
          } finally {
            ExperimentalFlags.enableSpeculativeDecoding = false
          }
          created = candidate
          if (useSpeculative && !withSpeculative) {
            // El motor no pudo arrancar con decodificación especulativa: se deja constancia en el
            // log, sin bloquear el interruptor (el fallo puede ser pasajero, p. ej. poca memoria).
            Log.w(
              TAG,
              "'${model.name}' no pudo inicializar con decodificación especulativa; se usa el modo normal",
            )
          }
          if (!withImage && shouldEnableImage || !withAudio && shouldEnableAudio) {
            Log.w(TAG, "Engine loaded WITHOUT image/audio after: ${firstError?.message}")
          }
          break
        } catch (e: Exception) {
          Log.w(TAG, "Engine init failed (image=$withImage, audio=$withAudio, spec=$withSpeculative): ${e.message}")
          if (firstError == null) firstError = e
          try {
            candidate?.close()
          } catch (ignored: Exception) {}
        }
      }
      val engine: Engine = created ?: throw (firstError ?: IllegalStateException("Failed to create engine"))

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val conversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (preferredBackend is Backend.NPU) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      model.instance = LlmModelInstance(engine = engine, conversation = conversation)
    } catch (e: Exception) {
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      // I-6: bajo el candado del modelo; si hay un turno en curso se cancela y se espera antes
      // de cerrar la conversación (cerrarla con el hilo nativo vivo dejaba el motor inservible).
      synchronized(gateFor(model.name)) {
        val instance = model.instance as LlmModelInstance? ?: return
        manualStops[model.name]?.set(true)
        runCatching { instance.conversation.cancelProcess() }
        awaitPreviousGeneration(model.name, AWAIT_GENERATION_MS)
        runCatching { instance.conversation.close() }

        val engine = instance.engine
        val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
        val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
        val temperature =
          model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
        val shouldEnableImage = supportImage
        val shouldEnableAudio = supportAudio
        Log.d(TAG, "Enable image: $shouldEnableImage, enable audio: $shouldEnableAudio")

        val accelerator =
          model.getStringConfigValue(
            key = ConfigKeys.ACCELERATOR,
            defaultValue = Accelerator.GPU.label,
          )
        ExperimentalFlags.enableConversationConstrainedDecoding =
          enableConversationConstrainedDecoding
        val newConversation =
          engine.createConversation(
            ConversationConfig(
              samplerConfig =
                if (accelerator == Accelerator.NPU.label) {
                  null
                } else {
                  SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                  )
                },
              systemInstruction = systemInstruction,
              tools = tools,
            )
          )
        ExperimentalFlags.enableConversationConstrainedDecoding = false
        instance.conversation = newConversation

        Log.d(TAG, "Resetting done")
      }
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset conversation", e)
    }
  }

  /**
   * Roleplay: como resetConversation, pero la conversación nueva arranca con un historial
   * (`initialMessages`). Roles: "user" y "assistant". Se usa para la ventana deslizante de contexto
   * y para reabrir un chat con el modelo ya "recordando" lo anterior.
   */
  @OptIn(ExperimentalApi::class)
  fun resetConversationWithHistory(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    history: List<Pair<String, String>>,
  ) {
    try {
      Log.d(TAG, "Resetting conversation with ${history.size} messages for '${model.name}'")

      // I-6: mismo candado que resetConversation. Este es el camino que usa el chat LiteRT al
      // enviar tras cancelar; cerrar aquí la conversación con el turno anterior vivo dejaba todas
      // las respuestas vacías (~500 ms) hasta reabrir la app.
      synchronized(gateFor(model.name)) {
        val instance = model.instance as LlmModelInstance? ?: return
        manualStops[model.name]?.set(true)
        runCatching { instance.conversation.cancelProcess() }
        awaitPreviousGeneration(model.name, AWAIT_GENERATION_MS)
        runCatching { instance.conversation.close() }

        val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
        val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
        val temperature =
          model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
        val accelerator =
          model.getStringConfigValue(
            key = ConfigKeys.ACCELERATOR,
            defaultValue = Accelerator.GPU.label,
          )

        // Gemma pide que los turnos alternen empezando por el usuario. Si el historial empieza con
        // el saludo del personaje (asistente), se antepone un turno de usuario neutro.
        val messages = mutableListOf<Message>()
        if (history.firstOrNull()?.first == "assistant") {
          messages.add(Message.user("(The roleplay begins.)"))
        }
        for ((role, content) in history) {
          if (content.isBlank()) continue
          messages.add(if (role == "user") Message.user(content) else Message.model(content))
        }

        val newConversation =
          instance.engine.createConversation(
            ConversationConfig(
              samplerConfig =
                if (accelerator == Accelerator.NPU.label) {
                  null
                } else {
                  SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                  )
                },
              systemInstruction = systemInstruction,
              initialMessages = messages,
            )
          )
        instance.conversation = newConversation
        Log.d(TAG, "Reset with history done")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset conversation with history", e)
    }
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    if (model.instance == null) {
      return
    }

    // I-6: la cancelación + espera del turno nativo puede tardar; nunca en el hilo principal.
    if (Looper.myLooper() == Looper.getMainLooper()) {
      cleanupExecutor.execute { cleanUpBlocking(model, onDone) }
      return
    }
    cleanUpBlocking(model, onDone)
  }

  private fun cleanUpBlocking(model: Model, onDone: () -> Unit) {
    if (!cleaningUp.add(model.name)) {
      Log.d(TAG, "cleanUp: ya había una limpieza en curso para '${model.name}'")
      onDone()
      return
    }
    try {
      // I-6: el mismo candado que reset y envío: no se cierra la conversación si otro camino la
      // está recreando o mandando un turno.
      synchronized(gateFor(model.name)) {
        if (model.instance == null) {
          onDone()
          return
        }

        // BUG (12:44): la instancia puede no ser de LiteRT (p. ej. un GGUF cargado por otra tarea
        // cuya limpieza llegó aquí de casualidad): el cast duro acababa en ClassCastException y
        // crasheaba la app. Ante una instancia ajena, no se toca: se deja nula y se termina.
        val instance = model.instance as? LlmModelInstance
        if (instance == null) {
          Log.w(TAG, "cleanUp: la instancia de ${model.name} no es de LiteRT; no se limpia aquí")
          model.instance = null
          onDone()
          return
        }

        // BUG-021: si hay una generación en marcha, cancelarla y esperar (acotado) a que el hilo
        // nativo termine antes de cerrar la conversación y el motor; si se cierra con un turno
        // en marcha, la rotación/limpieza acaba en SIGSEGV en liblitertlm_jni.so.
        val pending = pendingGenerations.remove(model.name)
        manualStops[model.name]?.set(true)
        try {
          instance.conversation.cancelProcess()
          pending?.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
          Log.w(TAG, "No se pudo esperar la generación al limpiar: ${e.message}")
        }

        try {
          instance.conversation.close()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to close the conversation: ${e.message}")
        }

        try {
          instance.engine.close()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to close the engine: ${e.message}")
        }

        val onCleanUp = cleanUpListeners.remove(model.name)
        if (onCleanUp != null) {
          onCleanUp()
        }
        model.instance = null

        onDone()
        Log.d(TAG, "Clean up done.")
      }
    } finally {
      cleaningUp.remove(model.name)
    }
  }

  override fun stopResponse(model: Model) {
    // I-6: marcar la cancelación antes de avisar al nativo; así su final no se confunde con una
    // respuesta vacía. No se toma el candado aquí a propósito: el botón de parar está en el hilo
    // principal y no debe esperar a un reset en curso.
    manualStops[model.name]?.set(true)
    val instance = model.instance as? LlmModelInstance ?: return
    instance.conversation.cancelProcess()
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>?,
  ) {
    // I-6: todo el envío va bajo el candado del modelo. Si el usuario canceló el turno anterior y
    // envía otro, aquí se espera a que el hilo nativo lo suelte antes de mandar el nuevo (ese era
    // el camino que dejaba todas las respuestas vacías).
    synchronized(gateFor(model.name)) {
      val instance = model.instance as? LlmModelInstance
      if (instance == null) {
        onError("LlmModelInstance is not initialized.")
        return
      }

      // Set listener.
      if (!cleanUpListeners.containsKey(model.name)) {
        cleanUpListeners[model.name] = cleanUpListener
      }

      awaitPreviousGeneration(model.name, AWAIT_GENERATION_MS)

      val conversation = instance.conversation

      // BUG-021: registrar la generación para poder cancelarla y esperarla al limpiar el motor.
      // I-6: también se usa para saber si la respuesta vacía es una cancelación manual.
      val pending = java.util.concurrent.CompletableFuture<Unit>()
      pendingGenerations[model.name] = pending
      val stopFlag = AtomicBoolean(false)
      manualStops[model.name] = stopFlag
      val startedAt = SystemClock.elapsedRealtime()
      val delivered = AtomicBoolean(false)

      val contents = mutableListOf<Content>()
      for (image in images) {
        contents.add(Content.ImageBytes(image.toPngByteArray()))
      }
      for (audioClip in audioClips) {
        contents.add(Content.AudioBytes(audioClip))
      }
      // add the text after image and audio for the accurate last token
      if (input.trim().isNotEmpty()) {
        contents.add(Content.Text(input))
      }

      fun finish(throwable: Throwable?) {
        if (pendingGenerations[model.name] === pending) pendingGenerations.remove(model.name)
        if (manualStops[model.name] === stopFlag) manualStops.remove(model.name)
        pending.complete(Unit)
        when {
          throwable is CancellationException -> {
            Log.i(TAG, "The inference is cancelled.")
            resultListener("", true, null)
          }
          throwable != null -> {
            Log.e(TAG, "onError", throwable)
            onError("Error: ${throwable.message}")
          }
          // I-6: tras cancelar, la conversación podía quedar inservible y el motor devolvía un
          // "done" vacío en ~500 ms. Eso no es una respuesta: se trata como error para que el
          // ViewModel limpie y reinitialice en vez de dejar el chat mudo.
          !delivered.get() &&
            !stopFlag.get() &&
            SystemClock.elapsedRealtime() - startedAt < EMPTY_RESPONSE_MS -> {
            Log.w(
              TAG,
              "Respuesta vacía de '${model.name}' en ${SystemClock.elapsedRealtime() - startedAt} ms; " +
                "se trata como error",
            )
            onError(
              "El modelo devolvió una respuesta vacía. Se reinicia la conversación; " +
                "vuelve a enviar el mensaje."
            )
          }
          else -> resultListener("", true, null)
        }
      }

      conversation.sendMessageAsync(
        Contents.of(contents),
        object : MessageCallback {
          override fun onMessage(message: Message) {
            delivered.set(true)
            resultListener(message.toString(), false, message.channels["thought"])
          }

          override fun onDone() {
            finish(null)
          }

          override fun onError(throwable: Throwable) {
            finish(throwable)
          }
        },
        extraContext ?: emptyMap(),
      )
    }
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
  }
}
