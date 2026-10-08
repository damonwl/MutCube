package com.dwl.mutcube.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessagePartId
import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.core.model.SpaceModelOverrides
import com.dwl.mutcube.core.model.ConversationCheckpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomConversationRepositoryTest {
    @Test
    fun checkpointAndEditableMemoryPersistWithoutChangingChatHistory() = runBlocking {
        val repository = RoomConversationRepository(database)
        val project = repository.createSpace("训练")
        val conversation = repository.createWithFirstUserMessage("我喜欢三分化", project)
        val original = repository.observeMessages(conversation).first().single()
        val memoryId = repository.saveMemory(project, "偏好三分化", conversation)
        assertEquals(memoryId, repository.saveMemory(project, "偏好三分化", conversation))
        repository.updateMemory(memoryId, "偏好三分化，每次训练 60 分钟")
        assertEquals("偏好三分化，每次训练 60 分钟", repository.memories.first().single().content)

        val checkpoint = ConversationCheckpoint(conversation, original.id, "fingerprint", "早期会话摘要", 1L)
        repository.saveConversationCheckpoint(checkpoint)
        assertEquals("早期会话摘要", repository.getConversationCheckpoint(conversation)?.summary)
        assertEquals("我喜欢三分化", repository.observeMessages(conversation).first().single().text)
        repository.deleteConversationCheckpoint(conversation)
        assertEquals(null, repository.getConversationCheckpoint(conversation))
    }

    private lateinit var database: MutCubeDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun projectModelOverridesPersistAndResolveFromConversation() = runBlocking {
        val repository = RoomConversationRepository(database)
        val spaceId = repository.createSpace("研究")
        val conversationId = repository.createWithFirstUserMessage("问题", spaceId)

        repository.updateSpaceModelSettings(
            spaceId,
            SpaceModelOverrides("mimo-project", "项目提示", 12, false, 0.3, 0.8, 2048),
        )
        repository.setSpaceModelProfile(spaceId, "provider-project")

        val space = requireNotNull(repository.getConversationSpace(conversationId))
        assertEquals("provider-project", space.modelProfileId)
        assertEquals("mimo-project", space.modelIdOverride)
        assertEquals("项目提示", space.systemPromptOverride)
        assertEquals(12, space.maxContextMessagesOverride)
        assertEquals(false, space.imageInputEnabledOverride)
        assertEquals(0.3, space.temperatureOverride)
        assertEquals(0.8, space.topPOverride)
        assertEquals(2048, space.maxOutputTokensOverride)
    }

    @Test
    fun structuredAttachmentIsPersistedAndRetainedWhenEditingText() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(database, now = { 42L }, newId = { "id-${++nextId}" })
        val attachment = MessageContentPart.Attachment(
            MessagePartId("attachment"), AttachmentKind.IMAGE, "file:///image.jpg", "image/jpeg", "image.jpg", 12,
        )
        val conversationId = repository.createWithFirstUserMessage(
            listOf(MessageContentPart.Text(MessagePartId("text"), "看看图片"), attachment),
        )

        val saved = repository.observeMessages(conversationId).first().single()
        assertEquals(attachment.uri, saved.parts.filterIsInstance<MessageContentPart.Attachment>().single().uri)
        repository.editMessage(conversationId, saved.nodeId, "换个问题")
        val edited = repository.observeMessages(conversationId).first().single()
        assertEquals("换个问题", edited.text)
        assertEquals(attachment.uri, edited.parts.filterIsInstance<MessageContentPart.Attachment>().single().uri)
    }

    @Test
    fun generationFailureDetailsSurviveConversationReload() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(database, now = { 42L }, newId = { "id-${++nextId}" })
        val conversationId = repository.createWithFirstUserMessage("测试失败持久化")

        repository.appendGeneratedMessage(
            conversationId = conversationId,
            parts = listOf(MessageContentPart.Error(MessagePartId("failure"), "模型服务暂时不可用（500）")),
            status = MessageStatus.FAILED,
            modelId = "mimo-v2.5",
            inputTokens = null,
            outputTokens = null,
        )

        val failed = repository.observeMessages(conversationId).first().last()
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("模型服务暂时不可用（500）", failed.parts.filterIsInstance<MessageContentPart.Error>().single().message)
    }

    @Test
    fun firstMessageCreatesConversationAndPersistsMessage() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 42L },
            newId = { "id-${++nextId}" },
        )

        val conversationId = repository.createWithFirstUserMessage(
            "  一段很长的首条消息，用它验证标题会被压缩到合理长度并保存  ",
        )

        val conversations = repository.conversations.first()
        val messages = repository.observeMessages(conversationId).first()
        assertEquals(1, conversations.size)
        assertFalse(conversations.single().title.startsWith(" "))
        assertEquals(24, conversations.single().title.length)
        assertEquals("一段很长的首条消息，用它验证标题会被压缩到合理长度并保存", messages.single().text)

        repository.appendMessage(conversationId, MessageRole.AI, "已保存")
        assertEquals(conversationId, repository.searchConversations("很长的首条").first().single().id)
        assertEquals(conversationId, repository.searchConversations("已保存").first().single().id)
        assertTrue(repository.searchConversations("不存在的内容").first().isEmpty())
        val originalAiMessage = repository.observeMessages(conversationId).first().last()
        assertEquals(MessageRole.AI, originalAiMessage.role)

        repository.setMessageFavorite(conversationId, originalAiMessage.id, true)
        assertTrue(repository.observeMessages(conversationId).first().last().favorite)
        assertEquals("已保存", repository.favoriteMessages.first().single().text)
        assertEquals(conversationId, repository.favoriteMessages.first().single().conversationId)
        repository.setMessageTranslation(conversationId, originalAiMessage.id, "Persisted translation")
        assertEquals("Persisted translation", repository.observeMessages(conversationId).first().last().translation)

        repository.replaceMessage(
            conversationId = conversationId,
            messageId = originalAiMessage.id,
            text = "重新生成的回复",
            status = MessageStatus.STOPPED,
        )
        val replacedAiMessage = repository.observeMessages(conversationId).first().last()
        assertEquals(originalAiMessage.id, replacedAiMessage.id)
        assertEquals(originalAiMessage.nodeId, replacedAiMessage.nodeId)
        assertEquals("重新生成的回复", replacedAiMessage.text)
        assertEquals(MessageStatus.STOPPED, replacedAiMessage.status)
        repository.setMessageFavorite(conversationId, originalAiMessage.id, false)
        assertTrue(repository.favoriteMessages.first().isEmpty())

        repository.appendMessage(conversationId, MessageRole.AI, "部分回复", MessageStatus.STOPPED)
        assertEquals(MessageStatus.STOPPED, repository.observeMessages(conversationId).first().last().status)

        val branchNode = repository.observeMessages(conversationId).first().last().nodeId
        val originalVariant = repository.observeMessages(conversationId).first().last().id
        val alternative = repository.appendMessageVariant(
            conversationId,
            branchNode,
            MessageRole.AI,
            "另一种回答",
        )
        val selectedAlternative = repository.observeMessages(conversationId).first().last()
        assertEquals("另一种回答", selectedAlternative.text)
        assertEquals(listOf(originalVariant, alternative), selectedAlternative.variantIds)
        assertEquals(1, selectedAlternative.selectedVariantIndex)

        repository.selectMessageVariant(conversationId, branchNode, originalVariant)
        val selectedOriginal = repository.observeMessages(conversationId).first().last()
        assertEquals("部分回复", selectedOriginal.text)
        assertEquals(0, selectedOriginal.selectedVariantIndex)
    }

    @Test
    fun conversationCanBeCreatedInMovedBetweenAndRemovedFromSpaces() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 42L },
            newId = { "id-${++nextId}" },
        )
        val trainingSpace = repository.createSpace("  训练   计划  ")
        val readingSpace = repository.createSpace("阅读")

        val conversationId = repository.createWithFirstUserMessage("今天练什么？", trainingSpace)
        assertEquals("训练 计划", repository.spaces.first().first { it.id == trainingSpace }.name)
        assertEquals(trainingSpace, repository.conversations.first().single().spaceId)

        repository.renameSpace(trainingSpace, "  健身   计划 ")
        assertEquals("健身 计划", repository.spaces.first().first { it.id == trainingSpace }.name)
        repository.setSpacePinned(trainingSpace, true)
        assertTrue(repository.spaces.first().first { it.id == trainingSpace }.pinned)

        repository.setConversationSpace(conversationId, readingSpace)
        assertEquals(readingSpace, repository.conversations.first().single().spaceId)

        repository.setConversationSpace(conversationId, null)
        assertEquals(null, repository.conversations.first().single().spaceId)

        repository.setConversationSpace(conversationId, readingSpace)
        repository.deleteSpace(readingSpace)
        assertEquals(null, repository.conversations.first().single().spaceId)
        assertFalse(repository.spaces.first().any { it.id == readingSpace })

        repository.renameConversation(conversationId, "  新的   标题  ")
        assertEquals("新的 标题", repository.conversations.first().single().title)

        repository.setConversationPinned(conversationId, true)
        assertTrue(repository.conversations.first().single().pinned)

        repository.trashConversation(conversationId)
        assertTrue(repository.conversations.first().isEmpty())

        repository.restoreConversation(conversationId)
        assertEquals(conversationId, repository.conversations.first().single().id)

        assertEquals(1, repository.trashAllConversations())
        assertTrue(repository.conversations.first().isEmpty())
        assertEquals(conversationId, repository.trashedConversations.first().single().id)
        assertEquals(1, repository.restoreAllConversations())
        assertEquals(conversationId, repository.conversations.first().single().id)

        repository.trashConversation(conversationId)
        repository.permanentlyDeleteConversation(conversationId)
        assertTrue(repository.trashedConversations.first().isEmpty())
    }

    @Test
    fun selectingAlternativeVariantSwitchesVisibleContinuation() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 100L },
            newId = { "branch-${++nextId}" },
        )
        val conversationId = repository.createWithFirstUserMessage("问题")
        repository.appendMessage(conversationId, MessageRole.AI, "原回答")
        val originalAi = repository.observeMessages(conversationId).first().last()
        repository.appendMessage(conversationId, MessageRole.USER, "沿用原回答追问")
        assertEquals(3, repository.observeMessages(conversationId).first().size)

        val alternative = repository.appendMessageVariant(
            conversationId,
            originalAi.nodeId,
            MessageRole.AI,
            "新回答",
        )
        val alternativePath = repository.observeMessages(conversationId).first()
        assertEquals(2, alternativePath.size)
        assertEquals("新回答", alternativePath.last().text)

        repository.appendMessage(conversationId, MessageRole.USER, "沿用新回答追问")
        assertEquals("沿用新回答追问", repository.observeMessages(conversationId).first().last().text)

        repository.selectMessageVariant(conversationId, originalAi.nodeId, originalAi.id)
        assertEquals("沿用原回答追问", repository.observeMessages(conversationId).first().last().text)

        repository.selectMessageVariant(conversationId, originalAi.nodeId, alternative)
        assertEquals("沿用新回答追问", repository.observeMessages(conversationId).first().last().text)
    }

    @Test
    fun editDeleteAndForkPreserveIndependentBranches() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 200L },
            newId = { "operation-${++nextId}" },
        )
        val conversationId = repository.createWithFirstUserMessage("原问题")
        val originalUser = repository.observeMessages(conversationId).first().single()
        repository.appendMessage(conversationId, MessageRole.AI, "原回答")
        val originalAi = repository.observeMessages(conversationId).first().last()
        repository.appendMessage(conversationId, MessageRole.USER, "旧分支追问")

        val editedUserId = repository.editMessage(conversationId, originalUser.nodeId, "修改后的问题")
        assertEquals(listOf("修改后的问题"), repository.observeMessages(conversationId).first().map { it.text })
        repository.appendMessage(conversationId, MessageRole.AI, "新分支回答")
        val newAi = repository.observeMessages(conversationId).first().last()

        val forkId = repository.forkConversation(conversationId, newAi.nodeId)
        assertEquals(
            listOf("修改后的问题", "新分支回答"),
            repository.observeMessages(forkId).first().map { it.text },
        )

        repository.selectMessageVariant(conversationId, originalUser.nodeId, originalUser.id)
        assertEquals("旧分支追问", repository.observeMessages(conversationId).first().last().text)
        repository.deleteMessageBranch(conversationId, originalAi.nodeId)
        assertEquals(listOf("原问题"), repository.observeMessages(conversationId).first().map { it.text })

        repository.selectMessageVariant(conversationId, originalUser.nodeId, editedUserId)
        assertEquals("新分支回答", repository.observeMessages(conversationId).first().last().text)
    }

    @Test
    fun memoriesAreScopedAndBecomeGlobalWhenProjectIsDeleted() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 300L },
            newId = { "memory-${++nextId}" },
        )
        val training = repository.createSpace("训练")
        val reading = repository.createSpace("阅读")
        val conversationId = repository.createWithFirstUserMessage("记录偏好", training)

        repository.saveMemory(null, "全局偏好：使用中文", conversationId)
        repository.saveMemory(training, "训练偏好：三分化", conversationId)
        repository.saveMemory(reading, "阅读偏好：科幻", null)

        assertEquals(2, repository.searchMemories(training, "偏好").size)
        assertEquals(2, repository.searchMemories(reading, "偏好").size)
        assertEquals(1, repository.searchMemories(null, "偏好").size)
        assertTrue(repository.searchMemories(reading, "三分化").isEmpty())

        repository.deleteSpace(training)

        val detached = repository.searchMemories(null, "三分化").single()
        assertEquals(null, detached.spaceId)
        repository.deleteMemory(detached.id)
        assertTrue(repository.searchMemories(null, "三分化").isEmpty())
    }

    @Test
    fun generatedMessageMetadataPersistsAcrossVariants() = runBlocking {
        var nextId = 0
        val repository = RoomConversationRepository(
            database = database,
            now = { 400L },
            newId = { "usage-${++nextId}" },
        )
        val conversationId = repository.createWithFirstUserMessage("问题")
        val firstParts = listOf(MessageContentPart.Text(MessagePartId("first"), "第一版"))
        repository.appendGeneratedMessage(
            conversationId,
            firstParts,
            MessageStatus.COMPLETE,
            "model-a",
            120,
            30,
            80,
            2_000,
        )
        val first = repository.observeMessages(conversationId).first().last()
        assertEquals("model-a", first.modelId)
        assertEquals(120L, first.inputTokens)
        assertEquals(30L, first.outputTokens)
        assertEquals(80L, first.cachedInputTokens)
        assertEquals(2_000L, first.generationDurationMs)

        repository.appendGeneratedMessageVariant(
            conversationId,
            first.nodeId,
            listOf(MessageContentPart.Text(MessagePartId("second"), "第二版")),
            MessageStatus.COMPLETE,
            "model-b",
            140,
            35,
            90,
            1_000,
        )
        val second = repository.observeMessages(conversationId).first().last()
        assertEquals("model-b", second.modelId)
        assertEquals(140L, second.inputTokens)
        assertEquals(35L, second.outputTokens)
        assertEquals(90L, second.cachedInputTokens)
        assertEquals(1_000L, second.generationDurationMs)
    }
}
