package com.dwl.mutcube.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.model.TemplateAccessContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class TemplateVersionTest {
    @Test fun concurrentConfirmationHasExactlyOneWinner() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            MutCubeDatabase::class.java).build()
        try {
            val project = RoomConversationRepository(database).createSpace("confirmation-race")
            val repository = TemplateRepository(database)
            val context = TemplateAccessContext(project.value, "test.race")
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            repository.enableWithGrants(TemplateBindingEntity(project.value, context.templateId, "1.0.0", true),
                listOf(TemplateCollectionEntity(context.templateId, "plans", 1, schema, "IMMUTABLE_HISTORY")), setOf("plans"))
            for (key in listOf("one", "two")) repository.append(context, "plans", key, "{\"text\":\"$key\"}", "USER")
            val results = listOf("one", "two").map { key -> async(Dispatchers.Default) {
                runCatching { repository.selectVersion(context, "1.0.0", "plans", key, 0) }
            } }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1L, repository.currentVersion(context, "plans")!!.revision)
            assertEquals(2, repository.list(context, "plans").size)
        } finally { database.close() }
    }

    @Test fun paginationReadsHistoryPastOneHundredWithoutDuplicateKeys() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            MutCubeDatabase::class.java).build()
        try {
            val project = RoomConversationRepository(database).createSpace("paging-fixture")
            val repository = TemplateRepository(database)
            val context = TemplateAccessContext(project.value, "test.page")
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            repository.enableWithGrants(TemplateBindingEntity(project.value, context.templateId, "1.0.0", true),
                listOf(TemplateCollectionEntity(context.templateId, "logs", 1, schema, "IMMUTABLE_HISTORY")))
            repeat(103) { repository.append(context, "logs", "log-${it.toString().padStart(3, '0')}", "{\"text\":\"$it\"}", "USER") }
            val keys = mutableListOf<String>()
            var time: Long? = null
            var key: String? = null
            do {
                val page = repository.page(context, "logs", time, key, 20)
                keys += page.map { it.recordKey }
                page.lastOrNull()?.let { time = it.updatedAt; key = it.recordKey }
            } while (page.size == 20)
            assertEquals(103, keys.size)
            assertEquals(103, keys.toSet().size)
        } finally { database.close() }
    }

    @Test fun explicitVersionCollectionsAreAuthorizedAtomically() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            MutCubeDatabase::class.java).build()
        try {
            val project = RoomConversationRepository(database).createSpace("version-grant-test")
            val repository = TemplateRepository(database)
            val context = TemplateAccessContext(project.value, "test.version")
            val binding = TemplateBindingEntity(project.value, context.templateId, "1.0.0", true)
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            val collection = TemplateCollectionEntity(context.templateId, "plans", 1, schema, "IMMUTABLE_HISTORY")
            assertTrue(runCatching { repository.enableWithGrants(binding, listOf(collection), setOf("undeclared")) }.isFailure)
            assertTrue(runCatching { repository.requireBinding(context, "1.0.0") }.isFailure)
            repository.enableWithGrants(binding, listOf(collection), setOf("plans"))
            repository.append(context, "plans", "one", "{\"text\":\"first\"}", "USER")
            repository.requireProposal(context, "1.0.0", "plans", 0)
            assertEquals(1L, repository.selectVersion(context, "1.0.0", "plans", "one", 0).revision)
            assertTrue(runCatching { repository.requireProposal(context, "1.0.0", "plans", 0) }.isFailure)
            repository.requireProposal(context, "1.0.0", "plans", 1)
            repository.revoke(context)
            assertTrue(runCatching { repository.requireProposal(context, "1.0.0", "plans", 1) }.isFailure)
        } finally { database.close() }
    }

    @Test fun versionSelectionRequiresExplicitGrantAndRejectsStaleConfirmation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("version-test")
            val other = conversations.createSpace("other-version-test")
            val repository = TemplateRepository(database)
            val context = TemplateAccessContext(project.value, "test.version")
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            repository.enableWithGrants(TemplateBindingEntity(project.value, "test.version", "1.0.0", true),
                listOf(TemplateCollectionEntity("test.version", "plans", 1, schema, "IMMUTABLE_HISTORY")))
            repository.append(context, "plans", "one", "{\"text\":\"first\"}", "USER")
            repository.append(context, "plans", "two", "{\"text\":\"second\"}", "USER")
            val firstPage = repository.page(context, "plans", null, null, 1)
            val last = firstPage.single()
            val secondPage = repository.page(context, "plans", last.updatedAt, last.recordKey, 1)
            assertEquals(2, (firstPage + secondPage).map { it.recordKey }.toSet().size)
            assertTrue(repository.page(context, "plans", secondPage.single().updatedAt, secondPage.single().recordKey, 1).isEmpty())
            assertTrue(runCatching { repository.page(context.copy(projectId = other.value), "plans", null, null, 1) }.isFailure)
            assertTrue(runCatching { repository.page(context, "plans", 0, null, 1) }.isFailure)
            assertNull(repository.currentVersion(context, "plans"))
            assertTrue(runCatching { repository.selectVersion(context, "1.0.0", "plans", "one", 0) }.isFailure)
            repository.grant(TemplatePermissionEntity(project.value, "test.version", "plans", "SELECT_VERSION", false))
            val first = repository.selectVersion(context, "1.0.0", "plans", "one", 0)
            assertEquals(1L, first.revision)
            assertEquals(first, repository.selectVersion(context, "1.0.0", "plans", "one", 1))
            assertTrue(runCatching { repository.selectVersion(context, "1.0.0", "plans", "two", 0) }.isFailure)
            assertTrue(runCatching { repository.selectVersion(context.copy(projectId = other.value), "1.0.0", "plans", "two", 1) }.isFailure)
            assertTrue(runCatching { repository.selectVersion(context, "1.0.0", "plans", "missing", 1) }.isFailure)
            assertEquals(2L, repository.selectVersion(context, "1.0.0", "plans", "two", 1).revision)
            assertEquals("{\"text\":\"first\"}", repository.read(context, "plans", "one")!!.json)
            repository.revoke(context)
            assertTrue(runCatching { repository.currentVersion(context, "plans") }.isFailure)
            assertTrue(runCatching { repository.selectVersion(context, "1.0.0", "plans", "one", 2) }.isFailure)
            conversations.deleteSpace(project)
            assertEquals(2, repository.userRecords.first().size)
        } finally { database.close() }
    }
}
