package com.google.ai.edge.gallery.data.characters

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda las mediciones del benchmark (las últimas [MAX]) en preferencias privadas. */
class BenchStore(context: Context) {
  private val prefs = context.getSharedPreferences("bench_results", Context.MODE_PRIVATE)

  fun load(): List<BenchRow> =
    runCatching {
      val arr = JSONArray(prefs.getString("rows", "[]"))
      (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        BenchRow(
          model = o.getString("model"), engine = o.optString("engine"), ok = o.optBoolean("ok"),
          error = o.optString("error"), loadSec = o.optDouble("loadSec").toFloat(),
          promptTokens = o.optInt("pt"), promptExact = o.optBoolean("pex"), outTokens = o.optInt("ot"),
          ttftMs = o.optLong("ttft"), prefillTps = o.optDouble("pre").toFloat(),
          decodeTps = o.optDouble("dec").toFloat(), totalSec = o.optDouble("tot").toFloat(),
          ramMb = o.optInt("ram"), sizeMb = o.optInt("size"), whenMs = o.optLong("when"),
        )
      }
    }.getOrDefault(emptyList())

  fun add(row: BenchRow) {
    val all = (listOf(row) + load()).take(MAX)
    val arr = JSONArray()
    all.forEach { r ->
      arr.put(JSONObject().apply {
        put("model", r.model); put("engine", r.engine); put("ok", r.ok); put("error", r.error)
        put("loadSec", r.loadSec.toDouble()); put("pt", r.promptTokens); put("pex", r.promptExact)
        put("ot", r.outTokens); put("ttft", r.ttftMs); put("pre", r.prefillTps.toDouble())
        put("dec", r.decodeTps.toDouble()); put("tot", r.totalSec.toDouble()); put("ram", r.ramMb)
        put("size", r.sizeMb); put("when", r.whenMs)
      })
    }
    prefs.edit().putString("rows", arr.toString()).apply()
  }

  fun clear() = prefs.edit().remove("rows").apply()

  companion object { const val MAX = 60 }
}
