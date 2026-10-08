package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    private val databaseName = "migration-13-14-test"
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }
    @Test
    fun addsAuditWithoutDeletingData() {
        helper.createDatabase(databaseName, 13).close()
        helper.runMigrationsAndValidate(databaseName, 14, true, MutCubeDatabase.MIGRATION_13_14).close()
    }
}
