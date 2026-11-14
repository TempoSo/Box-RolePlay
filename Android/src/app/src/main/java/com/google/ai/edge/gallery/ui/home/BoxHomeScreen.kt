package com.google.ai.edge.gallery.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/** Etiqueta de sección (MAYÚSCULAS pequeñas), como en las pantallas de ajustes. */
@Composable
fun BoxSectionLabel(text: String) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
  )
}

/** Tarjeta de acción: icono, título, subtítulo y flecha. Sirve para menús y para elegir opciones. */
@Composable
fun BoxActionCard(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  onClick: () -> Unit,
  selected: Boolean = false,
  dashed: Boolean = false,
  enabled: Boolean = true,
  trailing: @Composable (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(18.dp)
  Card(
    modifier =
      Modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (enabled) it.clickable(onClick = onClick) else it },
    shape = shape,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceContainerHigh
      ),
    border =
      when {
        selected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        dashed -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        else -> null
      },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint =
          if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
      Column(Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
          Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (trailing != null) trailing()
      else if (enabled && !selected) {
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

/** Barra superior con flecha de volver, título y subtítulo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScaffold(
  title: String,
  subtitle: String? = null,
  onBack: () -> Unit,
  content: @Composable () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
              Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
      )
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) { content() }
  }
}

/**
 * Pantalla de inicio: un toque para abrir un chat libre con el modelo elegido (como el chat
 * normal de Box), y accesos a Personajes, Historial y Configuración.
 */
@Composable
fun BoxHomeScreen(
  modelLabel: String?,
  onChat: () -> Unit,
  onCharacters: () -> Unit,
  onHistory: () -> Unit,
  onSettings: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(28.dp))
    Text(
      "BOX ROLEPLAY",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      "Toca para chatear",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.clickable(onClick = onChat).padding(8.dp),
    )
    Image(
      painter = painterResource(R.drawable.ic_box_mascot),
      contentDescription = "Toca para chatear",
      modifier = Modifier.size(190.dp).clickable(onClick = onChat),
    )
    Text(
      if (modelLabel != null) "Modelo: $modelLabel" else "Aún no hay modelo: elígelo en Configuración",
      style = MaterialTheme.typography.bodyMedium,
      color =
        if (modelLabel != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
    )
    BoxActionCard(
      icon = Icons.Rounded.People,
      title = "Elige un personaje",
      subtitle = "Habla con tus personajes de roleplay",
      onClick = onCharacters,
    )
    BoxActionCard(
      icon = Icons.Rounded.History,
      title = "Historial",
      subtitle = "Tus conversaciones anteriores",
      onClick = onHistory,
    )
    BoxActionCard(
      icon = Icons.Rounded.Settings,
      title = "Configuración",
      subtitle = "Modelo, visión, voz, rendimiento y copias de seguridad",
      onClick = onSettings,
    )
    Spacer(Modifier.height(24.dp))
  }
}
