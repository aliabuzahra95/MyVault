package com.myvault.app.data.quran.memorization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranMemorizationAttemptPersistenceTest {
    @Test
    fun perfectHighConfidenceAttemptDerivesPassedStatus() {
        val attempt = attempt(
            missingCount = 0,
            extraCount = 0,
            repeatedCount = 0,
            unknownCount = 0,
            confidence = 0.94f,
            perfectMatch = true,
        )

        assertEquals(AyahMemorizationStatus.PASSED, attempt.deriveAyahMemorizationStatus())
    }

    @Test
    fun attemptWithMistakesDerivesIncorrectStatusWhenScoreIsRepeat() {
        val attempt = attempt(missingCount = 1, perfectMatch = false)

        assertEquals(AyahMemorizationStatus.INCORRECT, attempt.deriveAyahMemorizationStatus())
    }

    @Test
    fun failedTranscriptionDerivesUnknownStatus() {
        val attempt = attempt(
            transcriptionSucceeded = false,
            errorMessage = "Google Speech credentials were rejected.",
            perfectMatch = false,
        )

        assertEquals(AyahMemorizationStatus.UNKNOWN, attempt.deriveAyahMemorizationStatus())
    }

    @Test
    fun savedAttemptRoundTripsThroughPreferenceEntry() {
        val saved = attempt(
            transcript = "بسم الله الرحمن الرحيم",
            normalizedTranscript = "بسم الله الرحمن الرحيم",
            expectedWordIds = listOf("1:1:1", "1:1:2"),
            matchedWordIds = listOf("1:1:1"),
            missingWordIds = listOf("1:1:2"),
            extraTranscriptWords = listOf("زيادة"),
            repeatedTranscriptWords = listOf("الله"),
            missingCount = 1,
            extraCount = 1,
            repeatedCount = 1,
            perfectMatch = false,
        ).toSavedAttempt()

        val restored = saved.toAttemptPreferenceEntry().toQuranMemorizationSavedAttemptOrNull()

        assertEquals(saved, restored)
        assertEquals(AyahMemorizationStatus.INCORRECT, restored?.status)
    }

    @Test
    fun malformedPreferenceEntryIsIgnored() {
        assertNull("not-json".toQuranMemorizationSavedAttemptOrNull())
    }

    private fun attempt(
        attemptId: String = "1:1-1234",
        timestampMs: Long = 1234L,
        transcript: String = "بسم الله",
        normalizedTranscript: String = "بسم الله",
        recognizedCount: Int = 2,
        missingCount: Int = 0,
        extraCount: Int = 0,
        repeatedCount: Int = 0,
        unknownCount: Int = 0,
        confidence: Float? = 0.94f,
        transcriptionSucceeded: Boolean = true,
        perfectMatch: Boolean = false,
        errorMessage: String? = null,
        expectedWordIds: List<String> = listOf("1:1:1", "1:1:2"),
        matchedWordIds: List<String> = listOf("1:1:1", "1:1:2"),
        missingWordIds: List<String> = emptyList(),
        extraTranscriptWords: List<String> = emptyList(),
        repeatedTranscriptWords: List<String> = emptyList(),
    ): QuranMemorizationAttempt =
        QuranMemorizationScoreEngine.score(
            expectedWordCount = expectedWordIds.size,
            recognizedCount = recognizedCount,
            missingCount = missingCount,
            extraCount = extraCount,
            repeatedCount = repeatedCount,
            unknownCount = unknownCount,
            transcriptionSucceeded = transcriptionSucceeded,
            analysisSucceeded = transcriptionSucceeded,
        ).let { score ->
            QuranMemorizationAttempt(
            attemptId = attemptId,
            timestampMs = timestampMs,
            surahNumber = 1,
            ayahNumber = 1,
            verseKey = "1:1",
            durationMs = 4_000L,
            providerName = "Google Speech",
            modelName = "chirp_3",
            latencyMs = 1_500L,
            transcript = transcript,
            normalizedTranscript = normalizedTranscript,
            recognizedCount = recognizedCount,
            missingCount = missingCount,
            extraCount = extraCount,
            repeatedCount = repeatedCount,
            unknownCount = unknownCount,
            confidence = confidence,
            overallScore = score.overallScore,
            grade = score.grade,
            recognizedPercentage = score.recognizedPercentage,
            scoreCalculationVersion = score.calculationVersion,
            transcriptionSucceeded = transcriptionSucceeded,
            perfectMatch = perfectMatch,
            errorMessage = errorMessage,
            expectedWordIds = expectedWordIds,
            matchedWordIds = matchedWordIds,
            missingWordIds = missingWordIds,
            extraTranscriptWords = extraTranscriptWords,
            repeatedTranscriptWords = repeatedTranscriptWords,
            expectedComparisonKeys = emptyList(),
            transcriptComparisonKeys = emptyList(),
            diagnostics = emptyList(),
        )
        }
}
