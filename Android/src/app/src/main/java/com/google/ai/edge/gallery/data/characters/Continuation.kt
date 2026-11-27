package com.google.ai.edge.gallery.data.characters

/**
 * Lógica pura para el botón «Continuar» del chat: recorta el final de una respuesta para que el
 * modelo tenga un trozo que continuar. Se prefiere cortar por una frase completa; si no hay, por
 * la última palabra que quepa en las dos terceras partes del texto.
 */
object Continuation {
  /** Texto recortado, o null si es demasiado corto o no se puede recortar. */
  fun cutForContinuation(text: String): String? {
    val clean = text.trimEnd()
    if (clean.length < 40) return null
    val last = clean.last()
    val searchFrom = if (last in ".!?…\n") clean.length - 2 else clean.length - 1
    val boundary =
      if (searchFrom < 0) -1
      else clean.lastIndexOfAny(charArrayOf('.', '!', '?', '…', '\n'), startIndex = searchFrom)
    val cut =
      if (boundary >= clean.length / 2) {
        boundary + 1
      } else {
        val target = (clean.length * 0.72).toInt().coerceIn(1, clean.length - 1)
        val space = clean.lastIndexOf(" ", startIndex = target)
        if (space > 0) space + 1 else target
      }
    val result = clean.take(cut).trimEnd()
    return result.takeIf { it.isNotBlank() && it.length < clean.length }
  }
}
