package com.dwl.mutcube.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigurationTransferTest {
    @Test
    fun roundTripPreservesCapabilitiesAndNeverExportsCredentials() {
        val source = ProviderConfiguration(
            listOf(
                ProviderProfile(
                    "custom", "自定义", "https://example.com/v1", "model-a",
                    models = listOf(
                        ProviderModel(
                            "model-a", "别名", favorite = true,
                            capabilities = ModelCapabilities(reasoning = CapabilityState.SUPPORTED),
                            contextWindow = 128_000,
                            ttsDefaultVoice = "voice-a",
                            ttsVoices = listOf("voice-a", "voice-b"),
                        ),
                    ),
                ),
            ),
            "custom",
        )

        val encoded = ProviderConfigurationTransfer.encode(source)
        val restored = ProviderConfigurationTransfer.decode(encoded)

        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertEquals("别名", restored.activeProfile.models.single().alias)
        assertEquals(CapabilityState.SUPPORTED, restored.activeProfile.models.single().capabilities.reasoning)
        assertEquals("voice-a", restored.activeProfile.models.single().ttsDefaultVoice)
        assertEquals(listOf("voice-a", "voice-b"), restored.activeProfile.models.single().ttsVoices)
    }

    @Test
    fun mergeRenamesConflictingIdsWithoutChangingCurrentActiveProvider() {
        val imported = ProviderConfigurationTransfer.decode(ProviderConfigurationTransfer.encode(ProviderConfiguration()))
        val merged = ProviderConfigurationTransfer.merge(ProviderConfiguration(), imported)

        assertEquals("mimo", merged.activeProfileId)
        assertEquals(2, merged.profiles.size)
        assertTrue(merged.profiles.last().id.startsWith("mimo-imported"))
    }
}
