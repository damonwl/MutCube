package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    private val databaseName = "migration-6-7-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MutCubeDatabase::class.java,
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun existingVariantsStartNotFavorite() {
        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO conversations(id, title, spaceId, pinned, createdAt, updatedAt, deletedAt)
                VALUES ('conversation', '收藏迁移', NULL, 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO message_nodes(id, conversationId, sortOrder, parentVariantId, selectedVariantId)
                VALUES ('node', 'conversation', 1, NULL, 'variant')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO message_variants(
                    id, nodeId, role, createdAt, status, modelId, inputTokens, outputTokens, translation
                ) VALUES ('variant', 'node', 'AI', 1, 'COMPLETE', NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 7, true, MutCubeDatabase.MIGRATION_6_7)
        migrated.query("SELECT favorite FROM message_variants WHERE id = 'variant'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }
}
