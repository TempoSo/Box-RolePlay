package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePresetTest {
    @Test fun roundTrip() {
        val l = listOf(EnginePreset("1", "Rápido", "gemma", "mm.gguf", "p1", "creative", "{\"a\":1}"), EnginePreset("2", "Calidad"))
        assertEquals(l, EnginePresetJson.decode(EnginePresetJson.encode(l)))
        assertTrue(EnginePresetJson.decode("???").isEmpty())
    }
    @Test fun upsertReplacesSameNameAndSorts() {
        var l = EnginePresetJson.upsert(emptyList(), EnginePreset("1", "Zeta", "m1"))
        l = EnginePresetJson.upsert(l, EnginePreset("2", "alfa", "m2"))
        l = EnginePresetJson.upsert(l, EnginePreset("3", "ZETA", "m3"))
        assertEquals(listOf("alfa", "ZETA"), l.map { it.name })
        assertEquals("m3", l.last().model)
        assertEquals(l, EnginePresetJson.upsert(l, EnginePreset("9", "   ")))
    }
}
