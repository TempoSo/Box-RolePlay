package com.google.ai.edge.gallery.ui.common.chat

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.tts.TtsManager
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.HoldToDictateViewModel
import kotlinx.coroutines.delay

/**
 * Botón de conversación manos libres: un toque empieza a escuchar, cada transcripción se envía como
 * mensaje y, cuando el modelo termina y el TTS deja de hablar, vuelve a escuchar sin tocar nada.
 * Un segundo toque corta el bucle y la voz. Solo se usa en el chat de roleplay.
 *
 * Por ahora el reconocimiento es el del sistema ( igual que «mantén para hablar» ). Es suficiente
 * para probar el bucle; el siguiente paso será sustituirlo por SenseVoice local del AAR.
 */
@Composable
fun HandsFreeButton(
  enabled: Boolean,
  inProgress: Boolean,
  onTranscript: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val dictate: HoldToDictateViewModel = hiltViewModel()
  var activo by remember { mutableStateOf(false) }
  var esperando by remember { mutableStateOf(false) }
  var reiniciando by remember { mutableStateOf(false) }
  val ttsSpeaking by TtsManager.speaking.collectAsState()

  fun iniciar() {
    esperando = false
    dictate.startHandsFree(
      onDone = { texto ->
        if (texto.isBlank()) {
          dictate.restartListening()
        } else {
          esperando = true
          onTranscript(texto)
        }
      },
      onAmplitudeChanged = {},
      onError = {
        activo = false
        esperando = false
        dictate.stopHandsFree()
      },
    )
    activo = true
  }

  fun parar() {
    activo = false
    esperando = false
    dictate.stopHandsFree()
    TtsManager.stop()
  }

  val permisoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
      if (concedido) iniciar() else parar()
    }

  fun alPulsar() {
    if (activo) {
      parar()
    } else if (tienePermiso(context)) {
      iniciar()
    } else {
      permisoLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  LaunchedEffect(activo, esperando, inProgress, ttsSpeaking) {
    if (!reiniciando && HandsFreePolicy.debeReiniciar(activo, esperando, inProgress, ttsSpeaking)) {
      reiniciando = true
      try {
        // Pequeña pausa para no relanzar el reconocedor justo al cerrarse el anterior.
        delay(350)
        dictate.restartListening()
        esperando = false
      } finally {
        reiniciando = false
      }
    }
  }

  IconButton(
    onClick = { alPulsar() },
    enabled = enabled,
    modifier = modifier,
    colors =
      IconButtonDefaults.iconButtonColors(
        containerColor =
          if (activo) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor =
          if (activo) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      ),
  ) {
    Icon(
      imageVector = if (activo) Icons.Rounded.MicOff else Icons.Rounded.Mic,
      contentDescription = if (activo) "Detener conversación manos libres" else "Conversación manos libres",
    )
  }
}

private fun tienePermiso(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED
