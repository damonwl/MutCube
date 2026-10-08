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
class Migration4To5Test {
    private val databaseName = "migration-4-5-test"

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
    fun existingSpacesStartUnpinned() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO spaces(id, name, modelProfileId) VALUES ('space', '项目', NULL)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 5, true, MutCubeDatabase.MIGRATION_4_5)
        migrated.query("SELECT pinned FROM spaces WHERE id = 'space'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }
}
