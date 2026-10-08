package com.dwl.mutcube.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.MessageId
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessageNode
import com.dwl.mutcube.core.model.MessageNodeId
import com.dwl.mutcube.core.model.MessagePartId
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.MessageVariant
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.MemoryEntry
import com.dwl.mutcube.core.model.MemoryId
import com.dwl.mutcube.core.model.ConversationCheckpoint

@Entity(tableName = "spaces")
internal data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelProfileId: String?,
    val pinned: Boolean,
    val modelIdOverride: String? = null,
    val systemPromptOverride: String? = null,
    val maxContextMessagesOverride: Int? = null,
    val imageInputEnabledOverride: Boolean? = null,
    val temperatureOverride: Double? = null,
    val topPOverride: Double? = null,
    val maxOutputTokensOverride: Int? = null,
)

@Entity(tableName = "memory_entries", indices = [Index("spaceId"), Index("updatedAt")])
internal data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val spaceId: String?,
    val content: String,
    val sourceConversationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "conversation_checkpoints",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class ConversationCheckpointEntity(
    @PrimaryKey val conversationId: String,
    val throughMessageId: String,
    val sourceFingerprint: String,
    val summary: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "conversations",
    indices = [Index("spaceId"), Index("updatedAt")],
)
internal data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val spaceId: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val systemPrompt: String? = null,
)

@Entity(
    tableName = "message_nodes",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversationId", "sortOrder"]), Index("parentVariantId")],
)
internal data class MessageNodeEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sortOrder: Long,
    val parentVariantId: String?,
    val selectedVariantId: String,
)

@Entity(
    tableName = "message_variants",
    foreignKeys = [
        ForeignKey(
            entity = MessageNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["nodeId", "createdAt"])],
)
internal data class MessageVariantEntity(
    @PrimaryKey val id: String,
    val nodeId: String,
    val role: String,
    val createdAt: Long,
    val status: String,
    val modelId: String?,
    val inputTokens: Long?,
    val cachedInputTokens: Long?,
    val outputTokens: Long?,
    val generationDurationMs: Long?,
    val translation: String?,
    val favorite: Boolean,
)

internal data class FavoriteMessageEntity(
    val messageId: String,
    val conversationId: String,
    val conversationTitle: String,
    val role: String,
    val text: String,
    val createdAt: Long,
)

@Entity(
    tableName = "message_parts",
    foreignKeys = [
        ForeignKey(
            entity = MessageVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["variantId", "partOrder"], unique = true)],
)
internal data class MessagePartEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val partOrder: Int,
    val kind: String,
    val text: String?,
    val uri: String?,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?,
    val attachmentKind: String?,
    val callId: String?,
    val toolName: String?,
    val argumentsJson: String?,
    val toolStatus: String?,
    val isError: Boolean?,
)

internal data class MessageVariantWithParts(
    @Embedded val variant: MessageVariantEntity,
    @Relation(parentColumn = "id", entityColumn = "variantId")
    val parts: List<MessagePartEntity>,
)

internal data class MessageNodeWithVariants(
    @Embedded val node: MessageNodeEntity,
    @Relation(
        entity = MessageVariantEntity::class,
        parentColumn = "id",
        entityColumn = "nodeId",
    )
    val variants: List<MessageVariantWithParts>,
)

internal fun SpaceEntity.toDomain() = Space(
    id = SpaceId(id),
    name = name,
    modelProfileId = modelProfileId,
    pinned = pinned,
    modelIdOverride = modelIdOverride,
    systemPromptOverride = systemPromptOverride,
    maxContextMessagesOverride = maxContextMessagesOverride,
    imageInputEnabledOverride = imageInputEnabledOverride,
    temperatureOverride = temperatureOverride,
    topPOverride = topPOverride,
    maxOutputTokensOverride = maxOutputTokensOverride,
)

internal fun ConversationEntity.toDomain() = Conversation(
    id = ConversationId(id),
    title = title,
    spaceId = spaceId?.let(::SpaceId),
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    systemPrompt = systemPrompt,
)

internal fun MemoryEntryEntity.toDomain() = MemoryEntry(
    id = MemoryId(id),
    spaceId = spaceId?.let(::SpaceId),
    content = content,
    sourceConversationId = sourceConversationId?.let(::ConversationId),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ConversationCheckpointEntity.toDomain() = ConversationCheckpoint(
    conversationId = ConversationId(conversationId),
    throughMessageId = MessageId(throughMessageId),
    sourceFingerprint = sourceFingerprint,
    summary = summary,
    updatedAt = updatedAt,
)

internal fun MessageNodeWithVariants.toDomain(): MessageNode {
    val domainVariants = variants
        .sortedWith(compareBy({ it.variant.createdAt }, { it.variant.id }))
        .map(MessageVariantWithParts::toDomain)
    return MessageNode(
        id = MessageNodeId(node.id),
        conversationId = ConversationId(node.conversationId),
        sortOrder = node.sortOrder,
        parentVariantId = node.parentVariantId?.let(::MessageId),
        variants = domainVariants,
        selectedVariantId = domainVariants.firstOrNull { it.id.value == node.selectedVariantId }?.id
            ?: requireNotNull(domainVariants.firstOrNull()) { "Message node has no variants" }.id,
    )
}

internal fun MessageVariantWithParts.toDomain() = MessageVariant(
    id = MessageId(variant.id),
    role = MessageRole.valueOf(variant.role),
    parts = parts.sortedBy(MessagePartEntity::partOrder).map(MessagePartEntity::toDomain),
    createdAt = variant.createdAt,
    status = MessageStatus.valueOf(variant.status),
    modelId = variant.modelId,
    inputTokens = variant.inputTokens,
    cachedInputTokens = variant.cachedInputTokens,
    outputTokens = variant.outputTokens,
    generationDurationMs = variant.generationDurationMs,
    translation = variant.translation,
    favorite = variant.favorite,
)

private fun MessagePartEntity.toDomain(): MessageContentPart = when (kind) {
    "TEXT" -> MessageContentPart.Text(MessagePartId(id), requireNotNull(text))
    "REASONING" -> MessageContentPart.Reasoning(MessagePartId(id), requireNotNull(text))
    "ATTACHMENT" -> MessageContentPart.Attachment(
        id = MessagePartId(id),
        kind = AttachmentKind.valueOf(requireNotNull(attachmentKind)),
        uri = requireNotNull(uri),
        mimeType = requireNotNull(mimeType),
        displayName = requireNotNull(displayName),
        sizeBytes = requireNotNull(sizeBytes),
    )
    "TOOL_CALL" -> MessageContentPart.ToolCall(
        id = MessagePartId(id),
        callId = requireNotNull(callId),
        toolName = requireNotNull(toolName),
        argumentsJson = requireNotNull(argumentsJson),
        status = com.dwl.mutcube.core.model.ToolCallStatus.valueOf(requireNotNull(toolStatus)),
    )
    "TOOL_RESULT" -> MessageContentPart.ToolResult(
        id = MessagePartId(id),
        callId = requireNotNull(callId),
        output = requireNotNull(text),
        isError = requireNotNull(isError),
    )
    "ERROR" -> MessageContentPart.Error(MessagePartId(id), requireNotNull(text))
    else -> error("Unsupported message part kind: $kind")
}

internal fun MessageNode.toChatMessage(): ChatMessage {
    val selected = selectedVariant
    val text = selected.parts.filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text }
    return ChatMessage(
        id = selected.id,
        conversationId = conversationId,
        role = selected.role,
        text = text,
        createdAt = selected.createdAt,
        status = selected.status,
        nodeId = id,
        parts = selected.parts,
        variantIds = variants.map { it.id },
        selectedVariantIndex = variants.indexOfFirst { it.id == selectedVariantId },
        favorite = selected.favorite,
        translation = selected.translation,
        modelId = selected.modelId,
        inputTokens = selected.inputTokens,
        cachedInputTokens = selected.cachedInputTokens,
        outputTokens = selected.outputTokens,
        generationDurationMs = selected.generationDurationMs,
    )
}

internal fun FavoriteMessageEntity.toDomain() = com.dwl.mutcube.core.model.FavoriteMessage(
    messageId = MessageId(messageId),
    conversationId = ConversationId(conversationId),
    conversationTitle = conversationTitle,
    role = MessageRole.valueOf(role),
    text = text,
    createdAt = createdAt,
)
