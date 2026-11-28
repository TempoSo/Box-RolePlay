package com.google.ai.edge.gallery.tts

import com.google.ai.edge.gallery.data.characters.WavReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLibraryTest {
    private fun v(id: String, name: String, vararg tags: String) = Voice(id, name, tags.toList(), 1L, 10_000L)

    // BUG-041: el nombre propuesto es el del archivo sin extensión.
    @Test fun nameComesFromFileWithoutExtension() {
        assertEquals("Mi voz", VoiceLibrary.nameFromFileName("Mi voz.wav"))
        assertEquals("a.b", VoiceLibrary.nameFromFileName("a.b.wav"))
        assertEquals("Voz", VoiceLibrary.nameFromFileName(null))
    }

    @Test fun duplicateNamesGetNumbered() {
        val list = listOf(v("1", "Ana"), v("2", "Ana (2)"))
        assertEquals("ana (3)", VoiceLibrary.uniqueName(list, "ana"))
        assertEquals("Luis", VoiceLibrary.uniqueName(list, "Luis"))
    }

    @Test fun searchMatchesNameAndTagsAllWords() {
        val list = listOf(v("1", "Ana", "mujer", "calmado"), v("2", "Luis", "hombre", "grave"))
        assertEquals(listOf("1"), VoiceLibrary.search(list, "mujer").map { it.id })
        assertEquals(listOf("2"), VoiceLibrary.search(list, "LUI grave").map { it.id })
        assertTrue(VoiceLibrary.search(list, "mujer grave").isEmpty())
        assertEquals(2, VoiceLibrary.search(list, "  ").size)
    }

    @Test fun addAndRemove() {
        var list = emptyList<Voice>()
        list = VoiceLibrary.withVoice(list, v("1", "Ana"))
        list = VoiceLibrary.withVoice(list, v("2", "Luis"))
        list = VoiceLibrary.without(list, "1")
        assertEquals(listOf("2"), list.map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val list = listOf(v("1", "Ana \"la\" grande", "mujer"), v("2", "Luis"))
        assertEquals(list, VoiceLibrary.decode(VoiceLibrary.encode(list)))
        assertTrue(VoiceLibrary.decode("no es json").isEmpty())
    }

    @Test fun cleanTagsLowercasesAndDedupes() {
        assertEquals(listOf("grave", "x"), VoiceLibrary.cleanTags(listOf(" Grave ", "grave", "", "X")))
    }

    @Test fun legacyFilesBecomeNamedVoices() {
        val out = VoiceLibrary.legacyVoices(setOf("default"), listOf("default", "char_abcd1234", "uuid-x", "char_zz"))
        assertEquals(listOf("char_abcd1234" to "Voz de personaje abcd", "char_zz" to "Voz de personaje zz"), out)
        assertEquals("Predeterminada", VoiceLibrary.legacyVoices(emptySet(), listOf("default")).single().second)
    }

    @Test fun durationFormat() {
        assertEquals("0:10", VoiceLibrary.formatDuration(10_000))
        assertEquals("1:05", VoiceLibrary.formatDuration(65_000))
    }

    @Test fun recordedWavIsReadableByWavReader() {
        val pcm = ByteArray(24000 * 2) // 1 s de silencio
        val pcmRead = WavReader.parse(WavWriter.pcm16Mono(pcm, 24000))
        assertNotNull(pcmRead)
        assertEquals(24000, pcmRead!!.sampleRate)
        assertEquals(1f, pcmRead.seconds, 0.01f)
    }
}
