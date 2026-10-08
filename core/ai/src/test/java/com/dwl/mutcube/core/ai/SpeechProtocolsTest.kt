package com.dwl.mutcube.core.ai

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SpeechProtocolsTest {
    @Test fun protocolIsDeclaredNotGuessedFromName() {
        val profile = ProviderProfile("custom", "Custom", "https://example.com/v1", "mimo-v2.5-asr",
            models = listOf(ProviderModel("other-asr", asrProtocol = AsrProtocol.CHAT_INPUT_AUDIO)))
        assertEquals(AsrProtocol.AUDIO_TRANSCRIPTIONS, SpeechProtocols.resolveAsr(profile, "mimo-v2.5-asr"))
        assertEquals(AsrProtocol.CHAT_INPUT_AUDIO, SpeechProtocols.resolveAsr(profile, "other-asr"))
        assertEquals(AsrProtocol.CHAT_INPUT_AUDIO, SpeechProtocols.resolveAsr(profile.copy(asrProtocol = AsrProtocol.CHAT_INPUT_AUDIO), "mimo-v2.5-asr"))
    }

    @Test fun chatRequestIncludesAudioAndLanguage() {
        val root = Json.parseToJsonElement(SpeechProtocols.chatAudioRequest("custom-asr", "YWJj", "zh-CN")).jsonObject
        assertEquals("custom-asr", root["model"]!!.jsonPrimitive.content)
        assertFalse(root["stream"]!!.jsonPrimitive.boolean)
        assertEquals("zh", root["asr_options"]!!.jsonObject["language"]!!.jsonPrimitive.content)
        val audio = root["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.single().jsonObject
        assertEquals("input_audio", audio["type"]!!.jsonPrimitive.content)
        assertEquals("data:audio/wav;base64,YWJj", audio["input_audio"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertTrue(SpeechProtocols.chatAudioRequest("asr", "abc", "fr-FR").contains("auto"))
    }

    @Test fun responseFormatsAndEmptyResponse() {
        assertEquals("你好", SpeechProtocols.transcript(AsrProtocol.CHAT_INPUT_AUDIO, """{"choices":[{"message":{"content":" 你好 "}}]}"""))
        assertEquals("hello", SpeechProtocols.transcript(AsrProtocol.AUDIO_TRANSCRIPTIONS, """{"text":"hello"}"""))
        assertThrows(IllegalStateException::class.java) { SpeechProtocols.transcript(AsrProtocol.CHAT_INPUT_AUDIO, """{"choices":[]}""") }
    }

    @Test fun transferPreservesProviderDefaultAndModelOverride() {
        val profile = ProviderProfile("audio", "Audio", "https://example.com/v1", "asr",
            asrProtocol = AsrProtocol.CHAT_INPUT_AUDIO,
            models = listOf(ProviderModel("asr", asrProtocol = AsrProtocol.AUDIO_TRANSCRIPTIONS,
                capabilities = ModelCapabilities(speechRecognition = CapabilityState.SUPPORTED, speechSynthesis = CapabilityState.UNSUPPORTED))))
        assertEquals(profile, ProviderConfigurationTransfer.decode(ProviderConfigurationTransfer.encode(ProviderConfiguration(listOf(profile), "audio"))).activeProfile)
        val old = ProviderConfigurationTransfer.encode(ProviderConfiguration(listOf(profile), "audio"))
            .replace(",\"asrProtocol\":\"CHAT_INPUT_AUDIO\"", "")
        assertEquals(AsrProtocol.AUDIO_TRANSCRIPTIONS, ProviderConfigurationTransfer.decode(old).activeProfile.asrProtocol)
    }
}
