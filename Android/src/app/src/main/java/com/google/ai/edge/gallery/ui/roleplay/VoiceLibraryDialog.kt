package com.google.ai.edge.gallery.ui.roleplay

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.characters.WavReader
import com.google.ai.edge.gallery.tts.SherpaPocketEngine
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.tts.Voice
import com.google.ai.edge.gallery.tts.VoiceLibrary
import com.google.ai.edge.gallery.tts.VoiceStore
import com.google.ai.edge.gallery.tts.WavWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una muestra por guardar: viene de un .wav subido o de una grabación. */
private class Pending(val bytes: ByteArray, val name: String, val durationMs: Long)

/**
 * Biblioteca de voces (BUG-041): sube un .wav o graba con el micrófono, escúchalo, ponle nombre y
 * etiquetas y guárdalo; después elige cualquiera como voz predeterminada o de un personaje.
 * [onPick] != null muestra el botón «Usar»; [currentPath] marca la voz que está en uso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceLibraryDialog(onDismiss: () -> Unit, currentPath: String? = null, onPick: ((Voice, String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { VoiceStore(context) }
    var voices by remember { mutableStateOf(store.all()) }
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var pendingTags by remember { mutableStateOf(setOf<String>()) }
    var customTag by remember { mutableStateOf("") }
    var testText by remember { mutableStateOf("Hola, esta es mi voz. ¿Qué te parece cómo suena?") }
    var playingKey by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Voice?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableIntStateOf(0) }
    val stopFlag = remember { AtomicBoolean(false) }
    val player = remember { PreviewPlayer() }
    DisposableEffect(Unit) { onDispose { stopFlag.set(true); player.stop() } }

    fun setPending(p: Pending) {
        pending = p; pendingName = p.name; pendingTags = emptySet(); customTag = ""
    }

    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val pcm = bytes?.let { WavReader.parse(it) }
            if (bytes == null || pcm == null) {
                Toast.makeText(context, "Ese archivo no es un WAV legible (PCM de 16/24/32 bits).", Toast.LENGTH_LONG).show()
            } else {
                val display =
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                setPending(Pending(bytes, VoiceLibrary.nameFromFileName(display ?: uri.lastPathSegment), (pcm.seconds * 1000).toLong()))
            }
        }

    fun startRecording() {
        stopFlag.set(false)
        recording = true
        recSeconds = 0
        scope.launch {
            val wav = withContext(Dispatchers.IO) { recordWav(stopFlag) { s -> recSeconds = s } }
            recording = false
            if (wav == null) Toast.makeText(context, "No se pudo grabar (¿micrófono en uso?).", Toast.LENGTH_LONG).show()
            else WavReader.parse(wav)?.let { setPending(Pending(wav, "Voz grabada", (it.seconds * 1000).toLong())) }
        }
    }
    val askMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startRecording() else Toast.makeText(context, "Sin permiso de micrófono no se puede grabar.", Toast.LENGTH_LONG).show()
        }

    fun togglePreview(key: String, path: String) {
        if (playingKey == key) { player.stop(); playingKey = null; return }
        playingKey = key
        player.play(path) { playingKey = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voces") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Sube un .wav o graba una muestra de unos 5-12 s de una sola voz, sin ruido de fondo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFile.launch(arrayOf("audio/*")) }, enabled = !recording) { Text("Subir .wav") }
                    OutlinedButton(
                        onClick = {
                            if (recording) stopFlag.set(true)
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                            else askMic.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    ) { Text(if (recording) "Detener (${VoiceLibrary.formatDuration(recSeconds * 1000L)})" else "Grabar") }
                }

                pending?.let { p ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider()
                        Text("Nueva voz · ${VoiceLibrary.formatDuration(p.durationMs)}", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = {
                                val tmp = File(context.cacheDir, "voice_preview.wav").apply { writeBytes(p.bytes) }
                                togglePreview("pending", tmp.absolutePath)
                            },
                        ) { Text(if (playingKey == "pending") "Detener" else "Escuchar muestra") }
                        OutlinedTextField(
                            value = pendingName,
                            onValueChange = { pendingName = it },
                            label = { Text("Nombre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (VoiceLibrary.FIXED_TAGS + pendingTags.filter { it !in VoiceLibrary.FIXED_TAGS }).forEach { t ->
                                FilterChip(
                                    selected = t in pendingTags,
                                    onClick = { pendingTags = if (t in pendingTags) pendingTags - t else pendingTags + t },
                                    label = { Text(t) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = customTag,
                                onValueChange = { customTag = it },
                                label = { Text("Etiqueta propia") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                enabled = customTag.isNotBlank(),
                                onClick = { pendingTags = pendingTags + customTag.trim().lowercase(); customTag = "" },
                            ) { Text("Añadir") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = pendingName.isNotBlank(),
                                onClick = {
                                    player.stop(); playingKey = null
                                    val saved = store.add(p.bytes, pendingName, pendingTags)
                                    if (saved == null) Toast.makeText(context, "No se pudo guardar la voz.", Toast.LENGTH_LONG).show()
                                    else { voices = store.all(); pending = null }
                                },
                            ) { Text("Guardar voz") }
                            TextButton(onClick = { player.stop(); playingKey = null; pending = null }) { Text("Descartar") }
                        }
                        HorizontalDivider()
                    }
                }

                Text("Mis voces (${voices.size})", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text("Texto de prueba (botón del micrófono parlante)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por nombre o etiqueta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = VoiceLibrary.search(voices, query).sortedBy { it.name.lowercase() }
                if (shown.isEmpty()) {
                    Text(
                        if (voices.isEmpty()) "Aún no hay voces guardadas." else "Ninguna voz coincide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shown.forEach { v ->
                    val path = store.pathOf(v.id)
                    val inUse = currentPath != null && currentPath == path
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name + if (inUse) " · en uso" else "", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (listOf(VoiceLibrary.formatDuration(v.durationMs)) + v.tags).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { togglePreview(v.id, path) }) {
                            Icon(if (playingKey == v.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Escuchar muestra")
                        }
                        IconButton(
                            onClick = {
                                if (!SherpaPocketEngine.isLibraryAvailable() || !TtsManager.isModelReady(context)) {
                                    Toast.makeText(context, "Falta el modelo o la librería de voz (Roleplay > Voz).", Toast.LENGTH_LONG).show()
                                } else {
                                    // Entre comillas para que también cuente con «Solo diálogo».
                                    TtsManager.speakAs(context, "\"" + testText.trim() + "\"", path)
                                }
                            },
                        ) { Icon(Icons.Rounded.RecordVoiceOver, "Probar con el texto de prueba") }
                        IconButton(onClick = { deleting = v }) {
                            Icon(Icons.Rounded.Delete, "Borrar voz", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (onPick != null && !inUse) {
                        TextButton(
                            onClick = { player.stop(); onPick(v, path) },
                            modifier = Modifier.padding(start = 0.dp),
                        ) { Text("Usar «${v.name}»") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )

    deleting?.let { v ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar «${v.name}»?") },
            text = { Text("Los personajes que la usen volverán a la voz predeterminada.") },
            confirmButton = {
                TextButton(onClick = {
                    player.stop(); playingKey = null
                    store.delete(v.id); voices = store.all(); deleting = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/** Reproduce una muestra con MediaPlayer; una sola a la vez. */
private class PreviewPlayer {
    private var mp: MediaPlayer? = null

    fun play(path: String, onDone: () -> Unit) {
        stop()
        mp = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { onDone() }
                prepare()
                start()
            }
        }.getOrElse { onDone(); null }
    }

    fun stop() {
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        mp = null
    }
}

/** Graba del micrófono a 24 kHz mono hasta [stop] o 30 s; devuelve un WAV, o null si no se pudo. */
@SuppressLint("MissingPermission")
private fun recordWav(stop: AtomicBoolean, onSecond: (Int) -> Unit): ByteArray? {
    val rate = 24000
    val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (min <= 0) return null
    val rec = runCatching {
        AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
    }.getOrNull() ?: return null
    if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    val maxBytes = rate * 2 * 30
    var lastSecond = 0
    try {
        rec.startRecording()
        while (!stop.get() && out.size() < maxBytes) {
            val n = rec.read(buf, 0, buf.size)
            if (n > 0) out.write(buf, 0, n) else if (n < 0) return null
            val sec = out.size() / (rate * 2)
            if (sec != lastSecond) { lastSecond = sec; onSecond(sec) }
        }
    } finally {
        runCatching { rec.stop() }
        rec.release()
    }
    return if (out.size() < rate / 2) null else WavWriter.pcm16Mono(out.toByteArray(), rate)
}
