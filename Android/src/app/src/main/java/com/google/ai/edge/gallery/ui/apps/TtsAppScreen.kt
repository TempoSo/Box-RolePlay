package com.google.ai.edge.gallery.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.ui.home.BoxScaffold
import com.google.ai.edge.gallery.ui.roleplay.TtsSettingsDialog

/** App independiente de voz: escribe un texto y escúchalo con Pocket TTS, sin modelo de lenguaje. */
@Composable
fun TtsAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val speaking by TtsManager.speaking.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { TtsManager.currentCharacterId = "" }
    val ready = remember(showSettings) { TtsManager.isModelReady(context) }

    if (showSettings) TtsSettingsDialog(onDismiss = { showSettings = false })

    BoxScaffold(title = "Texto a voz", subtitle = "Pocket TTS · funciona sin cargar ningún modelo de lenguaje", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ready) {
                Text(
                    "Aún falta el modelo de voz: abre «Ajustes de voz», importa la carpeta de Pocket TTS y una muestra de voz (.wav).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Escribe lo que quieres escuchar") },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = text.isNotBlank() && !speaking,
                    onClick = { TtsManager.speak(context, text) },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.PlayArrow, null); Text("Leer") }
                OutlinedButton(
                    enabled = speaking,
                    onClick = { TtsManager.stop() },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Stop, null); Text("Parar") }
            }
            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Settings, null); Text("Ajustes de voz")
            }
        }
    }
}
