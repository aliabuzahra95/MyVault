package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.data.quran.speech.SpeechRecognitionWord
import kotlin.math.abs

enum class QuranMemorizationWordState {
    CORRECT,
    MISSING,
    EXTRA,
    REPEATED,
    UNKNOWN,
}

data class QuranMemorizationWordAnalysis(
    val word: QuranWord,
    val comparisonWord: QuranExpectedComparisonWord,
    val state: QuranMemorizationWordState,
    val matchedTranscriptWord: QuranRecognizedWord? = null,
    val repeatCount: Int = 0,
    val diagnostic: QuranMemorizationMatchDiagnostic? = null,
)

data class QuranRecognizedWord(
    val text: String,
    val normalizedText: String,
    val comparisonKey: String,
    val transcriptIndex: Int,
    val confidence: Float? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
)

data class QuranMemorizationExtraWord(
    val recognizedWord: QuranRecognizedWord,
    val state: QuranMemorizationWordState,
    val relatedWordId: String? = null,
    val diagnostic: QuranMemorizationMatchDiagnostic? = null,
)

data class QuranMemorizationMatchDiagnostic(
    val expectedWordId: String?,
    val displayedQuranWord: String?,
    val expectedComparisonKey: String?,
    val transcriptWord: String?,
    val normalizedTranscriptWord: String?,
    val transcriptComparisonKey: String?,
    val category: QuranMemorizationDiagnosticCategory,
    val reason: String,
)

enum class QuranMemorizationDiagnosticCategory {
    NO_MATCH,
    NORMALIZATION_FAILED,
    EXTRA_WORD,
    MISSING_WORD,
    REPEATED_WORD,
    UNKNOWN,
}

enum class QuranMemorizationAlignmentAction {
    MATCH,
    MISSING,
    EXTRA,
    REPEATED,
    UNKNOWN,
}

data class QuranMemorizationAlignmentPathStep(
    val action: QuranMemorizationAlignmentAction,
    val expectedWordId: String?,
    val expectedAyahNumber: Int?,
    val expectedDisplayedWord: String?,
    val expectedComparisonKeys: List<String>,
    val transcriptIndex: Int?,
    val transcriptWord: String?,
    val transcriptComparisonKey: String?,
    val reason: String,
    val ayahBoundaryTieBreakUsed: Boolean = false,
    val matchedByGuardedSimilarity: Boolean = false,
    val matchedBySpokenForm: Boolean = false,
)

data class QuranMemorizationAnalysis(
    val verseKey: String,
    val expectedWords: List<QuranMemorizationWordAnalysis>,
    val recognizedWords: List<QuranRecognizedWord>,
    val extraWords: List<QuranMemorizationExtraWord>,
    val alignmentPath: List<QuranMemorizationAlignmentPathStep>,
    val expectedWordCount: Int,
    val recognizedWordCount: Int,
    val correctWordCount: Int,
    val missingWordCount: Int,
    val extraWordCount: Int,
    val repeatedWordCount: Int,
    val unknownWordCount: Int,
    val confidence: Float?,
)

object QuranMemorizationAnalysisEngine {
    private const val LOW_CONFIDENCE_THRESHOLD = 0.45f

    fun analyze(
        ayah: QuranAyah,
        speechResult: SpeechRecognitionResult,
    ): QuranMemorizationAnalysis =
        analyze(
            verseKey = ayah.verseKey,
            expectedWords = ayah.words,
            transcript = speechResult.transcript,
            normalizedTranscript = speechResult.normalizedTranscript,
            wordTimestamps = speechResult.wordTimestamps,
            speechConfidence = speechResult.confidence,
        )

    fun analyze(
        verseKey: String,
        expectedWords: List<QuranWord>,
        transcript: String,
        normalizedTranscript: String = "",
        wordTimestamps: List<SpeechRecognitionWord> = emptyList(),
        speechConfidence: Float? = null,
    ): QuranMemorizationAnalysis {
        val expectedTokens = expectedWords.map { word ->
            val comparisonWord = QuranMemorizationComparisonNormalizer.expectedWord(word)
            ExpectedToken(
                word = word,
                comparisonWord = comparisonWord,
            )
        }
        val recognizedWords = tokenizeTranscript(
            transcript = transcript,
            normalizedTranscript = normalizedTranscript,
            wordTimestamps = wordTimestamps,
        ).mergeAdjacentWordsForExpectedKeys(
            expectedTokens
                .flatMap { it.comparisonWord.comparisonForm.allComparisonKeys() }
                .filter { it.isNotBlank() }
                .toSet(),
        )
        val alignment = align(expectedTokens, recognizedWords)
        val matchedExpectedIndexes = alignment
            .filter { it.expectedIndex != null && it.recognizedIndex != null }
            .associate { it.expectedIndex!! to it.recognizedIndex!! }
        val unmatchedRecognizedIndexes = alignment
            .filter { it.expectedIndex == null && it.recognizedIndex != null }
            .mapNotNull { it.recognizedIndex }

        val mutableExpectedAnalyses = expectedTokens.mapIndexed { index, expected ->
            val recognizedIndex = matchedExpectedIndexes[index]
            if (recognizedIndex == null) {
                val nearbyRecognizedWord = recognizedWords.getOrNull(index)
                QuranMemorizationWordAnalysis(
                    word = expected.word,
                    comparisonWord = expected.comparisonWord,
                    state = QuranMemorizationWordState.MISSING,
                    diagnostic = QuranMemorizationMatchDiagnostic(
                        expectedWordId = expected.word.wordId,
                        displayedQuranWord = expected.word.arabicText,
                        expectedComparisonKey = expected.comparisonWord.comparisonForm.comparisonKey,
                        transcriptWord = nearbyRecognizedWord?.text,
                        normalizedTranscriptWord = nearbyRecognizedWord?.normalizedText,
                        transcriptComparisonKey = nearbyRecognizedWord?.comparisonKey,
                        category = QuranMemorizationDiagnosticCategory.MISSING_WORD,
                        reason = "No transcript word matched the expected comparison key.",
                    ),
                )
            } else {
                val recognizedWord = recognizedWords[recognizedIndex]
                QuranMemorizationWordAnalysis(
                    word = expected.word,
                    comparisonWord = expected.comparisonWord,
                    state = if (recognizedWord.isLowConfidence()) {
                        QuranMemorizationWordState.UNKNOWN
                    } else {
                        QuranMemorizationWordState.CORRECT
                    },
                    matchedTranscriptWord = recognizedWord,
                    diagnostic = if (recognizedWord.isLowConfidence()) {
                        QuranMemorizationMatchDiagnostic(
                            expectedWordId = expected.word.wordId,
                            displayedQuranWord = expected.word.arabicText,
                            expectedComparisonKey = expected.comparisonWord.comparisonForm.comparisonKey,
                            transcriptWord = recognizedWord.text,
                            normalizedTranscriptWord = recognizedWord.normalizedText,
                            transcriptComparisonKey = recognizedWord.comparisonKey,
                            category = QuranMemorizationDiagnosticCategory.UNKNOWN,
                            reason = "Transcript word matched, but Google confidence was below the threshold.",
                        )
                    } else {
                        null
                    },
                )
            }
        }.toMutableList()

        val matchedExpectedByNormalized = matchedExpectedIndexes.keys
            .flatMap { expectedIndex ->
                expectedTokens[expectedIndex].comparisonWord.comparisonForm.comparisonKeys.map { key -> key to expectedIndex }
            }
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.isNotBlank() }
        val allExpectedByComparisonKey = expectedTokens
            .flatMapIndexed { index, token ->
                token.comparisonWord.comparisonForm.comparisonKeys.map { key -> key to index }
            }
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.isNotBlank() }
        val extras = mutableListOf<QuranMemorizationExtraWord>()

        unmatchedRecognizedIndexes.forEach { recognizedIndex ->
            val recognizedWord = recognizedWords[recognizedIndex]
            val repeatedExpectedIndex = matchedExpectedByNormalized[recognizedWord.comparisonKey]
                ?.minByOrNull { expectedIndex ->
                    abs((matchedExpectedIndexes[expectedIndex] ?: recognizedIndex) - recognizedIndex)
                }
            if (repeatedExpectedIndex != null) {
                val current = mutableExpectedAnalyses[repeatedExpectedIndex]
                if (current.state == QuranMemorizationWordState.CORRECT || current.state == QuranMemorizationWordState.REPEATED) {
                    mutableExpectedAnalyses[repeatedExpectedIndex] = current.copy(
                        state = QuranMemorizationWordState.REPEATED,
                        repeatCount = current.repeatCount + 1,
                    )
                }
                extras += QuranMemorizationExtraWord(
                    recognizedWord = recognizedWord,
                    state = QuranMemorizationWordState.REPEATED,
                    relatedWordId = current.word.wordId,
                    diagnostic = QuranMemorizationMatchDiagnostic(
                        expectedWordId = current.word.wordId,
                        displayedQuranWord = current.word.arabicText,
                        expectedComparisonKey = current.comparisonWord.comparisonForm.comparisonKey,
                        transcriptWord = recognizedWord.text,
                        normalizedTranscriptWord = recognizedWord.normalizedText,
                        transcriptComparisonKey = recognizedWord.comparisonKey,
                        category = QuranMemorizationDiagnosticCategory.REPEATED_WORD,
                        reason = "Transcript word matched an already recognised Qur'an word.",
                    ),
                )
            } else {
                val missingExpectedIndex = allExpectedByComparisonKey[recognizedWord.comparisonKey]
                    ?.firstOrNull { mutableExpectedAnalyses[it].state == QuranMemorizationWordState.MISSING }
                if (missingExpectedIndex != null) {
                    val current = mutableExpectedAnalyses[missingExpectedIndex]
                    mutableExpectedAnalyses[missingExpectedIndex] = current.copy(
                        state = QuranMemorizationWordState.EXTRA,
                        matchedTranscriptWord = recognizedWord,
                        diagnostic = QuranMemorizationMatchDiagnostic(
                            expectedWordId = current.word.wordId,
                            displayedQuranWord = current.word.arabicText,
                            expectedComparisonKey = current.comparisonWord.comparisonForm.comparisonKey,
                            transcriptWord = recognizedWord.text,
                            normalizedTranscriptWord = recognizedWord.normalizedText,
                            transcriptComparisonKey = recognizedWord.comparisonKey,
                            category = QuranMemorizationDiagnosticCategory.EXTRA_WORD,
                            reason = "Transcript word has the same comparison key as this Qur'an word, but it appeared out of sequence.",
                        ),
                    )
                }
                extras += QuranMemorizationExtraWord(
                    recognizedWord = recognizedWord,
                    state = QuranMemorizationWordState.EXTRA,
                    relatedWordId = missingExpectedIndex?.let { expectedTokens[it].word.wordId },
                    diagnostic = QuranMemorizationMatchDiagnostic(
                        expectedWordId = missingExpectedIndex?.let { expectedTokens[it].word.wordId },
                        displayedQuranWord = missingExpectedIndex?.let { expectedTokens[it].word.arabicText },
                        expectedComparisonKey = missingExpectedIndex?.let { expectedTokens[it].comparisonWord.comparisonForm.comparisonKey },
                        transcriptWord = recognizedWord.text,
                        normalizedTranscriptWord = recognizedWord.normalizedText,
                        transcriptComparisonKey = recognizedWord.comparisonKey,
                        category = QuranMemorizationDiagnosticCategory.EXTRA_WORD,
                        reason = "Transcript word did not fit the expected ayah sequence.",
                    ),
                )
            }
        }

        val expectedAnalyses = mutableExpectedAnalyses.toList()
        val correctCount = expectedAnalyses.count { it.state == QuranMemorizationWordState.CORRECT }
        val repeatedOfficialCount = expectedAnalyses.count { it.state == QuranMemorizationWordState.REPEATED }
        val unknownCount = expectedAnalyses.count { it.state == QuranMemorizationWordState.UNKNOWN }
        val extraByRecognizedIndex = extras.associateBy { it.recognizedWord.transcriptIndex }
        val alignmentPath = alignment.map { step ->
            step.toPathStep(
                expectedTokens = expectedTokens,
                expectedAnalyses = expectedAnalyses,
                recognizedWords = recognizedWords,
                extraByRecognizedIndex = extraByRecognizedIndex,
            )
        }
        val fallbackConfidence = expectedWords
            .takeIf { it.isNotEmpty() }
            ?.let { (correctCount + repeatedOfficialCount).toFloat() / it.size.toFloat() }

        return QuranMemorizationAnalysis(
            verseKey = verseKey,
            expectedWords = expectedAnalyses,
            recognizedWords = recognizedWords,
            extraWords = extras,
            alignmentPath = alignmentPath,
            expectedWordCount = expectedWords.size,
            recognizedWordCount = correctCount + repeatedOfficialCount,
            correctWordCount = correctCount,
            missingWordCount = expectedAnalyses.count { it.state == QuranMemorizationWordState.MISSING },
            extraWordCount = extras.count { it.state == QuranMemorizationWordState.EXTRA },
            repeatedWordCount = extras.count { it.state == QuranMemorizationWordState.REPEATED },
            unknownWordCount = unknownCount,
            confidence = speechConfidence ?: fallbackConfidence,
        )
    }

    private fun tokenizeTranscript(
        transcript: String,
        normalizedTranscript: String,
        wordTimestamps: List<SpeechRecognitionWord>,
    ): List<QuranRecognizedWord> {
        val timestampWords = wordTimestamps.flatMap { word ->
            splitIntoWords(word.word).map { part ->
                TimedTranscriptWord(
                    text = part,
                    confidence = word.confidence,
                    startMs = word.startMs,
                    endMs = word.endMs,
                )
            }
        }
        val sourceWords = timestampWords.ifEmpty {
            splitIntoWords(transcript.ifBlank { normalizedTranscript }).map {
                TimedTranscriptWord(text = it)
            }
        }
        return sourceWords.mapNotNull { timedWord ->
            val comparisonForm = QuranMemorizationComparisonNormalizer.transcriptWord(timedWord.text)
            if (comparisonForm.comparisonKey.isBlank()) return@mapNotNull null
            QuranRecognizedWord(
                text = timedWord.text,
                normalizedText = comparisonForm.normalizedForm,
                comparisonKey = comparisonForm.comparisonKey,
                transcriptIndex = -1,
                confidence = timedWord.confidence,
                startMs = timedWord.startMs,
                endMs = timedWord.endMs,
            )
        }.mapIndexed { index, word -> word.copy(transcriptIndex = index) }
    }

    private fun align(
        expectedTokens: List<ExpectedToken>,
        recognizedWords: List<QuranRecognizedWord>,
    ): List<AlignmentStep> {
        val expectedCount = expectedTokens.size
        val recognizedCount = recognizedWords.size
        val scores = Array(expectedCount + 1) { Array(recognizedCount + 1) { AlignmentScore.unreachable() } }
        val previous = Array(expectedCount + 1) { arrayOfNulls<AlignmentPrevious>(recognizedCount + 1) }
        scores[0][0] = AlignmentScore(cost = 0, matches = 0)

        for (expectedIndex in 0..expectedCount) {
            for (recognizedIndex in 0..recognizedCount) {
                val current = scores[expectedIndex][recognizedIndex]
                if (!current.isReachable) continue
                if (expectedIndex < expectedCount) {
                    updateAlignmentCell(
                        scores = scores,
                        previous = previous,
                        nextExpectedIndex = expectedIndex + 1,
                        nextRecognizedIndex = recognizedIndex,
                        candidate = current.copy(cost = current.cost + 1),
                    previousStep = AlignmentPrevious(
                        expectedIndex = expectedIndex,
                        recognizedIndex = recognizedIndex,
                        step = AlignmentStep(
                            expectedIndex = expectedIndex,
                            recognizedIndex = null,
                            ayahBoundaryTieBreakUsed = isFirstWordInAyah(expectedTokens, expectedIndex),
                        ),
                    ),
                    expectedTokens = expectedTokens,
                )
            }
            if (recognizedIndex < recognizedCount) {
                updateAlignmentCell(
                    scores = scores,
                    previous = previous,
                    nextExpectedIndex = expectedIndex,
                    nextRecognizedIndex = recognizedIndex + 1,
                    candidate = current.copy(
                        cost = current.cost + 1,
                        extraCount = current.extraCount + 1,
                        ayahBoundaryPenalty = current.ayahBoundaryPenalty + extraBoundaryPenalty(expectedTokens, expectedIndex),
                    ),
                    previousStep = AlignmentPrevious(
                        expectedIndex = expectedIndex,
                        recognizedIndex = recognizedIndex,
                        step = AlignmentStep(
                            expectedIndex = null,
                            recognizedIndex = recognizedIndex,
                            ayahBoundaryTieBreakUsed = isAtAyahBoundary(expectedTokens, expectedIndex),
                        ),
                    ),
                    expectedTokens = expectedTokens,
                )
            }
            if (expectedIndex < expectedCount && recognizedIndex < recognizedCount) {
                val match = QuranMemorizationComparisonNormalizer.match(
                    expected = expectedTokens[expectedIndex].comparisonWord.comparisonForm,
                    transcript = QuranMemorizationComparisonForm(
                        originalText = recognizedWords[recognizedIndex].text,
                        normalizedForm = recognizedWords[recognizedIndex].normalizedText,
                        comparisonKey = recognizedWords[recognizedIndex].comparisonKey,
                    ),
                    context = QuranMemorizationComparisonContext(
                        allowConnectedSpeech = expectedIndex > 0 && recognizedIndex > 0,
                    ),
                )
                if (match.isMatch) {
                    updateAlignmentCell(
                        scores = scores,
                        previous = previous,
                        nextExpectedIndex = expectedIndex + 1,
                        nextRecognizedIndex = recognizedIndex + 1,
                        candidate = current.copy(
                            matches = current.matches + 1,
                            guardedSimilarityCount = current.guardedSimilarityCount + if (match.matchedByGuardedSimilarity) 1 else 0,
                            spokenFormCount = current.spokenFormCount + if (match.matchedBySpokenForm) 1 else 0,
                            matchedDistance = current.matchedDistance + abs(expectedIndex - recognizedIndex),
                            ayahBoundaryPenalty = current.ayahBoundaryPenalty +
                                matchBoundaryPenalty(expectedTokens, expectedIndex, recognizedIndex),
                        ),
                        previousStep = AlignmentPrevious(
                            expectedIndex = expectedIndex,
                            recognizedIndex = recognizedIndex,
                            step = AlignmentStep(
                                expectedIndex = expectedIndex,
                                recognizedIndex = recognizedIndex,
                                ayahBoundaryTieBreakUsed = matchBoundaryPenalty(expectedTokens, expectedIndex, recognizedIndex) > 0,
                                matchedByGuardedSimilarity = match.matchedByGuardedSimilarity,
                                matchedBySpokenForm = match.matchedBySpokenForm,
                                matchReason = match.reason,
                            ),
                        ),
                        expectedTokens = expectedTokens,
                    )
                }
            }
        }
        }

        return buildList {
            var expectedIndex = expectedCount
            var recognizedIndex = recognizedCount
            while (expectedIndex > 0 || recognizedIndex > 0) {
                val item = previous[expectedIndex][recognizedIndex] ?: break
                add(item.step)
                expectedIndex = item.expectedIndex
                recognizedIndex = item.recognizedIndex
            }
        }.asReversed()
    }

    private fun updateAlignmentCell(
        scores: Array<Array<AlignmentScore>>,
        previous: Array<Array<AlignmentPrevious?>>,
        nextExpectedIndex: Int,
        nextRecognizedIndex: Int,
        candidate: AlignmentScore,
        previousStep: AlignmentPrevious,
        expectedTokens: List<ExpectedToken>,
    ) {
        val current = scores[nextExpectedIndex][nextRecognizedIndex]
        val adjustedCandidate = if (previousStep.step.expectedIndex != null && previousStep.step.recognizedIndex == null) {
            candidate.copy(
                missingCount = candidate.missingCount + 1,
                ayahBoundaryPenalty = candidate.ayahBoundaryPenalty +
                    missingBoundaryPenalty(expectedTokens, previousStep.step.expectedIndex),
            )
        } else {
            candidate
        }
        if (adjustedCandidate.isBetterThan(current)) {
            scores[nextExpectedIndex][nextRecognizedIndex] = adjustedCandidate
            previous[nextExpectedIndex][nextRecognizedIndex] = previousStep
        }
    }

    private fun isFirstWordInAyah(expectedTokens: List<ExpectedToken>, expectedIndex: Int): Boolean {
        val current = expectedTokens.getOrNull(expectedIndex) ?: return false
        val previous = expectedTokens.getOrNull(expectedIndex - 1) ?: return true
        return previous.word.ayahNumber != current.word.ayahNumber
    }

    private fun isAtAyahBoundary(expectedTokens: List<ExpectedToken>, expectedIndex: Int): Boolean {
        val previous = expectedTokens.getOrNull(expectedIndex - 1) ?: return expectedIndex == 0
        val current = expectedTokens.getOrNull(expectedIndex) ?: return true
        return previous.word.ayahNumber != current.word.ayahNumber
    }

    private fun missingBoundaryPenalty(expectedTokens: List<ExpectedToken>, expectedIndex: Int): Int =
        if (isFirstWordInAyah(expectedTokens, expectedIndex)) 2 else 0

    private fun extraBoundaryPenalty(expectedTokens: List<ExpectedToken>, expectedIndex: Int): Int =
        if (isAtAyahBoundary(expectedTokens, expectedIndex)) 1 else 0

    private fun matchBoundaryPenalty(
        expectedTokens: List<ExpectedToken>,
        expectedIndex: Int,
        recognizedIndex: Int,
    ): Int {
        val expected = expectedTokens.getOrNull(expectedIndex) ?: return 0
        val ayahIndexes = expectedTokens
            .mapIndexedNotNull { index, token ->
                index.takeIf { token.word.ayahNumber == expected.word.ayahNumber }
            }
        val first = ayahIndexes.firstOrNull() ?: return 0
        val last = ayahIndexes.lastOrNull() ?: return 0
        return if (recognizedIndex in (first - 1)..(last + 1)) 0 else 1
    }

    private fun splitIntoWords(value: String): List<String> =
        Regex("\\S+").findAll(value).map { it.value }.toList()

    private fun List<QuranRecognizedWord>.mergeAdjacentWordsForExpectedKeys(
        expectedComparisonKeys: Set<String>,
    ): List<QuranRecognizedWord> {
        if (size < 2 || expectedComparisonKeys.isEmpty()) return this
        val merged = mutableListOf<QuranRecognizedWord>()
        var index = 0
        while (index < size) {
            val current = this[index]
            val next = getOrNull(index + 1)
            val combinedKey = next?.let { current.comparisonKey + it.comparisonKey }
            if (next != null && combinedKey in expectedComparisonKeys) {
                merged += QuranRecognizedWord(
                    text = "${current.text} ${next.text}",
                    normalizedText = "${current.normalizedText} ${next.normalizedText}",
                    comparisonKey = combinedKey.orEmpty(),
                    transcriptIndex = current.transcriptIndex,
                    confidence = listOfNotNull(current.confidence, next.confidence).minOrNull(),
                    startMs = current.startMs,
                    endMs = next.endMs,
                )
                index += 2
            } else {
                merged += current
                index += 1
            }
        }
        return merged.mapIndexed { mergedIndex, word -> word.copy(transcriptIndex = mergedIndex) }
    }

    private fun QuranRecognizedWord.isLowConfidence(): Boolean =
        confidence?.let { it < LOW_CONFIDENCE_THRESHOLD } == true

    private data class TimedTranscriptWord(
        val text: String,
        val confidence: Float? = null,
        val startMs: Long? = null,
        val endMs: Long? = null,
    )

    private data class ExpectedToken(
        val word: QuranWord,
        val comparisonWord: QuranExpectedComparisonWord,
    )

    private data class AlignmentStep(
        val expectedIndex: Int?,
        val recognizedIndex: Int?,
        val ayahBoundaryTieBreakUsed: Boolean = false,
        val matchedByGuardedSimilarity: Boolean = false,
        val matchedBySpokenForm: Boolean = false,
        val matchReason: String? = null,
    )

    private data class AlignmentPrevious(
        val expectedIndex: Int,
        val recognizedIndex: Int,
        val step: AlignmentStep,
    )

    private data class AlignmentScore(
        val cost: Int,
        val matches: Int,
        val missingCount: Int = 0,
        val extraCount: Int = 0,
        val guardedSimilarityCount: Int = 0,
        val spokenFormCount: Int = 0,
        val ayahBoundaryPenalty: Int = 0,
        val matchedDistance: Int = 0,
    ) {
        val isReachable: Boolean
            get() = cost < Int.MAX_VALUE

        fun isBetterThan(other: AlignmentScore): Boolean =
            when {
                cost != other.cost -> cost < other.cost
                ayahBoundaryPenalty != other.ayahBoundaryPenalty -> ayahBoundaryPenalty < other.ayahBoundaryPenalty
                guardedSimilarityCount != other.guardedSimilarityCount -> guardedSimilarityCount < other.guardedSimilarityCount
                spokenFormCount != other.spokenFormCount -> spokenFormCount < other.spokenFormCount
                matchedDistance != other.matchedDistance -> matchedDistance < other.matchedDistance
                missingCount != other.missingCount -> missingCount < other.missingCount
                extraCount != other.extraCount -> extraCount < other.extraCount
                else -> matches > other.matches
            }

        companion object {
            fun unreachable(): AlignmentScore = AlignmentScore(cost = Int.MAX_VALUE, matches = Int.MIN_VALUE)
        }
    }

    private fun AlignmentStep.toPathStep(
        expectedTokens: List<ExpectedToken>,
        expectedAnalyses: List<QuranMemorizationWordAnalysis>,
        recognizedWords: List<QuranRecognizedWord>,
        extraByRecognizedIndex: Map<Int, QuranMemorizationExtraWord>,
    ): QuranMemorizationAlignmentPathStep {
        val expected = expectedIndex?.let { expectedTokens.getOrNull(it) }
        val expectedAnalysis = expectedIndex?.let { expectedAnalyses.getOrNull(it) }
        val recognized = recognizedIndex?.let { recognizedWords.getOrNull(it) }
        val extra = recognized?.let { extraByRecognizedIndex[it.transcriptIndex] }
        val action = when {
            expectedAnalysis?.state == QuranMemorizationWordState.UNKNOWN -> QuranMemorizationAlignmentAction.UNKNOWN
            expected != null && recognized != null -> QuranMemorizationAlignmentAction.MATCH
            expected != null -> QuranMemorizationAlignmentAction.MISSING
            extra?.state == QuranMemorizationWordState.REPEATED -> QuranMemorizationAlignmentAction.REPEATED
            else -> QuranMemorizationAlignmentAction.EXTRA
        }
        val reason = when (action) {
            QuranMemorizationAlignmentAction.MATCH -> matchReason
                ?: "Transcript key matched one of the expected Qur'an comparison keys."
            QuranMemorizationAlignmentAction.MISSING -> "No transcript word aligned with this expected Qur'an word."
            QuranMemorizationAlignmentAction.EXTRA -> "Transcript word did not align with the expected Qur'an sequence."
            QuranMemorizationAlignmentAction.REPEATED -> "Transcript word repeated an already matched Qur'an comparison key."
            QuranMemorizationAlignmentAction.UNKNOWN -> "Transcript word matched but provider confidence was below the threshold."
        }
        return QuranMemorizationAlignmentPathStep(
            action = action,
            expectedWordId = expected?.word?.wordId ?: extra?.relatedWordId,
            expectedAyahNumber = expected?.word?.ayahNumber ?: extra?.relatedWordId?.split(':')?.getOrNull(1)?.toIntOrNull(),
            expectedDisplayedWord = expected?.word?.arabicText ?: extra?.diagnostic?.displayedQuranWord,
            expectedComparisonKeys = expected?.comparisonWord?.comparisonForm?.allComparisonKeys()?.toList()
                ?: extra?.diagnostic?.expectedComparisonKey?.let { listOf(it) }
                ?: emptyList(),
            transcriptIndex = recognized?.transcriptIndex,
            transcriptWord = recognized?.text,
            transcriptComparisonKey = recognized?.comparisonKey,
            reason = reason,
            ayahBoundaryTieBreakUsed = ayahBoundaryTieBreakUsed,
            matchedByGuardedSimilarity = matchedByGuardedSimilarity,
            matchedBySpokenForm = matchedBySpokenForm,
        )
    }

    private fun QuranMemorizationComparisonForm.allComparisonKeys(): Set<String> =
        comparisonKeys + connectedSpeechComparisonKeys
}
