package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import org.json.JSONArray
import org.json.JSONObject

data class QuranMemorizationAttempt(
    val attemptId: String,
    val timestampMs: Long,
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val durationMs: Long,
    val providerName: String,
    val modelName: String,
    val latencyMs: Long,
    val transcript: String,
    val normalizedTranscript: String,
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
    val transcriptionSucceeded: Boolean,
    val perfectMatch: Boolean,
    val errorMessage: String?,
    val expectedWordIds: List<String>,
    val matchedWordIds: List<String>,
    val missingWordIds: List<String>,
    val extraTranscriptWords: List<String>,
    val repeatedTranscriptWords: List<String>,
    val expectedComparisonKeys: List<QuranMemorizationAttemptExpectedKey>,
    val transcriptComparisonKeys: List<QuranMemorizationAttemptTranscriptKey>,
    val diagnostics: List<QuranMemorizationMatchDiagnostic>,
    val alignmentPath: List<QuranMemorizationAlignmentPathStep> = emptyList(),
)

data class QuranMemorizationAttemptExpectedKey(
    val wordId: String,
    val displayedWord: String,
    val normalizedDisplayForm: String,
    val comparisonKey: String,
)

data class QuranMemorizationAttemptTranscriptKey(
    val transcriptIndex: Int,
    val originalWord: String,
    val normalizedForm: String,
    val comparisonKey: String,
)

object QuranMemorizationAttemptFactory {
    fun from(
        ayah: QuranAyah,
        durationMs: Long,
        speechResult: SpeechRecognitionResult,
        analysis: QuranMemorizationAnalysis?,
        timestampMs: Long = System.currentTimeMillis(),
    ): QuranMemorizationAttempt {
        val expectedWordIds = ayah.words.map { it.wordId }
        val matchedWordIds = analysis
            ?.expectedWords
            ?.filter { it.matchedTranscriptWord != null }
            ?.map { it.word.wordId }
            .orEmpty()
        val missingWordIds = analysis
            ?.expectedWords
            ?.filter { it.state == QuranMemorizationWordState.MISSING }
            ?.map { it.word.wordId }
            .orEmpty()
        val extraTranscriptWords = analysis
            ?.extraWords
            ?.filter { it.state == QuranMemorizationWordState.EXTRA }
            ?.map { it.recognizedWord.text }
            .orEmpty()
        val repeatedTranscriptWords = analysis
            ?.extraWords
            ?.filter { it.state == QuranMemorizationWordState.REPEATED }
            ?.map { it.recognizedWord.text }
            .orEmpty()
        val diagnostics = buildList {
            analysis?.expectedWords?.mapNotNullTo(this) { it.diagnostic }
            analysis?.extraWords?.mapNotNullTo(this) { it.diagnostic }
        }.distinct()
        val expectedKeys = analysis?.expectedWords?.map {
            QuranMemorizationAttemptExpectedKey(
                wordId = it.word.wordId,
                displayedWord = it.word.arabicText,
                normalizedDisplayForm = it.comparisonWord.normalizedDisplayForm,
                comparisonKey = it.comparisonWord.comparisonForm.comparisonKey,
            )
        }.orEmpty()
        val transcriptKeys = analysis?.recognizedWords?.map {
            QuranMemorizationAttemptTranscriptKey(
                transcriptIndex = it.transcriptIndex,
                originalWord = it.text,
                normalizedForm = it.normalizedText,
                comparisonKey = it.comparisonKey,
            )
        }.orEmpty()

        val score = QuranMemorizationScoreEngine.score(
            expectedWordCount = expectedWordIds.size,
            recognizedCount = analysis?.recognizedWordCount ?: 0,
            missingCount = analysis?.missingWordCount ?: ayah.words.size.takeIf { speechResult.isSuccess } ?: 0,
            extraCount = analysis?.extraWordCount ?: 0,
            repeatedCount = analysis?.repeatedWordCount ?: 0,
            unknownCount = analysis?.unknownWordCount ?: 0,
            transcriptionSucceeded = speechResult.isSuccess,
            analysisSucceeded = analysis != null,
        )

        return QuranMemorizationAttempt(
            attemptId = "${ayah.verseKey}-$timestampMs",
            timestampMs = timestampMs,
            surahNumber = ayah.surahNumber,
            ayahNumber = ayah.ayahNumber,
            verseKey = ayah.verseKey,
            durationMs = durationMs,
            providerName = speechResult.providerName,
            modelName = speechResult.modelName,
            latencyMs = speechResult.latencyMs,
            transcript = speechResult.transcript,
            normalizedTranscript = speechResult.normalizedTranscript,
            recognizedCount = analysis?.recognizedWordCount ?: 0,
            missingCount = analysis?.missingWordCount ?: ayah.words.size.takeIf { speechResult.isSuccess } ?: 0,
            extraCount = analysis?.extraWordCount ?: 0,
            repeatedCount = analysis?.repeatedWordCount ?: 0,
            unknownCount = analysis?.unknownWordCount ?: 0,
            confidence = analysis?.confidence ?: speechResult.confidence,
            overallScore = score.overallScore,
            grade = score.grade,
            recognizedPercentage = score.recognizedPercentage,
            scoreCalculationVersion = score.calculationVersion,
            transcriptionSucceeded = speechResult.isSuccess,
            perfectMatch = analysis?.let {
                it.missingWordCount == 0 &&
                    it.extraWordCount == 0 &&
                    it.repeatedWordCount == 0 &&
                    it.unknownWordCount == 0
            } == true,
            errorMessage = speechResult.errorMessage,
            expectedWordIds = expectedWordIds,
            matchedWordIds = matchedWordIds,
            missingWordIds = missingWordIds,
            extraTranscriptWords = extraTranscriptWords,
            repeatedTranscriptWords = repeatedTranscriptWords,
            expectedComparisonKeys = expectedKeys,
            transcriptComparisonKeys = transcriptKeys,
            diagnostics = diagnostics,
            alignmentPath = analysis?.alignmentPath.orEmpty(),
        )
    }
}

object QuranMemorizationAttemptHistory {
    private const val MAX_ATTEMPTS = 50
    private val attempts = ArrayDeque<QuranMemorizationAttempt>()

    fun record(attempt: QuranMemorizationAttempt) {
        synchronized(attempts) {
            attempts.addFirst(attempt)
            while (attempts.size > MAX_ATTEMPTS) {
                attempts.removeLast()
            }
        }
    }

    fun recent(): List<QuranMemorizationAttempt> =
        synchronized(attempts) {
            attempts.toList()
        }

    fun clear() {
        synchronized(attempts) {
            attempts.clear()
        }
    }
}

data class QuranMemorizationSavedAttempt(
    val attemptId: String,
    val timestampMs: Long,
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val durationMs: Long,
    val providerName: String,
    val modelName: String,
    val latencyMs: Long,
    val transcript: String,
    val normalizedTranscript: String,
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
    val status: AyahMemorizationStatus,
    val transcriptionSucceeded: Boolean,
    val errorMessage: String?,
    val expectedWordIds: List<String>,
    val matchedWordIds: List<String>,
    val missingWordIds: List<String>,
    val extraTranscriptWords: List<String>,
    val repeatedTranscriptWords: List<String>,
)

fun QuranMemorizationAttempt.deriveAyahMemorizationStatus(): AyahMemorizationStatus =
    deriveAyahMemorizationStatus(
        transcriptionSucceeded = transcriptionSucceeded,
        errorMessage = errorMessage,
        grade = grade,
    )

fun QuranMemorizationAttempt.toSavedAttempt(): QuranMemorizationSavedAttempt =
    QuranMemorizationSavedAttempt(
        attemptId = attemptId,
        timestampMs = timestampMs,
        surahNumber = surahNumber,
        ayahNumber = ayahNumber,
        verseKey = verseKey,
        durationMs = durationMs,
        providerName = providerName,
        modelName = modelName,
        latencyMs = latencyMs,
        transcript = transcript,
        normalizedTranscript = normalizedTranscript,
        recognizedCount = recognizedCount,
        missingCount = missingCount,
        extraCount = extraCount,
        repeatedCount = repeatedCount,
        unknownCount = unknownCount,
        confidence = confidence,
        overallScore = overallScore,
        grade = grade,
        recognizedPercentage = recognizedPercentage,
        scoreCalculationVersion = scoreCalculationVersion,
        status = deriveAyahMemorizationStatus(),
        transcriptionSucceeded = transcriptionSucceeded,
        errorMessage = errorMessage,
        expectedWordIds = expectedWordIds,
        matchedWordIds = matchedWordIds,
        missingWordIds = missingWordIds,
        extraTranscriptWords = extraTranscriptWords,
        repeatedTranscriptWords = repeatedTranscriptWords,
    )

fun QuranMemorizationSavedAttempt.toStatusSnapshot(): AyahMemorizationStatusSnapshot =
    AyahMemorizationStatusSnapshot(
        verseKey = verseKey,
        surahNumber = surahNumber,
        ayahNumber = ayahNumber,
        status = status,
        lastAttemptAtMs = timestampMs,
        lastAttemptId = attemptId,
        correctWordCount = matchedWordIds.size,
        expectedWordCount = expectedWordIds.size,
    )

fun QuranMemorizationSavedAttempt.toAttemptPreferenceEntry(): String =
    JSONObject()
        .put("attemptId", attemptId)
        .put("timestampMs", timestampMs)
        .put("surahNumber", surahNumber)
        .put("ayahNumber", ayahNumber)
        .put("verseKey", verseKey)
        .put("durationMs", durationMs)
        .put("providerName", providerName)
        .put("modelName", modelName)
        .put("latencyMs", latencyMs)
        .put("transcript", transcript)
        .put("normalizedTranscript", normalizedTranscript)
        .put("recognizedCount", recognizedCount)
        .put("missingCount", missingCount)
        .put("extraCount", extraCount)
        .put("repeatedCount", repeatedCount)
        .put("unknownCount", unknownCount)
        .put("confidence", confidence ?: JSONObject.NULL)
        .put("overallScore", overallScore)
        .put("grade", grade.name)
        .put("recognizedPercentage", recognizedPercentage)
        .put("scoreCalculationVersion", scoreCalculationVersion)
        .put("status", status.name)
        .put("transcriptionSucceeded", transcriptionSucceeded)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)
        .put("expectedWordIds", expectedWordIds.toJsonArray())
        .put("matchedWordIds", matchedWordIds.toJsonArray())
        .put("missingWordIds", missingWordIds.toJsonArray())
        .put("extraTranscriptWords", extraTranscriptWords.toJsonArray())
        .put("repeatedTranscriptWords", repeatedTranscriptWords.toJsonArray())
        .toString()

fun String.toQuranMemorizationSavedAttemptOrNull(): QuranMemorizationSavedAttempt? =
    runCatching {
        val json = JSONObject(this)
        val surahNumber = json.optInt("surahNumber", 0)
        val ayahNumber = json.optInt("ayahNumber", 0)
        val verseKey = json.optString("verseKey").ifBlank { "$surahNumber:$ayahNumber" }
        if (surahNumber <= 0 || ayahNumber <= 0 || !verseKey.contains(':')) return null
        val expectedWordIds = json.optJSONArray("expectedWordIds").toStringList()
        val recognizedCount = json.optInt("recognizedCount", 0).coerceAtLeast(0)
        val missingCount = json.optInt("missingCount", 0).coerceAtLeast(0)
        val extraCount = json.optInt("extraCount", 0).coerceAtLeast(0)
        val repeatedCount = json.optInt("repeatedCount", 0).coerceAtLeast(0)
        val unknownCount = json.optInt("unknownCount", 0).coerceAtLeast(0)
        val transcriptionSucceeded = json.optBoolean("transcriptionSucceeded", false)
        val fallbackScore = QuranMemorizationScoreEngine.score(
            expectedWordCount = expectedWordIds.size.takeIf { it > 0 } ?: (recognizedCount + missingCount + unknownCount),
            recognizedCount = recognizedCount,
            missingCount = missingCount,
            extraCount = extraCount,
            repeatedCount = repeatedCount,
            unknownCount = unknownCount,
            transcriptionSucceeded = transcriptionSucceeded,
            analysisSucceeded = transcriptionSucceeded,
        )
        QuranMemorizationSavedAttempt(
            attemptId = json.optString("attemptId").ifBlank { "$verseKey-${json.optLong("timestampMs", 0L)}" },
            timestampMs = json.optLong("timestampMs", 0L).coerceAtLeast(0L),
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            verseKey = verseKey,
            durationMs = json.optLong("durationMs", 0L).coerceAtLeast(0L),
            providerName = json.optString("providerName"),
            modelName = json.optString("modelName"),
            latencyMs = json.optLong("latencyMs", 0L).coerceAtLeast(0L),
            transcript = json.optString("transcript"),
            normalizedTranscript = json.optString("normalizedTranscript"),
            recognizedCount = recognizedCount,
            missingCount = missingCount,
            extraCount = extraCount,
            repeatedCount = repeatedCount,
            unknownCount = unknownCount,
            confidence = if (json.isNull("confidence")) null else json.optDouble("confidence", 0.0).toFloat(),
            overallScore = if (json.has("overallScore")) json.optInt("overallScore", fallbackScore.overallScore).coerceIn(0, 100) else fallbackScore.overallScore,
            grade = json.optString("grade").toQuranMemorizationScoreGradeOrNull() ?: fallbackScore.grade,
            recognizedPercentage = if (json.has("recognizedPercentage")) json.optDouble("recognizedPercentage", fallbackScore.recognizedPercentage.toDouble()).toFloat().coerceIn(0f, 1f) else fallbackScore.recognizedPercentage,
            scoreCalculationVersion = json.optString("scoreCalculationVersion").ifBlank { fallbackScore.calculationVersion },
            status = deriveAyahMemorizationStatus(
                transcriptionSucceeded = transcriptionSucceeded,
                errorMessage = if (json.isNull("errorMessage")) null else json.optString("errorMessage"),
                grade = json.optString("grade").toQuranMemorizationScoreGradeOrNull() ?: fallbackScore.grade,
            ),
            transcriptionSucceeded = transcriptionSucceeded,
            errorMessage = if (json.isNull("errorMessage")) null else json.optString("errorMessage"),
            expectedWordIds = expectedWordIds,
            matchedWordIds = json.optJSONArray("matchedWordIds").toStringList(),
            missingWordIds = json.optJSONArray("missingWordIds").toStringList(),
            extraTranscriptWords = json.optJSONArray("extraTranscriptWords").toStringList(),
            repeatedTranscriptWords = json.optJSONArray("repeatedTranscriptWords").toStringList(),
        )
    }.getOrNull()

private fun List<String>.toJsonArray(): JSONArray =
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

private fun deriveAyahMemorizationStatus(
    transcriptionSucceeded: Boolean,
    errorMessage: String?,
    grade: QuranMemorizationScoreGrade,
): AyahMemorizationStatus =
    when {
        !transcriptionSucceeded -> AyahMemorizationStatus.UNKNOWN
        errorMessage != null -> AyahMemorizationStatus.UNKNOWN
        grade == QuranMemorizationScoreGrade.EXCELLENT || grade == QuranMemorizationScoreGrade.GOOD -> AyahMemorizationStatus.PASSED
        grade == QuranMemorizationScoreGrade.NEEDS_REVIEW -> AyahMemorizationStatus.NEEDS_REVIEW
        grade == QuranMemorizationScoreGrade.REPEAT -> AyahMemorizationStatus.INCORRECT
        else -> AyahMemorizationStatus.ATTEMPTED
    }
