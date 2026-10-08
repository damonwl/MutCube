package com.dwl.mutcube.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class Migration17To18Test {
    private val name = "migration-17-18-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MutCubeDatabase::class.java)
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    @Test fun clearsOnlyTemplateTablesAndNewRunsUseActions() {
        helper.createDatabase(name, 17).apply {
            execSQL("INSERT INTO spaces (id,name,pinned) VALUES ('project','keep',0)")
            execSQL("INSERT INTO conversations (id,title,spaceId,pinned,createdAt,updatedAt) VALUES ('chat','keep','project',0,1,1)")
            execSQL("INSERT INTO message_nodes (id,conversationId,sortOrder,selectedVariantId) VALUES ('node','chat',0,'variant')")
            execSQL("INSERT INTO message_variants (id,nodeId,role,createdAt,status,favorite) VALUES ('variant','node','USER',1,'COMPLETE',0)")
            execSQL("INSERT INTO message_parts (id,variantId,partOrder,kind,text) VALUES ('part','variant',0,'TEXT','keep')")
            execSQL("INSERT INTO memory_entries VALUES ('memory','project','keep','chat',1,1)")
            execSQL("INSERT INTO template_collections VALUES ('old','logs',1,'{}','IMMUTABLE_HISTORY')")
            execSQL("INSERT INTO template_bindings VALUES ('project','old','1.0.0',1)")
            execSQL("INSERT INTO template_permissions VALUES ('project','old','logs','LIST',0)")
            execSQL("INSERT INTO template_records VALUES ('project','old','logs','one','{}',1,1,'USER',1,1)")
            execSQL("INSERT INTO template_runs VALUES ('run','project','old','main','{}',NULL,'provider','model','FAILED','error',1,1,'{}')")
            execSQL("INSERT INTO template_audit VALUES ('audit','project','old','logs','LIST','SUCCEEDED',1)")
            execSQL("INSERT INTO template_summaries VALUES ('project','old','logs','digest','summary','provider','model',1)")
            execSQL("INSERT INTO template_current_versions VALUES ('project','old','logs','one',1,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 18, true, MutCubeDatabase.MIGRATION_17_18).apply {
            for (table in listOf("template_collections", "template_bindings", "template_permissions", "template_records", "template_runs", "template_audit", "template_summaries", "template_current_versions")) {
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(table, 0, it.getInt(0)) }
            }
            for (table in listOf("spaces", "conversations", "message_nodes", "message_variants", "message_parts", "memory_entries")) {
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(table, 1, it.getInt(0)) }
            }
            execSQL("INSERT INTO template_runs VALUES ('new','project','service','text.generate','{}',NULL,'provider','model','RUNNING',NULL,1,1,NULL)")
            query("SELECT actionId FROM template_runs").use { assertTrue(it.moveToFirst()); assertEquals("text.generate", it.getString(0)) }
            close()
        }
    }
}
