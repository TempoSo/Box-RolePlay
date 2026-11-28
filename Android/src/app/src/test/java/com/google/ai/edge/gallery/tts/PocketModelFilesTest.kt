package com.google.ai.edge.gallery.tts

import org.junit.Assert.assertTrue
import org.junit.Test

class PocketModelFilesTest {
    // BUG-014: el error de importación lista los archivos esperados.
    @Test fun genericMessageListsExpectedFiles() {
        val msg = PocketModelFiles.noPartsMessage(listOf("foto.jpg"))
        PocketModelFiles.EXPECTED_NAMES.forEach { assertTrue("falta $it", msg.contains(it)) }
        assertTrue(msg.contains("No se encontraron"))
    }

    @Test fun v2FolderGetsSpecificAdvice() {
        val msg = PocketModelFiles.noPartsMessage(listOf("flow_lm_flow.onnx", "mimi_encoder.onnx", "tokenizer.model"))
        assertTrue(msg.contains("v2"))
        assertTrue(msg.contains("Descargar de Hugging Face"))
        assertTrue(msg.contains("lm_flow.onnx"))
    }

    @Test fun expectedNamesAreSevenPieces() {
        assertTrue(PocketModelFiles.EXPECTED_NAMES.size == 7)
        assertTrue(PocketModelFiles.EXPECTED_NAMES.contains("vocab.json"))
    }
}
