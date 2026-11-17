package com.google.ai.edge.gallery.ui.memory

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.characters.LastEnginePerf
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
 * modelo si hace falta, y por cada prompt deja el motor limpio y devuelve el texto completo.
 * Se usa para "Absorber clústeres".
 *
 * BUG-002: la absorción va por tandas (un prompt pequeño por llamada, ver MemoryAbsorbPrompt.
 * tandas) para no desbordar el contexto: con GGUF un prompt enorme deslizaba la ventana y
 * crasheaba (BUG-001) y con LiteRT agotaba el watchdog ("El modelo tardó demasiado").
 *
 * BUG-002 (2.ª ronda): el reset por tanda ya no cierra/recarga el modelo (prepareForAuxInference);
 * en GGUF resetConversation hacía close+reload asíncrono y la inferencia podía arrancar contra un
 * motor a medio cerrar (use-after-free: SIGSEGV en llama_vocab::tokenize).
 *
 * BUG-002 (3.ª ronda): el timeout por tanda ya no es un fijo de 180 s (en un móvil con prefill
 * ~6.5 t/s, una tanda de ~1400 tokens necesita ~215 s solo de prefill: saltaba sin haber colgado),
 * sino que se estima con la velocidad real medida del dispositivo (LastEnginePerf). Y entre
 * tandas Gemma 4 conserva la KV (auxTandaBoundary): el preámbulo archivista, idéntico en todas
 * las tandas, solo se prefill una vez.
 */
class MemoryAbsorber(
    private val context: Context,
    private val modelManager: ModelManagerViewModel,
    private val scope: CoroutineScope,
) {
    @Volatile var running = false
        private set

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
                    // BUG-002: via ligera, sin close/reload del modelo (en GGUF eso dejaba una
                    // ventana de carrera con la inferencia de la tanda: SIGSEGV en el tokenizador).
                    // BUG-002 (3.ª ronda): solo antes de la PRIMERA tanda se resetea a fondo;
                    // entre tandas el límite es aún más ligero (en Gemma 4 conserva la KV).
                    if (index == 0) {
                        model.runtimeHelper.prepareForAuxInference(model = model)
                    } else {
                        model.runtimeHelper.auxTandaBoundary(model = model)
                    }
                    val timeoutMs = tandaTimeoutMs(prompt.length, LastEnginePerf.stats)
                    Log.i(
                        "MemoryAbsorber",
                        "tanda ${index + 1}/${prompts.size} con modelo ${model.name} " +
                            "(${prompt.length} car., watchdog ${timeoutMs / 1000} s)",
                    )
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
                    val error = withTimeoutOrNull(timeoutMs) { finished.await() }
                        ?: run {
                            model.runtimeHelper.stopResponse(model)
                            "El modelo tardó demasiado en la tanda ${index + 1} de ${prompts.size} " +
                                "(límite ${timeoutMs / 1000} s)."
                        }
                    if (error != null) {
                        onError(error)
                        return@launch
                    }
                    outputs.add(out.toString())
                }
                if (outputs.size < prompts.size) return@launch // ya se avisó del error de la tanda
                onProgress(prompts.size, prompts.size)
                onResult(outputs)
            } catch (e: Exception) {
                Log.e("MemoryAbsorber", "Fallo al absorber", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                running = false
            }
        }
    }

    companion object {
        /**
         * Timeout por tanda según la velocidad real del dispositivo (estadísticas de los últimos
         * turnos del motor). Estimación: ~3.5 car./token de media; prefill a la velocidad medida
         * (x1.5 de margen) más ~300 tokens de resumen a la velocidad de generación medida (x1.5).
         * Sin estadísticas (primera tanda tras abrir la app) usa supuestos conservadores.
         * Suelo 4 min, techo 30 min: es un límite anti-cuelgue, no un presupuesto de tiempo.
         */
        fun tandaTimeoutMs(promptChars: Int, stats: com.jegly.offlineLLM.smollm.SmolLM.PerfStats?): Long {
            val promptTps = stats?.promptTokensPerSecond?.takeIf { it > 0.5f } ?: 5f
            val genTps = stats?.generationTokensPerSecond?.takeIf { it > 0.5f } ?: 3f
            val tokens = (promptChars / 3.5f).coerceAtLeast(1f)
            val prefillMs = tokens / promptTps * 1000f * 1.5f
            val genMs = 300f / genTps * 1000f * 1.5f
            return (prefillMs + genMs).toLong().coerceIn(240_000L, 1_800_000L)
        }
    }
}
