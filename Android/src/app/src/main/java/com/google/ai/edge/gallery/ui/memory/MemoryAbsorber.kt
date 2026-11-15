package com.google.ai.edge.gallery.ui.memory

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.runtime.runtimeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ejecuta UNA generación corta con el modelo elegido (sin tocar el chat): carga el modelo si hace
 * falta, resetea su conversación y devuelve el texto completo. Se usa para "Absorber clústeres".
 */
class MemoryAbsorber(
    private val context: Context,
    private val modelManager: ModelManagerViewModel,
    private val scope: CoroutineScope,
) {
    @Volatile var running = false
        private set

    fun run(preferredModel: String?, prompt: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (running) return
        val task = modelManager.getTaskById(BuiltInTaskId.LLM_CHAT)
        val downloaded = task?.models?.filter {
            modelManager.uiState.value.modelDownloadStatus[it.name]?.status ==
                com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED
        } ?: emptyList()
        val model: Model? = downloaded.firstOrNull { it.name == preferredModel } ?: downloaded.firstOrNull()
        if (task == null || model == null) {
            onError("Elige un modelo en Configuración de inferencia para poder absorber recuerdos.")
            return
        }
        running = true
        scope.launch {
            try {
                if (modelManager.uiState.value.modelInitializationStatus[model.name]?.status !=
                    ModelInitializationStatusType.INITIALIZED
                ) {
                    modelManager.initializeModel(context, task, model)
                }
                // Espera (máx. 3 min) a que el motor esté listo.
                val ready = withTimeoutOrNull(180_000) {
                    while (model.instance == null) delay(150)
                    true
                }
                if (ready != true) { onError("El modelo no terminó de cargar."); return@launch }

                model.runtimeHelper.resetConversation(model = model)
                delay(300)
                val out = StringBuilder()
                val finished = kotlinx.coroutines.CompletableDeferred<String?>()
                model.runtimeHelper.runInference(
                    model = model,
                    input = prompt,
                    resultListener = { partial, done, _ ->
                        out.append(partial)
                        if (done) finished.complete(null)
                    },
                    cleanUpListener = { finished.complete(null) },
                    onError = { msg -> finished.complete(msg) },
                    coroutineScope = scope,
                )
                val error = withTimeoutOrNull(240_000) { finished.await() }
                    ?: run { model.runtimeHelper.stopResponse(model); "El modelo tardó demasiado." }
                if (error != null) onError(error) else onResult(out.toString())
            } catch (e: Exception) {
                Log.e("MemoryAbsorber", "Fallo al absorber", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                running = false
            }
        }
    }
}
