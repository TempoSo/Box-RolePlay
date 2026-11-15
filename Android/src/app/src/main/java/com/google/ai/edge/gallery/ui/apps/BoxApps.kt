package com.google.ai.edge.gallery.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.home.BoxScaffold

/**
 * Una mini-app de Box: funciona por sí sola (voz, visión, imágenes, transcripción, benchmark…)
 * o combinada dentro de Roleplay. Para añadir una app nueva basta agregarla a [BoxAppRegistry]
 * y manejar su id en la navegación (GalleryNavGraph, `onOpenApp`).
 */
data class BoxApp(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val colorA: Color,
    val colorB: Color,
)

object BoxAppIds {
    const val ROLEPLAY = "roleplay"
    const val SCENARIOS = "scenarios"
    const val CHAT = "chat"
    const val TTS = "tts"
    const val VISION = "vision"
    const val IMAGE_GEN = "image_gen"
    const val TRANSCRIBE = "transcribe"
    const val AUDIO_ASK = "audio_ask"
    const val PROMPT_LAB = "prompt_lab"
    const val BENCHMARK = "benchmark"
    const val MODELS = "models"
    const val HISTORY = "history"
}

object BoxAppRegistry {
    val all: List<BoxApp> = listOf(
        BoxApp(BoxAppIds.ROLEPLAY, "Personajes", "Habla 1 a 1 con voz, visión y memoria", Icons.Rounded.Forum, Color(0xFF7B52E0), Color(0xFF2E2F7A)),
        BoxApp(BoxAppIds.SCENARIOS, "Roleplay", "Escenas con varios personajes: tú controlas a uno", Icons.Rounded.Groups, Color(0xFFE05297), Color(0xFF7A2E55)),
        BoxApp(BoxAppIds.CHAT, "Chat libre", "Conversa directo con el modelo", Icons.Rounded.Chat, Color(0xFF3B82F6), Color(0xFF1E3A8A)),
        BoxApp(BoxAppIds.TTS, "Texto a voz", "Pocket TTS por separado: escribe y escucha", Icons.Rounded.RecordVoiceOver, Color(0xFF2DD4BF), Color(0xFF0F766E)),
        BoxApp(BoxAppIds.VISION, "Visión", "Describe y pregunta sobre imágenes", Icons.Rounded.RemoveRedEye, Color(0xFFF97316), Color(0xFF9A3412)),
        BoxApp(BoxAppIds.IMAGE_GEN, "Generador de imágenes", "Crea imágenes en tu teléfono", Icons.Rounded.Image, Color(0xFFEC4899), Color(0xFF9D174D)),
        BoxApp(BoxAppIds.TRANSCRIBE, "Transcripción", "Voz a texto sin conexión (Whisper)", Icons.Rounded.Subtitles, Color(0xFF8B5CF6), Color(0xFF4C1D95)),
        BoxApp(BoxAppIds.AUDIO_ASK, "Preguntar con audio", "Habla o sube audio al modelo", Icons.Rounded.Mic, Color(0xFF22C55E), Color(0xFF166534)),
        BoxApp(BoxAppIds.PROMPT_LAB, "Laboratorio de prompts", "Prueba prompts de una sola vez", Icons.Rounded.Science, Color(0xFFEAB308), Color(0xFF854D0E)),
        BoxApp(BoxAppIds.BENCHMARK, "Benchmark de modelos", "Mide la velocidad de tus modelos", Icons.Rounded.Speed, Color(0xFFEF4444), Color(0xFF7F1D1D)),
        BoxApp(BoxAppIds.MODELS, "Gestor de modelos", "Descarga, importa y borra modelos", Icons.Rounded.Memory, Color(0xFF06B6D4), Color(0xFF155E75)),
        BoxApp(BoxAppIds.HISTORY, "Historial", "Tus conversaciones anteriores", Icons.Rounded.History, Color(0xFF64748B), Color(0xFF1E293B)),
    )

    /** Apps fijadas en el inicio (en este orden). */
    val pinned: List<String> = listOf(BoxAppIds.ROLEPLAY, BoxAppIds.SCENARIOS, BoxAppIds.TTS, BoxAppIds.VISION)

    fun byId(id: String): BoxApp? = all.firstOrNull { it.id == id }
}

/** Icono de app: cuadrado redondeado con degradado y el símbolo en blanco. */
@Composable
fun BoxAppIcon(app: BoxApp, size: androidx.compose.ui.unit.Dp = 84.dp) {
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.linearGradient(listOf(app.colorA, app.colorB))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(app.icon, contentDescription = app.name, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun BoxAppTile(app: BoxApp, onClick: () -> Unit, iconSize: androidx.compose.ui.unit.Dp = 84.dp) {
    Column(
        Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxAppIcon(app, iconSize)
        Spacer(Modifier.height(8.dp))
        Text(
            app.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Pantalla "Apps": cuadrícula con búsqueda; cada app se abre por separado. */
@Composable
fun BoxAppsScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = BoxAppRegistry.all.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }
    BoxScaffold(title = "Apps", subtitle = "Cada app funciona sola; Roleplay las junta todas", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { app ->
                    BoxAppTile(app, onClick = { onOpenApp(app.id) }, iconSize = 92.dp)
                }
            }
        }
    }
}
