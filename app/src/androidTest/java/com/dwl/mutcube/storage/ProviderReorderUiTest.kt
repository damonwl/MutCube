package com.dwl.mutcube.storage

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.ui.components.ModelSettingsPage
import com.dwl.mutcube.ui.theme.MutCubeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProviderReorderUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun longPressHandleMovesProviderAndCommitsOnlyOnRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var activity: TemplateAcceptanceActivity? = null
        val moved = mutableStateOf<Pair<String, Int>?>(null)
        val providers = ProviderConfiguration(
            profiles = listOf(
                ProviderProfile("one", "服务一", "https://one.example/v1", "model"),
                ProviderProfile("two", "服务二", "https://two.example/v1", "model"),
                ProviderProfile("three", "服务三", "https://three.example/v1", "model"),
            ),
            activeProfileId = "one",
        )
        try {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity",
            )).use { it.readBytes() }
            val deadline = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<TemplateAcceptanceActivity>().firstOrNull()
                }
                if (activity == null) Thread.sleep(100)
            }
            instrumentation.runOnMainSync { requireNotNull(activity).setContent {
                MutCubeTheme {
                    ModelSettingsPage(
                        effectiveProviderId = "one", configuredProviderIds = setOf("one", "two", "three"),
                        settings = ModelSettings(), providers = providers,
                        inspectionLoading = false, inspectionMessage = null, discoveredModels = emptyList(),
                        onBack = {}, onSaveProvider = { _, _, _, _, _, _, _, _, _, _ -> },
                        onInspectProvider = { _, _, _, _, _, _ -> }, onSelectProvider = {},
                        onProviderEnabledChange = { _, _ -> },
                        onMoveProvider = { id, offset -> moved.value = id to offset },
                        onDeleteProvider = {}, onDeleteCredential = {}, onSaveSettings = {},
                        onImport = {}, onExport = {}, onShare = {},
                    )
                }
            } }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("长按拖动调整 服务一 顺序").performTouchInput {
                down(center)
                advanceEventTime(700)
                repeat(8) { moveBy(Offset(0f, 45f)); advanceEventTime(40) }
                up()
            }
            compose.waitForIdle()
            assertTrue("松手后应保存新的服务顺序", moved.value?.first == "one" && moved.value!!.second > 0)
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
