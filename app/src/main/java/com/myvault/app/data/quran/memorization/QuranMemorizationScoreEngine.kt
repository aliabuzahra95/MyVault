package com.myvault.app.data.quran.memorization

import kotlin.math.roundToInt

const val QURAN_MEMORIZATION_SCORE_VERSION = "quran_memorization_score_v1"

enum class QuranMemorizationScoreGrade(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    NEEDS_REVIEW("Needs Review"),
    REPEAT("Repeat"),
}

data class QuranMemorizationScore(
    val overallScore: Int,
    val grade: QuranMemorizationScoreGrade,
    val recognizedPercentage: Float,
    val calculationVersion: String = QURAN_MEMORIZATION_SCORE_VERSION,
)

object QuranMemorizationScoreEngine {
    fun score(
        expectedWordCount: Int,
        recognizedCount: Int,
        missingCount: Int,
        extraCount: Int,
        repeatedCount: Int,
        unknownCount: Int,
        transcriptionSucceeded: Boolean,
        analysisSucceeded: Boolean,
    ): QuranMemorizationScore {
        val safeExpectedCount = expectedWordCount.coerceAtLeast(0)
        if (!transcriptionSucceeded || !analysisSucceeded || safeExpectedCount == 0) {
            return QuranMemorizationScore(
                overallScore = 0,
                grade = QuranMemorizationScoreGrade.REPEAT,
                recognizedPercentage = 0f,
            )
        }

        val recognizedPercentage = (recognizedCount.coerceAtLeast(0).toFloat() / safeExpectedCount.toFloat())
            .coerceIn(0f, 1f)
        val proportionalPenalty = (
            missingCount.coerceAtLeast(0) * 1.0f +
                unknownCount.coerceAtLeast(0) * 0.9f +
                extraCount.coerceAtLeast(0) * 0.9f +
                repeatedCount.coerceAtLeast(0) * 0.8f
        ) / safeExpectedCount.toFloat()

        val score = ((recognizedPercentage - proportionalPenalty) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        return QuranMemorizationScore(
            overallScore = score,
            grade = gradeFor(score),
            recognizedPercentage = recognizedPercentage,
        )
    }

    fun gradeFor(score: Int): QuranMemorizationScoreGrade =
        when (score.coerceIn(0, 100)) {
            in 98..100 -> QuranMemorizationScoreGrade.EXCELLENT
            in 90..97 -> QuranMemorizationScoreGrade.GOOD
            in 75..89 -> QuranMemorizationScoreGrade.NEEDS_REVIEW
            else -> QuranMemorizationScoreGrade.REPEAT
        }
}
