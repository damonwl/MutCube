package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration15To16Test {
    private val name = "migration-15-16-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    @Test fun addsDerivedCacheAndKeepsRunSnapshot() {
        helper.createDatabase(name, 15).apply {
            execSQL("INSERT INTO template_runs VALUES ('run','project','template','main','{}','{}','provider','model','SUCCEEDED',NULL,1,1,'{}')")
            close()
        }
        helper.runMigrationsAndValidate(name, 16, true, MutCubeDatabase.MIGRATION_15_16).apply {
            query("SELECT contextJson FROM template_runs WHERE id='run'").use {
                assertTrue(it.moveToFirst()); assertEquals("{}", it.getString(0))
            }
            query("SELECT COUNT(*) FROM template_summaries").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
    }
}
