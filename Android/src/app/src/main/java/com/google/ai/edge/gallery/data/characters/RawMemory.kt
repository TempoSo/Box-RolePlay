package com.google.ai.edge.gallery.data.characters

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

/**
 * BUG-008: reemplaza el texto de una nota conservando su id y su fecha (el orden cronológico de las
 * tandas del absorbedor depende de `createdAt`). Un texto en blanco no edita nada: para eso está borrar.
 */
fun List<RawMemory>.withEditedText(id: String, text: String): List<RawMemory> {
    val clean = text.trim()
    if (clean.isEmpty()) return this
    return map { if (it.id == id) it.copy(text = clean) else it }
}

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

    /** Máximo de caracteres por campo de contexto del personaje (persona, descripción, etc.). */
    private const val MAX_FIELD_CHARS = 600

    /**
     * Prompt archivista (estilo Layla): el modelo lee las notas con el contexto del personaje por
     * campos y destila UN recuerdo. Las notas se sanitizan (sin etiquetas `<...>`), porque el
     * tokenizador nativo parsea special tokens dentro del texto y una nota manual puede traerse
     * uno pegado del chat (BUG-002).
     *
     * [oldestFirst]: notas de la más vieja a la más nueva. Si no caben, se descartan las más viejas.
     * [persona]/[description]/[personality]: contexto del personaje y del usuario (vacío = se omite).
     */
    fun build(
        userName: String,
        charName: String,
        oldestFirst: List<String>,
        persona: String = "",
        description: String = "",
        personality: String = "",
    ): String {
        val kept = ArrayDeque<String>()
        var size = 0
        for (n in oldestFirst.asReversed()) {
            val clean = sanitize(n).take(MAX_NOTES_CHARS)
            if (clean.isBlank()) continue
            if (size + clean.length > MAX_NOTES_CHARS && kept.isNotEmpty()) break
            kept.addFirst(clean); size += clean.length
        }
        val user = userName.ifBlank { "User" }
        val char = charName.ifBlank { "Character" }
        return buildString {
            append("You are a meticulous archivist of a character's inner life. Read the notes below, ")
            append("taken from conversations between $user and $char, and distil one durable memory: ")
            append("facts, feelings, relationships and turning points. Ignore small talk and meta-chatter.\n\n")
            append("Character context:\n")
            append("Character name: $char\n")
            append("User name: $user\n")
            if (persona.isNotBlank()) append("User persona: ").append(persona.take(MAX_FIELD_CHARS)).append('\n')
            if (description.isNotBlank()) append("Character description: ").append(description.take(MAX_FIELD_CHARS)).append('\n')
            if (personality.isNotBlank()) append("Character personality: ").append(personality.take(MAX_FIELD_CHARS)).append('\n')
            append("\nWrite ONE concise memory (3 to 6 sentences) from $char's point of view, in the third person, ")
            append("in the same language as the notes. Keep names, facts, promises, plans and feelings. ")
            append("Do not invent anything that is not in the notes. ")
            append("Output ONLY a JSON object with: summary (the memory), topic (short label), ")
            append("keys (3 to 5 short keywords for later recall), importance (1 to 10).\n\nNotes:\n")
            kept.forEach { append("- ").append(it.replace('\n', ' ')).append('\n') }
            append("\nMemory:")
        }
    }

    /** Quita etiquetas `<...>` (incluidos special tokens como `<end_of_turn>` o `<turn|>`) y compacta espacios. */
    private fun sanitize(text: String): String =
        text.replace(TAGS, " ").replace(Regex("\\s+"), " ").trim()

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

    /** Recuerdo destilado con metadatos; si el modelo no respeta el JSON se derivan del texto. */
    data class RecuerdoExtraido(
        val resumen: String,
        val topic: String = "",
        val keys: List<String> = emptyList(),
        val importance: Int = 5,
    )

    /**
     * Parseo tolerante del archivista: JSON si viene en JSON, y si no, texto plano con valores
     * por defecto. Un modelo local pequeño no garantiza el formato, así que la memoria no puede
     * depender de que lo acierte.
     */
    fun parse(output: String): RecuerdoExtraido? {
        val objeto = runCatching { Json.parseToJsonElement(output.trim()).jsonObject }.getOrNull()
        if (objeto != null) {
            val resumen =
                (objeto["summary"] as? JsonPrimitive)?.contentOrNull?.let { clean(it) }
                    ?: (objeto["memory"] as? JsonPrimitive)?.contentOrNull?.let { clean(it) }
            if (resumen != null) {
                val topic = (objeto["topic"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().take(60)
                val keys =
                    ((objeto["keys"] as? JsonArray)
                            ?: JsonArray(emptyList()))
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                        .filter { it.length in 2..40 }
                        .distinct()
                        .take(5)
                val importance = (objeto["importance"] as? JsonPrimitive)?.intOrNull?.coerceIn(1, 10) ?: 5
                return RecuerdoExtraido(resumen, topic, keys, importance)
            }
        }
        return clean(output)?.let { RecuerdoExtraido(it) }
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

    /**
     * Igual que [tandas], pero para la lista tal como la devuelve `RawMemoryStore.forCharacter`:
     * de la nota MÁS NUEVA a la más vieja. Se le da la vuelta antes de repartir, de modo que cada
     * tanda va en orden cronológico y, si hay demasiadas, se descartan las más viejas (antes se
     * pasaba la lista sin invertir y se perdían las más nuevas).
     */
    inline fun <T> tandasFromNewestFirst(
        newestFirst: List<T>,
        maxChars: Int = MAX_TANDA_CHARS,
        maxTandas: Int = MAX_TANDAS,
        sizeOf: (T) -> Int,
    ): List<List<T>> = tandas(newestFirst.asReversed(), maxChars, maxTandas, sizeOf)
}
