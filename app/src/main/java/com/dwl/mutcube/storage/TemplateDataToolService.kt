package com.dwl.mutcube.storage

import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.TemplateActions
import com.dwl.mutcube.template.runtime.TemplateRuntime
import com.dwl.mutcube.feature.chat.ScopedToolService
import com.dwl.mutcube.feature.chat.ExternalToolResult
import org.json.JSONObject

/** Chat sees only the service's explicitly declared actions, not raw collection APIs. */
class TemplateDataToolService(
    repository: TemplateRepository, runtime: TemplateRuntime, conversations: ConversationRepository,
    private val manifestProvider: () -> List<TemplateManifest>,
) : ScopedToolService {
    constructor(repository: TemplateRepository, runtime: TemplateRuntime, conversations: ConversationRepository,
        manifests: List<TemplateManifest>) : this(repository, runtime, conversations, { manifests })
    private val actions = TemplateActions(repository, runtime, conversations)
    private val manifests get() = manifestProvider()
    companion object {
        fun toolName(templateId: String, actionId: String): String = "template_action_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest("$templateId/$actionId".toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    suspend fun capabilities(projectId: SpaceId?): List<TemplateChatCapability> {
        val available = definitions(projectId).map { it.name }.toSet()
        return manifests.flatMap { manifest -> manifest.actions.mapNotNull { action ->
            val name = toolName(manifest.id, action.id)
            if (name !in available) null else TemplateChatCapability(name, manifest.id, manifest.name,
                action.id, action.title ?: action.description.substringBefore('；').take(60),
                action.example ?: "请使用${manifest.name}的${action.title ?: action.description.take(60)}能力", action.mode)
        } }
    }
    override suspend fun definitions(projectId: SpaceId?): List<ModelToolDefinition> {
        if (projectId == null) return emptyList()
        val tools = mutableListOf<ModelToolDefinition>()
        for (manifest in manifests) for (action in manifest.actions.filter { ActionChannel.CHAT in it.channels }) {
            try {
                actions.authorize(TemplateInstance(projectId.value, manifest.id), manifest, action)
                tools += ModelToolDefinition(toolName(manifest.id, action.id), "${manifest.name} / ${action.id}：${action.description}", action.inputSchema)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
            }
        }
        return tools
    }
    override suspend fun execute(projectId: SpaceId?, call: ModelToolCall): ExternalToolResult = try {
        require(projectId != null)
        val (manifest, action) = requireNotNull(manifests.flatMap { manifest -> manifest.actions.map { manifest to it } }
            .singleOrNull { (manifest, action) -> toolName(manifest.id, action.id) == call.name }) { "Undeclared action tool" }
        val result = actions.engine.execute(TemplateInstance(projectId.value, manifest.id), manifest, action.id,
            ActionChannel.CHAT, java.util.UUID.randomUUID().toString(), call.argumentsJson)
        val presentation = JSONObject().put("templateId", manifest.id).put("actionId", action.id)
            .put("title", action.title ?: action.description.take(60)).put("collection", action.collection)
            .put("recordKey", if (action.mode == ActionMode.GENERATE) result.key else "")
            .put("pendingConfirmation", result.pendingConfirmation)
        ExternalToolResult(JSONObject().put("presentation", presentation).put("pendingConfirmation", result.pendingConfirmation).put("key", result.key)
            .put("data", JSONObject(result.data)).toString(), false)
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        ExternalToolResult("模板操作失败：请检查项目授权、数据版本和运行记录。AI 候选不会自动启用。", true)
    }
}

data class TemplateChatCapability(val toolName: String, val templateId: String, val templateName: String,
    val actionId: String, val title: String, val example: String, val mode: ActionMode)
