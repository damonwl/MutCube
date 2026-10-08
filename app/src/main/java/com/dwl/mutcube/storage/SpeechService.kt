package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.dwl.mutcube.core.ai.ProviderProfileStore
import com.dwl.mutcube.core.ai.ProviderProtocol
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.core.ai.AsrProtocol
import com.dwl.mutcube.core.ai.TtsProtocol
import android.util.Base64
import com.dwl.mutcube.core.ai.SpeechProtocols
import com.dwl.mutcube.core.ai.SpeechText
import com.dwl.mutcube.speech.playSpeechSegments
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import kotlinx.coroutines.*
import okhttp3.Response
import java.security.MessageDigest
import java.io.RandomAccessFile
import com.dwl.mutcube.speech.wavHeader

enum class SpeechProvider { SYSTEM, OPENAI_COMPATIBLE }

data class SpeechServiceSettings(
    val ttsProvider: SpeechProvider = SpeechProvider.SYSTEM,
    val ttsBaseUrl: String = "https://api.openai.com/v1",
    val ttsModel: String = "gpt-4o-mini-tts",
    val ttsVoice: String = "alloy",
    val asrProvider: SpeechProvider = SpeechProvider.SYSTEM,
    val asrBaseUrl: String = "https://api.openai.com/v1",
    val asrModel: String = "gpt-4o-mini-transcribe",
    val ttsProfileId: String = "",
    val asrProfileId: String = "",
    val asrSilenceSeconds: Int = 5,
)

class SpeechServiceSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings = state.asStateFlow()

    fun write(value: SpeechServiceSettings) {
        val normalized = value.copy(
            ttsBaseUrl = value.ttsBaseUrl.trim().trimEnd('/'),
            ttsModel = value.ttsModel.trim(),
            ttsVoice = value.ttsVoice.trim(),
            asrBaseUrl = value.asrBaseUrl.trim().trimEnd('/'),
            asrModel = value.asrModel.trim(),
        )
        validate(normalized)
        preferences.edit()
            .putString("tts_provider", normalized.ttsProvider.name)
            .putString("tts_base_url", normalized.ttsBaseUrl)
            .putString("tts_model", normalized.ttsModel)
            .putString("tts_voice", normalized.ttsVoice)
            .putString("asr_provider", normalized.asrProvider.name)
            .putString("asr_base_url", normalized.asrBaseUrl)
            .putString("asr_model", normalized.asrModel)
            .putString("tts_profile_id", normalized.ttsProfileId)
            .putString("asr_profile_id", normalized.asrProfileId)
            .putInt("asr_silence_seconds", normalized.asrSilenceSeconds)
            .apply()
        state.value = normalized
    }

    private fun load() = SpeechServiceSettings(
        ttsProvider = enum("tts_provider"),
        ttsBaseUrl = preferences.getString("tts_base_url", "https://api.openai.com/v1").orEmpty(),
        ttsModel = preferences.getString("tts_model", "gpt-4o-mini-tts").orEmpty(),
        // Repair the legacy OpenAI voice default copied into the official MiMo configuration.
        ttsVoice = preferences.getString("tts_voice", "alloy").orEmpty().let { voice ->
            if (voice == "alloy" && preferences.getString("tts_base_url", "").orEmpty().trimEnd('/') == "https://api.xiaomimimo.com/v1") "mimo_default" else voice
        },
        asrProvider = enum("asr_provider"),
        asrBaseUrl = preferences.getString("asr_base_url", "https://api.openai.com/v1").orEmpty(),
        asrModel = preferences.getString("asr_model", "gpt-4o-mini-transcribe").orEmpty(),
        ttsProfileId = preferences.getString("tts_profile_id", "").orEmpty(),
        asrProfileId = preferences.getString("asr_profile_id", "").orEmpty(),
        asrSilenceSeconds = preferences.getInt("asr_silence_seconds", 5).coerceIn(2, 10),
    )

    private fun enum(key: String) = runCatching {
        SpeechProvider.valueOf(preferences.getString(key, SpeechProvider.SYSTEM.name).orEmpty())
    }.getOrDefault(SpeechProvider.SYSTEM)

    companion object {
        const val PREFERENCES = "speech_service"
        fun validate(value: SpeechServiceSettings) {
            require(value.asrSilenceSeconds in 2..10) { "停顿时间应为 2–10 秒" }
            if (value.ttsProvider == SpeechProvider.OPENAI_COMPATIBLE) {
                validateEndpoint(value.ttsBaseUrl)
                require(value.ttsModel.isNotBlank() && value.ttsVoice.isNotBlank()) { "TTS 模型和音色不能为空" }
            }
            if (value.asrProvider == SpeechProvider.OPENAI_COMPATIBLE) {
                validateEndpoint(value.asrBaseUrl)
                require(value.asrModel.isNotBlank()) { "ASR 模型不能为空" }
            }
        }

        private fun validateEndpoint(value: String) {
            val uri = URI(value)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "语音服务地址必须使用 HTTPS" }
        }
    }
}

class SpeechService(
    context: Context,
    private val settingsStore: SpeechServiceSettingsStore,
    private val credentialStore: CredentialStore,
    private val providerStore: ProviderProfileStore,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS).build(),
) {
    private val cacheDir = context.applicationContext.cacheDir

    private fun speechCacheFile(baseUrl: String, key: String, settings: SpeechServiceSettings, protocol: TtsProtocol, text: String): File {
        val cache = File(cacheDir, "network_tts").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest(listOf(baseUrl, key, settings.ttsModel, settings.ttsVoice, protocol.name, text).joinToString("\u0000").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cache, "$hash.${if (protocol == TtsProtocol.CHAT_AUDIO) "wav" else "mp3"}")
    }

    private fun validCache(file: File) = file.isFile && file.length() > 0 && System.currentTimeMillis() - file.lastModified() < 86_400_000

    private fun trimSpeechCache() {
        val directory = File(cacheDir, "network_tts")
        val files = directory.listFiles().orEmpty().filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.name.matches(Regex("[a-f0-9]{64}\\.(wav|mp3)")) }.sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size > 64L * 1024 * 1024 || System.currentTimeMillis() - file.lastModified() >= 86_400_000) {
                val length = file.length()
                if (file.delete()) size -= length
            }
        }
    }

    private suspend fun <T> execute(request: Request, block: suspend (Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { withContext(Dispatchers.IO) { call.execute().use { block(it) } } }
        finally { cancellation.cancel() }
    }

    suspend fun speak(text: String, onPreparing: suspend () -> Unit, onPlaying: suspend () -> Unit) {
        val settings = settingsStore.settings.value
        val profile = settings.ttsProfileId.takeIf(String::isNotBlank)?.let { resolveSpeechProfile(providerStore.read(), it, settings.ttsModel) }
        val streaming = profile?.models?.firstOrNull { it.id == settings.ttsModel }?.ttsStreaming ?: profile?.ttsStreaming ?: false
        val segments = SpeechText.segments(text)
        if (segments.isEmpty()) return
        require(profile?.models?.firstOrNull { it.id == settings.ttsModel }?.capabilities?.speechSynthesis != com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) { "此模型不支持语音合成" }
        withContext(Dispatchers.IO) { trimSpeechCache() }
        onPreparing()
        if (streaming && profile != null && SpeechProtocols.resolveTts(profile, settings.ttsModel) == TtsProtocol.CHAT_AUDIO) {
            var started = false
            try {
                for (segment in segments) streamSpeech(segment) { started = true; onPlaying() }
                return
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                // Never replay the start of an utterance after any PCM has been played.
                if (started) throw error
                currentCoroutineContext().ensureActive()
            }
        }
        playSpeechSegments(segments, { synthesize(it) }, onPlaying)
    }

    private suspend fun streamSpeech(text: String, onPlaying: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.value
        val (baseUrl, key) = connection(settings.ttsProfileId, settings.ttsModel, settings.ttsBaseUrl, TTS_KEY)
        val target = speechCacheFile(baseUrl, key, settings, TtsProtocol.CHAT_AUDIO, text)
        if (validCache(target)) {
            playSpeechSegments(listOf(text), { target }, onPlaying)
            return@withContext
        }
        val request = Request.Builder().url("$baseUrl/chat/completions").header("Authorization", "Bearer $key")
            .post(SpeechProtocols.chatSpeechRequest(settings.ttsModel, text, settings.ttsVoice, true).toRequestBody(JSON)).build()
        execute(request) { response ->
            require(response.isSuccessful) { "TTS 服务返回 HTTP ${response.code}" }
            val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(9_600))
                .build()
            coroutineScope {
                val temporary = File(target.parentFile, "${UUID.randomUUID()}.part")
                val output = RandomAccessFile(temporary, "rw")
                output.write(wavHeader(0, 24_000))
                val stopper = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { runCatching { track.pause(); track.flush() } }
                }
                try {
                    track.play()
                    var total = 0L
                    var pending = byteArrayOf()
                    var notified = false
                    var completed = false
                    val source = response.body.source()
                    val data = StringBuilder()
                    suspend fun consume(): Boolean {
                        val event = data.toString(); data.clear()
                        if (event == "[DONE]") { completed = true; return false }
                        if (event.isBlank()) return true
                        if (SpeechProtocols.speechFinished(event)) completed = true
                        val encoded = SpeechProtocols.speechChunk(event) ?: return true
                        val bytes = pending + Base64.decode(encoded, Base64.DEFAULT)
                        val length = bytes.size - bytes.size % 2
                        pending = bytes.copyOfRange(length, bytes.size)
                        if (length > 0 && !notified) { notified = true; onPlaying() }
                        var offset = 0
                        while (offset < length) {
                            currentCoroutineContext().ensureActive()
                            val written = track.write(bytes, offset, length - offset, AudioTrack.WRITE_BLOCKING)
                            require(written > 0) { "流式音频播放失败" }
                            offset += written; total += written
                            output.write(bytes, offset - written, written)
                        }
                        return true
                    }
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (line.isEmpty()) { if (!consume()) break }
                        else if (line.startsWith("data:")) { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trimStart()) }
                    }
                    if (data.isNotEmpty()) consume()
                    require(completed && total > 0 && pending.isEmpty()) { "TTS 服务未返回完整 PCM 音频" }
                    withTimeout(30_000) {
                        while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < total / 2) delay(20)
                    }
                    output.seek(0); output.write(wavHeader(total.toInt(), 24_000)); output.close()
                    currentCoroutineContext().ensureActive()
                    require(temporary.renameTo(target)) { "音频缓存保存失败" }
                } finally { withContext(NonCancellable) { stopper.cancelAndJoin(); track.release(); output.close(); temporary.delete() } }
            }
        }
    }

    suspend fun save(settings: SpeechServiceSettings, ttsKey: String, asrKey: String) {
        settingsStore.write(settings)
        ttsKey.takeIf(String::isNotBlank)?.let { credentialStore.write(TTS_KEY, it.trim()) }
        asrKey.takeIf(String::isNotBlank)?.let { credentialStore.write(ASR_KEY, it.trim()) }
    }

    suspend fun deleteTtsKey() = credentialStore.delete(TTS_KEY)
    suspend fun deleteAsrKey() = credentialStore.delete(ASR_KEY)

    suspend fun hasConfiguredTts(): Boolean = runCatching {
        val settings = settingsStore.settings.value
        if (settings.ttsProvider != SpeechProvider.OPENAI_COMPATIBLE) return@runCatching false
        SpeechServiceSettingsStore.validate(settings)
        connection(settings.ttsProfileId, settings.ttsModel, settings.ttsBaseUrl, TTS_KEY)
        if (settings.ttsProfileId.isNotBlank()) {
            val profile = resolveSpeechProfile(providerStore.read(), settings.ttsProfileId, settings.ttsModel)
            if (profile.models.firstOrNull { it.id == settings.ttsModel }?.capabilities?.speechSynthesis ==
                com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) return@runCatching false
        }
        true
    }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)

    suspend fun hasConfiguredAsr(): Boolean = runCatching {
        val settings = settingsStore.settings.value
        if (settings.asrProvider != SpeechProvider.OPENAI_COMPATIBLE) return@runCatching false
        SpeechServiceSettingsStore.validate(settings)
        connection(settings.asrProfileId, settings.asrModel, settings.asrBaseUrl, ASR_KEY)
        if (settings.asrProfileId.isNotBlank()) {
            val profile = resolveSpeechProfile(providerStore.read(), settings.asrProfileId, settings.asrModel)
            if (profile.models.firstOrNull { it.id == settings.asrModel }?.capabilities?.speechRecognition ==
                com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) return@runCatching false
        }
        true
    }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)

    private suspend fun connection(profileId: String, modelId: String, customUrl: String, customKey: String): Pair<String, String> {
        if (profileId.isBlank()) return customUrl.trimEnd('/') to
            (credentialStore.read(customKey)?.takeIf(String::isNotBlank) ?: error("请先保存语音服务 API Key"))
        val profile = resolveSpeechProfile(providerStore.read(), profileId, modelId)
        val key = credentialStore.read(profile.id)?.takeIf(String::isNotBlank) ?: error("请在 Provider 设置中保存 API Key")
        return profile.baseUrl.trimEnd('/') to key
    }

    suspend fun synthesize(text: String): File = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.value
        require(settings.ttsProvider == SpeechProvider.OPENAI_COMPATIBLE)
        val (baseUrl, key) = connection(settings.ttsProfileId, settings.ttsModel, settings.ttsBaseUrl, TTS_KEY)
        val profile = settings.ttsProfileId.takeIf(String::isNotBlank)?.let { resolveSpeechProfile(providerStore.read(), it, settings.ttsModel) }
        val protocol = profile?.let { SpeechProtocols.resolveTts(it, settings.ttsModel) } ?: TtsProtocol.AUDIO_SPEECH
        val cache = File(cacheDir, "network_tts").apply { mkdirs() }
        val target = speechCacheFile(baseUrl, key, settings, protocol, text)
        if (validCache(target)) return@withContext target
        if (settings.ttsProfileId.isNotBlank()) {
            val profile = resolveSpeechProfile(providerStore.read(), settings.ttsProfileId, settings.ttsModel)
            require(profile.models.firstOrNull { it.id == settings.ttsModel }?.capabilities?.speechSynthesis != com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) { "此模型已声明不支持语音合成，请在语音设置中重新选择" }
        }
        val body = if (protocol == TtsProtocol.CHAT_AUDIO) SpeechProtocols.chatSpeechRequest(settings.ttsModel, text, settings.ttsVoice).toRequestBody(JSON)
        else JSONObject().put("model", settings.ttsModel).put("voice", settings.ttsVoice)
            .put("input", text).put("response_format", "mp3").toString().toRequestBody(JSON)
        val path = if (protocol == TtsProtocol.CHAT_AUDIO) "chat/completions" else "audio/speech"
        val request = Request.Builder().url("$baseUrl/$path")
            .header("Authorization", "Bearer $key").post(body).build()
        execute(request) { response ->
            require(response.isSuccessful) { "TTS 服务返回 HTTP ${response.code}：${response.body.string().replace(key, "[REDACTED]").take(240)}" }
            val temporary = File(cache, "${UUID.randomUUID()}.part")
            try {
            if (protocol == TtsProtocol.CHAT_AUDIO) {
                val bytes = Base64.decode(SpeechProtocols.speechBase64(response.body.string()), Base64.DEFAULT)
                require(bytes.isNotEmpty()) { "TTS 服务返回空音频" }
                temporary.outputStream().use { it.write(bytes) }
            } else response.body.byteStream().use { input -> temporary.outputStream().use(input::copyTo) }
            currentCoroutineContext().ensureActive()
            require(temporary.length() > 0 && temporary.renameTo(target)) { "音频缓存保存失败" }
            } finally { temporary.delete() }
            target
        }
    }

    suspend fun transcribe(audio: File, language: String): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.value
        require(settings.asrProvider == SpeechProvider.OPENAI_COMPATIBLE)
        val (baseUrl, key) = connection(settings.asrProfileId, settings.asrModel, settings.asrBaseUrl, ASR_KEY)
        val profile = settings.asrProfileId.takeIf(String::isNotBlank)?.let {
            resolveSpeechProfile(providerStore.read(), it, settings.asrModel)
        }
        val protocol = profile?.let { SpeechProtocols.resolveAsr(it, settings.asrModel) } ?: AsrProtocol.AUDIO_TRANSCRIPTIONS
        require(profile?.models?.firstOrNull { it.id == settings.asrModel }?.capabilities?.speechRecognition != com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) { "此模型已声明不支持语音识别，请在语音设置中重新选择" }
        require(audio.length() in 1..MAX_AUDIO_BYTES) { "录音文件为空或超过 25 MB" }
        val body = if (protocol == AsrProtocol.CHAT_INPUT_AUDIO) {
            // MiMo limits the Base64 payload, not the original WAV size.
            require(((audio.length() + 2) / 3) * 4 <= 10L * 1024 * 1024) { "聊天式 ASR 的 Base64 音频不能超过 10 MB，请缩短录音" }
            SpeechProtocols.chatAudioRequest(settings.asrModel, Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP), language)
                .toRequestBody(JSON)
        } else MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", settings.asrModel)
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/wav".toMediaType()))
            .apply { language.takeIf { it.isNotBlank() && it != "auto" }?.let { addFormDataPart("language", it.substringBefore('-')) } }
            .build()
        val path = if (protocol == AsrProtocol.CHAT_INPUT_AUDIO) "chat/completions" else "audio/transcriptions"
        val request = Request.Builder().url("$baseUrl/$path")
            .header("Authorization", "Bearer $key").post(body).build()
        execute(request) { response ->
            require(response.isSuccessful) { "ASR 服务返回 HTTP ${response.code}：${response.body.string().replace(key, "[REDACTED]").take(240)}" }
            SpeechProtocols.transcript(protocol, response.body.string())
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val TTS_KEY = "speech.tts"
        private const val ASR_KEY = "speech.asr"
        private const val MAX_AUDIO_BYTES = 25L * 1024 * 1024
    }
}

internal fun resolveSpeechProfile(configuration: ProviderConfiguration, profileId: String, modelId: String): ProviderProfile {
    val profile = configuration.profiles.firstOrNull { it.id == profileId }
        ?: error("语音 Provider 已被删除，请重新选择")
    require(profile.enabled) { "语音 Provider 已停用，请启用或重新选择" }
    require(profile.protocol == ProviderProtocol.OPENAI_CHAT_COMPLETIONS) { "当前语音接口仅支持 OpenAI 兼容 Provider" }
    require(profile.models.any { it.id == modelId } || profile.modelId == modelId) { "语音模型已被移除，请重新选择" }
    return profile
}
