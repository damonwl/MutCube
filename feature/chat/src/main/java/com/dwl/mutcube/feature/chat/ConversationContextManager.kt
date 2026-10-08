package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.ConversationCheckpoint
import com.dwl.mutcube.core.model.ConversationId
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

internal data class PreparedConversationContext(
    val summary: String?,
    val recentMessages: List<ChatMessage>,
)

/** Checkpoints only affect model input. Original messages remain available for history search and export. */
internal class ConversationContextManager(private val repository: ConversationRepository) {
    suspend fun prepare(
        conversationId: ConversationId,
        history: List<ChatMessage>,
        maxMessages: Int,
        contextWindow: Int?,
        summarize: suspend (String?, List<ChatMessage>) -> String,
    ): PreparedConversationContext {
        val stored = repository.getConversationCheckpoint(conversationId)
        val throughIndex = stored?.let { checkpoint ->
            history.indexOfFirst { it.id == checkpoint.throughMessageId }
                .takeIf { it >= 0 && fingerprint(history.take(it + 1)) == checkpoint.sourceFingerprint }
        }
        if (stored != null && throughIndex == null) repository.deleteConversationCheckpoint(conversationId)
        val valid = if (throughIndex == null) null else stored
        val remaining = history.drop((throughIndex ?: -1) + 1)
        val messageLimit = maxMessages.coerceAtLeast(2)
        // A token estimate is deliberately conservative and only supplements the message-count limit.
        val tokenBudget = contextWindow?.let { (it * 0.6).toInt().coerceAtLeast(2_000) }
        val estimatedTokens = remaining.sumOf { estimateTokens(it) }
        if (remaining.size <= messageLimit && (tokenBudget == null || estimatedTokens <= tokenBudget)) {
            return PreparedConversationContext(valid?.summary, remaining)
        }
        val tailCount = (messageLimit / 2).coerceAtLeast(1)
        var split = (remaining.size - tailCount).coerceAtLeast(1)
        if (tokenBudget != null) {
            while (split < remaining.size - 1 && remaining.drop(split).sumOf(::estimateTokens) > tokenBudget / 2) {
                split++
            }
        }
        val toSummarize = remaining.take(split)
        val summary = try {
            summarize(valid?.summary, toSummarize).trim().take(12_000).takeIf(String::isNotBlank)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // Failed compaction must not discard the previous usable checkpoint.
        if (summary == null) return PreparedConversationContext(valid?.summary, remaining.takeLast(messageLimit))
        val covered = history.take((throughIndex ?: -1) + 1 + split)
        repository.saveConversationCheckpoint(
            ConversationCheckpoint(conversationId, covered.last().id, fingerprint(covered), summary, System.currentTimeMillis()),
        )
        return PreparedConversationContext(summary, remaining.drop(split))
    }

    private fun estimateTokens(message: ChatMessage): Int =
        (message.text.length / 2 + message.parts.size * 80 + 40).coerceAtLeast(40)

    private fun fingerprint(messages: List<ChatMessage>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        messages.forEach { message ->
            listOf(message.id.value, message.nodeId.value, message.role.name, message.status.name, message.text).forEach {
                digest.update(it.toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
            message.parts.forEach { part ->
                digest.update(part.toString().toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
