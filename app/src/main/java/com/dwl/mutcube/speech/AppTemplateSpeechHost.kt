package com.dwl.mutcube.speech

import android.content.Context
import android.os.SystemClock
import com.dwl.mutcube.storage.DisplaySettingsStore
import com.dwl.mutcube.storage.SpeechProvider
import com.dwl.mutcube.storage.SpeechService
import com.dwl.mutcube.storage.SpeechServiceSettingsStore
import com.dwl.mutcube.template.ui.TemplateSpeechHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.Locale

/** One template page session. No credential, audio file or microphone buffer crosses the WebView bridge. */
class AppTemplateSpeechHost(
    private val context: Context,
    private val settings: SpeechServiceSettingsStore,
    private val display: DisplaySettingsStore,
    private val service: SpeechService,
) : TemplateSpeechHost {
    private val player = SystemTtsFilePlayer(context, display.settings.value.ttsEnginePackage.ifBlank { null })
    private var systemInput: SystemSpeechInput? = null
    private var recorder: ContinuousSpeechRecorder? = null
    private var recognition: CompletableDeferred<String>? = null
    private var stopSignal: CompletableDeferred<Unit>? = null
    private var playback: CompletableDeferred<Unit>? = null
    private var playbackJob: Job? = null

    override suspend fun isConfigured(capability: String): Boolean = when (capability) {
        "speech.speak" -> settings.settings.value.ttsProvider == SpeechProvider.OPENAI_COMPATIBLE && service.hasConfiguredTts()
        "speech.recognize" -> settings.settings.value.asrProvider == SpeechProvider.OPENAI_COMPATIBLE && service.hasConfiguredAsr()
        else -> false
    }

    override suspend fun speak(text: String, onState: (String) -> Unit) {
        check(isConfigured("speech.speak")) { "请先在语音设置中配置可用的 TTS Provider 和模型" }
        require(text.isNotBlank() && text.length <= 10_000) { "朗读文字应为 1–10,000 字" }
        check(playback == null) { "已有朗读正在进行" }
        val finished = CompletableDeferred<Unit>()
        playback = finished
        playbackJob = currentCoroutineContext()[Job]
        try {
            onState("preparing")
            if (settings.settings.value.ttsProvider == SpeechProvider.SYSTEM) {
                val chosen = display.settings.value
                player.speak(text, chosen.ttsLanguageTag.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)
                    ?: Locale.getDefault(), chosen.ttsVoiceName, chosen.ttsSpeechRate,
                    onError = { finished.completeExceptionally(IllegalStateException(it)) },
                    onFinished = { finished.complete(Unit) })
                onState("playing")
                finished.await()
            } else {
                service.speak(text, { withContext(Dispatchers.Main.immediate) { onState("preparing") } },
                    { withContext(Dispatchers.Main.immediate) { onState("playing") } })
            }
            onState("finished")
        } finally {
            if (playback === finished) playback = null
            playbackJob = null
            player.stop()
        }
    }

    override fun stopSpeaking() {
        playback?.cancel()
        playbackJob?.cancel()
        player.stop()
    }

    override suspend fun recognize(language: String?, onState: (String, Float) -> Unit): String {
        check(isConfigured("speech.recognize")) { "请先在语音设置中配置可用的 ASR Provider 和模型" }
        require(language == null || language in setOf("auto", "en", "zh")) { "不支持的语音识别语言" }
        check(recognition == null) { "已有语音输入正在进行" }
        val result = CompletableDeferred<String>()
        recognition = result
        try {
            if (settings.settings.value.asrProvider == SpeechProvider.SYSTEM) {
                val input = SystemSpeechInput(context)
                systemInput = input
                val recognitionLanguage = language ?: display.settings.value.asrLanguageTag.ifBlank { Locale.getDefault().toLanguageTag() }
                onState("recording", 0f)
                input.start(recognitionLanguage, settings.settings.value.asrSilenceSeconds * 1_000L,
                    onLevel = { onState("recording", it) }, onProcessing = { onState("transcribing", 0f) },
                    onResult = { result.complete(it) },
                    onError = { result.completeExceptionally(IllegalStateException(it)) })
                return result.await()
            }
            val capture = ContinuousSpeechRecorder.start(context)
            recorder = capture
            val stop = CompletableDeferred<Unit>()
            stopSignal = stop
            val detector = SpeechPauseDetector(settings.settings.value.asrSilenceSeconds * 1_000L)
            val started = SystemClock.elapsedRealtime()
            while (!stop.isCompleted) {
                capture.checkHealthy()
                onState("recording", capture.amplitude)
                if (detector.shouldFinish(SystemClock.elapsedRealtime(), capture.decibels) ||
                    SystemClock.elapsedRealtime() - started >= 180_000) stop.complete(Unit)
                else delay(120)
            }
            onState("transcribing", 0f)
            val file = withContext(Dispatchers.IO) { capture.stop() }
            recorder = null
            try {
                return service.transcribe(file, language ?: display.settings.value.asrLanguageTag)
            } finally { file.delete() }
        } finally {
            systemInput?.close()
            systemInput = null
            recorder?.discard()
            recorder = null
            stopSignal = null
            if (recognition === result) recognition = null
        }
    }

    override fun stopRecognition() {
        systemInput?.stop()
        stopSignal?.complete(Unit)
    }

    override fun cancelRecognition() {
        systemInput?.cancel()
        recognition?.cancel(CancellationException("用户取消录音"))
        stopSignal?.cancel()
    }

    fun close() { stopSpeaking(); cancelRecognition() }
}
