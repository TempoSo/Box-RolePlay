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
 * BUG-002 (3.ª ronda): entre tandas Gemma 4 conserva la KV (auxTandaBoundary): el preámbulo
 * archivista, idéntico en todas las tandas, solo se prefill una vez.
 *
 * BUG-002 (4.ª ronda): cada tanda va con tope de generación de [MAX_RESPONSE_TOKENS] tokens
 * (setMaxResponse nativo, restaurado al acabar): sin tope, un resumen desbocado (sin EOS)
 * consumió el watchdog entero en la re-prueba (379 s).
 *
 * BUG-002 (5.ª ronda): las estimaciones de tiempo fallaron dos veces seguidas (180 s, 623 s)
 * porque la térmica del SoC ralentiza el prefill a menos de la mitad en carga sostenida
 * (medido: 5.8 t/s fresco → ~2.4 t/s caliente). Ahora el límite por tanda es un TOPE generoso
 * anti-cuelgue ([TANDA_TIMEOUT_MS], 20 min) más un detector de atasco real en generación
 * ([STALL_TIMEOUT_MS]: sin tokens nuevos, cuando llegan cada ~0.3 s), y hay una pausa de
 * enfriamiento de [COOLDOWN_MS] entre tandas.
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
                var ready = false
                kotlinx.coroutines.withTimeoutOrNull(180_000) {
                    while (model.instance == null) delay(150)
                    ready = true
                }
                if (!ready) { onError("El modelo no terminó de cargar."); return@launch }

                val outputs = mutableListOf<String>()
                for ((index, prompt) in prompts.withIndex()) {
                    onProgress(index, prompts.size)
                    // Pausa de enfriamiento entre tandas: la térmica redujo el prefill a menos
                    // de la mitad en las re-pruebas (5.8 -> ~2.4 t/s con carga sostenida).
                    if (index > 0) {
                        Log.i("MemoryAbsorber", "pausa de enfriamiento ${COOLDOWN_MS / 1000} s antes de la tanda ${index + 1}")
                        delay(COOLDOWN_MS)
                    }
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
                    Log.i(
                        "MemoryAbsorber",
                        "tanda ${index + 1}/${prompts.size} con modelo ${model.name} " +
                            "(${prompt.length} car., tope $TANDA_TIMEOUT_MS ms, resumen ≤$MAX_RESPONSE_TOKENS tok)",
                    )
                    val out = StringBuilder()
                    val finished = CompletableDeferred<String?>()
                    // Progreso visible para el detector de atasco (los partial llegan en el hilo
                    // del motor; un AtomicLong evita leer el StringBuilder en carrera).
                    val progress = java.util.concurrent.atomic.AtomicLong()
                    // BUG-002 (4.ª ronda): tope de tokens generados en llama.cpp. Sin tope, un
                    // resumen desbocado (sin EOS) consumió el watchdog entero de la tanda 1 en
                    // la re-prueba (379 s). En LiteRT no se envía: litertlm podría rechazar el
                    // kwarg desconocido.
                    val esGguf = model.runtimeHelper ==
                        com.google.ai.edge.gallery.runtime.LlamaCppModelHelper
                    model.runtimeHelper.runInference(
                        model = model,
                        input = prompt,
                        resultListener = { partial, done, _ ->
                            if (partial.isNotEmpty()) progress.addAndGet(partial.length.toLong())
                            out.append(partial)
                            if (done) finished.complete(null)
                        },
                        cleanUpListener = { finished.complete(null) },
                        onError = { msg -> finished.complete(msg) },
                        coroutineScope = scope,
                        extraContext =
                            if (esGguf) mapOf("max_response_tokens" to "$MAX_RESPONSE_TOKENS") else null,
                    )
                    // Vigilancia por fases (5.ª ronda): mientras no llegue ningún token se está
                    // en prefill (solo cuenta el tope global); tras el primero, un silencio de
                    // [STALL_TIMEOUT_MS] es un cuelgue, no lentitud (los tokens llegan cada ~0.3 s).
                    val started = System.nanoTime()
                    var lastLen = 0L
                    var lastProgress = started
                    var gotToken = false
                    var motivo: String? = null
                    while (motivo == null) {
                        if (finished.isCompleted) break
                        val now = System.nanoTime()
                        val len = progress.get()
                        if (len != lastLen) { lastLen = len; lastProgress = now; gotToken = true }
                        motivo = tandaAbortReason(
                            elapsedMs = (now - started) / 1_000_000,
                            idleMs = (now - lastProgress) / 1_000_000,
                            gotToken = gotToken,
                        )?.let { reason ->
                            when (reason) {
                                "stall" -> "La generación de la tanda ${index + 1} de ${prompts.size} se " +
                                    "detuvo (sin tokens nuevos en ${STALL_TIMEOUT_MS / 1000} s)."
                                else -> "La tanda ${index + 1} de ${prompts.size} superó el límite de " +
                                    "${TANDA_TIMEOUT_MS / 1000} s."
                            }
                        }
                        if (motivo != null) model.runtimeHelper.stopResponse(model) else delay(250)
                    }
                    val error = motivo ?: finished.await()
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
        /** Tope de tokens generados por tanda: acota la generación desbocada (sin EOS). */
        const val MAX_RESPONSE_TOKENS = 400

        /**
         * Tope total de una tanda (prefill + generación). NO es un presupuesto de tiempo: con la
         * térmica el prefill de una tanda puede ir a ~2.4 t/s (~500 s) y aun así terminar bien;
         * este límite solo existe para no esperar eternamente ante un cuelgue real.
         */
        const val TANDA_TIMEOUT_MS = 1_200_000L

        /** Sin tokens nuevos tras el primero: cuelgue (los tokens llegan cada ~0.3 s). */
        const val STALL_TIMEOUT_MS = 120_000L

        /** Pausa de enfriamiento entre tandas (la térmica del SoC ralentiza el prefill a la mitad). */
        const val COOLDOWN_MS = 30_000L

        /**
         * Decide si la tanda debe abortar. null = seguir; no null = motivo ("stall" o "timeout").
         * El atasco ([STALL_TIMEOUT_MS]) solo aplica tras el primer token; en prefill no hay
         * señal de progreso y manda el tope global ([TANDA_TIMEOUT_MS]).
         */
        fun tandaAbortReason(elapsedMs: Long, idleMs: Long, gotToken: Boolean): String? = when {
            gotToken && idleMs > STALL_TIMEOUT_MS -> "stall"
            elapsedMs > TANDA_TIMEOUT_MS -> "timeout"
            else -> null
        }
    }
}
