package com.google.ai.edge.gallery.data.characters

import java.text.Normalizer
import java.util.Locale

/**
 * Clave normalizada para detectar recuerdos repetidos antes de guardarlos (dedupe on write).
 * Ignora mayúsculas, tildes, puntuación y espacios, así que «Ana odia el café.» y
 * «ana odia el cafe» cuentan como el mismo hecho.
 */
object MemoryDedupe {
    private val MARCA = Regex("\\p{M}")
    private val NO_LETRA = Regex("[^\\p{L}\\p{N}\\s]")
    private val ESPACIOS = Regex("\\s+")

    fun key(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARCA, "")
            .replace(NO_LETRA, " ")
            .replace(ESPACIOS, " ")
            .trim()
}
