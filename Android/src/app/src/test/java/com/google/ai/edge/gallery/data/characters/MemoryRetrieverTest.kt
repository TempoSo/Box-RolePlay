package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrieverTest {
  private val now = 1_800_000_000_000L
  private val day = 24L * 60 * 60 * 1000

  private fun memoria(
    id: String,
    texto: String,
    claves: List<String>,
    createdAt: Long,
    importance: Int = 5,
    conversacion: String = "",
    accesos: Int = 0,
  ) =
    CharacterMemory(
      id = id,
      characterId = "ana",
      content = texto,
      createdAt = createdAt,
      keysJson = claves.joinToString(",", "[", "]") { "\"$it\"" },
      importance = importance,
      sourceConversationId = conversacion,
      accessCount = accesos,
    )

  @Test fun sinCoincidenciaNoLlegaAlUmbral() {
    val viejo = memoria("v", "Trabajó en el jardín", listOf("jardin"), now - 60 * day, 10, accesos = 100)
    val score = MemoryRetriever.puntuacion(viejo, MemoryRetriever.tokens("Hablemos de comida"), now)
    assertTrue(score <= 0.5501)
    val resultado =
      MemoryRetriever.seleccionar(
        listOf(viejo), "Ana", "Hablemos de comida", now, "",
        MemoryInjectionSettings(injectLastOnNewChat = false),
      )
    assertTrue(resultado.ids.isEmpty())
  }

  @Test fun eligePorPalabraClave() {
    val cafe = memoria("c", "Le gusta el café", listOf("cafe"), now - day)
    val jardin = memoria("j", "Podó el jardín", listOf("jardin"), now - 2 * day)
    val resultado =
      MemoryRetriever.seleccionar(
        listOf(cafe, jardin), "Ana", "¿Quieres café?", now, "",
        MemoryInjectionSettings(maxInjections = 1),
      )
    assertEquals(listOf("c"), resultado.ids)
    assertTrue(resultado.bloque.contains("Le gusta el café"))
    assertTrue(resultado.bloque.contains(MemoryPromptBuilder.GUARD))
  }

  @Test fun excluyeLaConversacionActual() {
    val actual = memoria("a", "Dijo café ayer", listOf("cafe"), now - day, conversacion = "conv-1")
    val otra = memoria("o", "Dijo café hace dos días", listOf("cafe"), now - 2 * day, conversacion = "conv-0")
    val resultado =
      MemoryRetriever.seleccionar(listOf(actual, otra), "Ana", "cafe", now, "conv-1")
    assertEquals(listOf("o"), resultado.ids)
  }

  @Test fun fallbackAlUltimoSiNingunoPasaElUmbral() {
    val viejo = memoria("v", "Jardín", listOf("jardin"), now - 5 * day)
    val reciente = memoria("r", "Café", listOf("cafe"), now - day)
    val resultado =
      MemoryRetriever.seleccionar(
        listOf(viejo, reciente), "Ana", "avión", now, "",
        MemoryInjectionSettings(minScore = 1.0),
      )
    assertEquals(listOf("r"), resultado.ids)
    assertTrue(resultado.traza.contains("fallback"))
  }

  @Test fun firmaEstableYAntiguedadSeOmiteDespuesDeTreintaDias() {
    val viejo = memoria("v", "Se mudó lejos", listOf("mudanza"), now - 60 * day)
    val ajustes = MemoryInjectionSettings()
    val a = MemoryRetriever.seleccionar(listOf(viejo), "Ana", "mudanza", now, "", ajustes)
    val b = MemoryRetriever.seleccionar(listOf(viejo), "Ana", "mudanza", now + 5_000, "", ajustes)
    assertEquals(a.firma, b.firma)
    assertEquals(a.bloque, b.bloque)
    assertFalse(a.bloque.contains("hace "))
    assertEquals("hace 3 días", MemoryRetriever.etiquetaTiempo(now - 3 * day, now))
  }

  @Test fun quitaElBloqueCongeladoDeChatsAntiguos() {
    val prompt =
      "Eres Ana.\n\nAna remembers previously:\n[1 day ago] algo\n${MemoryPromptBuilder.GUARD}\n\n[Example Chat]\nAna: hola"
    val limpio = MemoryRetriever.quitaBloqueAntiguo(prompt)
    assertFalse(limpio.contains("remembers previously"))
    assertTrue(limpio.contains("[Example Chat]"))
    assertTrue(limpio.contains("Eres Ana."))
  }

  @Test fun claveDerivadaDelContenidoCuandoNoHayJSON() {
    val sinClaves = memoria("s", "Ana prefiere respuestas cortas en español", emptyList(), now)
    assertTrue(MemoryRetriever.claves(sinClaves).contains("prefiere"))
  }
}
