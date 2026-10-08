package com.dwl.mutcube.template.runtime

import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.database.TemplateRevisionConflictException
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.core.model.TemplateOperation
import com.dwl.mutcube.template.core.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** Host adapter. Storage identities come only from validated declarations and the active project. */
class TemplateActions(
    private val repository: TemplateRepository,
    private val runtime: TemplateRuntime,
    private val conversations: ConversationRepository,
) : TemplateActionHost {
    val engine = TemplateActionEngine(this)
    override suspend fun authorize(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction) {
        val context = TemplateAccessContext(instance.projectId, instance.templateId)
        repository.requireBinding(context, manifest.version)
        val operations = when (action.mode) {
            ActionMode.LIST -> setOf(TemplateOperation.LIST)
            ActionMode.CURRENT -> setOf(TemplateOperation.READ)
            ActionMode.APPEND -> setOf(TemplateOperation.READ, TemplateOperation.APPEND)
            ActionMode.UPDATE -> setOf(TemplateOperation.READ, TemplateOperation.UPDATE)
            ActionMode.DELETE -> setOf(TemplateOperation.READ, TemplateOperation.DELETE)
            ActionMode.SELECT_VERSION -> setOf(TemplateOperation.READ, TemplateOperation.SELECT_VERSION)
            ActionMode.GENERATE -> setOf(TemplateOperation.READ, TemplateOperation.LIST, TemplateOperation.APPEND)
        }
        operations.forEach { repository.checkAccess(context, manifest.version, action.collection, it) }
        if (action.mode == ActionMode.GENERATE) {
            listOf(TemplateOperation.READ, TemplateOperation.APPEND).forEach {
                repository.checkAccess(context, manifest.version, requireNotNull(action.requestCollection), it)
            }
        }
        action.bindings.forEach {
            repository.checkAccess(context, manifest.version, it.collection,
                if (it.source == BindingSource.LATEST) TemplateOperation.LIST else TemplateOperation.READ)
        }
        if (manifest.collection(action.collection).policy == CollectionPolicy.VERSIONED && action.mode == ActionMode.GENERATE) {
            repository.requireProposal(context, manifest.version, action.collection, repository.currentVersion(context, action.collection)?.revision ?: 0L)
        }
    }
    override suspend fun resolve(instance: TemplateInstance, manifest: TemplateManifest, binding: ActionBinding): String? {
        manifest.collection(binding.collection)
        val context = TemplateAccessContext(instance.projectId, instance.templateId)
        return when (binding.source) {
            BindingSource.LATEST -> repository.list(context, binding.collection).firstOrNull()?.json
            BindingSource.CURRENT -> repository.currentVersion(context, binding.collection)?.let {
                repository.read(context, binding.collection, it.recordKey)?.json
            }
        }
    }
    override suspend fun perform(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction, requestId: String, input: String): String {
        authorize(instance, manifest, action)
        val context = TemplateAccessContext(instance.projectId, instance.templateId)
        val args = Json.parseToJsonElement(input).jsonObject
        fun revision() = args.getValue("expectedRevision").jsonPrimitive.long.also { require(it >= 0) }
        fun key() = args.getValue("key").jsonPrimitive.content.also { require(it.isNotBlank() && it.length <= 200) }
        fun record(row: com.dwl.mutcube.core.database.TemplateRecordEntity) = buildJsonObject {
            put("key", row.recordKey); put("revision", row.revision)
            put("data", Json.parseToJsonElement(row.json)); put("updatedAt", row.updatedAt)
        }
        return when (action.mode) {
            ActionMode.GENERATE -> {
                val project = requireNotNull(conversations.spaces.first().find { it.id.value == instance.projectId }) { "Project missing" }
                runtime.run(manifest, project, requestId, input, action.id)
            }
            ActionMode.APPEND -> {
                val existing = repository.read(context, action.collection, requestId)
                if (existing == null) repository.append(context, action.collection, requestId, input, "USER")
                else require(existing.json == input) { "Request identity conflict" }
                record(requireNotNull(repository.read(context, action.collection, requestId))).toString()
            }
            ActionMode.LIST -> {
                val beforeTime = args["beforeTime"]?.jsonPrimitive?.long
                val beforeKey = args["beforeKey"]?.jsonPrimitive?.content
                val rows = repository.page(context, action.collection, beforeTime, beforeKey, 20)
                var remaining = 90_000
                val records = rows.map(::record).takeWhile { row -> remaining -= row.toString().length; remaining >= 0 }
                require(rows.isEmpty() || records.isNotEmpty()) { "Record too large for page" }
                val last = records.lastOrNull()
                buildJsonObject {
                    put("records", JsonArray(records))
                    put("next", if (last != null && (rows.size == 20 || records.size < rows.size)) buildJsonObject {
                        put("beforeTime", last.getValue("updatedAt")); put("beforeKey", last.getValue("key"))
                    } else JsonNull)
                }.toString()
            }
            ActionMode.CURRENT -> {
                val current = repository.currentVersion(context, action.collection)
                buildJsonObject {
                    put("revision", current?.revision ?: 0L)
                    put("key", current?.recordKey?.let(::JsonPrimitive) ?: JsonNull)
                    put("data", current?.let { repository.read(context, action.collection, it.recordKey)?.json?.let(Json::parseToJsonElement) } ?: JsonNull)
                }.toString()
            }
            ActionMode.UPDATE -> record(repository.update(context, manifest.version, action.collection, key(), revision(), args.getValue("data").toString())).toString()
            ActionMode.DELETE -> {
                repository.delete(context, manifest.version, action.collection, key(), revision())
                "{\"confirmed\":true}"
            }
            ActionMode.SELECT_VERSION -> {
                val candidateKey = key()
                val base = revision()
                val run = repository.existingRun(context, manifest.version, candidateKey)
                require(run != null && run.status == "SUCCEEDED" && manifest.action(run.actionId).collection == action.collection) { "Candidate run missing" }
                if (Json.parseToJsonElement(run.inputJson).jsonObject.getValue("baseRevision").jsonPrimitive.long != base) {
                    throw TemplateRevisionConflictException("Proposal source version changed")
                }
                val current = repository.selectVersion(context, manifest.version, action.collection, candidateKey, base)
                buildJsonObject { put("confirmed", true); put("key", current.recordKey); put("revision", current.revision) }.toString()
            }
        }
    }
}
