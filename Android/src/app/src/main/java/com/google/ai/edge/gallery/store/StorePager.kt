package com.google.ai.edge.gallery.store

/** Resultado de una página de la tienda: tarjetas y, si la API lo dice, el total de resultados. */
data class StorePage(val cards: List<StoreCard>, val total: Int?)

/** Qué números mostrar en la barra de páginas. Lógica pura. */
object StorePager {
  /** Páginas máximas que se ofrecen aunque haya más resultados (la API no garantiza más allá). */
  const val MAX_PAGES = 500

  fun totalPages(total: Int?, pageSize: Int): Int? =
    total?.takeIf { it > 0 }?.let { ((it + pageSize - 1) / pageSize).coerceIn(1, MAX_PAGES) }

  /**
   * Elementos de la barra: números de página y `null` por cada «…». Con [totalPages] conocido: 1, la
   * ventana alrededor de [current] y la última. Sin total: de 1 a [current] (+ la siguiente si [hasMore]).
   */
  fun items(current: Int, totalPages: Int?, hasMore: Boolean): List<Int?> {
    val cur = current.coerceAtLeast(1)
    if (totalPages == null) {
      val last = if (hasMore) cur + 1 else cur
      return if (last <= 7) (1..last).toList() else listOf(1, null) + (cur - 2..last).toList()
    }
    val last = totalPages.coerceAtLeast(1)
    if (last <= 7) return (1..last).toList()
    val set = sortedSetOf(1, last, cur, cur - 1, cur + 1)
    if (cur <= 3) set.addAll(1..4)
    if (cur >= last - 2) set.addAll(last - 3..last)
    val sorted = set.filter { it in 1..last }
    val out = ArrayList<Int?>()
    sorted.forEachIndexed { i, p ->
      if (i > 0) {
        val gap = p - sorted[i - 1]
        if (gap == 2) out.add(sorted[i - 1] + 1) else if (gap > 2) out.add(null)
      }
      out.add(p)
    }
    return out
  }

  /** Página al azar distinta de [current]; con total desconocido, entre 1 y 50. */
  fun randomPage(current: Int, totalPages: Int?, rnd: java.util.Random = java.util.Random()): Int {
    val max = (totalPages ?: 50).coerceAtLeast(1)
    if (max == 1) return 1
    var p: Int
    do { p = rnd.nextInt(max) + 1 } while (p == current)
    return p
  }

  /** Tarjeta al azar entre las que aún no están añadidas; null si todas las de la página ya lo están. */
  fun randomCard(
    cards: List<StoreCard>,
    addedIds: Set<String>,
    rnd: kotlin.random.Random = kotlin.random.Random.Default,
  ): StoreCard? = cards.filter { it.id !in addedIds }.randomOrNull(rnd)
}
