package com.dwl.mutcube.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID

private const val TAG = "SystemTtsFilePlayer"
private const val MAX_CHUNK_LENGTH = 1_200

/**
 * Uses the system TTS engine for synthesis, then plays the generated audio itself.
 *
 * Some Android engines report success from [TextToSpeech.speak] without routing audible output.
 * Synthesizing to a file also gives us deterministic completion/error callbacks and lets long
 * replies be split into engine-safe chunks.
 */
class SystemTtsFilePlayer(
    context: Context,
    private val enginePackage: String?,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheDirectory = File(context.cacheDir, "system_tts").apply { mkdirs() }
    private var player: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var sessionId: String? = null
    private val sessionFiles = linkedSetOf<File>()
    private val selectedVoices = mutableMapOf<String, String>()
    private var completion: (() -> Unit)? = null

    fun speak(
        markdownText: String,
        locale: Locale,
        voiceName: String?,
        speechRate: Float,
        onError: (String) -> Unit,
        onFinished: () -> Unit = {},
    ) {
        stop()
        val chunks = markdownText.toSpeechText().splitForSpeech()
        if (chunks.isEmpty()) return
        Log.i(TAG, "Starting TTS session with ${chunks.size} chunk(s), engine=$enginePackage")

        val currentSession = UUID.randomUUID().toString()
        sessionId = currentSession
        completion = onFinished
        var engine: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            Log.i(TAG, "TTS engine initialization completed with status=$status")
            val initializedEngine = engine
            if (sessionId != currentSession) {
                initializedEngine?.shutdown()
            } else if (status != TextToSpeech.SUCCESS || initializedEngine == null) {
                fail(currentSession, "系统朗读服务初始化失败", onError)
            } else {
                textToSpeech = initializedEngine
                val languageResult = initializedEngine.setLanguage(locale)
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    fail(currentSession, "系统朗读服务不支持当前语言", onError)
                } else {
                    initializedEngine.setSpeechRate(speechRate)
                    val voices = initializedEngine.voices.orEmpty()
                    val requestedName = voiceName?.takeIf(String::isNotBlank)
                        ?: selectedVoices[locale.toLanguageTag()]
                    val voice = voices.firstOrNull { it.name == requestedName }
                        ?: voices.filter { it.locale.language == locale.language }
                            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == locale.country }
                                .thenByDescending { it.quality }
                                .thenBy { it.isNetworkConnectionRequired }.thenBy { it.name }).firstOrNull()
                    voice?.let {
                        initializedEngine.voice = it
                        selectedVoices[locale.toLanguageTag()] = it.name
                        Log.i(TAG, "Selected voice=${it.name}, quality=${it.quality}")
                    }
                    synthesizeChunk(currentSession, chunks, index = 0, onError)
                }
            }
        }
        engine = enginePackage?.let { TextToSpeech(appContext, listener, it) }
            ?: TextToSpeech(appContext, listener)
        textToSpeech = engine
    }

    fun stop() {
        sessionId = null
        completion = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        sessionFiles.forEach(File::delete)
        sessionFiles.clear()
    }

    private fun synthesizeChunk(
        currentSession: String,
        chunks: List<String>,
        index: Int,
        onError: (String) -> Unit,
    ) {
        if (sessionId != currentSession || index !in chunks.indices) return
        val audioFile = File(cacheDirectory, "${currentSession}_$index.wav")
        sessionFiles += audioFile
        val utteranceId = "${currentSession}_$index"
        val engine = textToSpeech ?: run {
            fail(currentSession, "系统朗读服务尚未就绪", onError)
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (sessionId == currentSession) playChunk(currentSession, chunks, index, audioFile, onError)
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) = fail(currentSession, "系统朗读合成失败", onError)

            override fun onError(utteranceId: String?, errorCode: Int) {
                fail(currentSession, "系统朗读合成失败（错误码 $errorCode）", onError)
            }
        })

        val result = engine.synthesizeToFile(chunks[index], Bundle(), audioFile, utteranceId)
        if (result == TextToSpeech.ERROR) fail(currentSession, "无法启动系统朗读合成", onError)
    }

    private fun playChunk(
        currentSession: String,
        chunks: List<String>,
        index: Int,
        audioFile: File,
        onError: (String) -> Unit,
    ) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            fail(currentSession, "系统朗读没有生成有效音频", onError)
            return
        }
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    sessionFiles.remove(audioFile)
                    audioFile.delete()
                    if (sessionId == currentSession && index + 1 < chunks.size) {
                        synthesizeChunk(currentSession, chunks, index + 1, onError)
                    } else if (sessionId == currentSession) {
                        sessionId = null
                        textToSpeech?.shutdown()
                        textToSpeech = null
                        completion?.invoke()
                        completion = null
                    }
                }
                setOnErrorListener { mediaPlayer, what, extra ->
                    mediaPlayer.release()
                    if (player === mediaPlayer) player = null
                    fail(currentSession, "系统朗读播放失败（$what/$extra）", onError)
                    true
                }
                prepare()
                start()
                Log.i(TAG, "Playing TTS chunk ${index + 1}/${chunks.size}")
            }
        }.onFailure {
            Log.e(TAG, "Unable to play synthesized speech", it)
            fail(currentSession, it.message ?: "系统朗读播放失败", onError)
        }
    }

    private fun fail(currentSession: String, message: String, onError: (String) -> Unit) {
        Log.e(TAG, message)
        mainHandler.post {
            if (sessionId == currentSession) {
                stop()
                onError(message)
            }
        }
    }
}

private fun String.toSpeechText(): String = this
    .replace(Regex("```[\\s\\S]*?```"), " 代码块 ")
    .replace(Regex("`([^`]*)`"), "$1")
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
    .replace(Regex("[*_~>]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.splitForSpeech(): List<String> {
    if (isBlank()) return emptyList()
    val result = mutableListOf<String>()
    var remaining = trim()
    while (remaining.isNotEmpty()) {
        if (remaining.length <= MAX_CHUNK_LENGTH) {
            result += remaining
            break
        }
        val boundary = remaining
            .take(MAX_CHUNK_LENGTH + 1)
            .indexOfLast { it in "。！？!?；;，,、.\n " }
            .takeIf { it >= MAX_CHUNK_LENGTH / 2 }
            ?: MAX_CHUNK_LENGTH
        result += remaining.substring(0, boundary + 1).trim()
        remaining = remaining.substring(boundary + 1).trimStart()
    }
    return result.filter(String::isNotBlank)
}
