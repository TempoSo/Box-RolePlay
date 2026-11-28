package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGroupsTest {
    @Test fun roundTrip() {
        val l = listOf(CharacterGroup("1", "Asistentes", listOf("a", "b")), CharacterGroup("2", "Vacío"))
        assertEquals(l, CharacterGroups.decode(CharacterGroups.encode(l)))
        assertTrue(CharacterGroups.decode("x").isEmpty())
    }
    @Test fun createSortsAndRejectsDuplicates() {
        var l = CharacterGroups.create(emptyList(), "Zeta")
        l = CharacterGroups.create(l, "alfa")
        l = CharacterGroups.create(l, " ZETA ")
        l = CharacterGroups.create(l, "   ")
        assertEquals(listOf("alfa", "Zeta"), l.map { it.name })
    }
    @Test fun membershipAndPrune() {
        var l = CharacterGroups.create(emptyList(), "G")
        val id = l.first().id
        l = CharacterGroups.setMember(l, id, "c1", true)
        l = CharacterGroups.setMember(l, id, "c1", true)
        l = CharacterGroups.setMember(l, id, "c2", true)
        assertEquals(listOf("c1", "c2"), l.first().characterIds)
        assertEquals(setOf(id), CharacterGroups.groupIdsOf(l, "c2"))
        assertEquals(listOf("c2"), CharacterGroups.prune(l, setOf("c2")).first().characterIds)
        l = CharacterGroups.setMember(l, id, "c1", false)
        assertEquals(listOf("c2"), l.first().characterIds)
        assertTrue(CharacterGroups.delete(l, id).isEmpty())
    }
}
