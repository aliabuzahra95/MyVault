package com.myvault.app.widget.quran

import org.junit.Assert.assertEquals
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
}
