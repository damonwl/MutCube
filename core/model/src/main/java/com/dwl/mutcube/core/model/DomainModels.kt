package com.dwl.mutcube.core.model

@JvmInline
value class SpaceId(val value: String)

@JvmInline
value class ConversationId(val value: String)

@JvmInline
value class MessageId(val value: String)

@JvmInline
value class MessageNodeId(val value: String)

@JvmInline
value class MessagePartId(val value: String)

@JvmInline
value class MemoryId(val value: String)

data class MemoryEntry(
    val id: MemoryId,
    val spaceId: SpaceId?,
    val content: String,
    val sourceConversationId: ConversationId?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A resumable conversation summary; unlike MemoryEntry it is never shared across chats. */
data class ConversationCheckpoint(
    val conversationId: ConversationId,
    val throughMessageId: MessageId,
    val sourceFingerprint: String,
    val summary: String,
    val updatedAt: Long,
)

data class Space(
    val id: SpaceId,
    val name: String,
    val modelProfileId: String?,
    val pinned: Boolean = false,
    val modelIdOverride: String? = null,
    val systemPromptOverride: String? = null,
    val maxContextMessagesOverride: Int? = null,
    val imageInputEnabledOverride: Boolean? = null,
    val temperatureOverride: Double? = null,
    val topPOverride: Double? = null,
    val maxOutputTokensOverride: Int? = null,
)

data class SpaceModelOverrides(
    val modelId: String? = null,
    val systemPrompt: String? = null,
    val maxContextMessages: Int? = null,
    val imageInputEnabled: Boolean? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
)

val Space.modelOverrides: SpaceModelOverrides
    get() = SpaceModelOverrides(
        modelIdOverride,
        systemPromptOverride,
        maxContextMessagesOverride,
        imageInputEnabledOverride,
        temperatureOverride,
        topPOverride,
        maxOutputTokensOverride,
    )

data class Conversation(
    val id: ConversationId,
    val title: String,
    val spaceId: SpaceId? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val systemPrompt: String? = null,
)

enum class MessageRole {
    USER,
    AI,
    SYSTEM,
}

enum class MessageStatus {
    COMPLETE,
    STOPPED,
    FAILED,
}

enum class AttachmentKind {
    IMAGE,
    DOCUMENT,
    AUDIO,
    VIDEO,
}

enum class ToolCallStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DENIED,
}

sealed interface MessageContentPart {
    val id: MessagePartId

    data class Text(
        override val id: MessagePartId,
        val text: String,
    ) : MessageContentPart

    data class Attachment(
        override val id: MessagePartId,
        val kind: AttachmentKind,
        val uri: String,
        val mimeType: String,
        val displayName: String,
        val sizeBytes: Long,
    ) : MessageContentPart

    data class Reasoning(
        override val id: MessagePartId,
        val text: String,
    ) : MessageContentPart

    data class ToolCall(
        override val id: MessagePartId,
        val callId: String,
        val toolName: String,
        val argumentsJson: String,
        val status: ToolCallStatus,
    ) : MessageContentPart

    data class ToolResult(
        override val id: MessagePartId,
        val callId: String,
        val output: String,
        val isError: Boolean,
    ) : MessageContentPart

    /** A persisted generation failure that is shown by the client but never sent back to a model. */
    data class Error(
        override val id: MessagePartId,
        val message: String,
    ) : MessageContentPart
}

data class MessageVariant(
    val id: MessageId,
    val role: MessageRole,
    val parts: List<MessageContentPart>,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val modelId: String? = null,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val outputTokens: Long? = null,
    val generationDurationMs: Long? = null,
    val translation: String? = null,
    val favorite: Boolean = false,
)

data class MessageNode(
    val id: MessageNodeId,
    val conversationId: ConversationId,
    val sortOrder: Long,
    val parentVariantId: MessageId?,
    val variants: List<MessageVariant>,
    val selectedVariantId: MessageId,
) {
    val selectedVariant: MessageVariant
        get() = variants.first { it.id == selectedVariantId }
}

data class ChatMessage(
    val id: MessageId,
    val conversationId: ConversationId,
    val role: MessageRole,
    val text: String,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val nodeId: MessageNodeId = MessageNodeId(id.value),
    val parts: List<MessageContentPart> = listOf(
        MessageContentPart.Text(MessagePartId("${id.value}:text"), text),
    ),
    val variantIds: List<MessageId> = listOf(id),
    val selectedVariantIndex: Int = 0,
    val favorite: Boolean = false,
    val translation: String? = null,
    val modelId: String? = null,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val outputTokens: Long? = null,
    val generationDurationMs: Long? = null,
)

data class FavoriteMessage(
    val messageId: MessageId,
    val conversationId: ConversationId,
    val conversationTitle: String,
    val role: MessageRole,
    val text: String,
    val createdAt: Long,
)

enum class DataMutability {
    IMMUTABLE_HISTORY,
    USER_EDITABLE,
    AI_PROPOSABLE,
}
