package com.myvault.app.ui.viewmodel

import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.memorization.MemorizationRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoriseResumePositionTest {
    private val surah = SurahInfo(2, "Al-Baqara", "البقرة", 286, "Madani", 1)

    @Test
    fun `explicit valid position has highest priority`() {
        assertEquals(155, memoriseResumeAyah(surah, listOf(record(1, memorised = true)), 155))
    }

    @Test
    fun `first ayah after contiguous memorised range is selected`() {
        val records = (1..100).map { record(it, memorised = true) }
        assertEquals(101, memoriseResumeAyah(surah, records, null))
    }

    @Test
    fun `earliest active status is used when there is no contiguous range`() {
        val records = listOf(record(20, inProgress = true), record(8, revision = true))
        assertEquals(8, memoriseResumeAyah(surah, records, null))
    }

    @Test
    fun `untouched Surah begins at first ayah`() {
        assertEquals(1, memoriseResumeAyah(surah, emptyList(), null))
    }

    private fun record(
        ayah: Int,
        memorised: Boolean = false,
        inProgress: Boolean = false,
        revision: Boolean = false,
    ) = MemorizationRecord(
        verseKey = "2:$ayah",
        surahNumber = 2,
        ayahNumber = ayah,
        startedAt = 1L,
        lastReviewedAt = 1L,
        reviewCount = 1,
        memorizedAt = 1L.takeIf { memorised },
        isRevision = revision,
        isWeak = false,
        updatedAt = 1L,
        isMemorising = inProgress,
    )
}
