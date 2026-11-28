package com.google.ai.edge.gallery.store

/** A qué sitio apunta un enlace pegado y qué identificador lleva. */
data class LinkTarget(val site: Site, val id: String) {
  enum class Site(val label: String) {
    CHUB("Chub"), PYGMALION("Pygmalion"), RISU("RisuRealm"), AICC("AI Character Cards"),
    JANITOR("JannyAI / Janitor"), PERCHANCE("Perchance"), CHUB_LORE("Lorebook de Chub"), DIRECT("Enlace directo"),
  }
}

/** Reconoce enlaces de tiendas de personajes y arma las direcciones de descarga. Lógica pura. */
object StoreLinks {
  private const val UUID_RE = "[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}"
  private val UUID = "[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}"

  fun parse(input: String): LinkTarget? {
    val s = input.trim().removeSuffix("/")
    if (s.isEmpty()) return null
    val noScheme = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    val host = noScheme.substringBefore('/').lowercase()
    val path = noScheme.substringAfter('/', "").substringBefore('?').substringBefore('#').trim('/')
    fun after(prefix: String) = path.removePrefix(prefix).trim('/')
    return when {
      host.endsWith("chub.ai") || host.endsWith("characterhub.org") -> {
        val rest = if (path.startsWith("characters/")) after("characters/") else path
        val parts = rest.split('/').filter { it.isNotEmpty() }
        if (path.startsWith("lorebooks/")) {
          val lp = after("lorebooks/").split('/').filter { it.isNotEmpty() }
          if (lp.size >= 2) LinkTarget(LinkTarget.Site.CHUB_LORE, "${lp[0]}/${lp[1]}") else null
        } else if (parts.size >= 2) LinkTarget(LinkTarget.Site.CHUB, "${parts[0]}/${parts[1]}") else null
      }
      host.endsWith("perchance.org") -> perchanceHash(s)?.let { LinkTarget(LinkTarget.Site.PERCHANCE, it) }
      host.endsWith("pygmalion.chat") ->
        Regex(UUID).find(path)?.value?.let { LinkTarget(LinkTarget.Site.PYGMALION, it) }
      host.endsWith("risuai.net") ->
        Regex(UUID).find(path)?.value?.let { LinkTarget(LinkTarget.Site.RISU, it) }
      host.endsWith("aicharactercards.com") -> {
        val rest = after("character-cards/")
        val parts = rest.split('/').filter { it.isNotEmpty() }
        if (parts.size >= 2) LinkTarget(LinkTarget.Site.AICC, "${parts[0]}/${parts[1]}") else null
      }
      host.contains("janitorai") || host.contains("jannyai") ->
        Regex(UUID).find(path)?.value?.let { LinkTarget(LinkTarget.Site.JANITOR, it) }
      !s.startsWith("http", ignoreCase = true) -> parseBareId(s)
      s.startsWith("http", ignoreCase = true) && (path.endsWith(".png", true) || path.endsWith(".json", true)) ->
        LinkTarget(LinkTarget.Site.DIRECT, s)
      else -> null
    }
  }

  /** `Nombre~hash.gz` de `…ai-character-chat?data=Nombre~hash.gz`; devuelve el hash con su `.gz`. */
  fun perchanceHash(url: String): String? {
    val data = Regex("[?&]data=([^&#]+)").find(url)?.groupValues?.get(1) ?: return null
    val decoded = runCatching { java.net.URLDecoder.decode(data, "UTF-8") }.getOrDefault(data)
    val hash = decoded.substringAfterLast('~')
    return hash.takeIf { it.isNotBlank() && Regex("[A-Za-z0-9_.-]+").matches(it) }
  }

  /** Identificadores sin enlace: `usuario/proyecto` (Chub), `lorebooks/u/p`, `AICC/autor/tarjeta`, UUID… */
  fun parseBareId(s: String): LinkTarget? {
    val t = s.trim().trim('/')
    val parts = t.split('/').filter { it.isNotEmpty() }
    val ok = Regex("[A-Za-z0-9_.@-]+")
    return when {
      parts.size == 3 && parts[0].equals("AICC", true) && parts.drop(1).all { ok.matches(it) } ->
        LinkTarget(LinkTarget.Site.AICC, "${parts[1]}/${parts[2]}")
      parts.size == 3 && parts[0].equals("lorebooks", true) && parts.drop(1).all { ok.matches(it) } ->
        LinkTarget(LinkTarget.Site.CHUB_LORE, "${parts[1]}/${parts[2]}")
      parts.size == 2 && parts.all { ok.matches(it) } -> LinkTarget(LinkTarget.Site.CHUB, "${parts[0]}/${parts[1]}")
      parts.size == 1 && Regex("^$UUID_RE$").matches(parts[0]) -> LinkTarget(LinkTarget.Site.PYGMALION, parts[0])
      parts.size == 1 && Regex("^$UUID_RE" + "_.+").matches(parts[0]) ->
        LinkTarget(LinkTarget.Site.JANITOR, Regex(UUID_RE).find(parts[0])!!.value)
      else -> null
    }
  }

  fun perchanceUrl(hash: String) = "https://user-uploads.perchance.org/file/$hash"
  fun chubLorebookUrl(id: String) = "https://api.chub.ai/api/lorebooks/${id.trim('/')}"
  fun chubLorebookRawUrl(projectId: String) =
    "https://api.chub.ai/api/v4/projects/$projectId/repository/files/raw%252Fsillytavern_raw.json/raw"

  fun pygmalionUrl(id: String) = "https://server.pygmalion.chat/api/export/character/$id/v2"
  fun risuUrl(id: String) = "https://realm.risuai.net/api/v1/download/png-v3/$id?non_commercial=true"
  fun aiccUrl(id: String) = "https://aicharactercards.com/wp-json/pngapi/v1/image/$id"
  const val JANITOR_DOWNLOAD = "https://api.jannyai.com/api/v1/download"
}
