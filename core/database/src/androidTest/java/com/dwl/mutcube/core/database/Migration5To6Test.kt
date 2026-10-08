package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val databaseName = "migration-5-6-test"

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
    fun existingLinearNodesBecomeLinkedPath() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO conversations(id, title, spaceId, pinned, createdAt, updatedAt, deletedAt)
                VALUES ('conversation', '迁移路径', NULL, 0, 1, 2, NULL)
                """.trimIndent(),
            )
            insertNode("node-1", "variant-1", 1)
            insertNode("node-2", "variant-2", 2)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 6, true, MutCubeDatabase.MIGRATION_5_6)
        migrated.query("SELECT id, parentVariantId FROM message_nodes ORDER BY sortOrder").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("node-1", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.moveToNext())
            assertEquals("node-2", cursor.getString(0))
            assertEquals("variant-1", cursor.getString(1))
        }
        migrated.close()
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.insertNode(id: String, variantId: String, order: Long) {
    execSQL(
        """
        INSERT INTO message_nodes(id, conversationId, sortOrder, selectedVariantId)
        VALUES ('$id', 'conversation', $order, '$variantId')
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO message_variants(
            id, nodeId, role, createdAt, status, modelId, inputTokens, outputTokens, translation
        ) VALUES ('$variantId', '$id', 'USER', $order, 'COMPLETE', NULL, NULL, NULL, NULL)
        """.trimIndent(),
    )
}
