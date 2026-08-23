package com.myvault.app.data.quran.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiSpeechRecognitionProviderTest {
    @Test
    fun providerTypeCreatesBothConfiguredProviders() {
        assertEquals("Google Speech", SpeechRecognitionProviderType.GoogleChirp.createProvider().providerName)
        assertEquals("OpenAI", SpeechRecognitionProviderType.OpenAiTranscribe.createProvider().providerName)
        assertEquals(SpeechRecognitionProviderType.GoogleChirp, SpeechRecognitionProviderType.fromName("unknown"))
    }

    @Test
    fun openAiProviderDefaultsToConfiguredTranscribeModel() {
        val provider = OpenAiSpeechRecognitionProvider(apiKey = "test-key")

        assertEquals("OpenAI", provider.providerName)
        assertTrue(provider.modelName.isNotBlank())
    }
}
