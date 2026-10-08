package com.dwl.mutcube.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigurationTest {
    @Test
    fun presetsIncludeMimo25AndAllSupportedNativeProtocols() {
        val mimo = PROVIDER_PRESETS.first { it.name == "Xiaomi MiMo" }
        assertEquals(listOf("mimo-v2.5-pro", "mimo-v2.5", "mimo-v2.5-asr", "mimo-v2.5-tts"), mimo.suggestedModels)
        assertEquals(AsrProtocol.CHAT_INPUT_AUDIO, mimo.asrProtocol)
        assertTrue(PROVIDER_PRESETS.any { it.protocol == ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE })
        assertTrue(PROVIDER_PRESETS.any { it.protocol == ProviderProtocol.ANTHROPIC_MESSAGES })
    }

    @Test
    fun freshMimoProviderHasNoSelectedOrListedModel() {
        val profile = ProviderConfiguration().normalized().activeProfile
        assertEquals("Xiaomi MiMo", profile.name)
        assertEquals("", profile.modelId)
        assertTrue(profile.models.isEmpty())
    }

    @Test
    fun unconfiguredLegacyStarterIsClearedButConfiguredOrCustomModelsArePreserved() {
        val starter = DEFAULT_MIMO_PROFILE.copy(
            modelId = "mimo-v2.5-pro",
            models = listOf(ProviderModel("mimo-v2.5-pro")),
        )
        val old = ProviderConfiguration(listOf(starter), starter.id)
        val cleared = old.withoutUnconfiguredMimoStarterModel(emptySet())
        assertTrue(cleared.activeProfile.models.isEmpty())
        assertEquals("", cleared.activeProfile.modelId)
        assertEquals(old, old.withoutUnconfiguredMimoStarterModel(setOf("mimo")))
        val customized = old.copy(profiles = listOf(starter.copy(models = listOf(ProviderModel("mimo-v2.5-pro", alias = "我的模型")))))
        assertEquals(customized, customized.withoutUnconfiguredMimoStarterModel(emptySet()))
    }

    @Test
    fun generationRequestRejectsOutOfRangeSamplingValues() {
        val messages = listOf(ModelMessage(ModelMessageRole.USER, "test"))
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(messages, temperature = 2.1) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(messages, topP = -0.1) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(messages, maxOutputTokens = 0) }
    }

    @Test
    fun normalizationTrimsFieldsAndRepairsMissingActiveProfile() {
        val result = ProviderConfiguration(
            profiles = listOf(ProviderProfile(" first ", " Provider ", "https://example.com/v1/", " model ")),
            activeProfileId = "missing",
        ).normalized()

        assertEquals("first", result.activeProfileId)
        assertEquals("https://example.com/v1", result.activeProfile.baseUrl)
        assertEquals("model", result.activeProfile.modelId)
    }

    @Test
    fun configurationRejectsInsecureOrEmptyProfiles() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfiguration(emptyList(), "").normalized()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfiguration(
                listOf(ProviderProfile("id", "Provider", "http://example.com/v1", "model")),
                "id",
            ).normalized()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfiguration(
                listOf(ProviderProfile("id", "Provider", "https://user:secret@example.com/v1", "model")),
                "id",
            ).normalized()
        }
    }

    @Test
    fun normalizationKeepsModelMetadataAndSelectsAnEnabledProvider() {
        val result = ProviderConfiguration(
            profiles = listOf(
                ProviderProfile("disabled", "Disabled", "https://disabled.example/v1", "model-a", enabled = false),
                ProviderProfile(
                    "enabled",
                    "Enabled",
                    "https://enabled.example/v1",
                    "model-b",
                    models = listOf(
                        ProviderModel(" model-b ", alias = " Main ", favorite = true),
                        ProviderModel("model-b"),
                    ),
                ),
            ),
            activeProfileId = "disabled",
        ).normalized()

        assertEquals("enabled", result.activeProfileId)
        assertEquals(1, result.activeProfile.models.size)
        assertEquals("Main", result.activeProfile.models.single().alias)
        assertTrue(result.activeProfile.models.single().favorite)
    }

    @Test
    fun configurationRejectsDisablingEveryProvider() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfiguration(
                listOf(ProviderProfile("id", "Provider", "https://example.com/v1", "model", enabled = false)),
                "id",
            ).normalized()
        }
    }
}
