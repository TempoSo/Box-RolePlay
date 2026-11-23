package com.google.ai.edge.gallery.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.VisionSettings
import com.google.ai.edge.gallery.data.characters.VisionStore

/**
 * Ajustes de la app de visión dedicada (inspirados en Layla / MobileVLM): longitud de respuesta,
 * prompt del sistema e instrucción al enviar solo una imagen. No afectan al chat ni al roleplay.
 */
@Composable
fun VisionSettingsDialog(
  initial: VisionSettings,
  onDismiss: () -> Unit,
  onSaved: (VisionSettings) -> Unit,
) {
  val context = LocalContext.current
  val store = remember { VisionStore(context) }
  var tokens by remember { mutableStateOf(initial.maxResponseTokens.toString()) }
  var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
  var instruction by remember { mutableStateOf(initial.imageInstruction) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ajustes de visión") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "Solo se aplican a la app Visión. El chat y el roleplay no cambian.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = tokens,
          onValueChange = { tokens = it.filter { c -> c.isDigit() || c == '-' }.take(5) },
          label = { Text("Tokens máximos por descripción (-1 = sin límite)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(256, 512, 1024, 2048).forEach { preset ->
            FilterChip(
              selected = tokens == preset.toString(),
              onClick = { tokens = preset.toString() },
              label = { Text("$preset") },
            )
          }
        }
        Text(
          "Por defecto 256. Bajarlo acelera la respuesta; subirlo permite descripciones más largas.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = systemPrompt,
          onValueChange = { systemPrompt = it },
          label = { Text("Prompt del sistema") },
          minLines = 3,
          maxLines = 6,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = instruction,
          onValueChange = { instruction = it },
          label = { Text("Instrucción al enviar solo una imagen") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          val reset = VisionSettings()
          store.save(reset)
          onSaved(reset)
        }
      ) {
        Text("Restablecer")
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val saved =
            VisionSettings(
                maxResponseTokens = tokens.toIntOrNull() ?: initial.maxResponseTokens,
                systemPrompt = systemPrompt,
                imageInstruction = instruction,
              )
              .sanitized()
          store.save(saved)
          onSaved(saved)
        }
      ) {
        Text("Guardar")
      }
    },
  )
}
