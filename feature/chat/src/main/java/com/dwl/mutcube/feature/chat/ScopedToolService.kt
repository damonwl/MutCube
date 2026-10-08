package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.model.SpaceId

interface ScopedToolService {
    suspend fun definitions(projectId: SpaceId?): List<ModelToolDefinition>
    suspend fun execute(projectId: SpaceId?, call: ModelToolCall): ExternalToolResult
}

object NoScopedToolService : ScopedToolService {
    override suspend fun definitions(projectId: SpaceId?) = emptyList<ModelToolDefinition>()
    override suspend fun execute(projectId: SpaceId?, call: ModelToolCall) = ExternalToolResult("Unknown scoped tool", true)
}
