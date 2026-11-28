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
    @Test fun performanceIsSavedAndLegacyPresetsStayValid() {
        val p = EnginePreset("3", "Perf", threads = 6, nBatch = 256, nUBatch = 128, flashAttn = 2,
            kvType = 3, noMmap = true, repack = false, contextLength = 8192, maxResponse = 512, imageMaxSide = 768)
        assertEquals(p, EnginePresetJson.decode(EnginePresetJson.encode(listOf(p))).first())
        val old = """[{"id":"4","name":"Viejo","model":"gemma"}]"""
        val legacy = EnginePresetJson.decode(old).first()
        assertEquals(null, legacy.threads)
        assertEquals(null, legacy.imageMaxSide)
        assertEquals(EnginePreset("4", "Viejo", "gemma"), legacy)
    }

    @Test fun applyingPresetOnlyOverridesSavedFields() {
        val current = EnginePerfSettings(threads = 4, nBatch = 512, kvType = 1, imageMaxSide = 512)
        val legacy = EnginePreset("1", "Viejo", threads = null, nBatch = null)
        assertEquals(current, current.overriddenBy(legacy))
        val full = EnginePreset("2", "Perf", threads = 8, nBatch = 999, imageMaxSide = 1024)
        val applied = current.overriddenBy(full)
        assertEquals(8, applied.threads)
        assertEquals(999, applied.nBatch)
        assertEquals(1024, applied.imageMaxSide)
        assertEquals(1, applied.kvType)
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
