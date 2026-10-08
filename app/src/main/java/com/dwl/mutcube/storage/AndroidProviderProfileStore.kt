package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.ai.DEFAULT_MIMO_PROFILE
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.CapabilityState
import com.dwl.mutcube.core.ai.ModelCapabilities
import com.dwl.mutcube.core.ai.ProviderModel
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.core.ai.ProviderProfileStore
import com.dwl.mutcube.core.ai.ProviderProtocol
import com.dwl.mutcube.core.ai.normalized
import com.dwl.mutcube.core.ai.AsrProtocol
import com.dwl.mutcube.core.ai.TtsProtocol
import com.dwl.mutcube.core.ai.MIMO_SPEECH_CAPABILITIES
import com.dwl.mutcube.core.ai.withBuiltInProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AndroidProviderProfileStore(context: Context) : ProviderProfileStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    override val configuration = state.asStateFlow()

    override suspend fun read(): ProviderConfiguration = state.value

    override suspend fun write(configuration: ProviderConfiguration) {
        val normalized = configuration.normalized()
        preferences.edit()
            .putString(KEY_PROFILES, normalized.profiles.toJson().toString())
            .putString(KEY_ACTIVE, normalized.activeProfileId)
            .apply()
        state.value = normalized
    }

    private fun load(): ProviderConfiguration {
        val stored = loadStored()
        val seeded = if (preferences.getBoolean("built_in_presets_seeded", false)) stored else stored.withBuiltInProviders()
        preferences.edit().putString(KEY_PROFILES, seeded.profiles.toJson().toString())
            .putString(KEY_ACTIVE, seeded.activeProfileId).putBoolean("built_in_presets_seeded", true).apply()
        return seeded
    }

    private fun loadStored(): ProviderConfiguration {
        val stored = preferences.getString(KEY_PROFILES, null)
        if (stored.isNullOrBlank()) {
            val legacyModel = appContext.getSharedPreferences("model_settings", Context.MODE_PRIVATE)
                .getString("model_id", null)
                ?.trim()
                .orEmpty()
            return ProviderConfiguration(
                profiles = listOf(
                    DEFAULT_MIMO_PROFILE.copy(
                        modelId = legacyModel.ifBlank { DEFAULT_MIMO_PROFILE.modelId },
                        models = legacyModel.takeIf(String::isNotBlank)?.let { listOf(ProviderModel(it)) } ?: emptyList(),
                    ),
                ),
            )
        }
        return runCatching {
            val array = JSONArray(stored)
            val profiles = buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        ProviderProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            baseUrl = item.getString("baseUrl"),
                            modelId = item.getString("modelId"),
                            protocol = ProviderProtocol.valueOf(
                                item.optString("protocol", ProviderProtocol.OPENAI_CHAT_COMPLETIONS.name),
                            ),
                            enabled = item.optBoolean("enabled", true),
                            ttsStreaming = item.optBoolean("ttsStreaming", item.getString("baseUrl").trimEnd('/') == "https://api.xiaomimimo.com/v1"),
                            ttsProtocol = item.optString("ttsProtocol").takeIf(String::isNotBlank)?.let(TtsProtocol::valueOf)
                                ?: if (item.getString("baseUrl").trimEnd('/') == "https://api.xiaomimimo.com/v1") TtsProtocol.CHAT_AUDIO else TtsProtocol.AUDIO_SPEECH,
                            asrProtocol = item.optString("asrProtocol").takeIf(String::isNotBlank)?.let(AsrProtocol::valueOf)
                                ?: if (item.getString("baseUrl").trimEnd('/') == "https://api.xiaomimimo.com/v1") AsrProtocol.CHAT_INPUT_AUDIO else AsrProtocol.AUDIO_TRANSCRIPTIONS,
                            models = item.optJSONArray("models")?.toModels(item.getString("baseUrl").trimEnd('/') == "https://api.xiaomimimo.com/v1")
                                ?: listOf(ProviderModel(item.getString("modelId"))),
                        ),
                    )
                }
            }
            ProviderConfiguration(profiles, preferences.getString(KEY_ACTIVE, null).orEmpty()).normalized()
        }.getOrElse { ProviderConfiguration() }
    }

    private fun List<ProviderProfile>.toJson() = JSONArray().apply {
        forEach { profile ->
            put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("baseUrl", profile.baseUrl)
                    .put("modelId", profile.modelId)
                    .put("protocol", profile.protocol.name)
                    .put("enabled", profile.enabled)
                    .put("asrProtocol", profile.asrProtocol.name)
                    .put("ttsProtocol", profile.ttsProtocol.name)
                    .put("ttsStreaming", profile.ttsStreaming)
                    .put("models", profile.models.toModelJson()),
            )
        }
    }

    private fun List<ProviderModel>.toModelJson() = JSONArray().apply {
        forEach { model ->
            put(
                JSONObject()
                    .put("id", model.id)
                    .put("alias", model.alias)
                    .put("icon", model.icon)
                    .put("favorite", model.favorite)
                    .put("imageInput", model.capabilities.imageInput.name)
                    .put("toolCalling", model.capabilities.toolCalling.name)
                    .put("reasoning", model.capabilities.reasoning.name)
                    .put("speechRecognition", model.capabilities.speechRecognition.name)
                    .put("speechSynthesis", model.capabilities.speechSynthesis.name)
                    .put("asrProtocol", model.asrProtocol?.name ?: JSONObject.NULL)
                    .put("ttsProtocol", model.ttsProtocol?.name ?: JSONObject.NULL)
                    .put("ttsStreaming", model.ttsStreaming ?: JSONObject.NULL)
                    .put("ttsDefaultVoice", model.ttsDefaultVoice)
                    .put("ttsVoices", JSONArray(model.ttsVoices))
                    .put("contextWindow", model.contextWindow ?: JSONObject.NULL)
                    .put("maxOutputTokens", model.maxOutputTokens ?: JSONObject.NULL),
            )
        }
    }

    private fun JSONArray.toModels(mimoPreset: Boolean) = buildList {
        repeat(length()) { index ->
            val item = getJSONObject(index)
            add(
                ProviderModel(
                    id = item.getString("id"),
                    alias = item.optString("alias"),
                    icon = item.optString("icon"),
                    favorite = item.optBoolean("favorite"),
                    capabilities = ModelCapabilities(
                        imageInput = item.capability("imageInput"),
                        toolCalling = item.capability("toolCalling"),
                        reasoning = item.capability("reasoning"),
                        speechRecognition = if (!item.has("speechRecognition") && mimoPreset) MIMO_SPEECH_CAPABILITIES[item.getString("id")]?.speechRecognition ?: CapabilityState.UNKNOWN else item.capability("speechRecognition"),
                        speechSynthesis = if (!item.has("speechSynthesis") && mimoPreset) MIMO_SPEECH_CAPABILITIES[item.getString("id")]?.speechSynthesis ?: CapabilityState.UNKNOWN else item.capability("speechSynthesis"),
                    ),
                    contextWindow = item.optionalInt("contextWindow"),
                    maxOutputTokens = item.optionalInt("maxOutputTokens"),
                    asrProtocol = item.optString("asrProtocol").takeIf { it.isNotBlank() && it != "null" }?.let(AsrProtocol::valueOf),
                    ttsProtocol = item.optString("ttsProtocol").takeIf { it.isNotBlank() && it != "null" }?.let(TtsProtocol::valueOf),
                    ttsStreaming = if (item.has("ttsStreaming") && !item.isNull("ttsStreaming")) item.optBoolean("ttsStreaming") else null,
                    ttsDefaultVoice = item.optString("ttsDefaultVoice"),
                    ttsVoices = item.optJSONArray("ttsVoices")?.let { values ->
                        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                    } ?: emptyList(),
                ),
            )
        }
    }

    private fun JSONObject.capability(key: String) = runCatching {
        CapabilityState.valueOf(optString(key, CapabilityState.UNKNOWN.name))
    }.getOrDefault(CapabilityState.UNKNOWN)

    private fun JSONObject.optionalInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key).takeIf { it > 0 }

    private companion object {
        const val PREFERENCES = "provider_profiles"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE = "active_profile_id"
    }
}
