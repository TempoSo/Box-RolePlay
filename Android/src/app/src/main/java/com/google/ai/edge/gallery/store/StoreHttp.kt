package com.google.ai.edge.gallery.store

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** HTTP mínimo para las tiendas de personajes (HttpURLConnection, sin dependencias nuevas). */
object StoreHttp {
  const val MAX_BYTES = 25 * 1024 * 1024
  private const val UA = "BoxRolePlay/1.0 (Android)"

  class HttpError(val code: Int, message: String) : Exception(message)

  fun get(url: String, accept: String = "*/*"): ByteArray = request(url, "GET", null, accept)

  fun postJson(url: String, body: String): ByteArray = request(url, "POST", body, "application/json")

  private fun request(url: String, method: String, body: String?, accept: String): ByteArray {
    var current = url
    // Redirecciones a mano (hasta 5) para poder pasar de http a https y entre dominios (avatars.charhub.io…).
    repeat(6) {
      val conn = (URL(current).openConnection() as HttpURLConnection)
      try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", accept)
        if (body != null) {
          conn.doOutput = true
          conn.setRequestProperty("Content-Type", "application/json")
          conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        if (code in 300..399) {
          val loc = conn.getHeaderField("Location") ?: throw HttpError(code, "Redirección sin destino")
          current = URL(URL(current), loc).toString()
          return@repeat
        }
        if (code !in 200..299) throw HttpError(code, "El servidor respondió $code")
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
          val buf = ByteArray(16 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw HttpError(-1, "El archivo es demasiado grande")
          }
        }
        return out.toByteArray()
      } finally {
        conn.disconnect()
      }
    }
    throw HttpError(-1, "Demasiadas redirecciones")
  }
}
