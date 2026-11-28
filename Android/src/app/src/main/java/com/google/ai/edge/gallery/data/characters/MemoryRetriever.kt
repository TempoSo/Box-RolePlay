package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Preferencias de inyección de memoria por personaje (equivalente local a los ajustes de Layla). */
data class MemoryInjectionSettings(
  val enabled: Boolean = true,
  val maxInjections: Int = 2,
  val minScore: Double = 0.60,
  val includeRelativeTime: Boolean = true,
  val injectLastOnNewChat: Boolean = true,
  val excludeActiveSession: Boolean = true,
)

/**
 * Recuperación de recuerdos para el prompt. Sin embeddings y sin red: combine coincidencia de
 * palabras clave, recencia, importancia declarada y refuerzo por uso. Determinista para no romper
 * la caché de prefijo de llama.cpp: a igualdad de entradas, el bloque resultante es idéntico.
 */
object MemoryRetriever {
  private const val DAY = 24L * 60 * 60 * 1000
  private const val MAX_ENTRY_CHARS = 400
  private val MARCA = Regex("\\p{M}")
  private val NO_PALABRA = Regex("[^\\p{L}\\p{N}\\s]")
  private val ESPACIOS = Regex("\\s+")
  private val BLOQUE_ANTIGUO = Regex("(?s)\\n\\n[^\\n]* remembers previously:\\n.*?(?=\\n\\n|\\z)")

  /** Español e inglés: son los idiomas con los que se escriben las tarjetas y los recuerdos. */
  private val STOPWORDS =
    setOf(
      "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que",
      "por", "para", "con", "sin", "en", "a", "al", "se", "su", "sus", "mi", "mis", "tu",
      "tus", "lo", "le", "les", "es", "era", "son", "fue", "ha", "habia", "había", "no",
      "si", "sí", "me", "te", "nos", "the", "and", "for", "with", "that", "this", "was",
      "were", "has", "have", "had", "its", "his", "her", "you", "not", "from", "into",
    )

  data class Resultado(
    val bloque: String,
    val ids: List<String>,
    /** Cambia solo si cambian los recuerdos elegidos o el día: sirve para no recalcular el prefijo. */
    val firma: String,
    val traza: String,
    /** Notas puente inyectadas; nunca se pasan a `bumpAccess` porque no son recuerdos Room. */
    val idsNotas: List<String> = emptyList(),
  )

  /** Normaliza like -> like, café -> cafe, sin puntuación ni espacios dobles. */
  fun normaliza(texto: String): String =
    Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
      .replace(MARCA, "")
      .replace(NO_PALABRA, " ")
      .replace(ESPACIOS, " ")
      .trim()

  /** Palabras con sentido de la consulta; se ignoran stopwords y tokens de una sola letra. */
  fun tokens(texto: String): List<String> =
    normaliza(texto).split(' ').filter { it.length >= 2 && it !in STOPWORDS }.distinct()

  /** Las claves guardadas por el archivista; si no hay, se derivan del propio resumen. */
  fun claves(memoria: CharacterMemory): List<String> {
    val guardadas =
      runCatching {
        Json.parseToJsonElement(memoria.keysJson).jsonArray.mapNotNull {
          it.jsonPrimitive.contentOrNull?.let(::normaliza)
        }
      }.getOrDefault(emptyList()).filter { it.length >= 2 && it !in STOPWORDS }.distinct()
    if (guardadas.isNotEmpty()) return guardadas.take(5)
    return tokens(memoria.content).sortedByDescending { it.length }.take(5)
  }

  /** "hoy", "ayer", "hace 3 días"; se omite a partir de los 30 días como en Layla. */
  fun etiquetaTiempo(createdAt: Long, now: Long): String {
    val dias = ((now - createdAt).coerceAtLeast(0) / DAY)
    return when (dias) {
      0L -> "hoy"
      1L -> "ayer"
      else -> if (dias < 30) "hace $dias días" else ""
    }
  }

  /**
   * score = 0,45·similitud + 0,30·recencia + 0,15·importancia + 0,10·uso.
   * Sin coincidencia de claves el máximo es 0,55, por debajo del umbral por defecto (0,60):
   * un recuerdo que no tiene nada que ver con el mensaje nunca entra en el prompt.
   */
  fun puntuacion(memoria: CharacterMemory, consulta: List<String>, now: Long): Double {
    val claves = claves(memoria)
    val sim = if (claves.isEmpty()) 0.0 else consulta.intersect(claves.toSet()).size.toDouble() / claves.size
    val dias = ((now - memoria.createdAt).coerceAtLeast(0) / DAY).toDouble()
    val rec = Math.pow(0.5, dias / 30.0)
    val imp = (memoria.importance.coerceIn(1, 10)).toDouble() / 10.0
    val uso = kotlin.math.min(1.0, kotlin.math.log2(1.0 + memoria.accessCount.coerceAtLeast(0)) / 4.0)
    return (0.45 * sim + 0.30 * rec + 0.15 * imp + 0.10 * uso).coerceIn(0.0, 1.0)
  }

  /** Quita el bloque que los chats antiguos tenían congelado en su system prompt. */
  fun quitaBloqueAntiguo(prompt: String): String = BLOQUE_ANTIGUO.replace(prompt, "").trimEnd()

  /** Coloca la memoria antes del último párrafo para que la frase de identidad siga siendo la última. */
  fun injectIntoPrompt(prompt: String, bloque: String): String {
    if (bloque.isBlank()) return prompt
    val corte = prompt.lastIndexOf("\n\n")
    return if (corte < 0) "$prompt\n\n$bloque"
    else prompt.substring(0, corte) + "\n\n" + bloque + prompt.substring(corte)
  }

  /** Las notas puente también invalidan la firma si se editan o se borran. */
  private fun sufijoNotas(notas: List<RawMemory>): String =
    if (notas.isEmpty()) "" else "|n:" + notas.joinToString(",") { "${it.id}:${it.text.hashCode()}" }

  /**
   * Arma el bloque con dos partes: los recuerdos destilados (scoring) y, detrás, las notas puente
   * recién absorbidas. La guardia va una sola vez al final y no hay líneas en blanco dentro del
   * bloque para que `quitaBloqueAntiguo` pueda retirarlo entero en el siguiente turno.
   */
  private fun armarBloque(
    nombre: String,
    lineas: List<String>,
    notas: List<RawMemory>,
    now: Long,
    incluirTiempo: Boolean,
    maxNotasChars: Int,
  ): String =
    buildString {
      append("${nombre.trim().ifEmpty { "Character" }} remembers previously:\n")
      append(lineas.joinToString("\n"))
      if (notas.isEmpty()) {
        append('\n')
      } else {
        append("\nRecent notes:\n")
        notas.forEach { nota ->
          val texto = nota.text.trim().let {
            if (it.length > maxNotasChars) it.take(maxNotasChars).trimEnd() + "…" else it
          }
          val etiqueta = if (incluirTiempo) etiquetaTiempo(nota.createdAt, now) else ""
          if (etiqueta.isNotEmpty()) append("[$etiqueta] ")
          append(texto).append('\n')
        }
      }
      append(MemoryPromptBuilder.GUARD)
    }

  /**
   * Elige los recuerdos que entran este turno y arma el bloque listo para el prompt. El orden es
   * estable (puntuación, fecha, id) y la firma solo cambia si cambia el conjunto o el día.
   */
  fun seleccionar(
    memorias: List<CharacterMemory>,
    nombrePersonaje: String,
    textoConsulta: String,
    now: Long,
    conversacionActual: String,
    ajustes: MemoryInjectionSettings = MemoryInjectionSettings(),
    maxChars: Int = MemoryPromptBuilder.DEFAULT_MAX_CHARS,
    /** Notas crudas recientes, más nueva primero; se inyectan aparte del scoring de recuerdos. */
    notasRecientes: List<RawMemory> = emptyList(),
    maxNotasRecientes: Int = NOTAS_PUENTE,
    maxNotasChars: Int = 400,
  ): Resultado {
    val consulta = tokens(textoConsulta)
    // Las notas puente no compiten con los recuerdos: van detrás, con su propio tope y presupuesto.
    val notas = notasRecientes.paraInyectar(maxNotasRecientes)
    val sufijo = sufijoNotas(notas)
    val trazaNotas =
      if (notas.isEmpty()) "" else ", notas=${notas.joinToString(",") { it.id.take(8) }}"
    val candidatos =
      memorias.filter { it.valid && it.content.isNotBlank() }
        .filterNot { ajustes.excludeActiveSession && conversacionActual.isNotBlank() && it.sourceConversationId == conversacionActual }
    val evaluados =
      candidatos.map { it to puntuacion(it, consulta, now) }
        .sortedWith(compareByDescending<Pair<CharacterMemory, Double>> { it.second }
          .thenByDescending { it.first.createdAt }.thenBy { it.first.id })

    val umbral = ajustes.minScore.coerceIn(0.0, 1.0)
    val tope = ajustes.maxInjections.coerceIn(0, 20)
    var elegidos = evaluados.filter { it.second >= umbral }.take(tope)
    var fallback = false
    if (elegidos.isEmpty() && ajustes.injectLastOnNewChat && candidatos.isNotEmpty()) {
      elegidos = listOf(candidatos.maxWith(compareBy<CharacterMemory> { it.createdAt }.thenBy { it.id }) to 1.0)
      fallback = true
    }
    if (elegidos.isEmpty()) {
      return Resultado(
        bloque =
          if (notas.isEmpty()) ""
          else armarBloque(nombrePersonaje, emptyList(), notas, now, ajustes.includeRelativeTime, maxNotasChars),
        ids = emptyList(),
        idsNotas = notas.map { it.id },
        firma = "vacio|${now / DAY}$sufijo",
        traza = "LTM: evaluados=${candidatos.size}, umbral=$umbral, entró ninguno$trazaNotas",
      )
    }

    var presupuesto = maxChars.coerceAtLeast(0)
    val lineas = mutableListOf<String>()
    for ((memoria, score) in elegidos) {
      if (presupuesto <= 0) break
      val etiqueta = if (ajustes.includeRelativeTime) etiquetaTiempo(memoria.createdAt, now) else ""
      val prefijo = if (etiqueta.isEmpty()) "" else "[$etiqueta] "
      var texto = memoria.content.trim()
      if (texto.length > MAX_ENTRY_CHARS) texto = texto.take(MAX_ENTRY_CHARS).trimEnd() + "…"
      var linea = prefijo + texto
      if (linea.length > presupuesto) {
        val disponible = presupuesto - prefijo.length
        if (disponible <= 1) break
        linea = prefijo + texto.take(disponible - 1).trimEnd() + "…"
      }
      lineas.add(linea)
      presupuesto -= linea.length + 1
    }
    if (lineas.isEmpty()) {
      return Resultado(
        bloque =
          if (notas.isEmpty()) ""
          else armarBloque(nombrePersonaje, emptyList(), notas, now, ajustes.includeRelativeTime, maxNotasChars),
        ids = emptyList(),
        idsNotas = notas.map { it.id },
        firma = "vacio|${now / DAY}$sufijo",
        traza = "LTM: evaluados=${candidatos.size}, umbral=$umbral, entró ninguno$trazaNotas",
      )
    }

    val ids = elegidos.take(lineas.size).map { it.first.id }
    val bloque =
      armarBloque(nombrePersonaje, lineas, notas, now, ajustes.includeRelativeTime, maxNotasChars)
    val detalle = elegidos.take(lineas.size).joinToString(", ") {
      "${it.first.id.take(8)}=${String.format(Locale.ROOT, "%.2f", it.second)}"
    }
    val traza =
      "LTM: evaluados=${candidatos.size}, umbral=$umbral, entraron=$detalle" +
        (if (fallback) " (fallback último recuerdo)" else "")
    return Resultado(
      bloque = bloque,
      ids = ids,
      firma = ids.joinToString(",") + "|${now / DAY}" + sufijo,
      traza = traza + trazaNotas,
      idsNotas = notas.map { it.id },
    )
  }
}
