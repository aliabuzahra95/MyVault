package com.myvault.app.data.quran.memorization

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorizationDashboardAiDataTest {
    @Test
    fun continueItemUsesLatestPersistedAiAttempt() {
        val state = MemorizationUiState(
            attempts = listOf(
                attempt(verseKey = "87:4", surah = 87, ayah = 4, timestamp = 1_000L, grade = QuranMemorizationScoreGrade.REPEAT),
                attempt(verseKey = "87:7", surah = 87, ayah = 7, timestamp = 2_000L, grade = QuranMemorizationScoreGrade.EXCELLENT),
            ),
        )

        val item = state.continueItem

        assertEquals("87:4", item?.record?.verseKey)
        assertEquals("Repeat", item?.latestAttempt?.grade?.label)
    }

    @Test
    fun needsReviewGroupUsesPersistedAiStatus() {
        val state = MemorizationUiState(
            attempts = listOf(
                attempt(verseKey = "87:4", surah = 87, ayah = 4, status = AyahMemorizationStatus.NEEDS_REVIEW),
                attempt(verseKey = "87:5", surah = 87, ayah = 5, status = AyahMemorizationStatus.PASSED),
            ),
            selectedGroup = MemorizationDashboardGroup.NeedsReview,
        )

        assertEquals(1, state.overview.needsReviewCount)
        assertEquals(listOf("87:4"), state.dashboardItems.map { it.record.verseKey })
    }

    private fun attempt(
        verseKey: String,
        surah: Int,
        ayah: Int,
        timestamp: Long = 1_000L,
        grade: QuranMemorizationScoreGrade = QuranMemorizationScoreGrade.REPEAT,
        status: AyahMemorizationStatus = if (grade == QuranMemorizationScoreGrade.EXCELLENT || grade == QuranMemorizationScoreGrade.GOOD) {
            AyahMemorizationStatus.PASSED
        } else {
            AyahMemorizationStatus.INCORRECT
        },
    ): QuranMemorizationSavedAttempt =
        QuranMemorizationSavedAttempt(
            attemptId = "$verseKey-$timestamp",
            timestampMs = timestamp,
            surahNumber = surah,
            ayahNumber = ayah,
            verseKey = verseKey,
            durationMs = 3_000L,
            providerName = "Google Speech",
            modelName = "chirp_3",
            latencyMs = 1_500L,
            transcript = "",
            normalizedTranscript = "",
            recognizedCount = 4,
            missingCount = 0,
            extraCount = 0,
            repeatedCount = 0,
            unknownCount = 0,
            confidence = 0.95f,
            overallScore = if (grade == QuranMemorizationScoreGrade.EXCELLENT) 100 else 70,
            grade = grade,
            recognizedPercentage = 1f,
            scoreCalculationVersion = QURAN_MEMORIZATION_SCORE_VERSION,
            status = status,
            transcriptionSucceeded = true,
            errorMessage = null,
            expectedWordIds = listOf("$verseKey:1"),
            matchedWordIds = listOf("$verseKey:1"),
            missingWordIds = emptyList(),
            extraTranscriptWords = emptyList(),
            repeatedTranscriptWords = emptyList(),
        )
}
