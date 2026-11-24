package com.google.ai.edge.gallery.ui.apps

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.characters.BenchMath
import com.google.ai.edge.gallery.data.characters.BenchPrompt
import com.google.ai.edge.gallery.data.characters.BenchRow
import com.google.ai.edge.gallery.data.characters.LastEnginePerf
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.runtime.LlamaCppModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mide UN modelo de forma aislada: descarga lo que haya cargado, carga el modelo, le pasa el
 * prompt fijo de resumen ([BenchPrompt]) y lo vuelve a descargar. Funciona igual para GGUF y
 * LiteRT: el tiempo hasta el primer token y los tokens se miden aquí; en GGUF, además, se usan las
 * cifras reales del motor nativo (recuento de tokens del prompt y velocidades).
 */
object BenchRunner {
  private suspend fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
    val t0 = SystemClock.elapsedRealtime()
    while (SystemClock.elapsedRealtime() - t0 < timeoutMs) {
      if (cond()) return true
      delay(200)
    }
    return cond()
  }

  private fun pssMb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024

  private suspend fun unloadAll(context: Context, mm: ModelManagerViewModel, task: Task, models: List<Model>) {
    val loaded = models.filter { it.instance != null }
    loaded.forEach { mm.cleanupModel(context = context, task = task, model = it) }
    waitFor(30_000) { loaded.all { it.instance == null } }
  }

  suspend fun measure(
    context: Context,
    mm: ModelManagerViewModel,
    task: Task,
    model: Model,
    maxTokens: Int,
    onStatus: (String) -> Unit,
  ): BenchRow = withContext(Dispatchers.Default) {
    val helper = model.runtimeHelper
    val engine = if (helper is LlamaCppModelHelper) "GGUF" else "LiteRT"
    val sizeMb = (model.totalBytes / (1024 * 1024)).toInt()
    val allModels = mm.getAllDownloadedModels() + model
    fun fail(msg: String) =
      BenchRow(model = model.name, engine = engine, ok = false, error = msg, sizeMb = sizeMb, whenMs = System.currentTimeMillis())
    try {
      onStatus("Liberando memoria…")
      unloadAll(context, mm, task, allModels)

      onStatus("Cargando $engine…")
      val tLoad = SystemClock.elapsedRealtime()
      mm.initializeModel(context = context, task = task, model = model, force = true)
      val loaded = waitFor(300_000) {
        val st = mm.uiState.value.modelInitializationStatus[model.name]?.status
        st == ModelInitializationStatusType.ERROR ||
          (st == ModelInitializationStatusType.INITIALIZED && helper.isModelReady(model))
      }
      val st = mm.uiState.value.modelInitializationStatus[model.name]?.status
      if (!loaded || st != ModelInitializationStatusType.INITIALIZED) return@withContext fail("No cargó el modelo")
      val loadSec = (SystemClock.elapsedRealtime() - tLoad) / 1000f

      onStatus("Procesando el prompt…")
      val prompt = BenchPrompt.FULL
      LastEnginePerf.stats = null
      val finished = CompletableDeferred<Unit>()
      var errMsg: String? = null
      var chunks = 0
      var tFirst = -1L
      var stopped = false
      val out = StringBuilder()
      val t0 = SystemClock.elapsedRealtime()
      helper.runInference(
        model = model,
        input = prompt,
        resultListener = { part, done, thought ->
          if (part.isNotEmpty() || !thought.isNullOrEmpty()) {
            if (tFirst < 0) {
              tFirst = SystemClock.elapsedRealtime()
              onStatus("Generando…")
            }
            chunks++
            out.append(part)
          }
          if (done) finished.complete(Unit)
          else if (chunks >= maxTokens && !stopped) {
            stopped = true
            helper.stopResponse(model)
          }
        },
        cleanUpListener = {},
        onError = { m ->
          errMsg = m
          finished.complete(Unit)
        },
        coroutineScope = CoroutineScope(Dispatchers.Default),
        extraContext = mapOf("max_response_tokens" to maxTokens.toString()),
      )
      val ok = withTimeoutOrNull(600_000) { finished.await() } != null
      val tEnd = SystemClock.elapsedRealtime()
      if (!ok) helper.stopResponse(model)
      errMsg?.let { return@withContext fail(it) }
      if (!ok) return@withContext fail("Tiempo agotado (10 min)")
      if (tFirst < 0) return@withContext fail("El modelo no generó nada")
      val ramMb = pssMb()

      val ttft = tFirst - t0
      val total = tEnd - t0
      val perf = LastEnginePerf.stats.takeIf { engine == "GGUF" }
      val promptTokens = perf?.promptTokens ?: BenchMath.estimateTokens(prompt)
      val outTokens = perf?.generatedTokens ?: chunks
      BenchRow(
        model = model.name, engine = engine, ok = true, loadSec = loadSec,
        promptTokens = promptTokens, promptExact = perf != null, outTokens = outTokens, ttftMs = ttft,
        prefillTps = perf?.promptTokensPerSecond ?: BenchMath.prefillTps(promptTokens, ttft),
        decodeTps = perf?.generationTokensPerSecond ?: BenchMath.decodeTps(outTokens, ttft, total),
        totalSec = total / 1000f, ramMb = ramMb, sizeMb = sizeMb, whenMs = System.currentTimeMillis(),
      )
    } finally {
      withContext(NonCancellable) {
        runCatching { helper.stopResponse(model) }
        unloadAll(context, mm, task, allModels)
      }
    }
  }
}
