package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDisplayTest {
  @Test fun jsonAntiguoSeMuestraComoResumen() {
    val json =
      """
      { "summary": "Héctor confesó su timidez al hablar con personas nuevas, algo que lo hace inseguro sobre cómo abordar las interacciones sociales. Claire validó su sentimiento, explicando que la timidez no es una respuesta conductual ante lo desconocido.",
        "topic": "Timidez y Dinámicas Interpersonales",
        "keys": ["Timidez", "Interacciones sociales", "Validación", "Respuesta conductual"],
        "importance": 7 }
      """.trimIndent()
    val memoria = CharacterMemory(id = "m", characterId = "c", content = json)
    val detalle = memoria.detalleParaUI()
    assertTrue(detalle.esJson)
    assertEquals("Timidez y Dinámicas Interpersonales", detalle.topic)
    assertTrue(detalle.summary.startsWith("Héctor confesó su timidez"))
    assertFalse(detalle.summary.contains("{"))
    assertEquals(listOf("Timidez", "Interacciones sociales", "Validación", "Respuesta conductual"), detalle.keys)
    assertEquals(7, detalle.importance)
  }

  @Test fun textoPlanoSeRespetaSinConvertir() {
    val memoria = CharacterMemory(id = "m", characterId = "c", content = "Le gusta el café con leche.")
    val detalle = memoria.detalleParaUI()
    assertFalse(detalle.esJson)
    assertEquals("Le gusta el café con leche.", detalle.summary)
    assertEquals("", detalle.topic)
    assertEquals(5, detalle.importance)
    assertTrue(detalle.keys.isNotEmpty())
  }

  @Test fun jsonRotoNoRompeLaPantalla() {
    val memoria = CharacterMemory(id = "m", characterId = "c", content = "{ \"summary\": roto")
    val detalle = memoria.detalleParaUI()
    assertFalse(detalle.esJson)
    assertEquals("{ \"summary\": roto", detalle.summary)
  }

  @Test fun prevalecenLosMetadatosRealesSobreElJSON() {
    val json = """{"summary":"Ana se mudó lejos de la ciudad y extraña a su hermana.","topic":"Del JSON","keys":["json"],"importance":9}"""
    val memoria =
      CharacterMemory(
        id = "m", characterId = "c", content = json,
        topic = "Tema real", keysJson = """["cafe","mudanza"]""", importance = 3,
      )
    val detalle = memoria.detalleParaUI()
    assertEquals("Tema real", detalle.topic)
    assertEquals(listOf("cafe", "mudanza"), detalle.keys)
    assertEquals(3, detalle.importance)
  }
}
