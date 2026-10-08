package com.dwl.mutcube.core.database

import com.dwl.mutcube.template.core.TemplateJsonContract
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.dwl.mutcube.core.model.DataMutability
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.core.model.TemplateOperation
import com.dwl.mutcube.core.model.TemplatePermission
import com.dwl.mutcube.core.model.TemplatePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

// No project/template foreign keys: uninstalling or deleting a project must not delete user data.
@Entity(tableName = "template_collections", primaryKeys = ["templateId", "collection"])
data class TemplateCollectionEntity(
    val templateId: String,
    val collection: String,
    val schemaVersion: Int,
    val schemaJson: String,
    val mutability: String,
)

@Entity(tableName = "template_bindings", primaryKeys = ["projectId", "templateId"])
data class TemplateBindingEntity(val projectId: String, val templateId: String, val version: String, val enabled: Boolean)

@Entity(tableName = "template_permissions", primaryKeys = ["projectId", "templateId", "collection", "operation"])
data class TemplatePermissionEntity(
    val projectId: String,
    val templateId: String,
    val collection: String,
    val operation: String,
    val revoked: Boolean,
)

@Entity(tableName = "template_records", primaryKeys = ["projectId", "templateId", "collection", "recordKey"])
data class TemplateRecordEntity(
    val projectId: String,
    val templateId: String,
    val collection: String,
    val recordKey: String,
    val json: String,
    val revision: Long,
    val schemaVersion: Int,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "template_runs", primaryKeys = ["id"])
data class TemplateRunEntity(
    val id: String,
    val projectId: String,
    val templateId: String,
    val actionId: String,
    val inputJson: String,
    val outputJson: String?,
    val providerId: String,
    val modelId: String,
    val status: String,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val contextJson: String? = null,
)

@Entity(tableName = "template_audit", primaryKeys = ["id"])
data class TemplateAuditEntity(
    val id: String,
    val projectId: String,
    val templateId: String,
    val collection: String,
    val operation: String,
    val outcome: String,
    val createdAt: Long,
)

@Entity(tableName = "template_summaries", primaryKeys = ["projectId", "templateId", "collection"])
data class TemplateSummaryEntity(
    val projectId: String,
    val templateId: String,
    val collection: String,
    val sourceDigest: String,
    val summary: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
)

@Dao
internal interface TemplateDao {
    @Query("SELECT name FROM spaces WHERE id = :project")
    suspend fun projectName(project: String): String?
    @Query("SELECT COUNT(*) FROM template_records WHERE projectId = :project AND templateId = :template")
    suspend fun resetRecordCount(project: String, template: String): Int
    @Query("SELECT COUNT(*) FROM template_runs WHERE projectId = :project AND templateId = :template")
    suspend fun resetRunCount(project: String, template: String): Int
    @Query("SELECT COUNT(*) FROM template_runs WHERE projectId = :project AND templateId = :template AND status = 'RUNNING'")
    suspend fun activeRunCount(project: String, template: String): Int
    @Query("SELECT COUNT(*) FROM template_runs WHERE templateId = :template AND status = 'RUNNING'")
    suspend fun activeTemplateRuns(template: String): Int
    @Query("DELETE FROM template_current_versions WHERE templateId = :template")
    suspend fun deleteAllTemplateVersions(template: String)
    @Query("DELETE FROM template_summaries WHERE templateId = :template")
    suspend fun deleteAllTemplateSummaries(template: String)
    @Query("DELETE FROM template_records WHERE templateId = :template")
    suspend fun deleteAllTemplateRecords(template: String)
    @Query("DELETE FROM template_runs WHERE templateId = :template")
    suspend fun deleteAllTemplateRuns(template: String)
    @Query("DELETE FROM template_audit WHERE templateId = :template")
    suspend fun deleteAllTemplateAudits(template: String)
    @Query("DELETE FROM template_permissions WHERE templateId = :template")
    suspend fun deleteAllTemplatePermissions(template: String)
    @Query("DELETE FROM template_bindings WHERE templateId = :template")
    suspend fun deleteAllTemplateBindings(template: String)
    @Query("DELETE FROM template_collections WHERE templateId = :template")
    suspend fun deleteAllTemplateCollections(template: String)
    @Query("DELETE FROM template_current_versions WHERE projectId = :project AND templateId = :template")
    suspend fun resetVersions(project: String, template: String)
    @Query("DELETE FROM template_summaries WHERE projectId = :project AND templateId = :template")
    suspend fun resetSummaries(project: String, template: String)
    @Query("DELETE FROM template_records WHERE projectId = :project AND templateId = :template")
    suspend fun resetRecords(project: String, template: String)
    @Query("DELETE FROM template_runs WHERE projectId = :project AND templateId = :template")
    suspend fun resetRuns(project: String, template: String)
    @Query("DELETE FROM template_audit WHERE projectId = :project AND templateId = :template")
    suspend fun resetAudits(project: String, template: String)
    @Query("SELECT * FROM template_current_versions WHERE projectId = :project AND templateId = :template AND collection = :collection")
    suspend fun currentVersion(project: String, template: String, collection: String): TemplateCurrentVersionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCurrentVersion(value: TemplateCurrentVersionEntity)
    @Query("SELECT * FROM template_summaries WHERE projectId = :project AND templateId = :template AND collection = :collection")
    suspend fun summary(project: String, template: String, collection: String): TemplateSummaryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSummary(value: TemplateSummaryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun audit(value: TemplateAuditEntity)
    @Query("SELECT * FROM template_audit ORDER BY createdAt DESC LIMIT 200")
    fun audits(): Flow<List<TemplateAuditEntity>>
    @Query("SELECT * FROM template_collections ORDER BY templateId, collection")
    fun userCollections(): Flow<List<TemplateCollectionEntity>>
    @Query("SELECT * FROM template_bindings ORDER BY projectId, templateId")
    fun userBindings(): Flow<List<TemplateBindingEntity>>
    @Query("SELECT * FROM template_permissions WHERE revoked = 0 ORDER BY projectId, templateId, collection, operation")
    fun userPermissions(): Flow<List<TemplatePermissionEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(value: TemplateCollectionEntity)
    @Query("SELECT * FROM template_collections WHERE templateId = :template AND collection = :collection")
    suspend fun collection(template: String, collection: String): TemplateCollectionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bind(value: TemplateBindingEntity)
    @Query("SELECT * FROM template_bindings WHERE projectId = :project AND templateId = :template")
    suspend fun binding(project: String, template: String): TemplateBindingEntity?
    @Query("SELECT * FROM template_bindings WHERE projectId = :project AND enabled = 1 AND templateId != :template")
    suspend fun otherEnabledBindings(project: String, template: String): List<TemplateBindingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun grant(value: TemplatePermissionEntity)
    @Query("SELECT * FROM template_permissions WHERE projectId = :project AND templateId = :template")
    suspend fun permissions(project: String, template: String): List<TemplatePermissionEntity>
    @Query("SELECT * FROM template_permissions WHERE projectId = :project AND revoked = 0 AND operation = 'LIST'")
    suspend fun readableCollections(project: String): List<TemplatePermissionEntity>
    @Query("UPDATE template_permissions SET revoked = 1 WHERE projectId = :project AND templateId = :template")
    suspend fun revoke(project: String, template: String)
    @Query("SELECT COUNT(*) FROM spaces WHERE id = :project")
    suspend fun projectExists(project: String): Int
    @Query("SELECT * FROM template_records WHERE projectId = :project AND templateId = :template AND collection = :collection AND recordKey = :key")
    suspend fun record(project: String, template: String, collection: String, key: String): TemplateRecordEntity?
    @Query("SELECT * FROM template_records WHERE projectId = :project AND templateId = :template AND collection = :collection ORDER BY updatedAt DESC LIMIT 100")
    suspend fun records(project: String, template: String, collection: String): List<TemplateRecordEntity>
    @Query("SELECT COUNT(*) FROM template_records WHERE projectId = :project AND templateId = :template AND collection = :collection")
    suspend fun recordCount(project: String, template: String, collection: String): Int
    @Query("SELECT * FROM template_records WHERE projectId = :project AND templateId = :template AND collection = :collection AND (:beforeTime IS NULL OR updatedAt < :beforeTime OR (updatedAt = :beforeTime AND recordKey < :beforeKey)) ORDER BY updatedAt DESC, recordKey DESC LIMIT :limit")
    suspend fun recordPage(project: String, template: String, collection: String, beforeTime: Long?, beforeKey: String?, limit: Int): List<TemplateRecordEntity>
    @Query("SELECT * FROM template_records ORDER BY updatedAt DESC")
    fun userRecords(): Flow<List<TemplateRecordEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(value: TemplateRecordEntity)
    @Query("UPDATE template_records SET json = :json, revision = revision + 1, updatedAt = :now, source = 'USER' WHERE projectId = :project AND templateId = :template AND collection = :collection AND recordKey = :key AND revision = :revision")
    suspend fun updateRecord(project: String, template: String, collection: String, key: String, revision: Long, json: String, now: Long): Int
    @Query("DELETE FROM template_records WHERE projectId = :project AND templateId = :template AND collection = :collection AND recordKey = :key AND revision = :revision")
    suspend fun deleteRecord(project: String, template: String, collection: String, key: String, revision: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(value: TemplateRunEntity)
    @Query("SELECT * FROM template_runs WHERE id = :id")
    suspend fun run(id: String): TemplateRunEntity?
    @Query("UPDATE template_runs SET contextJson = :json WHERE id = :id AND status = 'RUNNING' AND contextJson IS NOT NULL")
    suspend fun saveHistoryContext(id: String, json: String): Int
    @Query("UPDATE template_runs SET contextJson = :json, providerId = COALESCE(:providerId, providerId), modelId = COALESCE(:modelId, modelId) WHERE id = :id AND status = 'RUNNING' AND contextJson IS NULL")
    suspend fun saveContext(id: String, json: String, providerId: String?, modelId: String?): Int
    @Query("SELECT * FROM template_runs WHERE projectId = :project AND templateId = :template ORDER BY createdAt DESC LIMIT 50")
    fun runs(project: String, template: String): Flow<List<TemplateRunEntity>>
    @Query("UPDATE template_runs SET status = :status, outputJson = :output, error = :error, updatedAt = :now WHERE id = :id AND status = 'RUNNING'")
    suspend fun finishRun(id: String, status: String, output: String?, error: String?, now: Long): Int
    @Query("UPDATE template_runs SET status = 'INTERRUPTED', error = 'Application restarted', updatedAt = :now WHERE status = 'RUNNING'")
    suspend fun interruptRuns(now: Long)
}

data class TemplateResetPreview(val records: Int, val runs: Int)
class TemplateRevisionConflictException(message: String) : IllegalStateException(message)

class TemplateRepository(private val database: MutCubeDatabase) : TemplateRuntimeRepository {
    private val dao = database.templateDao()
    val userRecords: Flow<List<TemplateRecordEntity>> = dao.userRecords()
    val audits: Flow<List<TemplateAuditEntity>> = dao.audits()
    val userCollections = dao.userCollections()
    val userBindings = dao.userBindings()
    val userPermissions = dao.userPermissions()

    /** Host-only maintenance API: deliberately absent from runtime ports and template/AI bridges. */
    suspend fun previewDeveloperReset(context: TemplateAccessContext): TemplateResetPreview = database.withTransaction {
        require(dao.projectExists(context.projectId) == 1) { "项目已不存在，请重新选择" }
        require(dao.binding(context.projectId, context.templateId)?.enabled == true) { "模板绑定已变化，请重新选择" }
        TemplateResetPreview(dao.resetRecordCount(context.projectId, context.templateId), dao.resetRunCount(context.projectId, context.templateId))
    }

    suspend fun resetForDeveloper(context: TemplateAccessContext, confirmedProjectName: String): TemplateResetPreview = database.withTransaction {
        require(confirmedProjectName.isNotBlank() && dao.projectName(context.projectId) == confirmedProjectName) { "项目名称不匹配，请重新确认" }
        val preview = previewDeveloperReset(context)
        check(dao.activeRunCount(context.projectId, context.templateId) == 0) { "模板仍在生成，请等待完成后再重置" }
        dao.resetVersions(context.projectId, context.templateId)
        dao.resetSummaries(context.projectId, context.templateId)
        dao.resetRecords(context.projectId, context.templateId)
        dao.resetRuns(context.projectId, context.templateId)
        dao.resetAudits(context.projectId, context.templateId)
        preview
    }

    /** Host-only permanent delete. The UI must explicitly confirm the exact template ID first. */
    suspend fun deleteInstalledTemplateData(templateId: String, confirmedId: String) = database.withTransaction {
        require(templateId.isNotBlank() && templateId == confirmedId) { "模板 ID 确认不一致" }
        check(dao.activeTemplateRuns(templateId) == 0) { "模板仍有运行中的任务" }
        dao.deleteAllTemplateVersions(templateId)
        dao.deleteAllTemplateSummaries(templateId)
        dao.deleteAllTemplateRecords(templateId)
        dao.deleteAllTemplateRuns(templateId)
        dao.deleteAllTemplateAudits(templateId)
        dao.deleteAllTemplatePermissions(templateId)
        dao.deleteAllTemplateBindings(templateId)
        dao.deleteAllTemplateCollections(templateId)
    }

    private suspend fun <T> audited(context: TemplateAccessContext, collection: String, operation: TemplateOperation, block: suspend () -> T): T {
        fun entry(outcome: String) = TemplateAuditEntity(UUID.randomUUID().toString(), context.projectId, context.templateId,
            collection, operation.name, outcome, System.currentTimeMillis())
        return try {
            database.withTransaction {
                val result = block()
                dao.audit(entry("SUCCEEDED"))
                result
            }
        } catch (failure: Exception) {
            if (failure !is kotlinx.coroutines.CancellationException) dao.audit(entry("DENIED_OR_FAILED"))
            throw failure
        }
    }

    suspend fun registerCollection(value: TemplateCollectionEntity) = database.withTransaction {
        require(value.templateId.isNotBlank() && value.collection.isNotBlank() && value.schemaVersion > 0)
        require(Json.parseToJsonElement(value.schemaJson) is JsonObject)
        TemplateJsonContract.validateSchema(Json.parseToJsonElement(value.schemaJson) as JsonObject)
        DataMutability.valueOf(value.mutability)
        val existing = dao.collection(value.templateId, value.collection)
        if (existing == null) dao.insertCollection(value) else require(existing == value) { "Collection contract changed; migration required" }
    }

    override suspend fun enableWithGrants(binding: TemplateBindingEntity, collections: List<TemplateCollectionEntity>, versionCollections: Set<String>, operations: Map<String, Set<TemplateOperation>>?) = database.withTransaction {
        require(binding.enabled && collections.isNotEmpty() && collections.all { it.templateId == binding.templateId })
        require(versionCollections.all { name -> collections.any { it.collection == name } })
        collections.forEach { registerCollection(it) }
        bind(binding)
        if (operations != null) {
            require(operations.keys.all { name -> collections.any { it.collection == name } })
            dao.revoke(binding.projectId, binding.templateId)
            operations.forEach { (collection, grants) -> grants.forEach {
                grant(TemplatePermissionEntity(binding.projectId, binding.templateId, collection, it.name, false))
            } }
            return@withTransaction
        }
        collections.forEach { collection ->
            listOf(TemplateOperation.READ, TemplateOperation.LIST, TemplateOperation.APPEND).forEach {
                grant(TemplatePermissionEntity(binding.projectId, binding.templateId, collection.collection, it.name, false))
            }
            if (collection.mutability == DataMutability.USER_EDITABLE.name) {
                listOf(TemplateOperation.UPDATE, TemplateOperation.DELETE).forEach {
                    grant(TemplatePermissionEntity(binding.projectId, binding.templateId, collection.collection, it.name, false))
                }
            }
        }
        versionCollections.forEach { collection ->
            grant(TemplatePermissionEntity(binding.projectId, binding.templateId, collection, TemplateOperation.SELECT_VERSION.name, false))
            grant(TemplatePermissionEntity(binding.projectId, binding.templateId, collection, TemplateOperation.PROPOSE_UPDATE.name, false))
        }
    }

    suspend fun bind(value: TemplateBindingEntity) = database.withTransaction {
        require(dao.projectExists(value.projectId) == 1) { "Project no longer exists" }
        require(value.version.isNotBlank() && value.templateId.isNotBlank())
        if (value.enabled) dao.otherEnabledBindings(value.projectId, value.templateId).forEach { previous ->
            dao.revoke(previous.projectId, previous.templateId)
            dao.bind(previous.copy(enabled = false))
        }
        dao.bind(value)
        if (!value.enabled) dao.revoke(value.projectId, value.templateId)
    }

    suspend fun grant(value: TemplatePermissionEntity) = database.withTransaction {
        require(dao.projectExists(value.projectId) == 1)
        require(dao.collection(value.templateId, value.collection) != null)
        TemplateOperation.valueOf(value.operation)
        dao.grant(value)
    }

    suspend fun revoke(context: TemplateAccessContext) = database.withTransaction {
        dao.revoke(context.projectId, context.templateId)
    }

    private suspend fun requirePermission(context: TemplateAccessContext, collection: String, operation: TemplateOperation) {
        require(dao.projectExists(context.projectId) == 1) { "Project no longer exists; user data remains available in data management" }
        val grants = dao.permissions(context.projectId, context.templateId).map {
            TemplatePermission(it.projectId, it.templateId, it.collection, TemplateOperation.valueOf(it.operation), it.revoked)
        }
        require(TemplatePolicy.allows(context, collection, operation, grants)) { "Data access denied" }
    }

    suspend fun checkAccess(context: TemplateAccessContext, version: String, collection: String, operation: TemplateOperation) {
        requireBinding(context, version)
        requirePermission(context, collection, operation)
    }

    override suspend fun read(context: TemplateAccessContext, collection: String, key: String): TemplateRecordEntity? = audited(context, collection, TemplateOperation.READ) {
        requirePermission(context, collection, TemplateOperation.READ)
        dao.record(context.projectId, context.templateId, collection, key)
    }

    suspend fun currentVersion(context: TemplateAccessContext, collection: String): TemplateCurrentVersionEntity? =
        audited(context, collection, TemplateOperation.READ) {
            requirePermission(context, collection, TemplateOperation.READ)
            dao.currentVersion(context.projectId, context.templateId, collection)
        }

    /** Native confirmation calls this method; selection never overwrites the referenced record. */
    suspend fun selectVersion(
        context: TemplateAccessContext,
        templateVersion: String,
        collection: String,
        recordKey: String,
        expectedRevision: Long,
    ): TemplateCurrentVersionEntity = audited(context, collection, TemplateOperation.SELECT_VERSION) {
        database.withTransaction {
            requireBinding(context, templateVersion)
            requirePermission(context, collection, TemplateOperation.SELECT_VERSION)
            requirePermission(context, collection, TemplateOperation.READ)
            require(expectedRevision >= 0)
            requireNotNull(dao.record(context.projectId, context.templateId, collection, recordKey)) { "Version record missing" }
            val current = dao.currentVersion(context.projectId, context.templateId, collection)
            if ((current?.revision ?: 0L) != expectedRevision) throw TemplateRevisionConflictException("Plan changed; review the latest version before confirming")
            if (current?.recordKey == recordKey) current else {
                TemplateCurrentVersionEntity(context.projectId, context.templateId, collection, recordKey,
                    Math.addExact(expectedRevision, 1L), System.currentTimeMillis()).also { dao.saveCurrentVersion(it) }
            }
        }
    }

    override suspend fun list(context: TemplateAccessContext, collection: String): List<TemplateRecordEntity> = audited(context, collection, TemplateOperation.LIST) {
        requirePermission(context, collection, TemplateOperation.LIST)
        dao.records(context.projectId, context.templateId, collection)
    }

    override suspend fun count(context: TemplateAccessContext, collection: String): Int = audited(context, collection, TemplateOperation.LIST) {
        requirePermission(context, collection, TemplateOperation.LIST)
        dao.recordCount(context.projectId, context.templateId, collection)
    }

    suspend fun page(context: TemplateAccessContext, collection: String, beforeTime: Long?, beforeKey: String?, limit: Int): List<TemplateRecordEntity> = audited(context, collection, TemplateOperation.LIST) {
        requirePermission(context, collection, TemplateOperation.LIST)
        require(limit in 1..50 && (beforeTime == null) == (beforeKey == null))
        require(beforeTime == null || beforeTime >= 0)
        dao.recordPage(context.projectId, context.templateId, collection, beforeTime, beforeKey, limit)
    }
    override suspend fun requireProposal(context: TemplateAccessContext, version: String, collection: String, baseRevision: Long) = audited(context, collection, TemplateOperation.PROPOSE_UPDATE) {
        requireBinding(context, version)
        requirePermission(context, collection, TemplateOperation.PROPOSE_UPDATE)
        requirePermission(context, collection, TemplateOperation.READ)
        if (baseRevision < 0 || (dao.currentVersion(context.projectId, context.templateId, collection)?.revision ?: 0L) != baseRevision) {
            throw TemplateRevisionConflictException("Proposal base version changed")
        }
    }

    override suspend fun summary(context: TemplateAccessContext, collection: String): TemplateSummaryEntity? = audited(context, collection, TemplateOperation.LIST) {
        requirePermission(context, collection, TemplateOperation.LIST)
        dao.summary(context.projectId, context.templateId, collection)
    }

    override suspend fun saveSummary(context: TemplateAccessContext, version: String, runId: String, value: TemplateSummaryEntity) = audited(context, value.collection, TemplateOperation.LIST) {
        requireBinding(context, version)
        requirePermission(context, value.collection, TemplateOperation.LIST)
        val run = requireNotNull(dao.run(runId))
        require(run.projectId == context.projectId && run.templateId == context.templateId && run.status == "RUNNING")
        require(value.projectId == context.projectId && value.templateId == context.templateId)
        require(value.sourceDigest.matches(Regex("[a-f0-9]{64}")) && value.summary.isNotBlank() && value.summary.length <= 4_000)
        dao.saveSummary(value)
    }

    override suspend fun append(context: TemplateAccessContext, collection: String, key: String, json: String, source: String) = audited(context, collection, TemplateOperation.APPEND) {
        appendValidated(context, collection, key, json, source)
    }

    suspend fun update(context: TemplateAccessContext, version: String, collection: String, key: String, revision: Long, json: String): TemplateRecordEntity =
        audited(context, collection, TemplateOperation.UPDATE) {
            requireBinding(context, version)
            requirePermission(context, collection, TemplateOperation.UPDATE)
            val contract = requireNotNull(dao.collection(context.templateId, collection))
            require(contract.mutability == DataMutability.USER_EDITABLE.name && revision > 0 && json.length <= 100_000)
            TemplateJsonContract.validate(contract.schemaJson, json)
            check(dao.updateRecord(context.projectId, context.templateId, collection, key, revision, json, System.currentTimeMillis()) == 1) { "Record revision changed" }
            requireNotNull(dao.record(context.projectId, context.templateId, collection, key))
        }

    suspend fun delete(context: TemplateAccessContext, version: String, collection: String, key: String, revision: Long) =
        audited(context, collection, TemplateOperation.DELETE) {
            requireBinding(context, version)
            requirePermission(context, collection, TemplateOperation.DELETE)
            val contract = requireNotNull(dao.collection(context.templateId, collection))
            require(contract.mutability == DataMutability.USER_EDITABLE.name && revision > 0)
            check(dao.deleteRecord(context.projectId, context.templateId, collection, key, revision) == 1) { "Record revision changed" }
        }

    private suspend fun appendValidated(context: TemplateAccessContext, collection: String, key: String, json: String, source: String) {
        requirePermission(context, collection, TemplateOperation.APPEND)
        require(key.isNotBlank() && key.length <= 200 && json.length <= 100_000)
        require(Json.parseToJsonElement(json) is JsonObject) { "Record must be a JSON object" }
        val contract = requireNotNull(dao.collection(context.templateId, collection))
        TemplateJsonContract.validate(contract.schemaJson, json)
        TemplatePolicy.requireMutationAllowed(
            DataMutability.valueOf(contract.mutability),
            dao.record(context.projectId, context.templateId, collection, key) != null,
            TemplateOperation.APPEND,
        )
        val now = System.currentTimeMillis()
        dao.insertRecord(TemplateRecordEntity(context.projectId, context.templateId, collection, key, json, 1, contract.schemaVersion, source, now, now))
    }

    override suspend fun recoverInterruptedRuns() = dao.interruptRuns(System.currentTimeMillis())
    suspend fun readableCollections(project: String): List<TemplatePermissionEntity> =
        if (dao.projectExists(project) == 1) dao.readableCollections(project) else emptyList()

    override suspend fun requireBinding(context: TemplateAccessContext, version: String) {
        require(dao.projectExists(context.projectId) == 1)
        val binding = requireNotNull(dao.binding(context.projectId, context.templateId)) { "Template is not enabled for this project" }
        require(binding.enabled && binding.version == version) { "Template binding is unavailable" }
    }

    fun runs(context: TemplateAccessContext): Flow<List<TemplateRunEntity>> = dao.runs(context.projectId, context.templateId)

    override suspend fun existingRun(context: TemplateAccessContext, version: String, id: String): TemplateRunEntity? {
        requireBinding(context, version)
        return dao.run(id)?.also {
            require(it.projectId == context.projectId && it.templateId == context.templateId) { "Request identity conflict" }
        }
    }

    override suspend fun saveRunContext(context: TemplateAccessContext, version: String, id: String, json: String, collections: List<String>, providerId: String?, modelId: String?) = database.withTransaction {
        requireBinding(context, version)
        collections.forEach { requirePermission(context, it, TemplateOperation.LIST) }
        val run = requireNotNull(dao.run(id))
        require(run.projectId == context.projectId && run.templateId == context.templateId)
        require(json.length <= 200_000 && Json.parseToJsonElement(json) is JsonObject)
        check(dao.saveContext(id, json, providerId, modelId) == 1) { "Run context is immutable or run is no longer active" }
    }

    override suspend fun saveHistoryTrace(context: TemplateAccessContext, version: String, id: String, json: String) = database.withTransaction {
        requireBinding(context, version)
        val run = requireNotNull(dao.run(id))
        require(run.projectId == context.projectId && run.templateId == context.templateId)
        require(json.length <= 80_000)
        val original = Json.parseToJsonElement(requireNotNull(run.contextJson)).jsonObject
        val updated = JsonObject(original + ("projectHistoryTools" to Json.parseToJsonElement(json))).toString()
        require(updated.length <= 200_000)
        check(dao.saveHistoryContext(id, updated) == 1)
    }

    override suspend fun beginRun(value: TemplateRunEntity, version: String): TemplateRunEntity = database.withTransaction {
        requireBinding(TemplateAccessContext(value.projectId, value.templateId), version)
        require(value.status == "RUNNING" && value.id.isNotBlank())
        val previous = dao.run(value.id)
        if (previous != null) {
            require(previous.projectId == value.projectId && previous.templateId == value.templateId &&
                previous.actionId == value.actionId && previous.inputJson == value.inputJson) { "Request identity conflict" }
            previous
        } else {
            dao.insertRun(value)
            value
        }
    }

    override suspend fun finishRun(id: String, status: String, output: String?, error: String?) {
        require(status in setOf("SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED"))
        dao.finishRun(id, status, output, error, System.currentTimeMillis())
    }

    override suspend fun finishRunWithRecord(context: TemplateAccessContext, id: String, version: String, collection: String, json: String) = audited(context, collection, TemplateOperation.APPEND) {
        requireBinding(context, version)
        val run = requireNotNull(dao.run(id))
        require(run.projectId == context.projectId && run.templateId == context.templateId && run.status == "RUNNING")
        appendValidated(context, collection, id, json, "AI:$id")
        check(dao.finishRun(id, "SUCCEEDED", json, null, System.currentTimeMillis()) == 1)
    }
}
