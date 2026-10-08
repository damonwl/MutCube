package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.ai.ModelMessageRole
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ModelSettingsStore
import com.dwl.mutcube.core.ai.DefaultModelSettingsStore
import com.dwl.mutcube.core.ai.ModelStreamEvent
import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.ai.ModelErrorKind
import com.dwl.mutcube.core.ai.ModelProviderException
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.core.ai.ProviderModel
import com.dwl.mutcube.core.ai.ModelCapabilities
import com.dwl.mutcube.core.ai.CapabilityState
import com.dwl.mutcube.core.ai.ReasoningLevel
import com.dwl.mutcube.core.ai.ProviderProfileStore
import com.dwl.mutcube.core.ai.ProviderModelCatalog
import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.FavoriteMessage
import com.dwl.mutcube.core.model.MessageId
import com.dwl.mutcube.core.model.MessageNodeId
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.MemoryEntry
import com.dwl.mutcube.core.model.MemoryId
import com.dwl.mutcube.core.model.ConversationCheckpoint
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessagePartId
import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import java.time.Instant
import java.time.ZoneId

class ChatGenerationServiceTest {
    @Test
    fun checkpointKeepsRecentMessagesAndIsReusedWithoutAnotherSummary() = runBlocking {
        val id = ConversationId("checkpoint")
        val history = (1..6).map { message("m$it", id, MessageRole.USER, "消息$it") }
        val repository = FakeConversationRepository(history)
        val manager = ConversationContextManager(repository)
        var calls = 0
        val first = manager.prepare(id, history, 4, null) { previous, older ->
            calls++
            assertEquals(null, previous)
            assertEquals(4, older.size)
            "前四条摘要"
        }
        assertEquals("前四条摘要", first.summary)
        assertEquals(listOf("m5", "m6"), first.recentMessages.map { it.id.value })
        val second = manager.prepare(id, history, 4, null) { _, _ -> error("Should reuse checkpoint") }
        assertEquals("前四条摘要", second.summary)
        assertEquals(1, calls)
    }

    @Test
    fun editedHistoryInvalidatesCheckpointBeforeReuse() = runBlocking {
        val id = ConversationId("checkpoint-edit")
        val history = (1..6).map { message("m$it", id, MessageRole.USER, "消息$it") }
        val repository = FakeConversationRepository(history)
        val manager = ConversationContextManager(repository)
        manager.prepare(id, history, 4, null) { _, _ -> "旧摘要" }
        val changed = history.toMutableList().apply { this[1] = this[1].copy(text = "已修改") }
        val result = manager.prepare(id, changed, 4, null) { previous, _ ->
            assertEquals(null, previous)
            "新摘要"
        }
        assertEquals("新摘要", result.summary)
    }

    @Test
    fun excessiveToolCallsPersistTraceAndReportSpecificFailure() = runBlocking {
        val id = ConversationId("limit")
        val repository = FakeConversationRepository(listOf(message("user", id, MessageRole.USER, "查询时间")))
        val gateway = ScriptedEventGateway((0..3).map { round ->
            flowOf(ModelStreamEvent.ToolCallDelta(0, "call-$round", "current_time", "{}"))
        })
        val error = runCatching { testChatGenerationService(repository, gateway, FakeCredentialStore()).generateReply(id) }.exceptionOrNull()
        assertEquals(true, (error as? ChatGenerationException)?.userMessage?.contains("轮数上限"))
        assertEquals(MessageStatus.FAILED, repository.currentMessages.last().status)
        assertEquals(3, repository.currentMessages.last().parts.filterIsInstance<MessageContentPart.ToolCall>().size)
    }

    @Test
    fun obsoleteToolHistoryIsFilteredWithoutDeletingVisibleRecords() {
        val original = message("assistant", ConversationId("test"), MessageRole.AI, "原回复").copy(parts = listOf(
            MessageContentPart.ToolCall(MessagePartId("old"), "old-call", "fitness_plan_current", "{}", com.dwl.mutcube.core.model.ToolCallStatus.SUCCEEDED),
            MessageContentPart.ToolResult(MessagePartId("old-result"), "old-call", "旧结果", false),
            MessageContentPart.ToolCall(MessagePartId("new"), "new-call", "current_time", "{}", com.dwl.mutcube.core.model.ToolCallStatus.SUCCEEDED),
            MessageContentPart.ToolResult(MessagePartId("new-result"), "new-call", "当前结果", false),
            MessageContentPart.Text(MessagePartId("text"), "原回复"),
        ))
        val outgoing = original.toModelMessages(true, setOf("current_time"))
        assertEquals(listOf("current_time"), outgoing.flatMap { it.toolCalls }.map { it.name })
        assertEquals(listOf("new-call"), outgoing.filter { it.role == ModelMessageRole.TOOL }.map { it.toolCallId })
        assertEquals(5, original.parts.size)
        assertEquals(0, original.toModelMessages(true).count { it.role == ModelMessageRole.TOOL })
    }

    @Test
    fun followUpSuggestionsAreSanitizedAndLimited() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("assistant", conversationId, MessageRole.AI, "你可以逐步提高训练量。")),
        )
        val gateway = FakeModelGateway(flowOf("1. 每周应该增加多少组？\n- 如何判断恢复情况？\n• RIR 怎么记录？\n多余问题？"))

        val suggestions = testChatGenerationService(repository, gateway, FakeCredentialStore())
            .generateFollowUpSuggestions(conversationId)

        assertEquals(listOf("每周应该增加多少组？", "如何判断恢复情况？", "RIR 怎么记录？"), suggestions)
        assertEquals(160, gateway.request.maxOutputTokens)
        assertEquals(com.dwl.mutcube.core.ai.ReasoningLevel.OFF, gateway.request.reasoningLevel)
    }

    @Test
    fun standaloneTranslationDoesNotCreateConversationMessages() = runBlocking {
        val repository = FakeConversationRepository(emptyList())
        val gateway = FakeModelGateway(flowOf("Hello"))

        val translated = testChatGenerationService(
            repository = repository,
            modelGateway = gateway,
            credentialStore = FakeCredentialStore(),
        ).translateText("你好", "简体中文", "英语")

        assertEquals("Hello", translated)
        assertEquals(emptyList<ChatMessage>(), repository.currentMessages)
        assertEquals(ModelMessageRole.SYSTEM, gateway.request.messages.first().role)
        assertEquals(true, gateway.request.messages.first().content.contains("英语"))
    }

    @Test
    fun enabledSkillInstructionsAreAddedToSystemContext() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "检查这段文字")),
        )
        val gateway = FakeModelGateway(flowOf("已检查"))

        testChatGenerationService(
            repository = repository,
            modelGateway = gateway,
            credentialStore = FakeCredentialStore(),
            contextInstructions = { latestUserText ->
                assertEquals("检查这段文字", latestUserText)
                listOf("能力：校对\n检查错别字并解释修改原因。")
            },
        ).generateReply(conversationId)

        assertEquals(ModelMessageRole.SYSTEM, gateway.request.messages.first().role)
        assertEquals(
            true,
            gateway.request.messages.first().content.contains("能力：校对"),
        )
    }

    @Test
    fun explicitlyLoadedManualSkillHasVerifiedAvailabilitySeparateFromUntrustedBody() = runBlocking {
        val conversationId = ConversationId("manual-skill")
        val repository = FakeConversationRepository(listOf(message("user", conversationId, MessageRole.USER,
            "\$review-skill 请按流程检查")))
        val gateway = FakeModelGateway(flowOf("完成"))
        val skill = StubSkillService("---\nname: review-skill\ndescription: Review text.\n---\nReply TOKEN_OK")

        testChatGenerationService(repository, gateway, FakeCredentialStore(), skillService = skill)
            .generateReply(conversationId)

        assertEquals("review-skill", skill.openedName)
        val system = gateway.request.messages.first { it.role == ModelMessageRole.SYSTEM }.content
        assertTrue(system.contains("宿主已确认本轮显式选择的 Skill「review-skill」"))
        assertTrue(system.contains("未出现在自动候选目录"))
        assertTrue(!system.contains("TOKEN_OK"))
        assertTrue(gateway.request.messages.any { it.role == ModelMessageRole.USER && it.content.contains("TOKEN_OK") })
    }

    @Test
    fun unavailableExplicitSkillIsNotPresentedAsLoaded() = runBlocking {
        val conversationId = ConversationId("missing-skill")
        val repository = FakeConversationRepository(listOf(message("user", conversationId, MessageRole.USER,
            "\$missing-skill 请按流程检查")))
        val gateway = FakeModelGateway(flowOf("无法使用"))

        testChatGenerationService(repository, gateway, FakeCredentialStore(), skillService = StubSkillService(null))
            .generateReply(conversationId)

        val system = gateway.request.messages.first { it.role == ModelMessageRole.SYSTEM }.content
        assertTrue(system.contains("宿主未能在当前会话作用域加载用户引用的 Skill「missing-skill」"))
        assertTrue(gateway.request.messages.none { it.content.contains("本轮用户明确选择的 Skill 文档") })
    }

    @Test
    fun providerInspectionUsesUnsavedCredentialWithoutPersistingIt() = runBlocking {
        val gateway = FakeCatalogGateway(listOf("mimo-v2.5-pro", "mimo-v2.5"))
        val credentialStore = FakeCredentialStore()

        val models = testChatGenerationService(
            FakeConversationRepository(emptyList()),
            gateway,
            credentialStore,
        ).inspectProvider(
            profileId = null,
            name = "测试 Provider",
            baseUrl = "https://api.example.com/v1/",
            modelId = "test-model",
            unsavedCredential = "temporary-key",
        )

        assertEquals(listOf("mimo-v2.5-pro", "mimo-v2.5"), models)
        assertEquals("https://api.example.com/v1", gateway.baseUrl)
        assertEquals(null, credentialStore.lastReadProviderId)
    }

    @Test
    fun providerInspectionFallsBackToMinimalGenerationWhenCatalogIsUnsupported() = runBlocking {
        val gateway = UnsupportedCatalogGateway()

        val models = testChatGenerationService(
            FakeConversationRepository(emptyList()), gateway, FakeCredentialStore(),
        ).inspectProvider(
            profileId = null,
            name = "兼容 Provider",
            baseUrl = "https://api.example.com/v1",
            modelId = "manual-model",
            unsavedCredential = "temporary-key",
        )

        assertEquals(emptyList<String>(), models)
        assertEquals(1, gateway.probeCalls)
        assertEquals(8, gateway.probeRequest.maxOutputTokens)
    }

    @Test
    fun toolCallRunsInBoundedLoopAndPersistsStructuredTrace() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "现在几点？")),
        )
        val gateway = ScriptedEventGateway(
            listOf(
                flowOf(
                    ModelStreamEvent.ToolCallDelta(0, "call-1", "current_time", "{"),
                    ModelStreamEvent.ToolCallDelta(0, null, null, "}"),
                ),
                flowOf(
                    ModelStreamEvent.TextDelta("现在是测试时间。"),
                    ModelStreamEvent.Usage(12, 4, 3),
                ),
            ),
        )

        val clock = listOf(0L, 2_000_000_000L).iterator()
        val traces = mutableListOf<List<MessageContentPart>>()
        testChatGenerationService(
            repository,
            gateway,
            FakeCredentialStore(),
            nanoTime = clock::next,
        ).generateReply(conversationId, onToolTrace = { traces += it })

        assertTrue(traces.any { trace -> trace.filterIsInstance<MessageContentPart.ToolCall>().any { it.status == com.dwl.mutcube.core.model.ToolCallStatus.RUNNING } })
        assertTrue(traces.last().any { it is MessageContentPart.ToolResult })
        assertEquals(com.dwl.mutcube.core.model.ToolCallStatus.SUCCEEDED, traces.last().filterIsInstance<MessageContentPart.ToolCall>().single().status)

        assertEquals(2, gateway.requests.size)
        assertEquals(listOf("current_time", "memory_search"), gateway.requests.first().tools.map { it.name })
        assertEquals(ModelMessageRole.ASSISTANT, gateway.requests.last().messages.takeLast(2).first().role)
        assertEquals("call-1", gateway.requests.last().messages.last().toolCallId)
        val response = repository.currentMessages.last()
        assertEquals("现在是测试时间。", response.text)
        assertEquals(1, response.parts.filterIsInstance<MessageContentPart.ToolCall>().size)
        assertEquals(false, response.parts.filterIsInstance<MessageContentPart.ToolResult>().single().isError)
        assertEquals(12L, response.inputTokens)
        assertEquals(4L, response.outputTokens)
        assertEquals(3L, response.cachedInputTokens)
        assertEquals(2_000L, response.generationDurationMs)
        assertEquals("mimo-v2.5-pro", response.modelId)

        repository.appendMessage(conversationId, MessageRole.USER, "那是北京时间吗？")
        val followUpGateway = FakeModelGateway(flowOf("是。"))
        testChatGenerationService(repository, followUpGateway, FakeCredentialStore()).generateReply(conversationId)
        assertEquals(
            listOf(
                ModelMessageRole.USER,
                ModelMessageRole.ASSISTANT,
                ModelMessageRole.TOOL,
                ModelMessageRole.ASSISTANT,
                ModelMessageRole.USER,
            ),
            followUpGateway.request.messages.filter { it.role != ModelMessageRole.SYSTEM }.map { it.role },
        )
    }

    @Test
    fun generationDurationExcludesExternalToolApprovalWait() = runBlocking {
        val conversationId = ConversationId("approval-timing")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "查询文档")),
        )
        val gateway = ScriptedEventGateway(listOf(
            flowOf(ModelStreamEvent.ToolCallDelta(0, "call-1", "mcp_search", "{}")),
            flowOf(ModelStreamEvent.TextDelta("查询完成"), ModelStreamEvent.Usage(10, 5, null)),
        ))
        val externalTools = object : ExternalToolService {
            override suspend fun definitions() = listOf(ModelToolDefinition("mcp_search", "搜索", "{\"type\":\"object\"}"))
            override suspend fun execute(call: ModelToolCall) =
                ExternalToolResult("找到文档", false, approvalWaitDurationNanos = 10_000_000_000L)
        }
        val clock = listOf(0L, 12_000_000_000L).iterator()

        testChatGenerationService(
            repository, gateway, FakeCredentialStore(),
            externalToolService = externalTools, nanoTime = clock::next,
        ).generateReply(conversationId)

        val response = repository.currentMessages.last()
        assertEquals(MessageStatus.COMPLETE, response.status)
        assertEquals(2_000L, response.generationDurationMs)
        assertEquals(5L, response.outputTokens)
    }

    @Test
    fun memoryToolsRespectWritePermissionScopeAndSecretFilter() = runBlocking {
        val projectId = SpaceId("project")
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(emptyList())
        val service = BuiltInToolService(
            repository,
            now = { Instant.parse("2026-09-15T10:00:00Z") },
            zoneId = { ZoneId.of("Asia/Shanghai") },
        )

        val denied = service.execute(
            ModelToolCall("1", "memory_save", "{\"content\":\"偏好晨练\"}"),
            projectId,
            conversationId,
            memoryWriteEnabled = false,
        )
        val saved = service.execute(
            ModelToolCall("2", "memory_save", "{\"content\":\"偏好晨练\"}"),
            projectId,
            conversationId,
            memoryWriteEnabled = true,
        )
        val rejectedSecret = service.execute(
            ModelToolCall("3", "memory_save", "{\"content\":\"API key 是 sk-abcdefghijklmnop\"}"),
            projectId,
            conversationId,
            memoryWriteEnabled = true,
        )
        val searched = service.execute(
            ModelToolCall("4", "memory_search", "{\"query\":\"晨练\"}"),
            projectId,
            conversationId,
            memoryWriteEnabled = true,
        )

        assertEquals(true, denied.isError)
        assertEquals(false, saved.isError)
        assertEquals(true, rejectedSecret.isError)
        assertEquals(1, repository.currentMemories.size)
        assertEquals(true, searched.output.contains("偏好晨练"))
        assertEquals(true, searched.output.contains("project"))
    }

    @Test
    fun titleGenerationUsesConversationProviderAndSanitizesOutput() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "怎么安排三分化训练？")),
            Space(SpaceId("space"), "健身", "title-provider"),
        )
        val gateway = FakeModelGateway(flowOf("《三分化训练安排》\n说明"))
        val providers = FakeProviderProfileStore(
            ProviderConfiguration(
                listOf(ProviderProfile("title-provider", "标题", "https://title.example/v1", "title-model")),
                "title-provider",
            ),
        )

        val title = testChatGenerationService(
            repository,
            gateway,
            FakeCredentialStore(),
            providerProfileStore = providers,
        ).generateConversationTitle(conversationId)

        assertEquals("三分化训练安排", title)
        assertEquals("三分化训练安排", repository.renamedTitle)
        assertEquals("title-model", gateway.request.modelId)
        assertEquals(0.3, gateway.request.temperature)
        assertEquals(40, gateway.request.maxOutputTokens)
    }

    @Test
    fun unavailableProjectProviderDoesNotFallBackToGlobal() = runBlocking {
        val conversationId = ConversationId("conversation")
        val global = ProviderProfile("global", "全局", "https://global.example/v1", "chat")
        for (profiles in listOf(listOf(global), listOf(global, global.copy(id = "project", enabled = false)))) {
            val repository = FakeConversationRepository(
                listOf(message("user", conversationId, MessageRole.USER, "你好")),
                Space(SpaceId("space"), "项目", "project"),
            )
            val credentialStore = FakeCredentialStore()
            val result = runCatching {
                testChatGenerationService(
                    repository,
                    FakeModelGateway(flowOf("不应调用")),
                    credentialStore,
                    providerProfileStore = FakeProviderProfileStore(ProviderConfiguration(profiles, "global")),
                ).generateConversationTitle(conversationId)
            }
            assertEquals(true, result.exceptionOrNull() is ChatGenerationException)
            assertEquals(null, credentialStore.lastReadProviderId)
        }
    }

    @Test
    fun automaticTitleCanBeDisabledWithoutMakingProviderRequest() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "问题")),
        )
        val gateway = FakeModelGateway(flowOf("标题"))
        val settings = FakeModelSettingsStore(ModelSettings(autoTitleEnabled = false))

        val title = testChatGenerationService(repository, gateway, FakeCredentialStore(), settings)
            .generateConversationTitle(conversationId, automatic = true)

        assertEquals(null, title)
        assertEquals(0, gateway.calls)
        assertEquals(null, repository.renamedTitle)
    }

    @Test
    fun conversationExportProducesReadableMarkdownAndStructuredJsonWithoutFileUris() = runBlocking {
        val conversationId = ConversationId("conversation")
        val attachment = MessageContentPart.Attachment(
            MessagePartId("part"), AttachmentKind.DOCUMENT, "file:///private/secret.pdf",
            "application/pdf", "资料.pdf", 120,
        )
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "问题").copy(parts = listOf(attachment))),
        )
        val service = ConversationExportService(repository)

        val markdown = service.export(conversationId, ConversationExportFormat.MARKDOWN)
        val json = service.export(conversationId, ConversationExportFormat.JSON)

        assertEquals("text/markdown", markdown.mimeType)
        assertEquals(true, markdown.content.contains("资料.pdf"))
        assertEquals(true, json.content.contains("\"schemaVersion\":3"))
        assertEquals(true, json.content.contains("\"generationDurationMs\""))
        assertEquals(false, json.content.contains("file:///private"))
        Json.parseToJsonElement(json.content)
        Unit
    }

    @Test
    fun projectSettingsOverrideOnlyConfiguredGlobalFields() = runBlocking {
        val conversationId = ConversationId("conversation")
        val project = Space(
            id = SpaceId("project"),
            name = "项目",
            modelProfileId = "project-provider",
            modelIdOverride = "mimo-project",
            systemPromptOverride = "项目提示",
            temperatureOverride = 0.25,
            maxOutputTokensOverride = 4096,
        )
        val repository = FakeConversationRepository(
            listOf(message("latest", conversationId, MessageRole.USER, "问题")),
            project,
        )
        val gateway = FakeModelGateway(flowOf("完成"))
        val global = ModelSettings("全局提示", 18, false, temperature = 0.9, topP = 0.7)
        val providers = FakeProviderProfileStore(
            ProviderConfiguration(
                profiles = listOf(
                    ProviderProfile("global-provider", "全局", "https://global.example/v1", "mimo-global"),
                    ProviderProfile("project-provider", "项目", "https://project.example/v1", "provider-default"),
                ),
                activeProfileId = "global-provider",
            ),
        )

        val credentials = FakeCredentialStore()
        testChatGenerationService(repository, gateway, credentials, FakeModelSettingsStore(global), providers)
            .generateReply(conversationId)

        assertEquals("mimo-project", gateway.request.modelId)
        assertEquals("https://project.example/v1", gateway.request.baseUrl)
        assertEquals("project-provider", credentials.lastReadProviderId)
        assertEquals(0.25, gateway.request.temperature)
        assertEquals(0.7, gateway.request.topP)
        assertEquals(4096, gateway.request.maxOutputTokens)
        assertEquals(true, gateway.request.messages.first().content.startsWith("项目提示\n\n"))
    }

    @Test
    fun conversationPromptOverridesProjectAndGlobalPrompt() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("latest", conversationId, MessageRole.USER, "问题")),
            conversationSpace = Space(SpaceId("project"), "项目", null, systemPromptOverride = "项目提示"),
            conversationPrompt = "当前会话提示",
        )
        val gateway = FakeModelGateway(flowOf("完成"))

        testChatGenerationService(
            repository,
            gateway,
            FakeCredentialStore(),
            FakeModelSettingsStore(ModelSettings(systemPrompt = "全局提示")),
        ).generateReply(conversationId)

        assertEquals(true, gateway.request.messages.first().content.startsWith("当前会话提示\n\n"))
    }

    @Test
    fun generationAppliesPersistedModelAndContextSettings() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(
                message("old", conversationId, MessageRole.USER, "旧消息"),
                message("latest", conversationId, MessageRole.USER, "新消息"),
            ),
        )
        val gateway = FakeModelGateway(flowOf("完成"))
        val settings = ModelSettings(
            systemPrompt = "保持简洁",
            maxContextMessages = 2,
            imageInputEnabled = false,
        )

        val providers = FakeProviderProfileStore(
            ProviderConfiguration(listOf(ProviderProfile("custom", "自定义", "https://example.com/v1", "mimo-custom")), "custom"),
        )
        testChatGenerationService(repository, gateway, FakeCredentialStore(), FakeModelSettingsStore(settings), providers)
            .generateReply(conversationId)

        assertEquals("mimo-custom", gateway.request.modelId)
        assertEquals(ModelMessageRole.SYSTEM, gateway.request.messages.first().role)
        assertEquals(true, gateway.request.messages.first().content.startsWith("保持简洁\n\n"))
        assertEquals(listOf("旧消息", "新消息"), gateway.request.messages.drop(1).map { it.content })
    }

    @Test
    fun modelCapabilityDeclarationsConstrainGenerationRequest() = runBlocking {
        val conversationId = ConversationId("conversation")
        val file = File.createTempFile("mutcube-unsupported-image", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val source = message("source", conversationId, MessageRole.USER, "描述图片").copy(
            parts = listOf(
                MessageContentPart.Text(MessagePartId("text"), "描述图片"),
                MessageContentPart.Attachment(
                    MessagePartId("image"), AttachmentKind.IMAGE, file.toURI().toString(),
                    "image/jpeg", "图.jpg", 3,
                ),
            ),
        )
        val model = ProviderModel(
            id = "limited-model",
            capabilities = ModelCapabilities(
                imageInput = CapabilityState.UNSUPPORTED,
                toolCalling = CapabilityState.UNSUPPORTED,
                reasoning = CapabilityState.UNSUPPORTED,
            ),
            maxOutputTokens = 100,
        )
        val providers = FakeProviderProfileStore(
            ProviderConfiguration(
                listOf(ProviderProfile("limited", "受限", "https://example.com/v1", model.id, models = listOf(model))),
                "limited",
            ),
        )
        val settings = ModelSettings(
            imageInputEnabled = true,
            toolCallingEnabled = true,
            reasoningLevel = ReasoningLevel.HIGH,
            maxOutputTokens = 4_000,
        )
        val gateway = FakeModelGateway(flowOf("完成"))

        testChatGenerationService(
            FakeConversationRepository(listOf(source)), gateway, FakeCredentialStore(),
            FakeModelSettingsStore(settings), providers,
        ).generateReply(conversationId)

        assertEquals(emptyList<com.dwl.mutcube.core.ai.ModelImage>(), gateway.request.messages.last().images)
        assertEquals(emptyList<com.dwl.mutcube.core.ai.ModelToolDefinition>(), gateway.request.tools)
        assertEquals(ReasoningLevel.OFF, gateway.request.reasoningLevel)
        assertEquals(100, gateway.request.maxOutputTokens)
    }

    @Test
    fun imageAttachmentIsSentAsMultimodalData() = runBlocking {
        val conversationId = ConversationId("conversation")
        val file = File.createTempFile("mutcube-image", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val source = message("source", conversationId, MessageRole.USER, "描述图片").copy(
            parts = listOf(
                MessageContentPart.Text(MessagePartId("text"), "描述图片"),
                MessageContentPart.Attachment(
                    MessagePartId("image"), AttachmentKind.IMAGE, file.toURI().toString(), "image/jpeg", "图.jpg", 3,
                ),
            ),
        )
        val repository = FakeConversationRepository(listOf(source))
        val gateway = FakeModelGateway(flowOf("完成"))

        testChatGenerationService(repository, gateway, FakeCredentialStore()).generateReply(conversationId)

        assertEquals("data:image/jpeg;base64,AQID", gateway.request.messages.last().images.single().dataUrl)
        file.delete()
        Unit
    }

    @Test
    fun textAttachmentContentIsBoundedAndAddedToPrompt() = runBlocking {
        val conversationId = ConversationId("conversation")
        val root = File.createTempFile("mutcube-document", "-test").apply { delete() }
        val file = File(root, "files/attachments/${java.util.UUID.randomUUID()}.txt").apply {
            parentFile?.mkdirs()
            writeText("附件里的训练安排")
        }
        val source = message("source", conversationId, MessageRole.USER, "总结").copy(
            parts = listOf(
                MessageContentPart.Text(MessagePartId("text"), "总结"),
                MessageContentPart.Attachment(
                    MessagePartId("document"), AttachmentKind.DOCUMENT, file.toURI().toString(),
                    "text/plain", "计划.txt", file.length(),
                ),
            ),
        )
        val gateway = FakeModelGateway(flowOf("完成"))

        testChatGenerationService(FakeConversationRepository(listOf(source)), gateway, FakeCredentialStore())
            .generateReply(conversationId)

        assertEquals(true, gateway.request.messages.last().content.contains("附件里的训练安排"))
        assertEquals(true, gateway.request.messages.last().content.contains("附件正文开始：计划.txt"))
        root.deleteRecursively()
        Unit
    }

    @Test
    fun translationUsesDedicatedPromptAndPersistsResult() = runBlocking {
        val conversationId = ConversationId("conversation")
        val source = message("source", conversationId, MessageRole.AI, "Hello")
        val repository = FakeConversationRepository(
            listOf(source),
            Space(SpaceId("space"), "项目", "translation-provider"),
        )
        val gateway = FakeModelGateway(flowOf("你", "好"))
        val credentials = FakeCredentialStore()
        val providers = FakeProviderProfileStore(
            ProviderConfiguration(
                listOf(ProviderProfile("translation-provider", "翻译", "https://translate.example/v1", "translate-model")),
                "translation-provider",
            ),
        )
        val service = testChatGenerationService(
            repository,
            gateway,
            credentials,
            providerProfileStore = providers,
        )

        service.translateMessage(conversationId, source.id, source.text)

        assertEquals(listOf(ModelMessageRole.SYSTEM, ModelMessageRole.USER), gateway.request.messages.map { it.role })
        assertEquals("https://translate.example/v1", gateway.request.baseUrl)
        assertEquals("translation-provider", credentials.lastReadProviderId)
        assertEquals("你好", repository.currentMessages.single().translation)
    }

    @Test
    fun regenerationExcludesAndReplacesOriginalAssistantMessage() = runBlocking {
        val conversationId = ConversationId("conversation")
        val assistantMessageId = MessageId("assistant")
        val repository = FakeConversationRepository(
            listOf(
                message("user", conversationId, MessageRole.USER, "question"),
                message(assistantMessageId.value, conversationId, MessageRole.AI, "old answer"),
            ),
        )
        val gateway = FakeModelGateway(flowOf("new ", "answer"))
        val service = testChatGenerationService(repository, gateway, FakeCredentialStore())

        service.generateReply(conversationId, MessageNodeId(assistantMessageId.value))

        assertEquals(listOf(ModelMessageRole.SYSTEM, ModelMessageRole.USER), gateway.request.messages.map { it.role })
        assertEquals(2, repository.currentMessages.size)
        assertEquals(MessageId("variant-1"), repository.currentMessages.last().id)
        assertEquals("new answer", repository.currentMessages.last().text)
        assertEquals(MessageStatus.COMPLETE, repository.currentMessages.last().status)
        assertEquals(2, repository.currentMessages.last().variantIds.size)
    }

    @Test
    fun cancellationPersistsPartialReplacementAsStopped() = runBlocking {
        val conversationId = ConversationId("conversation")
        val assistantMessageId = MessageId("assistant")
        val repository = FakeConversationRepository(
            listOf(
                message("user", conversationId, MessageRole.USER, "question"),
                message(assistantMessageId.value, conversationId, MessageRole.AI, "old answer"),
            ),
        )
        val firstDeltaEmitted = CompletableDeferred<Unit>()
        val gateway = FakeModelGateway(
            flow {
                emit("partial answer")
                firstDeltaEmitted.complete(Unit)
                awaitCancellation()
            },
        )
        val service = testChatGenerationService(repository, gateway, FakeCredentialStore())

        val generation = launch {
            service.generateReply(conversationId, MessageNodeId(assistantMessageId.value))
        }
        firstDeltaEmitted.await()
        generation.cancelAndJoin()

        assertEquals(2, repository.currentMessages.size)
        assertEquals(MessageId("variant-1"), repository.currentMessages.last().id)
        assertEquals("partial answer", repository.currentMessages.last().text)
        assertEquals(MessageStatus.STOPPED, repository.currentMessages.last().status)
    }

    @Test
    fun providerFailureBeforeFirstTokenIsPersistedWithErrorDetails() = runBlocking {
        val conversationId = ConversationId("conversation")
        val repository = FakeConversationRepository(
            listOf(message("user", conversationId, MessageRole.USER, "question")),
        )
        val gateway = FakeModelGateway(
            flow {
                throw ModelProviderException(ModelErrorKind.SERVER, 500, "Provider request failed")
            },
        )
        val service = testChatGenerationService(repository, gateway, FakeCredentialStore())

        runCatching { service.generateReply(conversationId) }

        val failed = repository.currentMessages.last()
        assertEquals(MessageRole.AI, failed.role)
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("模型服务暂时不可用（500）", failed.parts.filterIsInstance<MessageContentPart.Error>().single().message)
    }
}

/** Test conversations opt into a model explicitly; production starts with no preselected MiMo model. */
private fun testChatGenerationService(
    repository: ConversationRepository,
    modelGateway: ModelGateway,
    credentialStore: CredentialStore,
    modelSettingsStore: ModelSettingsStore = DefaultModelSettingsStore,
    providerProfileStore: ProviderProfileStore = FakeProviderProfileStore(ProviderConfiguration(
        listOf(ProviderProfile("mimo", "Xiaomi MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.5-pro")), "mimo",
    )),
    externalToolService: ExternalToolService = NoExternalToolService,
    contextInstructions: suspend (latestUserText: String) -> List<String> = { emptyList() },
    nanoTime: () -> Long = System::nanoTime,
    scopedToolService: ScopedToolService = NoScopedToolService,
    skillService: SkillService = NoSkillService,
) = ChatGenerationService(repository, modelGateway, credentialStore, modelSettingsStore, providerProfileStore,
    externalToolService, contextInstructions, nanoTime, scopedToolService, skillService)

private class StubSkillService(private val document: String?) : SkillService {
    var openedName: String? = null

    override suspend fun catalog(projectId: SpaceId?, query: String) = emptyList<SkillDescriptor>()
    override fun definitions() = emptyList<com.dwl.mutcube.core.ai.ModelToolDefinition>()
    override suspend fun execute(projectId: SpaceId?, explicitName: String?, call: ModelToolCall) =
        ExternalToolResult("Not used", true)

    override suspend fun openExplicit(projectId: SpaceId?, name: String): String? {
        openedName = name
        return document
    }
}

private class FakeModelGateway(private val response: Flow<String>) : ModelGateway {
    lateinit var request: GenerationRequest
    var calls: Int = 0

    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> {
        this.request = request
        calls += 1
        return response
    }
}

private class ScriptedEventGateway(private val responses: List<Flow<ModelStreamEvent>>) : ModelGateway {
    val requests = mutableListOf<GenerationRequest>()

    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> =
        error("String streaming is not used in this test")

    override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> {
        requests += request
        return responses[requests.lastIndex]
    }
}

private class FakeCatalogGateway(private val models: List<String>) : ModelGateway, ProviderModelCatalog {
    var baseUrl: String? = null

    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()

    override suspend fun listModels(
        baseUrl: String,
        credential: ApiCredential,
        protocol: com.dwl.mutcube.core.ai.ProviderProtocol,
    ): List<String> {
        this.baseUrl = baseUrl
        return models
    }
}

private class UnsupportedCatalogGateway : ModelGateway, ProviderModelCatalog {
    var probeCalls = 0
    lateinit var probeRequest: GenerationRequest

    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> {
        probeCalls += 1
        probeRequest = request
        return flowOf("OK")
    }

    override suspend fun listModels(
        baseUrl: String,
        credential: ApiCredential,
        protocol: com.dwl.mutcube.core.ai.ProviderProtocol,
    ): List<String> = throw ModelProviderException(ModelErrorKind.REQUEST, 404, "Not found")
}

private class FakeCredentialStore : CredentialStore {
    var lastReadProviderId: String? = null
    override suspend fun read(providerId: String): String {
        lastReadProviderId = providerId
        return "test-key"
    }

    override suspend fun write(providerId: String, credential: String) = Unit

    override suspend fun delete(providerId: String) = Unit
}

private class FakeModelSettingsStore(private val value: ModelSettings) : ModelSettingsStore {
    override val settings: Flow<ModelSettings> = flowOf(value)
    override suspend fun read(): ModelSettings = value
    override suspend fun write(settings: ModelSettings) = Unit
}

private class FakeProviderProfileStore(private val value: ProviderConfiguration) : ProviderProfileStore {
    override val configuration: Flow<ProviderConfiguration> = flowOf(value)
    override suspend fun read(): ProviderConfiguration = value
    override suspend fun write(configuration: ProviderConfiguration) = Unit
}

private class FakeConversationRepository(
    initialMessages: List<ChatMessage>,
    private val conversationSpace: Space? = null,
    private val conversationPrompt: String? = null,
) : ConversationRepository {
    private val messageFlow = MutableStateFlow(initialMessages)
    private val memoryFlow = MutableStateFlow<List<MemoryEntry>>(emptyList())

    val currentMessages: List<ChatMessage> get() = messageFlow.value
    val currentMemories: List<MemoryEntry> get() = memoryFlow.value
    var renamedTitle: String? = null
    private var checkpoint: ConversationCheckpoint? = null

    override suspend fun getConversationCheckpoint(conversationId: ConversationId): ConversationCheckpoint? = checkpoint

    override suspend fun saveConversationCheckpoint(checkpoint: ConversationCheckpoint) {
        this.checkpoint = checkpoint
    }

    override suspend fun deleteConversationCheckpoint(conversationId: ConversationId) {
        checkpoint = null
    }

    override val spaces: Flow<List<Space>> = flowOf(emptyList())
    override val conversations: Flow<List<Conversation>> = flowOf(emptyList())
    override val favoriteMessages: Flow<List<FavoriteMessage>> = flowOf(emptyList())
    override val memories: Flow<List<MemoryEntry>> = memoryFlow

    override fun searchConversations(query: String): Flow<List<Conversation>> = flowOf(emptyList())

    override fun observeMessages(conversationId: ConversationId): Flow<List<ChatMessage>> = messageFlow

    override suspend fun getConversation(conversationId: ConversationId): Conversation = Conversation(
        id = conversationId,
        title = "测试对话",
        createdAt = 1,
        updatedAt = 2,
        systemPrompt = conversationPrompt,
    )

    override suspend fun getConversationSpace(conversationId: ConversationId): Space? = conversationSpace

    override suspend fun createSpace(name: String): SpaceId = error("Not used")

    override suspend fun renameSpace(spaceId: SpaceId, name: String) = error("Not used")

    override suspend fun setSpacePinned(spaceId: SpaceId, pinned: Boolean) = error("Not used")

    override suspend fun deleteSpace(spaceId: SpaceId) = error("Not used")

    override suspend fun createWithFirstUserMessage(text: String, spaceId: SpaceId?): ConversationId = error("Not used")

    override suspend fun setConversationSpace(conversationId: ConversationId, spaceId: SpaceId?) = error("Not used")

    override suspend fun renameConversation(conversationId: ConversationId, title: String) {
        renamedTitle = title
    }

    override suspend fun setConversationPinned(conversationId: ConversationId, pinned: Boolean) = error("Not used")

    override suspend fun trashConversation(conversationId: ConversationId) = error("Not used")

    override suspend fun restoreConversation(conversationId: ConversationId) = error("Not used")

    override suspend fun trashAllConversations(): Int = error("Not used")

    override suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        text: String,
        status: MessageStatus,
    ) {
        messageFlow.value += message("appended", conversationId, role, text, status)
    }

    override suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus,
    ) {
        val text = parts.filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text }
        messageFlow.value += message("appended", conversationId, role, text, status).copy(parts = parts)
    }

    override suspend fun appendGeneratedMessage(
        conversationId: ConversationId,
        parts: List<MessageContentPart>,
        status: MessageStatus,
        modelId: String,
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long?,
        generationDurationMs: Long?,
    ) {
        appendMessage(conversationId, MessageRole.AI, parts, status)
        messageFlow.value = messageFlow.value.dropLast(1) + messageFlow.value.last().copy(
            modelId = modelId,
            inputTokens = inputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens,
            generationDurationMs = generationDurationMs,
        )
    }

    override suspend fun replaceMessage(
        conversationId: ConversationId,
        messageId: MessageId,
        text: String,
        status: MessageStatus,
    ) {
        messageFlow.value = messageFlow.value.map { existing ->
            if (existing.id == messageId) existing.copy(text = text, status = status) else existing
        }
    }

    override suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        text: String,
        status: MessageStatus,
    ): MessageId {
        val variantId = MessageId("variant-1")
        messageFlow.value = messageFlow.value.map { existing ->
            if (existing.nodeId == nodeId) {
                existing.copy(
                    id = variantId,
                    role = role,
                    text = text,
                    status = status,
                    variantIds = existing.variantIds + variantId,
                    selectedVariantIndex = existing.variantIds.size,
                )
            } else {
                existing
            }
        }
        return variantId
    }

    override suspend fun appendGeneratedMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        parts: List<MessageContentPart>,
        status: MessageStatus,
        modelId: String,
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long?,
        generationDurationMs: Long?,
    ): MessageId {
        val id = appendMessageVariant(conversationId, nodeId, MessageRole.AI, parts, status)
        messageFlow.value = messageFlow.value.map { existing ->
            if (existing.id == id) existing.copy(
                modelId = modelId,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens,
                generationDurationMs = generationDurationMs,
            ) else existing
        }
        return id
    }

    override suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus,
    ): MessageId {
        val variantId = appendMessageVariant(
            conversationId,
            nodeId,
            role,
            parts.filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text },
            status,
        )
        messageFlow.value = messageFlow.value.map { existing ->
            if (existing.id == variantId) existing.copy(parts = parts) else existing
        }
        return variantId
    }

    override suspend fun selectMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        variantId: MessageId,
    ) = error("Not used")

    override suspend fun editMessage(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        text: String,
    ): MessageId = error("Not used")

    override suspend fun deleteMessageBranch(conversationId: ConversationId, nodeId: MessageNodeId) = error("Not used")

    override suspend fun forkConversation(
        conversationId: ConversationId,
        throughNodeId: MessageNodeId,
    ): ConversationId = error("Not used")

    override suspend fun setMessageFavorite(
        conversationId: ConversationId,
        messageId: MessageId,
        favorite: Boolean,
    ) = error("Not used")

    override suspend fun setMessageTranslation(
        conversationId: ConversationId,
        messageId: MessageId,
        translation: String?,
    ) {
        messageFlow.value = messageFlow.value.map { message ->
            if (message.id == messageId) message.copy(translation = translation) else message
        }
    }

    override suspend fun searchMemories(spaceId: SpaceId?, query: String, limit: Int): List<MemoryEntry> =
        memoryFlow.value.filter { memory ->
            (memory.spaceId == null || memory.spaceId == spaceId) && memory.content.contains(query)
        }.take(limit)

    override suspend fun saveMemory(
        spaceId: SpaceId?,
        content: String,
        sourceConversationId: ConversationId?,
    ): MemoryId {
        val id = MemoryId("memory-${memoryFlow.value.size + 1}")
        memoryFlow.value += MemoryEntry(id, spaceId, content, sourceConversationId, 1L, 1L)
        return id
    }
}

private fun message(
    id: String,
    conversationId: ConversationId,
    role: MessageRole,
    text: String,
    status: MessageStatus = MessageStatus.COMPLETE,
) = ChatMessage(
    id = MessageId(id),
    conversationId = conversationId,
    role = role,
    text = text,
    createdAt = 1L,
    status = status,
)
