package com.google.ai.edge.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import com.google.ai.edge.gallery.data.local.dao.PersonaDao
import com.google.ai.edge.gallery.data.local.dao.MemoryDao
import com.google.ai.edge.gallery.data.local.dao.MemoryGraphDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.MemoryEdge
import com.google.ai.edge.gallery.data.local.entities.MemoryNode
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import com.google.ai.edge.gallery.security.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Box: Encrypted Room database for chat history persistence.
 * Uses SQLCipher for at-rest encryption with a key derived from device Keystore.
 */
@Database(
    entities = [
        Conversation::class, Message::class, RoleplayCharacter::class, UserPersona::class,
        CharacterMemory::class, MemoryNode::class, MemoryEdge::class,
    ],
    version = 9,
    exportSchema = false
)
abstract class BoxChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun characterDao(): CharacterDao
    abstract fun personaDao(): PersonaDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryGraphDao(): MemoryGraphDao

    companion object {
        private const val DB_NAME = "box_chat.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        // Roleplay: tabla de personajes + enlace conversación -> personaje.
        // Las columnas deben coincidir con RoleplayCharacter y Conversation, o Room lanza error al abrir.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN characterId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `characters` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`personality` TEXT NOT NULL, " +
                        "`scenario` TEXT NOT NULL, " +
                        "`firstMessage` TEXT NOT NULL, " +
                        "`alternateGreetingsJson` TEXT NOT NULL, " +
                        "`exampleMessages` TEXT NOT NULL, " +
                        "`systemPrompt` TEXT NOT NULL, " +
                        "`postHistoryInstructions` TEXT NOT NULL, " +
                        "`creatorNotes` TEXT NOT NULL, " +
                        "`creator` TEXT NOT NULL, " +
                        "`characterVersion` TEXT NOT NULL, " +
                        "`tagsJson` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`impression` TEXT NOT NULL, " +
                        "`avatarPath` TEXT NOT NULL, " +
                        "`extensionsJson` TEXT NOT NULL, " +
                        "`characterBookJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        // Roleplay: personas (perfiles del usuario) + enlace conversación -> persona.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN personaId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personas` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        // Roleplay: swipes (varias respuestas por mensaje).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN alternatives TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN activeIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Roleplay: recuerdos a largo plazo por personaje.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS character_memories (" +
                        "id TEXT NOT NULL, characterId TEXT NOT NULL, content TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_character_memories_characterId ON character_memories(characterId)")
            }
        }

        // Roleplay: modelo propio por personaje.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN modelName TEXT NOT NULL DEFAULT ''")
            }
        }

        // Roleplay: campos de recuperación por relevancia y tablas del grafo (ahora en uso).
        // Todos los DEFAULT son obligatorios en SQLite para ALTER TABLE sobre filas existentes.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_memories ADD COLUMN kind TEXT NOT NULL DEFAULT 'fact'")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN source TEXT NOT NULL DEFAULT 'derived'")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN sourceConversationId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN keysJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN importance INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN accessCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN lastAccess INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN valid INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE character_memories ADD COLUMN supersedesId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_character_memories_characterId_valid " +
                        "ON character_memories(characterId, valid)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory_nodes (" +
                        "id TEXT NOT NULL, characterId TEXT NOT NULL, label TEXT NOT NULL, " +
                        "type TEXT NOT NULL, topic TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory_edges (" +
                        "id TEXT NOT NULL, characterId TEXT NOT NULL, sourceId TEXT NOT NULL, " +
                        "targetId TEXT NOT NULL, relation TEXT NOT NULL, linkType TEXT NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        // Las tablas del grafo ya las creó 7→8. Este paso solo hace falta para que Room actualice
        // el hash de identidad al registrar MemoryNode y MemoryEdge como entidades (@Database).
        // Sin el salto de versión, Room aborta la apertura con "cannot verify the data integrity".
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory_nodes (" +
                        "id TEXT NOT NULL, characterId TEXT NOT NULL, label TEXT NOT NULL, " +
                        "type TEXT NOT NULL, topic TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory_edges (" +
                        "id TEXT NOT NULL, characterId TEXT NOT NULL, sourceId TEXT NOT NULL, " +
                        "targetId TEXT NOT NULL, relation TEXT NOT NULL, linkType TEXT NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        @Volatile
        private var INSTANCE: BoxChatDatabase? = null

        fun getInstance(context: Context): BoxChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): BoxChatDatabase {
            // Load SQLCipher native libraries
            System.loadLibrary("sqlcipher")

            val passphrase = SecurityUtils.getDatabasePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                BoxChatDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
