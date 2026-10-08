package com.dwl.mutcube.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        SpaceEntity::class,
        ConversationEntity::class,
        MessageNodeEntity::class,
        MessageVariantEntity::class,
        MessagePartEntity::class,
        MemoryEntryEntity::class,
        ConversationCheckpointEntity::class,
        TemplateCollectionEntity::class,
        TemplateBindingEntity::class,
        TemplatePermissionEntity::class,
        TemplateRecordEntity::class,
        TemplateRunEntity::class,
        TemplateAuditEntity::class,
        TemplateSummaryEntity::class,
        TemplateCurrentVersionEntity::class,
    ],
    version = 19,
    exportSchema = true,
)
abstract class MutCubeDatabase : RoomDatabase() {
    internal abstract fun dao(): MutCubeDao
    internal abstract fun templateDao(): TemplateDao

    suspend fun checkpointForBackup() = withContext(Dispatchers.IO) {
        openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            check(cursor.moveToFirst()) { "Unable to checkpoint database" }
            check(cursor.getInt(0) == 0) { "Database is busy" }
        }
    }

    companion object {
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS conversation_checkpoints (" +
                        "conversationId TEXT NOT NULL PRIMARY KEY, throughMessageId TEXT NOT NULL, " +
                        "sourceFingerprint TEXT NOT NULL, summary TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }

        fun create(context: Context, databaseName: String = "mutcube.db"): MutCubeDatabase = Room.databaseBuilder(
            context.applicationContext,
            MutCubeDatabase::class.java,
            databaseName,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
        ).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETE'")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS message_nodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        selectedVariantId TEXT NOT NULL,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_message_nodes_conversationId_sortOrder " +
                        "ON message_nodes(conversationId, sortOrder)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS message_variants (
                        id TEXT NOT NULL PRIMARY KEY,
                        nodeId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        modelId TEXT,
                        inputTokens INTEGER,
                        outputTokens INTEGER,
                        translation TEXT,
                        FOREIGN KEY(nodeId) REFERENCES message_nodes(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_message_variants_nodeId_createdAt " +
                        "ON message_variants(nodeId, createdAt)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS message_parts (
                        id TEXT NOT NULL PRIMARY KEY,
                        variantId TEXT NOT NULL,
                        partOrder INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        text TEXT,
                        uri TEXT,
                        mimeType TEXT,
                        displayName TEXT,
                        sizeBytes INTEGER,
                        attachmentKind TEXT,
                        callId TEXT,
                        toolName TEXT,
                        argumentsJson TEXT,
                        toolStatus TEXT,
                        isError INTEGER,
                        FOREIGN KEY(variantId) REFERENCES message_variants(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_message_parts_variantId_partOrder " +
                        "ON message_parts(variantId, partOrder)",
                )
                db.execSQL(
                    """
                    INSERT INTO message_nodes(id, conversationId, sortOrder, selectedVariantId)
                    SELECT id, conversationId, createdAt, id FROM messages
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO message_variants(
                        id, nodeId, role, createdAt, status,
                        modelId, inputTokens, outputTokens, translation
                    )
                    SELECT id, id, role, createdAt, status, NULL, NULL, NULL, NULL FROM messages
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO message_parts(
                        id, variantId, partOrder, kind, text, uri, mimeType, displayName,
                        sizeBytes, attachmentKind, callId, toolName, argumentsJson, toolStatus, isError
                    )
                    SELECT id || ':text', id, 0, 'TEXT', text, NULL, NULL, NULL,
                           NULL, NULL, NULL, NULL, NULL, NULL, NULL
                    FROM messages
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE messages")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN deletedAt INTEGER")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spaces ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_nodes ADD COLUMN parentVariantId TEXT")
                db.execSQL(
                    """
                    UPDATE message_nodes
                    SET parentVariantId = (
                        SELECT previousVariantId
                        FROM (
                            SELECT id,
                                   LAG(selectedVariantId) OVER (
                                       PARTITION BY conversationId ORDER BY sortOrder, id
                                   ) AS previousVariantId
                            FROM message_nodes
                        ) ordered_nodes
                        WHERE ordered_nodes.id = message_nodes.id
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_message_nodes_parentVariantId " +
                        "ON message_nodes(parentVariantId)",
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_variants ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spaces ADD COLUMN modelIdOverride TEXT")
                db.execSQL("ALTER TABLE spaces ADD COLUMN systemPromptOverride TEXT")
                db.execSQL("ALTER TABLE spaces ADD COLUMN maxContextMessagesOverride INTEGER")
                db.execSQL("ALTER TABLE spaces ADD COLUMN imageInputEnabledOverride INTEGER")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spaces ADD COLUMN temperatureOverride REAL")
                db.execSQL("ALTER TABLE spaces ADD COLUMN topPOverride REAL")
                db.execSQL("ALTER TABLE spaces ADD COLUMN maxOutputTokensOverride INTEGER")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        spaceId TEXT,
                        content TEXT NOT NULL,
                        sourceConversationId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_spaceId ON memory_entries(spaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries(updatedAt)")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_variants ADD COLUMN cachedInputTokens INTEGER")
                db.execSQL("ALTER TABLE message_variants ADD COLUMN generationDurationMs INTEGER")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN systemPrompt TEXT")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS template_collections (templateId TEXT NOT NULL, collection TEXT NOT NULL, schemaVersion INTEGER NOT NULL, schemaJson TEXT NOT NULL, mutability TEXT NOT NULL, PRIMARY KEY(templateId, collection))")
                db.execSQL("CREATE TABLE IF NOT EXISTS template_bindings (projectId TEXT NOT NULL, templateId TEXT NOT NULL, version TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(projectId, templateId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS template_permissions (projectId TEXT NOT NULL, templateId TEXT NOT NULL, collection TEXT NOT NULL, operation TEXT NOT NULL, revoked INTEGER NOT NULL, PRIMARY KEY(projectId, templateId, collection, operation))")
                db.execSQL("CREATE TABLE IF NOT EXISTS template_records (projectId TEXT NOT NULL, templateId TEXT NOT NULL, collection TEXT NOT NULL, recordKey TEXT NOT NULL, json TEXT NOT NULL, revision INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(projectId, templateId, collection, recordKey))")
                db.execSQL("CREATE TABLE IF NOT EXISTS template_runs (id TEXT NOT NULL, projectId TEXT NOT NULL, templateId TEXT NOT NULL, interaction TEXT NOT NULL, inputJson TEXT NOT NULL, outputJson TEXT, providerId TEXT NOT NULL, modelId TEXT NOT NULL, status TEXT NOT NULL, error TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS template_summaries (projectId TEXT NOT NULL, templateId TEXT NOT NULL, collection TEXT NOT NULL, sourceDigest TEXT NOT NULL, summary TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(projectId, templateId, collection))")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS template_current_versions (projectId TEXT NOT NULL, templateId TEXT NOT NULL, collection TEXT NOT NULL, recordKey TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(projectId, templateId, collection))")
            }
        }

        /** Intentional one-time removal of obsolete template data. Never clears chat or credentials. */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = listOf("template_current_versions", "template_summaries", "template_audit",
                    "template_runs", "template_records", "template_permissions", "template_bindings", "template_collections")
                tables.forEach { table ->
                    db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        check(cursor.moveToFirst())
                        android.util.Log.i("TemplateReset", "$table: removing ${cursor.getLong(0)} obsolete rows")
                    }
                }
                tables.forEach { db.execSQL("DELETE FROM $it") }
                db.execSQL("DROP TABLE template_runs")
                db.execSQL("CREATE TABLE template_runs (id TEXT NOT NULL, projectId TEXT NOT NULL, templateId TEXT NOT NULL, actionId TEXT NOT NULL, inputJson TEXT NOT NULL, outputJson TEXT, providerId TEXT NOT NULL, modelId TEXT NOT NULL, status TEXT NOT NULL, error TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, contextJson TEXT, PRIMARY KEY(id))")
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE template_runs ADD COLUMN contextJson TEXT")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS template_audit (id TEXT NOT NULL, projectId TEXT NOT NULL, templateId TEXT NOT NULL, collection TEXT NOT NULL, operation TEXT NOT NULL, outcome TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}
