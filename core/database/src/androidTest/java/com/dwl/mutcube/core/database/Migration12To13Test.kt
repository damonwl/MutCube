package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {
    private val databaseName = "migration-12-13-test"
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }
    @Test
    fun addsIndependentTemplateStorage() {
        helper.createDatabase(databaseName, 12).close()
        helper.runMigrationsAndValidate(databaseName, 13, true, MutCubeDatabase.MIGRATION_12_13).close()
    }
}
