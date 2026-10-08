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
class Migration3To4Test {
    private val databaseName = "migration-3-4-test"

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
    fun existingConversationsRemainVisible() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO conversations(id, title, spaceId, pinned, createdAt, updatedAt)
                VALUES ('conversation', '保留会话', NULL, 0, 100, 100)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MutCubeDatabase.MIGRATION_3_4,
        )

        migrated.query("SELECT title, deletedAt FROM conversations WHERE id = 'conversation'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("保留会话", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
        }
        migrated.close()
    }
}
