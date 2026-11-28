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
  /** Hilos para leer el prompt (prefill). 0 = los mismos que la generación. */
  val threadsBatch: Int = 0,
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
  /** Cache en disco del bloque de sistema (ficha del personaje); sin contenido de la conversacion. */
  val kvCacheSystem: Boolean = true,
  /** Cache en disco de la conversacion completa (queda sin cifrar en el almacenamiento). */
  val kvCacheChat: Boolean = false,
) {
  fun sanitized(): EnginePerfSettings =
    copy(
      threads = threads.coerceIn(0, 16),
      threadsBatch = threadsBatch.coerceIn(0, 16),
      nBatch = nBatch.coerceIn(1, 4096),
      nUBatch = nUBatch.coerceIn(1, 4096),
      flashAttn = flashAttn.coerceIn(0, 2),
      kvType = kvType.coerceIn(0, KV_TYPES.size - 1),
      contextLength = if (contextLength <= 0) 0 else contextLength.coerceIn(256, 131072),
      maxResponse = if (maxResponse <= 0) -1 else maxResponse.coerceAtMost(32768),
      imageMaxSide = imageMaxSide.coerceIn(128, 2048),
    )

  /**
   * BUG-018: aplica un preset dejando intactos los campos que no guardó (null = "no cambiar").
   * Los presets anteriores a este campo siguen siendo válidos.
   */
  fun overriddenBy(preset: EnginePreset): EnginePerfSettings =
    copy(
      threads = preset.threads ?: threads,
      nBatch = preset.nBatch ?: nBatch,
      nUBatch = preset.nUBatch ?: nUBatch,
      flashAttn = preset.flashAttn ?: flashAttn,
      kvType = preset.kvType ?: kvType,
      noMmap = preset.noMmap ?: noMmap,
      repack = preset.repack ?: repack,
      contextLength = preset.contextLength ?: contextLength,
      maxResponse = preset.maxResponse ?: maxResponse,
      imageMaxSide = preset.imageMaxSide ?: imageMaxSide,
    ).sanitized()

  fun toEngineOptions(): SmolLM.EngineOptions =
    SmolLM.EngineOptions(
      nBatch = nBatch,
      nUBatch = nUBatch,
      nThreadsBatch = threadsBatch,
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
        threadsBatch = prefs.getInt("threads_batch", d.threadsBatch),
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
        kvCacheSystem = prefs.getBoolean("kv_cache_system", d.kvCacheSystem),
        kvCacheChat = prefs.getBoolean("kv_cache_chat", d.kvCacheChat),
      )
      .sanitized()
  }

  /** Selecciona un proyector descargado sin pisar el resto de ajustes de inferencia. */
  fun setMmproj(fileName: String) {
    save(load().copy(mmproj = fileName))
  }

  fun save(s: EnginePerfSettings) {
    val v = s.sanitized()
    prefs
      .edit()
      .putInt("threads", v.threads)
      .putInt("threads_batch", v.threadsBatch)
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
      .putBoolean("kv_cache_system", v.kvCacheSystem)
      .putBoolean("kv_cache_chat", v.kvCacheChat)
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

/**
 * Métricas reales del último turno de LiteRT-LM. Se cuentan tokens de prefilling del motor, no
 * trozos de texto, y **no son comparables** con las de GGUF (BUG-019): sirven para ver si el
 * historial se está releyendo o si se reutiliza la caché KV de la conversación.
 */
object LiteRtPerf {
  @Volatile var prefillTokens: Int = 0
  @Volatile var kvTokens: Int = 0
  @Volatile var timeToFirstTokenSeconds: Double = 0.0
  @Volatile var lastResetReason: String = ""

  fun clear() {
    prefillTokens = 0
    kvTokens = 0
    timeToFirstTokenSeconds = 0.0
    lastResetReason = ""
  }
}

/** Carpeta y borrado de las caches de KV guardadas en disco. */
object KvCacheFiles {
  fun dir(context: Context): java.io.File = java.io.File(context.applicationContext.cacheDir, "kv").apply { mkdirs() }

  fun sizeMb(context: Context): Long = (dir(context).listFiles()?.sumOf { it.length() } ?: 0L) shr 20

  /** Borra solo las cachés de conversación (.chat); las de ficha (.sys) no llevan contenido del chat. */
  fun clearChats(context: Context): Int {
    var n = 0
    dir(context).listFiles()?.forEach { if (it.name.endsWith(".chat") && it.delete()) n++ }
    return n
  }

  fun clear(context: Context): Int {
    var n = 0
    dir(context).listFiles()?.forEach { if (it.delete()) n++ }
    return n
  }
}
