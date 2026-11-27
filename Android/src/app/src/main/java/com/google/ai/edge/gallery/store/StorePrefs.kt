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
}
