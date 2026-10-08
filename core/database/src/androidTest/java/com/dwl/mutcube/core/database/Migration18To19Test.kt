package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration18To19Test {
    private val name = "migration-18-19-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)

    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }

    @Test fun retainsChatsAndMemoriesWhileAddingCheckpoints() {
        helper.createDatabase(name, 18).apply {
            execSQL("INSERT INTO spaces (id,name,pinned) VALUES ('project','keep',0)")
            execSQL("INSERT INTO conversations (id,title,spaceId,pinned,createdAt,updatedAt) VALUES ('chat','keep','project',0,1,1)")
            execSQL("INSERT INTO memory_entries VALUES ('memory','project','keep','chat',1,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 19, true, MutCubeDatabase.MIGRATION_18_19).apply {
            query("SELECT COUNT(*) FROM conversations").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            query("SELECT COUNT(*) FROM memory_entries").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            execSQL("INSERT INTO conversation_checkpoints VALUES ('chat','message','hash','summary',2)")
            query("SELECT summary FROM conversation_checkpoints WHERE conversationId='chat'").use {
                assertTrue(it.moveToFirst())
                assertEquals("summary", it.getString(0))
            }
            close()
        }
    }
}
