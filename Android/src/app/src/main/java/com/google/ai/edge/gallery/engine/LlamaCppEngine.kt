package com.google.ai.edge.gallery.engine

import com.jegly.offlineLLM.smollm.SmolLM
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
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

                    withContext(Dispatchers.Main) {
                        isModelLoaded.set(true)
                        onSuccess()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }
    }

    fun unloadModel() {
        stateLock.withLock {
            val gen = generationJob
            val inst = instance
            loadJob?.cancel()
            gen?.cancel()
            isModelLoaded.set(false)
            isGenerating = false
            lastHistory = emptyList()
            // BUG-021: liberar el motor solo cuando el hilo de generación haya terminado. La
            // cancelación surte efecto en el siguiente token (y un prefill en marcha puede
            // tardar bastante más), así que la espera y el cierre van en un hilo aparte; este
            // motor ya no es alcanzable (cleanUp lo saca del mapa antes de llamar aquí).
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(60_000) { gen?.join() }
                } catch (_: Exception) {}
                // Se cierra la instancia capturada: si mientras tanto el motor se hubiera
                // recreado, no se toca la nueva.
                try {
                    inst.close()
                } catch (_: Exception) {}
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
            val inst = instance
            // BUG-002: mientras dura el close+reload, ninguna generacion debe poder entrar
            // (antes seguia a true y una tanda podia arrancar contra un motor a medio cerrar).
            isModelLoaded.set(false)

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // BUG-021: la generacion cancelada termina en el siguiente token (y un
                    // prefill en marcha puede tardar bastante); esperarla aqui, fuera del hilo
                    // llamador, antes de cerrar el contexto nativo.
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(60_000) { gen?.join() }
                    } catch (_: Exception) {}
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
        stateLock.withLock {
            if (!isModelLoaded.get() || isGenerating) return
            instance.setSampler(settings.toFloatArray())
        }
    }

    /** Roleplay: activa el razonamiento (canal de pensamiento de Gemma 4) en el siguiente turno. */
    fun setThinking(enabled: Boolean) {
        if (isGenerating) return
        thinkingEnabled = enabled
        stateLock.withLock {
            if (!isModelLoaded.get()) return
            instance.setThinking(enabled)
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
        stateLock.withLock {
            if (!isModelLoaded.get() || isGenerating) {
                Log.w("AGLlamaPerf", "seedConversation ignorado: modelo ocupado o no cargado")
                return
            }
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
        }
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
        stateLock.withLock {
            if (!isModelLoaded.get() || isGenerating) {
                Log.w("AGLlamaPerf", "prepareForAuxTanda ignorado: modelo ocupado o no cargado")
                return
            }
            lastHistory = emptyList()
            lastSystemPrompt = ""
            if (!useGemma4Template) {
                // El prompt se forma con el historial nativo: hay que vaciarlo para que cada
                // tanda sea independiente (y con él se va la KV, como hasta ahora).
                instance.resetChat()
                return
            }
            // El historial nativo acumula el resumen de cada tanda, pero en la ruta de marcadores
            // no participa en la tokenización (el prompt lo construye GgufChatTemplate en Kotlin);
            // prepareForAuxInference —antes de la primera tanda— lo limpia. Solo queda resetear el
            // sampler: setSampler recrea la cadena y olvida las penalizaciones de la tanda previa.
            samplerSettings?.let { instance.setSampler(it.toFloatArray()) }
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

            generationJob?.cancel()

            generationJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    isGenerating = true
                    var fullResponse = ""
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
                                fullResponse += piece

                                // Detect the earliest stop sequence and truncate
                                val stopIdx = stopSequences
                                    .mapNotNull { seq -> fullResponse.indexOf(seq).takeIf { it >= 0 } }
                                    .minOrNull()
                                if (stopIdx != null) {
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
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        onComplete(
                            GenerationResult(
                                response = finalResponse,
                                tokensPerSecond = instance.getResponseGenerationSpeed(),
                                durationSeconds = duration.inWholeSeconds.toInt(),
                                contextLengthUsed = instance.getContextLengthUsed(),
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    isGenerating = false
                    withContext(Dispatchers.Main) { onCancelled() }
                } catch (e: Exception) {
                    isGenerating = false
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
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
        stateLock.withLock {
            generationJob?.cancel()
            isGenerating = false
        }
    }

    /** Prueba de velocidad sintética (prompt y generación). Devuelve null si no se puede ahora. */
    fun benchModel(pp: Int = 256, tg: Int = 64, pl: Int = 1, nr: Int = 2): String? {
        if (!isModelLoaded.get() || isGenerating) return null
        return stateLock.withLock {
            if (isGenerating) null else instance.benchModel(pp, tg, pl, nr)
        }
    }

    fun isReady(): Boolean = isModelLoaded.get() && !isGenerating
}
