package com.myvault.app.data.quran.memorization

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranMemorizationScoreEngineTest {
    @Test
    fun perfectRecitationScoresExcellent() {
        val score = score(expected = 4, recognized = 4)

        assertEquals(100, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.EXCELLENT, score.grade)
        assertEquals(1.0f, score.recognizedPercentage, 0.0001f)
        assertEquals(QURAN_MEMORIZATION_SCORE_VERSION, score.calculationVersion)
    }

    @Test
    fun oneMissingWordInVeryShortAyahIsSevere() {
        val score = score(expected = 3, recognized = 2, missing = 1)

        assertEquals(33, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.REPEAT, score.grade)
    }

    @Test
    fun oneMissingWordInLongAyahIsProportional() {
        val score = score(expected = 40, recognized = 39, missing = 1)

        assertEquals(95, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.GOOD, score.grade)
    }

    @Test
    fun repeatedWordsAffectTheScore() {
        val score = score(expected = 4, recognized = 4, repeated = 1)

        assertEquals(80, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.NEEDS_REVIEW, score.grade)
    }

    @Test
    fun extraWordsAffectTheScore() {
        val score = score(expected = 4, recognized = 4, extra = 1)

        assertEquals(78, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.NEEDS_REVIEW, score.grade)
    }

    @Test
    fun mixedMistakesScorePoorly() {
        val score = score(
            expected = 8,
            recognized = 6,
            missing = 1,
            extra = 1,
            repeated = 1,
            unknown = 1,
        )

        assertEquals(30, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.REPEAT, score.grade)
    }

    @Test
    fun unknownTranscriptScoresZero() {
        val score = score(
            expected = 4,
            recognized = 0,
            transcriptionSucceeded = false,
            analysisSucceeded = false,
        )

        assertEquals(0, score.overallScore)
        assertEquals(QuranMemorizationScoreGrade.REPEAT, score.grade)
        assertEquals(0.0f, score.recognizedPercentage, 0.0001f)
    }

    @Test
    fun scoringIsDeterministic() {
        val first = score(expected = 12, recognized = 10, missing = 1, extra = 1)
        val second = score(expected = 12, recognized = 10, missing = 1, extra = 1)

        assertEquals(first, second)
    }

    private fun score(
        expected: Int,
        recognized: Int,
        missing: Int = 0,
        extra: Int = 0,
        repeated: Int = 0,
        unknown: Int = 0,
        transcriptionSucceeded: Boolean = true,
        analysisSucceeded: Boolean = true,
    ): QuranMemorizationScore =
        QuranMemorizationScoreEngine.score(
            expectedWordCount = expected,
            recognizedCount = recognized,
            missingCount = missing,
            extraCount = extra,
            repeatedCount = repeated,
            unknownCount = unknown,
            transcriptionSucceeded = transcriptionSucceeded,
            analysisSucceeded = analysisSucceeded,
        )
}
