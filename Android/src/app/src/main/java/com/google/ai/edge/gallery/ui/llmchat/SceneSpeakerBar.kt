package com.google.ai.edge.gallery.ui.llmchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.ScenePromptBuilder

/**
 * Barra de escena sobre el campo de texto: con quién escribes ("Yo", el narrador o un personaje) y
 * un botón "Aleatorio" para que la IA haga hablar a un personaje.
 * [selected]: null = tú mismo; [ScenePromptBuilder.NARRATOR] = narrador; si no, el nombre del personaje.
 */
@Composable
fun SceneSpeakerBar(
    names: List<String>,
    narrator: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    onRandom: () -> Unit,
    enabled: Boolean,
    showMe: Boolean = true,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { AssistChip(onClick = onRandom, enabled = enabled, label = { Text("🎲 Aleatorio") }) }
        if (showMe) {
            item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Yo") }) }
        }
        if (narrator) {
            item {
                FilterChip(
                    selected = selected == ScenePromptBuilder.NARRATOR,
                    onClick = { onSelect(ScenePromptBuilder.NARRATOR) },
                    label = { Text("Narrador") },
                )
            }
        }
        items(names, key = { it }) { n ->
            FilterChip(selected = selected == n, onClick = { onSelect(n) }, label = { Text(n) })
        }
    }
}
