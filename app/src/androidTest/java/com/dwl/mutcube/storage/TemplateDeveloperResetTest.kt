package com.dwl.mutcube.storage

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.AbstractComposeView
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.ui.components.TemplateDeveloperReset
import com.dwl.mutcube.ui.components.TemplateResetTarget
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TemplateDeveloperResetTest {
    @Test fun realResetUiRequiresNameAndCancelDoesNotDelete() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        var activity: TemplateAcceptanceActivity? = null
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("重置验收")
            val repository = TemplateRepository(database)
            val access = TemplateAccessContext(project.value, "reset.fixture")
            repository.enableWithGrants(TemplateBindingEntity(project.value, access.templateId, "1", true),
                listOf(TemplateCollectionEntity(access.templateId, "facts", 1,
                    """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}""",
                    DataMutability.IMMUTABLE_HISTORY.name)), emptySet(), null)
            repository.append(access, "facts", "saved", """{"text":"隔离测试记录"}""", "USER")
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val deadline = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                Thread.sleep(100)
            }
            val target = TemplateResetTarget(project.value, access.templateId, "重置验收", "隔离模板")
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme { Surface { Column { TemplateDeveloperReset(true, listOf(target),
                    onPreview = { repository.previewDeveloperReset(access) },
                    onReset = { _, name -> repository.resetForDeveloper(access, name) }) } } }
            } }
            fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
                if (node == null) return null
                if (predicate(node)) return node
                return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it), predicate) }
            }
            fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
                val until = System.currentTimeMillis() + 10_000
                while (System.currentTimeMillis() < until) {
                    find(instrumentation.uiAutomation.rootInActiveWindow, predicate)?.let { return it }
                    Thread.sleep(100)
                }
                error("Expected reset UI node missing")
            }
            fun click(text: String) {
                Thread.sleep(400)
                var node = waitNode { it.text?.toString() == text }
                while (!node.isClickable) node = requireNotNull(node.parent)
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                Thread.sleep(400)
            }
            click("重置项目模板数据")
            click("重置验收 · 隔离模板")
            waitNode { it.text?.toString() == "永久重置模板数据" }
            click("取消")
            assertEquals(1, repository.previewDeveloperReset(access).records)
            click("重置项目模板数据")
            click("重置验收 · 隔离模板")
            val input = waitNode { it.isEditable }
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "重置验收")
            }))
            click("永久清除")
            waitNode { it.text?.toString()?.startsWith("已重置 重置验收") == true }
            assertEquals(0, repository.previewDeveloperReset(access).records)
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
            database.close()
        }
    }
}
