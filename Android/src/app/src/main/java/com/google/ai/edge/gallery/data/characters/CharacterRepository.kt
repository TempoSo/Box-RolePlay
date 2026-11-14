package com.google.ai.edge.gallery.data.characters

import android.content.Context
import android.net.Uri
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import com.google.ai.edge.gallery.security.SecurityUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Roleplay: guarda, importa y exporta personajes, y crea chats con ellos.
 * La lógica de leer tarjetas y armar el prompt vive en CharacterCardParser y
 * CharacterPromptBuilder, que no dependen de Android y tienen pruebas unitarias.
 */
@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<RoleplayCharacter>> = characterDao.getAll()

    suspend fun getById(id: String): RoleplayCharacter? = characterDao.getById(id)

    /**
     * Importa una tarjeta .json o .png elegida por el usuario.
     * Lanza CharacterCardException con un mensaje legible si el archivo no sirve.
     */
    suspend fun importFromUri(uri: Uri): RoleplayCharacter = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            it.readNBytes(CharacterCardParser.MAX_FILE_BYTES + 1)
        } ?: throw CharacterCardException("No se pudo abrir el archivo")
        importFromBytes(bytes)
    }

    /**
     * Importa un archivo: una tarjeta (.json/.png) o una copia de seguridad (.zip) con varias.
     * Devuelve los personajes creados.
     */
    suspend fun importAnyFromUri(uri: Uri): List<RoleplayCharacter> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            it.readNBytes(BACKUP_MAX_BYTES + 1)
        } ?: throw CharacterCardException("No se pudo abrir el archivo")
        if (bytes.size > BACKUP_MAX_BYTES) throw CharacterCardException("El archivo es demasiado grande")
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return@withContext listOf(importFromBytes(bytes))

        val jsons = linkedMapOf<String, ByteArray>()
        val avatars = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val name = e.name.substringAfterLast('/')
                if (!e.isDirectory) {
                    val data = zin.readNBytes(CharacterCardParser.MAX_FILE_BYTES)
                    when {
                        name.endsWith(".avatar.png") -> avatars[name.removeSuffix(".avatar.png")] = data
                        name.endsWith(".json") -> jsons[name.removeSuffix(".json")] = data
                    }
                }
                e = zin.nextEntry
            }
        }
        val created = mutableListOf<RoleplayCharacter>()
        for ((key, data) in jsons) {
            val c = runCatching { importFromBytes(data) }.getOrNull() ?: continue
            val png = avatars[key]
            created += if (png != null && c.avatarPath.isEmpty()) {
                val f = avatarFile(c.id).also { it.writeBytes(png) }
                c.copy(avatarPath = f.absolutePath).also { characterDao.insert(it) }
            } else c
        }
        if (created.isEmpty()) throw CharacterCardException("El zip no contiene tarjetas válidas")
        created
    }

    /** Copia de seguridad: un .zip con cada personaje (.json chara_card_v2) y su avatar. */
    suspend fun exportBackupZip(): ByteArray = withContext(Dispatchers.IO) {
        val all = characterDao.getAll().first()
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            all.forEachIndexed { i, c ->
                val safe = c.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40).ifEmpty { "personaje" }
                val key = "%03d-%s".format(i + 1, safe)
                zip.putNextEntry(java.util.zip.ZipEntry("$key.json"))
                zip.write(exportJson(c).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                val av = if (c.avatarPath.isNotEmpty()) File(c.avatarPath) else null
                if (av != null && av.exists()) {
                    zip.putNextEntry(java.util.zip.ZipEntry("$key.avatar.png"))
                    zip.write(av.readBytes())
                    zip.closeEntry()
                }
            }
        }
        out.toByteArray()
    }

    suspend fun importFromBytes(bytes: ByteArray): RoleplayCharacter = withContext(Dispatchers.IO) {
        val parsed = CharacterCardParser.parse(bytes)
        val id = UUID.randomUUID().toString()
        // Si la tarjeta venía en un PNG, esa imagen es el avatar del personaje.
        val avatarPath = parsed.avatarPng?.let { png ->
            avatarFile(id).also { it.writeBytes(png) }.absolutePath
        } ?: ""
        val entity = parsed.card.toEntity(id = id, avatarPath = avatarPath)
        characterDao.insert(entity)
        entity
    }

    /** Crea o actualiza (REPLACE) un personaje, por ejemplo desde la pantalla "Crear personaje". */
    suspend fun save(character: RoleplayCharacter) = withContext(Dispatchers.IO) {
        characterDao.insert(character.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Borra el personaje y su avatar. Los chats ya creados siguen funcionando: llevan el prompt guardado. */
    suspend fun delete(character: RoleplayCharacter) = withContext(Dispatchers.IO) {
        characterDao.delete(character)
        if (character.avatarPath.isNotEmpty()) {
            SecurityUtils.secureDelete(File(character.avatarPath))
        }
    }

    /** JSON chara_card_v2 para compartir el personaje con SillyTavern, Layla u otra app. */
    fun exportJson(character: RoleplayCharacter): String =
        CharacterCardParser.encodeV2(character.toCard())

    /**
     * Crea una conversación nueva con este personaje: guarda el prompt de sistema armado desde la
     * tarjeta y, si hay saludo, lo deja como primer mensaje del personaje.
     *
     * Ojo: este prompt NO pasa por SecurityUtils.sanitizePrompt, que corta a 4096 caracteres y
     * truncaría las tarjetas largas. Las instrucciones posteriores al historial
     * (post_history_instructions) todavía no se aplican en cada turno: eso va con el chat.
     */
    suspend fun startChat(
        character: RoleplayCharacter,
        modelName: String,
        persona: UserPersona? = null,
        random: Random = Random.Default,
    ): Conversation = withContext(Dispatchers.IO) {
        // Memoria a largo plazo: los recuerdos más recientes entran en el prompt del chat nuevo.
        val settings = RoleplaySettingsStore(context)
        val memoryText = if (settings.memoryEnabled(character.id)) {
            MemoryPromptBuilder.build(
                charName = character.name.trim().ifEmpty { "Character" },
                memories = memoryRepository.getAll(character.id).map { MemoryEntry(it.content, it.createdAt) },
                max = settings.memoryMaxInjections(),
                now = System.currentTimeMillis(),
            )
        } else ""
        val built = CharacterPromptBuilder.build(
            card = character.toCard(),
            userName = persona?.name ?: CharacterPromptBuilder.DEFAULT_USER_NAME,
            userDescription = persona?.description ?: "",
            memory = memoryText,
        )
        val greeting = CharacterPromptBuilder.pickGreeting(built.greetings, random)
        val conversation = chatRepository.createConversation(
            title = character.name,
            taskType = "llm_chat",
            modelName = modelName,
            systemPrompt = built.systemPrompt,
            characterId = character.id,
            personaId = persona?.id ?: "",
        )
        greeting?.let { text ->
            chatRepository.saveMessage(
                conversationId = conversation.id,
                role = "assistant",
                content = text,
            )
        }
        conversation
    }

    private companion object {
        const val BACKUP_MAX_BYTES = 120 * 1024 * 1024
    }

    private fun avatarFile(id: String): File {
        val dir = File(context.filesDir, "characters").apply { mkdirs() }
        return File(dir, "$id.png")
    }
}
