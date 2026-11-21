package com.google.ai.edge.gallery.ui.roleplay

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.ai.edge.gallery.data.characters.ShareFileName
import java.io.File

/**
 * BUG-039 / BUG-007: comparte la ficha como un archivo `Nombre.json` real (caché + FileProvider) para
 * poder guardarlo o enviarlo; el JSON va también como texto para que siga existiendo «Copiar texto».
 */
object CharacterShare {
    fun shareJson(context: Context, characterName: String, json: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, ShareFileName.forCharacter(characterName)).apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, json)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, "Compartir personaje"))
    }
}
