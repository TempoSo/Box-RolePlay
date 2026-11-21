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

    // BUG-037: el modelo escribe "Narrador:" aunque el nombre interno sea "Narrator".
    @Test fun filterRecognisesSpanishNarratorAlias() {
        val names = listOf("KIP", "Tohru")
        val soloKip = ScenePromptBuilder.restriction(ScenePromptBuilder.Speaker.Character("KIP"), names)!!
        // El bloque del narrador, que no está permitido, se elimina aunque venga en español.
        assertEquals("KIP: hola", ScenePromptBuilder.filterReply("KIP: hola\nNarrador: se ríen\nse oye un ruido", soloKip))
        assertEquals("KIP: hola", ScenePromptBuilder.filterReply("KIP: hola\nNarradora: se ríen", soloKip))
        // Cuando el narrador sí está permitido, se conserva en cualquiera de sus formas.
        val soloNarrador = ScenePromptBuilder.restriction(ScenePromptBuilder.Speaker.Narrator, names)!!
        assertEquals("Narrador: llueve\nlejos truena", ScenePromptBuilder.filterReply("Narrador: llueve\nlejos truena\nKIP: hola", soloNarrador))
        // Sin narrador en la escena, "Narrador:" no es un hablante conocido y no se recorta.
        val sinNarrador = ScenePromptBuilder.TurnRestriction(allowed = listOf("KIP"), known = names, instruction = "")
        assertEquals("KIP: hola\nNarrador: texto", ScenePromptBuilder.filterReply("KIP: hola\nNarrador: texto", sinNarrador))
    }

    // BUG-033: sin texto del usuario, el turno lleva delante el contexto escrito y aún no enviado.
    @Test fun engineInputCarriesContextTail() {
        val inst = "(Next message: write ONLY as KIP.)"
        assertEquals("hola\n\n$inst", ScenePromptBuilder.engineInput("hola", inst, listOf("ignorado")))
        assertEquals("KIP: a\n\nTohru: b\n\n$inst", ScenePromptBuilder.engineInput("", inst, listOf("KIP: a", "Tohru: b")))
        assertEquals(inst, ScenePromptBuilder.engineInput("", inst, emptyList()))
        assertEquals("solo texto", ScenePromptBuilder.engineInput("solo texto", null, listOf("x")))
    }

    // BUG-035/033: las escenas antiguas (sin los campos nuevos) siguen leyéndose; persona activa y sin respuesta automática.
    @Test fun oldScenesGetNewDefaults() {
        val old = """[{"id":"1","name":"A","prompt":"","tags":[],"participants":[],"narrator":true,"updatedAt":1}]"""
        val s = ScenarioJson.decode(old).single()
        assertTrue(s.usePersona)
        assertFalse(s.replyToUser)
        val flipped = Scenario(id = "2", name = "B", usePersona = false, replyToUser = true, updatedAt = 3L)
        assertEquals(listOf(flipped), ScenarioJson.decode(ScenarioJson.encode(listOf(flipped))))
    }

    // BUG-036: de cada ficha el prompt de escena solo usa nombre, descripción y personalidad.
    @Test fun sceneUsesOnlyNameDescriptionPersonality() {
        val p = ScenePromptBuilder.build("", "", members, "Héctor")
        val block = p.substringAfter("Characters you control:\n").substringBefore("\n\n")
        assertEquals("- Ana: Ana es médica. Personality: seria\n- Luis: Un ladrón.", block)
    }
}
