package com.google.ai.edge.gallery.data.characters

import kotlin.random.Random

/**
 * Escenas de roleplay con varios personajes: la IA lleva a todos los personajes de la escena (y
 * opcionalmente al narrador) y el usuario habla como su persona. Lógica pura y sin Android.
 */
object ScenePromptBuilder {
    const val NARRATOR = "Narrator"

    class Member(val name: String, val description: String = "", val personality: String = "")

    /** Quién habla en el siguiente turno. */
    sealed class Speaker {
        /** Elige la app al azar entre los personajes (como "Aleatorio" de Layla). */
        object Random : Speaker()
        object Narrator : Speaker()
        /** Habla el propio usuario (su persona). */
        object User : Speaker()
        class Character(val name: String) : Speaker()
    }

    fun build(
        sceneName: String,
        scenePrompt: String,
        members: List<Member>,
        userName: String,
        userDescription: String = "",
        narrator: Boolean = true,
    ): String {
        val user = userName.trim().ifEmpty { CharacterPromptBuilder.DEFAULT_USER_NAME }
        val parts = mutableListOf<String>()
        val title = sceneName.trim()
        parts += "This is a multi-character roleplay" + if (title.isNotEmpty()) " called \"$title\"." else "."
        CharacterPromptBuilder.applyMacros(scenePrompt, "", user).trim().takeIf { it.isNotEmpty() }
            ?.let { parts += "Scene: $it" }

        val lines = members.map { m ->
            val name = m.name.trim().ifEmpty { "Character" }
            fun r(t: String) = CharacterPromptBuilder.applyMacros(t, name, user).trim()
            buildString {
                append("- $name")
                r(m.description).takeIf { it.isNotEmpty() }?.let { append(": $it") }
                r(m.personality).takeIf { it.isNotEmpty() }?.let { append(" Personality: $it") }
            }
        }
        if (lines.isNotEmpty()) parts += "Characters you control:\n" + lines.joinToString("\n")
        if (narrator) parts += "You may also write as the $NARRATOR to describe the setting, events and actions."
        userDescription.trim().takeIf { it.isNotEmpty() }
            ?.let { parts += "Information about $user: ${CharacterPromptBuilder.applyMacros(it, "", user)}" }
        parts += "The User is $user. Write every message as lines that start with the speaker's name and a colon, " +
            "like \"Name: text\". Only write for the characters listed" +
            (if (narrator) " and the $NARRATOR" else "") +
            ". Never speak, think or act for $user. " +
            "If a message from the User starts with \"Name:\" (the User is speaking as that name), " +
            "never write lines for that same name in your reply; answer as the others. " +
            "Reply in the language $user writes in."
        return parts.joinToString("\n\n")
    }

    /** Elige un personaje al azar, evitando repetir al que habló en último lugar si hay más de uno. */
    fun pickRandom(names: List<String>, last: String?, random: Random = Random.Default): String? {
        if (names.isEmpty()) return null
        val pool = if (names.size > 1) names.filterNot { it.equals(last, ignoreCase = true) } else names
        return pool[random.nextInt(pool.size)]
    }

    /**
     * Instrucción oculta para el turno (se añade tras el mensaje del usuario). Null = no hace falta:
     * habla el usuario y la IA responde libremente.
     *
     * BUG-003/BUG-004: ya no se manda como mensaje visible; se inyecta al prompt del turno. La
     * restricción es fuerte (una sola respuesta, solo ese hablante) porque el system prompt solo
     * pedía "Only write for the characters listed" y el modelo lo ignoraba.
     */
    fun turnInstruction(speaker: Speaker, names: List<String>, last: String?, random: Random = Random.Default): String? =
        when (speaker) {
            Speaker.User -> null
            Speaker.Narrator -> "(Next message: write ONLY as the $NARRATOR. One single response, formatted as \"$NARRATOR: text\". Do not write as any character and do not add lines for anyone else.)"
            is Speaker.Character ->
                "(Next message: write ONLY as ${speaker.name}. One single response, formatted as \"${speaker.name}: text\". Do not write as any other character, do not add the $NARRATOR, and do not describe events outside ${speaker.name}'s words and actions.)"
            Speaker.Random -> pickRandom(names, last, random)?.let { turnInstruction(Speaker.Character(it), names, last, random) }
        }

    /**
     * Restricción del turno de una escena: quién puede hablar y la instrucción que se inyecta al
     * prompt sin crear mensaje. Null = sin restricción (habla el usuario, chat 1 a 1 o respuesta libre).
     */
    data class TurnRestriction(
        /** Hablantes permitidos en la respuesta. */
        val allowed: List<String>,
        /** Todos los hablantes conocidos de la escena (personajes + narrador). */
        val known: List<String>,
        /** Texto que se concatena al prompt del turno (nunca se muestra ni se persiste). */
        val instruction: String,
    )

    /** Restricción para un hablante concreto. [speaker] debe ser el hablante ya resuelto. */
    fun restriction(speaker: Speaker, names: List<String>, narrator: Boolean = true): TurnRestriction? {
        val allowed =
            when (speaker) {
                Speaker.User -> return null
                Speaker.Narrator -> if (narrator) listOf(NARRATOR) else return null
                is Speaker.Character -> listOf(speaker.name)
                Speaker.Random -> return null
            }
        val known = (names + NARRATOR).distinct()
        val instruction = turnInstruction(speaker, names, null) ?: return null
        return TurnRestriction(allowed = allowed, known = known, instruction = instruction)
    }

    /**
     * Restricción cuando escribe el usuario: la IA contesta como los demás. [speaker] es el hablante
     * elegido en la barra de escena (null = la persona del usuario: sin restricción). El texto que el
     * usuario ya firmó con `Nombre:` no debe repetirse en la respuesta (lo pide el system prompt).
     */
    fun userTurnRestriction(
        speaker: String?,
        names: List<String>,
        narrator: Boolean = true,
    ): TurnRestriction? {
        val who = speaker?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val known = (names + NARRATOR).distinct()
        val isNarrator = who.equals(NARRATOR, ignoreCase = true)
        // Nunca se permite repetir al hablante del usuario; el resto de la escena sí.
        val allowed =
            if (isNarrator) names.distinct()
            else
                names.filterNot { it.equals(who, ignoreCase = true) } +
                    (if (narrator) listOf(NARRATOR) else emptyList())
        if (allowed.isEmpty()) return null
        val list = allowed.joinToString(", ")
        val instruction =
            if (isNarrator) {
                "(Next message: the User already wrote as the $NARRATOR. Do not narrate. Write ONLY as the " +
                    "characters of the scene ($list), one single response, each block starting with " +
                    "\"Name:\". Do not write as the $NARRATOR.)"
            } else {
                "(Next message: the User already wrote as $who. Do not write any line as $who. Write ONLY " +
                    "as $list, one single response, each block starting with \"Name:\".)"
            }
        return TurnRestriction(allowed = allowed, known = known, instruction = instruction)
    }

    /**
     * Restricción deducida de una respuesta anterior (para regenerar/swipe): se reutiliza el primer
     * hablante conocido que aparezca como `Nombre:`. Null si la respuesta no tiene encabezados.
     */
    fun restrictionForReply(reply: String, names: List<String>, narrator: Boolean = true): TurnRestriction? {
        if (reply.isBlank()) return null
        val known = (names + NARRATOR).distinct()
        val first =
            reply.replace("\r\n", "\n").split("\n").firstNotNullOfOrNull { headerSpeaker(it, known) }
                ?: return null
        return restriction(
            speaker = if (first == NARRATOR) Speaker.Narrator else Speaker.Character(first),
            names = names,
            narrator = narrator,
        )
    }

    /**
     * Post-filtrado defensivo de la respuesta (BUG-004): el formato es `Nombre: texto`. Elimina las
     * líneas/bloques de hablantes conocidos que no estén permitidos en el turno y limpia los huecos.
     * El texto que aparece antes del primer encabezado se conserva (el modelo puede haber omitido
     * el nombre); solo se descartan los bloques explícitos de otros hablantes.
     */
    fun filterReply(text: String, restriction: TurnRestriction): String {
        if (text.isEmpty() || restriction.allowed.isEmpty()) return text
        val lines = text.replace("\r\n", "\n").split("\n")
        val out = StringBuilder()
        var keep = true
        for (line in lines) {
            val name = headerSpeaker(line, restriction.known)
            if (name != null) keep = restriction.allowed.any { it.equals(name, ignoreCase = true) }
            if (!keep) continue
            if (line.isBlank() && out.isNotEmpty() && out.endsWith("\n\n")) continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(line)
        }
        return out.toString().trim().replace(Regex("\n{3,}"), "\n\n")
    }

    /** Nombre del hablante si la línea empieza por `Nombre:` (también `**Nombre:**`) y es conocido. */
    private fun headerSpeaker(line: String, known: List<String>): String? {
        // `**Ana:**` y `**Ana**:` son equivalentes: se ignoran los `*` para detectar el encabezado.
        val match = HEADER.find(line.replace("*", "")) ?: return null
        val name = match.groupValues[1].trim()
        if (name.isEmpty()) return null
        return known.firstOrNull { it.equals(name, ignoreCase = true) }
    }

    /** `Nombre:` al principio de la línea, con `*` opcionales de markdown. */
    private val HEADER = Regex("^\\s*\\*{0,2}([\\p{L}\\p{N}][\\p{L}\\p{N}' ._-]{0,39})\\*{0,2}:(\\s|$)")
}
