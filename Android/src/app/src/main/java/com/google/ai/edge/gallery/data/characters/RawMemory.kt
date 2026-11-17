package com.google.ai.edge.gallery.data.characters

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Recuerdo "sin procesar": nota ligera guardada al instante; el modelo la resume al absorber. */
data class RawMemory(
    val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

object RawMemoryJson {
    fun encode(list: List<RawMemory>): String =
        buildJsonArray {
            list.forEach { m ->
                add(buildJsonObject {
                    put("id", m.id); put("characterId", m.characterId)
                    put("text", m.text); put("createdAt", m.createdAt)
                })
            }
        }.toString()

    fun decode(text: String): List<RawMemory> =
        runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                RawMemory(
                    id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    characterId = o["characterId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    text = o["text"]?.jsonPrimitive?.contentOrNull ?: "",
                    createdAt = o["createdAt"]?.jsonPrimitive?.long ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
}

/** Prompt para que el modelo convierta notas sueltas en un recuerdo real, y limpieza de su salida. */
object MemoryAbsorbPrompt {
    private const val MAX_NOTES_CHARS = 6000

    /** BUG-002: tamaño máximo de cada tanda de notas (caracteres), holgado para el contexto. */
    const val MAX_TANDA_CHARS = 3500

    /** BUG-002: máximo de tandas por absorción; si hay más, se descartan las más viejas. */
    const val MAX_TANDAS = 8
    private val THINK = Regex("(?s)<think>.*?</think>")
    private val TAGS = Regex("<[^>]{1,40}>")

    /** [oldestFirst]: notas de la más vieja a la más nueva. Si no caben, se descartan las más viejas. */
    fun build(userName: String, charName: String, oldestFirst: List<String>): String {
        val kept = ArrayDeque<String>()
        var size = 0
        for (n in oldestFirst.asReversed()) {
            if (size + n.length > MAX_NOTES_CHARS && kept.isNotEmpty()) break
            kept.addFirst(n.take(MAX_NOTES_CHARS)); size += n.length
        }
        val user = userName.ifBlank { "User" }
        val char = charName.ifBlank { "Character" }
        return buildString {
            append("Below are notes taken from conversations between $user and $char.\n")
            append("Write ONE concise memory (3 to 6 sentences) from $char's point of view, in the third person, ")
            append("in the same language as the notes. Keep names, facts, promises, plans and feelings. ")
            append("Do not invent anything that is not in the notes. Output only the memory text.\n\nNotes:\n")
            kept.forEach { append("- ").append(it.replace('\n', ' ')).append('\n') }
            append("\nMemory:")
        }
    }

    /** Quita razonamiento, etiquetas, markdown y prefijos; null si no quedó nada útil. */
    fun clean(output: String): String? {
        var t = output.replace(THINK, " ").replace(TAGS, " ").replace(Regex("[*_`#]+"), "")
        t = t.replace(Regex("^\\s*(Memory|Recuerdo)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length < 20) return null
        return if (t.length <= 1200) t else t.take(1200).let { c ->
            val end = c.lastIndexOfAny(charArrayOf('.', '!', '?'))
            if (end > 600) c.take(end + 1) else c
        }
    }

    /**
     * BUG-002: reparte las notas (en orden cronológico, la más vieja primero) en tandas que
     * caben holgadas en el contexto, para que la absorción no desborde el modelo (crash GGUF
     * al deslizar la ventana / timeout LiteRT). Si salen más de [maxTandas] tandas, se
     * descartan las más viejas (igual criterio que build()).
     */
    inline fun <T> tandas(
        oldestFirst: List<T>,
        maxChars: Int = MAX_TANDA_CHARS,
        maxTandas: Int = MAX_TANDAS,
        sizeOf: (T) -> Int,
    ): List<List<T>> {
        val out = mutableListOf<List<T>>()
        var current = mutableListOf<T>()
        var size = 0
        for (item in oldestFirst) {
            val s = sizeOf(item).coerceAtLeast(1)
            if (current.isNotEmpty() && size + s > maxChars) {
                out.add(current); current = mutableListOf(); size = 0
            }
            current.add(item); size += s
        }
        if (current.isNotEmpty()) out.add(current)
        return if (out.size > maxTandas) out.takeLast(maxTandas) else out
    }
}
