package com.google.ai.edge.gallery.data.characters

import android.content.Context
import com.jegly.offlineLLM.smollm.SmolLM

/**
 * Ajustes de rendimiento del motor GGUF (llama.cpp). Se aplican al (re)cargar el modelo.
 * Los valores por defecto equivalen al comportamiento anterior, salvo los hilos (0 = automático:
 * solo los núcleos rápidos del procesador).
 */
data class EnginePerfSettings(
  /** 0 = automático (núcleos rápidos). */
  val threads: Int = 0,
  val nBatch: Int = 512,
  val nUBatch: Int = 512,
  /** 0 auto, 1 apagado, 2 encendido. */
  val flashAttn: Int = 0,
  /** Índice en [KV_TYPES]; se usa para K y V. */
  val kvType: Int = 1,
  val noMmap: Boolean = false,
  val repack: Boolean = true,
  /** 0 = el del modelo ("Max tokens"). */
  val contextLength: Int = 0,
  /** -1 = sin límite. */
  val maxResponse: Int = -1,
  /** Nombre del archivo mmproj elegido (vacío = sin visión en GGUF). */
  val mmproj: String = "",
  /** Lado máximo, en píxeles, al que se reduce una imagen antes de enviarla al modelo. */
  val imageMaxSide: Int = 512,
) {
  fun sanitized(): EnginePerfSettings =
    copy(
      threads = threads.coerceIn(0, 16),
      nBatch = nBatch.coerceIn(1, 4096),
      nUBatch = nUBatch.coerceIn(1, 4096),
      flashAttn = flashAttn.coerceIn(0, 2),
      kvType = kvType.coerceIn(0, KV_TYPES.size - 1),
      contextLength = if (contextLength <= 0) 0 else contextLength.coerceIn(256, 131072),
      maxResponse = if (maxResponse <= 0) -1 else maxResponse.coerceAtMost(32768),
      imageMaxSide = imageMaxSide.coerceIn(128, 2048),
    )

  fun toEngineOptions(): SmolLM.EngineOptions =
    SmolLM.EngineOptions(
      nBatch = nBatch,
      nUBatch = nUBatch,
      flashAttn = flashAttn,
      kvTypeK = kvType,
      kvTypeV = kvType,
      repack = repack,
      maxResponse = maxResponse,
    )

  companion object {
    /** Mismo orden que SmolLM.EngineOptions / EngineOptions.h. */
    val KV_TYPES = listOf("f32", "f16", "bf16", "q8_0", "q5_0", "q5_1", "q4_0", "q4_1", "iq4_nl")
    val FLASH_LABELS = listOf("Auto", "Apagado", "Encendido")
  }
}

class EnginePerfStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("engine_perf", Context.MODE_PRIVATE)

  fun load(): EnginePerfSettings {
    val d = EnginePerfSettings()
    return EnginePerfSettings(
        threads = prefs.getInt("threads", d.threads),
        nBatch = prefs.getInt("n_batch", d.nBatch),
        nUBatch = prefs.getInt("n_ubatch", d.nUBatch),
        flashAttn = prefs.getInt("flash", d.flashAttn),
        kvType = prefs.getInt("kv_type", d.kvType),
        noMmap = prefs.getBoolean("no_mmap", d.noMmap),
        repack = prefs.getBoolean("repack", d.repack),
        contextLength = prefs.getInt("context", d.contextLength),
        maxResponse = prefs.getInt("max_response", d.maxResponse),
        mmproj = prefs.getString("mmproj", d.mmproj) ?: "",
        imageMaxSide = prefs.getInt("image_max_side", d.imageMaxSide),
      )
      .sanitized()
  }

  fun save(s: EnginePerfSettings) {
    val v = s.sanitized()
    prefs
      .edit()
      .putInt("threads", v.threads)
      .putInt("n_batch", v.nBatch)
      .putInt("n_ubatch", v.nUBatch)
      .putInt("flash", v.flashAttn)
      .putInt("kv_type", v.kvType)
      .putBoolean("no_mmap", v.noMmap)
      .putBoolean("repack", v.repack)
      .putInt("context", v.contextLength)
      .putInt("max_response", v.maxResponse)
      .putString("mmproj", v.mmproj)
      .putInt("image_max_side", v.imageMaxSide)
      .apply()
  }
}

/** Proyectores multimodales (mmproj .gguf) importados para dar visión a los modelos GGUF. */
object MmprojFiles {
  fun dir(context: Context): java.io.File = java.io.File(context.filesDir, "mmproj").apply { mkdirs() }

  fun list(context: Context): List<String> =
    dir(context).listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
      ?.map { it.name }?.sorted() ?: emptyList()

  /** Copia el archivo elegido a la app (puede pesar cientos de MB: llamar fuera del hilo principal). */
  fun import(context: Context, uri: android.net.Uri): String? {
    val name =
      context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?: return null
    if (!name.endsWith(".gguf", ignoreCase = true)) return null
    val out = java.io.File(dir(context), name)
    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
      ?: return null
    return name
  }
}

/** Estadísticas del último turno de GGUF, para mostrarlas en la pantalla de rendimiento. */
object LastEnginePerf {
  @Volatile var stats: SmolLM.PerfStats? = null
  @Volatile var loadSeconds: Float = 0f
}
