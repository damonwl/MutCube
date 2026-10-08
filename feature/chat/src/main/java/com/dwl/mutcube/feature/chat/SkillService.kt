package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.model.SpaceId

/** Only metadata is offered to the model until it deliberately opens a skill. */
data class SkillDescriptor(val id: String, val name: String, val description: String)

interface SkillService {
    suspend fun catalog(projectId: SpaceId?, query: String): List<SkillDescriptor>
    fun definitions(): List<ModelToolDefinition>
    suspend fun execute(projectId: SpaceId?, explicitName: String?, call: ModelToolCall): ExternalToolResult
    suspend fun openExplicit(projectId: SpaceId?, name: String): String?
}

object NoSkillService : SkillService {
    override suspend fun catalog(projectId: SpaceId?, query: String) = emptyList<SkillDescriptor>()
    override fun definitions() = emptyList<ModelToolDefinition>()
    override suspend fun execute(projectId: SpaceId?, explicitName: String?, call: ModelToolCall) = ExternalToolResult("Skill unavailable", true)
    override suspend fun openExplicit(projectId: SpaceId?, name: String): String? = null
}
