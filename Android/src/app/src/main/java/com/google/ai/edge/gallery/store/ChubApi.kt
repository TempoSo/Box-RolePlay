package com.google.ai.edge.gallery.store

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Direcciones y lectura de respuestas de la API pública de Chub (chub.ai). Lógica pura. */
object ChubApi {
  const val SOURCE = "chub"
  private const val SEARCH = "https://api.chub.ai/search"
  private const val DETAIL = "https://api.chub.ai/api/characters/"
  private const val AVATARS = "https://avatars.charhub.io/avatars/"

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  fun searchUrl(q: StoreQuery): String {
    val p = linkedMapOf(
      "search" to q.text.trim(),
      "first" to q.pageSize.toString(),
      "page" to q.page.coerceAtLeast(1).toString(),
      "sort" to q.sort.chub,
      "asc" to "false",
      "include_forks" to "false",
      "nsfw" to q.nsfw.toString(),
      "nsfl" to "false",
      "require_images" to "false",
      "min_tokens" to "50",
      "exclude_mine" to "false",
    )
    return SEARCH + "?" + p.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
  }

  /** Ficha del personaje (JSON con `node.max_res_url`, etc.). */
  fun detailUrl(fullPath: String): String = DETAIL + fullPath.trim('/') + "?full=true"

  /** PNG con la tarjeta v2 incrustada. */
  fun pngUrl(fullPath: String): String = AVATARS + fullPath.trim('/') + "/chara_card_v2.png"

  private val json = Json { isLenient = true; ignoreUnknownKeys = true }

  /** Lee la lista de resultados; con [allowNsfw] = false descarta además las marcadas NSFW. */
  fun parseSearch(text: String, allowNsfw: Boolean): List<StoreCard> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val nodes = ((root["data"] as? JsonObject)?.get("nodes") ?: root["nodes"]) as? JsonArray ?: return emptyList()
    return nodes.mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
      fun num(vararg ks: String) = ks.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.intOrNull } ?: 0
      val full = str("fullPath")
      if (full.isEmpty() || str("name").isEmpty()) return@mapNotNull null
      val tags = (o["topics"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
      val nsfw = tags.any { it.equals("NSFW", true) || it.equals("NSFL", true) } ||
        (o["nsfw_image"] as? JsonPrimitive)?.contentOrNull == "true"
      if (nsfw && !allowNsfw) return@mapNotNull null
      StoreCard(
        source = SOURCE,
        id = full,
        name = str("name"),
        tagline = str("tagline"),
        author = full.substringBefore('/'),
        tags = tags,
        avatarUrl = str("avatar_url").ifEmpty { str("max_res_url") },
        stars = num("starCount", "star_count"),
        tokens = num("nTokens", "n_tokens"),
        nsfw = nsfw,
      )
    }
  }

  /** `max_res_url` de la ficha de un personaje, o null. */
  fun parseMaxResUrl(text: String): String? =
    runCatching {
      val node = json.parseToJsonElement(text).jsonObject["node"] as? JsonObject
      (node?.get("max_res_url") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
}
