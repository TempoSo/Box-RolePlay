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
            ". Never speak, think or act for $user. Reply in the language $user writes in."
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
     */
    fun turnInstruction(speaker: Speaker, names: List<String>, last: String?, random: Random = Random.Default): String? =
        when (speaker) {
            Speaker.User -> null
            Speaker.Narrator -> "(Next message: write only as the $NARRATOR.)"
            is Speaker.Character -> "(Next message: write only as ${speaker.name}.)"
            Speaker.Random -> pickRandom(names, last, random)?.let { "(Next message: write only as $it.)" }
        }
}
