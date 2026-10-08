package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration8To9Test {
    private val databaseName = "migration-8-9-test"

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
    fun existingSpacesInheritGlobalSamplingSettings() {
        helper.createDatabase(databaseName, 8).apply {
            execSQL("INSERT INTO spaces(id, name, modelProfileId, pinned) VALUES ('space', '项目', NULL, 0)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 9, true, MutCubeDatabase.MIGRATION_8_9)
        migrated.query(
            "SELECT temperatureOverride, topPOverride, maxOutputTokensOverride FROM spaces WHERE id = 'space'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue((0..2).all(cursor::isNull))
        }
        migrated.close()
    }
}
