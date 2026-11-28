package com.google.ai.edge.gallery.data.characters

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Apariencia visual de un personaje (pestaña «Appearance» del editor). Se guarda aparte de la tarjeta
 * porque es decoración de Nexo, no un campo de character_card_v2: así no hay que migrar la base de
 * datos y las tarjetas compartidas siguen viajando igual.
 */
data class CharacterAppearance(
  /** Id del fondo animado, o [CharacterAppearanceStore.CUSTOM] para la imagen del usuario. */
  val wallpaperId: String = "",
  /** Ruta de la imagen propia dentro del almacenamiento privado ("" = sin imagen). */
  val imagePath: String = "",
  /** Expresión elegida; se guarda ya para cuando el motor la soporte, hoy no se usa en el prompt. */
  val expression: String = "",
) {
  val hasWallpaper: Boolean get() = wallpaperId.isNotBlank()
}

/** Preferencias y archivos de la apariencia, indexados por id de personaje. */
class CharacterAppearanceStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("character_appearance", Context.MODE_PRIVATE)

  fun load(characterId: String): CharacterAppearance =
    CharacterAppearance(
      wallpaperId = prefs.getString("wallpaper:$characterId", "") ?: "",
      imagePath = prefs.getString("image:$characterId", "") ?: "",
      expression = prefs.getString("expression:$characterId", "") ?: "",
    )

  fun save(characterId: String, appearance: CharacterAppearance) {
    prefs.edit {
      putString("wallpaper:$characterId", appearance.wallpaperId)
      putString("image:$characterId", appearance.imagePath)
      putString("expression:$characterId", appearance.expression)
    }
  }

  companion object {
    const val CUSTOM = "custom"
    val PRESETS = listOf("nebula", "aurora", "rain", "forest")
    val EXPRESSIONS = listOf("Neutral", "Feliz", "Triste", "Enojada", "Sorprendida", "Celosa")

    fun labelsFor(id: String): String =
      when (id) {
        "nebula" -> "Nebulosa"
        "aurora" -> "Aurora"
        "rain" -> "Lluvia"
        "forest" -> "Bosque"
        CUSTOM -> "Imagen propia"
        else -> "Ninguno"
      }

    fun imagesDir(context: Context): File =
      File(context.filesDir, "character_backgrounds").apply { mkdirs() }

    fun imageFile(context: Context, characterId: String): File =
      File(imagesDir(context), "$characterId.jpg")
  }
}
