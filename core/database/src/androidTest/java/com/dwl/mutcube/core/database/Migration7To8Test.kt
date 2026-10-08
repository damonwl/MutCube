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
class Migration7To8Test {
    private val databaseName = "migration-7-8-test"

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
    fun existingSpacesInheritGlobalModelSettings() {
        helper.createDatabase(databaseName, 7).apply {
            execSQL("INSERT INTO spaces(id, name, modelProfileId, pinned) VALUES ('space', '项目', NULL, 0)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 8, true, MutCubeDatabase.MIGRATION_7_8)
        migrated.query(
            "SELECT modelIdOverride, systemPromptOverride, maxContextMessagesOverride, " +
                "imageInputEnabledOverride FROM spaces WHERE id = 'space'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue((0..3).all(cursor::isNull))
        }
        migrated.close()
    }
}
