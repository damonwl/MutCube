package com.dwl.mutcube.core.ai

data class TtsVoiceOptions(val defaultVoice: String, val voices: List<String>)

object TtsVoiceCatalog {
    private val mimoVoices = listOf("mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")
    private val openAiVoices = listOf("alloy", "ash", "ballad", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer", "verse", "marin", "cedar")

    fun resolve(baseUrl: String, modelId: String, model: ProviderModel? = null): TtsVoiceOptions? {
        val configured = model?.ttsVoices.orEmpty().map(String::trim).filter(String::isNotEmpty).distinct()
        val configuredDefault = model?.ttsDefaultVoice.orEmpty().trim()
        if (configured.isNotEmpty() || configuredDefault.isNotEmpty()) {
            val defaultVoice = configuredDefault.ifEmpty { configured.first() }
            return TtsVoiceOptions(defaultVoice, (listOf(defaultVoice) + configured).distinct())
        }
        val host = runCatching { java.net.URI(baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host == "api.xiaomimimo.com" && modelId == "mimo-v2.5-tts" ->
                TtsVoiceOptions("mimo_default", mimoVoices)
            host == "api.openai.com" && (modelId in listOf("tts-1", "tts-1-hd") || modelId.startsWith("gpt-4o-mini-tts")) ->
                TtsVoiceOptions("alloy", openAiVoices)
            else -> null
        }
    }
}
