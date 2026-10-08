package com.dwl.mutcube.storage

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.template.core.ActionMode
import com.dwl.mutcube.ui.TemplateCapabilitiesPanel
import com.dwl.mutcube.ui.TemplateOperationCards
import com.dwl.mutcube.ui.templateResult
import com.dwl.mutcube.ui.theme.MutCubeTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TemplateChatUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun capabilitySelectionRealStatusAndResultNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var activity: TemplateAcceptanceActivity? = null
        val capability = TemplateChatCapability(TemplateDataToolService.toolName("mutcube.fitness", "plan.generate"),
            "mutcube.fitness", "训练记录", "plan.generate", "生成或调整训练计划", "请调整今天的训练计划", ActionMode.GENERATE)
        val call = MessageContentPart.ToolCall(MessagePartId("call"), "call", capability.toolName, "{}", ToolCallStatus.RUNNING)
        val parts = mutableStateOf<List<MessageContentPart>>(listOf(call))
        val panel = mutableStateOf(true)
        val theme = mutableStateOf(ThemeMode.LIGHT)
        var chosen: String? = null
        var opened: JSONObject? = null
        try {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${instrumentation.targetContext.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val until = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < until) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                Thread.sleep(100)
            }
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme(themeMode = theme.value) { Surface {
                    if (panel.value) TemplateCapabilitiesPanel(listOf(capability), { panel.value = false },
                        { chosen = it; panel.value = false })
                    else TemplateOperationCards(parts.value, listOf(capability), { opened = it })
                } }
            } }
            compose.onNodeWithText(capability.title).performClick()
            assertEquals(capability.example, chosen)
            compose.onNodeWithText("正在执行 · ${capability.title}").assertIsDisplayed()
            compose.onNodeWithText("查看并启用").assertDoesNotExist()
            val output = JSONObject().put("presentation", JSONObject().put("templateId", capability.templateId)
                .put("actionId", capability.actionId).put("collection", "plans").put("recordKey", "candidate")
                .put("title", capability.title).put("pendingConfirmation", true)).put("data", JSONObject().put("name", "胸和三头"))
            val result = MessageContentPart.ToolResult(MessagePartId("result"), "call", output.toString(), false)
            compose.runOnIdle { parts.value = listOf(call.copy(status = ToolCallStatus.SUCCEEDED), result) }
            compose.onNodeWithText("候选已生成，尚未启用。").assertIsDisplayed()
            compose.onNodeWithText("查看并启用").performClick()
            assertEquals("candidate", opened!!.getString("recordKey"))
            compose.runOnIdle { theme.value = ThemeMode.DARK }
            compose.onNodeWithText("已完成 · ${capability.title}").assertIsDisplayed()
            assertNull(templateResult(call, listOf(call, result.copy(output = output.toString().replace("plan.generate", "training.list")))))
            compose.runOnIdle { parts.value = listOf(call.copy(status = ToolCallStatus.FAILED), result.copy(isError = true, output = "授权已撤销")) }
            compose.onNodeWithText("执行失败 · ${capability.title}").performClick()
            compose.onNodeWithText("授权已撤销").assertIsDisplayed()
            compose.onNodeWithText("查看并启用").assertDoesNotExist()
        } finally {
            instrumentation.runOnMainSync { activity?.let { current ->
                fun dispose(view: View) {
                    if (view is AbstractComposeView) view.disposeComposition()
                    else if (view is ViewGroup) repeat(view.childCount) { dispose(view.getChildAt(it)) }
                }
                dispose(current.window.decorView)
                current.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
                current.finish()
            } }
        }
    }
}
