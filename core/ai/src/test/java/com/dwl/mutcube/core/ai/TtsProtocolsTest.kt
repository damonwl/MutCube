package com.dwl.mutcube.core.ai

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TtsProtocolsTest {
    @Test fun providerDefaultAndModelOverride() {
        val profile = ProviderProfile("audio", "Audio", "https://example.com/v1", "mimo-v2.5-tts",
            ttsProtocol = TtsProtocol.CHAT_AUDIO,
            models = listOf(ProviderModel("mimo-v2.5-tts", ttsProtocol = TtsProtocol.AUDIO_SPEECH)))
        assertEquals(TtsProtocol.AUDIO_SPEECH, SpeechProtocols.resolveTts(profile, "mimo-v2.5-tts"))
        assertEquals(TtsProtocol.CHAT_AUDIO, SpeechProtocols.resolveTts(profile, "other"))
        assertEquals(profile, ProviderConfigurationTransfer.decode(ProviderConfigurationTransfer.encode(ProviderConfiguration(listOf(profile), "audio"))).activeProfile)
    }

    @Test fun sendsAssistantTextAndParsesAudio() {
        val root = Json.parseToJsonElement(SpeechProtocols.chatSpeechRequest("tts", "你好", "冰糖")).jsonObject
        val message = root["messages"]!!.jsonArray.single().jsonObject
        assertEquals("assistant", message["role"]!!.jsonPrimitive.content)
        assertEquals("你好", message["content"]!!.jsonPrimitive.content)
        assertEquals("wav", root["audio"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("冰糖", root["audio"]!!.jsonObject["voice"]!!.jsonPrimitive.content)
        assertEquals("YWJj", SpeechProtocols.speechBase64("""{"choices":[{"message":{"audio":{"data":"YWJj"}}}]}"""))
        assertThrows(IllegalStateException::class.java) { SpeechProtocols.speechBase64("""{"choices":[]}""") }
    }
}
