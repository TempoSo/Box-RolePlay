package com.google.ai.edge.gallery.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Miniaturas remotas con caché en memoria (sin librerías de imágenes). Bloqueante: llamar desde IO. */
object StoreImages {
  private val cache = object : LruCache<String, Bitmap>(48) {}

  fun cached(url: String): Bitmap? = cache.get(url)

  /** Descarga [url] y la reduce a ~[target] px de lado; null si falla. */
  fun load(url: String, target: Int = 160): Bitmap? {
    cache.get(url)?.let { return it }
    return runCatching {
      val bytes = StoreHttp.get(url)
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sample = 1
      while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
      val opts = BitmapFactory.Options().apply { inSampleSize = sample }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()?.also { cache.put(url, it) }
  }
}
