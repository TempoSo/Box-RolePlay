package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Recuerdo listo para mostrar: texto legible y metadatos, sin importar cómo se guardó. */
data class MemoryDetalleUi(
  val summary: String,
  val topic: String,
  val keys: List<String>,
  val importance: Int,
  /** Indica que el contenido era JSON del archivista antiguo y se ha convertido solo al mostrar. */
  val esJson: Boolean,
)

/**
 * Los recuerdos absorbidos antes de la migración 7→8 guardaron la salida cruda del modelo dentro de
 * `content`. Se detecta al mostrar (nunca se reescribe la base de datos) para que las tarjetas viejas
 * dejen de parecer JSON y pasen a leerse como un resumen con tema, claves e importancia.
 */
fun CharacterMemory.detalleParaUI(): MemoryDetalleUi {
  val objeto = runCatching { Json.parseToJsonElement(content.trim()).jsonObject }.getOrNull()
  val resumenJson =
    objeto?.let { o ->
      listOf("summary", "memory").firstNotNullOfOrNull { clave ->
        (o[clave] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
      }
    }
  val esJson = resumenJson != null
  val resumen = resumenJson?.let { MemoryAbsorbPrompt.clean(it) ?: it } ?: content.trim()
  val topicJson = (objeto?.get("topic") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
  val topic = topic.ifBlank { topicJson }.take(60)
  val keysGuardadas =
    runCatching {
      Json.parseToJsonElement(keysJson).jsonArray.mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.length in 2..40 }
      }
    }.getOrDefault(emptyList()).distinct().take(5)
  val keysJsonAntiguas =
    ((objeto?.get("keys") as? JsonArray) ?: JsonArray(emptyList()))
      .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.length in 2..40 } }
      .distinct()
      .take(5)
  val keys =
    keysGuardadas.ifEmpty { keysJsonAntiguas }.ifEmpty {
      MemoryRetriever.tokens(resumen).sortedByDescending { it.length }.take(5)
    }
  // 5 es el valor por defecto de la columna: si está intacto, se aprovecha el del JSON antiguo.
  val importanceJson = (objeto?.get("importance") as? JsonPrimitive)?.intOrNull
  val importance = (if (importance != 5) importance else importanceJson ?: importance).coerceIn(1, 10)
  return MemoryDetalleUi(resumen, topic, keys, importance, esJson)
}
