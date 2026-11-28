package com.google.ai.edge.gallery.store

import com.google.ai.edge.gallery.data.characters.LoreEntry
import com.google.ai.edge.gallery.data.characters.LorebookMatcher
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Un lorebook listo para añadir a un personaje (formato `character_book` de la app). */
data class StoreLorebook(val name: String, val json: String, val entryCount: Int)

/** Conversión de formatos de lorebook y de tarjetas de otras tiendas. Lógica pura. */
object LorebookConvert {
  private val json = Json { isLenient = true; ignoreUnknownKeys = true }

  /**
   * Lee un lorebook en formato SillyTavern ("world info": `entries` como mapa con key/keysecondary/
   * order/disable…) o ya en formato character_book (`keys`, `enabled`…). Null si no hay entradas.
   */
  fun fromWorldInfo(text: String, fallbackName: String = "Lorebook"): StoreLorebook? {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val raw: List<JsonElement> = when (val e = root["entries"]) {
      is JsonArray -> e
      is JsonObject -> e.values.toList()
      else -> return null
    }
    fun JsonObject.strs(vararg ks: String): List<String> =
      ks.firstNotNullOfOrNull { k -> (this[k] as? JsonArray) }
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.filter { it.isNotEmpty() }
        ?: ((ks.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull })
          ?.let { LorebookMatcher.parseKeys(it) } ?: emptyList())
    fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    fun JsonObject.int(vararg ks: String): Int =
      ks.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.intOrNull } ?: 0
    val entries = raw.mapNotNull { el ->
      val o = el as? JsonObject ?: return@mapNotNull null
      val content = (o["content"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
      if (content.isEmpty()) return@mapNotNull null
      val enabled = o.bool("enabled") ?: !(o.bool("disable") ?: false)
      val comment = ((o["comment"] ?: o["name"]) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
      val keys = o.strs("keys", "key")
      val constant = o.bool("constant") ?: false
      // Sin palabras clave y sin ser constante nunca se activaría: se trata como constante.
      LoreEntry(
        keys = keys,
        secondaryKeys = o.strs("secondary_keys", "keysecondary"),
        content = content,
        enabled = enabled,
        constant = constant || keys.isEmpty(),
        selective = o.bool("selective") ?: false,
        caseSensitive = o.bool("case_sensitive") ?: o.bool("caseSensitive") ?: false,
        insertionOrder = o.int("insertion_order", "order", "insertionOrder"),
        priority = o.int("priority"),
        comment = comment,
      )
    }
    if (entries.isEmpty()) return null
    val name = (root["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackName
    val original = buildJsonObject { put("name", name) }.toString()
    return StoreLorebook(name, LorebookMatcher.encode(entries, original), entries.size)
  }

  /** Une los lorebooks: conserva las entradas del personaje y añade las nuevas (sin duplicar por contenido). */
  fun merge(existingJson: String, added: StoreLorebook): String {
    val old = LorebookMatcher.parse(existingJson)
    val new = LorebookMatcher.parse(added.json)
    val seen = old.map { it.content.trim() }.toMutableSet()
    val extra = new.filter { seen.add(it.content.trim()) }
    return LorebookMatcher.encode(old + extra, existingJson.ifBlank { added.json })
  }

  /** Descomprime si los bytes son gzip; si no, los devuelve tal cual. */
  fun maybeGunzip(bytes: ByteArray): ByteArray =
    if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
      GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    else bytes

  /**
   * Personaje de Perchance (ai-character-chat) -> JSON de tarjeta chara_card_v2. EXPERIMENTAL: el
   * formato se dedujo sin poder probarlo; null si no se reconoce.
   */
  fun perchanceToCard(text: String): String? {
    val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val o = (parsed["character"] as? JsonObject) ?: parsed
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val name = s("name")
    if (name.isEmpty()) return null
    val first = (o["initialMessages"] as? JsonArray)
      ?.mapNotNull { it as? JsonObject }
      ?.firstOrNull { (it["author"] as? JsonPrimitive)?.contentOrNull != "user" }
      ?.let { (it["content"] as? JsonPrimitive)?.contentOrNull }.orEmpty()
    val data = buildJsonObject {
      put("name", name)
      put("description", s("roleInstruction"))
      put("personality", "")
      put("scenario", "")
      put("first_mes", first)
      put("mes_example", "")
      put("creator_notes", s("reminderMessage"))
      put("system_prompt", "")
      put("post_history_instructions", "")
    }
    return buildJsonObject {
      put("spec", "chara_card_v2")
      put("spec_version", "2.0")
      put("data", data)
    }.toString()
  }
}
