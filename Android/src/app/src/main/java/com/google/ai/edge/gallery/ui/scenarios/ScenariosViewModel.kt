package com.google.ai.edge.gallery.ui.scenarios

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.characters.PersonaRepository
import com.google.ai.edge.gallery.data.characters.Scenario
import com.google.ai.edge.gallery.data.characters.ScenarioStore
import com.google.ai.edge.gallery.data.characters.ScenePromptBuilder
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Escenas de roleplay con varios personajes: lista, edición y apertura del chat de la escena. */
@HiltViewModel
class ScenariosViewModel @Inject constructor(
    @ApplicationContext context: Context,
    characterRepository: CharacterRepository,
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val store = ScenarioStore(context)

    private val _scenarios = MutableStateFlow(store.all())
    val scenarios: StateFlow<List<Scenario>> = _scenarios.asStateFlow()

    val characters: StateFlow<List<RoleplayCharacter>> =
        characterRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private fun refresh() { _scenarios.value = store.all() }

    fun get(id: String): Scenario? = store.get(id)

    fun save(s: Scenario) {
        viewModelScope.launch(Dispatchers.IO) { store.save(s); refresh() }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.delete(id); refresh() }
    }

    /** Crea el chat de la escena y devuelve el id de la conversación. */
    fun startScene(scenario: Scenario, modelName: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val chars = characters.value.filter { it.id in scenario.participantIds }
                if (chars.isEmpty()) {
                    _message.value = "Esta escena no tiene personajes: añade al menos uno."
                    return@launch
                }
                val persona = personaRepository.getActive()
                val prompt = ScenePromptBuilder.build(
                    sceneName = scenario.name,
                    scenePrompt = scenario.prompt,
                    members = chars.map { ScenePromptBuilder.Member(it.name, it.description, it.personality) },
                    userName = persona?.name ?: "User",
                    userDescription = persona?.description ?: "",
                    narrator = scenario.narrator,
                )
                val conv = withContext(Dispatchers.IO) {
                    chatRepository.createConversation(
                        title = scenario.name.ifBlank { "Escena" },
                        taskType = "llm_chat",
                        modelName = modelName,
                        systemPrompt = prompt,
                        characterId = "scene:${scenario.id}",
                        personaId = persona?.id ?: "",
                    )
                }
                onReady(conv.id)
            } catch (e: Exception) {
                _message.value = "No se pudo abrir la escena: ${e.message ?: "error desconocido"}"
            }
        }
    }
}
