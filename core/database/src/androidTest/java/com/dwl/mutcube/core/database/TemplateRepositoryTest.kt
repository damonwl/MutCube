package com.dwl.mutcube.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.model.TemplateAccessContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateRepositoryTest {
    @Test
    fun snapshotAndCompletionAreGuardedAndInterruptedRunCannotCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("run-test")
            val repository = TemplateRepository(database)
            val access = TemplateAccessContext(project.value, "test.service")
            val collection = TemplateCollectionEntity("test.service", "results", 1,
                """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}""", "IMMUTABLE_HISTORY")
            repository.enableWithGrants(TemplateBindingEntity(project.value, "test.service", "1.0.0", true), listOf(collection))
            val run = TemplateRunEntity("run", project.value, "test.service", "main", "{}", null, "provider", "model", "RUNNING", null, 1, 1)
            assertEquals(run, repository.beginRun(run, "1.0.0"))
            assertEquals(run, repository.beginRun(run, "1.0.0"))
            assertTrue(runCatching { repository.beginRun(run.copy(inputJson = "{\"different\":true}"), "1.0.0") }.isFailure)
            repository.saveRunContext(access, "1.0.0", "run", "{\"version\":1}", listOf("results"))
            val summary = TemplateSummaryEntity(project.value, "test.service", "results", "a".repeat(64), "derived-summary", "provider", "model", 1)
            repository.saveSummary(access, "1.0.0", "run", summary)
            assertEquals(summary, repository.summary(access, "results"))
            val otherProject = conversations.createSpace("summary-isolation-test")
            assertTrue(runCatching { repository.summary(access.copy(projectId = otherProject.value), "results") }.isFailure)
            assertTrue(runCatching { repository.saveRunContext(access, "1.0.0", "run", "{}", listOf("results")) }.isFailure)
            repository.revoke(access)
            assertTrue(runCatching { repository.summary(access, "results") }.isFailure)
            assertTrue(runCatching { repository.saveSummary(access, "1.0.0", "run", summary.copy(summary = "denied")) }.isFailure)
            assertTrue(runCatching { repository.finishRunWithRecord(access, "run", "1.0.0", "results", "{\"text\":\"denied\"}") }.isFailure)
            assertEquals(0, repository.userRecords.first().size)
            repository.recoverInterruptedRuns()
            assertEquals("INTERRUPTED", repository.runs(access).first().single().status)
            assertEquals("{\"version\":1}", repository.runs(access).first().single().contextJson)
            repository.enableWithGrants(TemplateBindingEntity(project.value, "test.service", "1.0.0", true), listOf(collection))
            assertTrue(runCatching { repository.finishRunWithRecord(access, "run", "1.0.0", "results", "{\"text\":\"late\"}") }.isFailure)
        } finally { database.close() }
    }

    @Test
    fun scopedGrantsDoNotAllowOverwriteAndProjectDeletionKeepsUserData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("template-test")
            val otherProject = conversations.createSpace("other-test")
            val repository = TemplateRepository(database)
            val access = TemplateAccessContext(project.value, "test.service")
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            repository.registerCollection(TemplateCollectionEntity("test.service", "logs", 1, schema, "IMMUTABLE_HISTORY"))
            assertTrue(runCatching { repository.list(access, "logs") }.isFailure)
            for (operation in listOf("READ", "LIST", "APPEND")) {
                repository.grant(TemplatePermissionEntity(project.value, "test.service", "logs", operation, false))
            }
            repository.append(access, "logs", "one", """{"text":"saved"}""", "USER")
            assertEquals(1, repository.list(access, "logs").size)
            assertTrue(runCatching { repository.list(access.copy(projectId = otherProject.value), "logs") }.isFailure)
            assertTrue(runCatching { repository.append(access, "logs", "one", """{"text":"changed"}""", "AI") }.isFailure)
            repository.revoke(access)
            assertTrue(runCatching { repository.list(access, "logs") }.isFailure)
            conversations.deleteSpace(project)
            assertEquals(1, repository.userRecords.first().size)
        } finally { database.close() }
    }
}
