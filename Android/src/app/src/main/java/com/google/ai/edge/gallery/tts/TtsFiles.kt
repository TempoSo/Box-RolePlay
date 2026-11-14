package com.google.ai.edge.gallery.tts

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Copias locales del modelo y de las muestras de voz (se importan con el selector de Android). */
object TtsFiles {
  fun modelDir(context: Context): File = File(context.filesDir, "tts/pocket").apply { mkdirs() }

  fun voicesDir(context: Context): File = File(context.filesDir, "tts/voices").apply { mkdirs() }

  /**
   * Copia a la app las piezas del modelo Pocket TTS que haya en la carpeta elegida. Devuelve los
   * nombres copiados.
   */
  fun importModelFolder(context: Context, tree: Uri): List<String> {
    val resolver = context.contentResolver
    val treeId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
    val copied = mutableListOf<String>()
    resolver
      .query(
        children,
        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
      )
      ?.use { c ->
        while (c.moveToNext()) {
          val id = c.getString(0)
          val name = c.getString(1)
          if (!PocketModelFiles.isPart(name)) continue
          val doc = DocumentsContract.buildDocumentUriUsingTree(tree, id)
          resolver.openInputStream(doc)?.use { input ->
            File(modelDir(context), name).outputStream().use { input.copyTo(it) }
            copied.add(name)
          }
        }
      }
    return copied
  }

  /** Copia un .wav elegido a la app y devuelve la ruta, o null si no es un WAV legible. */
  fun importVoice(context: Context, uri: Uri, id: String): String? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    com.google.ai.edge.gallery.data.characters.WavReader.parse(bytes) ?: return null
    val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val out = File(voicesDir(context), "$safe.wav")
    out.writeBytes(bytes)
    return out.absolutePath
  }
}
