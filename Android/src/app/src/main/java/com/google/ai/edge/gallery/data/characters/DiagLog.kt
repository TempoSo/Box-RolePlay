package com.google.ai.edge.gallery.data.characters

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una generación del modelo, con todo lo necesario para diagnosticar fallos (BUG-029, BUG-017…). */
data class DiagRecord(
    val engine: String,              // "GGUF" / "LiteRT"
    val model: String,
    val params: String,              // contexto, hilos, tope de respuesta, etc.
    val promptLabel: String,         // qué es exactamente [prompt] (completo o solo piezas)
    val prompt: String,
    val response: String,
    val stopReason: String,
    val promptTokens: Int = -1,
    val promptSeconds: Float = -1f,
    val generatedTokens: Int = -1,
    val generationSeconds: Float = -1f,
    val firstTokenMs: Long = -1,
    val totalMs: Long = -1,
    val contextUsed: Int = -1,
    val contextSize: Int = -1,
    val extra: String = "",
)

/** Parte pura (probada): construye el texto de un registro y recorta lo que sea demasiado largo. */
object DiagFormat {
    const val MAX_FIELD_CHARS = 60_000

    fun clip(text: String, max: Int = MAX_FIELD_CHARS): String =
        if (text.length <= max) text
        else text.take(max / 2) + "\n[… recortado: ${text.length - max} caracteres …]\n" + text.takeLast(max / 2)

    private fun num(v: Int) = if (v < 0) "?" else v.toString()
    private fun sec(v: Float) = if (v < 0) "?" else "%.1f".format(Locale.US, v)

    fun render(r: DiagRecord, stamp: String): String {
        val promptTps = if (r.promptSeconds > 0 && r.promptTokens >= 0) "%.1f".format(Locale.US, r.promptTokens / r.promptSeconds) else "?"
        val genTps = if (r.generationSeconds > 0 && r.generatedTokens >= 0) "%.2f".format(Locale.US, r.generatedTokens / r.generationSeconds) else "?"
        return buildString {
            appendLine("=== GENERACIÓN $stamp · ${r.engine} · ${r.model} ===")
            appendLine("Parámetros: ${r.params}")
            appendLine("MOTIVO DE PARADA: ${r.stopReason}")
            appendLine(
                "Prompt: ${num(r.promptTokens)} tok en ${sec(r.promptSeconds)} s ($promptTps t/s) · " +
                    "Generación: ${num(r.generatedTokens)} tok en ${sec(r.generationSeconds)} s ($genTps t/s)",
            )
            appendLine(
                "Primer token: ${if (r.firstTokenMs < 0) "?" else "${r.firstTokenMs} ms"} · " +
                    "Total: ${if (r.totalMs < 0) "?" else "${r.totalMs} ms"} · Contexto usado: ${num(r.contextUsed)}/${num(r.contextSize)}",
            )
            if (r.extra.isNotBlank()) appendLine("Extra: ${r.extra}")
            appendLine("--- PROMPT (${r.promptLabel}) · ${r.prompt.length} caracteres ---")
            appendLine(clip(r.prompt))
            appendLine("--- RESPUESTA CRUDA · ${r.response.length} caracteres ---")
            appendLine(clip(r.response))
            appendLine("=== FIN ===")
        }
    }

    /**
     * Motivo de parada deducido de lo que se sabe. Orden: cancelación/error, secuencia de parada,
     * motivo que informa el motor nativo (BUG-029), tope de tokens, contexto lleno y, si nada de
     * eso, fin natural del modelo (EOS).
     *
     * [nativeStop] es lo que devuelve `SmolLM.getStopReason()`: `"token"` (EOG del modelo),
     * `"stop:<texto>"` (stop string propio) o `"max_tokens"` (tope del motor).
     */
    fun stopReason(
        cancelled: Boolean,
        error: String?,
        stopSequence: String?,
        generatedTokens: Int,
        maxResponse: Int,
        contextUsed: Int,
        contextSize: Int,
        nativeStop: String? = null,
    ): String {
        val native = describeNativeStop(nativeStop)
        return when {
            error != null -> "ERROR: $error"
            cancelled -> "CANCELADA (el usuario o la app la cortó)"
            stopSequence != null -> "secuencia de parada «$stopSequence» (la detectó la app)"
            native != null -> native
            maxResponse > 0 && generatedTokens >= maxResponse -> "TOPE DE RESPUESTA alcanzado ($generatedTokens de $maxResponse tokens)"
            contextSize > 0 && contextUsed >= contextSize * 0.97 -> "CONTEXTO LLENO ($contextUsed de $contextSize)"
            else -> "fin natural del modelo (EOS)"
        }
    }

    /**
     * Marcadores de fin de turno de la plantilla. Si el motor corta por uno de ellos, el turno lo
     * cerró el modelo (es su marcador), no un stop nuestro.
     */
    private val TURN_MARKERS = setOf(
        "<turn|", "<|turn_end|>", "<turn_end|>", "<start_of_turn>", "<end_of_turn>",
    )

    /** Traduce el motivo nativo a texto legible; `null` si el motor no informó de nada. */
    fun describeNativeStop(nativeStop: String?): String? = when {
        nativeStop.isNullOrBlank() -> null
        nativeStop == "token" -> "fin de turno del modelo (token EOG)"
        nativeStop == "max_tokens" -> "TOPE DE RESPUESTA (lo cortó el motor nativo)"
        nativeStop.startsWith("stop:") -> {
            val s = nativeStop.removePrefix("stop:")
            if (s in TURN_MARKERS) "fin de turno del modelo (marcador «$s»)"
            else "secuencia de parada propia «$s» (la detectó el motor nativo)"
        }
        else -> "motivo del motor: $nativeStop"
    }
}

/**
 * BUG-044: modo diagnóstico. Guarda cada generación (prompt, respuesta cruda, tokens/s y motivo de
 * parada) en `files/diag/`, solo las últimas [KEEP]. Aparte del log normal para no engordarlo.
 * Activado por defecto mientras se cazan los bugs pesados; se apaga en Ajustes.
 */
object DiagLog {
    private const val KEEP = 30
    private const val PREFS = "box_diag"
    private const val KEY = "enabled"
    @Volatile private var appContext: Context? = null
    private val seq = java.util.concurrent.atomic.AtomicInteger()

    fun init(context: Context) { appContext = context.applicationContext }

    fun isEnabled(context: Context? = appContext): Boolean =
        runCatching { context!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true) }.getOrDefault(false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    private fun dir(context: Context) = File(context.filesDir, "diag").apply { mkdirs() }

    fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("gen-") }.orEmpty().sortedByDescending { it.name }

    fun clear(context: Context) = files(context).forEach { it.delete() }

    fun record(r: DiagRecord) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) return
        runCatching {
            val now = Date()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
            val name = "gen-" + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(now) + "-" + seq.incrementAndGet() + ".txt"
            File(dir(ctx), name).writeText(DiagFormat.render(r, stamp))
            files(ctx).drop(KEEP).forEach { it.delete() }
            PersistentLog.mark("DIAG", "generación guardada (${r.engine}, ${r.generatedTokens} tok, parada: ${r.stopReason.take(60)})")
        }
    }

    /** Contenido para el reporte: lo más reciente primero, hasta [maxChars] caracteres. */
    fun report(context: Context, maxChars: Int = 600_000): String {
        val sb = StringBuilder()
        for (f in files(context)) {
            val t = runCatching { f.readText() }.getOrNull() ?: continue
            if (sb.length + t.length > maxChars) { sb.appendLine("(… más generaciones omitidas por tamaño)"); break }
            sb.appendLine(t)
        }
        return sb.toString()
    }
}
