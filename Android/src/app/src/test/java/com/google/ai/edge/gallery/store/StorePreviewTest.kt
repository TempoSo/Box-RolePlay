package com.google.ai.edge.gallery.store

import com.google.ai.edge.gallery.data.characters.CharacterCardException
import com.google.ai.edge.gallery.data.characters.toCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La ficha previa se lee de los bytes descargados sin crear id ni avatar en disco. */
class StorePreviewTest {
  private val card = StoreCard("chub", "u/p", "Layla", "Una tarjeta", "u", listOf("amiga"), "", 12, 900, false)

  private val json = """
    {
      "spec": "chara_card_v2",
      "spec_version": "2.0",
      "data": {
        "name": "Layla",
        "description": "Una chica curiosa",
        "personality": "Timida pero directa",
        "scenario": "Una cafeteria",
        "first_mes": "Hola, {{user}}",
        "alternate_greetings": ["Buenas", "¿Otra vez por aqui?"],
        "tags": ["amiga", "cafe"]
      }
    }
  """.trimIndent().toByteArray()

  @Test fun previewLeeLaFichaSinGuardarla() {
    val preview = CharacterStore.preview(card, json)
    val c = preview.character

    assertEquals("Layla", c.name)
    assertEquals("Una chica curiosa", c.description)
    assertEquals("Timida pero directa", c.personality)
    assertEquals("Una cafeteria", c.scenario)
    assertEquals("Hola, {{user}}", c.firstMessage)
    assertEquals(listOf("Buenas", "¿Otra vez por aqui?"), c.toCard().alternateGreetings)
    assertEquals(listOf("amiga", "cafe"), c.toCard().tags)
    assertEquals("", c.id)
    assertEquals("", c.avatarPath)
    assertTrue(preview.bytes.contentEquals(json))
  }

  @Test(expected = CharacterCardException::class)
  fun previewRechazaUnaTarjetaSinNombre() {
    CharacterStore.preview(card, """{"spec":"chara_card_v2","data":{"description":"sin nombre"}}""".toByteArray())
  }
}
