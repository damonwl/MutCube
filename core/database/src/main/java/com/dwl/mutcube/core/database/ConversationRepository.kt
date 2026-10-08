package com.dwl.mutcube.core.database

import androidx.room.withTransaction
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.FavoriteMessage
import com.dwl.mutcube.core.model.MessageId
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessageNodeId
import com.dwl.mutcube.core.model.MessageNode
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.SpaceModelOverrides
import com.dwl.mutcube.core.model.MemoryEntry
import com.dwl.mutcube.core.model.MemoryId
import com.dwl.mutcube.core.model.ConversationCheckpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface ConversationRepository {
    val spaces: Flow<List<Space>>
    val conversations: Flow<List<Conversation>>
    val trashedConversations: Flow<List<Conversation>> get() = kotlinx.coroutines.flow.flowOf(emptyList())
    val favoriteMessages: Flow<List<FavoriteMessage>>
    val memories: Flow<List<MemoryEntry>> get() = kotlinx.coroutines.flow.flowOf(emptyList())

    fun searchConversations(query: String): Flow<List<Conversation>>

    fun observeMessages(conversationId: ConversationId): Flow<List<ChatMessage>>

    suspend fun getConversation(conversationId: ConversationId): Conversation? = null

    suspend fun getAllAttachmentUris(): Set<String> = emptySet()

    suspend fun createSpace(name: String): SpaceId

    suspend fun renameSpace(spaceId: SpaceId, name: String)

    suspend fun setSpacePinned(spaceId: SpaceId, pinned: Boolean)

    suspend fun deleteSpace(spaceId: SpaceId)

    suspend fun getConversationSpace(conversationId: ConversationId): Space? = null

    suspend fun updateSpaceModelSettings(spaceId: SpaceId, overrides: SpaceModelOverrides) = Unit

    suspend fun setSpaceModelProfile(spaceId: SpaceId, profileId: String?) = Unit

    suspend fun createWithFirstUserMessage(text: String, spaceId: SpaceId? = null): ConversationId

    suspend fun createWithFirstUserMessage(
        parts: List<MessageContentPart>,
        spaceId: SpaceId? = null,
    ): ConversationId = error("Structured message creation is not implemented")

    suspend fun setConversationSpace(conversationId: ConversationId, spaceId: SpaceId?)

    suspend fun setConversationSystemPrompt(conversationId: ConversationId, systemPrompt: String?) = Unit

    suspend fun renameConversation(conversationId: ConversationId, title: String)

    suspend fun setConversationPinned(conversationId: ConversationId, pinned: Boolean)

    suspend fun trashConversation(conversationId: ConversationId)

    suspend fun restoreConversation(conversationId: ConversationId)

    suspend fun trashAllConversations(): Int

    suspend fun restoreAllConversations(): Int = 0

    suspend fun permanentlyDeleteConversation(conversationId: ConversationId) = Unit

    suspend fun emptyTrash(): Int = 0

    suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        text: String,
        status: MessageStatus = MessageStatus.COMPLETE,
    )

    suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus = MessageStatus.COMPLETE,
    ): Unit = error("Structured message persistence is not implemented")

    suspend fun appendGeneratedMessage(
        conversationId: ConversationId,
        parts: List<MessageContentPart>,
        status: MessageStatus,
        modelId: String,
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long? = null,
        generationDurationMs: Long? = null,
    ): Unit = error("Generated message metadata persistence is not implemented")

    suspend fun replaceMessage(
        conversationId: ConversationId,
        messageId: MessageId,
        text: String,
        status: MessageStatus = MessageStatus.COMPLETE,
    )

    suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        text: String,
        status: MessageStatus = MessageStatus.COMPLETE,
    ): MessageId

    suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus = MessageStatus.COMPLETE,
    ): MessageId = error("Structured message variant persistence is not implemented")

    suspend fun appendGeneratedMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        parts: List<MessageContentPart>,
        status: MessageStatus,
        modelId: String,
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long? = null,
        generationDurationMs: Long? = null,
    ): MessageId = error("Generated variant metadata persistence is not implemented")

    suspend fun selectMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        variantId: MessageId,
    )

    suspend fun editMessage(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        text: String,
    ): MessageId

    suspend fun deleteMessageBranch(conversationId: ConversationId, nodeId: MessageNodeId)

    suspend fun forkConversation(conversationId: ConversationId, throughNodeId: MessageNodeId): ConversationId

    suspend fun setMessageFavorite(conversationId: ConversationId, messageId: MessageId, favorite: Boolean)

    suspend fun setMessageTranslation(conversationId: ConversationId, messageId: MessageId, translation: String?)

    suspend fun searchMemories(spaceId: SpaceId?, query: String, limit: Int = 8): List<MemoryEntry> = emptyList()

    suspend fun saveMemory(spaceId: SpaceId?, content: String, sourceConversationId: ConversationId?): MemoryId =
        error("Memory storage is not available")

    suspend fun deleteMemory(id: MemoryId) = Unit

    suspend fun updateMemory(id: MemoryId, content: String) = Unit

    suspend fun getConversationCheckpoint(conversationId: ConversationId): ConversationCheckpoint? = null

    suspend fun saveConversationCheckpoint(checkpoint: ConversationCheckpoint) = Unit

    suspend fun deleteConversationCheckpoint(conversationId: ConversationId) = Unit
}

class RoomConversationRepository(
    private val database: MutCubeDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ConversationRepository {
    private val dao = database.dao()

    override val spaces: Flow<List<Space>> = dao.observeSpaces().map { rows -> rows.map(SpaceEntity::toDomain) }

    override val conversations: Flow<List<Conversation>> =
        dao.observeConversations().map { rows -> rows.map(ConversationEntity::toDomain) }

    override val trashedConversations: Flow<List<Conversation>> =
        dao.observeTrashedConversations().map { rows -> rows.map(ConversationEntity::toDomain) }

    override val favoriteMessages: Flow<List<FavoriteMessage>> =
        dao.observeFavoriteMessages().map { rows -> rows.map(FavoriteMessageEntity::toDomain) }

    override val memories: Flow<List<MemoryEntry>> =
        dao.observeMemories().map { rows -> rows.map(MemoryEntryEntity::toDomain) }

    override fun searchConversations(query: String): Flow<List<Conversation>> =
        dao.searchConversations(query.trim()).map { rows -> rows.map(ConversationEntity::toDomain) }

    override fun observeMessages(conversationId: ConversationId): Flow<List<ChatMessage>> =
        dao.observeMessageNodes(conversationId.value).map { rows ->
            rows.map(MessageNodeWithVariants::toDomain).activePath().map { it.toChatMessage() }
        }

    override suspend fun getConversation(conversationId: ConversationId): Conversation? =
        dao.getConversation(conversationId.value)?.toDomain()

    override suspend fun getAllAttachmentUris(): Set<String> = dao.getAllAttachmentUris().toSet()

    override suspend fun createSpace(name: String): SpaceId {
        val normalizedName = name.trim().replace(Regex("\\s+"), " ")
        require(normalizedName.isNotEmpty()) { "Space name must not be blank" }
        val spaceId = SpaceId(newId())
        dao.insertSpace(SpaceEntity(spaceId.value, normalizedName, modelProfileId = null, pinned = false))
        return spaceId
    }

    override suspend fun renameSpace(spaceId: SpaceId, name: String) {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "Space name must not be blank" }
        check(dao.renameSpace(spaceId.value, normalized.take(60)) == 1) { "Space does not exist" }
    }

    override suspend fun setSpacePinned(spaceId: SpaceId, pinned: Boolean) {
        check(dao.setSpacePinned(spaceId.value, pinned) == 1) { "Space does not exist" }
    }

    override suspend fun deleteSpace(spaceId: SpaceId) {
        database.withTransaction {
            check(dao.countSpace(spaceId.value) == 1) { "Space does not exist" }
            dao.detachConversationsFromSpace(spaceId.value)
            dao.detachMemoriesFromSpace(spaceId.value)
            check(dao.deleteSpace(spaceId.value) == 1)
        }
    }

    override suspend fun getConversationSpace(conversationId: ConversationId): Space? =
        dao.getConversationSpace(conversationId.value)?.toDomain()

    override suspend fun updateSpaceModelSettings(spaceId: SpaceId, overrides: SpaceModelOverrides) {
        val modelId = overrides.modelId?.trim()?.takeIf(String::isNotEmpty)
        val systemPrompt = overrides.systemPrompt?.trim()?.takeIf(String::isNotEmpty)
        val maxContext = overrides.maxContextMessages?.coerceIn(2, 200)
        val temperature = overrides.temperature?.coerceIn(0.0, 2.0)
        val topP = overrides.topP?.coerceIn(0.0, 1.0)
        val maxOutputTokens = overrides.maxOutputTokens?.coerceIn(1, 131_072)
        check(
            dao.updateSpaceModelSettings(
                spaceId.value,
                modelId,
                systemPrompt,
                maxContext,
                overrides.imageInputEnabled,
                temperature,
                topP,
                maxOutputTokens,
            ) == 1,
        ) { "Space does not exist" }
    }

    override suspend fun setSpaceModelProfile(spaceId: SpaceId, profileId: String?) {
        check(dao.setSpaceModelProfile(spaceId.value, profileId?.trim()?.takeIf(String::isNotEmpty)) == 1) {
            "Space does not exist"
        }
    }

    override suspend fun createWithFirstUserMessage(text: String, spaceId: SpaceId?): ConversationId {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Message must not be blank" }
        val timestamp = now()
        val conversationId = ConversationId(newId())
        database.withTransaction {
            if (spaceId != null) require(dao.countSpace(spaceId.value) == 1) { "Space does not exist" }
            dao.insertConversation(
                ConversationEntity(
                    id = conversationId.value,
                    title = normalizedText.toConversationTitle(),
                    spaceId = spaceId?.value,
                    pinned = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                ),
            )
            insertTextMessage(
                conversationId = conversationId,
                role = MessageRole.USER,
                text = normalizedText,
                status = MessageStatus.COMPLETE,
                timestamp = timestamp,
            )
        }
        return conversationId
    }

    override suspend fun createWithFirstUserMessage(
        parts: List<MessageContentPart>,
        spaceId: SpaceId?,
    ): ConversationId {
        require(parts.isNotEmpty()) { "Message must not be empty" }
        val title = parts.messageText().ifBlank {
            parts.filterIsInstance<MessageContentPart.Attachment>().firstOrNull()?.displayName ?: "附件"
        }
        val timestamp = now()
        val conversationId = ConversationId(newId())
        database.withTransaction {
            if (spaceId != null) require(dao.countSpace(spaceId.value) == 1) { "Space does not exist" }
            dao.insertConversation(
                ConversationEntity(
                    id = conversationId.value,
                    title = title.toConversationTitle(),
                    spaceId = spaceId?.value,
                    pinned = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                ),
            )
            insertMessage(conversationId, MessageRole.USER, parts, MessageStatus.COMPLETE, timestamp)
        }
        return conversationId
    }

    override suspend fun setConversationSpace(conversationId: ConversationId, spaceId: SpaceId?) {
        database.withTransaction {
            if (spaceId != null) require(dao.countSpace(spaceId.value) == 1) { "Space does not exist" }
            check(dao.updateConversationSpace(conversationId.value, spaceId?.value) == 1) {
                "Conversation does not exist"
            }
        }
    }

    override suspend fun setConversationSystemPrompt(conversationId: ConversationId, systemPrompt: String?) {
        val normalized = systemPrompt?.trim()?.takeIf(String::isNotEmpty)?.take(8_000)
        check(dao.updateConversationSystemPrompt(conversationId.value, normalized, now()) == 1) {
            "Conversation does not exist"
        }
    }

    override suspend fun renameConversation(conversationId: ConversationId, title: String) {
        val normalized = title.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "Conversation title must not be blank" }
        check(dao.renameConversation(conversationId.value, normalized.take(80), now()) == 1) {
            "Conversation does not exist"
        }
    }

    override suspend fun setConversationPinned(conversationId: ConversationId, pinned: Boolean) {
        check(dao.setConversationPinned(conversationId.value, pinned, now()) == 1) {
            "Conversation does not exist"
        }
    }

    override suspend fun trashConversation(conversationId: ConversationId) {
        check(dao.trashConversation(conversationId.value, now()) == 1) { "Conversation does not exist" }
    }

    override suspend fun restoreConversation(conversationId: ConversationId) {
        check(dao.restoreConversation(conversationId.value, now()) == 1) { "Conversation does not exist" }
    }

    override suspend fun trashAllConversations(): Int = dao.trashAllConversations(now())

    override suspend fun restoreAllConversations(): Int = dao.restoreAllConversations(now())

    override suspend fun permanentlyDeleteConversation(conversationId: ConversationId) {
        check(dao.permanentlyDeleteConversation(conversationId.value) == 1) { "Trashed conversation does not exist" }
    }

    override suspend fun emptyTrash(): Int = dao.emptyTrash()

    override suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        text: String,
        status: MessageStatus,
    ) {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Message must not be blank" }
        val timestamp = now()
        database.withTransaction {
            insertTextMessage(conversationId, role, normalizedText, status, timestamp)
            dao.touchConversation(conversationId.value, timestamp)
        }
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
        require(parts.isNotEmpty()) { "Message must not be empty" }
        val timestamp = now()
        database.withTransaction {
            insertMessage(
                conversationId,
                MessageRole.AI,
                parts,
                status,
                timestamp,
                modelId,
                inputTokens,
                outputTokens,
                cachedInputTokens,
                generationDurationMs,
            )
            dao.touchConversation(conversationId.value, timestamp)
        }
    }

    override suspend fun appendMessage(
        conversationId: ConversationId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus,
    ) {
        require(parts.isNotEmpty()) { "Message must not be empty" }
        val timestamp = now()
        database.withTransaction {
            insertMessage(conversationId, role, parts, status, timestamp)
            dao.touchConversation(conversationId.value, timestamp)
        }
    }

    override suspend fun replaceMessage(
        conversationId: ConversationId,
        messageId: MessageId,
        text: String,
        status: MessageStatus,
    ) {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Message must not be blank" }
        val timestamp = now()
        database.withTransaction {
            val updatedRows = dao.updateMessage(
                conversationId = conversationId.value,
                messageId = messageId.value,
                status = status.name,
            )
            check(updatedRows == 1) { "Message does not belong to the conversation" }
            dao.deleteMessageParts(messageId.value)
            dao.insertMessagePart(
                MessagePartEntity(
                    id = newId(),
                    variantId = messageId.value,
                    partOrder = 0,
                    kind = "TEXT",
                    text = normalizedText,
                    uri = null,
                    mimeType = null,
                    displayName = null,
                    sizeBytes = null,
                    attachmentKind = null,
                    callId = null,
                    toolName = null,
                    argumentsJson = null,
                    toolStatus = null,
                    isError = null,
                ),
            )
            dao.touchConversation(conversationId.value, timestamp)
        }
    }

    override suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        text: String,
        status: MessageStatus,
    ): MessageId {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Message must not be blank" }
        return appendMessageVariant(
            conversationId,
            nodeId,
            role,
            listOf(MessageContentPart.Text(com.dwl.mutcube.core.model.MessagePartId(newId()), normalizedText)),
            status,
        )
    }

    override suspend fun appendMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus,
    ): MessageId {
        require(parts.isNotEmpty()) { "Message must not be empty" }
        val timestamp = now()
        val variantId = MessageId(newId())
        database.withTransaction {
            check(dao.countMessageNode(conversationId.value, nodeId.value) == 1) {
                "Message node does not belong to the conversation"
            }
            dao.insertMessageVariant(
                MessageVariantEntity(
                    id = variantId.value,
                    nodeId = nodeId.value,
                    role = role.name,
                    createdAt = timestamp,
                    status = status.name,
                    modelId = null,
                    inputTokens = null,
                    cachedInputTokens = null,
                    outputTokens = null,
                    generationDurationMs = null,
                    translation = null,
                    favorite = false,
                ),
            )
            parts.forEachIndexed { index, part ->
                dao.insertMessagePart(part.toEntity(newId(), variantId.value, index))
            }
            check(dao.selectMessageVariant(conversationId.value, nodeId.value, variantId.value) == 1)
            dao.touchConversation(conversationId.value, timestamp)
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
        require(parts.isNotEmpty()) { "Message must not be empty" }
        val timestamp = now()
        val variantId = MessageId(newId())
        database.withTransaction {
            check(dao.countMessageNode(conversationId.value, nodeId.value) == 1) {
                "Message node does not belong to the conversation"
            }
            dao.insertMessageVariant(
                MessageVariantEntity(
                    id = variantId.value,
                    nodeId = nodeId.value,
                    role = MessageRole.AI.name,
                    createdAt = timestamp,
                    status = status.name,
                    modelId = modelId,
                    inputTokens = inputTokens,
                    cachedInputTokens = cachedInputTokens,
                    outputTokens = outputTokens,
                    generationDurationMs = generationDurationMs,
                    translation = null,
                    favorite = false,
                ),
            )
            parts.forEachIndexed { index, part ->
                dao.insertMessagePart(part.toEntity(newId(), variantId.value, index))
            }
            check(dao.selectMessageVariant(conversationId.value, nodeId.value, variantId.value) == 1)
            dao.touchConversation(conversationId.value, timestamp)
        }
        return variantId
    }

    override suspend fun selectMessageVariant(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        variantId: MessageId,
    ) {
        database.withTransaction {
            check(dao.selectMessageVariant(conversationId.value, nodeId.value, variantId.value) == 1) {
                "Variant does not belong to the message node"
            }
            dao.touchConversation(conversationId.value, now())
        }
    }

    override suspend fun editMessage(
        conversationId: ConversationId,
        nodeId: MessageNodeId,
        text: String,
    ): MessageId {
        val node = dao.getMessageNodes(conversationId.value)
            .map(MessageNodeWithVariants::toDomain)
            .firstOrNull { it.id == nodeId }
            ?: error("Message node does not belong to the conversation")
        val updatedParts = buildList {
            add(MessageContentPart.Text(com.dwl.mutcube.core.model.MessagePartId(newId()), text.trim()))
            addAll(node.selectedVariant.parts.filterNot { it is MessageContentPart.Text })
        }
        return appendMessageVariant(conversationId, nodeId, node.selectedVariant.role, updatedParts)
    }

    override suspend fun deleteMessageBranch(conversationId: ConversationId, nodeId: MessageNodeId) {
        database.withTransaction {
            val nodes = dao.getMessageNodes(conversationId.value).map(MessageNodeWithVariants::toDomain)
            check(nodes.any { it.id == nodeId }) { "Message node does not belong to the conversation" }
            val removedNodeIds = mutableSetOf(nodeId)
            val removedVariantIds = mutableSetOf<MessageId>()
            var changed: Boolean
            do {
                changed = false
                nodes.filter { it.id in removedNodeIds }.forEach { removedVariantIds += it.variants.map { variant -> variant.id } }
                nodes.filter { it.parentVariantId in removedVariantIds }.forEach { node ->
                    if (removedNodeIds.add(node.id)) changed = true
                }
            } while (changed)
            check(dao.deleteMessageNodes(conversationId.value, removedNodeIds.map { it.value }) == removedNodeIds.size)
            dao.touchConversation(conversationId.value, now())
        }
    }

    override suspend fun forkConversation(
        conversationId: ConversationId,
        throughNodeId: MessageNodeId,
    ): ConversationId {
        val forkId = ConversationId(newId())
        database.withTransaction {
            val source = requireNotNull(dao.getConversation(conversationId.value)) { "Conversation does not exist" }
            val rows = dao.getMessageNodes(conversationId.value)
            val path = rows.map(MessageNodeWithVariants::toDomain).activePath()
            val endIndex = path.indexOfFirst { it.id == throughNodeId }
            require(endIndex >= 0) { "Message node is not on the active path" }
            val timestamp = now()
            dao.insertConversation(
                source.copy(
                    id = forkId.value,
                    title = "${source.title} · 分支".take(80),
                    pinned = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                ),
            )
            var parentVariantId: String? = null
            path.take(endIndex + 1).forEachIndexed { index, sourceNode ->
                val sourceRow = rows.first { it.node.id == sourceNode.id.value }
                val sourceVariant = sourceRow.variants.first { it.variant.id == sourceNode.selectedVariantId.value }
                val newNodeId = newId()
                val newVariantId = newId()
                dao.insertMessageNode(
                    sourceRow.node.copy(
                        id = newNodeId,
                        conversationId = forkId.value,
                        sortOrder = timestamp + index,
                        parentVariantId = parentVariantId,
                        selectedVariantId = newVariantId,
                    ),
                )
                dao.insertMessageVariant(
                    sourceVariant.variant.copy(
                        id = newVariantId,
                        nodeId = newNodeId,
                        createdAt = timestamp + index,
                        favorite = false,
                    ),
                )
                sourceVariant.parts.sortedBy(MessagePartEntity::partOrder).forEach { part ->
                    dao.insertMessagePart(part.copy(id = newId(), variantId = newVariantId))
                }
                parentVariantId = newVariantId
            }
        }
        return forkId
    }

    override suspend fun setMessageFavorite(
        conversationId: ConversationId,
        messageId: MessageId,
        favorite: Boolean,
    ) {
        check(dao.setMessageFavorite(conversationId.value, messageId.value, favorite) == 1) {
            "Message does not belong to the conversation"
        }
    }

    override suspend fun setMessageTranslation(
        conversationId: ConversationId,
        messageId: MessageId,
        translation: String?,
    ) {
        val normalized = translation?.trim()?.takeIf(String::isNotEmpty)
        check(dao.setMessageTranslation(conversationId.value, messageId.value, normalized) == 1) {
            "Message does not belong to the conversation"
        }
    }

    override suspend fun searchMemories(spaceId: SpaceId?, query: String, limit: Int): List<MemoryEntry> =
        dao.searchMemories(spaceId?.value, query.trim().take(200), limit.coerceIn(1, 20)).map(MemoryEntryEntity::toDomain)

    override suspend fun saveMemory(
        spaceId: SpaceId?,
        content: String,
        sourceConversationId: ConversationId?,
    ): MemoryId {
        val normalized = content.trim().replace(Regex("\\s+"), " ").take(2_000)
        require(normalized.isNotEmpty()) { "Memory must not be blank" }
        return database.withTransaction {
            dao.findExactMemory(spaceId?.value, normalized)?.let { return@withTransaction MemoryId(it.id) }
            val id = MemoryId(newId())
            val timestamp = now()
            dao.insertMemory(
                MemoryEntryEntity(id.value, spaceId?.value, normalized, sourceConversationId?.value, timestamp, timestamp),
            )
            id
        }
    }

    override suspend fun deleteMemory(id: MemoryId) {
        check(dao.deleteMemory(id.value) == 1) { "Memory does not exist" }
    }

    override suspend fun updateMemory(id: MemoryId, content: String) {
        val normalized = content.trim().replace(Regex("\\s+"), " ").take(2_000)
        require(normalized.isNotEmpty()) { "Memory must not be blank" }
        check(dao.updateMemory(id.value, normalized, now()) == 1) { "Memory does not exist" }
    }

    override suspend fun getConversationCheckpoint(conversationId: ConversationId): ConversationCheckpoint? =
        dao.getConversationCheckpoint(conversationId.value)?.toDomain()

    override suspend fun saveConversationCheckpoint(checkpoint: ConversationCheckpoint) {
        check(dao.getConversation(checkpoint.conversationId.value) != null) { "Conversation does not exist" }
        dao.upsertConversationCheckpoint(
            ConversationCheckpointEntity(
                checkpoint.conversationId.value,
                checkpoint.throughMessageId.value,
                checkpoint.sourceFingerprint,
                checkpoint.summary.trim().take(12_000),
                now(),
            ),
        )
    }

    override suspend fun deleteConversationCheckpoint(conversationId: ConversationId) {
        dao.deleteConversationCheckpoint(conversationId.value)
    }

    private suspend fun insertTextMessage(
        conversationId: ConversationId,
        role: MessageRole,
        text: String,
        status: MessageStatus,
        timestamp: Long,
    ) {
        insertMessage(
            conversationId,
            role,
            listOf(MessageContentPart.Text(com.dwl.mutcube.core.model.MessagePartId(newId()), text)),
            status,
            timestamp,
        )
    }

    private suspend fun insertMessage(
        conversationId: ConversationId,
        role: MessageRole,
        parts: List<MessageContentPart>,
        status: MessageStatus,
        timestamp: Long,
        modelId: String? = null,
        inputTokens: Long? = null,
        outputTokens: Long? = null,
        cachedInputTokens: Long? = null,
        generationDurationMs: Long? = null,
    ) {
        val nodeId = newId()
        val variantId = newId()
        val parentVariantId = dao.getMessageNodes(conversationId.value)
            .map(MessageNodeWithVariants::toDomain)
            .activePath()
            .lastOrNull()
            ?.selectedVariantId
        dao.insertMessageNode(
            MessageNodeEntity(
                id = nodeId,
                conversationId = conversationId.value,
                sortOrder = dao.nextMessageSortOrder(conversationId.value, timestamp),
                parentVariantId = parentVariantId?.value,
                selectedVariantId = variantId,
            ),
        )
        dao.insertMessageVariant(
            MessageVariantEntity(
                id = variantId,
                nodeId = nodeId,
                role = role.name,
                createdAt = timestamp,
                status = status.name,
                modelId = modelId,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens,
                generationDurationMs = generationDurationMs,
                translation = null,
                favorite = false,
            ),
        )
        parts.forEachIndexed { index, part ->
            dao.insertMessagePart(part.toEntity(newId(), variantId, index))
        }
    }

}

private fun List<MessageContentPart>.messageText(): String =
    filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text }.trim()

private fun MessageContentPart.toEntity(partId: String, variantId: String, order: Int): MessagePartEntity = when (this) {
    is MessageContentPart.Text -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "TEXT", text = text,
        uri = null, mimeType = null, displayName = null, sizeBytes = null, attachmentKind = null,
        callId = null, toolName = null, argumentsJson = null, toolStatus = null, isError = null,
    )
    is MessageContentPart.Attachment -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "ATTACHMENT", text = null,
        uri = uri, mimeType = mimeType, displayName = displayName, sizeBytes = sizeBytes,
        attachmentKind = kind.name, callId = null, toolName = null, argumentsJson = null,
        toolStatus = null, isError = null,
    )
    is MessageContentPart.Reasoning -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "REASONING", text = text,
        uri = null, mimeType = null, displayName = null, sizeBytes = null, attachmentKind = null,
        callId = null, toolName = null, argumentsJson = null, toolStatus = null, isError = null,
    )
    is MessageContentPart.ToolCall -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "TOOL_CALL", text = null,
        uri = null, mimeType = null, displayName = null, sizeBytes = null, attachmentKind = null,
        callId = callId, toolName = toolName, argumentsJson = argumentsJson, toolStatus = status.name,
        isError = null,
    )
    is MessageContentPart.ToolResult -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "TOOL_RESULT", text = output,
        uri = null, mimeType = null, displayName = null, sizeBytes = null, attachmentKind = null,
        callId = callId, toolName = null, argumentsJson = null, toolStatus = null, isError = isError,
    )
    is MessageContentPart.Error -> MessagePartEntity(
        id = partId, variantId = variantId, partOrder = order, kind = "ERROR", text = message,
        uri = null, mimeType = null, displayName = null, sizeBytes = null, attachmentKind = null,
        callId = null, toolName = null, argumentsJson = null, toolStatus = null, isError = null,
    )
}

private fun List<MessageNode>.activePath(): List<MessageNode> {
    val root = filter { it.parentVariantId == null }.minWithOrNull(compareBy({ it.sortOrder }, { it.id.value }))
        ?: return emptyList()
    val result = mutableListOf<MessageNode>()
    val visited = mutableSetOf<MessageNodeId>()
    var current: MessageNode? = root
    while (current != null && visited.add(current.id)) {
        result += current
        val parentVariantId = current.selectedVariantId
        current = asSequence()
            .filter { it.parentVariantId == parentVariantId }
            .minWithOrNull(compareBy({ it.sortOrder }, { it.id.value }))
    }
    return result
}

private fun String.toConversationTitle(): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return normalized.take(24)
}
