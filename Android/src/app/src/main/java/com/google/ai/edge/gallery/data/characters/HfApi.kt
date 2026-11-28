package com.google.ai.edge.gallery.data.characters

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class HfRepo(val id: String, val downloads: Long, val likes: Long)

data class HfFile(val path: String, val size: Long) {
    val name: String get() = path.substringAfterLast('/')
}

enum class HfFileKind { MODEL, MMPROJ, SPLIT, OTHER }

enum class HfFit { GOOD, TIGHT, TOO_BIG, UNKNOWN }

/** API pública de Hugging Face (búsqueda y archivos de un repositorio). Solo lógica pura, sin red. */
object HfApi {
    private const val HOST = "https://huggingface.co"
    private val SPLIT = Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE)
    private val QUANT = Regex("(?<![A-Za-z0-9])((?:IQ|Q)\\d(?:_[A-Z0-9]+)*|BF16|F16|F32)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Búsqueda ordenada por descargas; [filter] "gguf" por defecto. */
    fun searchUrl(query: String, limit: Int = 25, filter: String = "gguf"): String =
        "$HOST/api/models?search=${enc(query.trim())}&filter=${enc(filter)}&sort=downloads&direction=-1&limit=$limit"

    private fun repoPath(repoId: String) = repoId.split('/').joinToString("/") { enc(it).replace("+", "%20") }

    fun treeUrl(repoId: String): String = "$HOST/api/models/${repoPath(repoId)}/tree/main?recursive=true"

    fun downloadUrl(repoId: String, path: String): String =
        "$HOST/${repoPath(repoId)}/resolve/main/" + path.split('/').joinToString("/") { enc(it).replace("+", "%20") } + "?download=true"

    /** Acepta "usuario/repo" o un enlace de huggingface.co; devuelve "usuario/repo" o null. */
    fun parseRepoId(input: String): String? {
        var s = input.trim().removeSuffix("/")
        s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        s = s.removePrefix("huggingface.co/").removePrefix("hf.co/")
        val parts = s.split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val ok = Regex("^[A-Za-z0-9._-]+$")
        return if (ok.matches(parts[0]) && ok.matches(parts[1])) "${parts[0]}/${parts[1]}" else null
    }

    fun parseRepos(json: String): List<HfRepo> =
        runCatching {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: o["modelId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                HfRepo(id, o["downloads"]?.jsonPrimitive?.longOrNull ?: 0L, o["likes"]?.jsonPrimitive?.longOrNull ?: 0L)
            }
        }.getOrDefault(emptyList())

    private fun JsonObject.sizeOf(): Long =
        (this["lfs"] as? JsonObject)?.get("size")?.jsonPrimitive?.longOrNull
            ?: this["size"]?.jsonPrimitive?.longOrNull ?: 0L

    /** Archivos .gguf y .litertlm del repositorio (las carpetas y el resto se ignoran). */
    fun parseFiles(json: String): List<HfFile> = parseTree(json, MODEL_EXTENSIONS)

    /**
     * BUG-013: árbol completo del repo, sin filtro de extensión. El modelo de voz necesita ver
     * .onnx/.json/.model; el navegador LLM sigue usando [parseFiles] tal cual.
     */
    fun parseTreeFiles(json: String): List<HfFile> = parseTree(json, extensions = null)

    private val MODEL_EXTENSIONS = setOf(".gguf", ".litertlm")

    private fun parseTree(json: String, extensions: Set<String>?): List<HfFile> =
        runCatching {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                if (o["type"]?.jsonPrimitive?.contentOrNull != "file") return@mapNotNull null
                val path = o["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val lower = path.lowercase()
                if (extensions != null && extensions.none { lower.endsWith(it) }) return@mapNotNull null
                HfFile(path, o.sizeOf())
            }.sortedBy { it.size }
        }.getOrDefault(emptyList())

    fun kind(file: HfFile): HfFileKind {
        val n = file.name.lowercase()
        return when {
            SPLIT.containsMatchIn(n) -> HfFileKind.SPLIT
            n.endsWith(".gguf") && "mmproj" in n -> HfFileKind.MMPROJ
            n.endsWith(".gguf") || n.endsWith(".litertlm") -> HfFileKind.MODEL
            else -> HfFileKind.OTHER
        }
    }

    /** Cuantización del nombre ("Q4_K_M", "IQ3_XS", "F16"…), o null. */
    fun quant(fileName: String): String? = QUANT.find(fileName)?.groupValues?.get(1)?.uppercase()

    /**
     * BUG-046: Gemma 4 y Gemma 3 son multimodales; si se bajan desde aquí y se registran como
     * `supportImage=false`, Visión no los ve. Se usa como valor inicial del interruptor.
     */
    fun likelyVision(fileName: String): Boolean {
        val n = fileName.lowercase()
        return n.contains("gemma-4") || n.contains("gemma-3") || "mmproj" in n
    }

    /** Qué tan holgado cabe el archivo en la RAM del teléfono (el modelo ocupa su tamaño + caché). */
    fun fit(sizeBytes: Long, ramBytes: Long): HfFit =
        when {
            sizeBytes <= 0 || ramBytes <= 0 -> HfFit.UNKNOWN
            sizeBytes <= ramBytes * 0.45 -> HfFit.GOOD
            sizeBytes <= ramBytes * 0.65 -> HfFit.TIGHT
            else -> HfFit.TOO_BIG
        }

    /** Nombre de archivo seguro para guardar (sin carpetas ni caracteres raros). */
    fun safeFileName(path: String): String {
        val base = path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
        return base.ifEmpty { "modelo" }
    }
}
