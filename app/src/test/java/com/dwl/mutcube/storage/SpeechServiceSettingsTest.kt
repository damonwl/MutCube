package com.dwl.mutcube.storage

import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Assert.assertEquals
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.core.ai.ProviderProtocol

class SpeechServiceSettingsTest {
    @Test
    fun bindsExplicitProviderAndRejectsRemovedOrDisabledConfiguration() {
        val profile = ProviderProfile("audio", "Audio", "https://audio.example.com/v1", "whisper-1")
        val configuration = ProviderConfiguration(listOf(profile))
        assertEquals(profile, resolveSpeechProfile(configuration, "audio", "whisper-1"))
        assertThrows(IllegalStateException::class.java) { resolveSpeechProfile(configuration, "deleted", "whisper-1") }
        assertThrows(IllegalArgumentException::class.java) { resolveSpeechProfile(configuration, "audio", "removed") }
        assertThrows(IllegalArgumentException::class.java) {
            resolveSpeechProfile(configuration.copy(profiles = listOf(profile.copy(enabled = false))), "audio", "whisper-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveSpeechProfile(configuration.copy(profiles = listOf(profile.copy(protocol = ProviderProtocol.ANTHROPIC_MESSAGES))), "audio", "whisper-1")
        }
    }

    @Test
    fun networkProvidersRequireHttpsAndModelConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeechServiceSettingsStore.validate(
                SpeechServiceSettings(
                    ttsProvider = SpeechProvider.OPENAI_COMPATIBLE,
                    ttsBaseUrl = "http://speech.example.com/v1",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeechServiceSettingsStore.validate(
                SpeechServiceSettings(
                    asrProvider = SpeechProvider.OPENAI_COMPATIBLE,
                    asrModel = "",
                ),
            )
        }
    }

    @Test
    fun systemProvidersDoNotRequireNetworkConfiguration() {
        SpeechServiceSettingsStore.validate(
            SpeechServiceSettings(ttsBaseUrl = "", ttsModel = "", ttsVoice = "", asrBaseUrl = "", asrModel = ""),
        )
    }
}
