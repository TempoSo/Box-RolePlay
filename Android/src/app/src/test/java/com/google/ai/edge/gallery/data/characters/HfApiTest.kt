package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HfApiTest {
    @Test fun urls() {
        assertEquals("https://huggingface.co/api/models?search=qwen+3b&filter=gguf&sort=downloads&direction=-1&limit=25", HfApi.searchUrl(" qwen 3b "))
        assertEquals("https://huggingface.co/api/models/bartowski/Llama-3.2-3B-GGUF/tree/main?recursive=true", HfApi.treeUrl("bartowski/Llama-3.2-3B-GGUF"))
        assertEquals("https://huggingface.co/u/r/resolve/main/sub%20dir/a%20b.gguf?download=true", HfApi.downloadUrl("u/r", "sub dir/a b.gguf"))
    }
    @Test fun repoIdParsing() {
        assertEquals("u/r", HfApi.parseRepoId("u/r"))
        assertEquals("u/r", HfApi.parseRepoId("https://huggingface.co/u/r/blob/main/x.gguf"))
        assertEquals("u/r", HfApi.parseRepoId("hf.co/u/r/"))
        assertNull(HfApi.parseRepoId("solo"))
        assertNull(HfApi.parseRepoId("u/r r"))
    }
    @Test fun parseRepos() {
        val r = HfApi.parseRepos("""[{"id":"a/b","downloads":10,"likes":2},{"modelId":"c/d"},{"x":1}]""")
        assertEquals(listOf(HfRepo("a/b", 10, 2), HfRepo("c/d", 0, 0)), r)
        assertTrue(HfApi.parseRepos("no json").isEmpty())
    }
    @Test fun parseFilesUsesLfsSizeAndFilters() {
        val json = """[
          {"type":"file","path":"README.md","size":900},
          {"type":"directory","path":"sub"},
          {"type":"file","path":"m-Q8_0.gguf","size":134,"lfs":{"size":8000}},
          {"type":"file","path":"sub/m-Q4_K_M.gguf","size":134,"lfs":{"size":4000}},
          {"type":"file","path":"mmproj-F16.gguf","size":134,"lfs":{"size":500}},
          {"type":"file","path":"big-00001-of-00002.gguf","size":134,"lfs":{"size":9000}},
          {"type":"file","path":"gemma.litertlm","size":3000}]"""
        val f = HfApi.parseFiles(json)
        assertEquals(listOf("mmproj-F16.gguf", "gemma.litertlm", "sub/m-Q4_K_M.gguf", "m-Q8_0.gguf", "big-00001-of-00002.gguf"), f.map { it.path })
        assertEquals(4000L, f.first { it.name == "m-Q4_K_M.gguf" }.size)
        assertEquals(HfFileKind.MMPROJ, HfApi.kind(f[0]))
        assertEquals(HfFileKind.MODEL, HfApi.kind(f[1]))
        assertEquals(HfFileKind.SPLIT, HfApi.kind(f.last()))
    }
    @Test fun parseTreeFilesKeepsEveryExtension() {
        val json = """[
          {"type":"file","path":"README.md","size":10},
          {"type":"directory","path":"es"},
          {"type":"file","path":"es/encoder.onnx","size":400,"lfs":{"size":9000}},
          {"type":"file","path":"es/tokenizer.json","size":800},
          {"type":"file","path":"es/vocab.json","size":200},
          {"type":"file","path":"model.gguf","size":3000}]"""
        assertEquals(
            listOf("README.md", "es/vocab.json", "es/tokenizer.json", "model.gguf", "es/encoder.onnx"),
            HfApi.parseTreeFiles(json).map { it.path },
        )
        assertEquals(9000L, HfApi.parseTreeFiles(json).first { it.name == "encoder.onnx" }.size)
        // El navegador LLM conserva su filtro: solo .gguf/.litertlm.
        assertEquals(listOf("model.gguf"), HfApi.parseFiles(json).map { it.path })
        assertTrue(HfApi.parseTreeFiles("no json").isEmpty())
    }
    @Test fun quantAndFit() {
        assertEquals("Q4_K_M", HfApi.quant("Llama-3.2-3B-Instruct-Q4_K_M.gguf"))
        assertEquals("IQ3_XS", HfApi.quant("model.IQ3_XS.gguf"))
        assertEquals("F16", HfApi.quant("mmproj-model-f16.gguf"))
        assertNull(HfApi.quant("gemma.litertlm"))
        val gb = 1024L * 1024 * 1024
        assertEquals(HfFit.GOOD, HfApi.fit(2 * gb, 8 * gb))
        assertEquals(HfFit.TIGHT, HfApi.fit(5 * gb, 8 * gb))
        assertEquals(HfFit.TOO_BIG, HfApi.fit(7 * gb, 8 * gb))
        assertEquals(HfFit.UNKNOWN, HfApi.fit(0, 8 * gb))
    }
    @Test fun safeName() { assertEquals("a_b.gguf", HfApi.safeFileName("x/y/a b.gguf")); assertEquals("modelo", HfApi.safeFileName("///")) }
}
