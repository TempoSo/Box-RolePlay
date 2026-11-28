package com.google.ai.edge.gallery.data.characters

/** Nombre de archivo seguro para compartir una ficha: quita caracteres prohibidos y recorta. */
object ShareFileName {
    fun forCharacter(name: String, extension: String = "json"): String {
        val base = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ', '_')
            .take(60)
            .trim()
            .ifBlank { "personaje" }
        return "$base.$extension"
    }
}
