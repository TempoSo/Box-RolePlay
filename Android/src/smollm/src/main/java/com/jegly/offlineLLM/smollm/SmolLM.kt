package com.jegly.offlineLLM.smollm

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class SmolLM {
    companion object {
        private const val TAG = "SmolLM"

        init {
            val cpuFeatures = getCPUFeatures()
            val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
            val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
            val hasSve = cpuFeatures.contains("sve")
            val hasI8mm = cpuFeatures.contains("i8mm")
            val isAtLeastArmV82 =
                cpuFeatures.contains("asimd") &&
                    cpuFeatures.contains("crc32") &&
                    cpuFeatures.contains("aes")
            val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")

            val isEmulated =
                (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu"))

            if (!isEmulated) {
                if (supportsArm64V8a()) {
                    // i8mm/dotprod implican un nucleo moderno aunque /proc/cpuinfo no liste dcpop/uscat
                    // (pasa en varios Dimensity/Snapdragon); antes caian a una libreria sin i8mm, mas lenta.
                    if (hasSve && hasI8mm && hasFp16 && hasDotProd) {
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_i8mm_sve")
                    } else if (hasI8mm && hasFp16 && hasDotProd) {
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_i8mm")
                    } else if (isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd) {
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_sve")
                    } else if (isAtLeastArmV84 && hasFp16 && hasDotProd) {
                        System.loadLibrary("smollm_v8_4_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16 && hasDotProd) {
                        System.loadLibrary("smollm_v8_2_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16) {
                        System.loadLibrary("smollm_v8_2_fp16")
                    } else {
                        System.loadLibrary("smollm_v8")
                    }
                } else {
                    System.loadLibrary("smollm")
                }
            } else {
                System.loadLibrary("smollm")
            }
        }

        private fun getCPUFeatures(): String {
            return try {
                File("/proc/cpuinfo").readText()
                    .substringAfter("Features").substringAfter(":").substringBefore("\n").trim()
            } catch (e: FileNotFoundException) {
                ""
            }
        }

        /** Nucleos rapidos ("big"): los 8 hilos incluian los nucleos pequeños y frenaban todo. */
        fun performanceCoreCount(): Int {
            return try {
                val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull {
                    File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")
                        .takeIf { f -> f.exists() }?.readText()?.trim()?.toLongOrNull()
                }
                val max = freqs.maxOrNull() ?: return 4
                freqs.count { it >= max * 0.8 }.coerceIn(2, 8)
            } catch (e: Exception) {
                4
            }
        }

        private fun supportsArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0] == "arm64-v8a"
    }

    private var nativePtr = 0L

    data class InferenceParams(
        val minP: Float = 0.1f,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val storeChats: Boolean = true,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val engine: EngineOptions = EngineOptions(),
    )

    /**
     * Ajustes de rendimiento del motor. Los indices de `kvType` son: 0 f32, 1 f16, 2 bf16, 3 q8_0,
     * 4 q5_0, 5 q5_1, 6 q4_0, 7 q4_1, 8 iq4_nl. `flashAttn`: 0 auto, 1 apagado, 2 encendido.
     * El orden de toIntArray debe coincidir con EngineOptions.h.
     */
    data class EngineOptions(
        val nBatch: Int = 512,
        val nUBatch: Int = 512,
        val nThreadsBatch: Int = 0,
        val flashAttn: Int = 0,
        val kvTypeK: Int = 1,
        val kvTypeV: Int = 1,
        val repack: Boolean = true,
        val maxResponse: Int = -1,
    ) {
        fun toIntArray(): IntArray =
            intArrayOf(nBatch, nUBatch, nThreadsBatch, flashAttn, kvTypeK, kvTypeV, if (repack) 1 else 0, maxResponse)
    }

    /** Estadisticas del ultimo turno: tokens/segundos de prompt y de generacion. */
    data class PerfStats(
        val promptTokens: Int,
        val promptSeconds: Float,
        val generatedTokens: Int,
        val generationSeconds: Float,
    ) {
        val promptTokensPerSecond: Float get() = if (promptSeconds > 0f) promptTokens / promptSeconds else 0f
        val generationTokensPerSecond: Float get() = if (generationSeconds > 0f) generatedTokens / generationSeconds else 0f
    }

    object DefaultParams {
        const val CONTEXT_SIZE: Long = 2048L
        const val CHAT_TEMPLATE: String =
            "{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}{{ '<|im_start|>system You are a helpful AI assistant.<|im_end|> ' }}{% endif %}{{'<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|>' + ' '}}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}"
    }

    suspend fun load(modelPath: String, params: InferenceParams = InferenceParams()) =
        withContext(Dispatchers.IO) {
            val ggufReader = GGUFReader()
            ggufReader.load(modelPath)
            val modelContextSize = ggufReader.getContextSize() ?: DefaultParams.CONTEXT_SIZE
            val modelChatTemplate = ggufReader.getChatTemplate() ?: DefaultParams.CHAT_TEMPLATE
            nativePtr = loadModel(
                modelPath,
                params.minP,
                params.temperature,
                params.topP,
                params.topK,
                params.repeatPenalty,
                params.storeChats,
                params.contextSize ?: modelContextSize,
                params.chatTemplate ?: modelChatTemplate,
                params.numThreads,
                params.useMmap,
                params.useMlock,
                params.engine.toIntArray(),
            )
        }

    /**
     * Generic method to add a message with a specific role.
     * Essential for models like Gemma that use "model" instead of "assistant".
     */
    fun addChatMessage(role: String, message: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, role)
    }

    fun addUserMessage(message: String) {
        addChatMessage("user", message)
    }

    fun addSystemPrompt(prompt: String) {
        addChatMessage("system", prompt)
    }

    fun addAssistantMessage(message: String) {
        addChatMessage("assistant", message)
    }

    /** Borra el historial y el cache del modelo sin recargar los pesos. */
    fun resetChat() {
        verifyHandle()
        resetChat(nativePtr)
    }

    /**
     * Activa/desactiva el razonamiento (canal de pensamiento de Gemma 4). Con `true` se escribe
     * `<|think|>` al inicio del turno de sistema y se deja abierto el canal; con `false` no se
     * escribe y el canal se cierra vacio (respuesta directa).
     */
    fun setThinking(enabled: Boolean) {
        verifyHandle()
        setThinkingNative(nativePtr, enabled)
    }

    /**
     * Ventana deslizante: cuando el prompt supera `high` (fraccion del contexto) se borran los
     * mensajes mas viejos hasta bajar a `low`. El sistema, el saludo y el ultimo mensaje se conservan.
     */
    fun setContextWindow(high: Float, low: Float) {
        verifyHandle()
        setContextWindow(nativePtr, high, low)
    }

    /**
     * Cambia los ajustes de muestreo sin recargar el modelo. El arreglo tiene 13 valores
     * (ver SamplerSettings.toFloatArray en la app); sirve entre turnos.
     */
    fun setSampler(values: FloatArray) {
        verifyHandle()
        setSampler(nativePtr, values)
    }

    /** Limite de tokens por respuesta (-1 = sin limite). Se puede cambiar entre turnos. */
    fun setMaxResponse(maxTokens: Int) {
        verifyHandle()
        setMaxResponse(nativePtr, maxTokens)
    }

    fun getPerfStats(): PerfStats {
        verifyHandle()
        val v = getPerfStats(nativePtr)
        return PerfStats(v[0].toInt(), v[1], v[2].toInt(), v[3])
    }

    fun getResponseGenerationSpeed(): Float {
        verifyHandle()
        return getResponseGenerationSpeed(nativePtr)
    }

    fun getContextLengthUsed(): Int {
        verifyHandle()
        return getContextSizeUsed(nativePtr)
    }

    fun getResponseAsFlow(query: String): Flow<String> = flow {
        verifyHandle()
        startCompletion(nativePtr, query)
        var piece = completionLoop(nativePtr)
        while (piece != "[EOG]") {
            emit(piece)
            piece = completionLoop(nativePtr)
        }
        stopCompletion(nativePtr)
    }

    fun getResponse(query: String): String {
        verifyHandle()
        startCompletion(nativePtr, query)
        var piece = completionLoop(nativePtr)
        var response = ""
        while (piece != "[EOG]") {
            response += piece
            piece = completionLoop(nativePtr)
        }
        stopCompletion(nativePtr)
        return response
    }

    fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String {
        verifyHandle()
        return benchModel(nativePtr, pp, tg, pl, nr)
    }

    fun close() {
        if (nativePtr != 0L) {
            close(nativePtr)
            nativePtr = 0L
        }
    }

    fun isLoaded(): Boolean = nativePtr != 0L

    private fun verifyHandle() {
        check(nativePtr != 0L) { "Model is not loaded. Call SmolLM.load() first." }
    }

    private external fun loadModel(
        modelPath: String, minP: Float, temperature: Float, topP: Float, topK: Int,
        repeatPenalty: Float, storeChats: Boolean, contextSize: Long, chatTemplate: String,
        nThreads: Int, useMmap: Boolean, useMlock: Boolean, engineOptions: IntArray
    ): Long

    private external fun addChatMessage(modelPtr: Long, message: String, role: String)
    private external fun getResponseGenerationSpeed(modelPtr: Long): Float
    private external fun getContextSizeUsed(modelPtr: Long): Int
    private external fun close(modelPtr: Long)
    private external fun startCompletion(modelPtr: Long, prompt: String)
    private external fun completionLoop(modelPtr: Long): String
    private external fun stopCompletion(modelPtr: Long)
    private external fun resetChat(modelPtr: Long)
    private external fun setThinkingNative(modelPtr: Long, enabled: Boolean)
    private external fun setContextWindow(modelPtr: Long, high: Float, low: Float)
    private external fun setSampler(modelPtr: Long, values: FloatArray)
    private external fun setMaxResponse(modelPtr: Long, maxTokens: Int)
    private external fun getPerfStats(modelPtr: Long): FloatArray
    private external fun benchModel(modelPtr: Long, pp: Int, tg: Int, pl: Int, nr: Int): String
}
