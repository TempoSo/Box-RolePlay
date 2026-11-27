package com.google.ai.edge.gallery.store

/** A qué sitio apunta un enlace pegado y qué identificador lleva. */
data class LinkTarget(val site: Site, val id: String) {
  enum class Site(val label: String) {
    CHUB("Chub"), PYGMALION("Pygmalion"), RISU("RisuRealm"), AICC("AI Character Cards"), JANITOR("JannyAI / Janitor"), DIRECT("Enlace directo")
  }
}

/** Reconoce enlaces de tiendas de personajes y arma las direcciones de descarga. Lógica pura. */
object StoreLinks {
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
        if (parts.size >= 2 && !rest.startsWith("lorebooks")) LinkTarget(LinkTarget.Site.CHUB, "${parts[0]}/${parts[1]}") else null
      }
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
      s.startsWith("http", ignoreCase = true) && (path.endsWith(".png", true) || path.endsWith(".json", true)) ->
        LinkTarget(LinkTarget.Site.DIRECT, s)
      else -> null
    }
  }

  fun pygmalionUrl(id: String) = "https://server.pygmalion.chat/api/export/character/$id/v2"
  fun risuUrl(id: String) = "https://realm.risuai.net/api/v1/download/png-v3/$id?non_commercial=true"
  fun aiccUrl(id: String) = "https://aicharactercards.com/wp-json/pngapi/v1/image/$id"
  const val JANITOR_DOWNLOAD = "https://api.jannyai.com/api/v1/download"
}
