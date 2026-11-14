package com.google.ai.edge.gallery.data.characters

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
