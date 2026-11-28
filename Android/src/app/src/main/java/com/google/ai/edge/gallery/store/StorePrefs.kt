package com.google.ai.edge.gallery.store

import android.content.Context

/** Ajustes de la tienda: contenido para adultos (apagado por defecto) y tarjetas ya añadidas. */
class StorePrefs(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("character_store", Context.MODE_PRIVATE)

  var nsfw: Boolean
    get() = prefs.getBoolean("nsfw", false)
    set(v) = prefs.edit().putBoolean("nsfw", v).apply()

  fun added(): Set<String> = prefs.getStringSet("added", emptySet()) ?: emptySet()

  fun markAdded(id: String) = prefs.edit().putStringSet("added", added() + id).apply()

  /** Última búsqueda: texto, orden (nombre del enum) y página, para volver justo donde se dejó. */
  var lastText: String
    get() = prefs.getString("last_text", "") ?: ""
    set(v) = prefs.edit().putString("last_text", v).apply()

  var lastSort: String
    get() = prefs.getString("last_sort", "") ?: ""
    set(v) = prefs.edit().putString("last_sort", v).apply()

  var lastPage: Int
    get() = prefs.getInt("last_page", 1).coerceAtLeast(1)
    set(v) = prefs.edit().putInt("last_page", v).apply()
}
