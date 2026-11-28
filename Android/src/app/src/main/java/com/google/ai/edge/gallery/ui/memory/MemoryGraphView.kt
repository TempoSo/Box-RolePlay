package com.google.ai.edge.gallery.ui.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.MemoryGraphBuilder.Grafo
import com.google.ai.edge.gallery.data.local.entities.MemoryEdge
import com.google.ai.edge.gallery.data.local.entities.MemoryNode

/** Filtros del grafo, equivalentes a los del visor de Layla. */
enum class FiltroGrafo(val etiqueta: String) {
    TODO("Todo"),
    T0("T0"),
    T1("T1"),
    TEMPORAL("Temporal"),
}

/** Selección de nodos/aristas por capa. Es pura para poder probarla sin Compose. */
object MemoryGraphFilter {
    fun nodos(grafo: Grafo, filtro: FiltroGrafo): List<MemoryNode> =
        when (filtro) {
            FiltroGrafo.TODO -> grafo.nodes
            FiltroGrafo.T0 -> grafo.nodes.filter { it.type == "person" || it.type == "memory" }
            FiltroGrafo.T1 ->
                grafo.nodes.filter { it.type in setOf("concept", "emotion", "memory") }
            FiltroGrafo.TEMPORAL -> grafo.nodes.filter { it.type == "memory" }
        }

    fun aristas(grafo: Grafo, filtro: FiltroGrafo, visibles: List<MemoryNode>): List<MemoryEdge> {
        val ids = visibles.map { it.id }.toSet()
        return grafo.edges.filter { arista ->
            arista.sourceId in ids && arista.targetId in ids &&
                (filtro != FiltroGrafo.TEMPORAL || arista.linkType == "temporal")
        }
    }
}

private data class Posicion(val x: androidx.compose.ui.unit.Dp, val y: androidx.compose.ui.unit.Dp)

/**
 * Vista 2D tipo árbol del grafo de memoria. Sin dependencias nuevas: las aristas se dibujan en un
 * Canvas y los nodos son cajas encima, con scroll en la pantalla que lo contiene.
 */
@Composable
fun MemoryGraphView(grafo: Grafo, filtro: FiltroGrafo) {
    val nodos = MemoryGraphFilter.nodos(grafo, filtro)
    val aristas = MemoryGraphFilter.aristas(grafo, filtro, nodos)
    val porNivel =
        listOf("person", "concept", "emotion", "memory", "raw")
            .mapIndexed { nivel, tipo -> tipo to nivel }
            .toMap()
    val posiciones = LinkedHashMap<String, Posicion>()
    nodos.sortedWith(compareBy({ porNivel[it.type] ?: 99 }, { it.label })).forEach { nodo ->
        val nivel = porNivel[nodo.type] ?: 3
        val indice = posiciones.keys.count { id -> porNivel[nodos.first { it.id == id }.type] == nivel }
        posiciones[nodo.id] = Posicion((24 + nivel * 230).dp, (24 + indice * 74).dp)
    }
    val maxNivel = nodos.maxOfOrNull { porNivel[it.type] ?: 3 } ?: 0
    val maxFila = posiciones.values.maxOfOrNull { (it.y.value - 24) / 74 }?.toInt() ?: 0
    val ancho = (24 + maxNivel * 230 + 230).dp
    val alto = (48 + maxFila * 74).dp.coerceAtLeast(300.dp)

    val colorTemporal = Color(0xFF8E7CC3)
    val colorRaw = Color(0xFF9E9E9E)
    val colorSemantica = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.width(ancho).height(alto)) {
        Canvas(Modifier.width(ancho).height(alto)) {
            aristas.forEach { arista ->
                val a = posiciones[arista.sourceId] ?: return@forEach
                val b = posiciones[arista.targetId] ?: return@forEach
                val color =
                    when (arista.linkType) {
                        "temporal" -> colorTemporal
                        "raw" -> colorRaw
                        else -> colorSemantica
                    }
                drawLine(
                    color = color,
                    start =
                        Offset(
                            (a.x.value + 105).dp.toPx(),
                            (a.y.value + 28).dp.toPx(),
                        ),
                    end =
                        Offset(
                            (b.x.value + 105).dp.toPx(),
                            (b.y.value + 28).dp.toPx(),
                        ),
                    strokeWidth = 2f,
                )
            }
        }
        nodos.forEach { nodo ->
            val p = posiciones.getValue(nodo.id)
            val color =
                when (nodo.type) {
                    "person" -> MaterialTheme.colorScheme.tertiaryContainer
                    "memory" -> MaterialTheme.colorScheme.primaryContainer
                    "raw" -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            Column(
                Modifier
                    .offset(x = p.x, y = p.y)
                    .width(210.dp)
                    .background(color, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    nodo.label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nodo.topic.ifBlank { nodo.type },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
