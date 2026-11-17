package com.google.ai.edge.gallery.ui.memory

import com.jegly.offlineLLM.smollm.SmolLM.PerfStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-002: timeout por tanda según la velocidad real medida del dispositivo. */
class MemoryAbsorberTest {

    private fun stats(promptTps: Float, genTps: Float, promptTokens: Int = 100, genTokens: Int = 50) =
        PerfStats(promptTokens, 100f / promptTps, genTokens, 50f / genTps)

    @Test
    fun sinEstadisticas_usaSupuestosConservadores() {
        val t = MemoryAbsorber.tandaTimeoutMs(3500, null)
        // ~1167 tokens a 5 t/s de prefill (x2) + 400 tok a 3 t/s de gen (x2) = ~733 s.
        assertTrue("sin stats: $t", t in 700_000..770_000)
    }

    @Test
    fun dispositivoLento_watchdogPorEncimaDeLos180sAnteriores() {
        // Medidas reales del dispositivo de prueba: prefill 6.5 t/s, gen 4.33 t/s.
        val t = MemoryAbsorber.tandaTimeoutMs(3500, stats(6.5f, 4.33f))
        // ~1167 tok / 6.5 t/s x2 = ~359 s + 400/4.33 x2 = ~185 s.
        assertTrue("dispositivo lento: $t", t > 500_000)
    }

    @Test
    fun dispositivoRapido_watchdogMasCortoPeroConSuelo() {
        val t = MemoryAbsorber.tandaTimeoutMs(3500, stats(120f, 60f))
        assertEquals(480_000L, t)
    }

    @Test
    fun tandaPequena_respetaElSueloDe8Minutos() {
        val t = MemoryAbsorber.tandaTimeoutMs(200, stats(6.5f, 4.33f))
        assertEquals(480_000L, t)
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
