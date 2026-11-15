package com.google.ai.edge.gallery.data.characters

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo de Hugging Face (solo repositorios públicos, sin token). Usa java.net para no
 * añadir dependencias. Las descargas se reanudan: el archivo parcial se guarda como "<nombre>.part".
 */
object HfClient {
    private const val UA = "BoxRolePlay/1.0 (Android)"
    private const val MAX_REDIRECTS = 5

    /** GET que sigue redirecciones a mano (el CDN de descargas está en otro dominio). */
    private fun open(url: String, rangeStart: Long = 0, timeoutMs: Int = 20_000): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val u = URL(current)
            require(u.protocol == "https") { "Solo se permiten enlaces https" }
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            if (rangeStart > 0) c.setRequestProperty("Range", "bytes=$rangeStart-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                c.disconnect()
                current = URL(u, loc).toString()
            } else return c
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode == 404) throw IOException("No encontrado (¿repositorio privado o nombre incorrecto?)")
            if (c.responseCode !in 200..299) throw IOException("Hugging Face respondió ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun search(query: String): List<HfRepo> = HfApi.parseRepos(getText(HfApi.searchUrl(query)))

    fun files(repoId: String): List<HfFile> = HfApi.parseFiles(getText(HfApi.treeUrl(repoId)))

    /**
     * Descarga [url] en [dest] (reanudando si ya hay un .part). [onProgress] recibe (bytes, total).
     * Lanza IOException si falla, si se cancela o si el tamaño final no coincide con [expectedSize].
     */
    fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var have = if (part.exists()) part.length() else 0L
        if (expectedSize > 0 && have > expectedSize) { part.delete(); have = 0 }
        if (expectedSize > 0 && dest.parentFile!!.usableSpace < expectedSize - have + 200L * 1024 * 1024) {
            throw IOException("No hay espacio suficiente (se necesitan ${(expectedSize - have) / (1024 * 1024)} MB libres)")
        }
        val c = open(url, rangeStart = have, timeoutMs = 30_000)
        try {
            val code = c.responseCode
            if (code != 200 && code != 206) throw IOException("Hugging Face respondió $code")
            // 200 = el servidor ignoró el rango: se empieza de cero.
            if (code == 200 && have > 0) { part.delete(); have = 0 }
            val total = if (expectedSize > 0) expectedSize else (have + c.contentLengthLong).coerceAtLeast(0)
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(have)
                val buf = ByteArray(256 * 1024)
                var done = have
                var lastReport = 0L
                c.inputStream.use { input ->
                    while (true) {
                        if (isCancelled()) throw IOException("Descarga cancelada")
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250) { lastReport = now; onProgress(done, total) }
                    }
                }
                onProgress(done, total)
            }
        } finally {
            c.disconnect()
        }
        if (expectedSize > 0 && part.length() != expectedSize) {
            throw IOException("Descarga incompleta (${part.length()} de $expectedSize bytes). Vuelve a intentarlo para reanudar.")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("No se pudo guardar el archivo")
    }
}
