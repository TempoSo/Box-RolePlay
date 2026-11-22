package com.google.ai.edge.gallery.engine

import com.jegly.offlineLLM.smollm.SmolLM
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.measureTime

/**
 * Box: llama.cpp inference engine wrapper.
 * Provides the same lifecycle (load → generate → unload) as LiteRT
 * but backed by llama.cpp via the smollm JNI module.
 */
class LlamaCppEngine {

    companion object {
        /** Al pasar de este porcentaje del contexto se limpian los mensajes más viejos... */
        const val CONTEXT_WINDOW_HIGH = 0.75f

        /** ...hasta bajar a este porcentaje (como Layla: 75 % -> ~45 %). */
        const val CONTEXT_WINDOW_LOW = 0.45f

        /** Espera máxima para que una generación cancelada termine de verdad en el nativo. */
        private const val GATE_WAIT_MS = 60_000L
    }

    private var instance = SmolLM()

    /** Ajustes de muestreo de Roleplay; se aplican al cargar el modelo y al cambiarlos. */
    @Volatile var samplerSettings: com.google.ai.edge.gallery.data.characters.SamplerSettings? = null
    private val stateLock = ReentrantLock()

    // Track load params for resetConversation
    var lastLoadParams: SmolLM.InferenceParams? = null
        private set
    var lastModelPath: String? = null
        private set
    var lastSystemPrompt: String = ""
        private set

    // BUG-001/BUG-015: con la plantilla de Gemma 4 aplicada en Kotlin, el prompt se manda ya
    // formateado y el nativo lo tokeniza tal cual (sin _applyContextWindow ni apply_template).
    @Volatile private var useGemma4Template = false
    @Volatile private var thinkingEnabled = false

    /** Historial sembrado por seedConversation (sin vacios); se usa al construir el prompt. */
    @Volatile private var lastHistory: List<Pair<String, String>> = emptyList()

    @Volatile
    private var generationJob: Job? = null

    @Volatile
    private var loadJob: Job? = null

    val isModelLoaded = AtomicBoolean(false)

    /**
     * I-3 (revisión de código): puerta única del motor nativo. LLMInference no es seguro entre
     * hilos, y cancelar una corrutina solo surte efecto en el siguiente token (un prefill largo
     * sigue dentro del nativo). La generación la retiene desde antes de su primera llamada nativa
     * hasta después de la última; seedConversation y el resto de operaciones esperan a que se
     * libere, y una generación nueva no arranca hasta que la anterior haya terminado de verdad.
     * Es un Semaphore (no un ReentrantLock) porque la corrutina puede reanudarse en otro hilo.
     * Orden de bloqueo: primero la puerta y luego stateLock; nunca al revés.
     */
    private val nativeGate = Semaphore(1)

    /** Identifica la última generación pedida, para que una anterior no apague su isGenerating. */
    private val generationEpoch = AtomicInteger(0)

    /** true mientras el tope de tokens de la respuesta es el de una inferencia auxiliar. */
    @Volatile private var maxResponseOverridden = false

    /** Ejecuta [block] con la puerta del nativo; null si no se libró en [waitMs]. */
    private inline fun <T> whenNativeIdle(waitMs: Long, block: () -> T): T? {
        if (!nativeGate.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) return null
        try {
            return block()
        } finally {
            nativeGate.release()
        }
    }

    @Volatile
    var isGenerating = false
        private set

    data class GenerationResult(
        val response: String,
        val tokensPerSecond: Float,
        val durationSeconds: Int,
        val contextLengthUsed: Int,
    )

    /** true si se cargó un mmproj y este modelo admite imágenes. */
    val visionReady: Boolean get() = instance.visionReady

    /**
     * Encola las imágenes (reducidas a [maxSide] px) para el próximo mensaje y devuelve el texto
     * con los marcadores que hay que poner delante del mensaje, o "" si no hay visión.
     */
    fun queueImages(images: List<android.graphics.Bitmap>, maxSide: Int): String {
        if (!instance.visionReady) return ""
        val marker = instance.mediaMarker()
        val sb = StringBuilder()
        for (bmp in images) {
            val (rgb, w, h) = toRgb(bmp, maxSide) ?: continue
            if (instance.addImage(rgb, w, h)) sb.append(marker).append('\n')
        }
        return sb.toString()
    }

    private fun toRgb(src: android.graphics.Bitmap, maxSide: Int): Triple<ByteArray, Int, Int>? {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val w = maxOf(1, (src.width * scale).toInt())
        val h = maxOf(1, (src.height * scale).toInt())
        // copy(): un Bitmap de hardware no deja leer sus píxeles.
        val soft = src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return null
        val scaled = if (w == soft.width && h == soft.height) soft else android.graphics.Bitmap.createScaledBitmap(soft, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 3)
        for (i in px.indices) {
            val c = px[i]
            out[i * 3] = ((c shr 16) and 0xFF).toByte()
            out[i * 3 + 1] = ((c shr 8) and 0xFF).toByte()
            out[i * 3 + 2] = (c and 0xFF).toByte()
        }
        if (scaled !== soft) scaled.recycle()
        if (soft !== src) soft.recycle()
        return Triple(out, w, h)
    }

    fun loadModel(
        modelPath: String,
        params: SmolLM.InferenceParams = SmolLM.InferenceParams(),
        systemPrompt: String = "",
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        // Track for resetConversation
        lastLoadParams = params
        lastModelPath = modelPath
        lastSystemPrompt = systemPrompt
        com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "cargando ${java.io.File(modelPath).name} (${java.io.File(modelPath).length() shr 20} MB) ctx=${params.contextSize} hilos=${params.numThreads}")

        stateLock.withLock {
            loadJob?.cancel()

            loadJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    val loadTime = measureTime { instance.load(modelPath, params) }
                    com.google.ai.edge.gallery.data.characters.LastEnginePerf.loadSeconds =
                        loadTime.inWholeMilliseconds / 1000f
                    Log.i("AGLlamaPerf", "modelo cargado en ${"%.1f".format(loadTime.inWholeMilliseconds / 1000f)} s")
                    if (params.mmprojPath != null) {
                        Log.i("AGLlamaPerf", "vision (mmproj ${params.mmprojPath}): ${if (instance.visionReady) "lista" else "NO se pudo cargar"}")
                    }
                    instance.setContextWindow(CONTEXT_WINDOW_HIGH, CONTEXT_WINDOW_LOW)
                    samplerSettings?.let { instance.setSampler(it.toFloatArray()) }

                    if (systemPrompt.isNotBlank()) {
                        instance.addSystemPrompt(systemPrompt)
                    }

                    for ((role, content) in conversationHistory) {
                        instance.addChatMessage(role, content)
                    }

                    com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "modelo cargado en ${"%.1f".format(loadTime.inWholeMilliseconds / 1000f)} s")
                    withContext(Dispatchers.Main) {
                        isModelLoaded.set(true)
                        onSuccess()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "ERROR al cargar: ${e.javaClass.simpleName}: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }
    }

    fun unloadModel() {
        com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "descargando modelo")
        stateLock.withLock {
            val gen = generationJob
            val load = loadJob
            val inst = instance
            load?.cancel()
            gen?.cancel()
            isModelLoaded.set(false)
            isGenerating = false
            lastHistory = emptyList()
            // BUG-021 / I-1: liberar el motor solo cuando hayan terminado la generación Y la carga
            // en curso. La cancelación surte efecto en el siguiente token (un prefill puede tardar
            // bastante más) y una carga nativa no se puede interrumpir: si se cerrara antes, el
            // close vería nativePtr == 0 y el modelo que termina de cargar después quedaría
            // abierto sin dueño (varios GB de RAM). La espera y el cierre van en un hilo aparte;
            // este motor ya no es alcanzable (cleanUp lo saca del mapa antes de llamar aquí).
            CoroutineScope(Dispatchers.Default).launch {
                val terminaron =
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(GATE_WAIT_MS) {
                            gen?.join()
                            load?.join()
                            true
                        } == true
                    } catch (_: Exception) {
                        false
                    }
                if (!terminaron) {
                    // Cerrar con el hilo nativo aún dentro es un SIGSEGV seguro; es preferible
                    // dejar el motor sin cerrar (fuga puntual) que matar la app (BUG-030).
                    Log.e("AGLlamaPerf", "unloadModel: la generación o la carga no terminaron en ${GATE_WAIT_MS / 1000} s; no se cierra el motor")
                    return@launch
                }
                // Se cierra la instancia capturada y la actual (si resetConversation la había
                // sustituido). close() es atómico e idempotente.
                try { inst.close() } catch (_: Exception) {}
                try { instance.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Box: Reset conversation while keeping the model loaded.
     * Closes the current llama.cpp context and re-opens with the same params.
     * This clears the KV cache and chat history without reloading weights from disk.
     */
    fun resetConversation(
        modelPath: String,
        params: SmolLM.InferenceParams = lastLoadParams ?: SmolLM.InferenceParams(),
        systemPrompt: String = "",
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {},
    ) {
        stateLock.withLock {
            generationJob?.cancel()
            isGenerating = false
            // La conversacion nativa se borra; el historial sembrado para la plantilla tambien.
            lastHistory = emptyList()
            val gen = generationJob
            val prevLoad = loadJob
            val inst = instance
            // BUG-002: mientras dura el close+reload, ninguna generacion debe poder entrar
            // (antes seguia a true y una tanda podia arrancar contra un motor a medio cerrar).
            isModelLoaded.set(false)

            // I-1: este recargado cuenta como "carga en curso": si unloadModel llega a mitad, espera
            // a que termine (y cierra la instancia nueva) en vez de dejarla abierta sin dueño.
            loadJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    // BUG-021: la generacion cancelada termina en el siguiente token (y un
                    // prefill en marcha puede tardar bastante); esperarla aqui, fuera del hilo
                    // llamador, antes de cerrar el contexto nativo. Igual con una carga previa.
                    val terminaron =
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(GATE_WAIT_MS) {
                                gen?.join()
                                prevLoad?.join()
                                true
                            } == true
                        } catch (_: Exception) {
                            false
                        }
                    if (!terminaron) {
                        throw IllegalStateException("El motor sigue ocupado; no se puede reiniciar la conversación ahora")
                    }
                    // Close and reopen — this clears the KV cache and message history
                    // but the OS keeps model pages in memory so reload is fast
                    inst.close()
                    instance = SmolLM()
                    instance.load(modelPath, params)
                    instance.setContextWindow(CONTEXT_WINDOW_HIGH, CONTEXT_WINDOW_LOW)
                    samplerSettings?.let { instance.setSampler(it.toFloatArray()) }

                    if (systemPrompt.isNotBlank()) {
                        instance.addSystemPrompt(systemPrompt)
                    }

                    isModelLoaded.set(true)
                    withContext(Dispatchers.Main) { onSuccess() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    isModelLoaded.set(false)
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    /**
     * Roleplay: deja el historial nativo igual al que se ve en pantalla (prompt de sistema + mensajes
     * anteriores, sin el mensaje nuevo). Se llama antes de cada respuesta, así el personaje siempre
     * está presente y las ediciones/borrados de mensajes se respetan. Es barato: el modelo reutiliza
     * lo ya procesado y solo calcula lo que cambió.
     *
     * Roles: "user" y "assistant". No hace nada si el modelo está ocupado.
     */
    /** Cambia el muestreo entre turnos (no hace nada si el modelo está ocupado). */
    fun applySampler(settings: com.google.ai.edge.gallery.data.characters.SamplerSettings) {
        samplerSettings = settings
        // Sin espera: si el nativo está ocupado se queda como antes ("no hace nada si ocupado").
        whenNativeIdle(0) {
            stateLock.withLock {
                if (!isModelLoaded.get() || isGenerating) return@whenNativeIdle
                instance.setSampler(settings.toFloatArray())
            }
        }
    }

    /** Roleplay: activa el razonamiento (canal de pensamiento de Gemma 4) en el siguiente turno. */
    fun setThinking(enabled: Boolean) {
        if (isGenerating) return
        thinkingEnabled = enabled
        whenNativeIdle(GATE_WAIT_MS) {
            stateLock.withLock {
                if (!isModelLoaded.get()) return@whenNativeIdle
                instance.setThinking(enabled)
            }
        }
    }

    /**
     * BUG-015: activa la plantilla de Gemma 4 en Kotlin ([GgufChatTemplate]). Se llama al cargar
     * el modelo si su nombre es de la familia Gemma 4; el nativo solo la aplica si detecta los
     * marcadores <|turn> en los metadatos del GGUF, que a veces no estan o su plantilla Jinja
     * falla en llama.cpp y el prompt salia como texto crudo.
     */
    fun setGemma4Template(enabled: Boolean) {
        useGemma4Template = enabled
    }

    fun seedConversation(systemPrompt: String, history: List<Pair<String, String>>) {
        if (!isModelLoaded.get() || isGenerating) {
            Log.w("AGLlamaPerf", "seedConversation ignorado: modelo ocupado o no cargado")
            return
        }
        // I-3: si acabas de parar una respuesta, el hilo nativo puede seguir dentro (prefill);
        // se espera a que termine antes de tocar el historial o la KV.
        val hecho =
            whenNativeIdle(GATE_WAIT_MS) {
                stateLock.withLock {
                    if (!isModelLoaded.get() || isGenerating) return@whenNativeIdle false
                    instance.resetChat()
                    lastSystemPrompt = systemPrompt
                    val seeded = mutableListOf<Pair<String, String>>()
                    if (systemPrompt.isNotBlank()) {
                        instance.addSystemPrompt(systemPrompt)
                    }
                    for ((role, content) in history) {
                        // BUG-001: no sembrar cadenas vacias (el nativo acabaria tokenizandolas).
                        if (content.isNotBlank()) {
                            instance.addChatMessage(role, content)
                            seeded.add(role to content)
                        }
                    }
                    lastHistory = seeded
                    true
                }
            }
        if (hecho != true) Log.w("AGLlamaPerf", "seedConversation ignorado: el motor nativo sigue ocupado")
    }

    /**
     * BUG-002 (3.ª ronda): límite entre tandas de absorción. En la ruta de marcadores (Gemma 4)
     * NO tira la KV: el nativo (LLMInference::_prepareDecode + reusablePrefix) reutiliza el
     * prefijo común —el preámbulo archivista es idéntico en todas las tandas— y solo prefill las
     * notas nuevas. resetChat() haría llama_memory_clear y obligaría a re-prefill ~1400 tokens
     * por tanda (con prefill ~6.5 t/s son más de 180 s por tanda: el watchdog saltaba siempre).
     * En la ruta normal (prompt formado con el historial nativo) sí hace falta vaciar el chat.
     */
    fun prepareForAuxTanda() {
        val hecho =
            whenNativeIdle(GATE_WAIT_MS) {
                stateLock.withLock {
                    if (!isModelLoaded.get() || isGenerating) return@whenNativeIdle false
                    lastHistory = emptyList()
                    lastSystemPrompt = ""
                    if (!useGemma4Template) {
                        // El prompt se forma con el historial nativo: hay que vaciarlo para que cada
                        // tanda sea independiente (y con él se va la KV, como hasta ahora).
                        instance.resetChat()
                        return@whenNativeIdle true
                    }
                    // El historial nativo acumula el resumen de cada tanda, pero en la ruta de marcadores
                    // no participa en la tokenización (el prompt lo construye GgufChatTemplate en Kotlin);
                    // prepareForAuxInference —antes de la primera tanda— lo limpia. Solo queda resetear el
                    // sampler: setSampler recrea la cadena y olvida las penalizaciones de la tanda previa.
                    samplerSettings?.let { instance.setSampler(it.toFloatArray()) }
                    true
                }
            }
        if (hecho != true) Log.w("AGLlamaPerf", "prepareForAuxTanda ignorado: modelo ocupado o no cargado")
    }

    /**
     * BUG-002 (4.ª ronda): tope de tokens generados para las inferencias auxiliares (absorción).
     * Sin tope, un resumen desbocado (el modelo que no emite EOS) puede consumir el watchdog
     * entero solo en generación. Debe restaurarse con [restoreMaxResponse] al acabar la tanda.
     */
    fun setMaxResponse(tokens: Int) {
        whenNativeIdle(GATE_WAIT_MS) {
            stateLock.withLock {
                if (!isModelLoaded.get() || isGenerating) return@whenNativeIdle
                instance.setMaxResponse(tokens)
                maxResponseOverridden = true
            }
        }
    }

    /** Devuelve el tope de generación al que tenía el chat (EngineOptions.maxResponse). */
    fun restoreMaxResponse() {
        // Salida rápida (este método se llama desde callbacks, a veces en el hilo principal): si el
        // tope ya se devolvió al terminar la generación, o hay otra en marcha, no se espera la puerta.
        if (!maxResponseOverridden || isGenerating) return
        whenNativeIdle(GATE_WAIT_MS) {
            stateLock.withLock {
                if (!isModelLoaded.get() || isGenerating) return@whenNativeIdle
                restoreMaxResponseLocked()
            }
        }
    }

    /** Restaura el tope del chat si estaba cambiado. Quien llama ya tiene la puerta del nativo. */
    private fun restoreMaxResponseLocked() {
        if (!maxResponseOverridden) return
        maxResponseOverridden = false
        try {
            instance.setMaxResponse(lastLoadParams?.engine?.maxResponse ?: -1)
        } catch (e: Exception) {
            Log.w("AGLlamaPerf", "no se pudo restaurar el tope de respuesta: ${e.message}")
        }
    }

    fun generateResponse(
        query: String,
        onToken: (String) -> Unit,
        onComplete: (GenerationResult) -> Unit,
        onCancelled: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        stateLock.withLock {
            if (!isModelLoaded.get()) {
                onError(IllegalStateException("Model not loaded"))
                return
            }

            // I-3: la anterior se cancela, pero la nueva no toca el nativo hasta que aquella haya
            // terminado de verdad (nativeGate). isGenerating se marca YA, aquí, y no dentro de la
            // corrutina: antes quedaba una ventana en la que seedConversation podía colarse.
            com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "generando (consulta de ${query.length} caracteres)")
            generationJob?.cancel()
            val epoch = generationEpoch.incrementAndGet()
            isGenerating = true

            val job = CoroutineScope(Dispatchers.Default).launch {
                var gateHeld = false
                // Callback final (completo, cancelado o error). Se ejecuta DESPUÉS de soltar la
                // puerta del nativo, para que quien lo reciba pueda llamar a otros métodos del motor.
                var finish: (suspend () -> Unit)? = null
                // BUG-044 (diagnóstico): lo que se necesita para registrar la generación pase lo que pase.
                var fullResponse = ""
                var diagPrompt = ""
                var diagLabel = ""
                var diagStopSeq: String? = null
                var diagFirstMs = -1L
                val diagStartNs = System.nanoTime()
                try {
                    gateHeld = nativeGate.tryAcquire(GATE_WAIT_MS, TimeUnit.MILLISECONDS)
                    if (!gateHeld) {
                        throw IllegalStateException(
                            "El motor sigue ocupado con la respuesta anterior; inténtalo de nuevo en unos segundos.",
                        )
                    }
                    ensureActive()
                    var lastDisplayed = ""

                    // Stop generation at any of these tokens — covers ChatML (Qwen/Mistral),
                    // Llama-3 instruct, and generic EOS. Without this the model talks to itself.
                    val stopSequences = listOf(
                        "<|im_end|>",    // ChatML — Qwen, Mistral, etc.
                        "<|eot_id|>",    // Llama-3
                        "<|endoftext|>", // GPT-style EOS
                        "<|im_start|>",  // Model starting a new turn (should have stopped already)
                    )
                    var shouldStop = false

                    val duration = measureTime {
                        // BUG-015: con Gemma 4 el prompt llega ya con la plantilla de turnos
                        // construida en Kotlin; el nativo la detecta y tokeniza tal cual, sin
                        // pasar por el formateo nativo ni _applyContextWindow (ruta del crash
                        // de BUG-001).
                        val prompt =
                            if (useGemma4Template) {
                                GgufChatTemplate.build(lastSystemPrompt, lastHistory, query, thinkingEnabled)
                            } else query
                        if (useGemma4Template) {
                            diagLabel = "COMPLETO, tal como se envía al modelo (plantilla Gemma 4 armada en Kotlin)"
                            diagPrompt = prompt
                        } else {
                            diagLabel = "SOLO PIEZAS: el prompt final lo arma el código nativo con la plantilla del modelo"
                            diagPrompt = buildString {
                                appendLine("[SISTEMA]"); appendLine(lastSystemPrompt)
                                appendLine("[HISTORIAL: ${lastHistory.size} turnos]")
                                lastHistory.forEach { (role, text) -> appendLine("<$role> $text") }
                                appendLine("[MENSAJE ACTUAL]"); append(query)
                            }
                        }
                        // Diagnostico de crashes (BUG-002): estado del prompt justo antes de
                        // tokenizar. Solo tamanos, nunca el contenido.
                        Log.i(
                            "AGLlamaPerf",
                            "prompt: ${prompt.length} car. | plantilla=${useGemma4Template} | " +
                                "sys=${lastSystemPrompt.length} car. | hist=${lastHistory.size} turnos",
                        )
                        instance.getResponseAsFlow(prompt)
                            .takeWhile { !shouldStop }
                            .collect { piece ->
                                if (diagFirstMs < 0) diagFirstMs = (System.nanoTime() - diagStartNs) / 1_000_000
                                fullResponse += piece

                                // Detect the earliest stop sequence and truncate
                                val stopIdx = stopSequences
                                    .mapNotNull { seq -> fullResponse.indexOf(seq).takeIf { it >= 0 } }
                                    .minOrNull()
                                if (stopIdx != null) {
                                    diagStopSeq = stopSequences.firstOrNull { fullResponse.indexOf(it) == stopIdx }
                                    fullResponse = fullResponse.substring(0, stopIdx)
                                    shouldStop = true
                                    return@collect
                                }

                                // Send only the NEW portion (delta) so the ViewModel can append
                                // correctly. Sending the full string caused "hello hello hello..."
                                val displayResponse = cleanModelOutput(fullResponse, isFinal = false)
                                val delta = if (displayResponse.length > lastDisplayed.length) {
                                    displayResponse.substring(lastDisplayed.length)
                                } else ""
                                lastDisplayed = displayResponse

                                if (delta.isNotEmpty()) {
                                    withContext(Dispatchers.Main) { onToken(delta) }
                                }
                            }
                    }

                    val finalResponse = cleanModelOutput(fullResponse, isFinal = true).ifBlank {
                        if (fullResponse.isNotBlank()) "(No visible content produced)" else "(Empty response)"
                    }

                    runCatching {
                        val st = instance.getPerfStats()
                        com.google.ai.edge.gallery.data.characters.LastEnginePerf.stats = st
                        Log.i(
                            "AGLlamaPerf",
                            "prompt: ${st.promptTokens} tok en ${"%.1f".format(st.promptSeconds)} s " +
                                "(${"%.1f".format(st.promptTokensPerSecond)} t/s) | " +
                                "generacion: ${st.generatedTokens} tok en ${"%.1f".format(st.generationSeconds)} s " +
                                "(${"%.2f".format(st.generationTokensPerSecond)} t/s)",
                        )
                    }
                    // Las lecturas nativas se hacen aquí, con la puerta aún en la mano.
                    val result = GenerationResult(
                        response = finalResponse,
                        tokensPerSecond = instance.getResponseGenerationSpeed(),
                        durationSeconds = duration.inWholeSeconds.toInt(),
                        contextLengthUsed = instance.getContextLengthUsed(),
                    )
                    diagRecord(diagPrompt, diagLabel, fullResponse, false, null, diagStopSeq, diagFirstMs,
                        (System.nanoTime() - diagStartNs) / 1_000_000, runCatching { instance.getPerfStats() }.getOrNull(), result.contextLengthUsed)
                    finish = { onComplete(result) }
                } catch (e: CancellationException) {
                    diagRecord(diagPrompt, diagLabel, fullResponse, true, null, diagStopSeq, diagFirstMs,
                        (System.nanoTime() - diagStartNs) / 1_000_000, null, -1)
                    finish = { onCancelled() }
                } catch (e: Exception) {
                    diagRecord(diagPrompt, diagLabel, fullResponse, false, "${e.javaClass.simpleName}: ${e.message}", diagStopSeq, diagFirstMs,
                        (System.nanoTime() - diagStartNs) / 1_000_000, null, -1)
                    finish = { onError(e) }
                } finally {
                    if (gateHeld) {
                        // El tope de una inferencia auxiliar (absorción) vale para una sola
                        // generación: se devuelve aquí, también si se cancela o falla (el callback
                        // de cancelación no siempre llega), antes de soltar la puerta.
                        restoreMaxResponseLocked()
                        nativeGate.release()
                    }
                    if (generationEpoch.get() == epoch) isGenerating = false
                }
                finish?.let { withContext(Dispatchers.Main) { it() } }
            }
            generationJob = job
            // Si la corrutina se cancela antes de empezar, su cuerpo (y su finally) no se ejecuta.
            job.invokeOnCompletion {
                if (generationEpoch.get() == epoch) isGenerating = false
            }
        }
    }

    /** BUG-044: guarda la generación en el modo diagnóstico (no hace nada si está apagado). */
    private fun diagRecord(
        prompt: String, label: String, response: String, cancelled: Boolean, error: String?, stopSeq: String?,
        firstMs: Long, totalMs: Long, st: SmolLM.PerfStats?, ctxUsed: Int,
    ) {
        val lp = lastLoadParams
        val maxResp = lp?.engine?.maxResponse ?: -1
        val ctxSize = lp?.contextSize?.toInt() ?: -1
        val gen = st?.generatedTokens ?: -1
        com.google.ai.edge.gallery.data.characters.DiagLog.record(
            com.google.ai.edge.gallery.data.characters.DiagRecord(
                engine = "GGUF",
                model = lastModelPath?.let { java.io.File(it).name } ?: "?",
                params = "ctx=$ctxSize hilos=${lp?.numThreads} tope=$maxResp temp=${lp?.temperature} topP=${lp?.topP} topK=${lp?.topK} " +
                    "minP=${lp?.minP} repeat=${lp?.repeatPenalty} plantilla4=$useGemma4Template pensar=$thinkingEnabled",
                promptLabel = label, prompt = prompt, response = response,
                stopReason = com.google.ai.edge.gallery.data.characters.DiagFormat.stopReason(cancelled, error, stopSeq, gen, maxResp, ctxUsed, ctxSize),
                promptTokens = st?.promptTokens ?: -1, promptSeconds = st?.promptSeconds ?: -1f,
                generatedTokens = gen, generationSeconds = st?.generationSeconds ?: -1f,
                firstTokenMs = firstMs, totalMs = totalMs, contextUsed = ctxUsed, contextSize = ctxSize,
            ),
        )
    }

    private fun cleanModelOutput(raw: String, isFinal: Boolean): String {
        var cleaned = raw
            // ChatML tokens (Qwen, Mistral, etc.)
            .replace(Regex("<\\|im_start\\|>(system|user|assistant)\\n?", RegexOption.IGNORE_CASE), "")
            .replace("<|im_end|>", "")
            .replace("<|endoftext|>", "")
            .replace("<|eot_id|>", "")
            // Thinking blocks
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "")
            // Gemma turn tokens
            .replace(Regex("<start_of_turn>.*?\\n", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<end_of_turn>", RegexOption.IGNORE_CASE), "")
            // Misc
            .replace(Regex("<turn\\|.*?\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|turn_end\\|>", RegexOption.IGNORE_CASE), "")
            .replace("System instruction:", "")

        if (isFinal) {
            cleaned = cleaned.replace(Regex("<.*$", RegexOption.IGNORE_CASE), "")
        }

        return cleaned.trim()
    }

    fun stopGeneration() {
        com.google.ai.edge.gallery.data.characters.PersistentLog.mark("GGUF", "stopGeneration")
        stateLock.withLock {
            generationJob?.cancel()
            isGenerating = false
        }
    }

    /** Prueba de velocidad sintética (prompt y generación). Devuelve null si no se puede ahora. */
    fun benchModel(pp: Int = 256, tg: Int = 64, pl: Int = 1, nr: Int = 2): String? {
        if (!isModelLoaded.get() || isGenerating) return null
        // Sin espera: si el nativo está ocupado (aunque sea una respuesta recién cancelada), no corre.
        return whenNativeIdle(0) {
            stateLock.withLock {
                if (isGenerating) null else instance.benchModel(pp, tg, pl, nr)
            }
        }
    }

    fun isReady(): Boolean = isModelLoaded.get() && !isGenerating
}
