package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class Migration16To17Test {
    private val name = "migration-16-17-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    @Test fun currentVersionStartsEmptyWithoutActivatingOldRecords() {
        helper.createDatabase(name, 16).apply {
            execSQL("INSERT INTO template_records VALUES ('project','template','plans','one','{}',1,1,'AI',1,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 17, true, MutCubeDatabase.MIGRATION_16_17).apply {
            query("SELECT COUNT(*) FROM template_records").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            query("SELECT COUNT(*) FROM template_current_versions").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
    }
}
