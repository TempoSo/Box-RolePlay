package com.google.ai.edge.gallery.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * BUG-006: chips con las etiquetas ya usadas en la biblioteca. Se mantienen juntos la lógica pura
 * (fácil de testear) y el componente compartido por los editores de personaje y de escena.
 */
object TagChips {
    /** Etiquetas usadas, sin duplicados (ignorando mayúsculas) y en orden alfabético. */
    fun collect(vararg groups: Collection<String>): List<String> =
        groups.asSequence()
            .flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
            .toList()

    /** Añade o quita una etiqueta de un texto separado por comas, sin duplicarla. */
    fun toggle(text: String, tag: String): String {
        val clean = tag.trim()
        if (clean.isEmpty()) return text
        val parts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val exists = parts.any { it.equals(clean, ignoreCase = true) }
        val next = if (exists) parts.filterNot { it.equals(clean, ignoreCase = true) } else parts + clean
        return next.joinToString(", ")
    }
}

/** Fila de chips: al tocar uno se añade/quita del campo de texto libre. */
@Composable
fun TagChips(
    suggestions: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val selected = value.split(",").map { it.trim().lowercase() }.toSet()
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        suggestions.forEach { tag ->
            FilterChip(
                selected = tag.lowercase() in selected,
                onClick = { onValueChange(TagChips.toggle(value, tag)) },
                label = { Text("#$tag", style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}
