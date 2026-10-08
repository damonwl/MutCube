package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition

interface ExternalToolService {
    suspend fun definitions(): List<ModelToolDefinition>
    suspend fun execute(call: ModelToolCall): ExternalToolResult
}

data class ExternalToolResult(
    val output: String,
    val isError: Boolean,
    val approvalWaitDurationNanos: Long = 0,
)

object NoExternalToolService : ExternalToolService {
    override suspend fun definitions() = emptyList<ModelToolDefinition>()
    override suspend fun execute(call: ModelToolCall) = ExternalToolResult("{\"error\":\"Unknown tool\"}", true)
}
