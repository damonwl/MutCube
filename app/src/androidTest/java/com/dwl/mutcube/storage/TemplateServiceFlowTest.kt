package com.dwl.mutcube.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.template.builtin.NotesTemplate
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TemplateServiceFlowTest {
    @Test fun declaredCrudAndChatShareOneScopedServiceAndConfirmProposals() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("isolated-service")
            val other = conversations.createSpace("isolated-other")
            val repository = TemplateRepository(database)
            val manifest = NotesTemplate.manifest()
            val credentials = object : CredentialStore {
                override suspend fun read(providerId: String) = "fixture-key"
                override suspend fun write(providerId: String, credential: String) = error("No writes")
                override suspend fun delete(providerId: String) = error("No deletes")
            }
            var modelCalls = 0
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> {
                    modelCalls++; return flowOf("""{"text":"整理后的笔记"}""")
                }
            }
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore, credentials, gateway)
            runtime.enable(manifest, project)
            val instance = TemplateInstance(project.value, manifest.id)
            val access = TemplateAccessContext(project.value, manifest.id)
            val engine = TemplateActions(repository, runtime, conversations).engine
            repeat(2) { engine.execute(instance, manifest, "notes.save", ActionChannel.GUI, "note", """{"text":"原始笔记"}""") }
            assertEquals(1, repository.list(access, "notes").size)
            val tools = TemplateDataToolService(repository, runtime, conversations, listOf(manifest))
            assertEquals(4, tools.definitions(project).size)
            assertTrue(tools.definitions(other).isEmpty())
            val listCall = ModelToolCall("call", TemplateDataToolService.toolName(manifest.id, "notes.list"), "{}")
            assertFalse(tools.execute(project, listCall).isError)
            assertTrue(tools.execute(other, listCall).isError)
            assertTrue(tools.execute(project, listCall.copy(name = "template_data_list")).isError)
            assertTrue(tools.execute(project, listCall.copy(name = TemplateDataToolService.toolName(manifest.id, "notes.save"), argumentsJson = """{"text":"attack"}""")).isError)
            engine.execute(instance, manifest, "notes.update", ActionChannel.GUI, "edit", """{"key":"note","expectedRevision":1,"data":{"text":"已修改"}}""")
            assertTrue(runCatching { engine.execute(instance, manifest, "notes.update", ActionChannel.GUI, "stale", """{"key":"note","expectedRevision":1,"data":{"text":"stale"}}""") }.isFailure)
            val generated = tools.execute(project, listCall.copy(name = TemplateDataToolService.toolName(manifest.id, "document.generate"), argumentsJson = """{"request":"整理","baseRevision":0}"""))
            assertFalse(generated.isError)
            val key = JSONObject(generated.output).getString("key")
            assertTrue(JSONObject(generated.output).getBoolean("pendingConfirmation"))
            assertEquals("已修改", JSONObject(repository.runs(access).first().single().inputJson).getJSONObject("note").getString("text"))
            assertNull(repository.currentVersion(access, "documents"))
            val confirmation = JSONObject().put("key", key).put("expectedRevision", 0).toString()
            assertTrue(runCatching { engine.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "deny", confirmation) }.isFailure)
            engine.withConfirmation { _, _, _ -> false }.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "cancel", confirmation)
            assertNull(repository.currentVersion(access, "documents"))
            engine.withConfirmation { _, _, _ -> true }.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "confirm", confirmation)
            assertEquals(1L, repository.currentVersion(access, "documents")!!.revision)
            assertTrue(runCatching { engine.withConfirmation { _, _, _ -> true }.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "stale", confirmation) }.isFailure)
            assertTrue(runCatching { repository.update(access, manifest.version, "requests", key, 1, "{}") }.isFailure)
            engine.withConfirmation { _, _, _ -> true }.execute(instance, manifest, "notes.delete", ActionChannel.GUI, "delete", """{"key":"note","expectedRevision":2}""")
            assertTrue(repository.list(access, "notes").isEmpty())
            assertEquals(1, repository.list(access, "documents").size)
            assertEquals(1, modelCalls)
            assertTrue(conversations.conversations.first().isEmpty())
            // Maintenance is host-only and scoped: wrong names and active generation leave everything intact.
            runtime.enable(manifest, other)
            val otherAccess = TemplateAccessContext(other.value, manifest.id)
            engine.execute(TemplateInstance(other.value, manifest.id), manifest, "notes.save", ActionChannel.GUI,
                "other-note", """{"text":"其他项目数据必须保留"}""")
            conversations.createWithFirstUserMessage("聊天不清除", project)
            val beforeBindings = repository.userBindings.first()
            val beforePermissions = repository.userPermissions.first()
            val before = repository.previewDeveloperReset(access)
            assertTrue(before.records > 0)
            assertTrue(runCatching { repository.resetForDeveloper(access, "错误项目名称") }.isFailure)
            assertEquals(before, repository.previewDeveloperReset(access))
            val now = System.currentTimeMillis()
            repository.beginRun(TemplateRunEntity("running-reset-test", project.value, manifest.id, "document.generate",
                "{}", null, "fixture", "fixture", "RUNNING", null, now, now), manifest.version)
            assertTrue(runCatching { repository.resetForDeveloper(access, "isolated-service") }.isFailure)
            assertNotNull(repository.currentVersion(access, "documents"))
            repository.finishRun("running-reset-test", "CANCELLED", null, "fixture")
            val removed = repository.resetForDeveloper(access, "isolated-service")
            assertEquals(before.records, removed.records)
            assertEquals(before.runs + 1, removed.runs)
            assertEquals(TemplateResetPreview(0, 0), repository.previewDeveloperReset(access))
            assertNull(repository.currentVersion(access, "documents"))
            assertNull(repository.summary(access, "notes"))
            assertTrue(repository.runs(access).first().isEmpty())
            assertFalse(repository.audits.first().any { it.projectId == project.value && it.operation == "APPEND" })
            assertEquals(beforeBindings, repository.userBindings.first())
            assertEquals(beforePermissions, repository.userPermissions.first())
            assertEquals(1, repository.list(otherAccess, "notes").size)
            assertEquals(1, conversations.conversations.first().size)
            // Same deterministic request identifier can be used again after a full reset.
            engine.execute(instance, manifest, "notes.save", ActionChannel.GUI, "note", """{"text":"重新测试"}""")
            assertEquals(1, repository.list(access, "notes").size)
            repository.revoke(access)
            assertTrue(tools.definitions(project).isEmpty())
            assertTrue(tools.execute(project, listCall).isError)
        } finally { database.close() }
    }
}
