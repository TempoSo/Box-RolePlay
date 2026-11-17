package com.google.ai.edge.gallery.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.runtime.LlamaCppModelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Prueba rápida del motor GGUF para la app "Benchmark de modelos".
 *
 * A diferencia del antiguo botón en Rendimiento (que solo decía "no hay modelo cargado"),
 * aquí se muestra el estado real: si no hay GGUF cargado en esta sesión se explica que hay
 * que abrir un chat primero y que los LiteRT se miden con "Benchmark completo".
 */
@Composable
fun QuickBenchDialog(onDismiss: () -> Unit) {
    var loadedName by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Estado del motor en el momento de abrir el diálogo.
    LaunchedEffect(Unit) {
        loadedName = LlamaCppModelHelper.loadedGgufModelName()
        checked = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prueba rápida (GGUF)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!checked) {
                    Text("Comprobando el motor…", style = MaterialTheme.typography.bodySmall)
                } else if (loadedName == null) {
                    Text(
                        "No hay ningún GGUF cargado en esta sesión. Los modelos LiteRT no se miden " +
                            "aquí: para esos usa «Benchmark completo».\n\n" +
                            "Abre un chat con un modelo GGUF, espera a que cargue y vuelve a esta pantalla.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Modelo cargado: $loadedName\n\n" +
                            "Mide tokens/s de prompt (PP) y generación (TG) sin chat. Para comparar " +
                            "ajustes: cambia hilos/batch/flash attention en Configuración → Rendimiento " +
                            "del motor, recarga el modelo y repite.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        enabled = !running,
                        onClick = {
                            running = true
                            result = "Midiendo…"
                            scope.launch(Dispatchers.Default) {
                                val r =
                                    runCatching { LlamaCppModelHelper.benchmarkLoaded() }
                                        .getOrElse { "Error: ${it.message}" }
                                result =
                                    r
                                        ?: "El motor se liberó o está generando. Recarga el modelo " +
                                            "(abre un chat) e inténtalo de nuevo."
                                running = false
                            }
                        },
                    ) { Text(if (running) "Midiendo…" else "Medir velocidad") }
                    if (result.isNotEmpty()) {
                        Text(result, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}
