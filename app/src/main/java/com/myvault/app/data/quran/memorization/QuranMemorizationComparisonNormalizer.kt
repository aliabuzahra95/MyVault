package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranWord

data class QuranMemorizationComparisonForm(
    val originalText: String,
    val normalizedForm: String,
    val comparisonKey: String,
    val rulesApplied: List<String> = emptyList(),
    val comparisonKeys: Set<String> = setOf(comparisonKey).filter { it.isNotBlank() }.toSet(),
    val connectedSpeechComparisonKeys: Set<String> = emptySet(),
    val comparisonKeyRules: Map<String, String> = emptyMap(),
)

data class QuranExpectedComparisonWord(
    val wordId: String,
    val displayedUthmaniWord: String,
    val normalizedDisplayForm: String,
    val imlaeiText: String? = null,
    val imlaeiSimpleText: String? = null,
    val comparisonForm: QuranMemorizationComparisonForm,
)

data class QuranMemorizationComparisonMatch(
    val isMatch: Boolean,
    val isExact: Boolean,
    val reason: String,
    val matchedByGuardedSimilarity: Boolean = false,
    val matchedBySpokenForm: Boolean = false,
)

data class QuranMemorizationComparisonContext(
    val allowConnectedSpeech: Boolean = false,
)

object QuranMemorizationComparisonNormalizer {
    fun expectedWord(word: QuranWord): QuranExpectedComparisonWord {
        val normalizedDisplay = word.normalizedArabicText.ifBlank {
            normalizeDisplayForm(word.arabicText)
        }
        val (displayComparisonKey, displayRules) = expectedComparisonKey(
            displayedUthmaniWord = word.arabicText,
            normalizedDisplay = normalizedDisplay,
        )
        val (displayComparisonKeys, displayAliasRules) = expectedComparisonKeys(
            displayedUthmaniWord = word.arabicText,
            primaryKey = displayComparisonKey,
        )
        val imlaeiText = word.metadata?.imlaeiText?.takeIf { it.isNotBlank() }
        val imlaeiSimpleText = word.metadata?.imlaeiSimpleText?.takeIf { it.isNotBlank() }
        val imlaeiSource = imlaeiSimpleText ?: imlaeiText
        val imlaeiKey = imlaeiSource?.let { toComparisonKey(it) }.orEmpty()
        val primaryKey = imlaeiKey.ifBlank { displayComparisonKey }
        val (primaryKeys, primaryAliasRules) = expectedComparisonKeys(
            displayedUthmaniWord = word.arabicText,
            primaryKey = primaryKey,
        )
        val comparisonKeys = (primaryKeys + displayComparisonKeys).filterTo(linkedSetOf()) { it.isNotBlank() }
        val connectedSpeechKeys = connectedSpeechComparisonKeys(
            displayedUthmaniWord = word.arabicText,
            baseKeys = comparisonKeys,
        )
        val comparisonRules = buildList {
            if (imlaeiKey.isNotBlank()) add("imlaei-comparison-corpus")
            addAll(displayRules)
            addAll(displayAliasRules)
            addAll(primaryAliasRules)
            if (connectedSpeechKeys.isNotEmpty()) add(HAMZAT_WASL_CONNECTED_RULE)
        }.distinct()
        return QuranExpectedComparisonWord(
            wordId = word.wordId,
            displayedUthmaniWord = word.arabicText,
            normalizedDisplayForm = normalizedDisplay,
            imlaeiText = imlaeiText,
            imlaeiSimpleText = imlaeiSimpleText,
            comparisonForm = QuranMemorizationComparisonForm(
                originalText = imlaeiSource ?: word.arabicText,
                normalizedForm = imlaeiSource?.let(::normalizeDisplayForm) ?: normalizedDisplay,
                comparisonKey = primaryKey,
                rulesApplied = comparisonRules,
                comparisonKeys = comparisonKeys,
                connectedSpeechComparisonKeys = connectedSpeechKeys,
                comparisonKeyRules = connectedSpeechKeys.associateWith { HAMZAT_WASL_CONNECTED_RULE },
            ),
        )
    }

    fun transcriptWord(originalText: String): QuranMemorizationComparisonForm {
        val normalized = normalizeTranscriptForm(originalText)
        return QuranMemorizationComparisonForm(
            originalText = originalText,
            normalizedForm = normalized,
            comparisonKey = toComparisonKey(normalized),
        )
    }

    fun normalizeDisplayForm(value: String): String =
        normalizeArabicForComparison(value, keepDaggerAlif = false)

    fun normalizeTranscriptForm(value: String): String =
        normalizeArabicForComparison(value, keepDaggerAlif = false)

    fun match(
        expected: QuranMemorizationComparisonForm,
        transcript: QuranMemorizationComparisonForm,
        context: QuranMemorizationComparisonContext = QuranMemorizationComparisonContext(),
    ): QuranMemorizationComparisonMatch {
        if (transcript.comparisonKey in expected.comparisonKeys) {
            return QuranMemorizationComparisonMatch(
                isMatch = true,
                isExact = true,
                reason = "Transcript key exactly matched an expected Qur'an comparison key.",
            )
        }
        if (context.allowConnectedSpeech && transcript.comparisonKey in expected.connectedSpeechComparisonKeys) {
            val rule = expected.comparisonKeyRules[transcript.comparisonKey] ?: "spoken-form-comparison"
            return QuranMemorizationComparisonMatch(
                isMatch = true,
                isExact = false,
                reason = "Transcript key matched a deterministic Qur'anic spoken-form key: $rule.",
                matchedBySpokenForm = true,
            )
        }
        if (!context.allowConnectedSpeech && transcript.comparisonKey in expected.connectedSpeechComparisonKeys) {
            return QuranMemorizationComparisonMatch(
                isMatch = false,
                isExact = false,
                reason = "Transcript key only matches a connected-recitation spoken form, but this position is not connected.",
            )
        }
        val guardedMatch = if (expected.rulesApplied.contains("dagger-alif-to-alif")) {
            null
        } else {
            expected.comparisonKeys.firstNotNullOfOrNull { expectedKey ->
                guardedSimilarityReason(expectedKey, transcript.comparisonKey)
            }
        }
        return if (guardedMatch != null) {
            QuranMemorizationComparisonMatch(
                isMatch = true,
                isExact = false,
                reason = guardedMatch,
                matchedByGuardedSimilarity = true,
            )
        } else {
            QuranMemorizationComparisonMatch(
                isMatch = false,
                isExact = false,
                reason = "Transcript key did not match the expected Qur'an comparison keys.",
            )
        }
    }

    private fun expectedComparisonKey(
        displayedUthmaniWord: String,
        normalizedDisplay: String,
    ): Pair<String, List<String>> {
        val rules = mutableListOf<String>()
        val withoutDaggerKey = toComparisonKey(normalizedDisplay)
        val rasmModernized = displayedUthmaniWord
            .replace("و\u0670ة", "اة")
            .replace("و\u0670ت", "ات")
        val rasmModernizedKey = toComparisonKey(rasmModernized)
        if (rasmModernizedKey.isNotBlank() && rasmModernizedKey != withoutDaggerKey) {
            rules += "waw-dagger-alif-to-alif"
            return rasmModernizedKey to rules
        }

        if (displayedUthmaniWord.contains('\u0670')) {
            val daggerRestorationSource = displayedUthmaniWord.replace("\u0649\u0670", "\u0649")
            val withDaggerRestored = toComparisonKey(daggerRestorationSource.replace("\u0670", "ا"))
            if (
                withDaggerRestored.isNotBlank() &&
                withDaggerRestored != withoutDaggerKey &&
                !isProtectedNoAlifRestorationKey(withoutDaggerKey)
            ) {
                rules += "dagger-alif-to-alif"
                return withDaggerRestored to rules
            }
            rules += "dagger-alif-removed"
        }

        return withoutDaggerKey to rules
    }

    private fun expectedComparisonKeys(
        displayedUthmaniWord: String,
        primaryKey: String,
    ): Pair<Set<String>, List<String>> {
        val keys = linkedSetOf(primaryKey).filterTo(linkedSetOf()) { it.isNotBlank() }
        val rules = mutableListOf<String>()

        if (displayedUthmaniWord.contains('\u0649') && primaryKey.endsWith("ي")) {
            keys += primaryKey.dropLast(1) + "ا"
            rules += "final-alif-maqsurah-spoken-as-alif"
        }

        if (displayedUthmaniWord.contains(Regex("[\\u064B\\u0657]\\u0627$")) && primaryKey.endsWith("ا")) {
            keys += primaryKey.dropLast(1)
            rules += "pause-on-tanween-fatha-final-alif"
        }

        if (primaryKey.endsWith("ة")) {
            keys += primaryKey.dropLast(1) + "ه"
            rules += "ta-marbuta-final-haa-transcript"
        }

        if (
            primaryKey.length >= DEFINITE_ARTICLE_INSERTION_MIN_KEY_LENGTH &&
            !primaryKey.startsWith("ال") &&
            displayedUthmaniWord.contains(Regex("[\\u064B\\u064C\\u064D]"))
        ) {
            keys += "ال$primaryKey"
            rules += "stt-inserted-definite-article-for-tanween-noun"
        }

        if (primaryKey.endsWith("اليل")) {
            keys += primaryKey.removeSuffix("اليل") + "الليل"
            rules += "article-lam-restored-for-al-layl"
        }

        if (primaryKey.startsWith("ولل") && primaryKey.length > 3) {
            keys += "ولاال" + primaryKey.removePrefix("ولل")
            rules += "contracted-lam-split-by-transcript"
        }

        val expandedKeys = keys.toList().fold(keys) { accumulated, key ->
            accumulated.apply {
                if (key.endsWith("ة")) add(key.dropLast(1) + "ه")
                if (key.endsWith("ه")) add(normalizeKnownFinalHaaImlaForms(key))
            }
        }.filterTo(linkedSetOf()) { it.isNotBlank() }

        return expandedKeys to rules.distinct()
    }

    private fun connectedSpeechComparisonKeys(
        displayedUthmaniWord: String,
        baseKeys: Set<String>,
    ): Set<String> {
        val trimmed = displayedUthmaniWord.trimStart()
        if (!trimmed.startsWith('ٱ')) return emptySet()
        return baseKeys
            .mapNotNull { key -> key.dropInitialHamzatWaslForConnectedSpeech() }
            .filterTo(linkedSetOf()) { it.isNotBlank() && it !in baseKeys }
    }

    private fun String.dropInitialHamzatWaslForConnectedSpeech(): String? {
        if (!startsWith("ا")) return null
        if (startsWith("ال")) return null
        if (this in HAMZAT_WASL_CONNECTED_PROTECTED_KEYS) return null
        return drop(1).takeIf { it.length >= HAMZAT_WASL_CONNECTED_MIN_RESULT_LENGTH }
    }

    private fun toComparisonKey(value: String): String =
        normalizeArabicForComparison(value, keepDaggerAlif = false)
            .replace("ءا", "ا")
            .replace(Regex("^اا+"), "ا")
            .let(::normalizeKnownFinalHaaImlaForms)
            .replace(" ", "")

    private fun normalizeKnownFinalHaaImlaForms(value: String): String =
        value
            .replaceKnownFinalHaa("الصلاه", "الصلاة")
            .replaceKnownFinalHaa("والصلاه", "والصلاة")
            .replaceKnownFinalHaa("فالصلاه", "فالصلاة")
            .replaceKnownFinalHaa("بالصلاه", "بالصلاة")
            .replaceKnownFinalHaa("كالصلاه", "كالصلاة")
            .replaceKnownFinalHaa("للصلاه", "للصلاة")
            .replaceKnownFinalHaa("الزكاه", "الزكاة")
            .replaceKnownFinalHaa("والزكاه", "والزكاة")
            .replaceKnownFinalHaa("فالزكاه", "فالزكاة")
            .replaceKnownFinalHaa("بالزكاه", "بالزكاة")
            .replaceKnownFinalHaa("كالزكاه", "كالزكاة")
            .replaceKnownFinalHaa("للزكاه", "للزكاة")
            .replaceKnownFinalHaa("سنه", "سنة")
            .replaceKnownFinalHaa("وسنه", "وسنة")
            .replaceKnownFinalHaa("فسنه", "فسنة")
            .replaceKnownFinalHaa("بسنه", "بسنة")
            .replaceKnownFinalHaa("كسنه", "كسنة")
            .replaceKnownFinalHaa("لسنه", "لسنة")

    private fun String.replaceKnownFinalHaa(from: String, to: String): String =
        if (this == from) to else this

    private fun guardedSimilarityReason(
        expectedKey: String,
        transcriptKey: String,
    ): String? {
        if (expectedKey.length < GUARDED_SIMILARITY_MIN_EXPECTED_LENGTH) return null
        if (transcriptKey.length < GUARDED_SIMILARITY_MIN_TRANSCRIPT_LENGTH) return null
        if (kotlin.math.abs(expectedKey.length - transcriptKey.length) != 1) return null
        val longer = if (expectedKey.length > transcriptKey.length) expectedKey else transcriptKey
        val shorter = if (expectedKey.length > transcriptKey.length) transcriptKey else expectedKey
        val missingIndex = singleExtraCharacterIndex(longer = longer, shorter = shorter) ?: return null
        val extraCharacter = longer[missingIndex]
        if (extraCharacter == 'ه') {
            return if (
                expectedKey.length > transcriptKey.length &&
                missingIndex == expectedKey.lastIndex &&
                expectedKey.dropLast(1) == transcriptKey
            ) {
                "Guarded similarity accepted omitted final haa pronoun."
            } else {
                null
            }
        }
        if (extraCharacter !in GUARDED_SIMILARITY_WEAK_LETTERS) return null
        val sharedPrefixLength = missingIndex
        val sharedSuffixLength = shorter.length - missingIndex
        if (sharedPrefixLength < 1 || sharedSuffixLength < 1) return null
        return "Guarded similarity accepted one weak-letter spelling difference: '$extraCharacter'."
    }

    private fun singleExtraCharacterIndex(
        longer: String,
        shorter: String,
    ): Int? {
        var longerIndex = 0
        var shorterIndex = 0
        var extraIndex: Int? = null
        while (longerIndex < longer.length && shorterIndex < shorter.length) {
            if (longer[longerIndex] == shorter[shorterIndex]) {
                longerIndex += 1
                shorterIndex += 1
            } else {
                if (extraIndex != null) return null
                extraIndex = longerIndex
                longerIndex += 1
            }
        }
        if (longerIndex < longer.length) {
            if (extraIndex != null) return null
            extraIndex = longerIndex
        }
        return extraIndex
    }

    private fun isProtectedNoAlifRestorationKey(key: String): Boolean {
        if (key in NO_ALIF_RESTORATION_KEYS) return true
        val withoutSinglePrefix = key.removePrefix("و")
            .removePrefix("ف")
            .removePrefix("ب")
            .removePrefix("ك")
            .removePrefix("ل")
        return withoutSinglePrefix in NO_ALIF_RESTORATION_KEYS
    }

    private fun normalizeArabicForComparison(
        value: String,
        keepDaggerAlif: Boolean,
    ): String {
        val marks = if (keepDaggerAlif) {
            Regex("[\\u064B-\\u065F\\u06D6-\\u06E4\\u06E7-\\u06ED]")
        } else {
            Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
        }
        return value
            .replace('\u00A0', ' ')
            .replace("\u0640", "")
            .replace("\u0654", "ء")
            .replace("\u0655", "ء")
            .replace("\u06E5", "")
            .replace("\u06E6", "")
            .replace(marks, "")
            .replace(Regex("[\\p{Punct}ۚۖۗۘۙۛۜ۝۞؟،؛«»“”‘’]"), " ")
            .replace('ٱ', 'ا')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ؤ', 'ء')
            .replace('ئ', 'ء')
            .replace('ى', 'ي')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val NO_ALIF_RESTORATION_KEYS = setOf(
        "الله",
        "اللهم",
        "الرحمن",
        "هذا",
        "هذه",
        "هذان",
        "هذين",
        "هؤلاء",
        "ذلك",
        "ذلكم",
        "ذلكما",
        "ذلكن",
        "لكن",
        "ولكن",
        "اولئك",
        "اله",
        "واله",
        "فاله",
        "لاله",
        "الهكم",
        "الهنا",
        "الهه",
        "الهين",
    )

    private const val GUARDED_SIMILARITY_MIN_EXPECTED_LENGTH = 5
    private const val GUARDED_SIMILARITY_MIN_TRANSCRIPT_LENGTH = 4
    private const val DEFINITE_ARTICLE_INSERTION_MIN_KEY_LENGTH = 4
    private const val HAMZAT_WASL_CONNECTED_MIN_RESULT_LENGTH = 3
    private const val HAMZAT_WASL_CONNECTED_RULE = "hamzat-wasl-dropped-in-connected-recitation"

    private val HAMZAT_WASL_CONNECTED_PROTECTED_KEYS = setOf(
        "الله",
        "اللهم",
    )

    private val GUARDED_SIMILARITY_WEAK_LETTERS = setOf(
        'ا',
        'و',
        'ي',
        'ء',
    )
}
