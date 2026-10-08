package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    private val databaseName = "migration-14-15-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName) }
    @Test fun retainsExistingRunsAndAddsNullableSnapshot() {
        helper.createDatabase(databaseName, 14).apply {
            execSQL("INSERT INTO template_runs VALUES ('run','project','template','interaction.run','{}','{}','provider','model','SUCCEEDED',NULL,1,1)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 15, true, MutCubeDatabase.MIGRATION_14_15).apply {
            query("SELECT status, contextJson FROM template_runs WHERE id = 'run'").use {
                assertTrue(it.moveToFirst())
                assertEquals("SUCCEEDED", it.getString(0))
                assertTrue(it.isNull(1))
            }
            close()
        }
    }
}
