package com.dwl.mutcube.template.runtime

import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class TemplateRuntimeTest {
    private val project = Space(SpaceId("project"), "测试", null)
    private val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
    private val manifest = TemplateManifest("test.service", "1.0.0", "测试", "templates/test/index.html",
        listOf(TemplateCollection("inputs", schema, CollectionPolicy.IMMUTABLE_HISTORY), TemplateCollection("results", schema, CollectionPolicy.IMMUTABLE_HISTORY)),
        listOf(TemplateAction("text.generate", "整理文本", ActionMode.GENERATE, setOf(ActionChannel.GUI), schema, "results", "inputs", "Return JSON")), setOf("action.run"))
    private suspend fun TemplateRuntime.runText(manifest: TemplateManifest, project: Space, id: String, input: String) =
        run(manifest, project, id, input, "text.generate")
    private val input = "{\"text\":\"input\"}"

    private fun runtime(store: Store, gateway: ModelGateway, timeout: Long = 180_000, configured: Boolean = true): TemplateRuntime {
        val conversations = Proxy.newProxyInstance(ConversationRepository::class.java.classLoader, arrayOf(ConversationRepository::class.java)) { _, method, _ ->
            when {
                method.name == "getSpaces" -> flowOf(listOf(project))
                method.name == "getConversations" -> flowOf(emptyList<Conversation>())
                method.name.startsWith("searchMemories") -> emptyList<MemoryEntry>()
                else -> error("Template must not write conversations: ${method.name}")
            }
        } as ConversationRepository
        val credentials = object : CredentialStore {
            override suspend fun read(providerId: String) = if (configured) "test-key" else null
            override suspend fun write(providerId: String, credential: String) = error("No credential writes")
            override suspend fun delete(providerId: String) = error("No credential deletes")
        }
        val testConfiguration = ProviderConfiguration(
            profiles = listOf(DEFAULT_MIMO_PROFILE.copy(modelId = "test-model")),
            activeProfileId = DEFAULT_MIMO_PROFILE.id,
        ).normalized()
        val profiles = object : ProviderProfileStore {
            override val configuration = flowOf(testConfiguration)
            override suspend fun read() = testConfiguration
            override suspend fun write(configuration: ProviderConfiguration) = Unit
        }
        return TemplateRuntime(store, conversations, profiles, DefaultModelSettingsStore, credentials, gateway, timeout)
    }

    private fun gateway(block: (GenerationRequest) -> Flow<String>) = object : ModelGateway {
        override fun stream(request: GenerationRequest, credential: ApiCredential) = block(request)
    }

    @Test fun savesSnapshotAndReplaysWithoutProviderAccess() = runBlocking {
        val store = Store()
        var calls = 0
        val runtime = runtime(store, gateway { calls++; flowOf("{\"text\":\"result\"}") })
        assertEquals("{\"text\":\"result\"}", runtime.runText(manifest, project, "one", input))
        assertEquals("SUCCEEDED", store.runs.getValue("one").status)
        assertTrue(store.runs.getValue("one").contextJson!!.contains("recentResults"))
        val unconfigured = runtime(store, gateway { error("Replay must not call model") }, configured = false)
        assertEquals("{\"text\":\"result\"}", unconfigured.runText(manifest, project, "one", input))
        assertEquals(1, calls)
        assertEquals(null, runtime.progress.value)
        assertEquals(2, store.records.size)
        assertTrue(runCatching { runtime.runText(manifest, project, "one", "{\"text\":\"conflict\"}") }.isFailure)
    }

    @Test fun configurationAndInvalidOutputPreserveInputAndOldResult() = runBlocking {
        val store = Store()
        store.records["results/old"] = store.record("results", "old", "{\"text\":\"old-result\"}")
        val noKey = runtime(store, gateway { error("No key") }, configured = false)
        assertTrue(runCatching { noKey.runText(manifest, project, "no-key", input) }.isFailure)
        assertEquals("FAILED", store.runs.getValue("no-key").status)
        val invalid = runtime(store, gateway { flowOf("invalid-json") })
        assertTrue(runCatching { invalid.runText(manifest, project, "invalid", input) }.isFailure)
        assertEquals("FAILED", store.runs.getValue("invalid").status)
        assertEquals("{\"text\":\"old-result\"}", store.records.getValue("results/old").json)
        assertTrue(store.records.containsKey("inputs/no-key") && store.records.containsKey("inputs/invalid"))
    }

    @Test fun cancellationDoesNotBlockNavigationInitialization() = runBlocking {
        val store = Store()
        val started = CompletableDeferred<Unit>()
        val runtime = runtime(store, gateway { flow { started.complete(Unit); awaitCancellation() } })
        val job = launch { runtime.runText(manifest, project, "cancel", input) }
        started.await()
        assertTrue(runtime.progress.value!!.message.contains("生成候选"))
        withTimeout(1000) { runtime.initialize() }
        job.cancelAndJoin()
        assertEquals(null, runtime.progress.value)
        assertEquals("CANCELLED", store.runs.getValue("cancel").status)
        assertTrue(store.records.containsKey("inputs/cancel"))
        assertFalse(store.records.containsKey("results/cancel"))
    }

    @Test fun timeoutFailsAndRevocationBeforeCommitDeniesResult() = runBlocking {
        val timed = Store()
        val runtime = runtime(timed, gateway { flow { awaitCancellation() } }, 20)
        assertTrue(runCatching { runtime.runText(manifest, project, "timeout", input) }.isFailure)
        assertEquals("FAILED", timed.runs.getValue("timeout").status)
        val revoked = Store()
        val revoking = runtime(revoked, gateway { flow { revoked.allowed = false; emit("{\"text\":\"denied\"}") } })
        assertTrue(runCatching { revoking.runText(manifest, project, "revoked", input) }.isFailure)
        assertEquals("FAILED", revoked.runs.getValue("revoked").status)
        assertFalse(revoked.records.containsKey("results/revoked"))
    }

    @Test fun recoveryRejectsResumingInterruptedIdentity() = runBlocking {
        val store = Store()
        store.runs["restart"] = TemplateRunEntity("restart", "project", manifest.id, "text.generate", input, null, "mimo", "model", "RUNNING", null, 1, 1)
        val runtime = runtime(store, gateway { error("Must not resume") })
        runtime.initialize()
        assertEquals("INTERRUPTED", store.runs.getValue("restart").status)
        assertTrue(runCatching { runtime.runText(manifest, project, "restart", input) }.isFailure)
    }

    @Test fun localExcerptIsPersistedWithoutExtraModelCallAndFailedPrimaryRunAllowsCacheReuse() = runBlocking {
        val store = Store()
        (1..12).forEach { store.records["results/$it"] = store.record("results", "$it", "{\"text\":\"record-$it\"}", it.toLong()) }
        var summaryCalls = 0
        var primaryCalls = 0
        val runtime = runtime(store, gateway {
            if (it.messages.first().content.startsWith("Summarize")) {
                summaryCalls++; flowOf("{\"summary\":\"历史记录摘要 [1,2,3,4]\"}")
            } else {
                primaryCalls++; flowOf(if (primaryCalls == 1) "invalid" else "{\"text\":\"result\"}")
            }
        })
        assertTrue(runCatching { runtime.runText(manifest, project, "first", input) }.isFailure)
        runtime.runText(manifest, project, "second", input)
        assertEquals(0, summaryCalls)
        assertTrue(store.cached!!.summary.contains("record-1"))
        assertTrue(store.cached!!.summary.contains("非 AI 摘要"))
        val snapshot = Json.parseToJsonElement(store.runs.getValue("second").contextJson!!).jsonObject
        assertEquals("CACHED", snapshot.getValue("historySummary").jsonObject.getValue("status").jsonPrimitive.content)
    }

    /** This fake tests runtime control flow, not Room's SQL permission implementation. */
    private class Store : TemplateRuntimeRepository {
        val runs = linkedMapOf<String, TemplateRunEntity>()
        val records = linkedMapOf<String, TemplateRecordEntity>()
        var cached: TemplateSummaryEntity? = null
        var allowed = true
        fun record(collection: String, key: String, json: String, now: Long = 1) = TemplateRecordEntity("project", "test.service", collection, key, json, 1, 1, "TEST", now, now)
        private fun checkAccess(context: TemplateAccessContext) { require(allowed && context.projectId == "project" && context.templateId == "test.service") }
        override suspend fun recoverInterruptedRuns() { runs.replaceAll { _, run -> if (run.status == "RUNNING") run.copy(status = "INTERRUPTED") else run } }
        override suspend fun enableWithGrants(binding: TemplateBindingEntity, collections: List<TemplateCollectionEntity>, versionCollections: Set<String>, operations: Map<String, Set<TemplateOperation>>?) = Unit
        override suspend fun requireBinding(context: TemplateAccessContext, version: String) { require(context.projectId == "project" && context.templateId == "test.service" && version == "1.0.0") }
        override suspend fun existingRun(context: TemplateAccessContext, version: String, id: String): TemplateRunEntity? { requireBinding(context, version); return runs[id] }
        override suspend fun read(context: TemplateAccessContext, collection: String, key: String): TemplateRecordEntity? { checkAccess(context); return records["$collection/$key"] }
        override suspend fun list(context: TemplateAccessContext, collection: String): List<TemplateRecordEntity> { checkAccess(context); return records.values.filter { it.collection == collection } }
        override suspend fun append(context: TemplateAccessContext, collection: String, key: String, json: String, source: String) { checkAccess(context); check(!records.containsKey("$collection/$key")); records["$collection/$key"] = record(collection, key, json) }
        override suspend fun beginRun(value: TemplateRunEntity, version: String): TemplateRunEntity { check(!runs.containsKey(value.id)); runs[value.id] = value; return value }
        override suspend fun summary(context: TemplateAccessContext, collection: String): TemplateSummaryEntity? { checkAccess(context); return cached }
        override suspend fun saveSummary(context: TemplateAccessContext, version: String, runId: String, value: TemplateSummaryEntity) { checkAccess(context); cached = value }
        override suspend fun saveRunContext(context: TemplateAccessContext, version: String, id: String, json: String, collections: List<String>, providerId: String?, modelId: String?) { checkAccess(context); runs[id] = runs.getValue(id).copy(contextJson = json, providerId = providerId!!, modelId = modelId!!) }
        override suspend fun finishRun(id: String, status: String, output: String?, error: String?) { if (runs.getValue(id).status == "RUNNING") runs[id] = runs.getValue(id).copy(status = status, outputJson = output, error = error) }
        override suspend fun finishRunWithRecord(context: TemplateAccessContext, id: String, version: String, collection: String, json: String) { checkAccess(context); append(context, collection, id, json, "AI"); finishRun(id, "SUCCEEDED", json, null) }
    }
}
