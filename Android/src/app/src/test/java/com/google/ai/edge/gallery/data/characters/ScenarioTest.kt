package com.google.ai.edge.gallery.data.characters

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioTest {
    private val members = listOf(
        ScenePromptBuilder.Member("Ana", "{{char}} es médica.", "seria"),
        ScenePromptBuilder.Member("Luis", "Un ladrón."),
    )

    @Test fun promptHasEverything() {
        val p = ScenePromptBuilder.build("Noche", "Un hospital en ruinas.", members, "Héctor", "Un curioso.", narrator = true)
        assertTrue(p.contains("called \"Noche\""))
        assertTrue(p.contains("Scene: Un hospital en ruinas."))
        assertTrue(p.contains("- Ana: Ana es médica. Personality: seria"))
        assertTrue(p.contains("- Luis: Un ladrón."))
        assertTrue(p.contains("Narrator"))
        assertTrue(p.contains("Information about Héctor: Un curioso."))
        assertTrue(p.contains("Never speak, think or act for Héctor"))
    }

    @Test fun noNarrator() {
        val p = ScenePromptBuilder.build("", "", members, "", narrator = false)
        assertFalse(p.contains("Narrator"))
        assertTrue(p.contains("The User is User."))
    }

    @Test fun randomAvoidsLast() {
        repeat(50) { assertEquals("Luis", ScenePromptBuilder.pickRandom(listOf("Ana", "Luis"), "Ana", Random(it))) }
        assertEquals("Solo", ScenePromptBuilder.pickRandom(listOf("Solo"), "Solo"))
        assertNull(ScenePromptBuilder.pickRandom(emptyList(), null))
    }

    @Test fun turnInstructions() {
        assertNull(ScenePromptBuilder.turnInstruction(ScenePromptBuilder.Speaker.User, listOf("Ana"), null))
        assertTrue(ScenePromptBuilder.turnInstruction(ScenePromptBuilder.Speaker.Narrator, listOf("Ana"), null)!!.contains("Narrator"))
        assertTrue(ScenePromptBuilder.turnInstruction(ScenePromptBuilder.Speaker.Character("Luis"), listOf("Ana", "Luis"), null)!!.contains("Luis"))
        assertTrue(ScenePromptBuilder.turnInstruction(ScenePromptBuilder.Speaker.Random, listOf("Ana", "Luis"), "Ana", Random(1))!!.contains("Luis"))
    }

    @Test fun restrictionForReplyKeepsFirstSpeaker() {
        val r = ScenePromptBuilder.restrictionForReply("Narrator: llueve.\nAna: hola.", listOf("Ana", "Luis"))!!
        assertEquals(listOf(ScenePromptBuilder.NARRATOR), r.allowed)
        val a = ScenePromptBuilder.restrictionForReply("**Ana:** hola.", listOf("Ana", "Luis"))!!
        assertEquals(listOf("Ana"), a.allowed)
        assertNull(ScenePromptBuilder.restrictionForReply("sin encabezado", listOf("Ana")))
    }

    @Test fun filterDropsOtherSpeakers() {
        val r = ScenePromptBuilder.restriction(ScenePromptBuilder.Speaker.Character("Akiko"), listOf("KIP", "Akiko"))!!
        val raw = "Narrator: la puerta se abre.\nKIP: ¿quién anda ahí?\nAkiko: soy yo.\nKIP: no te creo."
        assertEquals("Akiko: soy yo.", ScenePromptBuilder.filterReply(raw, r))
        // El texto previo al primer encabezado se conserva (el modelo omitió el nombre).
        assertEquals("Hola.\nAkiko: soy yo.", ScenePromptBuilder.filterReply("Hola.\nKIP: x\nAkiko: soy yo.", r))
        // Varias líneas del hablante permitido y huecos colapsados.
        val multi = "KIP: a\n\n\nAkiko: uno\ndos\n\nKIP: b"
        assertEquals("Akiko: uno\ndos", ScenePromptBuilder.filterReply(multi, r))
    }

    @Test fun userTurnRestrictionExcludesUserSpeaker() {
        val r = ScenePromptBuilder.userTurnRestriction("Akiko", listOf("KIP", "Akiko"))!!
        assertEquals(listOf("KIP", ScenePromptBuilder.NARRATOR), r.allowed)
        assertTrue(r.instruction.contains("Do not write any line as Akiko"))
        val raw = "KIP: hola.\nAkiko: no debería salir.\nNarrator: el viento sopla."
        assertEquals("KIP: hola.\nNarrator: el viento sopla.", ScenePromptBuilder.filterReply(raw, r))

        val narrator = ScenePromptBuilder.userTurnRestriction(ScenePromptBuilder.NARRATOR, listOf("KIP", "Akiko"))!!
        assertEquals(listOf("KIP", "Akiko"), narrator.allowed)

        // "Yo": habla el usuario como su persona, sin restricción.
        assertNull(ScenePromptBuilder.userTurnRestriction(null, listOf("KIP")))
    }

    @Test fun jsonRoundTrip() {
        val s = Scenario(id = "1", name = "Escena \"uno\"", prompt = "línea1\nlínea2", tags = listOf("a", "b"), participantIds = listOf("x", "y"), narrator = false, updatedAt = 5L)
        val back = ScenarioJson.decode(ScenarioJson.encode(listOf(s)))
        assertEquals(listOf(s), back)
        assertNotEquals(emptyList<Scenario>(), back)
        assertEquals(emptyList<Scenario>(), ScenarioJson.decode("no es json"))
    }
}
