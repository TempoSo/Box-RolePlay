package com.google.ai.edge.gallery.tts

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Copias locales del modelo y de las muestras de voz (se importan con el selector de Android). */
object TtsFiles {
  /** Carpeta del modelo de voz activo (ver [TtsModels]). */
  fun modelDir(context: Context): File = TtsModels.dirOf(context, TtsModels.activeId(context)).apply { mkdirs() }

  /** Carpeta de trabajo para descargas en curso. */
  fun stagingDir(context: Context): File = File(context.filesDir, "tts/pocket_dl").apply { mkdirs() }

  fun voicesDir(context: Context): File = File(context.filesDir, "tts/voices").apply { mkdirs() }

  /**
   * Copia a la app las piezas del modelo Pocket TTS que haya en la carpeta elegida. Devuelve los
   * nombres copiados.
   */
  fun folderName(context: Context, tree: Uri): String? =
    runCatching {
      val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
      context.contentResolver
        .query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

  fun importModelFolder(context: Context, tree: Uri, target: File = modelDir(context)): List<String> {
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
            File(target, name).outputStream().use { input.copyTo(it) }
            copied.add(name)
          }
        }
      }
    return copied
  }
}
