package com.google.ai.edge.gallery.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorePagerTest {
  @Test fun ventanaAlrededorDeLaActual() {
    assertEquals(listOf(1, 2, 3, 4, null, 141), StorePager.items(1, 141, true))
    assertEquals(listOf(1, null, 6, 7, 8, null, 141), StorePager.items(7, 141, true))
    assertEquals(listOf(1, null, 138, 139, 140, 141), StorePager.items(140, 141, true))
    assertEquals(listOf(1, 2, 3, 4, 5), StorePager.items(3, 5, true))
  }

  @Test fun sinTotalMuestraHastaLaSiguiente() {
    assertEquals(listOf(1, 2), StorePager.items(1, null, true))
    assertEquals(listOf(1, null, 7, 8, 9), StorePager.items(9, null, false))
  }

  @Test fun totalDePaginas() {
    assertEquals(101, StorePager.totalPages(2001, 20))
    assertEquals(null, StorePager.totalPages(null, 20))
    assertEquals(StorePager.MAX_PAGES, StorePager.totalPages(999_999, 20))
  }

  @Test fun aleatorioNoRepiteLaActual() {
    repeat(50) { assertNotEquals(3, StorePager.randomPage(3, 10)) }
  }

  @Test fun aleatorioDePersonajeEvitaLosYaAnadidos() {
    fun card(id: String) = StoreCard("chub", id, id, "", "u", emptyList(), "", 0, 0, false)
    val cards = listOf(card("a"), card("b"), card("c"))
    repeat(20) { assertEquals("c", StorePager.randomCard(cards, setOf("a", "b"), kotlin.random.Random(it))?.id) }
    assertNull(StorePager.randomCard(cards, setOf("a", "b", "c")))
    assertNull(StorePager.randomCard(emptyList(), emptySet()))
  }
}
