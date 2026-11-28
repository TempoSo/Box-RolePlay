package com.google.ai.edge.gallery.tts

import android.content.Context
import java.io.File

/** Tipo de modelo de voz: Pocket (clona una muestra), Kokoro y Supertonic (voces incluidas). */
enum class TtsModelType(val label: String) {
  POCKET("Pocket TTS"),
  KOKORO("Kokoro"),
  SUPERTONIC("Supertonic TTS"),
}

/** Un modelo de voz instalado: carpeta propia dentro de `tts/models/` (o el modelo antiguo `tts/pocket`). */
data class TtsModelInfo(
  val id: String,
  val label: String,
  val dir: File,
  val complete: Boolean,
  val sizeMb: Long,
  val type: TtsModelType = TtsModelType.POCKET,
  val missing: List<String> = emptyList(),
)

/**
 * Biblioteca de modelos de voz: se pueden tener varios (p. ej. español e inglés) y elegir cuál se usa.
 * El modelo activo se guarda en preferencias; [TtsFiles.modelDir] devuelve su carpeta.
 */
object TtsModels {
  /** Id del modelo que había antes de existir la biblioteca (carpeta `tts/pocket`). */
  const val LEGACY_ID = "pocket"
  private const val LABEL_FILE = "label.txt"

  /** Id seguro para usar de nombre de carpeta. Lógica pura. */
  fun slug(text: String): String {
    val s = text.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return if (s.isEmpty()) "modelo" else s.take(40)
  }

  /** [base] si está libre; si no, base_2, base_3… Lógica pura. */
  fun uniqueId(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var i = 2
    while ("${base}_$i" in taken) i++
    return "${base}_$i"
  }

  private fun root(context: Context) = File(context.filesDir, "tts/models").apply { mkdirs() }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("tts_models", Context.MODE_PRIVATE)

  fun dirOf(context: Context, id: String): File =
    if (id == LEGACY_ID) File(context.filesDir, "tts/pocket") else File(root(context), id)

  fun list(context: Context): List<TtsModelInfo> {
    val out = mutableListOf<TtsModelInfo>()
    val legacy = dirOf(context, LEGACY_ID)
    if (legacy.isDirectory && legacy.listFiles()?.any { PocketModelFiles.isPart(it.name) } == true) {
      out.add(info(LEGACY_ID, legacy, "Modelo anterior"))
    }
    root(context).listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach {
      out.add(info(it.name, it, it.name))
    }
    return out
  }

  fun typeOf(dir: File): TtsModelType =
    when {
      SupertonicModelFiles.looksLike(dir) -> TtsModelType.SUPERTONIC
      KokoroModelFiles.looksLike(dir) -> TtsModelType.KOKORO
      else -> TtsModelType.POCKET
    }

  /** El modelo activo (el elegido, o el primero completo), o null si no hay ninguno. */
  fun active(context: Context): TtsModelInfo? {
    val id = activeId(context)
    return list(context).firstOrNull { it.id == id }
  }

  private fun info(id: String, dir: File, fallback: String): TtsModelInfo {
    val label = runCatching { File(dir, LABEL_FILE).readText().trim() }.getOrDefault("").ifEmpty { fallback }
    val size = (dir.listFiles()?.sumOf { it.length() } ?: 0L) shr 20
    val type = typeOf(dir)
    val missing =
      when (type) {
        TtsModelType.SUPERTONIC -> SupertonicModelFiles.missing(dir)
        TtsModelType.KOKORO -> KokoroModelFiles.missing(dir)
        else -> PocketModelFiles.missing(dir)
      }
    return TtsModelInfo(id, label, dir, missing.isEmpty(), size, type, missing)
  }

  /** Id del modelo en uso; si el guardado ya no existe, el primero completo (o el antiguo). */
  fun activeId(context: Context): String {
    val models = list(context)
    val saved = prefs(context).getString("active", null)
    return models.firstOrNull { it.id == saved }?.id
      ?: models.firstOrNull { it.complete }?.id
      ?: saved
      ?: LEGACY_ID
  }

  fun setActive(context: Context, id: String) {
    prefs(context).edit().putString("active", id).apply()
    TtsManager.release() // recarga el motor con el modelo elegido
  }

  /** Carpeta nueva y vacía para un modelo con este nombre; guarda la etiqueta legible. */
  fun create(context: Context, label: String): TtsModelInfo {
    val taken = list(context).map { it.id }.toSet() + LEGACY_ID
    val id = uniqueId(slug(label), taken)
    val dir = File(root(context), id).apply { mkdirs() }
    File(dir, LABEL_FILE).writeText(label)
    return TtsModelInfo(id, label, dir, false, 0)
  }

  fun delete(context: Context, id: String): Boolean {
    val dir = dirOf(context, id)
    val ok = dir.deleteRecursively()
    if (ok) TtsManager.release()
    return ok
  }
}
