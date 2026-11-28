package com.google.ai.edge.gallery.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TagChipsTest {
    @Test fun collectIsSortedAndDeduplicatesIgnoringCase() {
        assertEquals(
            listOf("Fantasía", "romance", "terror"),
            TagChips.collect(listOf(" romance ", "Fantasía"), listOf("terror", "ROMANCE", "")),
        )
    }

    @Test fun toggleAddsAndRemovesWithoutDuplicates() {
        assertEquals("fantasía, romance", TagChips.toggle("fantasía", "romance"))
        assertEquals("fantasía", TagChips.toggle("fantasía, romance", "ROMANCE"))
        assertEquals("fantasía, ciencia ficción", TagChips.toggle("fantasía", "ciencia ficción"))
        assertEquals("", TagChips.toggle("romance", "romance"))
        assertEquals("fantasía", TagChips.toggle("fantasía", "  "))
    }
}
