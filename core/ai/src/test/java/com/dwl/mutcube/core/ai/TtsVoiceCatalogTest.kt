package com.dwl.mutcube.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceCatalogTest {
    @Test fun officialModelsHaveDistinctDefaults() {
        val mimo = TtsVoiceCatalog.resolve("https://api.xiaomimimo.com/v1", "mimo-v2.5-tts")!!
        val openAi = TtsVoiceCatalog.resolve("https://api.openai.com/v1", "gpt-4o-mini-tts")!!
        assertEquals("mimo_default", mimo.defaultVoice)
        assertTrue("冰糖" in mimo.voices)
        assertEquals("alloy", openAi.defaultVoice)
        assertTrue("nova" in openAi.voices)
    }

    @Test fun modelConfigurationOverridesPreset() {
        val model = ProviderModel("mimo-v2.5-tts", ttsDefaultVoice = "voice-a", ttsVoices = listOf("voice-b"))
        assertEquals(listOf("voice-a", "voice-b"), TtsVoiceCatalog.resolve("https://api.xiaomimimo.com/v1", model.id, model)!!.voices)
    }

    @Test fun unknownModelsRequireExplicitVoice() {
        assertNull(TtsVoiceCatalog.resolve("https://example.org/v1", "my-tts"))
    }
}
