package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationProgressAndModelTest {

    @Test
    fun testNarrationProgressStalenessDetection() {
        val originalHash = "hash_abc_123"
        val editedHash = "hash_def_456"

        val progress = NarrationProgress(
            sourceId = "note-99",
            cacheKey = "key_99",
            contentHash = originalHash,
            provider = NarrationProvider.GeminiFlashLite.storedValue,
            model = GeminiNarrationConfig.MODEL_FLASH_LITE,
            voice = "Puck",
            positionMs = 45_000L,
            durationMs = 120_000L,
        )

        // Same content -> not stale
        assertFalse(progress.isStaleFor(originalHash))

        // Content altered -> detected as stale!
        assertTrue(progress.isStaleFor(editedHash))
    }

    @Test
    fun testProviderTierBadgesAndDefaults() {
        val flashLite = NarrationProvider.fromStoredValue("gemini_flash_lite")
        assertEquals(NarrationProvider.GeminiFlashLite, flashLite)
        assertEquals("Very low cost • Intelligent", flashLite.tierBadge)
        assertTrue(flashLite.isCloud)

        val flash = NarrationProvider.fromStoredValue("gemini_flash")
        assertEquals(NarrationProvider.GeminiFlash, flash)
        assertEquals("High quality • Low cost", flash.tierBadge)

        val device = NarrationProvider.fromStoredValue("device")
        assertEquals(NarrationProvider.Device, device)
        assertEquals("Free • Offline", device.tierBadge)
        assertFalse(device.isCloud)

        // Default fallback for unknown value should be GeminiFlashLite
        val fallback = NarrationProvider.fromStoredValue("unknown_random_provider")
        assertEquals(NarrationProvider.GeminiFlashLite, fallback)
    }

    @Test
    fun testToAzureProgressCompatibility() {
        val progress = NarrationProgress(
            sourceId = "note-compat",
            cacheKey = "key-compat",
            contentHash = "hash-123",
            positionMs = 15_000L,
            durationMs = 60_000L,
            activeSentence = "An introductory sentence.",
        )

        val azureProgress = progress.toAzureProgress()
        assertEquals("note-compat", azureProgress.sourceId)
        assertEquals("key-compat", azureProgress.cacheKey)
        assertEquals(15_000L, azureProgress.positionMs)
        assertEquals(60_000L, azureProgress.durationMs)
        assertEquals("An introductory sentence.", azureProgress.activeSentence)
    }
}
