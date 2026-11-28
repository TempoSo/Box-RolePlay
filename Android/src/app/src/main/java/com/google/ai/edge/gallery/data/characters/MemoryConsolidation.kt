package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fase 3 de memoria: re-resumir los recuerdos breves antiguos por tema en uno solo, marcar los
 * originales como no vigentes y dejar el consolidado listo para recuperarse. Sin modelo nuevo,
 * sin migración de BD y sin tocar las notas puente del chat.
 *
 * Toda la lógica de selección y metadatos es determinista y no depende de Android, para poder
 * probarla en JVM. El modelo solo produce el texto del resumen; el resto lo decide este objeto.
 */
object MemoryConsolidation {
    const val MIN_EDAD_DIAS = 7
    const val MIN_ITEMS = 3
    private const val DIA_MS = 24L * 60 * 60 * 1000
    private val PENSAMIENTO = Regex("(?s)<think>.*?</think>")
    private val ETIQUETAS = Regex("<[^>]{1,40}>")
    private val ESPACIOS = Regex("\\s+")

    data class PlanConsolidacion(
        val nuevo: CharacterMemory,
        val idsAInvalidar: List<String>,
    )

    /**
     * Grupos de recuerdos lo bastante antiguos como para consolidarse. Se ignoran los que ya
     * están invalidados y los que ya son resúmenes (así la operación es idempotente). El grupo
     * se forma por tema; los recuerdos sin tema se agrupan en "general".
     */
    fun cohortes(
        memorias: List<CharacterMemory>,
        now: Long,
        minEdadDias: Int = MIN_EDAD_DIAS,
        minItems: Int = MIN_ITEMS,
    ): List<List<CharacterMemory>> {
        val corte = now - minEdadDias * DIA_MS
        val candidatas =
            memorias
                .filter { it.valid && it.kind != "summary" && it.createdAt <= corte }
                .groupBy { it.topic.trim().lowercase().ifEmpty { "general" } }
                .values
                .map { grupo -> grupo.sortedBy { it.createdAt } }
                .filter { it.size >= minItems }
        return candidatas.flatMap { grupo ->
            MemoryAbsorbPrompt.tandas(grupo, sizeOf = { it.content.length })
        }
    }

    /** Prompt de re-resumen: aparece una jerarquía, no notas sueltas de conversación. */
    fun prompt(
        cohorte: List<CharacterMemory>,
        userName: String,
        charName: String,
        persona: String = "",
        description: String = "",
        personality: String = "",
    ): String {
        val limpio: (String) -> String = {
            it.replace(PENSAMIENTO, " ").replace(ETIQUETAS, " ").replace(ESPACIOS, " ").trim()
        }
        val user = limpio(userName).ifBlank { "User" }
        val char = limpio(charName).ifBlank { "Character" }
        return buildString {
            append("You are consolidating a character's long-term memory. Several smaller memories ")
            append("about the same topic have already been distilled. Merge them into ONE durable memory ")
            append("from $char's point of view, in the third person, in the same language as the text. ")
            append("Keep every name, fact, feeling, promise and plan that still matters; drop repetition. ")
            append("Do not invent anything that is not in the source memories.\n\n")
            append("Character: $char\nUser: $user\n")
            if (persona.isNotBlank()) append("User persona: ").append(limpio(persona).take(600)).append('\n')
            if (description.isNotBlank()) append("Character description: ").append(limpio(description).take(600)).append('\n')
            if (personality.isNotBlank()) append("Character personality: ").append(limpio(personality).take(600)).append('\n')
            append("\nSource memories (oldest first):\n")
            cohorte.forEachIndexed { i, m ->
                append(i + 1).append(". ").append(limpio(m.content).take(1200)).append('\n')
            }
            append("\nWrite ONE concise memory of 3 to 6 sentences. Output ONLY a JSON object with: ")
            append("summary, topic (short label), keys (3 to 5 short keywords), importance (1 to 10).\n\nNotes:\n")
        }
    }

    /** Claves del JSON de un recuerdo, tolerante a datos viejos o mal formados. */
    fun claves(memory: CharacterMemory): List<String> =
        runCatching {
            (Json.parseToJsonElement(memory.keysJson) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                ?.filter { it.length in 1..40 }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun clavesJson(claves: List<String>): String =
        claves
            .distinct()
            .take(5)
            .joinToString(",", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

    /**
     * Construye el recuerdo consolidado. Elige el tema y las claves del modelo, completando con
     * los originales; la importancia es el máximo, nunca la media, para no perder un hecho crítico.
     */
    fun unir(
        cohorte: List<CharacterMemory>,
        extraido: MemoryAbsorbPrompt.RecuerdoExtraido,
        now: Long,
    ): CharacterMemory? {
        val primero = cohorte.firstOrNull() ?: return null
        val texto = MemoryAbsorbPrompt.clean(extraido.resumen) ?: return null
        val tema = extraido.topic.ifBlank { primero.topic }.take(60)
        val claves = clavesJson(extraido.keys + cohorte.flatMap { claves(it) })
        return CharacterMemory(
            characterId = primero.characterId,
            content = texto,
            createdAt = now,
            kind = "summary",
            topic = tema,
            source = "derived",
            // Anti-eco: un consolidado no pertenece a la sesión en que se creó.
            sourceConversationId = "",
            keysJson = claves,
            importance = maxOf(extraido.importance, cohorte.maxOf { it.importance }).coerceIn(1, 10),
            updatedAt = now,
            valid = true,
            // El más reciente de los originales es el que este recuerdo deja sin efecto.
            supersedesId = cohorte.maxByOrNull { it.createdAt }?.id.orEmpty(),
        )
    }

    /** Ids de grafo que deben desaparecer cuando un recuerdo deja de estar vigente. */
    fun nodosAEliminar(ids: List<String>): List<String> = ids.flatMap { listOf("m|$it", "r|$it") }

    /** Solo se invalidan los originales si el consolidado se guardó de verdad (dedupe incluido). */
    fun plan(
        nuevo: CharacterMemory,
        cohorte: List<CharacterMemory>,
        guardado: Boolean,
    ): PlanConsolidacion? =
        if (!guardado) null else PlanConsolidacion(nuevo, cohorte.map { it.id })
}
