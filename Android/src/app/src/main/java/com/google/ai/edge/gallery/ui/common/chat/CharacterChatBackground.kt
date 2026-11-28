package com.google.ai.edge.gallery.ui.common.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.gallery.data.characters.CharacterAppearanceStore
import java.io.File

/**
 * Fondo del chat de un personaje. Se dibuja detrás de los mensajes con mucha transparencia para que
 * las burbujas sigan siendo legibles. Los presets son degradados animados; la imagen propia se lee
 * reducida y se muestra fija.
 */
@Composable
fun CharacterChatBackground(characterId: String) {
    if (characterId.isBlank()) return
    val context = LocalContext.current
    val look = remember(characterId, context) { CharacterAppearanceStore(context).load(characterId) }
    if (!look.hasWallpaper) return

    val phase by
        rememberInfiniteTransition(label = "fondo_personaje").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
            label = "fase_fondo",
        )

    Box(modifier = Modifier.fillMaxSize()) {
        if (look.wallpaperId == CharacterAppearanceStore.CUSTOM) {
            val bitmap =
                remember(look.imagePath) {
                    runCatching {
                        val file = File(look.imagePath)
                        if (file.exists()) {
                            BitmapFactory.decodeFile(
                                file.absolutePath,
                                BitmapFactory.Options().apply { inSampleSize = 4 },
                            )?.asImageBitmap()
                        } else {
                            null
                        }
                    }.getOrNull()
                }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.16f },
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            val palette = paletteFor(look.wallpaperId)
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = 0.28f }
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            lerp(palette[0], palette[1], phase),
                                            palette[2],
                                            lerp(palette[1], palette[0], 1f - phase),
                                        ),
                                    start = Offset(0f, phase * 1200f),
                                    end = Offset(1200f, (1f - phase) * 1200f),
                                )
                        )
            )
        }
    }
}

/** Colores tenues: se mezclan con la superficie del tema, no tapan los mensajes. */
private fun paletteFor(id: String): List<Color> =
    when (id) {
        "nebula" ->
            listOf(Color(0xFF2A1B4D), Color(0xFF7B3FA0), Color(0xFF1B2A6B))
        "aurora" ->
            listOf(Color(0xFF123B3A), Color(0xFF2E8B75), Color(0xFF1B3A6B))
        "rain" ->
            listOf(Color(0xFF152238), Color(0xFF3A6C8E), Color(0xFF1F3B52))
        "forest" ->
            listOf(Color(0xFF1B3326), Color(0xFF4B6B3A), Color(0xFF2A3A22))
        else ->
            listOf(Color(0xFF222222), Color(0xFF444444), Color(0xFF333333))
    }
