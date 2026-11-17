package com.google.ai.edge.gallery.ui.memory

import com.jegly.offlineLLM.smollm.SmolLM.PerfStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-002 (3.ª ronda): timeout por tanda según la velocidad real medida del dispositivo. */
class MemoryAbsorberTest {

    private fun stats(promptTps: Float, genTps: Float, promptTokens: Int = 100, genTokens: Int = 50) =
        PerfStats(promptTokens, 100f / promptTps, genTokens, 50f / genTps)

    @Test
    fun sinEstadisticas_usaSupuestosConservadores() {
        val t = MemoryAbsorber.tandaTimeoutMs(3500, null)
        // ~1000 tokens a 5 t/s de prefill (x1.5) + 300 tok a 3 t/s de gen (x1.5) = 450 s.
        assertTrue("sin stats: $t", t in 430_000..470_000)
    }

    @Test
    fun dispositivoLento_watchdogPorEncimaDeLos180sAnteriores() {
        // Medidas reales del dispositivo de prueba: prefill 6.5 t/s, gen 4.33 t/s.
        val t = MemoryAbsorber.tandaTimeoutMs(3500, stats(6.5f, 4.33f))
        // ~1000 tok / 6.5 t/s x1.5 = ~230 s + 300/4.33 x1.5 = ~104 s.
        assertTrue("dispositivo lento: $t", t > 300_000)
    }

    @Test
    fun dispositivoRapido_watchdogMasCortoPeroConSuelo() {
        val t = MemoryAbsorber.tandaTimeoutMs(3500, stats(120f, 60f))
        assertEquals(240_000L, t)
    }

    @Test
    fun tandaPequena_respetaElSueloDe4Minutos() {
        val t = MemoryAbsorber.tandaTimeoutMs(200, stats(6.5f, 4.33f))
        assertEquals(240_000L, t)
    }

    @Test
    fun velocidadesInutiles_seTratanComoSinEstadisticas() {
        // Stats de un turno degenerado (0 tokens, division por cero -> tps 0): se ignoran.
        val degeneradas = PerfStats(0, 0f, 0, 0f)
        assertEquals(
            MemoryAbsorber.tandaTimeoutMs(3500, null),
            MemoryAbsorber.tandaTimeoutMs(3500, degeneradas),
        )
    }

    @Test
    fun techoDe30MinutosAunqueElDispositivoSeaExtremadamenteLento() {
        val t = MemoryAbsorber.tandaTimeoutMs(3500, stats(0.6f, 0.6f))
        assertEquals(1_800_000L, t)
    }
}
