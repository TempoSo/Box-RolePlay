package com.google.ai.edge.gallery.data.characters

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta los logs de la app para diagnosticar errores: logcat del propio proceso (una app solo
 * puede leer sus propios logs) + datos del equipo + el último cierre inesperado (si lo hubo).
 * Se comparte como un .txt desde el menú de compartir de Android.
 */
object LogExporter {
    private const val CRASH_FILE = "last_crash.txt"
    private const val MAX_LINES = 6000

    /** Guarda el error de un cierre inesperado para incluirlo en el próximo export. */
    fun installCrashRecorder(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(app.filesDir, CRASH_FILE)
                    .writeText("Cierre inesperado $stamp en hilo '${thread.name}'\n" + error.stackTraceToString())
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * BUG-022: los crashes reales del proyecto son nativos (SIGSEGV en libllama.so /
     * liblitertlm_jni.so) y no pasan por el handler de Java, así que last_crash.txt nunca se
     * escribe. El buffer `crash` de logcat persiste entre reinicios del proceso: se filtra por el
     * paquete y se conserva el entorno de cada coincidencia porque el backtrace no repite el nombre.
     */
    private fun nativeCrashLog(context: Context): String {
        val lines =
            runCatching {
                val p =
                    ProcessBuilder(
                        "logcat", "-b", "crash", "-d", "-v", "threadtime", "-t", MAX_LINES.toString()
                    ).redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().readText()
                p.destroy()
                text.lineSequence().toList()
            }.getOrElse { return "(no se pudo leer el buffer de crashes: ${it.message})" }
        val keep = BooleanArray(lines.size)
        lines.forEachIndexed { i, line ->
            if (line.contains(context.packageName, ignoreCase = true)) {
                for (j in (i - 5).coerceAtLeast(0)..(i + 60).coerceAtMost(lines.lastIndex)) keep[j] = true
            }
        }
        return lines.filterIndexed { i, _ -> keep[i] }.joinToString("\n").take(20_000)
    }

    /**
     * BUG-022 (segunda fuente, 19/11): `ApplicationExitInfo` no necesita `READ_LOGS` ni el protobuf
     * del tombstone. Devuelve las últimas salidas anómalas del proceso con su motivo, hora,
     * descripción y, desde Android 12, la traza. Complementa el buffer `crash` de logcat, que una
     * app normal no siempre puede leer.
     */
    private fun nativeExitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val interesting =
                setOf(
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                    android.app.ApplicationExitInfo.REASON_CRASH,
                    android.app.ApplicationExitInfo.REASON_ANR,
                    android.app.ApplicationExitInfo.REASON_SIGNALED,
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                )
            val records =
                manager
                    .getHistoricalProcessExitReasons(context.packageName, 0, 6)
                    .filter { it.reason in interesting }
            buildString {
                records.forEach { info ->
                    val stamp =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(info.timestamp))
                    val reason =
                        when (info.reason) {
                            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
                            android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
                            android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
                            android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                            else -> "motivo ${info.reason}"
                        }
                    appendLine("• $stamp · $reason · estado=${info.status} · pid=${info.pid}")
                    info.description?.takeIf { it.isNotBlank() }?.let {
                        appendLine("  descripción: ${it.take(2000)}")
                    }
                    // getTraceInputStream existe desde Android 11 (API 30); se limita a 12+ por
                    // si el fabricante no la expone y se protege con runCatching.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                                info.traceInputStream?.bufferedReader()?.use { reader ->
                                    val trace = reader.readText().take(8_000)
                                    if (trace.isNotBlank()) {
                                        appendLine("  traza:")
                                        appendLine(trace)
                                    }
                                }
                            }
                            .onFailure { appendLine("  (traza no disponible: ${it.message})") }
                    }
                }
            }
        }.getOrElse { "(no se pudo leer ApplicationExitInfo: ${it.message})" }
    }

    /** Arma el texto completo del reporte. Puede tardar un par de segundos: llamar fuera del hilo principal. */
    fun buildReport(context: Context): String {
        val sb = StringBuilder()
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        sb.appendLine("=== Box RolePlay: reporte de logs ===")
        sb.appendLine("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        sb.appendLine("App: ${context.packageName} v${pkg?.versionName} (${pkg?.longVersionCode})")
        sb.appendLine("Equipo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        val rt = Runtime.getRuntime()
        sb.appendLine("RAM de la app: usada ${(rt.totalMemory() - rt.freeMemory()) shr 20} MB / máx ${rt.maxMemory() shr 20} MB")
        LastEnginePerf.stats?.let {
            sb.appendLine(
                "Último turno GGUF: prompt ${it.promptTokens} tok ${"%.1f".format(it.promptTokensPerSecond)} t/s · " +
                    "gen ${it.generatedTokens} tok ${"%.2f".format(it.generationTokensPerSecond)} t/s · carga ${"%.1f".format(LastEnginePerf.loadSeconds)} s"
            )
        }
        sb.appendLine()
        val crash = File(context.filesDir, CRASH_FILE)
        if (crash.exists()) {
            sb.appendLine("--- Último cierre inesperado ---")
            sb.appendLine(crash.readText().take(20_000))
            sb.appendLine()
        }
        val nativeCrash = nativeCrashLog(context)
        if (nativeCrash.isNotBlank()) {
            sb.appendLine("--- Crash nativo (logcat -b crash) ---")
            sb.appendLine(nativeCrash)
            sb.appendLine()
        }
        val exitReasons = nativeExitReasons(context)
        if (exitReasons.isNotBlank()) {
            sb.appendLine("--- Crash nativo (ApplicationExitInfo) ---")
            sb.appendLine(exitReasons)
            sb.appendLine()
        }
        sb.appendLine("--- logcat (últimas líneas) ---")
        val log =
            runCatching {
                val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", MAX_LINES.toString())
                    .redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().readText()
                p.destroy()
                text
            }.getOrElse { "(no se pudo leer logcat: ${it.message})" }
        sb.append(log)
        return sb.toString()
    }

    /** Escribe el reporte y abre el menú de compartir. */
    fun share(context: Context) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "box-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val file = File(dir, name).apply { writeText(buildReport(context)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Logs de Box RolePlay")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, "Enviar logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
