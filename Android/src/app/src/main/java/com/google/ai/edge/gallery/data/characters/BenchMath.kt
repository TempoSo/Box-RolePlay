package com.google.ai.edge.gallery.data.characters

/** Una medición de un modelo (lógica pura: sin Android, probada en `BenchMathTest`). */
data class BenchRow(
  val model: String,
  val engine: String, // "GGUF" | "LiteRT"
  val ok: Boolean,
  val error: String = "",
  val loadSec: Float = 0f,
  val promptTokens: Int = 0,
  val promptExact: Boolean = false, // true si el motor dio el recuento real (GGUF)
  val outTokens: Int = 0,
  val ttftMs: Long = 0,
  val prefillTps: Float = 0f,
  val decodeTps: Float = 0f,
  val totalSec: Float = 0f,
  val ramMb: Int = 0,
  val sizeMb: Int = 0,
  val whenMs: Long = 0,
)

object BenchMath {
  /** Estimación de tokens por caracteres (inglés ≈ 4 c/t). Solo para motores que no dan el recuento. */
  fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else (text.length / 4.0).toInt().coerceAtLeast(1)

  /** Velocidad de lectura del prompt a partir del tiempo hasta el primer token. */
  fun prefillTps(promptTokens: Int, ttftMs: Long): Float =
    if (promptTokens <= 0 || ttftMs <= 0) 0f else promptTokens / (ttftMs / 1000f)

  /** Velocidad de generación: tokens tras el primero entre el tiempo transcurrido desde el primero. */
  fun decodeTps(outTokens: Int, ttftMs: Long, totalMs: Long): Float {
    val span = totalMs - ttftMs
    return if (outTokens < 2 || span <= 0) 0f else (outTokens - 1) / (span / 1000f)
  }

  /** Puntuación 0–100: generación 50 %, lectura 20 %, memoria 30 % (menos RAM sobre el total, mejor). */
  fun score(row: BenchRow, totalRamMb: Int): Double {
    if (!row.ok) return 0.0
    val dec = (row.decodeTps / 20f).coerceIn(0f, 1f)
    val pre = (row.prefillTps / 100f).coerceIn(0f, 1f)
    val ram = if (totalRamMb <= 0 || row.ramMb <= 0) 0.5f else (1f - row.ramMb.toFloat() / totalRamMb).coerceIn(0f, 1f)
    return (dec * 50 + pre * 20 + ram * 30).toDouble()
  }

  /** El mejor modelo entre lo medido (se usa la medición más reciente de cada uno). */
  fun best(rows: List<BenchRow>, totalRamMb: Int): BenchRow? =
    rows.filter { it.ok }.groupBy { it.model }.map { (_, v) -> v.maxBy { it.whenMs } }
      .maxByOrNull { score(it, totalRamMb) }

  /**
   * Suposición sin medir: el modelo más grande que cabe cómodamente (≤ 40 % de la RAM total),
   * porque a igual comodidad el más grande suele responder mejor. null si ninguno cabe.
   */
  fun suggestBySize(sizesMb: Map<String, Int>, totalRamMb: Int): String? =
    sizesMb.filter { it.value in 1..(totalRamMb * 0.4).toInt() }.maxByOrNull { it.value }?.key

  fun rank(rows: List<BenchRow>, totalRamMb: Int): List<BenchRow> =
    rows.filter { it.ok }.groupBy { it.model }.map { (_, v) -> v.maxBy { it.whenMs } }
      .sortedByDescending { score(it, totalRamMb) }
}
