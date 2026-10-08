package com.dwl.mutcube.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.MutCubeApplication
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.template.builtin.NotesTemplate
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in: real paid Provider calls, but all business data stays in a separate database. */
class TemplateLiveProviderTest {
    @Test fun notesRoundTripWithConfiguredProvider() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveProvider") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as MutCubeApplication).container
        val name = "template-live-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MutCubeDatabase::class.java, name).build()
        try {
            val conversations = RoomConversationRepository(db)
            val project = conversations.createSpace("隔离真实模型验收")
            val manifest = NotesTemplate.manifest()
            var repository = TemplateRepository(db)
            val configuredSettings = container.modelSettingsStore.read().copy(toolCallingEnabled = true, memoryWriteEnabled = false)
            val settings = object : ModelSettingsStore {
                private val value = configuredSettings
                override val settings: Flow<ModelSettings> = flowOf(value)
                override suspend fun read() = value
                override suspend fun write(settings: ModelSettings) = error("Read-only test settings")
            }
            val gateway = OpenAiCompatibleModelGateway()
            var runtime = TemplateRuntime(repository, conversations, container.providerProfileStore, settings, container.credentialStore, gateway)
            runtime.enable(manifest, project)
            var engine = TemplateActions(repository, runtime, conversations).engine
            val instance = TemplateInstance(project.value, manifest.id)
            val access = TemplateAccessContext(project.value, manifest.id)
            engine.execute(instance, manifest, "notes.save", ActionChannel.GUI, "live-note", """{"text":"这是一条隔离验收笔记：项目代号是蓝色方块，验收编号是7391。不是用户业务数据。"}""")
            val candidate = withTimeout(240_000) {
                engine.execute(instance, manifest, "document.generate", ActionChannel.GUI, UUID.randomUUID().toString(), """{"request":"整理笔记，准确保留项目代号和验收编号","baseRevision":0}""")
            }
            assertTrue(candidate.pendingConfirmation)
            assertTrue(candidate.data.contains("7391"))
            assertNull(repository.currentVersion(access, "documents"))
            val confirmation = """{"key":"${candidate.key}","expectedRevision":0}"""
            engine.withConfirmation { _, _, _ -> false }.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "cancel", confirmation)
            assertNull(repository.currentVersion(access, "documents"))
            engine.withConfirmation { _, _, _ -> true }.execute(instance, manifest, "document.confirm", ActionChannel.GUI, "confirm", confirmation)
            db.close()
            db = Room.databaseBuilder(context, MutCubeDatabase::class.java, name).build()
            repository = TemplateRepository(db)
            val reopenedConversations = RoomConversationRepository(db)
            runtime = TemplateRuntime(repository, reopenedConversations, container.providerProfileStore, settings, container.credentialStore, gateway)
            engine = TemplateActions(repository, runtime, reopenedConversations).engine
            assertTrue(engine.execute(instance, manifest, "document.current", ActionChannel.GUI, "reopen", "{}").data.contains("7391"))
            val tools = TemplateDataToolService(repository, runtime, reopenedConversations, listOf(manifest))
            val chat = reopenedConversations.createWithFirstUserMessage("请务必调用智能笔记的 notes.list 工具，查询后告诉我笔记中的验收编号，不要猜测。", project)
            val service = ChatGenerationService(reopenedConversations, gateway, container.credentialStore, settings, container.providerProfileStore, scopedToolService = tools)
            withTimeout(240_000) { service.generateReply(chat) }
            val messages = reopenedConversations.observeMessages(chat).first()
            assertTrue(messages.any { it.role == MessageRole.AI && it.text.contains("7391") })
            assertTrue(messages.flatMap { it.parts }.filterIsInstance<MessageContentPart.ToolCall>().any { it.toolName == TemplateDataToolService.toolName(manifest.id, "notes.list") })
            repository.revoke(access)
            assertTrue(tools.definitions(project).isEmpty())
            assertTrue(runCatching { engine.execute(instance, manifest, "notes.list", ActionChannel.GUI, "revoked", "{}") }.isFailure)
        } finally {
            db.close()
            check(name.startsWith("template-live-") && name.endsWith(".db"))
            context.deleteDatabase(name)
        }
    }
}
