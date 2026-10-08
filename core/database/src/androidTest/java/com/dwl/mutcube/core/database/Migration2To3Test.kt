package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    private val databaseName = "migration-2-3-test"

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
    fun flatMessagesBecomeSelectedTextVariants() {
        helper.createDatabase(databaseName, 2).apply {
            insertConversationAndMessage()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MutCubeDatabase.MIGRATION_2_3,
        )

        migrated.query(
            """
            SELECT n.id, n.selectedVariantId, v.role, v.status, p.kind, p.text
            FROM message_nodes n
            JOIN message_variants v ON v.nodeId = n.id
            JOIN message_parts p ON p.variantId = v.id
            WHERE n.conversationId = 'conversation'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("message", cursor.getString(0))
            assertEquals("message", cursor.getString(1))
            assertEquals("USER", cursor.getString(2))
            assertEquals("STOPPED", cursor.getString(3))
            assertEquals("TEXT", cursor.getString(4))
            assertEquals("保留下来的消息", cursor.getString(5))
            assertEquals(false, cursor.moveToNext())
        }
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'messages'").singleInt())
        migrated.close()
    }
}

private fun SupportSQLiteDatabase.insertConversationAndMessage() {
    execSQL(
        """
        INSERT INTO conversations(id, title, spaceId, pinned, createdAt, updatedAt)
        VALUES ('conversation', '迁移测试', NULL, 0, 100, 100)
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO messages(id, conversationId, role, text, createdAt, status)
        VALUES ('message', 'conversation', 'USER', '保留下来的消息', 100, 'STOPPED')
        """.trimIndent(),
    )
}

private fun android.database.Cursor.singleInt(): Int = use { cursor ->
    check(cursor.moveToFirst())
    cursor.getInt(0)
}
