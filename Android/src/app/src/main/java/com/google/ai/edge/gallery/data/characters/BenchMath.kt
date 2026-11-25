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
  /** Configuración del motor con la que se midió (BenchCfg codificada; vacía en LiteRT). */
  val cfg: String = "",
  val reps: Int = 1,
)

/** Ajustes del motor GGUF solo para el benchmark (independientes de los de los chats). */
data class BenchCfg(
  val threads: Int = 0,
  val nBatch: Int = 512,
  val nUBatch: Int = 512,
  val flash: Int = 0,
  val kv: Int = 1,
  val noMmap: Boolean = false,
  val repack: Boolean = true,
  val ctx: Int = 2048,
) {
  fun encode(): String =
    "t=$threads;nb=$nBatch;nu=$nUBatch;fl=$flash;kv=$kv;nm=${if (noMmap) 1 else 0};rp=${if (repack) 1 else 0};cx=$ctx"

  fun label(): String =
    "hilos ${if (threads == 0) "auto" else threads} · nBatch $nBatch · nUBatch $nUBatch · " +
      "flash ${listOf("auto", "off", "on").getOrElse(flash) { "?" }} · KV ${KV.getOrElse(kv) { "?" }} · ctx $ctx" +
      (if (noMmap) " · sin mmap" else "") + (if (repack) "" else " · sin repack")

  /** Copia con un parámetro cambiado (barrido). null si el nombre o el valor no valen. */
  fun with(param: String, value: Int): BenchCfg? =
    when (param) {
      "nbatch" -> copy(nBatch = value.coerceIn(1, 4096), nUBatch = minOf(nUBatch, value.coerceIn(1, 4096)))
      "hilos" -> copy(threads = value.coerceIn(0, 16))
      "flash" -> copy(flash = value.coerceIn(0, 2))
      "kv" -> copy(kv = value.coerceIn(0, KV.size - 1))
      "ctx" -> copy(ctx = value.coerceIn(256, 131072))
      else -> null
    }

  companion object {
    val KV = listOf("f32", "f16", "bf16", "q8_0", "q5_0", "q5_1", "q4_0", "q4_1", "iq4_nl")

    fun parse(s: String): BenchCfg {
      val m = s.split(';').mapNotNull { it.split('=').takeIf { p -> p.size == 2 } }.associate { it[0] to it[1].toIntOrNull() }
      val d = BenchCfg()
      return BenchCfg(
        threads = (m["t"] ?: d.threads).coerceIn(0, 16),
        nBatch = (m["nb"] ?: d.nBatch).coerceIn(1, 4096),
        nUBatch = (m["nu"] ?: d.nUBatch).coerceIn(1, 4096),
        flash = (m["fl"] ?: d.flash).coerceIn(0, 2),
        kv = (m["kv"] ?: d.kv).coerceIn(0, KV.size - 1),
        noMmap = (m["nm"] ?: 0) == 1,
        repack = (m["rp"] ?: 1) == 1,
        ctx = (m["cx"] ?: d.ctx).coerceIn(256, 131072),
      )
    }
  }
}

/** Una pasada medida (varias pasadas se promedian en una BenchRow). */
data class BenchSample(
  val prefillTps: Float, val decodeTps: Float, val ttftMs: Long,
  val promptTokens: Int, val outTokens: Int, val totalMs: Long, val ramMb: Int,
)

object BenchMath {
  /** Promedio de las pasadas. Lista vacía → null. */
  fun average(s: List<BenchSample>): BenchSample? {
    if (s.isEmpty()) return null
    return BenchSample(
      prefillTps = s.map { it.prefillTps }.average().toFloat(),
      decodeTps = s.map { it.decodeTps }.average().toFloat(),
      ttftMs = s.map { it.ttftMs }.average().toLong(),
      promptTokens = s.last().promptTokens, outTokens = s.last().outTokens,
      totalMs = s.map { it.totalMs }.average().toLong(), ramMb = s.maxOf { it.ramMb },
    )
  }

  /** Valores de un barrido escritos a mano ("64, 128;512"): enteros, sin repetir, máx. 8. */
  fun parseSweep(text: String): List<Int> =
    text.split(',', ';', ' ').mapNotNull { it.trim().toIntOrNull() }.distinct().take(8)

  /** La mejor configuración medida de cada modelo (por puntuación). */
  fun bestPerModel(rows: List<BenchRow>, totalRamMb: Int): List<BenchRow> =
    rows.filter { it.ok }.groupBy { it.model }.map { (_, v) -> v.maxBy { score(it, totalRamMb) } }

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

  /** El mejor modelo entre lo medido (con su mejor configuración). */
  fun best(rows: List<BenchRow>, totalRamMb: Int): BenchRow? =
    bestPerModel(rows, totalRamMb).maxByOrNull { score(it, totalRamMb) }

  /**
   * Suposición sin medir: el modelo más grande que cabe cómodamente (≤ 40 % de la RAM total),
   * porque a igual comodidad el más grande suele responder mejor. null si ninguno cabe.
   */
  fun suggestBySize(sizesMb: Map<String, Int>, totalRamMb: Int): String? =
    sizesMb.filter { it.value in 1..(totalRamMb * 0.4).toInt() }.maxByOrNull { it.value }?.key

  fun rank(rows: List<BenchRow>, totalRamMb: Int): List<BenchRow> =
    bestPerModel(rows, totalRamMb).sortedByDescending { score(it, totalRamMb) }
}
