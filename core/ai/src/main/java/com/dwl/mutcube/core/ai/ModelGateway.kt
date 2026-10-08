package com.dwl.mutcube.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.net.URI

enum class ModelMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class ModelToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ModelMessage(
    val role: ModelMessageRole,
    val content: String,
    val images: List<ModelImage> = emptyList(),
    val toolCalls: List<ModelToolCall> = emptyList(),
    val toolCallId: String? = null,
    val reasoningContent: String? = null,
)

data class ModelImage(val dataUrl: String)

data class GenerationRequest(
    val messages: List<ModelMessage>,
    val modelId: String = OpenAiCompatibleModelGateway.DEFAULT_MODEL_ID,
    val baseUrl: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val tools: List<ModelToolDefinition> = emptyList(),
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
) {
    init {
        require(temperature == null || temperature in 0.0..2.0) { "Temperature must be between 0 and 2" }
        require(topP == null || topP in 0.0..1.0) { "Top P must be between 0 and 1" }
        require(maxOutputTokens == null || maxOutputTokens in 1..131_072) { "Invalid max output tokens" }
    }
}

data class ModelToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

sealed interface ModelStreamEvent {
    data class TextDelta(val text: String) : ModelStreamEvent
    data class ReasoningDelta(val text: String) : ModelStreamEvent
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String,
    ) : ModelStreamEvent
    data class Usage(
        val inputTokens: Long?,
        val outputTokens: Long?,
        val cachedInputTokens: Long? = null,
    ) : ModelStreamEvent
}

enum class ProviderProtocol {
    OPENAI_CHAT_COMPLETIONS,
    GOOGLE_GENERATIVE_LANGUAGE,
    ANTHROPIC_MESSAGES,
}

enum class ReasoningLevel(val apiValue: String?) {
    OFF("none"),
    AUTO(null),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
}

enum class CapabilityState {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

enum class AsrProtocol(val label: String) {
    AUDIO_TRANSCRIPTIONS("音频转写（表单上传）"),
    CHAT_INPUT_AUDIO("聊天式转写（Base64 音频）"),
}

enum class TtsProtocol(val label: String) {
    AUDIO_SPEECH("标准语音合成（音频响应）"),
    CHAT_AUDIO("聊天式语音合成（Base64 响应）"),
}

data class ModelCapabilities(
    val imageInput: CapabilityState = CapabilityState.UNKNOWN,
    val toolCalling: CapabilityState = CapabilityState.UNKNOWN,
    val reasoning: CapabilityState = CapabilityState.UNKNOWN,
    val speechRecognition: CapabilityState = CapabilityState.UNKNOWN,
    val speechSynthesis: CapabilityState = CapabilityState.UNKNOWN,
)

data class ProviderModel(
    val id: String,
    val alias: String = "",
    val icon: String = "",
    val favorite: Boolean = false,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val asrProtocol: AsrProtocol? = null,
    val ttsProtocol: TtsProtocol? = null,
    val ttsStreaming: Boolean? = null,
    val ttsDefaultVoice: String = "",
    val ttsVoices: List<String> = emptyList(),
) {
    val displayName: String
        get() = alias.ifBlank { id }
}

data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
    val enabled: Boolean = true,
    val models: List<ProviderModel> = modelId.takeIf(String::isNotBlank)?.let { listOf(ProviderModel(it)) } ?: emptyList(),
    val asrProtocol: AsrProtocol = AsrProtocol.AUDIO_TRANSCRIPTIONS,
    val ttsProtocol: TtsProtocol = TtsProtocol.AUDIO_SPEECH,
    val ttsStreaming: Boolean = false,
)

data class ProviderConfiguration(
    val profiles: List<ProviderProfile> = listOf(DEFAULT_MIMO_PROFILE),
    val activeProfileId: String = DEFAULT_MIMO_PROFILE.id,
) {
    val activeProfile: ProviderProfile
        get() = profiles.firstOrNull { it.id == activeProfileId && it.enabled }
            ?: profiles.firstOrNull(ProviderProfile::enabled)
            ?: profiles.first()
}

val DEFAULT_MIMO_PROFILE = ProviderProfile(
    id = "mimo",
    name = "Xiaomi MiMo",
    baseUrl = OpenAiCompatibleModelGateway.DEFAULT_BASE_URL,
    modelId = "",
    asrProtocol = AsrProtocol.CHAT_INPUT_AUDIO,
    ttsProtocol = TtsProtocol.CHAT_AUDIO,
    ttsStreaming = true,
)

fun ProviderConfiguration.normalized(): ProviderConfiguration {
    val normalizedProfiles = profiles.map { profile ->
        val selectedModelId = profile.modelId.trim()
        val normalizedModels = (profile.models + ProviderModel(selectedModelId))
            .map { model ->
                model.copy(
                    id = model.id.trim(),
                    alias = model.alias.trim(),
                    icon = model.icon.trim(),
                    contextWindow = model.contextWindow?.coerceIn(1, 10_000_000),
                    maxOutputTokens = model.maxOutputTokens?.coerceIn(1, 1_000_000),
                )
            }
            .filter { it.id.isNotEmpty() }
            .distinctBy(ProviderModel::id)
        profile.copy(
            id = profile.id.trim(),
            name = profile.name.trim(),
            baseUrl = profile.baseUrl.trim().trimEnd('/'),
            modelId = selectedModelId,
            models = normalizedModels,
        )
    }
    require(normalizedProfiles.isNotEmpty()) { "At least one provider profile is required" }
    require(normalizedProfiles.all { it.id.isNotEmpty() && it.name.isNotEmpty() }) {
        "Provider identity is required"
    }
    require(normalizedProfiles.all { it.baseUrl.isSecureBaseUrl() }) {
        "Provider URL must use HTTPS"
    }
    require(normalizedProfiles.map { it.id }.distinct().size == normalizedProfiles.size) {
        "Provider profile IDs must be unique"
    }
    require(normalizedProfiles.any(ProviderProfile::enabled)) { "At least one provider profile must be enabled" }
    val active = activeProfileId.takeIf { id -> normalizedProfiles.any { it.id == id && it.enabled } }
        ?: normalizedProfiles.first(ProviderProfile::enabled).id
    return copy(profiles = normalizedProfiles, activeProfileId = active)
}

private fun String.isSecureBaseUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
}.getOrDefault(false)

interface ProviderProfileStore {
    val configuration: Flow<ProviderConfiguration>
    suspend fun read(): ProviderConfiguration
    suspend fun write(configuration: ProviderConfiguration)
}

object DefaultProviderProfileStore : ProviderProfileStore {
    private val value = ProviderConfiguration()
    override val configuration: Flow<ProviderConfiguration> = flowOf(value)
    override suspend fun read(): ProviderConfiguration = value
    override suspend fun write(configuration: ProviderConfiguration) = Unit
}

class ApiCredential private constructor(internal val value: String) {
    override fun toString(): String = "ApiCredential([REDACTED])"

    companion object {
        fun from(value: String): ApiCredential {
            require(value.isNotBlank()) { "API credential must not be blank" }
            return ApiCredential(value.trim())
        }
    }
}

interface ModelGateway {
    fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String>
    fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> =
        stream(request, credential).map(ModelStreamEvent::TextDelta)
}

interface ProviderModelCatalog {
    suspend fun listModels(
        baseUrl: String,
        credential: ApiCredential,
        protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
    ): List<String>
}

data class ModelSettings(
    val systemPrompt: String = "",
    val maxContextMessages: Int = 40,
    val imageInputEnabled: Boolean = true,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val autoTitleEnabled: Boolean = true,
    val toolCallingEnabled: Boolean = true,
    val memoryWriteEnabled: Boolean = false,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
)

interface ModelSettingsStore {
    val settings: Flow<ModelSettings>
    suspend fun read(): ModelSettings
    suspend fun write(settings: ModelSettings)
}

object DefaultModelSettingsStore : ModelSettingsStore {
    private val value = ModelSettings()
    override val settings: Flow<ModelSettings> = flowOf(value)
    override suspend fun read(): ModelSettings = value
    override suspend fun write(settings: ModelSettings) = Unit
}

enum class ModelErrorKind(val retryable: Boolean) {
    AUTHENTICATION(false),
    RATE_LIMIT(true),
    SERVER(true),
    NETWORK(true),
    INVALID_RESPONSE(true),
    REQUEST(false),
}

class ModelProviderException(
    val kind: ModelErrorKind,
    val statusCode: Int?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
