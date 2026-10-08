package com.dwl.mutcube.storage

import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.ai.DefaultModelSettingsStore
import com.dwl.mutcube.core.ai.DefaultProviderProfileStore
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.database.MutCubeDatabase
import com.dwl.mutcube.core.database.RoomConversationRepository
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.template.runtime.TemplateActions
import com.dwl.mutcube.template.runtime.TemplateRuntime
import com.dwl.mutcube.template.ui.TemplateRuntimePage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Optional real-package test: pass -e packageFile after pushing a Vant Todo archive to app external files. */
class VantTodoPackageTest {
    @Test fun importsVantBundleAndCreatesTodoInIsolatedProject() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fileName = InstrumentationRegistry.getArguments().getString("packageFile")
        assumeTrue(!fileName.isNullOrBlank())
        require(fileName!!.matches(Regex("[a-zA-Z0-9_.-]+\\.mutcube-template")))
        val archive = File(context.getExternalFilesDir(null), fileName)
        assertTrue("Missing test package: $archive", archive.isFile)
        val sandbox = File(context.cacheDir, "vant-todo-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scopedContext = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        var activity: TemplateAcceptanceActivity? = null
        try {
            val conversations = RoomConversationRepository(database)
            val projectId = conversations.createSpace("Vant Todo 隔离验收")
            val project = conversations.spaces.first().single()
            val repository = TemplateRepository(database)
            val store = InstalledTemplateStore(scopedContext, repository)
            val installed = archive.inputStream().use { store.install(it) }
            val manifest = installed.definition.manifest
            assertEquals("example.vant-todo", manifest.id)
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore,
                object : CredentialStore {
                    override suspend fun read(providerId: String) = null
                    override suspend fun write(providerId: String, credential: String) = Unit
                    override suspend fun delete(providerId: String) = Unit
                }, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()
                })
            runtime.enable(manifest, projectId)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity",
            )).use { it.readBytes() }
            val deadline = System.currentTimeMillis() + 30_000
            while (activity == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                if (activity == null) Thread.sleep(100)
            }
            val testActivity = requireNotNull(activity)
            instrumentation.runOnMainSync { testActivity.setContent {
                MutCubeTheme { Surface { TemplateRuntimePage(manifest, project, repository, runtime,
                    TemplateActions(repository, runtime, conversations).engine, onBack = {}, onOpenChat = {},
                    installedResource = { path -> store.resource(manifest.id, path) },
                    hostPermissions = installed.definition.permissions) } }
            } }
            fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
                (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
            var web: WebView? = null
            while (web == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { web = find(testActivity.window.decorView) }
                if (web == null) Thread.sleep(100)
            }
            val page = requireNotNull(web)
            fun js(expression: String): String {
                val latch = CountDownLatch(1)
                var result = "null"
                instrumentation.runOnMainSync { page.evaluateJavascript(expression) { result = it; latch.countDown() } }
                check(latch.await(10, TimeUnit.SECONDS))
                return result
            }
            fun waitFor(expression: String) {
                val until = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < until) {
                    if (js(expression) == "true") return
                    Thread.sleep(100)
                }
                error("Vant Todo condition failed: $expression; page=" + js("document.body.textContent.slice(0,300)"))
            }
            waitFor("!!document.querySelector('.composer input') && document.body.textContent.includes('待办清单')")
            js("const input=document.querySelector('.composer input');input.value='真机隔离待办';input.dispatchEvent(new Event('input',{bubbles:true}));true")
            waitFor("!document.querySelector('.composer button').disabled")
            js("document.querySelector('.composer button').click();true")
            waitFor("document.body.textContent.includes('真机隔离待办')")
            val records = repository.list(TemplateAccessContext(projectId.value, manifest.id), "todos")
            assertEquals(1, records.size)
            assertTrue(records.single().json.contains("真机隔离待办"))
            instrumentation.runOnMainSync { page.reload() }
            waitFor("performance.getEntriesByType('navigation')[0]?.type === 'reload' && document.body.textContent.includes('真机隔离待办')")
            js("document.querySelector('.task-cell .van-checkbox').click();true")
            waitFor("!!document.querySelector('.task-cell .completed')")
            assertTrue(repository.list(TemplateAccessContext(projectId.value, manifest.id), "todos")
                .single().json.contains("\"done\":true"))
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            database.close()
            sandbox.deleteRecursively()
        }
    }
}
