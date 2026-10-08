package com.dwl.mutcube.storage

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.database.MutCubeDatabase
import com.dwl.mutcube.core.database.RoomConversationRepository
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.template.core.TemplateManifest
import com.dwl.mutcube.template.runtime.TemplateRuntime
import com.dwl.mutcube.template.runtime.TemplateActions
import com.dwl.mutcube.template.ui.TemplateRuntimePage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import com.dwl.mutcube.TemplateAcceptanceActivity
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.core.ai.DefaultModelSettingsStore
import com.dwl.mutcube.core.ai.DefaultProviderProfileStore
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstalledTemplateStoreTest {
    private val template = """{"protocolVersion":2,"id":"example.testpackage","version":"1.0.0","name":"Test","entry":"templates/test/index.html","collections":[{"id":"todos","policy":"MUTABLE","schema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}}],"actions":[{"id":"todo.list","description":"List","mode":"LIST","channels":["GUI","CHAT"],"collection":"todos","inputSchema":{"type":"object","properties":{"beforeTime":{"type":"integer"},"beforeKey":{"type":"string"}},"required":[],"additionalProperties":false}},{"id":"todo.create","description":"Create","mode":"APPEND","channels":["GUI"],"collection":"todos","inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}}],"capabilities":["action.run"]}"""
    private fun bytes(
        templateJson: String = template,
        entryName: String = "templates/test/index.html",
        page: ByteArray = "<html><body>Test</body></html>".toByteArray(),
        expectedHash: String? = null,
        extraEntries: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val hash = expectedHash ?: MessageDigest.getInstance("SHA-256").digest(page)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = """{"packageVersion":1,"minHostVersion":"0.1.2","developer":{"name":"Test"},"permissions":["data.records"],"networkDomains":[],"resources":{"templates/test/index.html":"$hash"},"template":$templateJson}"""
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry(entryName)); zip.write(page); zip.closeEntry()
            extraEntries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
        } }.toByteArray()
    }

    @Test fun rejectsAdversarialArchivesWithoutInstallingFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox = File(context.cacheDir, "template-attacks-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scoped = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val store = InstalledTemplateStore(scoped, TemplateRepository(database))
            val attacks = listOf(
                bytes(entryName = "../escape.html"),
                bytes(extraEntries = listOf("templates\\test\\index.html" to byteArrayOf())),
                bytes(expectedHash = "f".repeat(64)),
                bytes(extraEntries = listOf("assets/undeclared.js" to byteArrayOf(1))),
                bytes(page = ByteArray(2 * 1024 * 1024 + 1)),
                bytes().let { it.copyOf(it.size / 2) },
            )
            attacks.forEachIndexed { index, archive ->
                assertTrue("inspect accepted attack $index", runCatching { store.inspect(ByteArrayInputStream(archive)) }.isFailure)
                assertTrue("install accepted attack $index", runCatching { store.install(ByteArrayInputStream(archive)) }.isFailure)
            }
            assertTrue(store.templates.value.isEmpty())
            assertTrue(File(sandbox, "installed_templates").listFiles().orEmpty().isEmpty())
        } finally {
            database.close()
            sandbox.deleteRecursively()
        }
    }

    @Test fun importsRunsAndUninstallsWithoutSilentlyDeletingData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox = File(context.cacheDir, "template-test-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scopedContext = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("template-test")
            val repository = TemplateRepository(database)
            val store = InstalledTemplateStore(scopedContext, repository)
            val archive = bytes()
            assertEquals("example.testpackage", store.inspect(ByteArrayInputStream(archive)).definition.manifest.id)
            val installed = store.install(ByteArrayInputStream(archive))
            assertNotNull(store.resource(installed.definition.manifest.id, installed.definition.manifest.entry))
            assertNull(store.resource(installed.definition.manifest.id, "../manifest.json"))
            val runtime = TemplateRuntime(repository, conversations, DefaultProviderProfileStore, DefaultModelSettingsStore,
                object : CredentialStore {
                    override suspend fun read(providerId: String) = null
                    override suspend fun write(providerId: String, credential: String) = Unit
                    override suspend fun delete(providerId: String) = Unit
                }, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()
                })
            runtime.enable(installed.definition.manifest, project)
            val access = TemplateAccessContext(project.value, installed.definition.manifest.id)
            repository.append(access, "todos", "one", """{"text":"saved"}""", "USER")
            store.uninstall(installed.definition.manifest.id)
            assertTrue(store.templates.value.isEmpty())
            assertTrue(repository.userRecords.first().any { it.recordKey == "one" })
            assertTrue(runCatching { repository.read(access, "todos", "one") }.isFailure)
            store.install(ByteArrayInputStream(archive))
            store.uninstall(installed.definition.manifest.id, deleteData = true, confirmedId = installed.definition.manifest.id)
            assertTrue(repository.userRecords.first().none { it.templateId == installed.definition.manifest.id })
        } finally {
            database.close()
            sandbox.deleteRecursively()
        }
    }

    @Test fun upgradeRequiresNewVersionAndPreservesCompatibleData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox = File(context.cacheDir, "template-upgrade-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scopedContext = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val store = InstalledTemplateStore(scopedContext, TemplateRepository(database))
            store.install(ByteArrayInputStream(bytes()))
            assertTrue(runCatching { store.install(ByteArrayInputStream(bytes())) }.isFailure)
            val upgraded = bytes(template.replace("\"version\":\"1.0.0\"", "\"version\":\"1.1.0\""))
            val preview = store.inspect(ByteArrayInputStream(bytes()))
            assertTrue(runCatching { store.install(ByteArrayInputStream(upgraded), preview.definition) }.isFailure)
            assertEquals("1.1.0", store.install(ByteArrayInputStream(upgraded)).definition.manifest.version)
            val changedSchema = template.replace("\"version\":\"1.0.0\"", "\"version\":\"1.2.0\"")
                .replace("\"text\":{\"type\":\"string\"}", "\"text\":{\"type\":\"integer\"}")
            assertTrue(runCatching { store.install(ByteArrayInputStream(bytes(changedSchema))) }.isFailure)
            assertEquals("1.1.0", store.templates.value.single().definition.manifest.version)
        } finally {
            database.close()
            sandbox.deleteRecursively()
        }
    }

    @Test fun installedPageLoadsInIsolatedWebView() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sandbox = File(context.cacheDir, "template-webview-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scopedContext = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        var activity: TemplateAcceptanceActivity? = null
        try {
            val conversations = RoomConversationRepository(database)
            val projectId = conversations.createSpace("外部模板测试")
            val project = conversations.spaces.first().first { it.id == projectId }
            val repository = TemplateRepository(database)
            val store = InstalledTemplateStore(scopedContext, repository)
            val manifest = store.install(ByteArrayInputStream(bytes())).definition.manifest
            val runtime = TemplateRuntime(repository, conversations, DefaultProviderProfileStore, DefaultModelSettingsStore,
                object : CredentialStore {
                    override suspend fun read(providerId: String) = null
                    override suspend fun write(providerId: String, credential: String) = Unit
                    override suspend fun delete(providerId: String) = Unit
                }, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()
                })
            runtime.enable(manifest, projectId)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val until = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < until) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                Thread.sleep(100)
            }
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme { Surface { TemplateRuntimePage(manifest, project, repository, runtime,
                    TemplateActions(repository, runtime, conversations).engine, onBack = {}, onOpenChat = {},
                    installedResource = { path -> store.resource(manifest.id, path) }, hostPermissions = setOf("data.records")) } }
            } }
            var webView: WebView? = null
            while (webView == null && System.currentTimeMillis() < until) {
                instrumentation.runOnMainSync { webView = findWebView(requireNotNull(activity).window.decorView) }
                Thread.sleep(100)
            }
            val result = AtomicReference<String>()
            repeat(50) {
                if (result.get()?.contains("Test") == true) return@repeat
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync { requireNotNull(webView).evaluateJavascript("document.body.textContent") {
                    result.set(it); latch.countDown()
                } }
                assertTrue("WebView did not answer", latch.await(1, TimeUnit.SECONDS))
                if (result.get()?.contains("Test") != true) Thread.sleep(100)
            }
            assertTrue("External page was not loaded: ${result.get()}", result.get()?.contains("Test") == true)
            val start = CountDownLatch(1)
            instrumentation.runOnMainSync { requireNotNull(webView).evaluateJavascript(
                "window.__directNetwork='waiting';fetch('https://example.com/').then(r=>window.__directNetwork=r.ok?'allowed':'blocked').catch(()=>window.__directNetwork='blocked');true"
            ) { start.countDown() } }
            assertTrue(start.await(2, TimeUnit.SECONDS))
            var networkResult = ""
            repeat(30) {
                if (networkResult == "\"blocked\"") return@repeat
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync { requireNotNull(webView).evaluateJavascript("window.__directNetwork") {
                    networkResult = it; latch.countDown()
                } }
                assertTrue(latch.await(1, TimeUnit.SECONDS))
                if (networkResult != "\"blocked\"") Thread.sleep(100)
            }
            assertEquals("Direct WebView network request escaped the template sandbox", "\"blocked\"", networkResult)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            database.close()
            sandbox.deleteRecursively()
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) repeat(view.childCount) { index -> findWebView(view.getChildAt(index))?.let { return it } }
        return null
    }
}
