package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private data class Item(val id: String, val v: String)

    // BUG-038: lo de la copia sobrescribe por id; lo que ya existía y no está en la copia se conserva; nada se duplica.
    @Test fun mergeOverwritesById_keepsOthers_noDuplicates() {
        val existing = listOf(Item("a", "viejo"), Item("b", "mío"), Item("c", "creado después"))
        val incoming = listOf(Item("a", "de la copia"), Item("d", "nuevo"), Item("d", "repetido en la copia"))
        val merged = BackupFormat.mergeById(existing, incoming) { it.id }
        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
        assertEquals("de la copia", merged.first { it.id == "a" }.v)
        assertEquals("mío", merged.first { it.id == "b" }.v)
        assertEquals("creado después", merged.first { it.id == "c" }.v)
    }

    @Test fun mergeWithEmptyIncomingChangesNothing() {
        val existing = listOf(Item("a", "x"))
        assertEquals(existing, BackupFormat.mergeById(existing, emptyList()) { it.id })
        assertEquals(existing, BackupFormat.mergeById(emptyList(), existing) { it.id })
    }

    @Test fun namedPresetsMergeByName() {
        val m = BackupFormat.mergeByName(mapOf("a" to "1", "b" to "2"), mapOf("b" to "nuevo", "c" to "3"))
        assertEquals(mapOf("a" to "1", "b" to "nuevo", "c" to "3"), m)
    }

    @Test fun activePersonaStaysTheCurrentOne() {
        val mine = PersonaDto("p1", "Yo", "", true, 1, 1)
        val other = PersonaDto("p2", "Otra", "", false, 1, 1)
        val fromBackup = listOf(PersonaDto("p2", "Otra (copia)", "d", true, 2, 2), PersonaDto("p3", "Tercera", "", false, 2, 2))
        val merged = BackupFormat.mergePersonas(listOf(mine, other), fromBackup)
        assertEquals(listOf("p1", "p2", "p3"), merged.map { it.id })
        assertEquals(listOf("p1"), merged.filter { it.isActive }.map { it.id })
        assertEquals("Otra (copia)", merged.first { it.id == "p2" }.name)
        // Sin persona activa previa, se activa la de la copia.
        val none = BackupFormat.mergePersonas(emptyList(), fromBackup)
        assertEquals(listOf("p2"), none.filter { it.isActive }.map { it.id })
    }

    @Test fun sectionsAreDetectedFromEntryNames() {
        val names = listOf("manifest.json", "characters/abc.json", "characters/abc.meta.json", "characters/abc.avatar.png", "memories.json", "scenes.json", "sampler_presets.json")
        assertEquals(
            setOf(BackupSection.CHARACTERS, BackupSection.MEMORIES, BackupSection.SCENES, BackupSection.PRESETS),
            BackupFormat.sectionsOf(names),
        )
        assertFalse(BackupFormat.isLegacy(names))
        assertEquals(emptySet<BackupSection>(), BackupFormat.sectionsOf(listOf("manifest.json")))
    }

    @Test fun legacyZipOnlyHasCharacters() {
        val names = listOf("001-Ana.json", "001-Ana.avatar.png", "002-Luis.json")
        assertTrue(BackupFormat.isLegacy(names))
        assertEquals(setOf(BackupSection.CHARACTERS), BackupFormat.sectionsOf(names))
    }

    @Test fun characterIdFromEntry() {
        assertEquals("abc", BackupFormat.characterId("characters/abc.json"))
        assertNull(BackupFormat.characterId("characters/abc.meta.json"))
        assertNull(BackupFormat.characterId("characters/abc.avatar.png"))
        assertNull(BackupFormat.characterId("memories.json"))
        assertNull(BackupFormat.characterId("characters/sub/abc.json"))
    }

    @Test fun jsonRoundTrips() {
        val mem = listOf(MemoryDto("m1", "c1", "le gusta \"el té\"", 5))
        assertEquals(mem, BackupFormat.decodeMemories(BackupFormat.encodeMemories(mem)))
        val per = listOf(PersonaDto("p", "Yo", "línea1\nlínea2", true, 1, 2))
        assertEquals(per, BackupFormat.decodePersonas(BackupFormat.encodePersonas(per)))
        val map = mapOf("Mi preset" to "{\"t\":0.8}")
        assertEquals(map, BackupFormat.decodeStringMap(BackupFormat.encodeStringMap(map)))
        assertTrue(BackupFormat.decodeMemories("no es json").isEmpty())
        assertTrue(BackupFormat.decodePersonas("{}").isEmpty())
        assertTrue(BackupFormat.decodeStringMap("[]").isEmpty())
    }

    @Test fun characterMetaRoundTrip() {
        val meta = BackupFormat.decodeCharacterMeta(BackupFormat.encodeCharacterMeta("modelo.gguf", 10, 20))
        assertNotNull(meta)
        assertEquals("modelo.gguf", meta!!.modelName)
        assertEquals(10L, meta.createdAt)
        assertNull(BackupFormat.decodeCharacterMeta("basura"))
    }

    @Test fun manifestListsSections() {
        val m = BackupFormat.manifest(listOf(BackupSection.CHARACTERS, BackupSection.SCENES), 7)
        assertTrue(m.contains("\"characters\"") && m.contains("\"scenes\"") && m.contains("\"version\":2"))
    }
}
