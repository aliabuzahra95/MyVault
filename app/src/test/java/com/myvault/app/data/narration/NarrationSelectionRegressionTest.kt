package com.myvault.app.data.narration

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationSelectionRegressionTest {
    @Test
    fun longSourceUsesProviderSafeChunksForBothGeminiModels() {
        val director = NarrationDirector(BilingualTextSegmenter())
        val body = (1..700).joinToString("\n\n") {
            "A long narration paragraph with enough words to exceed the former document limit."
        }
        val plan = director.createPlan("long-note", "Long note", body)
        assertTrue(plan.fullSpokenText.length > 25_000)
        for (provider in listOf(NarrationProvider.GeminiFlashLite, NarrationProvider.GeminiFlash)) {
            val chunks = director.planToChunks(plan)
            assertTrue(chunks.size > 1)
            assertTrue(chunks.all { it.text.length <= GeminiNarrationConfig.MAX_CHARS_PER_CHUNK })
            assertEquals(plan.units.map { it.spokenText }.joinToString(" ").words(), chunks.joinToString(" ") { it.text }.words())
            assertEquals(provider, NarrationProvider.fromModel(provider.defaultModel))
        }
    }

    @Test
    fun repositoriesDoNotRejectTheWholeSourceBeforeChunking() {
        for (name in listOf("GeminiTtsRepository", "TtsRepository")) {
            val source = File("src/main/java/com/myvault/app/data/narration/$name.kt").readText()
            assertFalse(source.contains("MAX_TOTAL_CHARS"))
            assertFalse(source.contains("too long for Listen Mode"))
        }
    }

    @Test
    fun selectionChangesPlayerProviderAndActualModelForEveryProvider() {
        var state = NarrationUiState()
        for (provider in NarrationProvider.entries) {
            state = state.preparing("note", "Note", provider, provider.resolveVoice(null))
            assertEquals(provider, state.provider)
            assertEquals(provider, NarrationProvider.fromModel(state.provider.defaultModel))
            assertEquals(provider.resolveVoice(null), state.voice)
            assertEquals(NarrationPlaybackStatus.Preparing, state.status)
        }
        assertEquals("gemini-3.8-flash-tts", NarrationProvider.GeminiFlash.defaultModel)
        assertEquals("gemini-3.8-flash-lite-tts", NarrationProvider.GeminiFlashLite.defaultModel)
        assertEquals("gpt-4o-mini-tts", NarrationProvider.OpenAi.defaultModel)
    }

    @Test
    fun providerSwitchUsesOnlyTheCorrectVoiceFamily() {
        val gemini = NarrationUiState().preparing("note", "Note", NarrationProvider.GeminiFlash, "Kore")
        assertEquals("Kore", gemini.voice)
        val openAi = gemini.preparing("note", "Note", NarrationProvider.OpenAi, gemini.voice)
        assertEquals("cedar", openAi.voice)
        assertEquals(listOf("cedar", "marin"), openAi.provider.voiceOptions)
        assertFalse(openAi.provider.voiceOptions.contains("Puck"))
        assertTrue(NarrationProvider.Device.voiceOptions.isEmpty())
        assertEquals("device", NarrationProvider.Device.resolveVoice("Kore"))
        assertEquals(AzureNarrationConfig.VoiceOptions, NarrationProvider.Azure.voiceOptions)
        assertEquals("Kore", NarrationProvider.GeminiFlashLite.resolveVoice(gemini.voice))
    }

    @Test
    fun savedGeminiProviderAndVoiceAreRestoredWithoutChangingFamily() {
        val provider = NarrationProvider.fromStoredValue(NarrationProvider.GeminiFlash.storedValue)
        val reopened = NarrationUiState().preparing("note", "Note", provider, "Kore")
        assertEquals(NarrationProvider.GeminiFlash, reopened.provider)
        assertEquals("Kore", reopened.voice)
        val preferences = File("src/main/java/com/myvault/app/data/preferences/VaultPreferences.kt").readText()
        assertTrue(preferences.contains("preferences[Keys.GeminiSpeechModel] = provider.defaultModel"))
        assertTrue(preferences.contains("preferences[Keys.GeminiSpeechVoice] = selectedVoice"))
    }

    private fun String.words() = split(Regex("\\s+")).filter(String::isNotBlank)
}
