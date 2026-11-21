package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileNameTest {
    @Test fun keepsNormalNamesAndAddsExtension() {
        assertEquals("Akiko.json", ShareFileName.forCharacter("Akiko"))
        assertEquals("Dr. Ana Pérez.json", ShareFileName.forCharacter("  Dr. Ana   Pérez "))
    }

    @Test fun removesForbiddenCharacters() {
        assertEquals("a_b_c.json", ShareFileName.forCharacter("a/b:c"))
        assertEquals("personaje.json", ShareFileName.forCharacter("  ...  "))
    }

    @Test fun cutsLongNames() {
        assertEquals(60 + 5, ShareFileName.forCharacter("x".repeat(200)).length)
    }
}
