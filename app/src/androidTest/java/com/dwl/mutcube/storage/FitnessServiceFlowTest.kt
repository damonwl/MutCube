package com.dwl.mutcube.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.template.builtin.FitnessTemplate
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal object FitnessFixture {
    val profiles = object : ProviderProfileStore {
        private val value = ProviderConfiguration(listOf(ProviderProfile(
            id = "fixture", name = "Isolated fixture", baseUrl = "https://example.invalid/v1", modelId = "fixture-model",
        )), "fixture")
        override val configuration = flowOf(value)
        override suspend fun read() = value
        override suspend fun write(configuration: ProviderConfiguration) = error("No writes")
    }
    val profile = """{"heightCm":190,"weightKg":94,"goal":"增肌与力量","split":3,"weeklyDays":3,"experience":"隔离测试","equipment":"哑铃","limitations":"腿部暂不加重","estimatedLoads":""}"""
    fun plan(date: String = "2026-09-17") = """{"name":"隔离推训练","targetDate":"$date","description":"仅供测试，不是用户实际计划","exercises":[{"id":"press","name":"哑铃卧推","weightKg":12.5,"sets":2,"repsMin":10,"repsMax":12,"rirMin":1,"rirMax":2,"note":"控制动作"}]}"""
    fun training(planKey: String, date: String = "2026-09-17") = JSONObject().put("date", date).put("planKey", planKey)
        .put("plan", JSONObject(plan())).put("exercises", org.json.JSONArray("""[{"exerciseId":"press","name":"哑铃卧推","sets":[{"weightKg":12.5,"reps":12,"rir":2},{"weightKg":12.5,"reps":11,"rir":1}]}]"""))
        .put("note", "隔离模拟记录").toString()
    val credentials = object : CredentialStore {
        override suspend fun read(providerId: String) = "fixture"
        override suspend fun write(providerId: String, credential: String) = error("No writes")
        override suspend fun delete(providerId: String) = error("No deletes")
    }
}

class FitnessServiceFlowTest {
    @Test fun immutableTrainingAndSharedChatCandidatesRespectSourcesAndPagination() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(db)
            val project = conversations.createSpace("isolated-fitness")
            val other = conversations.createSpace("isolated-other")
            val repository = TemplateRepository(db)
            val manifest = FitnessTemplate.manifest()
            var fail = false
            var revokeDuringGeneration = false
            val access = TemplateAccessContext(project.value, manifest.id)
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flow {
                    if (fail) error("fixture failure")
                    if (revokeDuringGeneration) repository.revoke(access)
                    emit(FitnessFixture.plan(JSONObject(request.messages.last().content).getString("targetDate")))
                }
            }
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore, FitnessFixture.credentials, gateway)
            val engine = TemplateActions(repository, runtime, conversations).engine
            val instance = TemplateInstance(project.value, manifest.id)
            runtime.enable(manifest, project)
            val tools = TemplateDataToolService(repository, runtime, conversations, listOf(manifest))
            assertEquals(5, tools.capabilities(project).size)
            assertTrue(tools.capabilities(project).all { it.title.isNotBlank() && it.example.isNotBlank() })
            val publicInput = """{"request":"首份计划","baseRevision":0,"targetDate":"2026-09-17"}"""
            assertTrue(runCatching { engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "missing-profile", publicInput) }.isFailure)
            engine.execute(instance, manifest, "profile.save", ActionChannel.GUI, "profile", FitnessFixture.profile)
            val first = engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "first-plan", publicInput)
            val second = engine.execute(instance, manifest, "plan.generate", ActionChannel.CHAT, "second-plan", publicInput)
            assertTrue(first.pendingConfirmation)
            assertNull(repository.currentVersion(access, "plans"))
            val confirming = engine.withConfirmation { _, _, _ -> true }
            confirming.execute(instance, manifest, "plan.confirm", ActionChannel.GUI, "confirm", """{"key":"${first.key}","expectedRevision":0}""")
            assertTrue(runCatching { confirming.execute(instance, manifest, "plan.confirm", ActionChannel.GUI, "stale", """{"key":"${second.key}","expectedRevision":1}""") }.isFailure)
            val actual = FitnessFixture.training(first.key)
            repeat(2) { engine.execute(instance, manifest, "training.submit", ActionChannel.GUI, "training-2026-09-17", actual) }
            assertEquals(1, repository.list(access, "training").size)
            assertTrue(runCatching { engine.execute(instance, manifest, "training.submit", ActionChannel.GUI, "training-2026-09-17", actual.replace("隔离模拟记录", "different")) }.isFailure)
            assertTrue(runCatching { engine.execute(instance, manifest, "training.submit", ActionChannel.CHAT, "attack", actual) }.isFailure)
            val nextInput = """{"request":"下一次","baseRevision":1,"targetDate":"2026-09-19"}"""
            fail = true
            assertTrue(runCatching { engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "failed-next", nextInput) }.isFailure)
            assertEquals(1, repository.list(access, "training").size)
            assertEquals("FAILED", repository.existingRun(access, manifest.version, "failed-next")!!.status)
            fail = false
            val result = tools.execute(project, ModelToolCall("chat", TemplateDataToolService.toolName(manifest.id, "plan.generate"), nextInput))
            assertFalse(result.isError)
            val generatedKey = JSONObject(result.output).getString("key")
            val presentation = JSONObject(result.output).getJSONObject("presentation")
            assertEquals(manifest.id, presentation.getString("templateId"))
            assertEquals("plans", presentation.getString("collection"))
            assertEquals(generatedKey, presentation.getString("recordKey"))
            assertTrue(presentation.getBoolean("pendingConfirmation"))
            val input = JSONObject(repository.existingRun(access, manifest.version, generatedKey)!!.inputJson)
            assertEquals(190, input.getJSONObject("profile").getInt("heightCm"))
            assertEquals(first.key, input.getJSONObject("last_training").getString("planKey"))
            assertTrue(input.has("current_plan"))
            assertEquals(first.key, repository.currentVersion(access, "plans")!!.recordKey)
            assertTrue(tools.definitions(other).isEmpty())
            assertTrue(tools.capabilities(other).isEmpty())
            assertTrue(tools.execute(project, ModelToolCall("spoof", TemplateDataToolService.toolName(manifest.id, "plan.generate"), JSONObject(nextInput).put("profile", JSONObject(FitnessFixture.profile)).toString())).isError)
            repeat(103) { engine.execute(instance, manifest, "training.submit", ActionChannel.GUI, "fixture-$it", FitnessFixture.training(first.key, "2025-01-01")) }
            val keys = mutableListOf<String>()
            var cursor = "{}"
            do {
                val page = JSONObject(engine.execute(instance, manifest, "training.list", ActionChannel.GUI, "page-${keys.size}", cursor).data)
                val rows = page.getJSONArray("records")
                repeat(rows.length()) { keys += rows.getJSONObject(it).getString("key") }
                cursor = if (page.isNull("next")) "" else page.getJSONObject("next").toString()
            } while (cursor.isNotEmpty())
            assertEquals(104, keys.size)
            assertEquals(104, keys.toSet().size)
            revokeDuringGeneration = true
            assertTrue(runCatching { engine.execute(instance, manifest, "plan.generate", ActionChannel.GUI, "revoked-next", nextInput) }.isFailure)
            assertEquals("FAILED", repository.existingRun(access, manifest.version, "revoked-next")!!.status)
            assertTrue(tools.definitions(project).isEmpty())
            assertTrue(tools.capabilities(project).isEmpty())
            assertEquals(104, repository.userRecords.first().count { it.collection == "training" })
            assertTrue(repository.userRecords.first().none { it.collection == "plans" && it.recordKey == "revoked-next" })
        } finally { db.close() }
    }
}
