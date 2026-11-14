package com.google.ai.edge.gallery.ui.roleplay

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.SpeechScope
import com.google.ai.edge.gallery.data.characters.TtsSettingsStore
import com.google.ai.edge.gallery.tts.PocketModelFiles
import com.google.ai.edge.gallery.tts.SherpaPocketEngine
import com.google.ai.edge.gallery.tts.TtsFiles
import com.google.ai.edge.gallery.tts.TtsManager
import java.io.File

/** Voz (Pocket TTS): modelo, voz predeterminada, qué se lee, velocidad y lectura automática. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TtsSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TtsSettingsStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var autoRead by remember { mutableStateOf(store.autoRead()) }
    var scope by remember { mutableStateOf(store.scope()) }
    var speed by remember { mutableStateOf(store.speed().toString()) }
    var steps by remember { mutableStateOf(store.numSteps().toString()) }

    val pickModel =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val copied = runCatching { TtsFiles.importModelFolder(context, uri) }.getOrElse { emptyList() }
                Toast.makeText(
                    context,
                    if (copied.isEmpty()) "No se encontraron archivos de Pocket TTS en esa carpeta."
                    else "Copiados ${copied.size} archivos.",
                    Toast.LENGTH_LONG,
                ).show()
                TtsManager.release() // recarga el motor con los archivos nuevos
                refresh++
            }
        }
    val pickVoice =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val path = runCatching { TtsFiles.importVoice(context, uri, "default") }.getOrNull()
                if (path == null) {
                    Toast.makeText(context, "Ese archivo no es un WAV legible (PCM de 16/24/32 bits).", Toast.LENGTH_LONG).show()
                } else {
                    store.setDefaultVoicePath(path)
                }
                refresh++
            }
        }

    val missing = remember(refresh) { PocketModelFiles.missing(TtsFiles.modelDir(context)) }
    val libOk = remember { SherpaPocketEngine.isLibraryAvailable() }
    val voice = remember(refresh) { store.defaultVoicePath()?.takeIf { File(it).exists() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voz (Pocket TTS)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (libOk) "Librería sherpa-onnx: lista."
                    else "Librería sherpa-onnx: NO está en la app. Copia el .aar de sherpa-onnx a " +
                        "Android/src/app/libs/sherpa-onnx.aar y vuelve a compilar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (libOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )

                Text("Modelo", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (missing.isEmpty()) "Modelo completo."
                    else "Faltan: ${missing.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missing.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { pickModel.launch(null) }) { Text("Importar carpeta del modelo") }
                Text(
                    "Elige la carpeta con lm_flow, lm_main, encoder, decoder, text_conditioner, vocab.json y token_scores.json " +
                        "(por ejemplo la de pockettts de Layla).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Voz predeterminada", style = MaterialTheme.typography.titleSmall)
                Text(
                    voice?.let { "Muestra cargada: ${File(it).name}" } ?: "Sin muestra. Sube un .wav de unos 5-12 s de una sola voz.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { pickVoice.launch(arrayOf("audio/*")) }) { Text("Elegir muestra de voz (.wav)") }

                Text("Qué se lee", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SpeechScope.ALL to "Todo",
                        SpeechScope.DIALOGUE_ONLY to "Solo diálogo",
                        SpeechScope.NARRATION_AND_DIALOGUE to "Narración y diálogo",
                    ).forEach { (sc, label) ->
                        FilterChip(
                            selected = scope == sc,
                            onClick = { scope = sc; store.setScope(sc) },
                            label = { Text(label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = speed,
                    onValueChange = { t ->
                        speed = t
                        t.toFloatOrNull()?.let { store.setSpeed(it) }
                    },
                    label = { Text("Velocidad (0.5 - 2.0)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { t ->
                        steps = t
                        t.toIntOrNull()?.let { store.setNumSteps(it) }
                    },
                    label = { Text("Pasos (1-10: menos = más rápido, más = mejor calidad)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoRead, onCheckedChange = { autoRead = it; store.setAutoRead(it) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Leer cada respuesta automáticamente", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "También puedes pulsar el altavoz de cada mensaje.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        TtsManager.currentCharacterId = ""
                        TtsManager.speak(context, "Hola, esta es mi voz. ¿Qué te parece cómo suena?")
                    },
                    enabled = libOk && missing.isEmpty() && voice != null,
                ) { Text("Probar voz") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

/** Fila del editor de personaje: muestra de voz propia (si no, se usa la predeterminada). */
@Composable
fun CharacterVoiceRow(characterId: String) {
    val context = LocalContext.current
    val store = remember { TtsSettingsStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val current = remember(refresh) { store.characterVoicePath(characterId)?.takeIf { File(it).exists() } }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val path = runCatching { TtsFiles.importVoice(context, uri, "char_$characterId") }.getOrNull()
                if (path == null) {
                    Toast.makeText(context, "Ese archivo no es un WAV legible (PCM de 16/24/32 bits).", Toast.LENGTH_LONG).show()
                } else {
                    store.setCharacterVoicePath(characterId, path)
                }
                refresh++
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Voz de este personaje", style = MaterialTheme.typography.titleSmall)
        if (characterId == "new") {
            Text(
                "Guarda el personaje primero y vuelve a abrirlo para asignarle una voz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            current?.let { "Muestra propia: ${File(it).name}" } ?: "Usa la voz predeterminada de Roleplay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Text("Elegir .wav") }
            if (current != null) {
                TextButton(onClick = { store.setCharacterVoicePath(characterId, null); refresh++ }) { Text("Quitar") }
            }
        }
    }
}
