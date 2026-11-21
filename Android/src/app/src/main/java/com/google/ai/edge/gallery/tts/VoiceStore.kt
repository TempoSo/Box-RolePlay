package com.google.ai.edge.gallery.tts

import android.content.Context
import com.google.ai.edge.gallery.data.characters.TtsSettingsStore
import com.google.ai.edge.gallery.data.characters.WavReader
import java.io.File
import java.util.UUID

/**
 * Biblioteca de voces propias (BUG-041): cada voz es `tts/voices/<id>.wav` y la lista, con nombre y
 * etiquetas, va en `tts/voices.json`. Los WAV de versiones anteriores (`default.wav`, `char_*.wav`)
 * se incorporan solos como voces con nombre la primera vez que se lee la lista.
 */
class VoiceStore(private val context: Context) {
    private val dir = TtsFiles.voicesDir(context)
    private val file = File(dir.parentFile, "voices.json")

    fun pathOf(id: String): String = File(dir, "$id.wav").absolutePath

    @Synchronized
    fun all(): List<Voice> {
        var list = if (file.exists()) VoiceLibrary.decode(file.readText()) else emptyList()
        // Se descartan voces cuyo archivo ya no existe y se incorporan los WAV antiguos.
        val alive = list.filter { File(pathOf(it.id)).exists() }
        var changed = alive.size != list.size
        list = alive
        val stems = dir.listFiles { f -> f.extension.equals("wav", true) }?.map { it.nameWithoutExtension }.orEmpty()
        VoiceLibrary.legacyVoices(list.map { it.id }.toSet(), stems).forEach { (id, name) ->
            val ms = runCatching { WavReader.parse(File(pathOf(id)).readBytes())?.seconds?.times(1000)?.toLong() }.getOrNull() ?: 0L
            list = list + Voice(id, VoiceLibrary.uniqueName(list, name), emptyList(), File(pathOf(id)).lastModified(), ms)
            changed = true
        }
        if (changed) file.writeText(VoiceLibrary.encode(list))
        return list
    }

    /** Guarda un WAV como voz nueva; null si los bytes no son un WAV legible. */
    @Synchronized
    fun add(bytes: ByteArray, name: String, tags: Collection<String>): Voice? {
        val pcm = WavReader.parse(bytes) ?: return null
        val current = all()
        val id = UUID.randomUUID().toString()
        File(pathOf(id)).writeBytes(bytes)
        val voice =
            Voice(id, VoiceLibrary.uniqueName(current, name), VoiceLibrary.cleanTags(tags), System.currentTimeMillis(), (pcm.seconds * 1000).toLong())
        file.writeText(VoiceLibrary.encode(current + voice))
        return voice
    }

    @Synchronized
    fun delete(id: String) {
        val path = pathOf(id)
        File(path).delete()
        file.writeText(VoiceLibrary.encode(VoiceLibrary.without(all(), id)))
        // Si era la predeterminada, se limpia; las de personajes caen solas a la predeterminada (el archivo ya no existe).
        val settings = TtsSettingsStore(context)
        if (settings.defaultVoicePath() == path) settings.setDefaultVoicePath(null)
    }
}
