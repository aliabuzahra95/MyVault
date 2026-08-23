package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import org.json.JSONArray
import org.json.JSONObject

enum class QuranSurahMemorizationTestMode(
    val label: String,
    val description: String,
) {
    CONTINUE_REVISION(
        label = "Continue Revision",
        description = "Analyse only the ayahs you reached.",
    ),
    FULL_SURAH_TEST(
        label = "Full Surah Test",
        description = "Treat the whole surah as an exam.",
    ),
}

data class QuranSurahMemorizationAnalysis(
    val surahNumber: Int,
    val surahName: String,
    val totalAyahs: Int,
    val totalExpectedWords: Int,
    val recognizedCount: Int,
    val missingCount: Int,
    val extraCount: Int,
    val repeatedCount: Int,
    val unknownCount: Int,
    val confidence: Float?,
    val overallScore: Int,
    val grade: QuranMemorizationScoreGrade,
    val recognizedPercentage: Float,
    val scoreCalculationVersion: String,
    val ayahResults: List<QuranSurahMemorizationAyahResult>,
    val ayahsNeedingReview: List<String>,
    val missingWordIds: List<String>,
    val extraTranscriptWords: List<String>,
    val repeatedTranscriptWords: List<String>,
    val alignmentPath: List<QuranMemorizationAlignmentPathStep> = emptyList(),
    val testMode: QuranSurahMemorizationTestMode = QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
)

data class QuranSurahMemorizationAyahResult(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val expectedWordCount: Int,
    val recognizedCount: Int,
    val missingCount: Int,
    val extraCount: Int,
    val repeatedCount: Int,
    val unknownCount: Int,
    val overallScore: Int,
    val grade: QuranMemorizationScoreGrade,
    val status: AyahMemorizationStatus,
    val missingWordIds: List<String>,
    val wordResults: List<QuranSurahMemorizationAyahWordResult> = emptyList(),
    val extraTranscriptWords: List<String> = emptyList(),
    val repeatedTranscriptWords: List<String> = emptyList(),
)

data class QuranSurahMemorizationAyahWordResult(
    val wordId: String,
    val displayedWord: String,
    val state: QuranMemorizationWordState,
    val matchedTranscriptWord: String? = null,
)

data class QuranSurahMemorizationAttempt(
    val attemptId: String,
    val timestampMs: Long,
    val surahNumber: Int,
    val surahName: String,
    val totalAyahs: Int,
    val durationMs: Long,
    val providerName: String,
    val modelName: String,
    val latencyMs: Long,
    val transcript: String,
    val normalizedTranscript: String,
    val overallScore: Int,
    val grade: QuranMemorizationScoreGrade,
    val recognizedPercentage: Float,
    val scoreCalculationVersion: String,
    val transcriptionSucceeded: Boolean,
    val errorMessage: String?,
    val ayahResults: List<QuranSurahMemorizationAyahResult>,
    val ayahsNeedingReview: List<String>,
    val missingWordIds: List<String>,
    val extraTranscriptWords: List<String>,
    val repeatedTranscriptWords: List<String>,
    val alignmentPath: List<QuranMemorizationAlignmentPathStep> = emptyList(),
    val testMode: QuranSurahMemorizationTestMode = QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
)

data class QuranSurahMemorizationSavedAttempt(
    val attemptId: String,
    val timestampMs: Long,
    val surahNumber: Int,
    val surahName: String,
    val totalAyahs: Int,
    val durationMs: Long,
    val providerName: String,
    val modelName: String,
    val latencyMs: Long,
    val transcript: String,
    val normalizedTranscript: String,
    val overallScore: Int,
    val grade: QuranMemorizationScoreGrade,
    val recognizedPercentage: Float,
    val scoreCalculationVersion: String,
    val transcriptionSucceeded: Boolean,
    val errorMessage: String?,
    val ayahResults: List<QuranSurahMemorizationAyahResult>,
    val ayahsNeedingReview: List<String>,
    val missingWordIds: List<String>,
    val extraTranscriptWords: List<String>,
    val repeatedTranscriptWords: List<String>,
    val testMode: QuranSurahMemorizationTestMode = QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
)

object QuranSurahMemorizationTestEngine {
    fun analyze(
        surah: SurahInfo,
        ayahs: List<QuranAyah>,
        speechResult: SpeechRecognitionResult,
        testMode: QuranSurahMemorizationTestMode = QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
    ): QuranSurahMemorizationAnalysis? {
        if (!speechResult.isSuccess || ayahs.isEmpty()) return null

        val allExpectedWords = ayahs.flatMap { it.words }
        val initialAnalysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "${surah.num}:surah",
            expectedWords = allExpectedWords,
            transcript = speechResult.transcript,
            normalizedTranscript = speechResult.normalizedTranscript,
            wordTimestamps = speechResult.wordTimestamps,
            speechConfidence = speechResult.confidence,
        )
        val scopedAyahs = if (testMode == QuranSurahMemorizationTestMode.CONTINUE_REVISION) {
            val lastAttemptedAyah = initialAnalysis.expectedWords
                .filter { it.state == QuranMemorizationWordState.CORRECT || it.state == QuranMemorizationWordState.REPEATED }
                .maxOfOrNull { it.word.ayahNumber }
                ?: ayahs.first().ayahNumber
            ayahs.filter { it.ayahNumber <= lastAttemptedAyah.coerceIn(1, surah.ayat) }.ifEmpty { listOf(ayahs.first()) }
        } else {
            ayahs
        }
        val fullAnalysis = if (scopedAyahs.size == ayahs.size) {
            initialAnalysis
        } else {
            QuranMemorizationAnalysisEngine.analyze(
                verseKey = "${surah.num}:revision",
                expectedWords = scopedAyahs.flatMap { it.words },
                transcript = speechResult.transcript,
                normalizedTranscript = speechResult.normalizedTranscript,
                wordTimestamps = speechResult.wordTimestamps,
                speechConfidence = speechResult.confidence,
            )
        }
        val extraByRelatedAyah = fullAnalysis.extraWords.groupBy { extra ->
            extra.relatedWordId?.split(':')?.getOrNull(1)?.toIntOrNull()
        }
        val ayahResults = scopedAyahs.map { ayah ->
            val expected = fullAnalysis.expectedWords.filter { it.word.ayahNumber == ayah.ayahNumber }
            val extras = extraByRelatedAyah[ayah.ayahNumber].orEmpty()
            val missing = expected.filter { it.state == QuranMemorizationWordState.MISSING }
            val unknown = expected.count { it.state == QuranMemorizationWordState.UNKNOWN }
            val recognized = expected.count { it.state == QuranMemorizationWordState.CORRECT }
            val repeated = extras.count { it.state == QuranMemorizationWordState.REPEATED }
            val extra = extras.count { it.state == QuranMemorizationWordState.EXTRA }
            val score = QuranMemorizationScoreEngine.score(
                expectedWordCount = expected.size,
                recognizedCount = recognized,
                missingCount = missing.size,
                extraCount = extra,
                repeatedCount = repeated,
                unknownCount = unknown,
                transcriptionSucceeded = true,
                analysisSucceeded = true,
            )
            QuranSurahMemorizationAyahResult(
                verseKey = ayah.verseKey,
                surahNumber = ayah.surahNumber,
                ayahNumber = ayah.ayahNumber,
                expectedWordCount = expected.size,
                recognizedCount = recognized,
                missingCount = missing.size,
                extraCount = extra,
                repeatedCount = repeated,
                unknownCount = unknown,
                overallScore = score.overallScore,
                grade = score.grade,
                status = score.grade.toAyahStatus(),
                missingWordIds = missing.map { it.word.wordId },
                wordResults = expected.map { wordAnalysis ->
                    QuranSurahMemorizationAyahWordResult(
                        wordId = wordAnalysis.word.wordId,
                        displayedWord = wordAnalysis.word.arabicText,
                        state = wordAnalysis.state,
                        matchedTranscriptWord = wordAnalysis.matchedTranscriptWord?.text,
                    )
                },
                extraTranscriptWords = extras
                    .filter { it.state == QuranMemorizationWordState.EXTRA }
                    .map { it.recognizedWord.text },
                repeatedTranscriptWords = extras
                    .filter { it.state == QuranMemorizationWordState.REPEATED }
                    .map { it.recognizedWord.text },
            )
        }
        val score = QuranMemorizationScoreEngine.score(
            expectedWordCount = fullAnalysis.expectedWordCount,
            recognizedCount = fullAnalysis.recognizedWordCount,
            missingCount = fullAnalysis.missingWordCount,
            extraCount = fullAnalysis.extraWordCount,
            repeatedCount = fullAnalysis.repeatedWordCount,
            unknownCount = fullAnalysis.unknownWordCount,
            transcriptionSucceeded = true,
            analysisSucceeded = true,
        )
        val ayahsNeedingReview = ayahResults
            .filter { it.status != AyahMemorizationStatus.PASSED }
            .map { it.verseKey }

        return QuranSurahMemorizationAnalysis(
            surahNumber = surah.num,
            surahName = surah.name,
            totalAyahs = scopedAyahs.size,
            totalExpectedWords = fullAnalysis.expectedWordCount,
            recognizedCount = fullAnalysis.recognizedWordCount,
            missingCount = fullAnalysis.missingWordCount,
            extraCount = fullAnalysis.extraWordCount,
            repeatedCount = fullAnalysis.repeatedWordCount,
            unknownCount = fullAnalysis.unknownWordCount,
            confidence = fullAnalysis.confidence,
            overallScore = score.overallScore,
            grade = score.grade,
            recognizedPercentage = score.recognizedPercentage,
            scoreCalculationVersion = score.calculationVersion,
            ayahResults = ayahResults,
            ayahsNeedingReview = ayahsNeedingReview,
            missingWordIds = fullAnalysis.expectedWords
                .filter { it.state == QuranMemorizationWordState.MISSING }
                .map { it.word.wordId },
            extraTranscriptWords = fullAnalysis.extraWords
                .filter { it.state == QuranMemorizationWordState.EXTRA }
                .map { it.recognizedWord.text },
            repeatedTranscriptWords = fullAnalysis.extraWords
                .filter { it.state == QuranMemorizationWordState.REPEATED }
                .map { it.recognizedWord.text },
            alignmentPath = fullAnalysis.alignmentPath,
            testMode = testMode,
        )
    }
}

object QuranSurahMemorizationAttemptFactory {
    fun from(
        surah: SurahInfo,
        ayahs: List<QuranAyah>,
        durationMs: Long,
        speechResult: SpeechRecognitionResult,
        analysis: QuranSurahMemorizationAnalysis?,
        testMode: QuranSurahMemorizationTestMode = QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
        timestampMs: Long = System.currentTimeMillis(),
    ): QuranSurahMemorizationAttempt {
        val fallbackScore = QuranMemorizationScoreEngine.score(
            expectedWordCount = ayahs.sumOf { it.words.size },
            recognizedCount = analysis?.recognizedCount ?: 0,
            missingCount = analysis?.missingCount ?: ayahs.sumOf { it.words.size }.takeIf { speechResult.isSuccess } ?: 0,
            extraCount = analysis?.extraCount ?: 0,
            repeatedCount = analysis?.repeatedCount ?: 0,
            unknownCount = analysis?.unknownCount ?: 0,
            transcriptionSucceeded = speechResult.isSuccess,
            analysisSucceeded = analysis != null,
        )
        return QuranSurahMemorizationAttempt(
            attemptId = "${surah.num}:surah-$timestampMs",
            timestampMs = timestampMs,
            surahNumber = surah.num,
            surahName = surah.name,
            totalAyahs = ayahs.size,
            durationMs = durationMs,
            providerName = speechResult.providerName,
            modelName = speechResult.modelName,
            latencyMs = speechResult.latencyMs,
            transcript = speechResult.transcript,
            normalizedTranscript = speechResult.normalizedTranscript,
            overallScore = analysis?.overallScore ?: fallbackScore.overallScore,
            grade = analysis?.grade ?: fallbackScore.grade,
            recognizedPercentage = analysis?.recognizedPercentage ?: fallbackScore.recognizedPercentage,
            scoreCalculationVersion = analysis?.scoreCalculationVersion ?: fallbackScore.calculationVersion,
            transcriptionSucceeded = speechResult.isSuccess,
            errorMessage = speechResult.errorMessage,
            ayahResults = analysis?.ayahResults.orEmpty(),
            ayahsNeedingReview = analysis?.ayahsNeedingReview.orEmpty(),
            missingWordIds = analysis?.missingWordIds.orEmpty(),
            extraTranscriptWords = analysis?.extraTranscriptWords.orEmpty(),
            repeatedTranscriptWords = analysis?.repeatedTranscriptWords.orEmpty(),
            alignmentPath = analysis?.alignmentPath.orEmpty(),
            testMode = analysis?.testMode ?: testMode,
        )
    }
}

fun QuranSurahMemorizationAttempt.toSavedAttempt(): QuranSurahMemorizationSavedAttempt =
    QuranSurahMemorizationSavedAttempt(
        attemptId = attemptId,
        timestampMs = timestampMs,
        surahNumber = surahNumber,
        surahName = surahName,
        totalAyahs = totalAyahs,
        durationMs = durationMs,
        providerName = providerName,
        modelName = modelName,
        latencyMs = latencyMs,
        transcript = transcript,
        normalizedTranscript = normalizedTranscript,
        overallScore = overallScore,
        grade = grade,
        recognizedPercentage = recognizedPercentage,
        scoreCalculationVersion = scoreCalculationVersion,
        transcriptionSucceeded = transcriptionSucceeded,
        errorMessage = errorMessage,
        ayahResults = ayahResults,
        ayahsNeedingReview = ayahsNeedingReview,
        missingWordIds = missingWordIds,
        extraTranscriptWords = extraTranscriptWords,
        repeatedTranscriptWords = repeatedTranscriptWords,
        testMode = testMode,
    )

fun QuranSurahMemorizationSavedAttempt.toSurahAttemptPreferenceEntry(): String =
    JSONObject()
        .put("attemptId", attemptId)
        .put("timestampMs", timestampMs)
        .put("surahNumber", surahNumber)
        .put("surahName", surahName)
        .put("totalAyahs", totalAyahs)
        .put("durationMs", durationMs)
        .put("providerName", providerName)
        .put("modelName", modelName)
        .put("latencyMs", latencyMs)
        .put("transcript", transcript)
        .put("normalizedTranscript", normalizedTranscript)
        .put("overallScore", overallScore)
        .put("grade", grade.name)
        .put("recognizedPercentage", recognizedPercentage)
        .put("scoreCalculationVersion", scoreCalculationVersion)
        .put("transcriptionSucceeded", transcriptionSucceeded)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)
        .put("ayahResults", ayahResults.toJsonArray())
        .put("ayahsNeedingReview", ayahsNeedingReview.toStringJsonArray())
        .put("missingWordIds", missingWordIds.toStringJsonArray())
        .put("extraTranscriptWords", extraTranscriptWords.toStringJsonArray())
        .put("repeatedTranscriptWords", repeatedTranscriptWords.toStringJsonArray())
        .put("testMode", testMode.name)
        .toString()

fun String.toQuranSurahMemorizationSavedAttemptOrNull(): QuranSurahMemorizationSavedAttempt? =
    runCatching {
        val json = JSONObject(this)
        val surahNumber = json.optInt("surahNumber", 0)
        if (surahNumber <= 0) return null
        QuranSurahMemorizationSavedAttempt(
            attemptId = json.optString("attemptId").ifBlank { "$surahNumber:surah-${json.optLong("timestampMs", 0L)}" },
            timestampMs = json.optLong("timestampMs", 0L).coerceAtLeast(0L),
            surahNumber = surahNumber,
            surahName = json.optString("surahName"),
            totalAyahs = json.optInt("totalAyahs", 0).coerceAtLeast(0),
            durationMs = json.optLong("durationMs", 0L).coerceAtLeast(0L),
            providerName = json.optString("providerName"),
            modelName = json.optString("modelName"),
            latencyMs = json.optLong("latencyMs", 0L).coerceAtLeast(0L),
            transcript = json.optString("transcript"),
            normalizedTranscript = json.optString("normalizedTranscript"),
            overallScore = json.optInt("overallScore", 0).coerceIn(0, 100),
            grade = json.optString("grade").toQuranMemorizationScoreGradeOrNull() ?: QuranMemorizationScoreGrade.REPEAT,
            recognizedPercentage = json.optDouble("recognizedPercentage", 0.0).toFloat().coerceIn(0f, 1f),
            scoreCalculationVersion = json.optString("scoreCalculationVersion").ifBlank { QURAN_MEMORIZATION_SCORE_VERSION },
            transcriptionSucceeded = json.optBoolean("transcriptionSucceeded", false),
            errorMessage = if (json.isNull("errorMessage")) null else json.optString("errorMessage"),
            ayahResults = json.optJSONArray("ayahResults").toAyahResults(),
            ayahsNeedingReview = json.optJSONArray("ayahsNeedingReview").toStringList(),
            missingWordIds = json.optJSONArray("missingWordIds").toStringList(),
            extraTranscriptWords = json.optJSONArray("extraTranscriptWords").toStringList(),
            repeatedTranscriptWords = json.optJSONArray("repeatedTranscriptWords").toStringList(),
            testMode = json.optString("testMode").toQuranSurahMemorizationTestModeOrNull()
                ?: QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
        )
    }.getOrNull()

private fun QuranMemorizationScoreGrade.toAyahStatus(): AyahMemorizationStatus =
    when (this) {
        QuranMemorizationScoreGrade.EXCELLENT,
        QuranMemorizationScoreGrade.GOOD -> AyahMemorizationStatus.PASSED
        QuranMemorizationScoreGrade.NEEDS_REVIEW -> AyahMemorizationStatus.NEEDS_REVIEW
        QuranMemorizationScoreGrade.REPEAT -> AyahMemorizationStatus.INCORRECT
    }

private fun List<QuranSurahMemorizationAyahResult>.toJsonArray(): JSONArray =
    JSONArray().also { array ->
        forEach { result ->
            array.put(
                JSONObject()
                    .put("verseKey", result.verseKey)
                    .put("surahNumber", result.surahNumber)
                    .put("ayahNumber", result.ayahNumber)
                    .put("expectedWordCount", result.expectedWordCount)
                    .put("recognizedCount", result.recognizedCount)
                    .put("missingCount", result.missingCount)
                    .put("extraCount", result.extraCount)
                    .put("repeatedCount", result.repeatedCount)
                    .put("unknownCount", result.unknownCount)
                    .put("overallScore", result.overallScore)
                    .put("grade", result.grade.name)
                    .put("status", result.status.name)
                    .put("missingWordIds", result.missingWordIds.toStringJsonArray())
                    .put("wordResults", result.wordResults.toWordResultsJsonArray())
                    .put("extraTranscriptWords", result.extraTranscriptWords.toStringJsonArray())
                    .put("repeatedTranscriptWords", result.repeatedTranscriptWords.toStringJsonArray()),
            )
        }
    }

private fun List<QuranSurahMemorizationAyahWordResult>.toWordResultsJsonArray(): JSONArray =
    JSONArray().also { array ->
        forEach { result ->
            array.put(
                JSONObject()
                    .put("wordId", result.wordId)
                    .put("displayedWord", result.displayedWord)
                    .put("state", result.state.name)
                    .put("matchedTranscriptWord", result.matchedTranscriptWord ?: JSONObject.NULL),
            )
        }
    }

private fun JSONArray?.toAyahResults(): List<QuranSurahMemorizationAyahResult> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                QuranSurahMemorizationAyahResult(
                    verseKey = item.optString("verseKey"),
                    surahNumber = item.optInt("surahNumber", 0),
                    ayahNumber = item.optInt("ayahNumber", 0),
                    expectedWordCount = item.optInt("expectedWordCount", 0).coerceAtLeast(0),
                    recognizedCount = item.optInt("recognizedCount", 0).coerceAtLeast(0),
                    missingCount = item.optInt("missingCount", 0).coerceAtLeast(0),
                    extraCount = item.optInt("extraCount", 0).coerceAtLeast(0),
                    repeatedCount = item.optInt("repeatedCount", 0).coerceAtLeast(0),
                    unknownCount = item.optInt("unknownCount", 0).coerceAtLeast(0),
                    overallScore = item.optInt("overallScore", 0).coerceIn(0, 100),
                    grade = item.optString("grade").toQuranMemorizationScoreGradeOrNull() ?: QuranMemorizationScoreGrade.REPEAT,
                    status = item.optString("status").toAyahMemorizationStatusOrNull() ?: AyahMemorizationStatus.UNKNOWN,
                    missingWordIds = item.optJSONArray("missingWordIds").toStringList(),
                    wordResults = item.optJSONArray("wordResults").toWordResults(),
                    extraTranscriptWords = item.optJSONArray("extraTranscriptWords").toStringList(),
                    repeatedTranscriptWords = item.optJSONArray("repeatedTranscriptWords").toStringList(),
                ),
            )
        }
    }
}

private fun JSONArray?.toWordResults(): List<QuranSurahMemorizationAyahWordResult> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val wordId = item.optString("wordId")
            val displayedWord = item.optString("displayedWord")
            if (wordId.isBlank() || displayedWord.isBlank()) continue
            add(
                QuranSurahMemorizationAyahWordResult(
                    wordId = wordId,
                    displayedWord = displayedWord,
                    state = item.optString("state").toQuranMemorizationWordStateOrNull() ?: QuranMemorizationWordState.UNKNOWN,
                    matchedTranscriptWord = if (item.isNull("matchedTranscriptWord")) null else item.optString("matchedTranscriptWord"),
                ),
            )
        }
    }
}

private fun List<String>.toStringJsonArray(): JSONArray =
    JSONArray().also { array ->
        forEach { array.put(it) }
    }

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun String.toAyahMemorizationStatusOrNull(): AyahMemorizationStatus? =
    runCatching { AyahMemorizationStatus.valueOf(this) }.getOrNull()

private fun String.toQuranMemorizationScoreGradeOrNull(): QuranMemorizationScoreGrade? =
    runCatching { QuranMemorizationScoreGrade.valueOf(this) }.getOrNull()

private fun String.toQuranSurahMemorizationTestModeOrNull(): QuranSurahMemorizationTestMode? =
    runCatching { QuranSurahMemorizationTestMode.valueOf(this) }.getOrNull()

private fun String.toQuranMemorizationWordStateOrNull(): QuranMemorizationWordState? =
    runCatching { QuranMemorizationWordState.valueOf(this) }.getOrNull()
