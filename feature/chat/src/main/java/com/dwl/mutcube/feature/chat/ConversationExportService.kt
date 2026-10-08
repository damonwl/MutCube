package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.MessageContentPart
import kotlinx.coroutines.flow.first
import java.time.Instant

enum class ConversationExportFormat { MARKDOWN, JSON }

data class ConversationExport(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

class ConversationExportService(private val repository: ConversationRepository) {
    suspend fun export(id: ConversationId, format: ConversationExportFormat): ConversationExport {
        val conversation = requireNotNull(repository.getConversation(id)) { "Conversation does not exist" }
        val messages = repository.observeMessages(id).first()
        val baseName = conversation.title.safeFileName().ifBlank { "MutCube 对话" }
        return when (format) {
            ConversationExportFormat.MARKDOWN -> ConversationExport(
                "$baseName.md",
                "text/markdown",
                conversation.toMarkdown(messages),
            )
            ConversationExportFormat.JSON -> ConversationExport(
                "$baseName.json",
                "application/json",
                conversation.toJson(messages),
            )
        }
    }
}

private fun Conversation.toMarkdown(messages: List<ChatMessage>) = buildString {
    appendLine("# $title")
    appendLine()
    appendLine("- MutCube 会话 ID：`${id.value}`")
    appendLine("- 导出时间：${Instant.now()}")
    appendLine()
    messages.forEach { message ->
        appendLine("## ${message.role.name} · ${Instant.ofEpochMilli(message.createdAt)}")
        if (message.modelId != null || message.inputTokens != null || message.outputTokens != null) {
            appendLine(
                listOfNotNull(
                    message.modelId?.let { "模型：$it" },
                    message.inputTokens?.let { "输入 Token：$it" },
                    message.cachedInputTokens?.let { "缓存 Token：$it" },
                    message.outputTokens?.let { "输出 Token：$it" },
                    message.generationDurationMs?.let { "生成耗时：${it}ms" },
                ).joinToString(" · "),
            )
            appendLine()
        }
        if (message.text.isNotBlank()) appendLine(message.text)
        message.parts.filterIsInstance<MessageContentPart.Attachment>().forEach {
            appendLine("- 附件：${it.displayName}（${it.mimeType}，${it.sizeBytes} 字节）")
        }
        message.translation?.let { appendLine("\n> 译文：$it") }
        message.parts.filterIsInstance<MessageContentPart.Error>().lastOrNull()?.let {
            appendLine("- 生成失败：${it.message}")
        }
        val technicalParts = message.parts.filter {
            it is MessageContentPart.Reasoning || it is MessageContentPart.ToolCall || it is MessageContentPart.ToolResult
        }
        if (technicalParts.isNotEmpty()) {
            appendLine()
            appendLine("<details><summary>执行详情</summary>")
            technicalParts.forEach { part ->
                when (part) {
                    is MessageContentPart.Reasoning -> appendLine("- 推理：${part.text}")
                    is MessageContentPart.ToolCall -> appendLine(
                        "- 工具 `${part.toolName}`（${part.status.name}）：`${part.argumentsJson}`",
                    )
                    is MessageContentPart.ToolResult -> appendLine("- 工具结果：`${part.output}`")
                    else -> Unit
                }
            }
            appendLine("</details>")
        }
        appendLine()
    }
}

private fun Conversation.toJson(messages: List<ChatMessage>) = buildString {
    append("{\"schemaVersion\":3,\"conversation\":{")
    append("\"id\":${id.value.json()},\"title\":${title.json()},\"createdAt\":$createdAt,\"updatedAt\":$updatedAt")
    append("},\"messages\":[")
    messages.forEachIndexed { index, message ->
        if (index > 0) append(',')
        append("{\"id\":${message.id.value.json()},\"role\":${message.role.name.json()},")
        append("\"text\":${message.text.json()},\"createdAt\":${message.createdAt},")
        append("\"status\":${message.status.name.json()},\"translation\":${message.translation?.json() ?: "null"},")
        append("\"modelId\":${message.modelId?.json() ?: "null"},")
        append("\"inputTokens\":${message.inputTokens ?: "null"},")
        append("\"cachedInputTokens\":${message.cachedInputTokens ?: "null"},")
        append("\"outputTokens\":${message.outputTokens ?: "null"},")
        append("\"generationDurationMs\":${message.generationDurationMs ?: "null"},\"parts\":[")
        message.parts.forEachIndexed { partIndex, part ->
            if (partIndex > 0) append(',')
            append(part.toExportJson())
        }
        append("]}")
    }
    append("]}")
}

private fun MessageContentPart.toExportJson(): String = when (this) {
    is MessageContentPart.Text -> "{\"type\":\"text\",\"text\":${text.json()}}"
    is MessageContentPart.Attachment -> "{\"type\":\"attachment\",\"kind\":${kind.name.json()}," +
        "\"mimeType\":${mimeType.json()},\"displayName\":${displayName.json()},\"sizeBytes\":$sizeBytes}"
    is MessageContentPart.Reasoning -> "{\"type\":\"reasoning\",\"text\":${text.json()}}"
    is MessageContentPart.ToolCall -> "{\"type\":\"toolCall\",\"callId\":${callId.json()}," +
        "\"toolName\":${toolName.json()},\"argumentsJson\":${argumentsJson.json()}," +
        "\"status\":${status.name.json()}}"
    is MessageContentPart.ToolResult -> "{\"type\":\"toolResult\",\"callId\":${callId.json()}," +
        "\"output\":${output.json()},\"isError\":$isError}"
    is MessageContentPart.Error -> "{\"type\":\"error\",\"message\":${message.json()}}"
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}

private fun String.safeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
