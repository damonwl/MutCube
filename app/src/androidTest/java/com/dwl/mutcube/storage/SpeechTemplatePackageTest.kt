package com.dwl.mutcube.storage

import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.Manifest
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.template.runtime.*
import com.dwl.mutcube.template.ui.TemplateRuntimePage
import com.dwl.mutcube.template.ui.TemplateSpeechHost
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Tests the real installed-package WebView bridge without opening the microphone or using a Provider key. */
class SpeechTemplatePackageTest {
    @Test fun speechBridgeAndNativeConsent() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fileName = InstrumentationRegistry.getArguments().getString("packageFile")
        assumeTrue(!fileName.isNullOrBlank())
        require(fileName!!.matches(Regex("[a-zA-Z0-9_.-]+\\.mutcube-template")))
        val archive = File(context.getExternalFilesDir(null), fileName)
        assertTrue(archive.isFile)
        val sandbox = File(context.cacheDir, "speech-template-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val scoped = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        var activity: TemplateAcceptanceActivity? = null
        try {
            val conversations = RoomConversationRepository(database)
            val projectId = conversations.createSpace("语音隔离验收")
            val project = conversations.spaces.first().single()
            val repository = TemplateRepository(database)
            val store = InstalledTemplateStore(scoped, repository)
            val installed = archive.inputStream().use { store.install(it) }
            val manifest = installed.definition.manifest
            assertEquals("example.speech", manifest.id)
        val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore,
                object : CredentialStore {
                    override suspend fun read(providerId: String) = null
                    override suspend fun write(providerId: String, credential: String) = Unit
                    override suspend fun delete(providerId: String) = Unit
                }, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf()
                })
            runtime.enable(manifest, projectId)
            var spoke = false
            var recorded = false
            val speechConfigured = java.util.concurrent.atomic.AtomicBoolean(false)
            val speech = object : TemplateSpeechHost {
                override suspend fun isConfigured(capability: String) = speechConfigured.get()
                override suspend fun speak(text: String, onState: (String) -> Unit) {
                    assertEquals("真机桥接", text)
                    spoke = true
                    onState("preparing"); onState("playing"); onState("finished")
                }
                override fun stopSpeaking() = Unit
                override suspend fun recognize(language: String?, onState: (String, Float) -> Unit): String {
                    assertEquals("en", language)
                    recorded = true
                    return "测试转写"
                }
                override fun stopRecognition() = Unit
                override fun cancelRecognition() = Unit
            }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")) .use { it.readBytes() }
            val deadline = System.currentTimeMillis() + 30_000
            while (activity == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                if (activity == null) Thread.sleep(100)
            }
            val target = requireNotNull(activity)
            instrumentation.runOnMainSync { target.setContent {
                MutCubeTheme { Surface { TemplateRuntimePage(manifest, project, repository, runtime,
                    TemplateActions(repository, runtime, conversations).engine, onBack = {}, onOpenChat = {},
                    installedResource = { path -> store.resource(manifest.id, path) },
                    hostPermissions = installed.definition.permissions, speechHost = speech) } }
            } }
            fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
                (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
            var web: WebView? = null
            while (web == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { web = find(target.window.decorView) }
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
                error("Speech page condition failed: $expression; page=" + js("document.body.textContent.slice(0,300)"))
            }
            waitFor("document.documentElement.dataset.speechReady === 'true'")
            js("document.getElementById('language').value='en';true")
            js("document.getElementById('text').value='真机桥接';document.getElementById('speak').click();true")
            waitFor("document.getElementById('status').textContent.includes('SERVICE_NOT_CONFIGURED')")
            assertFalse(spoke)
            speechConfigured.set(true)
            js("document.getElementById('speak').click();true")
            waitFor("document.getElementById('status').textContent.includes('朗读finished')")
            assertTrue(spoke)
            speechConfigured.set(false)
            js("document.getElementById('recognize').click();true")
            waitFor("document.getElementById('status').textContent.includes('SERVICE_NOT_CONFIGURED')")
            assertFalse(recorded)
            speechConfigured.set(true)
            js("document.getElementById('recognize').click();true")
            fun nodeWithText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text) return node
                repeat(node.childCount) { nodeWithText(node.getChild(it), text)?.let { return it } }
                return null
            }
            var cancel: AccessibilityNodeInfo? = null
            while (cancel == null && System.currentTimeMillis() < deadline) {
                cancel = nodeWithText(instrumentation.uiAutomation.rootInActiveWindow, "取消")
                if (cancel == null) Thread.sleep(100)
            }
            val button = requireNotNull(cancel)
            assertFalse(recorded)
            var clickable: AccessibilityNodeInfo? = button
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue(requireNotNull(clickable).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitFor("document.getElementById('status').textContent.includes('录音已取消')")
            assertFalse(recorded)
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                js("document.getElementById('recognize').click();true")
                var confirm: AccessibilityNodeInfo? = null
                while (confirm == null && System.currentTimeMillis() < deadline) {
                    confirm = nodeWithText(instrumentation.uiAutomation.rootInActiveWindow, "确认")
                    if (confirm == null) Thread.sleep(100)
                }
                var approval: AccessibilityNodeInfo? = requireNotNull(confirm)
                while (approval != null && !approval.isClickable) approval = approval.parent
                assertTrue(requireNotNull(approval).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitFor("document.getElementById('text').value === '测试转写'")
                assertTrue(recorded)
            }
            js("window.MutCube.onmessage=e=>window.__speechInvalidLanguage=JSON.parse(e.data);" +
                "window.MutCube.postMessage(JSON.stringify({protocolVersion:2,requestId:'invalid-language-test'," +
                "capability:'speech.recognize',input:{language:'fr'}}));true")
            waitFor("window.__speechInvalidLanguage?.requestId === 'invalid-language-test'")
            assertEquals("\"INVALID_REQUEST\"", js("window.__speechInvalidLanguage.errorCode"))
            // A page already open must lose host capabilities as soon as its project binding is removed.
            val binding = repository.userBindings.first().single()
            repository.bind(binding.copy(enabled = false))
            spoke = false
            js("window.MutCube.onmessage=e=>window.__speechRevoked=JSON.parse(e.data);" +
                "window.MutCube.postMessage(JSON.stringify({protocolVersion:2,requestId:'revoked-speech-test'," +
                "capability:'speech.speak',input:{text:'真机桥接'}}));true")
            waitFor("window.__speechRevoked?.requestId === 'revoked-speech-test'")
            assertEquals("\"PERMISSION_DENIED\"", js("window.__speechRevoked.errorCode"))
            assertFalse(spoke)
        } finally {
            instrumentation.runOnMainSync { activity?.setContent { }; activity?.finish() }
            instrumentation.waitForIdleSync()
            database.close()
            sandbox.deleteRecursively()
        }
    }
}
