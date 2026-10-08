package com.dwl.mutcube.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class StartupMode { LAST_CONVERSATION, NEW_CONVERSATION }
enum class ChatBackground { DEFAULT, WARM, COOL }
enum class BubbleStyle { STANDARD, TONAL, MINIMAL }

data class DisplaySettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1f,
    val showMessageTime: Boolean = false,
    val autoScroll: Boolean = true,
    val startupMode: StartupMode = StartupMode.LAST_CONVERSATION,
    val enterToSend: Boolean = false,
    val completionVibration: Boolean = true,
    val completionNotification: Boolean = false,
    val followUpSuggestions: Boolean = true,
    val autoReadReplies: Boolean = false,
    val ttsSpeechRate: Float = 1f,
    val ttsEnginePackage: String = "",
    val ttsVoiceName: String = "",
    val ttsLanguageTag: String = "",
    val asrLanguageTag: String = "",
    val asrAutoSend: Boolean = false,
    val developerMode: Boolean = false,
    val chatBackground: ChatBackground = ChatBackground.DEFAULT,
    val bubbleStyle: BubbleStyle = BubbleStyle.STANDARD,
    val showMessageAvatars: Boolean = false,
    val showModelLabel: Boolean = true,
    val userNickname: String = "",
    val userAvatarFile: String = "",
) {
    val userDisplayName: String get() = userNickname.trim().ifBlank { "用户" }
}

class DisplaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings = state.asStateFlow()

    fun write(value: DisplaySettings) {
        val normalized = value.copy(
            textScale = value.textScale.coerceIn(0.85f, 1.3f),
            ttsSpeechRate = value.ttsSpeechRate.coerceIn(0.5f, 2f),
            userNickname = value.userNickname.trim(),
        )
        preferences.edit()
            .putString("theme", normalized.themeMode.name)
            .putFloat("text_scale", normalized.textScale)
            .putBoolean("show_message_time", normalized.showMessageTime)
            .putBoolean("auto_scroll", normalized.autoScroll)
            .putString("startup_mode", normalized.startupMode.name)
            .putBoolean("enter_to_send", normalized.enterToSend)
            .putBoolean("completion_vibration", normalized.completionVibration)
            .putBoolean("completion_notification", normalized.completionNotification)
            .putBoolean("follow_up_suggestions", normalized.followUpSuggestions)
            .putBoolean("auto_read_replies", normalized.autoReadReplies)
            .putFloat("tts_speech_rate", normalized.ttsSpeechRate)
            .putString("tts_engine_package", normalized.ttsEnginePackage)
            .putString("tts_voice_name", normalized.ttsVoiceName)
            .putString("tts_language_tag", normalized.ttsLanguageTag)
            .putString("asr_language_tag", normalized.asrLanguageTag)
            .putBoolean("asr_auto_send", normalized.asrAutoSend)
            .putBoolean("developer_mode", normalized.developerMode)
            .putString("chat_background", normalized.chatBackground.name)
            .putString("bubble_style", normalized.bubbleStyle.name)
            .putBoolean("show_message_avatars", normalized.showMessageAvatars)
            .putBoolean("show_model_label", normalized.showModelLabel)
            .putString("user_nickname", normalized.userNickname)
            .putString("user_avatar_file", normalized.userAvatarFile)
            .apply()
        state.value = normalized
    }

    private fun load() = DisplaySettings(
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme", ThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemeMode.SYSTEM),
        textScale = preferences.getFloat("text_scale", 1f).coerceIn(0.85f, 1.3f),
        showMessageTime = preferences.getBoolean("show_message_time", false),
        autoScroll = preferences.getBoolean("auto_scroll", true),
        startupMode = runCatching {
            StartupMode.valueOf(preferences.getString("startup_mode", StartupMode.LAST_CONVERSATION.name).orEmpty())
        }.getOrDefault(StartupMode.LAST_CONVERSATION),
        enterToSend = preferences.getBoolean("enter_to_send", false),
        completionVibration = preferences.getBoolean("completion_vibration", true),
        completionNotification = preferences.getBoolean("completion_notification", false),
        followUpSuggestions = preferences.getBoolean("follow_up_suggestions", true),
        autoReadReplies = preferences.getBoolean("auto_read_replies", false),
        ttsSpeechRate = preferences.getFloat("tts_speech_rate", 1f).coerceIn(0.5f, 2f),
        ttsEnginePackage = preferences.getString("tts_engine_package", "").orEmpty(),
        ttsVoiceName = preferences.getString("tts_voice_name", "").orEmpty(),
        ttsLanguageTag = preferences.getString("tts_language_tag", "").orEmpty(),
        asrLanguageTag = preferences.getString("asr_language_tag", "").orEmpty(),
        asrAutoSend = preferences.getBoolean("asr_auto_send", false),
        developerMode = preferences.getBoolean("developer_mode", false),
        chatBackground = runCatching {
            ChatBackground.valueOf(preferences.getString("chat_background", ChatBackground.DEFAULT.name).orEmpty())
        }.getOrDefault(ChatBackground.DEFAULT),
        bubbleStyle = runCatching {
            BubbleStyle.valueOf(preferences.getString("bubble_style", BubbleStyle.STANDARD.name).orEmpty())
        }.getOrDefault(BubbleStyle.STANDARD),
        showMessageAvatars = preferences.getBoolean("show_message_avatars", false),
        showModelLabel = preferences.getBoolean("show_model_label", true),
        userNickname = preferences.getString("user_nickname", "").orEmpty(),
        userAvatarFile = preferences.getString("user_avatar_file", "").orEmpty(),
    )
}
