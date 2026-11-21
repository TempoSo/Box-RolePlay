package com.google.ai.edge.gallery.data.characters

import android.content.Context
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import com.google.ai.edge.gallery.data.local.dao.MemoryDao
import com.google.ai.edge.gallery.data.local.dao.PersonaDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Lo que trae un `.zip` de copia de seguridad: secciones disponibles y cuántos elementos tiene cada una. */
class BackupContents(val sections: Set<BackupSection>, val counts: Map<BackupSection, Int>, val legacy: Boolean)

/**
 * BUG-038: copia de seguridad completa (personajes, recuerdos, personas, presets y escenas) y
 * restauración que **fusiona** en vez de duplicar: en el resto de secciones lo de la copia
 * sobrescribe el elemento con el mismo id y lo demás se conserva. Las reglas puras viven en
 * [BackupFormat] (con pruebas).
 *
 * BUG-042: los personajes se comparan por contenido. Si son idénticos no se tocan; si la copia trae
 * una versión distinta (p. ej. sin `{{char}}` en la descripción), se conserva la que ya tienes y la
 * de la copia se inserta como personaje nuevo para no perder ninguna de las dos.
 *
 * Los chats (base cifrada) no entran en esta fase.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val memoryDao: MemoryDao,
    private val personaDao: PersonaDao,
    private val scenarioStore: ScenarioStore,
    @ApplicationContext private val context: Context,
) {
    suspend fun exportZip(): ByteArray = withContext(Dispatchers.IO) {
        val chars = characterDao.getAll().first()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            fun put(name: String, text: String) = put(name, text.toByteArray(Charsets.UTF_8))

            put(BackupFormat.MANIFEST, BackupFormat.manifest(BackupSection.entries, System.currentTimeMillis()))
            chars.forEach { c ->
                put(BackupFormat.characterEntry(c.id), CharacterCardParser.encodeV2(c.toCard()))
                put(BackupFormat.characterMetaEntry(c.id), BackupFormat.encodeCharacterMeta(c.modelName, c.createdAt, c.updatedAt))
                val av = if (c.avatarPath.isNotEmpty()) File(c.avatarPath) else null
                if (av != null && av.exists()) put(BackupFormat.characterAvatarEntry(c.id), av.readBytes())
            }
            val memories = chars.flatMap { c -> memoryDao.getAll(c.id) }.map { MemoryDto(it.id, it.characterId, it.content, it.createdAt) }
            if (memories.isNotEmpty()) put(BackupFormat.MEMORIES, BackupFormat.encodeMemories(memories))
            val raw = RawMemoryStore(context).all()
            if (raw.isNotEmpty()) put(BackupFormat.RAW_MEMORIES, RawMemoryJson.encode(raw))
            val personas = personaDao.getAll().first().map { PersonaDto(it.id, it.name, it.description, it.isActive, it.createdAt, it.updatedAt) }
            if (personas.isNotEmpty()) put(BackupFormat.PERSONAS, BackupFormat.encodePersonas(personas))
            val engine = EnginePresetStore(context).all()
            if (engine.isNotEmpty()) put(BackupFormat.ENGINE_PRESETS, EnginePresetJson.encode(engine))
            val sampler = SamplerSettingsStore(context).namedPresets().mapValues { SamplerSettings.encode(it.value) }
            if (sampler.isNotEmpty()) put(BackupFormat.SAMPLER_PRESETS, BackupFormat.encodeStringMap(sampler))
            val scenes = scenarioStore.all()
            if (scenes.isNotEmpty()) put(BackupFormat.SCENES, ScenarioJson.encode(scenes))
        }
        out.toByteArray()
    }

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) map[e.name.trimStart('/')] = zin.readNBytes(CharacterCardParser.MAX_FILE_BYTES)
                e = zin.nextEntry
            }
        }
        return map
    }

    fun isZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** Mira dentro del `.zip` sin tocar nada: qué secciones hay y cuántos elementos. */
    suspend fun inspect(bytes: ByteArray): BackupContents = withContext(Dispatchers.IO) {
        val entries = readEntries(bytes)
        val names = entries.keys
        val counts = mutableMapOf<BackupSection, Int>()
        val legacy = BackupFormat.isLegacy(names)
        if (legacy) {
            counts[BackupSection.CHARACTERS] = names.count { it.endsWith(".json") }
        } else {
            counts[BackupSection.CHARACTERS] = names.count { BackupFormat.characterId(it) != null }
            counts[BackupSection.MEMORIES] =
                (entries[BackupFormat.MEMORIES]?.let { BackupFormat.decodeMemories(String(it)).size } ?: 0) +
                    (entries[BackupFormat.RAW_MEMORIES]?.let { RawMemoryJson.decode(String(it)).size } ?: 0)
            counts[BackupSection.PERSONAS] = entries[BackupFormat.PERSONAS]?.let { BackupFormat.decodePersonas(String(it)).size } ?: 0
            counts[BackupSection.PRESETS] =
                (entries[BackupFormat.ENGINE_PRESETS]?.let { EnginePresetJson.decode(String(it)).size } ?: 0) +
                    (entries[BackupFormat.SAMPLER_PRESETS]?.let { BackupFormat.decodeStringMap(String(it)).size } ?: 0)
            counts[BackupSection.SCENES] = entries[BackupFormat.SCENES]?.let { ScenarioJson.decode(String(it)).size } ?: 0
        }
        val sections = BackupFormat.sectionsOf(names).filter { (counts[it] ?: 0) > 0 }.toSet()
        BackupContents(sections, counts.filterKeys { it in sections }, legacy)
    }

    /** Restaura solo [selected], fusionando con lo que ya hay. Devuelve un resumen legible. */
    suspend fun restore(bytes: ByteArray, selected: Set<BackupSection>): String = withContext(Dispatchers.IO) {
        val entries = readEntries(bytes)
        val legacy = BackupFormat.isLegacy(entries.keys)
        val done = mutableListOf<String>()

        if (BackupSection.CHARACTERS in selected) {
            val tally = if (legacy) restoreLegacyCharacters(entries) else restoreCharacters(entries)
            val parts = buildList {
                if (tally.created > 0) add("${tally.created} personaje(s) nuevo(s)")
                if (tally.duplicated > 0) add("${tally.duplicated} copia(s) de personajes editados")
                if (tally.unchanged > 0) add("${tally.unchanged} personaje(s) sin cambios")
            }
            if (parts.isNotEmpty()) done += parts.joinToString(", ")
        }
        if (!legacy) {
            val charIds = characterDao.getAll().first().map { it.id }.toSet()
            if (BackupSection.MEMORIES in selected) {
                var n = 0
                entries[BackupFormat.MEMORIES]?.let { raw ->
                    BackupFormat.decodeMemories(String(raw)).filter { it.characterId in charIds }.forEach {
                        memoryDao.insert(CharacterMemory(it.id, it.characterId, it.content, it.createdAt)); n++
                    }
                }
                entries[BackupFormat.RAW_MEMORIES]?.let { raw ->
                    val list = RawMemoryJson.decode(String(raw)).filter { it.characterId in charIds }
                    RawMemoryStore(context).merge(list); n += list.size
                }
                if (n > 0) done += "$n recuerdo(s)"
            }
            if (BackupSection.PERSONAS in selected) {
                val incoming = entries[BackupFormat.PERSONAS]?.let { BackupFormat.decodePersonas(String(it)) }.orEmpty()
                if (incoming.isNotEmpty()) {
                    val existing = personaDao.getAll().first().map { PersonaDto(it.id, it.name, it.description, it.isActive, it.createdAt, it.updatedAt) }
                    val merged = BackupFormat.mergePersonas(existing, incoming)
                    val incomingIds = incoming.map { it.id }.toSet()
                    merged.filter { it.id in incomingIds }.forEach {
                        personaDao.insert(UserPersona(it.id, it.name, it.description, it.isActive, it.createdAt, it.updatedAt))
                    }
                    merged.firstOrNull { it.isActive }?.let { personaDao.setActiveOnly(it.id) }
                    done += "${incoming.size} persona(s)"
                }
            }
            if (BackupSection.PRESETS in selected) {
                var n = 0
                entries[BackupFormat.ENGINE_PRESETS]?.let { raw ->
                    val store = EnginePresetStore(context)
                    EnginePresetJson.decode(String(raw)).forEach { store.save(it); n++ }
                }
                entries[BackupFormat.SAMPLER_PRESETS]?.let { raw ->
                    val store = SamplerSettingsStore(context)
                    BackupFormat.decodeStringMap(String(raw)).forEach { (name, enc) ->
                        SamplerSettings.decodeOrNull(enc)?.let { store.saveNamedPreset(name, it); n++ }
                    }
                }
                if (n > 0) done += "$n preset(s)"
            }
            if (BackupSection.SCENES in selected) {
                val incoming = entries[BackupFormat.SCENES]?.let { ScenarioJson.decode(String(it)) }.orEmpty()
                incoming.forEach { scenarioStore.save(it) }
                if (incoming.isNotEmpty()) done += "${incoming.size} escena(s)"
            }
        }
        if (done.isEmpty()) "No había nada que restaurar de lo elegido."
        else "Restaurado: ${done.joinToString(", ")}. Lo que ya tenías y no estaba en la copia se conserva."
    }

    private fun avatarFile(id: String): File = File(File(context.filesDir, "characters").apply { mkdirs() }, "$id.png")

    /** Inserta una tarjeta ya leída con el id indicado; el avatar se guarda aparte. */
    private suspend fun saveCharacter(
        id: String,
        parsed: ParsedCharacterCard,
        avatar: ByteArray?,
        meta: BackupFormat.CharacterMeta?,
    ): Boolean {
        val png = avatar ?: parsed.avatarPng
        val avatarPath = if (png != null) avatarFile(id).also { it.writeBytes(png) }.absolutePath else ""
        val now = System.currentTimeMillis()
        val entity: RoleplayCharacter =
            parsed.card.toEntity(id = id, avatarPath = avatarPath).copy(
                modelName = meta?.modelName ?: "",
                createdAt = meta?.createdAt ?: now,
                updatedAt = now,
            )
        characterDao.insert(entity)
        return true
    }

    /** Resumen de personajes: nuevos, copias de versiones editadas y los que ya eran idénticos. */
    private class CharacterTally {
        var created = 0
        var duplicated = 0
        var unchanged = 0
    }

    /**
     * BUG-042: mismo id y mismo contenido = no se toca; mismo id con cambios = se conserva el que ya
     * tienes y la versión de la copia entra con un id nuevo. Un id que no existe se crea tal cual.
     */
    private suspend fun restoreCharacters(entries: Map<String, ByteArray>): CharacterTally {
        val tally = CharacterTally()
        for ((name, data) in entries) {
            val id = BackupFormat.characterId(name) ?: continue
            val parsed = runCatching { CharacterCardParser.parse(data) }.getOrNull() ?: continue
            val existing = characterDao.getById(id)
            if (existing != null && BackupFormat.sameCharacterContent(existing.toCard(), parsed.card)) {
                tally.unchanged++
                continue
            }
            val meta = entries[BackupFormat.characterMetaEntry(id)]?.let { BackupFormat.decodeCharacterMeta(String(it)) }
            val targetId = if (existing == null) id else UUID.randomUUID().toString()
            if (saveCharacter(targetId, parsed, entries[BackupFormat.characterAvatarEntry(id)], meta)) {
                if (existing == null) tally.created++ else tally.duplicated++
            }
        }
        return tally
    }

    /** Copias v1 (sin ids): se empareja por nombre y se aplica la misma regla de contenido (BUG-042). */
    private suspend fun restoreLegacyCharacters(entries: Map<String, ByteArray>): CharacterTally {
        val tally = CharacterTally()
        val byName = characterDao.getAll().first().associateBy { it.name.trim().lowercase() }.toMutableMap()
        for ((name, data) in entries) {
            if (!name.endsWith(".json")) continue
            val parsed = runCatching { CharacterCardParser.parse(data) }.getOrNull() ?: continue
            val key = parsed.card.name.trim().lowercase()
            val existing = byName[key]
            if (existing != null && BackupFormat.sameCharacterContent(existing.toCard(), parsed.card)) {
                tally.unchanged++
                continue
            }
            val id = UUID.randomUUID().toString()
            val avatar = entries[name.removeSuffix(".json") + ".avatar.png"]
            if (saveCharacter(id, parsed, avatar, null)) {
                if (existing == null) tally.created++ else tally.duplicated++
                characterDao.getById(id)?.let { byName[it.name.trim().lowercase()] = it }
            }
        }
        return tally
    }
}
