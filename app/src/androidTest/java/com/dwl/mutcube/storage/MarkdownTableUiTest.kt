package com.dwl.mutcube.storage

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.ui.components.MarkdownMessage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkdownTableUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun wideTableScrollsLocallyAndPreservesOffsetDuringStreamingAndThemeChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var activity: TemplateAcceptanceActivity? = null
        val description = "这是一段包含较长说明的表格内容，不应该被挤压成很窄的一列。".repeat(4)
        val prefix = "普通正文保持屏幕内换行。\n\n| 次数 | 说明 | 补充说明 |\n| --- | --- | --- |\n| 12 | **$description** | [查看详情](https://example.com) $description |"
        val markdown = mutableStateOf(prefix)
        val theme = mutableStateOf(ThemeMode.LIGHT)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val deadline = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                Thread.sleep(100)
            }
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme(themeMode = theme.value) { Surface { Column { MarkdownMessage(markdown.value) } } }
            } }
            compose.waitForIdle()
            val table = compose.onNodeWithContentDescription("表格，左右滑动查看")
            fun range() = table.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            assertTrue(range().maxValue() > 0f)
            table.performTouchInput { swipeLeft() }
            compose.waitForIdle()
            val offset = range().value()
            assertTrue(offset > 0f)
            compose.runOnIdle { markdown.value = prefix + "\n| 10 | 新生成的一行 | 保留原来的横向滚动位置 |" }
            compose.waitForIdle()
            assertEquals(offset, range().value(), 1f)
            compose.runOnIdle { theme.value = ThemeMode.DARK }
            compose.waitForIdle()
            assertEquals(offset, range().value(), 1f)
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
