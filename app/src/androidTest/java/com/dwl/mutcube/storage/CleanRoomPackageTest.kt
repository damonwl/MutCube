package com.dwl.mutcube.storage

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.ai.DefaultModelSettingsStore
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.database.MutCubeDatabase
import com.dwl.mutcube.core.database.RoomConversationRepository
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.template.core.ActionChannel
import com.dwl.mutcube.template.core.TemplateInstance
import com.dwl.mutcube.template.runtime.TemplateActions
import com.dwl.mutcube.template.runtime.TemplateRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Runs a package created only through the public quickstart CLI against isolated host data. */
class CleanRoomPackageTest {
    @Test fun generatedEnglishSampleInstallsAndRunsDeclaredActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = InstrumentationRegistry.getArguments().getString("packageFile")
        assumeTrue(!fileName.isNullOrBlank())
        require(fileName!!.matches(Regex("[a-zA-Z0-9_.-]+\\.mutcube-template")))
        val archive = File(context.getExternalFilesDir(null), fileName)
        assertTrue(archive.isFile)
        val sandbox = File(context.cacheDir, "cleanroom-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scoped = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val projectId = conversations.createSpace("外部开发者隔离验收")
            val repository = TemplateRepository(database)
            val store = InstalledTemplateStore(scoped, repository)
            val preview = archive.inputStream().use { store.inspect(it) }
            val installed = archive.inputStream().use { store.install(it, preview.definition) }
            val manifest = installed.definition.manifest
            assertEquals("org.example.cleanroom", manifest.id)
            assertEquals("Independent developer", installed.definition.developer.name)
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore,
                object : CredentialStore {
                    override suspend fun read(providerId: String) = null
                    override suspend fun write(providerId: String, credential: String) = Unit
                    override suspend fun delete(providerId: String) = Unit
                }, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()
                })
            runtime.enable(manifest, projectId)
            val instance = TemplateInstance(projectId.value, manifest.id)
            val actions = TemplateActions(repository, runtime, conversations).engine
            actions.execute(instance, manifest, "profile.create", ActionChannel.GUI, "cleanroom-record",
                """{"goal":"IELTS","level":"B1","dailyMinutes":20}""")
            val listed = actions.execute(instance, manifest, "profile.list", ActionChannel.GUI, "cleanroom-list", "{}")
            val records = JSONObject(listed.data).getJSONArray("records")
            assertEquals("IELTS", records.getJSONObject(0).getJSONObject("data").getString("goal"))
            assertEquals(1, repository.list(TemplateAccessContext(projectId.value, manifest.id), "profiles").size)
            val binding = repository.userBindings.first().single()
            repository.bind(binding.copy(enabled = false))
            assertTrue(runCatching {
                actions.execute(instance, manifest, "profile.list", ActionChannel.GUI, "after-revocation", "{}")
            }.isFailure)
        } finally {
            database.close()
            sandbox.deleteRecursively()
        }
    }
}
