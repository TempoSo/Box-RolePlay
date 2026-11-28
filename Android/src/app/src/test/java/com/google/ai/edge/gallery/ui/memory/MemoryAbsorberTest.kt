package com.google.ai.edge.gallery.ui.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BUG-002 (5.ª ronda): vigilancia de tanda por tope global + detector de atasco en generación. */
class MemoryAbsorberTest {

    @Test
    fun prefillSinTokens_noDisparaAtascoSoloElTopeGlobal() {
        // En prefill no hay señal de progreso: 10 min sin tokens siguen sin ser "atasco".
        assertNull(MemoryAbsorber.tandaAbortReason(elapsedMs = 600_000, idleMs = 600_000, gotToken = false))
    }

    @Test
    fun prefillLargo_superaElTopeGlobal() {
        assertEquals(
            "timeout",
            MemoryAbsorber.tandaAbortReason(
                elapsedMs = MemoryAbsorber.TANDA_TIMEOUT_MS + 1,
                idleMs = MemoryAbsorber.TANDA_TIMEOUT_MS + 1,
                gotToken = false,
            ),
        )
    }

    @Test
    fun generacionConProgresoConstante_noAborta() {
        // Tokens cada 0.3 s durante 19 minutos: idle siempre por debajo del umbral.
        assertNull(MemoryAbsorber.tandaAbortReason(elapsedMs = 1_140_000, idleMs = 500, gotToken = true))
    }

    @Test
    fun generacionSinTokensNuevos_esAtasco() {
        assertEquals(
            "stall",
            MemoryAbsorber.tandaAbortReason(
                elapsedMs = 200_000,
                idleMs = MemoryAbsorber.STALL_TIMEOUT_MS + 1,
                gotToken = true,
            ),
        )
    }

    @Test
    fun elTopeGlobalMandaAunqueHayaTokens() {
        assertEquals(
            "timeout",
            MemoryAbsorber.tandaAbortReason(
                elapsedMs = MemoryAbsorber.TANDA_TIMEOUT_MS + 1,
                idleMs = 0,
                gotToken = true,
            ),
        )
    }

    @Test
    fun alBordeDeLosUmbrales_noAborta() {
        assertNull(
            MemoryAbsorber.tandaAbortReason(
                elapsedMs = MemoryAbsorber.TANDA_TIMEOUT_MS,
                idleMs = MemoryAbsorber.STALL_TIMEOUT_MS,
                gotToken = true,
            ),
        )
    }
}
