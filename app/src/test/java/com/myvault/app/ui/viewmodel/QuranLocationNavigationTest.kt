package com.myvault.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranLocationNavigationTest {
    @Test
    fun `dashboard continue preserves an exact valid Quran reference`() {
        val continueLocation = HomeQuranContinue(
            surahName = "Al-Anfal",
            surahNumber = 8,
            ayahNumber = 41,
        )

        assertEquals("8:41", continueLocation.verseKey)
        assertEquals("8:41", normalizedQuranVerseKey(8, 41, 75))
    }

    @Test
    fun `stale ayah falls back inside the valid saved Surah`() {
        assertEquals("8:75", normalizedQuranVerseKey(8, 999, 75))
        assertEquals("8:1", normalizedQuranVerseKey(8, 0, 75))
    }
}
