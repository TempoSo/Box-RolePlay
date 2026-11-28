package com.google.ai.edge.gallery.ui.roleplay

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.BackupContents
import com.google.ai.edge.gallery.data.characters.BackupRepository
import com.google.ai.edge.gallery.data.characters.BackupSection
import com.google.ai.edge.gallery.data.characters.CharacterCardException
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.characters.PersonaRepository
import com.google.ai.edge.gallery.data.characters.Scenario
import com.google.ai.edge.gallery.data.characters.ScenarioStore
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Roleplay: lista de personajes, importar/exportar, guardar y abrir un chat. */
@HiltViewModel
class RoleplayViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val personaRepository: PersonaRepository,
    private val memoryRepository: com.google.ai.edge.gallery.data.characters.MemoryRepository,
    scenarioStore: ScenarioStore,
    private val backup: BackupRepository,
) : ViewModel() {

    /** BUG-006: etiquetas de escenas para los chips del editor de personajes. */
    val scenarios: StateFlow<List<Scenario>> = scenarioStore.scenarios

    val characters: StateFlow<List<RoleplayCharacter>> = repository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personas: StateFlow<List<UserPersona>> = personaRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    /** Mensaje corto para mostrar al usuario (error o confirmación). */
    val message: StateFlow<String?> = _message.asStateFlow()

    // --- Memoria a largo plazo ---
    fun memories(characterId: String) = memoryRepository.observe(characterId)

    fun saveMemory(memory: com.google.ai.edge.gallery.data.local.entities.CharacterMemory) {
        viewModelScope.launch {
            val charName = characters.value.firstOrNull { it.id == memory.characterId }?.name.orEmpty()
            val userName = personaRepository.getActive()?.name.orEmpty()
            val anterior = memoryRepository.getAll(memory.characterId).firstOrNull()?.id.orEmpty()
            memoryRepository.saveStructured(
                memory = memory,
                rawText = memory.content,
                userName = userName,
                charName = charName,
                anteriorId = anterior,
            )
        }
    }

    fun deleteMemory(memory: com.google.ai.edge.gallery.data.local.entities.CharacterMemory) {
        viewModelScope.launch { memoryRepository.delete(memory) }
    }

    fun clearMemories(characterId: String) {
        viewModelScope.launch { memoryRepository.deleteAll(characterId) }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            try {
                val list = repository.importAnyFromUri(uri)
                _message.value =
                    if (list.size == 1) "Personaje importado: ${list[0].name}"
                    else "Se importaron ${list.size} personajes"
            } catch (e: CharacterCardException) {
                _message.value = e.message ?: "No se pudo importar la tarjeta"
            } catch (e: Exception) {
                _message.value = "No se pudo importar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    /** Resultado de abrir un archivo para restaurar: ya hecho (tarjeta suelta) o hay que elegir qué recuperar. */
    sealed class RestorePlan {
        class Done(val message: String) : RestorePlan()
        class Choose(val bytes: ByteArray, val contents: BackupContents) : RestorePlan()
    }

    /**
     * BUG-038: una tarjeta .json/.png se importa tal cual; un .zip se inspecciona y devuelve sus
     * secciones para que el usuario elija. Restaurar fusiona: lo idéntico no se duplica, lo editado
     * se conserva y la versión de la copia se añade como personaje nuevo; nada se borra.
     */
    suspend fun prepareRestore(uri: Uri): RestorePlan =
        try {
            val bytes = repository.readBackupBytes(uri)
            if (!backup.isZip(bytes)) {
                RestorePlan.Done("Personaje importado: ${repository.importFromBytes(bytes).name}")
            } else {
                val contents = backup.inspect(bytes)
                if (contents.sections.isEmpty()) RestorePlan.Done("El .zip no contiene nada que restaurar.")
                else RestorePlan.Choose(bytes, contents)
            }
        } catch (e: CharacterCardException) {
            RestorePlan.Done(e.message ?: "No se pudo importar la tarjeta")
        } catch (e: Exception) {
            RestorePlan.Done("No se pudo restaurar: ${e.message ?: "error desconocido"}")
        }

    suspend fun restoreBackup(bytes: ByteArray, selected: Set<BackupSection>): String =
        try {
            backup.restore(bytes, selected)
        } catch (e: Exception) {
            "No se pudo restaurar: ${e.message ?: "error desconocido"}"
        }

    fun save(character: RoleplayCharacter, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.save(character)
                onDone()
            } catch (e: Exception) {
                _message.value = "No se pudo guardar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun savePersona(persona: UserPersona) {
        viewModelScope.launch {
            try {
                personaRepository.save(persona)
            } catch (e: Exception) {
                _message.value = "No se pudo guardar la persona: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun selectPersona(persona: UserPersona) {
        viewModelScope.launch {
            try {
                personaRepository.setActive(persona)
            } catch (e: Exception) {
                _message.value = "No se pudo cambiar de persona: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun deletePersona(persona: UserPersona) {
        viewModelScope.launch {
            try {
                personaRepository.delete(persona)
            } catch (e: Exception) {
                _message.value = "No se pudo borrar la persona: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun delete(character: RoleplayCharacter) {
        viewModelScope.launch {
            try {
                repository.delete(character)
            } catch (e: Exception) {
                _message.value = "No se pudo borrar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    suspend fun getById(id: String): RoleplayCharacter? = repository.getById(id)

    /** Copia de seguridad de todos los personajes (.zip) lista para compartir. */
    suspend fun backupZip(): ByteArray = backup.exportZip()

    fun exportJson(character: RoleplayCharacter): String = repository.exportJson(character)

    /** Datos para la hoja de detalle: saludos, último chat y estadísticas. */
    class CharacterDetail(
        val greetings: List<String>,
        val latestConversationId: String?,
        val latestModel: String?,
        val chats: Int,
        val messages: Int,
    )

    fun loadDetail(character: RoleplayCharacter, onLoaded: (CharacterDetail) -> Unit) {
        viewModelScope.launch {
            try {
                val persona = personaRepository.getActive()
                val greetings = repository.greetingsFor(character, persona)
                val last = repository.latestChat(character.id)
                val (chats, msgs) = repository.chatStats(character.id)
                onLoaded(CharacterDetail(greetings, last?.id, last?.modelName, chats, msgs))
            } catch (e: Exception) {
                _message.value = "No se pudo cargar el personaje: ${e.message ?: "error desconocido"}"
            }
        }
    }

    /** Crea el chat y devuelve el id de la conversación (null si falló). */
    fun startChat(
        character: RoleplayCharacter,
        modelName: String,
        greetingOverride: String? = null,
        onReady: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val conversation = repository.startChat(character, modelName, personaRepository.getActive(), greetingOverride = greetingOverride)
                onReady(conversation.id)
            } catch (e: Exception) {
                _message.value = "No se pudo abrir el chat: ${e.message ?: "error desconocido"}"
            }
        }
    }
}
