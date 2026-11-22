package com.google.ai.edge.gallery.data.characters

/**
 * BUG-042: decide qué líneas de logcat merece la pena guardar en el log persistente. El logcat de un
 * Xiaomi está lleno de ruido del sistema (MIUI, HWUI, Skia…) que llenaría el archivo en minutos y
 * taparía lo útil. Lógica pura, con pruebas.
 *
 * Formato `threadtime`: `MM-DD HH:MM:SS.mmm PID TID L TAG: mensaje`.
 */
object LogLineFilter {
    /** Etiquetas de ruido: no se guardan ni siquiera sus avisos. */
    private val NOISE = Regex(
        "^(Miui.*|HWUI|MiGL|libMEOW|libMiGL|SKIA|QT|libEGL|ResMonitorStub|GraphicsEnvironment|ActivityThread|" +
            "nativeloader|Choreographer|ViewRootImpl.*|InsetsController|ImeTracker|InputMethod.*|BLASTBufferQueue.*|" +
            "WM-.*|PowerHalWrapper|ForceDarkHelper.*|TurboSched.*|NativeTurboSchedManager|UnionPowerNotifier|" +
            "FramePredict|DisplayManager|ashmem|AssetManager2?|AppScout.*|RENDER_TURBO|M-ProMotion|FirebaseApp|" +
            "FirebaseInitProvider|ApplicationLoaders|BoxLog|com\\.box\\.gallery)$"
    )

    /** Fragmentos de JSON/listas que la app vuelca al arrancar (lista de modelos): no aportan. */
    private val JSON_LINE = Regex("^\\s*[\\[\\]{}\"].*|^\\s*$")

    /** `E`/`F`/`W` se guardan siempre (salvo ruido); `I`/`D`/`V` solo si no son ruido ni volcados de JSON. */
    fun keep(line: String): Boolean {
        val parsed = parse(line) ?: return false
        val (level, tag, msg) = parsed
        if (NOISE.matches(tag)) return false
        if (level == 'F' || level == 'E' || level == 'W') return true
        if (tag == "AGModelManagerViewModel" && JSON_LINE.matches(msg)) return false
        return true
    }

    class Parsed(val level: Char, val tag: String, val message: String) {
        operator fun component1() = level
        operator fun component2() = tag
        operator fun component3() = message
    }

    /** Devuelve (nivel, etiqueta, mensaje) o null si no es una línea `threadtime` (p. ej. «--- beginning of main»). */
    fun parse(line: String): Parsed? {
        // 11-22 13:07:34.072 27964 27964 V GraphicsEnvironment: texto
        val m = LINE.matchEntire(line) ?: return null
        return Parsed(m.groupValues[1][0], m.groupValues[2].trim(), m.groupValues[3])
    }

    private val LINE = Regex("^\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+(.*?)\\s*:\\s?(.*)$")
}
