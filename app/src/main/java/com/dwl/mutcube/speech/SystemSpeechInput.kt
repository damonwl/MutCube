package com.dwl.mutcube.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Optional system recognition path. Never requires app-managed Provider credentials. */
class SystemSpeechInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val lifecycle = RecognitionSessionLifecycle { recognizer?.cancel() }
    private val session get() = lifecycle.session
    private val active get() = lifecycle.active
    private val handler = Handler(Looper.getMainLooper())
    private var stopTimeout: Runnable? = null
    private var timeoutError: (() -> Unit)? = null

    fun start(
        language: String,
        silenceMillis: Long,
        onLevel: (Float) -> Unit,
        onProcessing: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (active) cancel()
        lifecycle.begin()
        val token = session
        fun fail(message: String) {
            if (session != token) return
            finishSession()
            onError(message)
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("设备没有可用的系统语音识别服务")
            return
        }
        timeoutError = { fail("系统语音识别未返回结果，请重试") }
        runCatching {
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            engine.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onRmsChanged(rmsdB: Float) {
                        if (session == token && rmsdB.isFinite()) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                    }
                    override fun onEndOfSpeech() {
                        if (session == token) {
                            onProcessing()
                            scheduleStopTimeout()
                        }
                    }
                    override fun onError(error: Int) {
                        val description = when (error) {
                            1 -> "系统语音识别网络超时（ERROR_NETWORK_TIMEOUT，代码 1）"
                            2 -> "系统语音识别网络失败（ERROR_NETWORK，代码 2）"
                            6, 7 -> "没有识别到清晰语音（代码 $error）"
                            9 -> "系统语音识别缺少权限（代码 9）"
                            11 -> "系统语音识别服务连接断开（ERROR_SERVER_DISCONNECTED，代码 11）"
                            else -> "系统语音识别失败（代码 $error）"
                        }
                        if (session != token) return
                        if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                            val disconnected = recognizer
                            recognizer = null
                            handler.post { disconnected?.destroy() }
                        }
                        fail(description)
                    }
                    override fun onResults(results: Bundle?) {
                        if (session != token) return
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        finishSession()
                        if (text.isBlank()) onError("系统语音识别未返回文字") else onResult(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMillis)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMillis)
                })
            }
        }.onFailure {
            recognizer?.cancel()
            fail("无法启动系统语音识别：${it.message}")
        }
    }

    fun stop() {
        if (!active) return
        recognizer?.stopListening()
        scheduleStopTimeout()
    }

    private fun scheduleStopTimeout() {
        stopTimeout?.let(handler::removeCallbacks)
        stopTimeout = Runnable {
            if (active) {
                recognizer?.cancel()
                timeoutError?.invoke()
            }
        }.also { handler.postDelayed(it, 30_000) }
    }

    /** A final result already ended recognition. Do not cancel or unbind a healthy service. */
    private fun finishSession() {
        lifecycle.complete()
        stopTimeout?.let(handler::removeCallbacks)
        stopTimeout = null
        timeoutError = null
    }

    fun cancel() {
        lifecycle.cancel()
        stopTimeout?.let(handler::removeCallbacks)
        stopTimeout = null
        timeoutError = null
    }

    fun close() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
