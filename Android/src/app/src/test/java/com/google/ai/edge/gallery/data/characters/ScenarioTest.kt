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

    @Test fun jsonRoundTrip() {
        val s = Scenario(id = "1", name = "Escena \"uno\"", prompt = "línea1\nlínea2", tags = listOf("a", "b"), participantIds = listOf("x", "y"), narrator = false, updatedAt = 5L)
        val back = ScenarioJson.decode(ScenarioJson.encode(listOf(s)))
        assertEquals(listOf(s), back)
        assertNotEquals(emptyList<Scenario>(), back)
        assertEquals(emptyList<Scenario>(), ScenarioJson.decode("no es json"))
    }
}
