package com.dwl.mutcube.core.ai

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class SpeechDeliveryTest {
    @Test fun streamingRequestsAndChunkFormats() {
        val json = Json.parseToJsonElement(SpeechProtocols.chatSpeechRequest("tts", "你好", "冰糖", true)).jsonObject
        assertTrue(json["stream"]!!.jsonPrimitive.boolean)
        assertEquals("pcm16", json["audio"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("YWJj", SpeechProtocols.speechChunk("""{"choices":[{"delta":{"audio":{"data":"YWJj"}}}]}"""))
        assertNull(SpeechProtocols.speechChunk("""{"choices":[{"delta":{"audio":null}}]}"""))
        assertNull(SpeechProtocols.speechChunk("""{"choices":[],"usage":{}}"""))
        assertTrue(SpeechProtocols.speechFinished("""{"choices":[{"finish_reason":"stop"}]}"""))
        assertFalse(SpeechProtocols.speechFinished("""{"choices":[{"finish_reason":null}]}"""))
        assertThrows(IllegalArgumentException::class.java) { SpeechProtocols.speechChunk("""{"error":{"message":"bad"}}""") }
    }

    @Test fun textIsShortFirstThenBoundedAndPreserved() {
        val text = "第一句。" + "后续文本".repeat(200)
        val segments = SpeechText.segments(text)
        assertEquals(text, segments.joinToString(""))
        assertTrue(segments.first().length <= 90)
        assertTrue(segments.all { it.length <= 260 })
        assertEquals(emptyList<String>(), SpeechText.segments("  "))
        assertEquals("标题链接", SpeechText.segments("# 标题\n[链接](https://example.com)").joinToString(""))
        val emoji = "a".repeat(89) + "😀" + "b".repeat(500)
        assertEquals(emoji, SpeechText.segments(emoji).joinToString(""))
        assertFalse(SpeechText.segments(emoji).any { it.last().isHighSurrogate() })
    }

    @Test fun seedsDisabledProvidersWithoutOverwritingOrDuplicating() {
        val original = ProviderConfiguration()
        val seeded = original.withBuiltInProviders()
        assertEquals(original.activeProfile, seeded.activeProfile)
        assertTrue(seeded.profiles.filter { it.id != "mimo" }.all { !it.enabled })
        assertEquals(PROVIDER_PRESETS.count { it.name != "自定义" }, seeded.profiles.size)
        assertEquals(seeded, seeded.withBuiltInProviders())
        assertEquals(seeded, ProviderConfigurationTransfer.decode(ProviderConfigurationTransfer.encode(seeded)))
        val mimo = seeded.profiles.first { it.id == "mimo" }
        assertTrue(mimo.ttsStreaming)
        assertTrue(mimo.models.isEmpty())
        assertEquals("", mimo.modelId)
    }
}
