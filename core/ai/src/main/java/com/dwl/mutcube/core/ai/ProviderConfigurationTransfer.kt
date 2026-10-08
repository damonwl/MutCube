package com.dwl.mutcube.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ProviderConfigurationTransfer {
    private const val SCHEMA_VERSION = 1
    private const val MAX_PROVIDERS = 100
    private const val MAX_MODELS_PER_PROVIDER = 1_000

    fun encode(configuration: ProviderConfiguration): String = buildJsonObject {
        put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
        put("containsCredentials", JsonPrimitive(false))
        put("activeProfileId", JsonPrimitive(configuration.activeProfileId))
        put("profiles", buildJsonArray { configuration.profiles.forEach { add(it.toJson()) } })
    }.toString()

    fun decode(content: String): ProviderConfiguration {
        require(content.length <= 1_000_000) { "Provider 配置文件不能超过 1 MB" }
        val root = Json.parseToJsonElement(content).jsonObject
        require(root["schemaVersion"]?.jsonPrimitive?.intOrNull == SCHEMA_VERSION) { "不支持此配置版本" }
        require(root["containsCredentials"]?.jsonPrimitive?.booleanOrNull == false) { "拒绝导入包含凭据的配置" }
        val profilesJson = root["profiles"]?.jsonArray ?: error("配置缺少 Provider 列表")
        require(profilesJson.size in 1..MAX_PROVIDERS) { "Provider 数量无效" }
        val profiles = profilesJson.map { it.jsonObject.toProfile() }
        return ProviderConfiguration(
            profiles = profiles,
            activeProfileId = root["activeProfileId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        ).normalized()
    }

    fun merge(current: ProviderConfiguration, imported: ProviderConfiguration): ProviderConfiguration {
        val usedIds = current.profiles.mapTo(mutableSetOf(), ProviderProfile::id)
        val additions = imported.profiles.map { profile ->
            if (profile.id !in usedIds) {
                usedIds += profile.id
                profile
            } else {
                var suffix = 2
                var candidate = "${profile.id}-imported"
                while (candidate in usedIds) candidate = "${profile.id}-imported-${suffix++}"
                usedIds += candidate
                profile.copy(id = candidate, name = "${profile.name}（导入）")
            }
        }
        return ProviderConfiguration(current.profiles + additions, current.activeProfileId).normalized()
    }

    private fun ProviderProfile.toJson() = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("baseUrl", JsonPrimitive(baseUrl))
        put("modelId", JsonPrimitive(modelId))
        put("protocol", JsonPrimitive(protocol.name))
        put("enabled", JsonPrimitive(enabled))
        put("asrProtocol", JsonPrimitive(asrProtocol.name))
        put("ttsProtocol", JsonPrimitive(ttsProtocol.name))
        put("ttsStreaming", JsonPrimitive(ttsStreaming))
        put("models", buildJsonArray { models.forEach { add(it.toJson()) } })
    }

    private fun ProviderModel.toJson() = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("alias", JsonPrimitive(alias))
        put("icon", JsonPrimitive(icon))
        put("favorite", JsonPrimitive(favorite))
        put("imageInput", JsonPrimitive(capabilities.imageInput.name))
        put("toolCalling", JsonPrimitive(capabilities.toolCalling.name))
        put("reasoning", JsonPrimitive(capabilities.reasoning.name))
        put("speechRecognition", JsonPrimitive(capabilities.speechRecognition.name))
        put("speechSynthesis", JsonPrimitive(capabilities.speechSynthesis.name))
        asrProtocol?.let { put("asrProtocol", JsonPrimitive(it.name)) }
        ttsProtocol?.let { put("ttsProtocol", JsonPrimitive(it.name)) }
        ttsStreaming?.let { put("ttsStreaming", JsonPrimitive(it)) }
        if (ttsDefaultVoice.isNotBlank()) put("ttsDefaultVoice", JsonPrimitive(ttsDefaultVoice))
        if (ttsVoices.isNotEmpty()) put("ttsVoices", buildJsonArray { ttsVoices.forEach { add(JsonPrimitive(it)) } })
        contextWindow?.let { put("contextWindow", JsonPrimitive(it)) }
        maxOutputTokens?.let { put("maxOutputTokens", JsonPrimitive(it)) }
    }

    private fun JsonObject.toProfile(): ProviderProfile {
        val modelsJson = this["models"]?.jsonArray ?: JsonArray(emptyList())
        require(modelsJson.size <= MAX_MODELS_PER_PROVIDER) { "单个 Provider 的模型数量过多" }
        val models = modelsJson.map { item ->
            val model = item.jsonObject
            ProviderModel(
                id = model.requiredText("id"),
                alias = model.text("alias"),
                icon = model.text("icon"),
                favorite = model["favorite"]?.jsonPrimitive?.booleanOrNull ?: false,
                capabilities = ModelCapabilities(
                    imageInput = model.capability("imageInput"),
                    toolCalling = model.capability("toolCalling"),
                    reasoning = model.capability("reasoning"),
                    speechRecognition = model.capability("speechRecognition"),
                    speechSynthesis = model.capability("speechSynthesis"),
                ),
                contextWindow = model["contextWindow"]?.jsonPrimitive?.intOrNull,
                maxOutputTokens = model["maxOutputTokens"]?.jsonPrimitive?.intOrNull,
                asrProtocol = model.text("asrProtocol").takeIf(String::isNotBlank)?.let(AsrProtocol::valueOf),
                ttsProtocol = model.text("ttsProtocol").takeIf(String::isNotBlank)?.let(TtsProtocol::valueOf),
                ttsStreaming = model["ttsStreaming"]?.jsonPrimitive?.booleanOrNull,
                ttsDefaultVoice = model.text("ttsDefaultVoice"),
                ttsVoices = model["ttsVoices"]?.jsonArray?.take(100)?.mapNotNull { it.jsonPrimitive.contentOrNull?.take(100) } ?: emptyList(),
            )
        }
        return ProviderProfile(
            id = requiredText("id"),
            name = requiredText("name"),
            baseUrl = requiredText("baseUrl"),
            modelId = text("modelId"),
            protocol = runCatching { ProviderProtocol.valueOf(requiredText("protocol")) }
                .getOrElse { error("未知 Provider 协议") },
            enabled = this["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            asrProtocol = text("asrProtocol").takeIf(String::isNotBlank)?.let(AsrProtocol::valueOf) ?: AsrProtocol.AUDIO_TRANSCRIPTIONS,
            ttsProtocol = text("ttsProtocol").takeIf(String::isNotBlank)?.let(TtsProtocol::valueOf) ?: TtsProtocol.AUDIO_SPEECH,
            ttsStreaming = this["ttsStreaming"]?.jsonPrimitive?.booleanOrNull ?: false,
            models = models,
        )
    }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty().take(500)
    private fun JsonObject.requiredText(key: String) = text(key).also { require(it.isNotBlank()) { "配置字段 $key 不能为空" } }
    private fun JsonObject.capability(key: String) = runCatching { CapabilityState.valueOf(text(key)) }
        .getOrDefault(CapabilityState.UNKNOWN)
}
