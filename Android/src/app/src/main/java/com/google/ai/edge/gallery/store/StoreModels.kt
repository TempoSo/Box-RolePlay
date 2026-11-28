package com.google.ai.edge.gallery.store

import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter

/** Una tarjeta de personaje de una tienda en línea. */
data class StoreCard(
  /** Fuente (por ahora «chub»). */
  val source: String,
  /** Identificador dentro de la fuente (en Chub: usuario/nombre). */
  val id: String,
  val name: String,
  val tagline: String,
  val author: String,
  val tags: List<String>,
  val avatarUrl: String,
  val stars: Int,
  val tokens: Int,
  val nsfw: Boolean,
)

/** Orden de los resultados de la búsqueda. */
enum class StoreSort(val label: String, val chub: String) {
  POPULAR("Populares", "download_count"),
  TRENDING("Tendencia", "trending_downloads"),
  RECENT("Recientes", "created_at"),
  STARS("Mejor valorados", "star_count"),
}

data class StoreQuery(
  val text: String = "",
  val sort: StoreSort = StoreSort.POPULAR,
  val page: Int = 1,
  /** Mostrar también contenido para adultos (NSFW). */
  val nsfw: Boolean = false,
  val pageSize: Int = 20,
)

/**
 * Ficha descargada pero aún no guardada. [character] tiene un id vacío y sin avatar en disco:
 * solo sirve para mostrársela al usuario antes de decidir si importa [bytes].
 */
data class StorePreview(
  val card: StoreCard,
  val bytes: ByteArray,
  val character: RoleplayCharacter,
)
