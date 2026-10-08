package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ModelSettingsStore
import com.dwl.mutcube.core.ai.ReasoningLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidModelSettingsStore(context: Context) : ModelSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences("model_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    override val settings = state.asStateFlow()

    override suspend fun read(): ModelSettings = state.value

    override suspend fun write(settings: ModelSettings) {
        val normalized = settings.copy(
            systemPrompt = settings.systemPrompt.trim(),
            maxContextMessages = settings.maxContextMessages.coerceIn(2, 200),
            temperature = settings.temperature?.coerceIn(0.0, 2.0),
            topP = settings.topP?.coerceIn(0.0, 1.0),
            maxOutputTokens = settings.maxOutputTokens?.coerceIn(1, 131_072),
        )
        preferences.edit()
            .putString(KEY_SYSTEM_PROMPT, normalized.systemPrompt)
            .putInt(KEY_CONTEXT_MESSAGES, normalized.maxContextMessages)
            .putBoolean(KEY_IMAGE_INPUT, normalized.imageInputEnabled)
            .putOptionalFloat(KEY_TEMPERATURE, normalized.temperature)
            .putOptionalFloat(KEY_TOP_P, normalized.topP)
            .putOptionalInt(KEY_MAX_OUTPUT_TOKENS, normalized.maxOutputTokens)
            .putBoolean(KEY_AUTO_TITLE, normalized.autoTitleEnabled)
            .putBoolean(KEY_TOOL_CALLING, normalized.toolCallingEnabled)
            .putBoolean(KEY_MEMORY_WRITE, normalized.memoryWriteEnabled)
            .putString(KEY_REASONING_LEVEL, normalized.reasoningLevel.name)
            .apply()
        state.value = normalized
    }

    private fun load() = ModelSettings(
        systemPrompt = preferences.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
        maxContextMessages = preferences.getInt(KEY_CONTEXT_MESSAGES, 40).coerceIn(2, 200),
        imageInputEnabled = preferences.getBoolean(KEY_IMAGE_INPUT, true),
        temperature = preferences.takeIf { it.contains(KEY_TEMPERATURE) }?.getFloat(KEY_TEMPERATURE, 0f)?.toDouble(),
        topP = preferences.takeIf { it.contains(KEY_TOP_P) }?.getFloat(KEY_TOP_P, 0f)?.toDouble(),
        maxOutputTokens = preferences.takeIf { it.contains(KEY_MAX_OUTPUT_TOKENS) }
            ?.getInt(KEY_MAX_OUTPUT_TOKENS, 0),
        autoTitleEnabled = preferences.getBoolean(KEY_AUTO_TITLE, true),
        toolCallingEnabled = preferences.getBoolean(KEY_TOOL_CALLING, true),
        memoryWriteEnabled = preferences.getBoolean(KEY_MEMORY_WRITE, false),
        reasoningLevel = runCatching {
            ReasoningLevel.valueOf(preferences.getString(KEY_REASONING_LEVEL, ReasoningLevel.AUTO.name).orEmpty())
        }.getOrDefault(ReasoningLevel.AUTO),
    )

    private fun android.content.SharedPreferences.Editor.putOptionalFloat(key: String, value: Double?) =
        if (value == null) remove(key) else putFloat(key, value.toFloat())

    private fun android.content.SharedPreferences.Editor.putOptionalInt(key: String, value: Int?) =
        if (value == null) remove(key) else putInt(key, value)

    private companion object {
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_CONTEXT_MESSAGES = "context_messages"
        const val KEY_IMAGE_INPUT = "image_input"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_P = "top_p"
        const val KEY_MAX_OUTPUT_TOKENS = "max_output_tokens"
        const val KEY_AUTO_TITLE = "auto_title"
        const val KEY_TOOL_CALLING = "tool_calling"
        const val KEY_MEMORY_WRITE = "memory_write"
        const val KEY_REASONING_LEVEL = "reasoning_level"
    }
}
