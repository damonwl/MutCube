package com.dwl.mutcube.storage

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.room.Room
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.MutCubeApplication
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.template.builtin.FitnessTemplate
import com.dwl.mutcube.template.runtime.*
import com.dwl.mutcube.template.ui.TemplateRuntimePage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual secure WebView + actual native confirmation; all business facts use an in-memory Room. */
class FitnessWebViewTest {
    @Test fun actualPageDraftSubmissionCalendarAndNativeConfirmation() = verify(false)
    @Test fun chatResultOpensCandidateWithoutAutomaticallyActivating() = verify(false, true)
    @Test fun revisedPlanIsVisibleAndExerciseCanBeRemovedWithoutChangingPlan() = verify(false, false, true)
    @Test fun completedPreviousDayPlanDoesNotBecomeTodaysDraft() = verify(false, false, false, true)

    @Test fun actualPageAndProjectChatWithRealProvider() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveProvider") == "true")
        verify(true)
    }

    private fun verify(live: Boolean, resultNavigationOnly: Boolean = false, draftActionsOnly: Boolean = false,
        rolloverOnly: Boolean = false) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as MutCubeApplication).container
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        // Xiaomi may reject ActivityScenario's background launch (START_ABORTED). Use the shell
        // launch identity, then obtain the same Activity through the instrumentation lifecycle monitor.
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity",
        )).use { it.readBytes() }
        var testActivity: TemplateAcceptanceActivity? = null
        val launchUntil = System.currentTimeMillis() + 30_000
        while (testActivity == null && System.currentTimeMillis() < launchUntil) {
            instrumentation.runOnMainSync {
                testActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<TemplateAcceptanceActivity>().firstOrNull()
            }
            if (testActivity == null) Thread.sleep(100)
        }
        val activity = requireNotNull(testActivity) { "Acceptance Activity did not resume" }
        val scenario = object {
            fun onActivity(block: (TemplateAcceptanceActivity) -> Unit) = instrumentation.runOnMainSync { block(activity) }
            fun close() = instrumentation.runOnMainSync {
                fun dispose(view: View) {
                    if (view is AbstractComposeView) view.disposeComposition()
                    else if (view is ViewGroup) repeat(view.childCount) { dispose(view.getChildAt(it)) }
                }
                dispose(activity.window.decorView)
                activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
                activity.finish()
            }
        }
        try {
            val conversations = RoomConversationRepository(db)
            val project = conversations.createSpace("隔离页面验收")
            val repository = TemplateRepository(db)
            val manifest = FitnessTemplate.manifest()
            var failGeneration = false
            val gateway = if (live) OpenAiCompatibleModelGateway() else object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential) = flow {
                    if (failGeneration) error("Fixture Provider failure")
                    val input = JSONObject(request.messages.last().content)
                    if (input.has("form")) emit(JSONObject().put("reply", "资料已整理，请核对后保存。")
                        .put("ready", true).put("fields", input.getJSONObject("form").put("heightCm", "190").put("weightKg", "94")).toString())
                    else emit(FitnessFixture.plan(input.getString("targetDate")))
                }
            }
            val configured = (if (live) container.modelSettingsStore.read() else ModelSettings())
                .copy(toolCallingEnabled = true, memoryWriteEnabled = false)
            val settings = object : ModelSettingsStore {
                override val settings = flowOf(configured)
                override suspend fun read() = configured
                override suspend fun write(settings: ModelSettings) = error("Read-only")
            }
            val credentials = if (live) container.credentialStore else FitnessFixture.credentials
            val profiles = if (live) container.providerProfileStore else FitnessFixture.profiles
            if (live) {
                val active = profiles.read().activeProfile
                println("LIVE_PROVIDER=${active.name}; MODEL=${active.modelId}")
            }
            val runtime = TemplateRuntime(repository, conversations, profiles, settings, credentials, gateway)
            runtime.enable(manifest, project)
            val actions = TemplateActions(repository, runtime, conversations)
            val space = conversations.spaces.first().single()
            val access = TemplateAccessContext(project.value, manifest.id)
            val visible = mutableStateOf(true)
            val dark = mutableStateOf(false)
            scenario.onActivity { activity -> activity.setContent {
                MutCubeTheme(themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                    if (visible.value) TemplateRuntimePage(manifest, space, repository, runtime, actions.engine,
                        onBack = { visible.value = false }, onOpenChat = { visible.value = false },
                        trustedGuiVersionSelection = true)
                    else Surface { Text("项目聊天测试入口") }
                    }
                }
            } }
            fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup) {
                (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            } else null
            fun js(expression: String): String {
                val latch = CountDownLatch(1)
                var output = "null"
                scenario.onActivity { activity ->
                    val web = find(activity.window.decorView)
                    if (web == null) latch.countDown()
                    else web.evaluateJavascript(expression) { output = it; latch.countDown() }
                }
                check(latch.await(10, TimeUnit.SECONDS)) { "JavaScript callback timed out" }
                return output
            }
            fun waitJs(expression: String, timeout: Long = 30_000) {
                val until = System.currentTimeMillis() + timeout
                var last = ""
                while (System.currentTimeMillis() < until) {
                    last = js(expression)
                    if (last == "true") return
                    Thread.sleep(100)
                }
                error("Page condition failed: $expression; last=$last; status=" + js("document.getElementById('status').textContent") +
                    "; vant=" + js("typeof window.FitnessProfileVant") +
                    "; assets=" + js("[...document.scripts].map(s=>s.src).filter(Boolean).join(',')"))
            }
            fun nodeWithText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text) return node
                return (0 until node.childCount).firstNotNullOfOrNull { nodeWithText(node.getChild(it), text) }
            }
            fun clickNative(text: String) {
                // Do not click the accessibility node of a previous dialog during its exit animation.
                Thread.sleep(400)
                val until = System.currentTimeMillis() + 15_000
                while (System.currentTimeMillis() < until) {
                    val found = nodeWithText(instrumentation.uiAutomation.rootInActiveWindow, text)
                    if (found != null) {
                        var clickable: AccessibilityNodeInfo = found
                        while (!clickable.isClickable) clickable = clickable.parent ?: break
                        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        Thread.sleep(400)
                        return
                    }
                    Thread.sleep(100)
                }
                error("Native action not found: $text")
            }
            fun screenshot(name: String) {
                // Let the native dialog dismissal and the next WebView frame finish before capture.
                Thread.sleep(400)
                val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val dir = File(context.getExternalFilesDir(null), "template-acceptance").also { it.mkdirs() }
                File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            waitJs("typeof busy !== 'undefined' && !busy && page === 'profile'")
            if (!live) {
                js("document.getElementById('startProfileAi').click();true")
                waitJs("!busy && !!document.getElementById('profileInput')")
                js("document.getElementById('profileInput').value='身高190厘米，体重94公斤，希望三分化训练';document.getElementById('sendProfileAi').click();true")
                waitJs("!busy && profileEntry.heightCm === '190' && profileDialogue.length === 2")
                assertTrue(repository.list(access, "profiles").isEmpty())
                assertEquals(1, repository.list(access, "profile_candidates").size)
                js("document.getElementById('reviewProfile').click();true")
            } else js("document.getElementById('startProfileForm').click();true")
            waitJs("!busy && !!document.getElementById('height')")
            screenshot(if (live) "live-profile" else "light-profile")
            js("document.getElementById('height').value='190';document.getElementById('weight').value='94';document.getElementById('equipment').value='哑铃与绳索';document.getElementById('limitations').value='腿部暂不加重';document.getElementById('profileForm').requestSubmit();true")
            waitJs("!busy && page === 'train'")
            js("document.getElementById('generate').click();true")
            waitJs("!busy && !!document.getElementById('confirmPlan')", if (live) 240_000 else 30_000)
            assertNull(repository.currentVersion(access, "plans"))
            js("document.getElementById('closePreview').click();true")
            waitJs("!busy")
            assertNull(repository.currentVersion(access, "plans"))
            js("window.MutCubeLaunchTarget={collection:'plans',recordKey:candidates[0].key};window.dispatchEvent(new Event('mutcube:open-record'));true")
            waitJs("!busy && !!document.getElementById('confirmPlan')")
            assertNull(repository.currentVersion(access, "plans"))
            if (resultNavigationOnly) {
                assertTrue(js("document.getElementById('overlay').textContent.includes('启用计划')") == "true")
                js("document.getElementById('closePreview').click();true")
                waitJs("!busy")
                assertNull(repository.currentVersion(access, "plans"))
                return@runBlocking
            }
            js("document.getElementById('confirmPlan').click();true")
            waitJs("!busy && !!draft")
            assertEquals(1L, repository.currentVersion(access, "plans")!!.revision)
            if (rolloverOnly) {
                failGeneration = true
                js("draft.date=dateKey(new Date(Date.now()-86400000));draft.exercises.forEach(a=>a.sets.forEach(s=>{s.reps='12';s.rir='2'}));render();document.getElementById('submit').click();true")
                waitJs("!busy && document.getElementById('status').textContent.includes('失败')")
                assertEquals(1, repository.list(access, "training").size)
                assertEquals("true", js("targetDate===TODAY"))
                js("document.getElementById('refresh').click();true")
                waitJs("!busy && draft===null")
                assertEquals("true", js("document.getElementById('content').textContent.includes('等待下一次训练计划') && !document.getElementById('submit')"))
                assertEquals("true", js("document.getElementById('content').textContent.includes('待执行计划') && document.getElementById('content').textContent.includes('暂无。上一份计划已完成') && !document.getElementById('content').textContent.includes('当前已启用计划')"))
                js("page='mine';render();true")
                assertEquals("true", js("document.getElementById('content').textContent.includes('待执行计划：暂无') && document.getElementById('content').textContent.includes('上一份已完成')"))
                js("page='train';render();true")
                failGeneration = false
                js("document.getElementById('generate').click();true")
                waitJs("!busy && !!document.getElementById('confirmPlan')")
                assertEquals("null", js("draft"))
                js("document.getElementById('confirmPlan').click();true")
                waitJs("!busy && draft?.planKey===current.key && current.revision===2")
                return@runBlocking
            }
            if (draftActionsOnly) {
                js("document.querySelector('[data-remove-exercise=\"0\"]').click();true")
                waitJs("!!document.getElementById('confirmRemoveExercise')")
                js("document.getElementById('confirmRemoveExercise').click();true")
                waitJs("!busy && draft.exercises.length===0")
                val selectedPlan = repository.currentVersion(access, "plans")!!
                assertEquals(1, JSONObject(repository.read(access, "plans", selectedPlan.recordKey)!!.json)
                    .getJSONArray("exercises").length())
                js("document.querySelector('[data-restore-exercise]').click();true")
                waitJs("!busy && draft.exercises.length===1")
                js("document.querySelector('[data-edit-set=\"0:0:reps\"]').click();true")
                assertEquals("\"10\"", js("document.getElementById('setValue').value"))
                js("document.getElementById('setValue').value='12';document.getElementById('saveSetValue').click();true")
                assertEquals("\"12\"", js("draft.exercises[0].sets[0].reps"))
                js("document.getElementById('generate').click();true")
                waitJs("!busy && !!document.getElementById('confirmPlan')")
                js("document.getElementById('confirmPlan').click();true")
                waitJs("!busy && current.revision===2 && draft.planKey===current.key")
                assertEquals(2L, repository.currentVersion(access, "plans")!!.revision)
                assertEquals("true", js("document.getElementById('content').textContent.includes('待执行计划')"))
                assertEquals("\"\"", js("draft.exercises[0].sets[0].reps"))
                js("document.getElementById('restorePreviousDraft').click();true")
                waitJs("!busy && draft.planKey!==current.key")
                assertEquals("\"12\"", js("draft.exercises[0].sets[0].reps"))
                js("document.getElementById('refresh').click();true")
                waitJs("!busy && draft.planKey!==current.key && !!document.getElementById('switchCurrentPlan')")
                js("document.getElementById('switchCurrentPlan').click();true")
                waitJs("!busy && draft.planKey===current.key")
                assertEquals("\"\"", js("draft.exercises[0].sets[0].reps"))
                return@runBlocking
            }
            assertEquals("true", js("document.querySelector('[data-edit-set=\"0:0:reps\"]').getBoundingClientRect().height <= 55"))
            assertEquals("true", js("!document.querySelector('[data-wheel-field]')"))
            assertEquals("\"\"", js("draft.exercises[0].sets[0].reps"))
            js("document.querySelector('[data-edit-set=\"0:0:reps\"]').click();true")
            assertEquals("\"10\"", js("document.getElementById('setValue').value"))
            js("document.getElementById('cancelSetValue').click();true")
            assertEquals("\"\"", js("draft.exercises[0].sets[0].reps"))
            js("document.querySelector('[data-edit-set=\"0:0:reps\"]').click();document.getElementById('setValue').value='12';document.getElementById('saveSetValue').click();true")
            assertEquals("\"12\"", js("draft.exercises[0].sets[0].reps"))
            js("document.querySelector('[data-edit-set=\"0:0:rir\"]').click();document.getElementById('setValue').value='2';document.getElementById('saveSetValue').click();true")
            assertEquals("\"2\"", js("draft.exercises[0].sets[0].rir"))
            assertEquals("\"\"", js("document.getElementById('overlay').innerHTML"))
            js("document.querySelector('[data-edit-set=\"0:1:reps\"]').click();document.getElementById('setValue').value='11';document.getElementById('saveSetValue').click();true")
            assertEquals("\"11\"", js("draft.exercises[0].sets[1].reps"))
            js("document.querySelector('[data-edit-set=\"0:0:reps\"]').scrollIntoView({block:'center'});true")
            screenshot("compact-set-editor")
            screenshot(if (live) "live-plan" else "light-train")
            val lightForeground = js("getComputedStyle(document.body).color")
            scenario.onActivity { dark.value = true }
            screenshot("theme-transition")
            waitJs("JSON.stringify(getComputedStyle(document.body).color) !== ${JSONObject.quote(lightForeground)}")
            screenshot(if (live) "live-dark-plan" else "dark-train")
            val color = js("getComputedStyle(document.body).color")
            assertFalse(color.contains("rgb(0, 0, 0)"))
            if (live) {
                val tools = TemplateDataToolService(repository, runtime, conversations, listOf(manifest))
                val chat = conversations.createWithFirstUserMessage("请调用训练记录模板的 plan.current，先读取当前计划；然后调用 plan.generate，baseRevision 使用读取的 revision，targetDate 使用当前计划的 targetDate，要求是所有动作减轻强度、避免力竭。只生成候选，不要自动确认。", project)
                val service = ChatGenerationService(conversations, gateway, credentials, settings, profiles, scopedToolService = tools)
                withTimeout(300_000) { service.generateReply(chat) }
                assertTrue(repository.runs(access).first().count { it.actionId == "plan.generate" && it.status == "SUCCEEDED" } >= 2)
                assertEquals(1L, repository.currentVersion(access, "plans")!!.revision)
                js("document.getElementById('refresh').click();true")
                waitJs("!busy && candidates.length >= 2")
                screenshot("live-chat-candidate")
            } else {
                js("document.querySelector('[data-edit-set=\"0:0:reps\"]').click();document.getElementById('setValue').value='12';document.getElementById('saveSetValue').click();true")
                waitJs("savedDraft.includes('12')")
                js("document.getElementById('chat').click();true")
                val until = System.currentTimeMillis() + 15_000
                while (visible.value && System.currentTimeMillis() < until) Thread.sleep(100)
                assertFalse(visible.value)
                scenario.onActivity { visible.value = true }
                waitJs("typeof busy !== 'undefined' && !busy && draft?.exercises[0].sets[0].reps === '12'")
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
                val backUntil = System.currentTimeMillis() + 15_000
                while (visible.value && System.currentTimeMillis() < backUntil) Thread.sleep(100)
                assertFalse(visible.value)
                scenario.onActivity { visible.value = true }
                waitJs("typeof busy !== 'undefined' && !busy && draft?.exercises[0].sets[0].reps === '12'")
                failGeneration = true
                js("draft.exercises.forEach(a=>a.sets.forEach(s=>{s.reps='12';s.rir='2'}));render();document.getElementById('submit').click();true")
                waitJs("!busy && document.getElementById('status').textContent.includes('失败')")
                assertEquals(1, repository.list(access, "training").size)
                assertEquals("true", js("targetDate===tomorrow()"))
                assertEquals(1L, repository.currentVersion(access, "plans")!!.revision)
                failGeneration = false
                js("document.getElementById('generate').click();true")
                waitJs("!busy && !!document.getElementById('confirmPlan')")
                assertEquals(1, repository.list(access, "training").size)
                js("document.getElementById('confirmPlan').click();true")
                waitJs("!busy && current.revision===2 && draft===null")
                assertEquals("true", js("document.getElementById('content').textContent.includes('今天已有训练记录，这份新计划已启用') && !document.getElementById('submit')"))
                js("page='calendar';render();true")
                waitJs("document.getElementById('content').textContent.includes('12 次')")
                screenshot("dark-calendar")
                scenario.onActivity { dark.value = false }
                waitJs("JSON.stringify(getComputedStyle(document.body).color) === ${JSONObject.quote(lightForeground)}")
                screenshot("light-calendar")
                js("page='mine';render();true")
                screenshot("light-mine")
            }
        } finally {
            scenario.close()
            db.close()
        }
    }
}
