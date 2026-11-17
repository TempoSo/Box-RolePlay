package com.google.ai.edge.gallery.ui.memory

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.runtime.runtimeHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ejecuta UNA o VARIAS generaciones cortas con el modelo elegido (sin tocar el chat): carga el
 * modelo si hace falta, y por cada prompt resetea la conversación y devuelve el texto completo.
 * Se usa para "Absorber clústeres".
 *
 * BUG-002: la absorción va por tandas (un prompt pequeño por llamada, ver MemoryAbsorbPrompt.
 * tandas) para no desbordar el contexto: con GGUF un prompt enorme deslizaba la ventana y
 * crasheaba (BUG-001) y con LiteRT agotaba el watchdog ("El modelo tardó demasiado").
 */
class MemoryAbsorber(
    private val context: Context,
    private val modelManager: ModelManagerViewModel,
    private val scope: CoroutineScope,
) {
    @Volatile var running = false
        private set

    /** Máximo que puede tardar cada tanda antes de darla por agotada. */
    private val promptTimeoutMs = 180_000L

    fun run(
        preferredModel: String?,
        prompts: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (running) return
        if (prompts.isEmpty()) { onError("No hay notas para absorber."); return }
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

                val outputs = mutableListOf<String>()
                for ((index, prompt) in prompts.withIndex()) {
                    onProgress(index, prompts.size)
                    // Cada tanda arranca limpia (sin historial de la anterior ni de un chat).
                    model.runtimeHelper.resetConversation(model = model)
                    delay(300)
                    val out = StringBuilder()
                    val finished = CompletableDeferred<String?>()
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
                    val error = withTimeoutOrNull(promptTimeoutMs) { finished.await() }
                        ?: run { model.runtimeHelper.stopResponse(model); "El modelo tardó demasiado (tanda ${index + 1} de ${prompts.size})." }
                    if (error != null) {
                        onError(error)
                        return@launch
                    }
                    outputs.add(out.toString())
                }
                if (outputs.size < prompts.size) return@launch // ya se avisó del error de la tanda
                onProgress(prompts.size, prompts.size)
                onResult(outputs)            } catch (e: Exception) {
                Log.e("MemoryAbsorber", "Fallo al absorber", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                running = false
            }
        }
    }
}
