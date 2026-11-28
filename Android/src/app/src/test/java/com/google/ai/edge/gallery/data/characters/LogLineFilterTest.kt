package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLineFilterTest {
    private fun l(level: Char, tag: String, msg: String) = "11-22 13:07:34.072 27964 27964 $level $tag: $msg"

    @Test fun parseaThreadtime() {
        val p = LogLineFilter.parse(l('E', "AndroidRuntime", "FATAL EXCEPTION: main"))
        assertNotNull(p)
        assertEquals('E', p!!.level); assertEquals("AndroidRuntime", p.tag); assertEquals("FATAL EXCEPTION: main", p.message)
    }

    @Test fun lineasQueNoSonLogcatSeDescartan() {
        assertNull(LogLineFilter.parse("--------- beginning of main"))
        assertFalse(LogLineFilter.keep("--------- beginning of main"))
    }

    @Test fun ruidoDelSistemaSeDescarta() {
        assertFalse(LogLineFilter.keep(l('V', "GraphicsEnvironment", "ANGLE Developer option")))
        assertFalse(LogLineFilter.keep(l('D', "MiuiMultiWindowAdapter", "x")))
        assertFalse(LogLineFilter.keep(l('I', "HWUI", "x")))
        assertFalse(LogLineFilter.keep(l('W', "Choreographer", "Skipped 40 frames")))
    }

    @Test fun avisosYErroresUtilesSeConservan() {
        assertTrue(LogLineFilter.keep(l('E', "AndroidRuntime", "FATAL")))
        assertTrue(LogLineFilter.keep(l('W', "SmolLM", "algo raro")))
        assertTrue(LogLineFilter.keep(l('I', "AGLlamaPerf", "modelo cargado en 3.2 s")))
        assertTrue(LogLineFilter.keep(l('F', "libc", "Fatal signal 11")))
    }

    @Test fun volcadoDeJsonDelGestorDeModelosSeDescarta() {
        assertFalse(LogLineFilter.keep(l('D', "AGModelManagerViewModel", "  \"name\": \"x\",")))
        assertFalse(LogLineFilter.keep(l('D', "AGModelManagerViewModel", "}")))
        assertTrue(LogLineFilter.keep(l('D', "AGModelManagerViewModel", "Model loaded ok")))
    }
}
