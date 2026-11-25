package com.google.ai.edge.gallery.ui.apps

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.characters.BenchCfg
import com.google.ai.edge.gallery.data.characters.BenchMath
import com.google.ai.edge.gallery.data.characters.EnginePerfStore
import com.google.ai.edge.gallery.data.characters.BenchPrompt
import com.google.ai.edge.gallery.data.characters.BenchRow
import com.google.ai.edge.gallery.data.characters.BenchStore
import com.google.ai.edge.gallery.runtime.LlamaCppModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App «Benchmark»: una sola pantalla. Marcas modelos (GGUF y LiteRT), pulsas Ejecutar y se miden
 * uno tras otro, aislados, con el mismo prompt de resumen. Los resultados se guardan y se ordenan
 * con una puntuación local (velocidad + memoria): no hay servidor, es una estimación del equipo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkAppScreen(modelManagerViewModel: ModelManagerViewModel, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val store = remember { BenchStore(context) }
  val task = remember { modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)!! }
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val models = remember(uiState.modelDownloadStatus) { modelManagerViewModel.getAllDownloadedModels().distinctBy { it.name } }
  val totalRamMb = remember {
    val mi = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    (mi.totalMem / (1024 * 1024)).toInt()
  }

  val selected = remember { mutableStateListOf<String>() }
  var tokensText by remember { mutableStateOf("128") }
  var rows by remember { mutableStateOf(store.load()) }
  var running by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("") }
  var progress by remember { mutableStateOf("") }
  var job by remember { mutableStateOf<Job?>(null) }
  var showPrompt by remember { mutableStateOf(false) }
  var cfg by remember { mutableStateOf(store.loadCfg()) }
  var repsText by remember { mutableStateOf("2") }
  var warmup by remember { mutableStateOf(true) }
  var sweepParam by remember { mutableStateOf("") }
  var sweepText by remember { mutableStateOf("64, 128, 512") }
  var appliedNote by remember { mutableStateOf<String?>(null) }
  fun setCfg(c: BenchCfg) { cfg = c; store.saveCfg(c) }
  val anyGguf = models.any { it.name in selected && it.runtimeHelper is LlamaCppModelHelper }

  // Pantalla encendida mientras se mide (una medición larga con la pantalla apagada falsea los números).
  val view = LocalView.current
  DisposableEffect(running) {
    view.keepScreenOn = running
    onDispose { view.keepScreenOn = false }
  }

  fun run() {
    val maxTokens = (tokensText.toIntOrNull() ?: 128).coerceIn(16, 1024)
    val reps = (repsText.toIntOrNull() ?: 2).coerceIn(1, 5)
    val sweep = if (sweepParam.isBlank()) emptyList() else BenchMath.parseSweep(sweepText)
    // Cola: cada modelo × cada configuración (solo GGUF; LiteRT no tiene estos ajustes y va una vez).
    val jobs = mutableListOf<Triple<com.google.ai.edge.gallery.data.Model, BenchCfg?, String>>()
    for (m in models.filter { it.name in selected }) {
      if (m.runtimeHelper is LlamaCppModelHelper) {
        if (sweep.isEmpty()) jobs.add(Triple(m, cfg, ""))
        else sweep.forEach { v -> cfg.with(sweepParam, v)?.let { jobs.add(Triple(m, it, "$sweepParam=$v")) } }
      } else jobs.add(Triple(m, null, ""))
    }
    if (jobs.isEmpty()) return
    running = true
    appliedNote = null
    job = scope.launch {
      try {
        jobs.forEachIndexed { i, (m, c, tag) ->
          progress = "Prueba ${i + 1} de ${jobs.size}: ${m.displayName.ifBlank { m.name }}" + (if (tag.isNotEmpty()) " ($tag)" else "")
          val row = BenchRunner.measure(context, modelManagerViewModel, task, m, maxTokens, c, reps, warmup) { status = it }
          store.add(row)
          rows = store.load()
        }
      } catch (_: CancellationException) {
      } finally {
        running = false
        status = ""
        progress = ""
      }
    }
  }

  BoxScaffold(title = "Benchmark de modelos", subtitle = "Mide la velocidad de tus modelos, uno por uno", onBack = { if (!running) onBack() }) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (running) {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trabajando…", style = MaterialTheme.typography.titleMedium)
            Text(progress, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
              "Mantén el teléfono conectado y la app abierta: la pantalla no se apaga mientras mide.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { job?.cancel() }) { Text("Cancelar") }
          }
        }
        return@Column
      }

      Text(
        "Las cifras de GGUF y de LiteRT las mide cada motor a su manera: compara siempre dentro del mismo motor.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // ── Recomendación local ──
      val best = BenchMath.best(rows, totalRamMb)
      val guess =
        if (best == null)
          BenchMath.suggestBySize(models.associate { it.name to (it.totalBytes / (1024 * 1024)).toInt() }, totalRamMb)
        else null
      if (best != null || guess != null) {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              if (best != null) "RECOMENDADO PARA TU DISPOSITIVO" else "SUPOSICIÓN SIN MEDIR",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(best?.model ?: guess!!, style = MaterialTheme.typography.titleMedium)
            Text(
              if (best != null)
                "Mejor equilibrio entre generación (50 %), lectura (20 %) y memoria (30 %) entre lo que has medido. " +
                  "Puntuación ${"%.0f".format(BenchMath.score(best, totalRamMb))}/100 · ${"%.1f".format(best.decodeTps)} t/s."
              else
                "Es el modelo más grande que cabe cómodo en tu RAM (${totalRamMb / 1024} GB, tope 40 %). " +
                  "Mídelo abajo para confirmarlo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // ── Selección ──
      Text("Elige los modelos", style = MaterialTheme.typography.titleSmall)
      if (models.isEmpty()) {
        Text(
          "No hay modelos descargados o importados. Impórtalos en el Gestor de modelos.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      models.forEach { m ->
        val last = rows.filter { it.model == m.name && it.ok }.maxByOrNull { it.whenMs }
        Card(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = m.name in selected,
              onCheckedChange = { if (it) selected.add(m.name) else selected.remove(m.name) },
            )
            Column(Modifier.weight(1f)) {
              Text(m.displayName.ifBlank { m.name }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
              Text(
                (if (m.runtimeHelper is LlamaCppModelHelper) "GGUF" else "LiteRT") + " · ${m.totalBytes / (1024 * 1024)} MB" +
                  (last?.let { " · ${"%.1f".format(it.decodeTps)} t/s" } ?: " · sin medir"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      OutlinedTextField(
        value = tokensText,
        onValueChange = { tokensText = it.filter(Char::isDigit).take(4) },
        label = { Text("Tokens máximos de respuesta (16–1024)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = repsText,
        onValueChange = { repsText = it.filter(Char::isDigit).take(1) },
        label = { Text("Pasadas medidas por prueba (1–5, se promedian)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = warmup, onCheckedChange = { warmup = it })
        Text("Pasada de calentamiento previa (no cuenta)", style = MaterialTheme.typography.bodySmall)
      }

      if (anyGguf) {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ajustes del motor GGUF (solo benchmark)", style = MaterialTheme.typography.titleSmall)
            Text(
              "Son independientes de los ajustes de tus chats: aquí puedes probar sin tocar nada de ellos.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              NumField("Hilos (0=auto)", cfg.threads, Modifier.weight(1f)) { setCfg(cfg.copy(threads = it.coerceIn(0, 16))) }
              NumField("Contexto", cfg.ctx, Modifier.weight(1f)) { setCfg(cfg.copy(ctx = it.coerceIn(256, 131072))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              NumField("nBatch", cfg.nBatch, Modifier.weight(1f)) { setCfg(cfg.copy(nBatch = it.coerceIn(1, 4096))) }
              NumField("nUBatch", cfg.nUBatch, Modifier.weight(1f)) { setCfg(cfg.copy(nUBatch = it.coerceIn(1, 4096))) }
            }
            Text("Flash attention", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("Auto", "Apagado", "Encendido").forEachIndexed { i, l ->
                FilterChip(selected = cfg.flash == i, onClick = { setCfg(cfg.copy(flash = i)) }, label = { Text(l) })
              }
            }
            Text("Tipo de KV", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              BenchCfg.KV.forEachIndexed { i, l ->
                FilterChip(selected = cfg.kv == i, onClick = { setCfg(cfg.copy(kv = i)) }, label = { Text(l) })
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Switch(checked = cfg.repack, onCheckedChange = { setCfg(cfg.copy(repack = it)) })
              Text("Repack", style = MaterialTheme.typography.bodySmall)
              Switch(checked = cfg.noMmap, onCheckedChange = { setCfg(cfg.copy(noMmap = it)) })
              Text("Sin mmap", style = MaterialTheme.typography.bodySmall)
            }
            Text("Barrido automático: prueba cada valor, uno tras otro", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("" to "Ninguno", "nbatch" to "nBatch", "hilos" to "Hilos", "flash" to "Flash", "kv" to "KV", "ctx" to "Contexto").forEach { (k, l) ->
                FilterChip(selected = sweepParam == k, onClick = { sweepParam = k }, label = { Text(l) })
              }
            }
            if (sweepParam.isNotBlank()) {
              OutlinedTextField(
                value = sweepText,
                onValueChange = { sweepText = it.filter { c -> c.isDigit() || c == ',' || c == ' ' }.take(40) },
                label = { Text("Valores (ej. 64, 128, 512)") },
                supportingText = { Text("Flash: 0 auto, 1 apagado, 2 encendido · KV: 0 f32, 1 f16, 2 bf16, 3 q8_0, 6 q4_0 · Hilos: 0 = auto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              Text(
                "Cada valor recarga el modelo: ${BenchMath.parseSweep(sweepText).size} pruebas por modelo GGUF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = selected.isNotEmpty(), onClick = { run() }) { Text("Ejecutar (${selected.size})") }
        TextButton(onClick = { showPrompt = !showPrompt }) { Text(if (showPrompt) "Ocultar prompt" else "Ver prompt") }
      }
      if (showPrompt) {
        Text(BenchPrompt.FULL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      // ── Resultados ──
      if (rows.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Resultados", style = MaterialTheme.typography.titleSmall)
          TextButton(onClick = { store.clear(); rows = emptyList() }) { Text("Borrar") }
        }
        val ranked = BenchMath.rank(rows, totalRamMb)
        appliedNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        val failed = rows.filter { !it.ok }.take(5)
        ranked.forEachIndexed { i, top ->
          ModelResults(
            pos = i + 1, top = top, all = rows.filter { it.ok && it.model == top.model },
            totalRamMb = totalRamMb,
            onApply = {
              val c = BenchCfg.parse(top.cfg)
              val ps = EnginePerfStore(context)
              ps.save(ps.load().copy(threads = c.threads, nBatch = c.nBatch, nUBatch = c.nUBatch,
                flashAttn = c.flash, kvType = c.kv, noMmap = c.noMmap, repack = c.repack))
              appliedNote = "Aplicado a tus chats: ${c.label()} (el contexto no se cambia)."
            },
          )
        }
        failed.forEach { r ->
          Text(
            "✗ ${r.model}: ${r.error}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun NumField(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { t ->
      text = t.filter(Char::isDigit).take(6)
      text.toIntOrNull()?.let(onValue)
    },
    label = { Text(label) },
    singleLine = true,
    modifier = modifier,
  )
}

/** Un modelo con todas sus configuraciones medidas; la mejor por puntuación va marcada. */
@Composable
private fun ModelResults(pos: Int, top: BenchRow, all: List<BenchRow>, totalRamMb: Int, onApply: () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$pos · ${top.model}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2)
        Text("${"%.0f".format(BenchMath.score(top, totalRamMb))} pts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric("%.1f".format(top.decodeTps), "generación t/s")
        Metric("%.1f".format(top.prefillTps), "lectura t/s")
      }
      Text(
        "${top.engine} · 1.er token ${"%.1f".format(top.ttftMs / 1000f)} s · prompt ${if (top.promptExact) "" else "≈"}${top.promptTokens} tok · " +
          "${top.outTokens} tok generados · RAM ${"%.2f".format(top.ramMb / 1024f)} GB · carga ${"%.0f".format(top.loadSec)} s · " +
          "${top.reps} pasada(s) · total ${"%.0f".format(top.totalSec)} s",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (top.engine == "GGUF") {
        Text("Configuraciones probadas (por puntuación):", style = MaterialTheme.typography.labelMedium)
        all.sortedByDescending { BenchMath.score(it, totalRamMb) }.forEach { r ->
          val best = r === top
          Text(
            (if (best) "★ " else "• ") + BenchCfg.parse(r.cfg).label() +
              "\n   ${"%.1f".format(r.decodeTps)} t/s gen · ${"%.1f".format(r.prefillTps)} t/s lectura · RAM ${"%.1f".format(r.ramMb / 1024f)} GB",
            style = MaterialTheme.typography.bodySmall,
            color = if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        OutlinedButton(onClick = onApply) { Text("Aplicar la mejor a mis chats") }
      }
    }
  }
}

@Composable
private fun Metric(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.headlineSmall)
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
