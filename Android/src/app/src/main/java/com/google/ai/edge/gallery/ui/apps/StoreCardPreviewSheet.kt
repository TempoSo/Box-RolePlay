package com.google.ai.edge.gallery.ui.apps

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.toCard
import com.google.ai.edge.gallery.store.StorePreview
import com.google.ai.edge.gallery.ui.roleplay.ExpandableBlock

/**
 * Ficha de un personaje de la tienda antes de importarlo: mismo resumen que se muestra al tocar un
 * personaje ya guardado (descripción, personalidad, escenario, notas y saludos), pero con «Importar»
 * en lugar de «Nuevo chat». La tarjeta ya está descargada; aquí solo se decide si se guarda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreCardPreviewSheet(
  preview: StorePreview,
  alreadyAdded: Boolean,
  importing: Boolean,
  onDismiss: () -> Unit,
  onImport: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scrollState = rememberScrollState()
  val character = preview.character
  val card = preview.card
  val greetings = listOf(character.firstMessage).filter { it.isNotBlank() } + character.toCard().alternateGreetings
  // Si la tarjeta se descargó como PNG, su imagen es el avatar; si no, se usa la URL de la tienda.
  val localAvatar = remember(preview.bytes) {
    runCatching {
      BitmapFactory.decodeByteArray(
        preview.bytes,
        0,
        preview.bytes.size,
        BitmapFactory.Options().apply { inSampleSize = 4 },
      )?.asImageBitmap()
    }.getOrNull()
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
      Column(
        // Sin overscroll propio: con fichas cortas el estiramiento del scroll peleaba con el drag
        // del sheet y producia el "levantamiento fantasma" al arrastrar hacia abajo.
        Modifier.weight(1f, fill = false).verticalScroll(scrollState, overscrollEffect = null),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val avatarShape = RoundedCornerShape(50)
          val avatarModifier = Modifier.size(64.dp).clip(avatarShape)
          if (localAvatar != null) {
            Image(localAvatar, character.name, contentScale = ContentScale.Crop, modifier = avatarModifier)
          } else {
            RemoteImage(card.avatarUrl, avatarModifier, avatarShape)
          }
          Column {
            Text(character.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
              "por @${card.author}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (card.tagline.isNotBlank()) {
          Text(
            card.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (card.stars > 0) Text("★ ${shortCount(card.stars)}", style = MaterialTheme.typography.labelMedium)
          if (card.tokens > 0) Text("${shortCount(card.tokens)} tokens", style = MaterialTheme.typography.labelMedium)
          if (card.nsfw) {
            Text(
              "+18",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }

        if (card.tags.isNotEmpty()) {
          Text(
            card.tags.take(8).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        ExpandableBlock("Descripción", character.description)
        ExpandableBlock("Personalidad", character.personality)
        ExpandableBlock("Escenario actual", character.scenario, emptyText = "No hay escenario establecido.")
        if (character.creatorNotes.isNotBlank()) ExpandableBlock("Notas del creador", character.creatorNotes)
        if (greetings.isNotEmpty()) {
          ExpandableBlock(
            if (greetings.size > 1) "Saludos (${greetings.size})" else "Saludo inicial",
            greetings.mapIndexed { i, greeting -> "${i + 1}. $greeting" }.joinToString("\n\n"),
          )
        }
        Spacer(Modifier.height(4.dp))
      }

      Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        Button(
          onClick = onImport,
          enabled = !alreadyAdded && !importing,
          modifier = Modifier.weight(1f),
        ) {
          if (importing) {
            CircularProgressIndicator(
              Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Text(if (alreadyAdded) "Ya importado" else "Importar")
          }
        }
      }
    }
  }
}

private fun shortCount(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000f) else "$n"
