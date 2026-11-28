package com.google.ai.edge.gallery.data.local

import com.google.ai.edge.gallery.data.characters.Swipes
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import android.content.Context
import com.google.ai.edge.gallery.data.characters.AutoMemory
import com.google.ai.edge.gallery.data.characters.MemoryGraphBuilder
import com.google.ai.edge.gallery.data.characters.MemoryRepository
import com.google.ai.edge.gallery.data.characters.MemoryRetriever
import com.google.ai.edge.gallery.data.characters.NOTAS_PUENTE
import com.google.ai.edge.gallery.data.characters.RawMemory
import com.google.ai.edge.gallery.data.characters.RawMemoryStore
import com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore
import com.google.ai.edge.gallery.data.characters.Scenario
import com.google.ai.edge.gallery.data.characters.ScenarioStore
import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.PersonaDao
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Box: Repository for encrypted chat persistence.
 * Bridges the in-memory ChatViewModel messages with the SQLCipher-encrypted Room database.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao,
    private val personaDao: PersonaDao,
    private val scenarioStore: ScenarioStore,
    private val memoryRepository: MemoryRepository,
    @ApplicationContext private val context: Context,
) {
    /** Nombres de los personajes de una escena ("scene:<id>"), o lista vacía si no es una escena. */
    suspend fun sceneMemberNames(conversationId: String): List<String> {
        val cid = conversationDao.getConversationById(conversationId)?.characterId ?: return emptyList()
        if (!cid.startsWith("scene:")) return emptyList()
        val scenario = scenarioStore.get(cid.removePrefix("scene:")) ?: return emptyList()
        return scenario.participantIds.mapNotNull { characterDao.getById(it)?.name }
    }

    /** Opciones de la escena de la conversación (null si no es una escena). */
    suspend fun sceneOptions(conversationId: String): Scenario? {
        val cid = conversationDao.getConversationById(conversationId)?.characterId ?: return null
        if (!cid.startsWith("scene:")) return null
        return scenarioStore.get(cid.removePrefix("scene:"))
    }

    /**
     * Memoria automática tras cada respuesta: guarda las frases clave del último intercambio.
     * Empieza con el 2.º mensaje del usuario, no usa el modelo y no inventa texto.
     */
    suspend fun captureAutoMemory(conversationId: String) {
        val conv = conversationDao.getConversationById(conversationId) ?: return
        val charId = conv.characterId
        if (charId.isBlank() || charId.startsWith("scene:")) return
        val settings = RoleplaySettingsStore(context)
        if (!settings.memoryEnabled(charId) || !settings.autoMemory(charId)) return
        val msgs = messageDao.getMessagesForConversationSync(conversationId)
        if (msgs.count { it.role == "user" } < 2) return
        val reply = msgs.lastOrNull { it.role == "assistant" } ?: return
        val user = msgs.lastOrNull { it.role == "user" } ?: return
        val charName = characterDao.getById(charId)?.name ?: return
        val userName = personaDao.getActive()?.name ?: "User"
        val text = AutoMemory.fromExchange(userName, charName, user.content, reply.content) ?: return
        val raw = RawMemoryStore(context)
        if (AutoMemory.isDuplicate(text, raw.forCharacter(charId).take(5).map { it.text })) return
        raw.add(RawMemory(characterId = charId, text = text))
        // La nota ya nace como recuerdo estructurado: no hace falta absorberla para que cuente.
        // sourceConversationId evita que se inyecte en el chat que la acaba de generar (anti-eco
        // y firma estable para la caché de prefijo); en el chat siguiente queda elegible.
        val claves = MemoryRetriever.tokens(text).sortedByDescending { it.length }.take(5)
        val anterior = memoryRepository.getAll(charId).firstOrNull()?.id.orEmpty()
        memoryRepository.saveStructured(
            memory =
                CharacterMemory(
                    characterId = charId,
                    content = text,
                    topic = MemoryGraphBuilder.sugerirTopic(text),
                    keysJson = claves.joinToString(",", "[", "]") { "\"$it\"" },
                    sourceConversationId = conversationId,
                ),
            rawText = text,
            userName = userName,
            charName = charName,
            anteriorId = anterior,
        )
    }

    /**
     * Notas puente para el próximo chat: las más recientes del personaje (máximo 2), ya en orden
     * de más nueva a más vieja. Se leen en IO porque viven en un fichero JSON, no en Room.
     */
    suspend fun recentRawMemories(characterId: String, limit: Int = NOTAS_PUENTE): List<RawMemory> =
        withContext(Dispatchers.IO) {
            if (characterId.isBlank() || characterId.startsWith("scene:")) emptyList()
            else RawMemoryStore(context).forCharacter(characterId).take(limit.coerceIn(0, NOTAS_PUENTE))
        }

    /** Roleplay: JSON del lorebook (character_book) del personaje, o "" si no tiene. */
    suspend fun getCharacterBookJson(characterId: String): String =
        characterDao.getById(characterId)?.characterBookJson ?: ""

    // --- Conversations ---

    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    suspend fun getAllConversationsSync(): List<Conversation> = conversationDao.getAllConversationsSync()

    fun getConversationsByTask(taskType: String): Flow<List<Conversation>> =
        conversationDao.getConversationsByTask(taskType)

    suspend fun getConversationById(id: String): Conversation? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String = "New Chat",
        taskType: String = "",
        modelName: String = "",
        systemPrompt: String = "",
        characterId: String = "",
        personaId: String = "",
    ): Conversation {
        val conversation = Conversation(
            title = title,
            taskType = taskType,
            modelName = modelName,
            systemPrompt = systemPrompt,
            characterId = characterId,
            personaId = personaId,
        )
        conversationDao.insert(conversation)
        return conversation
    }

    suspend fun updateConversation(conversation: Conversation) =
        conversationDao.update(conversation)

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDao.delete(conversation)
        // La caché de KV del chat va indexada por la ficha, no por la conversación: al borrar un
        // chat se borran las cachés de chat (se rehacen solas) para no dejar su contenido en disco.
        com.google.ai.edge.gallery.data.characters.KvCacheFiles.clearChats(context)
    }

    suspend fun latestConversationForCharacter(characterId: String): Conversation? =
        conversationDao.getLatestForCharacter(characterId)

    suspend fun countConversationsForCharacter(characterId: String): Int =
        conversationDao.countForCharacter(characterId)

    suspend fun countMessagesForCharacter(characterId: String): Int =
        conversationDao.messagesForCharacter(characterId)

    suspend fun deleteAllConversations() {
        conversationDao.deleteAll()
        com.google.ai.edge.gallery.data.characters.KvCacheFiles.clearChats(context)
    }

    suspend fun getLatestConversationForModel(modelName: String): Conversation? =
        conversationDao.getLatestForModel(modelName)

    // --- Messages ---

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesSync(conversationId: String): List<Message> =
        messageDao.getMessagesForConversationSync(conversationId)

    suspend fun saveMessage(
        conversationId: String,
        role: String,
        content: String,
        tokenCount: Int = 0,
        latencyMs: Long = 0,
    ): Message {
        val message = Message(
            conversationId = conversationId,
            role = role,
            content = content,
            tokenCount = tokenCount,
            latencyMs = latencyMs,
        )
        messageDao.insert(message)

        // Update conversation metadata
        val conversation = conversationDao.getConversationById(conversationId)
        if (conversation != null) {
            conversationDao.update(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1,
                    // Auto-title from first user message
                    title = if (conversation.messageCount == 0 && role == "user") {
                        content.take(50).let { if (content.length > 50) "$it…" else it }
                    } else {
                        conversation.title
                    }
                )
            )
        }
        return message
    }

    suspend fun deleteMessagesForConversation(conversationId: String) =
        messageDao.deleteAllForConversation(conversationId)

    // --- Swipes y edición (roleplay) ---

    /** Guarda una respuesta nueva como otra versión de la última respuesta del asistente. */
    suspend fun addSwipe(conversationId: String, text: String, latencyMs: Long = 0): Message? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.withNew(Swipes.decode(last.alternatives, last.content, last.activeIndex), text)
        val updated = last.copy(
            content = text,
            alternatives = Swipes.encode(state.texts),
            activeIndex = state.index,
            latencyMs = latencyMs,
        )
        messageDao.update(updated)
        return updated
    }

    /** (versión activa, total) de la última respuesta del asistente. */
    suspend fun swipeInfo(conversationId: String): Pair<Int, Int>? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.decode(last.alternatives, last.content, last.activeIndex)
        return state.index to state.count
    }

    /** Cambia a la versión anterior (-1) o siguiente (+1). Devuelve el mensaje ya actualizado. */
    suspend fun moveSwipe(conversationId: String, delta: Int): Message? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.move(Swipes.decode(last.alternatives, last.content, last.activeIndex), delta)
        val updated = last.copy(content = state.active, activeIndex = state.index)
        messageDao.update(updated)
        return updated
    }

    /**
     * Edita el texto del mensaje número [position] (contando solo mensajes de usuario y asistente,
     * en orden). Si el rol no coincide con [expectedRole] no toca nada.
     */
    suspend fun editMessageAt(conversationId: String, position: Int, expectedRole: String, newText: String): Boolean {
        val chat = messageDao.getMessagesForConversationSync(conversationId)
            .filter { it.role == "user" || it.role == "assistant" }
        val target = chat.getOrNull(position) ?: return false
        if (target.role != expectedRole) return false
        val state = Swipes.replaceActive(Swipes.decode(target.alternatives, target.content, target.activeIndex), newText)
        messageDao.update(target.copy(content = newText, alternatives = Swipes.encode(state.texts)))
        return true
    }

    /**
     * Borra el mensaje número [position] y todos los posteriores (solo se cuentan los mensajes de
     * usuario/asistente, en orden). Devuelve cuántos se borraron. Así se puede reeditar un turno
     * sin dejar respuestas huérfanas colgando.
     */
    suspend fun deleteMessagesFrom(conversationId: String, position: Int): Int {
        if (position < 0) return 0
        val chat = messageDao.getMessagesForConversationSync(conversationId)
            .filter { it.role == "user" || it.role == "assistant" }
        val doomed = if (position < chat.size) chat.drop(position) else return 0
        messageDao.deleteMessages(doomed)
        return doomed.size
    }
}
