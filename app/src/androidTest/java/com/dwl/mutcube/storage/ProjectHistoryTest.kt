package com.dwl.mutcube.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.MutCubeApplication
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.context.ProjectConversationContext
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.template.builtin.*
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProjectHistoryTest {
    @Test fun sufficientPrefetchedEvidenceUsesOneModelRequestAndClearsProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val repo = RoomConversationRepository(db)
            val project = repo.createSpace("隔离快速生成")
            repo.createWithFirstUserMessage("高位下拉55kg，4组10次，RIR 1。" + "有来源的说明。".repeat(150), project)
            val repository = TemplateRepository(db)
            var calls = 0
            lateinit var runtime: TemplateRuntime
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> {
                    calls++
                    assertTrue(request.messages.first().content.contains("55kg"))
                    assertTrue(request.messages.first().content.contains("有来源的说明。".repeat(2)))
                    assertTrue(runtime.progress.value!!.message.contains("生成候选"))
                    return flowOf(FitnessFixture.plan("2026-09-18"))
                }
            }
            val manifest = FitnessTemplate.manifest()
            runtime = TemplateRuntime(repository, repo, FitnessFixture.profiles, DefaultModelSettingsStore, FitnessFixture.credentials, gateway)
            runtime.enable(manifest, project)
            val engine = TemplateActions(repository, runtime, repo).engine
            val instance = TemplateInstance(project.value, manifest.id)
            engine.execute(instance, manifest, "profile.save", ActionChannel.GUI, "profile", FitnessFixture.profile)
            engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "generate",
                """{"request":"参考高位下拉已有重量生成计划","baseRevision":0,"targetDate":"2026-09-18"}""")
            assertEquals(1, calls)
            assertNull(runtime.progress.value)
            assertNull(repository.currentVersion(TemplateAccessContext(project.value, manifest.id), "plans"))
        } finally { db.close() }
    }

    @Test fun scopedEvidenceIncludesTxtAndRejectsMovedDeletedAndOtherProjectSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        val store = AttachmentStore(context)
        val attachment = store.saveText("高位下拉 55kg 4×10 RIR 1，附件校验8237")
        try {
            val repo = RoomConversationRepository(db)
            val project = repo.createSpace("A")
            val other = repo.createSpace("B")
            val source = repo.createWithFirstUserMessage(listOf(attachment), project)
            val forbidden = repo.createWithFirstUserMessage("高位下拉 跨项目秘密9933", other)
            val trashed = repo.createWithFirstUserMessage("高位下拉 回收站秘密8877", project)
            repo.trashConversation(trashed)
            repo.appendMessage(source, MessageRole.AI, "高位下拉 失败秘密7766", MessageStatus.FAILED)
            val history = ProjectConversationContext(repo)
            val result = history.search(project, "高位下拉 附件")
            assertTrue(result.contains("8237"))
            assertFalse(result.contains("9933")); assertFalse(result.contains("8877")); assertFalse(result.contains("7766"))
            val message = repo.observeMessages(source).first().first()
            assertTrue(history.read(project, source.value, message.id.value).contains("55kg"))
            val hidden = repo.appendMessageVariant(source, message.nodeId, MessageRole.USER, "高位下拉 未选择分支6666")
            repo.selectMessageVariant(source, message.nodeId, message.id)
            assertFalse(history.search(project, "高位下拉").contains("6666"))
            assertTrue(runCatching { history.read(project, source.value, hidden.value) }.isFailure)
            assertTrue(runCatching { history.read(project, forbidden.value, "unknown") }.isFailure)
            assertTrue(runCatching { history.execute(null, ModelToolCall("id", ProjectConversationContext.SEARCH, "{\"query\":\"高位下拉\"}")) }.isFailure)
            repo.setConversationSpace(source, other)
            assertTrue(runCatching { history.read(project, source.value, message.id.value) }.isFailure)
            assertFalse(history.search(project, "高位下拉").contains("8237"))
        } finally { db.close(); store.deleteDraft(attachment) }
    }

    @Test fun normalChatAndTemplateShareSearchReadAndArchiveTemplateEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val repo = RoomConversationRepository(db)
            val project = repo.createSpace("隔离上下文")
            val source = repo.createWithFirstUserMessage("我目前高位下拉55kg，4组10次，RIR 1；这是已提供的工作重量。", project)
            val sourceMessage = repo.observeMessages(source).first().first()
            val captured = mutableListOf<GenerationRequest>()
            var round = 0
            var template = false
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential) = error("Use structured events")
                override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> {
                    captured += request
                    return when (round++) {
                        0 -> flowOf(ModelStreamEvent.ToolCallDelta(0, "search", ProjectConversationContext.SEARCH, "{\"query\":\"高位下拉\"}"))
                        1 -> flowOf(ModelStreamEvent.ToolCallDelta(0, "read", ProjectConversationContext.READ,
                            """{"conversationId":"${source.value}","messageId":"${sourceMessage.id.value}"}"""))
                        else -> flowOf(ModelStreamEvent.TextDelta(if (template) FitnessFixture.plan("2026-09-18") else "根据项目来源记录，高位下拉55kg。"))
                    }
                }
            }
            val chat = repo.createWithFirstUserMessage("我背部的工作重量是多少？请查询项目历史。", project)
            ChatGenerationService(repo, gateway, FitnessFixture.credentials,
                providerProfileStore = FitnessFixture.profiles).generateReply(chat)
            assertTrue(captured.first().messages.first().content.contains("55kg"))
            assertTrue(repo.observeMessages(chat).first().last().text.contains("55kg"))
            assertEquals(2, repo.observeMessages(chat).first().last().parts.filterIsInstance<MessageContentPart.ToolCall>().size)
            template = true; round = 0; captured.clear()
            val repository = TemplateRepository(db)
            val manifest = FitnessTemplate.manifest()
            val runtime = TemplateRuntime(repository, repo, FitnessFixture.profiles, DefaultModelSettingsStore, FitnessFixture.credentials, gateway)
            runtime.enable(manifest, project)
            val engine = TemplateActions(repository, runtime, repo).engine
            val instance = TemplateInstance(project.value, manifest.id)
            engine.execute(instance, manifest, "profile.save", ActionChannel.GUI, "profile", FitnessFixture.profile)
            val candidate = engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "generate",
                """{"request":"生成背部训练，参考其他对话中高位下拉的工作重量","baseRevision":0,"targetDate":"2026-09-18"}""")
            assertTrue(candidate.pendingConfirmation)
            val access = TemplateAccessContext(project.value, manifest.id)
            val run = repository.runs(access).first().single()
            assertTrue(run.contextJson.orEmpty().contains("55kg"))
            assertEquals(2, JSONObject(run.contextJson!!).getJSONArray("projectHistoryTools").length())
            assertNull(repository.currentVersion(access, "plans"))
            assertTrue(captured.first().tools.any { it.name == ProjectConversationContext.READ })
            assertTrue(captured.last().tools.isEmpty())
            assertEquals(3, captured.size)
        } finally { db.close() }
    }

    @Test fun liveProviderFindsFactsAcrossChatsAndTemplateGeneration() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveProvider") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as MutCubeApplication).container
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val repo = RoomConversationRepository(db)
            val project = repo.createSpace("隔离真实历史检索")
            repo.createWithFirstUserMessage("本项目代号Orion73，验收编号7391。请准确保留这两个事实。", project)
            val configured = container.modelSettingsStore.read().copy(toolCallingEnabled = true, memoryWriteEnabled = false)
            val settings = object : ModelSettingsStore {
                override val settings = flowOf(configured)
                override suspend fun read() = configured
                override suspend fun write(settings: ModelSettings) = error("Read-only")
            }
            val gateway = OpenAiCompatibleModelGateway()
            val chat = repo.createWithFirstUserMessage("请务必调用project_history_search，搜索其他对话提供的项目代号与验收编号，然后准确回答。", project)
            withTimeout(180_000) {
                ChatGenerationService(repo, gateway, container.credentialStore, settings, container.providerProfileStore).generateReply(chat)
            }
            val reply = repo.observeMessages(chat).first().last()
            assertTrue(reply.text.contains("7391")); assertTrue(reply.text.contains("Orion73"))
            assertTrue(reply.parts.filterIsInstance<MessageContentPart.ToolCall>().any { it.toolName == ProjectConversationContext.SEARCH })
            val repository = TemplateRepository(db)
            val manifest = NotesTemplate.manifest()
            val runtime = TemplateRuntime(repository, repo, container.providerProfileStore, settings, container.credentialStore, gateway)
            runtime.enable(manifest, project)
            val engine = TemplateActions(repository, runtime, repo).engine
            val instance = TemplateInstance(project.value, manifest.id)
            engine.execute(instance, manifest, "notes.save", ActionChannel.GUI, "note", """{"text":"验收文稿需要保留其他会话提供的项目代号与编号。"}""")
            val candidate = withTimeout(200_000) {
                engine.execute(instance, manifest, "document.generate", ActionChannel.GUI, "generate",
                    """{"request":"务必先调用project_history_search查询其他会话的项目代号和验收编号，再生成包含这两个精确值的中文文稿","baseRevision":0}""")
            }
            assertTrue(candidate.data.contains("7391")); assertTrue(candidate.data.contains("Orion73"))
            val run = repository.runs(TemplateAccessContext(project.value, manifest.id)).first().single()
            assertTrue(JSONObject(run.contextJson!!).getJSONArray("projectHistoryTools").length() >= 1)
            assertNull(repository.currentVersion(TemplateAccessContext(project.value, manifest.id), "documents"))
        } finally { db.close() }
    }
}
