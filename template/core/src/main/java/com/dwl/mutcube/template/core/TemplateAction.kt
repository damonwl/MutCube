package com.dwl.mutcube.template.core

import kotlinx.serialization.json.*

enum class ActionChannel { GUI, CHAT }
enum class ActionMode { GENERATE, APPEND, LIST, CURRENT, UPDATE, DELETE, SELECT_VERSION }
enum class BindingSource { LATEST, CURRENT }
data class ActionBinding(val field: String, val collection: String, val source: BindingSource, val required: Boolean)

data class TemplateAction(
    val id: String, val description: String, val mode: ActionMode, val channels: Set<ActionChannel>,
    val inputSchema: String, val collection: String, val requestCollection: String? = null,
    val instruction: String? = null, val bindings: List<ActionBinding> = emptyList(),
    val title: String? = null, val example: String? = null,
)
data class TemplateInstance(val projectId: String, val templateId: String) {
    init { require(projectId.isNotBlank() && templateId.isNotBlank()) }
}
data class ActionResult(val key: String, val data: String, val pendingConfirmation: Boolean)

interface TemplateActionHost {
    suspend fun authorize(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction)
    suspend fun resolve(instance: TemplateInstance, manifest: TemplateManifest, binding: ActionBinding): String?
    suspend fun perform(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction, requestId: String, input: String): String
}

/** Both interfaces submit public input here; no prepared-input bypass exists. */
class TemplateActionEngine(
    private val host: TemplateActionHost,
    private val confirmation: (suspend (TemplateInstance, TemplateAction, String) -> Boolean)? = null,
) {
    fun withConfirmation(callback: suspend (TemplateInstance, TemplateAction, String) -> Boolean) = TemplateActionEngine(host, callback)
    suspend fun execute(instance: TemplateInstance, manifest: TemplateManifest, actionId: String,
        channel: ActionChannel, requestId: String, input: String): ActionResult {
        require(instance.templateId == manifest.id && requestId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        require(input.length <= 100_000)
        val action = manifest.action(actionId)
        require(channel in action.channels) { "Action not exposed to caller" }
        require(channel != ActionChannel.CHAT || action.mode !in setOf(ActionMode.APPEND, ActionMode.UPDATE, ActionMode.DELETE, ActionMode.SELECT_VERSION))
        TemplateJsonContract.validate(action.inputSchema, input)
        host.authorize(instance, manifest, action)
        val prepared = Json.parseToJsonElement(input).jsonObject.toMutableMap()
        action.bindings.forEach { binding ->
            require(binding.field !in prepared) { "Caller cannot override host context" }
            val value = host.resolve(instance, manifest, binding)
            require(!binding.required || value != null) { "Required template context missing" }
            if (value != null) prepared[binding.field] = Json.parseToJsonElement(value)
        }
        val fullInput = JsonObject(prepared).toString()
        require(fullInput.length <= 100_000)
        TemplateJsonContract.validate(manifest.preparedSchema(action), fullInput)
        if (action.mode in setOf(ActionMode.SELECT_VERSION, ActionMode.DELETE)) {
            require(channel == ActionChannel.GUI && confirmation != null) { "Host confirmation required" }
            if (!confirmation.invoke(instance, action, fullInput)) return ActionResult(requestId, "{\"confirmed\":false}", false)
        }
        val output = host.perform(instance, manifest, action, requestId, fullInput)
        if (action.mode == ActionMode.GENERATE) TemplateJsonContract.validate(manifest.collection(action.collection).schema, output)
        return ActionResult(requestId, output, action.mode == ActionMode.GENERATE && manifest.collection(action.collection).policy == CollectionPolicy.VERSIONED)
    }
}
