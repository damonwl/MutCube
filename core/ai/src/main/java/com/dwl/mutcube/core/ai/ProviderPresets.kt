package com.dwl.mutcube.core.ai

data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
    val suggestedModels: List<String> = emptyList(),
    val asrProtocol: AsrProtocol = AsrProtocol.AUDIO_TRANSCRIPTIONS,
    val modelMetadata: Map<String, ModelCapabilities> = emptyMap(),
    val ttsProtocol: TtsProtocol = TtsProtocol.AUDIO_SPEECH,
    val ttsStreaming: Boolean = false,
)

val MIMO_SPEECH_CAPABILITIES = mapOf(
    "mimo-v2.5-pro" to ModelCapabilities(speechRecognition = CapabilityState.UNSUPPORTED, speechSynthesis = CapabilityState.UNSUPPORTED),
    "mimo-v2.5" to ModelCapabilities(speechRecognition = CapabilityState.UNSUPPORTED, speechSynthesis = CapabilityState.UNSUPPORTED),
    "mimo-v2.5-asr" to ModelCapabilities(speechRecognition = CapabilityState.SUPPORTED, speechSynthesis = CapabilityState.UNSUPPORTED),
    "mimo-v2.5-tts" to ModelCapabilities(speechRecognition = CapabilityState.UNSUPPORTED, speechSynthesis = CapabilityState.SUPPORTED),
)

/** Public provider endpoints offered as editable starting points; no credentials are bundled. */
val PROVIDER_PRESETS = listOf(
    ProviderPreset("Xiaomi MiMo", "https://api.xiaomimimo.com/v1", suggestedModels = listOf("mimo-v2.5-pro", "mimo-v2.5", "mimo-v2.5-asr", "mimo-v2.5-tts"), asrProtocol = AsrProtocol.CHAT_INPUT_AUDIO, modelMetadata = MIMO_SPEECH_CAPABILITIES, ttsProtocol = TtsProtocol.CHAT_AUDIO, ttsStreaming = true),
    ProviderPreset("OpenAI", "https://api.openai.com/v1"),
    ProviderPreset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE),
    ProviderPreset("Anthropic Claude", "https://api.anthropic.com/v1", ProviderProtocol.ANTHROPIC_MESSAGES),
    ProviderPreset("AiHubMix", "https://aihubmix.com/v1"),
    ProviderPreset("硅基流动", "https://api.siliconflow.cn/v1"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
    ProviderPreset("月之暗面", "https://api.moonshot.cn/v1"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    ProviderPreset("Vercel AI Gateway", "https://ai-gateway.vercel.sh/v1"),
    ProviderPreset("小马算力", "https://api.tokenpony.cn/v1"),
    ProviderPreset("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ProviderPreset("火山引擎", "https://ark.cn-beijing.volces.com/api/v3"),
    ProviderPreset("智谱 AI", "https://open.bigmodel.cn/api/paas/v4"),
    ProviderPreset("阶跃星辰", "https://api.stepfun.com/v1"),
    ProviderPreset("302.AI", "https://api.302.ai/v1"),
    ProviderPreset("腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1"),
    ProviderPreset("xAI", "https://api.x.ai/v1"),
    ProviderPreset("随想 AI 网关", "https://sui-xiang.com/v1"),
    ProviderPreset("MiniMax（Anthropic）", "https://api.minimaxi.com/anthropic/v1", ProviderProtocol.ANTHROPIC_MESSAGES),
    ProviderPreset("AckAI", "https://ackai.fun/v1"),
    ProviderPreset("自定义", "https://api.example.com/v1"),
)

/** Seed disabled presets once; existing endpoints, identities, keys and selections are untouched. */
fun ProviderConfiguration.withBuiltInProviders(): ProviderConfiguration {
    val additions = PROVIDER_PRESETS.filter { it.name != "自定义" }.filter { preset ->
        profiles.none { it.baseUrl.trimEnd('/') == preset.baseUrl.trimEnd('/') && it.protocol == preset.protocol }
    }.mapIndexed { index, preset ->
        var id = "builtin-provider-$index"
        while (profiles.any { it.id == id }) id += "-new"
        ProviderProfile(id, preset.name, preset.baseUrl, preset.suggestedModels.firstOrNull().orEmpty(),
            protocol = preset.protocol, enabled = false,
            models = preset.suggestedModels.map { ProviderModel(it, capabilities = preset.modelMetadata[it] ?: ModelCapabilities()) },
            asrProtocol = preset.asrProtocol, ttsProtocol = preset.ttsProtocol, ttsStreaming = preset.ttsStreaming)
    }
    return copy(profiles = profiles + additions).normalized()
}

/** Remove only the untouched legacy starter model when its Provider has no saved credential. */
fun ProviderConfiguration.withoutUnconfiguredMimoStarterModel(configuredProviderIds: Set<String>): ProviderConfiguration {
    if ("mimo" in configuredProviderIds) return this
    val updated = profiles.map { profile ->
        val starter = profile.models.singleOrNull()
        if (profile.id == "mimo" && profile.name == "Xiaomi MiMo" &&
            profile.baseUrl.trimEnd('/') == "https://api.xiaomimimo.com/v1" &&
            profile.modelId == "mimo-v2.5-pro" && starter?.id == "mimo-v2.5-pro" &&
            starter.alias.isBlank() && starter.icon.isBlank() && !starter.favorite
        ) profile.copy(modelId = "", models = emptyList()) else profile
    }
    return if (updated == profiles) this else copy(profiles = updated).normalized()
}
