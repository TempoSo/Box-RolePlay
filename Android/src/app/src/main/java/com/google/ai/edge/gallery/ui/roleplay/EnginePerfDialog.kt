package com.google.ai.edge.gallery.ui.roleplay

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.EnginePerfSettings
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.LastEnginePerf
import com.google.ai.edge.gallery.data.characters.MmprojFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rendimiento del motor GGUF (llama.cpp): hilos, lotes, mmap, repack, Flash Attention, caché KV,
 * contexto y longitud máxima de respuesta. Cada cambio se guarda al momento y se aplica al volver
 * a cargar el modelo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnginePerfDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EnginePerfStore(context) }
    var s by remember { mutableStateOf(store.load()) }
    fun update(n: EnginePerfSettings) {
        s = n
        store.save(n)
    }

    val scope = rememberCoroutineScope()
    var mmprojRefresh by remember { mutableIntStateOf(0) }
    var copying by remember { mutableStateOf(false) }
    val mmprojFiles = remember(mmprojRefresh) { MmprojFiles.list(context) }
    val pickMmproj =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                copying = true
                scope.launch {
                    val name = withContext(Dispatchers.IO) { runCatching { MmprojFiles.import(context, uri) }.getOrNull() }
                    copying = false
                    if (name == null) {
                        Toast.makeText(context, "Elige un archivo mmproj en formato .gguf.", Toast.LENGTH_LONG).show()
                    } else {
                        update(s.copy(mmproj = name))
                        mmprojRefresh++
                    }
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rendimiento (GGUF)") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Se aplican al volver a cargar el modelo. Solo afectan a modelos GGUF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section("CPU")
                NumberField("Hilos (0 = automático)", s.threads) { update(s.copy(threads = it)) }
                NumberField("Tamaño de lote (n_batch)", s.nBatch) { update(s.copy(nBatch = it)) }
                NumberField("Lote físico (n_ubatch)", s.nUBatch) { update(s.copy(nUBatch = it)) }
                SwitchRow(
                    "Sin mmap",
                    "Carga el modelo entero en RAM en vez de mapearlo desde el archivo.",
                    s.noMmap,
                ) { update(s.copy(noMmap = it)) }
                SwitchRow(
                    "Repack de CPU",
                    "Reordena los pesos al cargar para ir más rápido por token; tarda más en cargar.",
                    s.repack,
                ) { update(s.copy(repack = it)) }

                Text("Flash Attention", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnginePerfSettings.FLASH_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = s.flashAttn == i,
                            onClick = { update(s.copy(flashAttn = i)) },
                            label = { Text(label) },
                        )
                    }
                }

                Section("Caché KV")
                Text(
                    "Cuantizar la caché ahorra RAM (q8_0 usa la mitad) y exige Flash Attention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnginePerfSettings.KV_TYPES.forEachIndexed { i, label ->
                        FilterChip(
                            selected = s.kvType == i,
                            onClick = { update(s.copy(kvType = i)) },
                            label = { Text(label) },
                        )
                    }
                }

                Section("Contexto y respuesta")
                NumberField("Longitud del contexto (0 = la del modelo)", s.contextLength) {
                    update(s.copy(contextLength = it))
                }
                if (s.contextLength > 8192) {
                    Text(
                        "Un contexto grande gasta mucha RAM y puede cerrar la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                NumberField("Longitud máx. de respuesta (-1 = sin límite)", s.maxResponse, allowNegative = true) {
                    update(s.copy(maxResponse = it))
                }

                Section("Visión (mmproj)")
                Text(
                    "Para enviar imágenes a un modelo GGUF hace falta su archivo mmproj (el que coincide con ese " +
                        "modelo). Marca «soporta imágenes» al importar el modelo y úsalo en «AI Chat con imágenes».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().clickable { update(s.copy(mmproj = "")) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.mmproj.isEmpty(), onClick = { update(s.copy(mmproj = "")) })
                    Text("Ninguno (solo texto)", style = MaterialTheme.typography.bodyMedium)
                }
                mmprojFiles.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { update(s.copy(mmproj = name)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.mmproj == name, onClick = { update(s.copy(mmproj = name)) })
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(onClick = { pickMmproj.launch(arrayOf("*/*")) }, enabled = !copying) {
                    Text(if (copying) "Copiando…" else "Importar mmproj (.gguf)")
                }
                NumberField("Reducir imagen enviada (px, lado máximo)", s.imageMaxSide) {
                    update(s.copy(imageMaxSide = it))
                }

                Section("Último turno")
                val st = LastEnginePerf.stats
                Text(
                    if (st == null) "Aún no hay datos: envía un mensaje con un modelo GGUF."
                    else
                        "Carga del modelo: ${"%.1f".format(LastEnginePerf.loadSeconds)} s\n" +
                            "Prompt: ${st.promptTokens} tokens, ${"%.1f".format(st.promptTokensPerSecond)} t/s\n" +
                            "Generación: ${st.generatedTokens} tokens, " +
                            "${"%.2f".format(st.generationTokensPerSecond)} t/s",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Section("Prueba de velocidad")
                val benchScope = androidx.compose.runtime.rememberCoroutineScope()
                var benchRunning by remember { mutableStateOf(false) }
                var benchResult by remember { mutableStateOf("") }
                Text(
                    "Mide la velocidad pura del modelo cargado (sin chat). Sirve para comparar hilos, batch o " +
                        "flash attention: cambia un ajuste, vuelve a cargar el modelo y repite. Tarda unos segundos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    enabled = !benchRunning,
                    onClick = {
                        benchRunning = true
                        benchResult = "Midiendo…"
                        benchScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            val r =
                                runCatching {
                                        com.google.ai.edge.gallery.runtime.LlamaCppModelHelper.benchmarkLoaded()
                                    }
                                    .getOrElse { "Error: ${it.message}" }
                            benchResult = r ?: "No hay un modelo GGUF cargado (o está generando). Abre un chat y espera a que cargue."
                            benchRunning = false
                        }
                    },
                ) { Text(if (benchRunning) "Midiendo…" else "Probar velocidad del modelo cargado") }
                if (benchResult.isNotEmpty()) {
                    Text(benchResult, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = { update(EnginePerfSettings()) }) { Text("Restablecer") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    allowNegative: Boolean = false,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val clean = t.filterIndexed { i, c -> c.isDigit() || (allowNegative && i == 0 && c == '-') }
            text = clean
            clean.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
