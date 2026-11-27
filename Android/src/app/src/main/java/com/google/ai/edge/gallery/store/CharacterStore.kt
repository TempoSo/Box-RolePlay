package com.google.ai.edge.gallery.store

import com.google.ai.edge.gallery.data.characters.CharacterCardException
import com.google.ai.edge.gallery.data.characters.CharacterCardParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Búsqueda y descarga de tarjetas de personaje de tiendas en línea. Bloqueante: llamar desde IO. */
object CharacterStore {
  private val json = Json { isLenient = true; ignoreUnknownKeys = true }

  /** Busca en Chub (la única tienda con búsqueda pública verificada). */
  fun search(q: StoreQuery): List<StoreCard> {
    val body = StoreHttp.get(ChubApi.searchUrl(q), "application/json").toString(Charsets.UTF_8)
    return ChubApi.parseSearch(body, q.nsfw)
  }

  /** Una página de resultados con el total (si Chub lo informa). */
  fun searchPage(q: StoreQuery): StorePage {
    val body = StoreHttp.get(ChubApi.searchUrl(q), "application/json").toString(Charsets.UTF_8)
    return StorePage(ChubApi.parseSearch(body, q.nsfw), ChubApi.parseTotal(body))
  }

  /** Bytes de la tarjeta (PNG con datos incrustados, o JSON) de un resultado de la búsqueda. */
  fun download(card: StoreCard): ByteArray = downloadTarget(LinkTarget(LinkTarget.Site.CHUB, card.id))

  /** Reconoce un enlace o ID pegado; lanza si no se entiende. */
  fun resolve(link: String): LinkTarget = StoreLinks.parse(link) ?: throw CharacterCardException(
    "No reconozco eso. Vale un enlace o ID de Chub (usuario/proyecto), un lorebook de Chub, Pygmalion, RisuRealm, AI Character Cards (AICC/autor/tarjeta), JannyAI, Perchance o un enlace directo a .png/.json."
  )

  /** Descarga un lorebook de Chub y lo convierte al formato de la app. */
  fun downloadLorebook(id: String): StoreLorebook {
    val info = json.parseToJsonElement(StoreHttp.get(StoreLinks.chubLorebookUrl(id), "application/json").toString(Charsets.UTF_8)).jsonObject
    val node = info["node"] as? JsonObject ?: throw CharacterCardException("Chub no encontró ese lorebook")
    val pid = (node["id"] as? JsonPrimitive)?.contentOrNull ?: throw CharacterCardException("Chub no devolvió el lorebook")
    val name = (node["name"] as? JsonPrimitive)?.contentOrNull ?: id.substringAfter('/')
    val raw = StoreHttp.get(StoreLinks.chubLorebookRawUrl(pid), "application/json").toString(Charsets.UTF_8)
    return LorebookConvert.fromWorldInfo(raw, name)?.copy(name = name)
      ?: throw CharacterCardException("El lorebook no tiene entradas con texto")
  }

  /** Descarga desde un enlace pegado. Devuelve el sitio y los bytes; lanza si el enlace no se reconoce. */
  fun downloadLink(link: String): Pair<LinkTarget, ByteArray> {
    val target = resolve(link)
    if (target.site == LinkTarget.Site.CHUB_LORE) throw CharacterCardException("Eso es un lorebook, no un personaje")
    return target to downloadTarget(target)
  }

  private fun downloadTarget(t: LinkTarget): ByteArray =
    when (t.site) {
      LinkTarget.Site.CHUB -> {
        // 1) el PNG con la tarjeta v2; 2) si falla, la ficha de la API y su max_res_url.
        val png = runCatching { StoreHttp.get(ChubApi.pngUrl(t.id)) }.getOrNull()
        if (png != null && valid(png)) png
        else {
          val detail = StoreHttp.get(ChubApi.detailUrl(t.id), "application/json").toString(Charsets.UTF_8)
          val url = ChubApi.parseMaxResUrl(detail) ?: throw CharacterCardException("Chub no devolvió la tarjeta")
          StoreHttp.get(url)
        }
      }
      LinkTarget.Site.PYGMALION -> {
        // La respuesta trae {"character": {...}}: se importa ese objeto como JSON de tarjeta.
        val root = json.parseToJsonElement(StoreHttp.get(StoreLinks.pygmalionUrl(t.id), "application/json").toString(Charsets.UTF_8)).jsonObject
        val card = root["character"] as? JsonObject ?: throw CharacterCardException("Pygmalion no devolvió el personaje")
        card.toString().toByteArray()
      }
      LinkTarget.Site.RISU -> StoreHttp.get(StoreLinks.risuUrl(t.id))
      LinkTarget.Site.AICC -> StoreHttp.get(StoreLinks.aiccUrl(t.id))
      LinkTarget.Site.JANITOR -> {
        // Experimental: este servicio usa protección anti-bots y puede rechazar la app.
        val resp = json.parseToJsonElement(
          StoreHttp.postJson(StoreLinks.JANITOR_DOWNLOAD, """{"characterId":"${t.id}"}""").toString(Charsets.UTF_8)
        ).jsonObject
        val url = (resp["downloadUrl"] as? JsonPrimitive)?.contentOrNull
          ?: throw CharacterCardException("JannyAI no permitió la descarga (protección anti-bots). Descarga el archivo desde el navegador e impórtalo desde Personajes.")
        StoreHttp.get(url)
      }
      LinkTarget.Site.PERCHANCE -> {
        // Experimental: formato de Perchance deducido, sin verificar.
        val text = LorebookConvert.maybeGunzip(StoreHttp.get(StoreLinks.perchanceUrl(t.id))).toString(Charsets.UTF_8)
        (LorebookConvert.perchanceToCard(text) ?: throw CharacterCardException("No pude leer ese personaje de Perchance")).toByteArray()
      }
      LinkTarget.Site.CHUB_LORE -> throw CharacterCardException("Eso es un lorebook, no un personaje")
      LinkTarget.Site.DIRECT -> StoreHttp.get(t.id)
    }

  /** ¿Es una tarjeta que la app sabe leer? */
  private fun valid(bytes: ByteArray): Boolean = runCatching { CharacterCardParser.parse(bytes) }.isSuccess
}
