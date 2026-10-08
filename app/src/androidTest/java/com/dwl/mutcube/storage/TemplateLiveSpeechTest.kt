package com.dwl.mutcube.storage

import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dwl.mutcube.TemplateAcceptanceActivity
import com.dwl.mutcube.core.security.AndroidKeystoreCredentialStore
import com.dwl.mutcube.speech.AppTemplateSpeechHost
import com.dwl.mutcube.ui.theme.MutCubeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in human-in-the-loop test. Uses the app's configured Provider, but never prints its key or raw audio. */
class TemplateLiveSpeechTest {
    @Test fun configuredProviderTtsAndPausedAsr() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviderSpeech") == "true")
        val recognitionLanguage = InstrumentationRegistry.getArguments().getString("recognitionLanguage") ?: "zh"
        require(recognitionLanguage in setOf("en", "zh"))
        val context = instrumentation.targetContext
        val speechSettings = SpeechServiceSettingsStore(context)
        assertTrue(speechSettings.settings.value.ttsProvider == SpeechProvider.OPENAI_COMPATIBLE)
        assertTrue(speechSettings.settings.value.asrProvider == SpeechProvider.OPENAI_COMPATIBLE)
        assumeTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val host = AppTemplateSpeechHost(context, speechSettings, DisplaySettingsStore(context),
            SpeechService(context, speechSettings, AndroidKeystoreCredentialStore(context), AndroidProviderProfileStore(context)))
        assertTrue("模板 TTS 服务未配置完成", host.isConfigured("speech.speak"))
        assertTrue("模板 ASR 服务未配置完成", host.isConfigured("speech.recognize"))
        var activity: TemplateAcceptanceActivity? = null
        var status by mutableStateOf("准备真机语音验收…")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -n ${context.packageName}/com.dwl.mutcube.TemplateAcceptanceActivity")).use { it.readBytes() }
            val until = System.currentTimeMillis() + 20_000
            while (activity == null && System.currentTimeMillis() < until) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TemplateAcceptanceActivity>().firstOrNull() }
                if (activity == null) Thread.sleep(100)
            }
            val target = requireNotNull(activity)
            instrumentation.runOnMainSync { target.setContent {
                MutCubeTheme { Surface { Column(Modifier.fillMaxSize().padding(28.dp)) {
                    Text("MutCube 真机语音验收")
                    Text(status)
                } } }
            } }
            withContext(Dispatchers.Main.immediate) {
                status = "正在播放 Provider 朗读，请听是否清晰…"
                withTimeout(45_000) { host.speak("MutCube 语音验收。朗读结束后，请看屏幕提示开始说话。") {
                    status = "朗读状态：$it"
                } }
                status = "朗读完成。五秒后开始${if (recognitionLanguage == "en") "英语" else "中文"}录音，请准备好。"
                delay(5_000)
                var peakLevel = 0f
                val transcript = withTimeout(90_000) { host.recognize(recognitionLanguage) { state, level ->
                    peakLevel = maxOf(peakLevel, level)
                    status = if (state == "recording") "正在录音，请说${if (recognitionLanguage == "en") "一段英语" else "两段中文"}。音量 ${(level * 100).toInt()}%"
                        else "正在转写，请稍候…"
                } }
                status = "转写完成：$transcript"
                println("LIVE_ASR_RESULT=language:$recognitionLanguage,length:${transcript.length},peak:${(peakLevel * 100).toInt()}")
                assertTrue("真实录音未检测到音量波动", peakLevel > 0.01f)
                assertTrue("真实 ASR 未返回文字", transcript.isNotBlank())
                if (recognitionLanguage == "en") assertTrue("英语 ASR 未返回英文字符", transcript.any { it in 'a'..'z' || it in 'A'..'Z' })
                // The model may normalize or paraphrase the scripted phrase; inspect accuracy with the speaker.
                delay(8_000)
            }
        } finally {
            host.close()
            instrumentation.runOnMainSync { activity?.setContent { }; activity?.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
