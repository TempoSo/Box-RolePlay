package com.google.ai.edge.gallery.data.characters

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BUG-042: log persistente. Una app solo puede leer el logcat de **su propio proceso**, y ese buffer
 * empieza de cero en cada arranque: si MIUI cierra la app o ocurre un crash, el log de «antes» ya no
 * existe cuando se exporta. Esto copia en vivo el logcat del proceso (filtrado, ver [LogLineFilter])
 * a un archivo por sesión en `files/logs/`, con un volcado de memoria cada 20 s y los avisos de
 * `onTrimMemory`, y lo escribe línea a línea para que sobreviva a un cierre brusco. Se conservan las
 * últimas sesiones; [LogExporter] las incluye en el reporte.
 */
object PersistentLog {
    private const val KEEP_SESSIONS = 5
    private const val CHUNK_BYTES = 1_500_000L // al pasarse, el trozo se archiva como .1 (solo uno por sesión)
    private const val MEMORY_EVERY_MS = 20_000L

    @Volatile private var writer: BufferedWriter? = null
    private var file: File? = null
    private var written = 0L
    @Volatile private var started = false
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun logsDir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    /** Archivos de log, el más reciente primero (incluye el trozo `.1` de cada sesión). */
    fun files(context: Context): List<File> =
        logsDir(context).listFiles { f -> f.name.startsWith("session-") }.orEmpty()
            .sortedByDescending { it.name }

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        runCatching {
            val dir = logsDir(app)
            // Se borran las sesiones más viejas (cada sesión puede tener `.log` y `.log.1`).
            val sessions = dir.listFiles { f -> f.name.startsWith("session-") }.orEmpty()
                .groupBy { it.name.substringBefore(".log") }.toSortedMap(compareByDescending { it })
            sessions.keys.drop(KEEP_SESSIONS - 1).forEach { k -> sessions[k]?.forEach { it.delete() } }
            val name = "session-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"
            file = File(dir, name)
            open(append = false)
        }
        mark("SESION", header(app))
        mark("SESION", "salida anterior del proceso: " + LogExporter.lastExitSummary(app))
        startLogcatThread()
        startMemoryThread(app)
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = mark("MEMORIA", "onTrimMemory ${trimName(level)}")
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java") override fun onLowMemory() = mark("MEMORIA", "onLowMemory (el sistema pide liberar memoria)")
        })
    }

    private fun header(c: Context): String {
        val pkg = runCatching { c.packageManager.getPackageInfo(c.packageName, 0) }.getOrNull()
        return "v${pkg?.versionName} (${pkg?.longVersionCode}) · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · pid=${Process.myPid()}"
    }

    @Synchronized
    private fun open(append: Boolean) {
        writer?.runCatching { close() }
        writer = file?.let { BufferedWriter(FileWriter(it, append)) }
        written = if (append) file?.length() ?: 0L else 0L
    }

    @Synchronized
    private fun write(text: String) {
        val w = writer ?: return
        runCatching {
            if (written > CHUNK_BYTES) {
                // Archiva el trozo actual y sigue en uno nuevo: así el archivo nunca crece sin límite.
                val f = file!!
                w.close()
                File(f.path + ".1").delete()
                f.renameTo(File(f.path + ".1"))
                writer = BufferedWriter(FileWriter(f, false))
                written = 0L
            }
            val out = writer!!
            out.write(text); out.newLine(); out.flush()
            written += text.length + 1
        }
    }

    /** Anota un hito con hora (carga de modelo, inicio/fin de generación, memoria…). También va a logcat. */
    fun mark(tag: String, message: String) {
        android.util.Log.i("BoxLog", "[$tag] $message")
        write("${synchronized(stamp) { stamp.format(Date()) }} ■ [$tag] $message")
    }

    private fun startLogcatThread() {
        Thread({
            runCatching {
                val p = ProcessBuilder("logcat", "-v", "threadtime", "--pid=${Process.myPid()}")
                    .redirectErrorStream(true).start()
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { if (LogLineFilter.keep(it)) write(it) }
                }
            }.onFailure { mark("LOGCAT", "no se pudo leer logcat: ${it.message}") }
        }, "BoxLogcat").apply { isDaemon = true; start() }
    }

    private fun startMemoryThread(context: Context) {
        Thread({
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            while (true) {
                runCatching {
                    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                    val mb = { b: Long -> b shr 20 }
                    mark(
                        "MEM",
                        "libre del sistema ${mb(mi.availMem)}/${mb(mi.totalMem)} MB (umbral ${mb(mi.threshold)}" +
                            "${if (mi.lowMemory) ", POCA MEMORIA" else ""}) · nativo de la app ${mb(Debug.getNativeHeapAllocatedSize())} MB · " +
                            "java ${mb(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())} MB",
                    )
                }
                Thread.sleep(MEMORY_EVERY_MS)
            }
        }, "BoxMemLog").apply { isDaemon = true; start() }
    }

    private fun trimName(level: Int) = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN (la app pasó a segundo plano)"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE (el sistema está a punto de cerrar la app)"
        else -> "nivel $level"
    }
}
