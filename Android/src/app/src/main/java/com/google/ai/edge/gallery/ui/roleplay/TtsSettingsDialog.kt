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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.SpeechScope
import com.google.ai.edge.gallery.data.characters.TtsSettingsStore
import com.google.ai.edge.gallery.tts.KokoroModelFiles
import com.google.ai.edge.gallery.tts.KokoroVoice
import com.google.ai.edge.gallery.tts.KokoroVoices
import com.google.ai.edge.gallery.tts.PocketModelFiles
import com.google.ai.edge.gallery.tts.TtsModelType
import com.google.ai.edge.gallery.tts.TtsVoiceRef
import com.google.ai.edge.gallery.tts.SherpaPocketEngine
import com.google.ai.edge.gallery.tts.SupertonicModelFiles
import com.google.ai.edge.gallery.tts.SupertonicVoices
import com.google.ai.edge.gallery.tts.TtsFiles
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.tts.TtsModels
import com.google.ai.edge.gallery.tts.VoiceStore
import java.io.File

/** Ajustes de voz en un diálogo (se abre desde Roleplay y desde Ajustes de Box). */
@Composable
fun TtsSettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voz") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { TtsSettingsPanel() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

/**
 * Voz: modelos instalados (Pocket TTS, Kokoro y Supertonic), voz, qué se lee, velocidad y lectura automática.
 * Es una sola interfaz: la usa el diálogo de ajustes y la app «Texto a voz» ([textArea] pone ahí el texto).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TtsSettingsPanel(textArea: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { TtsSettingsStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var autoRead by remember { mutableStateOf(store.autoRead()) }
    var live by remember { mutableStateOf(store.streamWhileGenerating()) }
    var scope by remember { mutableStateOf(store.scope()) }
    var speed by remember { mutableStateOf(store.speed().toString()) }
    var steps by remember { mutableStateOf(store.numSteps().toString()) }

    val pickModel =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                // Cada importación es un modelo nuevo (no pisa los que ya hay) y pasa a ser el activo.
                val model = TtsModels.create(context, TtsFiles.folderName(context, uri) ?: "Importado")
                val copied = runCatching { TtsFiles.importModelFolder(context, uri, model.dir) }.getOrElse { emptyList() }
                if (copied.isEmpty()) TtsModels.delete(context, model.id)
                Toast.makeText(
                    context,
                    // BUG-014: el error lista los archivos esperados (y avisa si la carpeta es del formato v2).
                    if (copied.isEmpty()) PocketModelFiles.noPartsMessage(listFolderNames(context, uri)) +
                        " También vale Supertonic: ${SupertonicModelFiles.EXPECTED_NAMES.joinToString(", ")}."
                    else {
                        val faltan =
                            if (TtsModels.typeOf(model.dir) == TtsModelType.SUPERTONIC)
                                SupertonicModelFiles.missing(model.dir)
                            else PocketModelFiles.missing(model.dir)
                        if (faltan.isNotEmpty()) "Copiados ${copied.size} archivos, pero faltan: ${faltan.joinToString(", ")}"
                        else "Modelo importado (${copied.size} archivos)."
                    },
                    Toast.LENGTH_LONG,
                ).show()
                if (copied.isNotEmpty()) TtsModels.setActive(context, model.id)
                TtsManager.release() // recarga el motor con los archivos nuevos
                refresh++
            }
        }
    var showVoices by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    if (showDownload) TtsModelDownloadDialog(onDismiss = { showDownload = false }, onDone = { refresh++ })

    if (showVoices) {
        VoiceLibraryDialog(
            onDismiss = { showVoices = false; refresh++ },
            currentPath = store.defaultVoicePath(),
            onPick = { _, path -> store.setDefaultVoicePath(path); refresh++ },
        )
    }

    val models = remember(refresh) { TtsModels.list(context) }
    val activeId = remember(refresh) { TtsModels.activeId(context) }
    var toDelete by remember { mutableStateOf<com.google.ai.edge.gallery.tts.TtsModelInfo?>(null) }
    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Borrar modelo") },
            text = { Text("¿Borrar «${m.label}» (${m.sizeMb} MB)? Se puede volver a descargar.") },
            confirmButton = { TextButton(onClick = { TtsModels.delete(context, m.id); toDelete = null; refresh++ }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
    val libOk = remember { SherpaPocketEngine.isLibraryAvailable() }
    val active = models.firstOrNull { it.id == activeId }
    val isKokoro = active?.type == TtsModelType.KOKORO
    val isSupertonic = active?.type == TtsModelType.SUPERTONIC
    var kokoroSid by remember(refresh) { mutableStateOf(store.kokoroDefaultSid()) }
    var supertonicSid by remember(refresh) { mutableStateOf(store.supertonicDefaultSid()) }
    var supertonicLang by remember(refresh) { mutableStateOf(store.supertonicLang()) }
    var voiceFilter by remember { mutableStateOf("") }
    val voice =
      remember(refresh, showVoices) {
        store.defaultVoicePath()?.takeIf { TtsVoiceRef.typeOf(it) == TtsModelType.POCKET }
      }
    val voiceName = remember(refresh, showVoices) {
        voice?.let { p -> VoiceStore(context).all().firstOrNull { VoiceStore(context).pathOf(it.id) == p }?.name }
    }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (libOk) "Librería sherpa-onnx: lista."
                    else "Librería sherpa-onnx: NO está en la app. Copia el .aar de sherpa-onnx a " +
                        "Android/src/app/libs/sherpa-onnx.aar y vuelve a compilar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (libOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )

                Text("Modelo", style = MaterialTheme.typography.titleSmall)
                if (models.isEmpty()) {
                    Text("No hay ningún modelo instalado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                models.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = m.id == activeId,
                            enabled = m.complete,
                            onClick = { TtsModels.setActive(context, m.id); refresh++ },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (m.complete) "${m.type.label} · ${m.sizeMb} MB" else "${m.type.label} · incompleto, faltan: ${m.missing.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (m.complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = { toDelete = m }) { Text("Borrar") }
                    }
                }
                OutlinedButton(onClick = { showDownload = true }) { Text("Descargar de Hugging Face") }
                OutlinedButton(onClick = { pickModel.launch(null) }) { Text("Importar carpeta de modelo") }
                Text(
                    "Importar vale para Pocket TTS (lm_flow, lm_main, encoder, decoder, text_conditioner, vocab.json y " +
                        "token_scores.json) y Supertonic (cuatro .onnx, tts.json, unicode_indexer.bin y voice.bin). " +
                        "Kokoro se descarga con el botón de arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                textArea?.invoke()

                if (isKokoro && active != null) {
                    val numVoices = KokoroModelFiles.estimatedVoices(active.dir)
                    val all = KokoroVoices.available(numVoices)
                    val shownSid = if (kokoroSid >= 0) kokoroSid else KokoroVoices.defaultSid(numVoices)
                    Text("Voz", style = MaterialTheme.typography.titleSmall)
                    KokoroVoiceList(
                        voices = all,
                        selectedSid = shownSid,
                        filter = voiceFilter,
                        onFilter = { voiceFilter = it },
                        onSelect = { store.setKokoroDefaultSid(it); kokoroSid = it },
                        onPreview = { v ->
                            TtsManager.currentCharacterId = ""
                            TtsManager.speakAs(context, "\"" + KokoroVoices.sample(v.langCode) + "\"", TtsVoiceRef.kokoro(v.sid))
                        },
                    )
                } else if (isSupertonic && active != null) {
                    Text("Voz", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SupertonicVoices.languages.forEach { (code, name) ->
                            FilterChip(
                                selected = supertonicLang == code,
                                onClick = { supertonicLang = code; store.setSupertonicLang(code) },
                                label = { Text(name) },
                            )
                        }
                    }
                    SupertonicVoiceList(
                        count = SupertonicVoices.COUNT,
                        selectedSid = supertonicSid,
                        onSelect = { store.setSupertonicDefaultSid(it); supertonicSid = it },
                        onPreview = { sid ->
                            TtsManager.currentCharacterId = ""
                            val lang = supertonicLang
                            TtsManager.speakAs(context, "\"" + SupertonicVoices.sample(lang) + "\"", TtsVoiceRef.supertonic(sid, lang))
                        },
                    )
                } else {
                    Text("Voz predeterminada", style = MaterialTheme.typography.titleSmall)
                    Text(
                        voice?.let { "Voz: ${voiceName ?: File(it).name}" } ?: "Sin voz. Abre la biblioteca para subir o grabar una.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { showVoices = true }) { Text("Biblioteca de voces") }
                }

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
                if (!isKokoro && !isSupertonic) OutlinedTextField(
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
                    Switch(checked = live, enabled = autoRead, onCheckedChange = { live = it; store.setStreamWhileGenerating(it) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Hablar mientras se escribe", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Empieza con la primera frase completa. La voz y el modelo comparten procesador: la escritura puede ir algo más lenta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

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
                        TtsManager.speak(context, "\"Hola, esta es mi voz. ¿Qué te parece cómo suena?\"")
                    },
                    enabled = libOk && active?.complete == true && (isKokoro || isSupertonic || voice != null),
                ) { Text("Probar voz") }
            }
}

/** Lista de voces de Kokoro con filtro por idioma, vista previa (▶) y selección. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KokoroVoiceList(
    voices: List<KokoroVoice>,
    selectedSid: Int?,
    filter: String,
    onFilter: (String) -> Unit,
    onSelect: (Int) -> Unit,
    onPreview: (KokoroVoice) -> Unit,
) {
    val languages = remember(voices) { voices.map { it.langCode to it.language }.distinct() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter.isEmpty(), onClick = { onFilter("") }, label = { Text("Todas (${voices.size})") })
        languages.forEach { (code, name) ->
            FilterChip(selected = filter == code, onClick = { onFilter(code) }, label = { Text(name) })
        }
    }
    voices.filter { filter.isEmpty() || it.langCode == filter }.forEach { v ->
        Row(
            Modifier.fillMaxWidth().clickable { onSelect(v.sid) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.RadioButton(selected = v.sid == selectedSid, onClick = { onSelect(v.sid) })
            Column(Modifier.weight(1f)) {
                Text(v.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(v.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onPreview(v) }) { Icon(Icons.Rounded.PlayArrow, "Escuchar ${v.displayName}") }
        }
    }
}

/** Lista sencilla de voces de Supertonic (sid + escucha rápida); el idioma se elige aparte. */
@Composable
fun SupertonicVoiceList(
  count: Int,
  selectedSid: Int,
  onSelect: (Int) -> Unit,
  onPreview: (Int) -> Unit,
) {
  (0 until count).forEach { sid ->
    Row(Modifier.fillMaxWidth().clickable { onSelect(sid) }, verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected = sid == selectedSid, onClick = { onSelect(sid) })
      Text("Voz $sid", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      IconButton(onClick = { onPreview(sid) }) { Icon(Icons.Rounded.PlayArrow, "Escuchar voz $sid") }
    }
  }
}

/** Fila del editor de personaje: voz propia (Pocket, Kokoro o Supertonic); si no, se usa la predeterminada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterVoiceRow(characterId: String) {
    val context = LocalContext.current
    val store = remember { TtsSettingsStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val current = remember(refresh) { store.characterVoicePath(characterId)?.takeIf { TtsVoiceRef.usable(it) } }
    val activeModel = remember(refresh) { TtsModels.active(context) }
    var supLang by remember { mutableStateOf(store.supertonicLang()) }
    var showVoices by remember { mutableStateOf(false) }
    if (showVoices) {
        if (activeModel?.type == TtsModelType.KOKORO) {
            var filter by remember { mutableStateOf("") }
            val voices = KokoroVoices.available(KokoroModelFiles.estimatedVoices(activeModel.dir))
            AlertDialog(
                onDismissRequest = { showVoices = false; refresh++ },
                title = { Text("Voz del personaje") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KokoroVoiceList(
                            voices = voices,
                            selectedSid = TtsVoiceRef.kokoroSid(current),
                            filter = filter,
                            onFilter = { filter = it },
                            onSelect = { store.setCharacterVoicePath(characterId, TtsVoiceRef.kokoro(it)); refresh++ },
                            onPreview = { v ->
                                TtsManager.currentCharacterId = ""
                                TtsManager.speakAs(context, "\"" + KokoroVoices.sample(v.langCode) + "\"", TtsVoiceRef.kokoro(v.sid))
                            },
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showVoices = false; refresh++ }) { Text("Listo") } },
            )
        } else if (activeModel?.type == TtsModelType.SUPERTONIC) {
            AlertDialog(
                onDismissRequest = { showVoices = false; refresh++ },
                title = { Text("Voz del personaje") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SupertonicVoices.languages.forEach { (code, name) ->
                                FilterChip(
                                    selected = supLang == code,
                                    onClick = { supLang = code; store.setSupertonicLang(code) },
                                    label = { Text(name) },
                                )
                            }
                        }
                        SupertonicVoiceList(
                            count = SupertonicVoices.COUNT,
                            selectedSid = TtsVoiceRef.supertonicSid(current) ?: -1,
                            onSelect = { store.setCharacterVoicePath(characterId, TtsVoiceRef.supertonic(it, supLang)); refresh++ },
                            onPreview = { sid ->
                                TtsManager.currentCharacterId = ""
                                TtsManager.speakAs(
                                    context,
                                    "\"" + SupertonicVoices.sample(supLang) + "\"",
                                    TtsVoiceRef.supertonic(sid, supLang),
                                )
                            },
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showVoices = false; refresh++ }) { Text("Listo") } },
            )
        } else {
            VoiceLibraryDialog(
                onDismiss = { showVoices = false; refresh++ },
                currentPath = current?.takeIf { TtsVoiceRef.typeOf(it) == TtsModelType.POCKET },
                onPick = { _, path -> store.setCharacterVoicePath(characterId, path); refresh++ },
            )
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
        val kokoroSid = TtsVoiceRef.kokoroSid(current)
        val supertonicSid = TtsVoiceRef.supertonicSid(current)
        Text(
            when {
                supertonicSid != null -> "Voz propia: Supertonic #$supertonicSid" +
                    (TtsVoiceRef.supertonicLang(current)?.let { " · $it" } ?: "")
                kokoroSid != null -> "Voz propia: " + (KokoroVoices.byIndex(kokoroSid)?.let { "${it.displayName} (${it.subtitle})" } ?: "Kokoro #$kokoroSid")
                current != null -> "Voz propia: " + (VoiceStore(context).all().firstOrNull { VoiceStore(context).pathOf(it.id) == current }?.name ?: File(current).name)
                else -> "Usa la voz predeterminada de Roleplay."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val refType = TtsVoiceRef.typeOf(current)
        if (refType != null && activeModel != null && refType != activeModel.type) {
            Text(
                "Esta voz es de ${refType.label} y el modelo activo es ${activeModel.type.label}: " +
                    "se usará la predeterminada hasta que actives ${refType.label}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showVoices = true }) { Text("Elegir voz") }
            if (current != null) {
                TextButton(onClick = { store.setCharacterVoicePath(characterId, null); refresh++ }) { Text("Quitar") }
            }
        }
    }
}

/** Nombres de los archivos de la carpeta elegida (vacío si no se puede leer). */
private fun listFolderNames(context: android.content.Context, tree: Uri): List<String> =
    runCatching {
        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)
            ?.listFiles()?.mapNotNull { it.name }.orEmpty()
    }.getOrDefault(emptyList())
