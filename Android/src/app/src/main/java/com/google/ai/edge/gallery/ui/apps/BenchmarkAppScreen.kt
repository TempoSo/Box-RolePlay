package com.google.ai.edge.gallery.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.home.BoxActionCard
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.roleplay.EnginePerfDialog

/** App de pruebas de modelos: benchmark completo de Box y prueba rápida del motor GGUF. */
@Composable
fun BenchmarkAppScreen(onBack: () -> Unit, onOpenModelBenchmark: () -> Unit) {
    var showPerf by remember { mutableStateOf(false) }
    if (showPerf) EnginePerfDialog(onDismiss = { showPerf = false })
    BoxScaffold(title = "Benchmark de modelos", subtitle = "Mide la velocidad de tus modelos", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            BoxActionCard(
                icon = Icons.Rounded.Speed,
                title = "Benchmark completo",
                subtitle = "Elige un modelo (GGUF o LiteRT) y mide prefill, decodificación y tiempo al primer token",
                onClick = onOpenModelBenchmark,
            )
            BoxActionCard(
                icon = Icons.Rounded.Timer,
                title = "Prueba rápida del motor GGUF",
                subtitle = "Con un GGUF cargado: tokens/s de lectura y generación, y ajustes de hilos y batch",
                onClick = { showPerf = true },
            )
        }
    }
}
