package com.dwl.mutcube.core.database

import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.core.model.TemplateOperation

/** Runtime persistence boundary; the Room implementation remains the authoritative permission gate. */
interface TemplateRuntimeRepository {
    suspend fun requireProposal(context: TemplateAccessContext, version: String, collection: String, baseRevision: Long) { error("Proposal capability unavailable") }
    suspend fun recoverInterruptedRuns()
    suspend fun enableWithGrants(binding: TemplateBindingEntity, collections: List<TemplateCollectionEntity>, versionCollections: Set<String> = emptySet(), operations: Map<String, Set<TemplateOperation>>? = null)
    suspend fun requireBinding(context: TemplateAccessContext, version: String)
    suspend fun existingRun(context: TemplateAccessContext, version: String, id: String): TemplateRunEntity?
    suspend fun read(context: TemplateAccessContext, collection: String, key: String): TemplateRecordEntity?
    suspend fun list(context: TemplateAccessContext, collection: String): List<TemplateRecordEntity>
    suspend fun count(context: TemplateAccessContext, collection: String): Int = list(context, collection).size
    suspend fun append(context: TemplateAccessContext, collection: String, key: String, json: String, source: String)
    suspend fun beginRun(value: TemplateRunEntity, version: String): TemplateRunEntity
    suspend fun summary(context: TemplateAccessContext, collection: String): TemplateSummaryEntity?
    suspend fun saveSummary(context: TemplateAccessContext, version: String, runId: String, value: TemplateSummaryEntity)
    suspend fun saveRunContext(context: TemplateAccessContext, version: String, id: String, json: String, collections: List<String>, providerId: String? = null, modelId: String? = null)
    suspend fun finishRun(id: String, status: String, output: String? = null, error: String? = null)
    suspend fun saveHistoryTrace(context: TemplateAccessContext, version: String, id: String, json: String) = Unit
    suspend fun finishRunWithRecord(context: TemplateAccessContext, id: String, version: String, collection: String, json: String)
}
