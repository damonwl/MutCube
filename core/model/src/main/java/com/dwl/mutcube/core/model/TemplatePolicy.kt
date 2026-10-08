package com.dwl.mutcube.core.model

enum class TemplateRunStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED }

enum class TemplateOperation { READ, LIST, APPEND, UPDATE, DELETE, PROPOSE_UPDATE, SELECT_VERSION }

data class TemplateAccessContext(val projectId: String, val templateId: String)

data class TemplatePermission(
    val projectId: String,
    val templateId: String,
    val collection: String,
    val operation: TemplateOperation,
    val revoked: Boolean = false,
)

object TemplatePolicy {
    fun allows(
        context: TemplateAccessContext,
        collection: String,
        operation: TemplateOperation,
        grants: List<TemplatePermission>,
    ): Boolean = grants.any {
        !it.revoked && it.projectId == context.projectId && it.templateId == context.templateId &&
            it.collection == collection && it.operation == operation
    }

    fun requireMutationAllowed(mutability: DataMutability, exists: Boolean, operation: TemplateOperation) {
        require(operation == TemplateOperation.APPEND || operation == TemplateOperation.PROPOSE_UPDATE)
        if (operation == TemplateOperation.APPEND) require(!exists) { "Record already exists" }
        if (operation == TemplateOperation.PROPOSE_UPDATE) {
            require(mutability == DataMutability.AI_PROPOSABLE) { "Collection does not permit AI updates" }
        }
    }
}
