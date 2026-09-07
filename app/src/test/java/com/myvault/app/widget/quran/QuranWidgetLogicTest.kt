package com.myvault.app.widget.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranWidgetLogicTest {
    @Test
    fun `responsive buckets adapt across width and height`() {
        assertEquals(QuranWidgetSizeBucket.Compact, quranWidgetSizeBucket(220, 420))
        assertEquals(QuranWidgetSizeBucket.Compact, quranWidgetSizeBucket(400, 150))
        assertEquals(QuranWidgetSizeBucket.Medium, quranWidgetSizeBucket(300, 260))
        assertEquals(QuranWidgetSizeBucket.Large, quranWidgetSizeBucket(412, 460))
        assertEquals(QuranWidgetSizeBucket.ExtraLarge, quranWidgetSizeBucket(500, 620))
    }

    @Test
    fun `previous and next Surah stay inside the canonical catalog`() {
        assertEquals(1, adjacentSurah(1, -1))
        assertEquals(2, adjacentSurah(1, 1))
        assertEquals(113, adjacentSurah(114, -1))
        assertEquals(114, adjacentSurah(114, 1))
    }

    @Test
    fun `invalid widget locations use a safe canonical fallback`() {
        assertEquals(QuranWidgetLocation(1, 1), validatedWidgetLocation(-5, 100))
        assertEquals(QuranWidgetLocation(2, 286), validatedWidgetLocation(2, 999))
        assertEquals(QuranWidgetLocation(67, 1), validatedWidgetLocation(67, 0))
    }

    @Test
    fun `Surah search matches number transliteration and Arabic name`() {
        listOf("91", "Shams", "Ash-Shams", "الشمس").forEach { query ->
            assertEquals(listOf(91), filteredWidgetSurahs(query).map { it.num })
        }
    }

    @Test
    fun `Surah search handles blank and unmatched queries`() {
        assertEquals(114, filteredWidgetSurahs("  ").size)
        assertTrue(filteredWidgetSurahs("not a real Surah").isEmpty())
    }

    @Test
    fun `Arabic font levels stay readable and adapt by widget size`() {
        assertEquals(MIN_ARABIC_FONT_LEVEL, adjustedArabicFontLevel(MIN_ARABIC_FONT_LEVEL, -1))
        assertEquals(MAX_ARABIC_FONT_LEVEL, adjustedArabicFontLevel(MAX_ARABIC_FONT_LEVEL, 1))
        assertEquals(20f, quranWidgetArabicTextSize(QuranWidgetSizeBucket.Compact, MIN_ARABIC_FONT_LEVEL))
        assertEquals(35f, quranWidgetArabicTextSize(QuranWidgetSizeBucket.ExtraLarge, MAX_ARABIC_FONT_LEVEL))
    }

    @Test
    fun `display preferences remain isolated when one widget state changes`() {
        val widgetA = QuranWidgetState(91, QuranWidgetMode.Reader, 1)
        val widgetB = QuranWidgetState(2, QuranWidgetMode.Reader, 1)
        val changedA = widgetA.copy(
            translationEnabled = true,
            arabicFontLevel = MAX_ARABIC_FONT_LEVEL,
            tajweedEnabled = true,
            reciterId = 6,
            reciterName = "Mahmoud Khalil al-Husary",
        )

        assertTrue(changedA.translationEnabled)
        assertEquals(MAX_ARABIC_FONT_LEVEL, changedA.arabicFontLevel)
        assertTrue(changedA.tajweedEnabled)
        assertEquals(6, changedA.reciterId)
        assertEquals(QuranWidgetState(2, QuranWidgetMode.Reader, 1), widgetB)
    }
}
