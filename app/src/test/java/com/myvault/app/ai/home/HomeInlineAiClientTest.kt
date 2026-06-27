package com.myvault.app.ai.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInlineAiClientTest {
    private val client = HomeInlineAiClient()

    @Test
    fun staleGeminiFileDetection_reuploadsExpiredFileUris() {
        val error = HomeInlineAiError.Unknown("Gemini file URI is expired and invalid.")

        assertTrue(client.isLikelyStaleGeminiFileError(error))
    }

    @Test
    fun staleGeminiFileDetection_doesNotTreatAuthErrorsAsStaleFiles() {
        val error = HomeInlineAiError.Unknown("API key or provider permission rejected.")

        assertFalse(client.isLikelyStaleGeminiFileError(error))
    }
}
