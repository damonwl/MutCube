package com.dwl.mutcube.storage

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.template.builtin.NotesTemplate
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import com.dwl.mutcube.template.ui.TemplateRuntimePage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real chat service + real secure WebView + isolated Room; no user credentials or user records. */
class NotesTemplateAcceptanceTest {
    @Test fun chatQueryGenerationExactLaunchConfirmationIsolationAndRebinding() = verify(false)
    @Test fun bundledHostUsesExplicitGuiSelectionWithoutSecondConfirmation() = verify(true)

    private fun verify(trustedGui: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        var activity: TemplateAcceptanceActivity? = null
        try {
            val conversations = RoomConversationRepository(db)
            val a = conversations.createSpace("笔记验收 A")
            val b = conversations.createSpace("笔记验收 B")
            val repository = TemplateRepository(db)
            val manifest = NotesTemplate.manifest()
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles, DefaultModelSettingsStore,
                FitnessFixture.credentials, object : ModelGateway {
                    override fun stream(request: GenerationRequest, credential: ApiCredential) = flowOf("""{"text":"A 项目的候选文稿"}""")
                })
            runtime.enable(manifest, a); runtime.enable(manifest, b)
            val engine = TemplateActions(repository, runtime, conversations).engine
            val instance = TemplateInstance(a.value, manifest.id)
            val access = TemplateAccessContext(a.value, manifest.id)
            val bAccess = TemplateAccessContext(b.value, manifest.id)
            engine.execute(instance, manifest, "notes.save", ActionChannel.GUI, "note-a", """{"text":"仅属于 A 的笔记"}""")
            engine.execute(TemplateInstance(b.value, manifest.id), manifest, "notes.save", ActionChannel.GUI, "note-b", """{"text":"仅属于 B 的笔记"}""")
            val tools = TemplateDataToolService(repository, runtime, conversations, listOf(manifest))
            val trace = mutableListOf<List<MessageContentPart>>()
            var round = 0
            val chatGateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = error("Use structured stream")
                override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> = flow {
                    assertTrue(request.tools.any { it.name == TemplateDataToolService.toolName(manifest.id, "notes.list") })
                    when (round++) {
                        0 -> {
                            emit(ModelStreamEvent.ToolCallDelta(0, "list", TemplateDataToolService.toolName(manifest.id, "notes.list"), "{}"))
                            emit(ModelStreamEvent.ToolCallDelta(1, "current", TemplateDataToolService.toolName(manifest.id, "document.current"), "{}"))
                        }
                        1 -> {
                            val returned = request.messages.filter { it.role == ModelMessageRole.TOOL }.joinToString { it.content }
                            assertTrue(returned.contains("仅属于 A")); assertFalse(returned.contains("仅属于 B"))
                            emit(ModelStreamEvent.ToolCallDelta(0, "generate", TemplateDataToolService.toolName(manifest.id, "document.generate"), """{"request":"整理我的笔记","baseRevision":0}"""))
                        }
                        else -> emit(ModelStreamEvent.TextDelta("文稿候选已生成，请打开模板预览确认。"))
                    }
                }
            }
            val chat = conversations.createWithFirstUserMessage("查询笔记并整理成文稿", a)
            ChatGenerationService(conversations, chatGateway, FitnessFixture.credentials,
                providerProfileStore = FitnessFixture.profiles, scopedToolService = tools)
                .generateReply(chat, onToolTrace = { trace += it })
            val messages = conversations.observeMessages(chat).first()
            val reply = messages.last()
            assertEquals(3, reply.parts.filterIsInstance<MessageContentPart.ToolCall>().size)
            assertTrue(trace.any { snapshot -> snapshot.any { it is MessageContentPart.ToolCall && it.status == ToolCallStatus.RUNNING } })
            val generated = reply.parts.filterIsInstance<MessageContentPart.ToolResult>().single { it.callId == "generate" }
            val output = JSONObject(generated.output)
            val candidateKey = output.getString("key")
            assertNull(repository.currentVersion(access, "documents"))
            assertEquals(1, repository.list(access, "documents").size)

            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val until = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < until) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                Thread.sleep(100)
            }
            val visible = mutableStateOf(true)
            val epoch = mutableStateOf(0)
            val dark = mutableStateOf(false)
            val project = conversations.spaces.first().first { it.id == a }
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme(themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) { Surface {
                    if (visible.value) key(epoch.value) {
                        TemplateRuntimePage(manifest, project, repository, runtime, engine,
                            onBack = { visible.value = false }, onOpenChat = { visible.value = false },
                            trustedGuiVersionSelection = trustedGui,
                            initialTarget = if (epoch.value == 0) output.getJSONObject("presentation").toString() else null)
                    } else Text("返回原项目聊天")
                } }
            } }
            fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
                (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
            fun js(code: String): String {
                val latch = CountDownLatch(1); var result = "null"
                instrumentation.runOnMainSync { val web = find(requireNotNull(activity).window.decorView)
                    if (web == null) latch.countDown() else web.evaluateJavascript(code) { result = it; latch.countDown() } }
                check(latch.await(10, TimeUnit.SECONDS)); return result
            }
            fun waitJs(code: String) {
                val limit = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < limit) { if (js(code) == "true") return; Thread.sleep(100) }
                error("WebView condition failed: $code; status=${js("document.getElementById('status')?.textContent")}")
            }
            fun locate(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text) return node
                return (0 until node.childCount).firstNotNullOfOrNull { locate(node.getChild(it), text) }
            }
            fun clickNative(text: String) {
                Thread.sleep(400)
                val limit = System.currentTimeMillis() + 15_000
                while (System.currentTimeMillis() < limit) {
                    val found = locate(instrumentation.uiAutomation.rootInActiveWindow, text)
                    if (found != null) { var clickable: AccessibilityNodeInfo = found
                        while (!clickable.isClickable) clickable = clickable.parent ?: break
                        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)); Thread.sleep(400); return }
                    Thread.sleep(100)
                }
                error("Native action missing: $text")
            }
            waitJs("typeof busy !== 'undefined' && !busy && document.getElementById('candidate').textContent.includes('A 项目的候选文稿')")
            assertEquals("true", js("document.getElementById('notes').textContent.includes('仅属于 A') && !document.getElementById('notes').textContent.includes('仅属于 B')"))
            if (!trustedGui) {
                js("document.getElementById('candidate').querySelector('button').click();true")
                clickNative("取消")
                waitJs("!busy")
                assertNull(repository.currentVersion(access, "documents"))
            }
            js("document.getElementById('candidate').querySelector('button').click();true")
            if (!trustedGui) clickNative("确认启用")
            waitJs("!busy && document.getElementById('current').textContent.includes('A 项目的候选文稿')")
            assertEquals(candidateKey, repository.currentVersion(access, "documents")!!.recordKey)
            assertEquals(1L, repository.currentVersion(access, "documents")!!.revision)
            val oldForeground = js("getComputedStyle(document.body).color")
            instrumentation.runOnMainSync { dark.value = true; epoch.value++ }
            waitJs("typeof busy !== 'undefined' && !busy && document.getElementById('current').textContent.includes('A 项目的候选文稿') && JSON.stringify(getComputedStyle(document.body).color) !== ${JSONObject.quote(oldForeground)}")
            js("document.getElementById('chat').click();true")
            val chatUntil = System.currentTimeMillis() + 15_000
            while (visible.value && System.currentTimeMillis() < chatUntil) Thread.sleep(100)
            assertFalse("Template should return to the original chat entry", visible.value)
            assertEquals(1, conversations.conversations.first().size)
            assertTrue(repository.list(bAccess, "documents").isEmpty())
            assertTrue(runCatching { engine.withConfirmation { _, _, _ -> true }.execute(TemplateInstance(b.value, manifest.id), manifest,
                "document.confirm", ActionChannel.GUI, "cross-project", """{"key":"$candidateKey","expectedRevision":0}""") }.isFailure)
            val beforeRecords = repository.list(access, "notes")
            repository.revoke(access)
            assertTrue(tools.capabilities(a).isEmpty())
            assertTrue(tools.execute(a, ModelToolCall("revoked", TemplateDataToolService.toolName(manifest.id, "notes.list"), "{}")).isError)
            assertEquals(4, tools.capabilities(b).size)
            runtime.enable(manifest, a)
            assertEquals(beforeRecords, repository.list(access, "notes"))
            assertEquals(candidateKey, repository.currentVersion(access, "documents")!!.recordKey)
            assertEquals(4, tools.capabilities(a).size)
            assertFalse(tools.execute(a, ModelToolCall("rebound", TemplateDataToolService.toolName(manifest.id, "notes.list"), "{}")).isError)
            assertEquals(1, repository.list(bAccess, "notes").size)
        } finally {
            instrumentation.runOnMainSync { activity?.let { current ->
                fun dispose(view: View) {
                    if (view is AbstractComposeView) view.disposeComposition()
                    else if (view is ViewGroup) repeat(view.childCount) { dispose(view.getChildAt(it)) }
                }
                dispose(current.window.decorView); current.findViewById<ViewGroup>(android.R.id.content).removeAllViews(); current.finish()
            } }
            db.close()
        }
    }
}
