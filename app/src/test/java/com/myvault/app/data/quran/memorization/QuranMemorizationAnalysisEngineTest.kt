package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.QuranWordMetadata
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.data.quran.speech.SpeechRecognitionWord
import com.myvault.app.data.quran.speech.normalizeArabicTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranMemorizationAnalysisEngineTest {
    @Test
    fun matchesAlFatihahOpeningWordsWithoutChangingDisplayedText() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = listOf(
                word(position = 1, arabic = "بِسۡمِ", normalized = "بسم"),
                word(position = 2, arabic = "ٱللَّهِ", normalized = "الله"),
                word(position = 3, arabic = "ٱلرَّحۡمَٰنِ", normalized = "الرحمن"),
                word(position = 4, arabic = "ٱلرَّحِيمِ", normalized = "الرحيم"),
            ),
            transcript = "بسم الله الرحمن الرحيم",
        )

        assertAllCorrect(analysis, expectedCount = 4)
        assertEquals("بِسۡمِ", analysis.expectedWords.first().comparisonWord.displayedUthmaniWord)
        assertEquals("1:1:1", analysis.expectedWords.first().comparisonWord.wordId)
    }

    @Test
    fun matchesShortSurahWordsWithWasla() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "112:1",
            expectedWords = listOf(
                word(position = 1, arabic = "قُلۡ", normalized = "قل"),
                word(position = 2, arabic = "هُوَ", normalized = "هو"),
                word(position = 3, arabic = "ٱللَّهُ", normalized = "الله"),
                word(position = 4, arabic = "أَحَدٌ", normalized = "احد"),
            ),
            transcript = "قل هو الله احد",
        )

        assertAllCorrect(analysis, expectedCount = 4)
    }

    @Test
    fun marksSkippedOfficialWordAsMissing() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن", "الرحيم"),
            transcript = "بسم الله الرحيم",
        )

        assertEquals(3, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.MISSING,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
        assertEquals(QuranMemorizationDiagnosticCategory.MISSING_WORD, analysis.expectedWords[2].diagnostic?.category)
    }

    @Test
    fun marksRepeatedOfficialWordAsRepeatedWithoutCountingItAsExtra() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن", "الرحيم"),
            transcript = "بسم الله الله الرحمن الرحيم",
        )

        assertEquals(4, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(1, analysis.repeatedWordCount)
        assertEquals(QuranMemorizationWordState.REPEATED, analysis.expectedWords[1].state)
        assertEquals(QuranMemorizationDiagnosticCategory.REPEATED_WORD, analysis.extraWords.single().diagnostic?.category)
    }

    @Test
    fun countsWordsOutsideTheAyahSequenceAsExtra() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن", "الرحيم"),
            transcript = "بسم الله يا الرحمن الرحيم",
        )

        assertEquals(4, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals("يا", analysis.extraWords.single().recognizedWord.text)
        assertEquals(QuranMemorizationDiagnosticCategory.EXTRA_WORD, analysis.extraWords.single().diagnostic?.category)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
    }

    @Test
    fun alignmentPathMarksInsertedWordWithoutShiftingFollowingWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن", "الرحيم"),
            transcript = "بسم الله يا الرحمن الرحيم",
        )

        assertEquals(4, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.EXTRA,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
            ),
            analysis.alignmentPath.map { it.action },
        )
    }

    @Test
    fun alignmentPathMarksMissingWordWithoutShiftingFollowingWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن", "الرحيم"),
            transcript = "بسم الله الرحيم",
        )

        assertEquals(3, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MISSING,
                QuranMemorizationAlignmentAction.MATCH,
            ),
            analysis.alignmentPath.map { it.action },
        )
    }

    @Test
    fun repeatedCommonWordNearAyahBoundaryDoesNotCorruptNextAyah() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:boundary",
            expectedWords = listOf(
                wordAt(93, 3, 1, "وَمَا", "وما"),
                wordAt(93, 3, 2, "قَلَىٰ", "قلي"),
                wordAt(93, 8, 1, "وَوَجَدَكَ", "ووجدك"),
                wordAt(93, 8, 2, "عَآئِلٗا", "عائلا"),
                wordAt(93, 8, 3, "فَأَغۡنَىٰ", "فاغني"),
            ),
            transcript = "وما وما قلا ووجدك عائل فاغنى",
        )

        assertEquals(5, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(1, analysis.repeatedWordCount)
        assertEquals(
            listOf(
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.REPEATED,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
                QuranMemorizationAlignmentAction.MATCH,
            ),
            analysis.alignmentPath.map { it.action },
        )
    }

    @Test
    fun repeatedFalaNearAyahBoundaryDoesNotCreateFalseMissingWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:boundary",
            expectedWords = listOf(
                wordAt(93, 9, 1, "فَأَمَّا", "فاما"),
                wordAt(93, 9, 2, "ٱلۡيَتِيمَ", "اليتيم"),
                wordAt(93, 9, 3, "فَلَا", "فلا"),
                wordAt(93, 9, 4, "تَقۡهَرۡ", "تقهر"),
                wordAt(93, 10, 1, "وَأَمَّا", "واما"),
                wordAt(93, 10, 2, "ٱلسَّآئِلَ", "السائل"),
                wordAt(93, 10, 3, "فَلَا", "فلا"),
                wordAt(93, 10, 4, "تَنۡهَرۡ", "تنهر"),
            ),
            transcript = "فاما اليتيم فلا تقهر فلا واما السائل فلا تنهر",
        )

        assertEquals(8, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(1, analysis.repeatedWordCount)
        assertTrue(analysis.alignmentPath.any { it.action == QuranMemorizationAlignmentAction.REPEATED })
        assertEquals(
            listOf("93:10:1", "93:10:2", "93:10:3", "93:10:4"),
            analysis.expectedWords
                .filter { it.word.ayahNumber == 10 }
                .mapNotNull { it.matchedTranscriptWord?.let { _ -> it.word.wordId } },
        )
    }

    @Test
    fun guardedSimilarityAcceptsSingleWeakLetterTranscriptOmission() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "95:2",
            expectedWords = listOf(
                word(position = 1, arabic = "وَطُورِ", normalized = "وطور"),
                word(position = 2, arabic = "سِينِينَ", normalized = "سينين"),
            ),
            transcript = "وطور سنين",
        )

        assertAllCorrect(analysis, expectedCount = 2)
        val guardedStep = analysis.alignmentPath.single { it.expectedWordId == "1:1:2" }
        assertTrue(guardedStep.matchedByGuardedSimilarity)
        assertTrue(guardedStep.reason.contains("weak-letter"))
    }

    @Test
    fun guardedSimilarityDoesNotAcceptShortDangerousWeakLetterOmission() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:1",
            expectedWords = listOf(
                word(position = 1, arabic = "عَلِيمٌ", normalized = "عليم"),
            ),
            transcript = "علم",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertTrue(analysis.alignmentPath.none { it.matchedByGuardedSimilarity })
    }

    @Test
    fun guardedSimilarityDoesNotAcceptMultipleWeakLetterOmissions() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "95:2",
            expectedWords = listOf(
                word(position = 1, arabic = "سِينِينَ", normalized = "سينين"),
            ),
            transcript = "سنن",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun guardedSimilarityAcceptsFinalHaaPronounDroppedByTranscript() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "110:3",
            expectedWords = listOf(
                wordAt(110, 3, 1, "فَسَبِّحۡ", "فسبح"),
                wordAt(110, 3, 2, "بِحَمۡدِ", "بحمد"),
                wordAt(110, 3, 3, "رَبِّكَ", "ربك"),
                wordAt(110, 3, 4, "وَٱسۡتَغۡفِرۡهُۚ", "واستغفره"),
                wordAt(110, 3, 5, "إِنَّهُۥ", "انه"),
                wordAt(110, 3, 6, "كَانَ", "كان"),
                wordAt(110, 3, 7, "تَوَّابَۢا", "توابا"),
            ),
            transcript = "فسبح بحمد ربك واستغفر انه كان توابا",
        )

        assertAllCorrect(analysis, expectedCount = 7)
        assertEquals(0, analysis.extraWordCount)
        val guardedStep = analysis.alignmentPath.single { it.expectedWordId == "110:3:4" }
        assertTrue(guardedStep.matchedByGuardedSimilarity)
        assertTrue(guardedStep.reason.contains("final haa"))
    }

    @Test
    fun guardedSimilarityDoesNotAcceptNonFinalHaaDifference() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "110:3",
            expectedWords = listOf(
                wordAt(110, 3, 4, "وَٱسۡتَغۡفِرۡهُۚ", "واستغفره"),
            ),
            transcript = "واستهر",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertTrue(analysis.alignmentPath.none { it.matchedByGuardedSimilarity })
    }

    @Test
    fun acceptsGoogleInsertedDefiniteArticleForTanweenNoun() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "89:13",
            expectedWords = listOf(
                wordAt(89, 13, 1, "فَصَبَّ", "فصب"),
                wordAt(89, 13, 2, "عَلَيۡهِمۡ", "عليهم"),
                wordAt(89, 13, 3, "رَبُّكَ", "ربك"),
                wordAt(89, 13, 4, "سَوۡطَ", "سوط"),
                wordAt(89, 13, 5, "عَذَابٍ", "عذاب"),
            ),
            transcript = "فصب عليهم ربك سوط العذاب",
        )

        assertAllCorrect(analysis, expectedCount = 5)
        assertEquals(0, analysis.extraWordCount)
        assertEquals("العذاب", analysis.expectedWords.last().matchedTranscriptWord?.comparisonKey)
    }

    @Test
    fun doesNotAcceptInsertedDefiniteArticleForShortDangerousWord() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "112:1",
            expectedWords = listOf(
                wordAt(112, 1, 4, "أَحَدٌ", "احد"),
            ),
            transcript = "الاحد",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun matchesUthmaniAyatRasmAgainstGoogleImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "3:4",
            expectedWords = listOf(
                word(position = 1, arabic = "كَفَرُواْ", normalized = "كفروا"),
                word(position = 2, arabic = "بِـَٔايَٰتِ", normalized = "بايت"),
                word(position = 3, arabic = "ٱللَّهِ", normalized = "الله"),
            ),
            transcript = "كفروا بآيات الله",
        )

        assertEquals(3, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
    }

    @Test
    fun matchesDaggerAlifRasmAgainstGoogleImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:2",
            expectedWords = listOf(
                word(position = 1, arabic = "ذَٰلِكَ", normalized = "ذلك"),
                word(position = 2, arabic = "ٱلۡكِتَٰبُ", normalized = "الكتب"),
            ),
            transcript = "ذلك الكتاب",
        )

        assertEquals(2, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
    }

    @Test
    fun matchesWawDaggerAlifRasmAgainstGoogleImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:43",
            expectedWords = listOf(
                word(position = 1, arabic = "وَأَقِيمُواْ", normalized = "واقيموا"),
                word(position = 2, arabic = "ٱلصَّلَوٰةَ", normalized = "الصلوة"),
                word(position = 3, arabic = "وَءَاتُواْ", normalized = "واتوا"),
                word(position = 4, arabic = "ٱلزَّكَوٰةَ", normalized = "الزكوة"),
            ),
            transcript = "واقيموا الصلاة واتوا الزكاة",
        )

        assertEquals(4, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
    }

    @Test
    fun matchesPrayerAndZakahWhenGoogleUsesFinalHaaImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "9:5",
            expectedWords = listOf(
                word(position = 1, arabic = "فَإِن", normalized = "فان"),
                word(position = 2, arabic = "تَابُواْ", normalized = "تابوا"),
                word(position = 3, arabic = "وَأَقَامُواْ", normalized = "واقاموا"),
                word(position = 4, arabic = "ٱلصَّلَوٰةَ", normalized = "الصلوة"),
                word(position = 5, arabic = "وَءَاتَوُاْ", normalized = "واتوا"),
                word(position = 6, arabic = "ٱلزَّكَوٰةَ", normalized = "الزكوة"),
            ),
            transcript = "فان تابوا واقاموا الصلاه واتوا الزكاه",
        )

        assertAllCorrect(analysis, expectedCount = 6)
        assertEquals("الصلاة", analysis.expectedWords[3].matchedTranscriptWord?.comparisonKey)
        assertEquals("الزكاة", analysis.expectedWords[5].matchedTranscriptWord?.comparisonKey)
    }

    @Test
    fun matchesPrayerAndZakahWithCommonPrefixedFinalHaaTranscriptForms() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:43",
            expectedWords = listOf(
                word(position = 1, arabic = "بِٱلصَّلَوٰةِ", normalized = "بالصلوة"),
                word(position = 2, arabic = "وَٱلزَّكَوٰةِ", normalized = "والزكوة"),
            ),
            transcript = "بالصلاه والزكاه",
        )

        assertAllCorrect(analysis, expectedCount = 2)
    }

    @Test
    fun imlaeiCorpusMatchesKnownAsrSpellingsWithoutChangingDisplayedWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "imlaei:examples",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "107:1:1",
                    arabic = "أَرَءَيۡتَ",
                    normalized = "ارءيت",
                    imlaei = "أَرَأَيْتَ",
                    imlaeiSimple = "ارايت",
                ),
                wordWithImlaei(
                    wordId = "106:1:1",
                    arabic = "لِإِيلَٰفِ",
                    normalized = "لايلف",
                    imlaei = "لِإِيلَافِ",
                    imlaeiSimple = "لايلاف",
                ),
                wordWithImlaei(
                    wordId = "2:43:2",
                    arabic = "ٱلصَّلَوٰةَ",
                    normalized = "الصلوة",
                    imlaei = "الصَّلَاةَ",
                    imlaeiSimple = "الصلاة",
                ),
                wordWithImlaei(
                    wordId = "9:5:20",
                    arabic = "ٱلزَّكَوٰةَ",
                    normalized = "الزكوة",
                    imlaei = "الزَّكَاةَ",
                    imlaeiSimple = "الزكاة",
                ),
                wordWithImlaei(
                    wordId = "110:3:4",
                    arabic = "وَٱسۡتَغۡفِرۡهُۚ",
                    normalized = "واستغفره",
                    imlaei = "وَاسْتَغْفِرْهُ",
                    imlaeiSimple = "واستغفره",
                ),
                wordWithImlaei(
                    wordId = "3:4:10",
                    arabic = "بِـَٔايَٰتِ",
                    normalized = "بءايت",
                    imlaei = "بِآيَاتِ",
                    imlaeiSimple = "بايات",
                ),
            ),
            transcript = "ارأيت لايلاف الصلاة الزكاة واستغفره بآيات",
        )

        assertAllCorrect(analysis, expectedCount = 6)
        assertEquals("أَرَءَيۡتَ", analysis.expectedWords.first().comparisonWord.displayedUthmaniWord)
        assertEquals("ارايت", analysis.expectedWords.first().comparisonWord.comparisonForm.comparisonKey)
        assertTrue(
            analysis.expectedWords.all {
                it.comparisonWord.comparisonForm.rulesApplied.contains("imlaei-comparison-corpus")
            },
        )
    }

    @Test
    fun imlaeiCorpusDoesNotAllowDifferentWordsToPass() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:43",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "2:43:2",
                    arabic = "ٱلصَّلَوٰةَ",
                    normalized = "الصلوة",
                    imlaei = "الصَّلَاةَ",
                    imlaeiSimple = "الصلاة",
                ),
            ),
            transcript = "الصيام",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun connectedHamzatWaslSpokenFormMatchesWithoutChangingDisplayedWord() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "89:15",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "89:15:1",
                    arabic = "مَا",
                    normalized = "ما",
                    imlaei = "مَا",
                    imlaeiSimple = "ما",
                ),
                wordWithImlaei(
                    wordId = "89:15:2",
                    arabic = "ٱبۡتَلَىٰهُ",
                    normalized = "ابتليه",
                    imlaei = "ابْتَلَاهُ",
                    imlaeiSimple = "ابتلاه",
                ),
                wordWithImlaei(
                    wordId = "89:15:3",
                    arabic = "رَبُّهُۥ",
                    normalized = "ربه",
                    imlaei = "رَبُّهُ",
                    imlaeiSimple = "ربه",
                ),
            ),
            transcript = "ما بتلاه ربه",
        )

        assertAllCorrect(analysis, expectedCount = 3)
        val spokenStep = analysis.alignmentPath.single { it.expectedWordId == "89:15:2" }
        assertTrue(spokenStep.matchedBySpokenForm)
        assertTrue(spokenStep.reason.contains("hamzat-wasl-dropped-in-connected-recitation"))
        assertEquals("ٱبۡتَلَىٰهُ", analysis.expectedWords[1].comparisonWord.displayedUthmaniWord)
        assertTrue("بتلاه" in analysis.expectedWords[1].comparisonWord.comparisonForm.connectedSpeechComparisonKeys)
    }

    @Test
    fun droppedHamzatWaslDoesNotMatchAtStartOfAttempt() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "89:15",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "89:15:2",
                    arabic = "ٱبۡتَلَىٰهُ",
                    normalized = "ابتليه",
                    imlaei = "ابْتَلَاهُ",
                    imlaeiSimple = "ابتلاه",
                ),
                wordWithImlaei(
                    wordId = "89:15:3",
                    arabic = "رَبُّهُۥ",
                    normalized = "ربه",
                    imlaei = "رَبُّهُ",
                    imlaeiSimple = "ربه",
                ),
            ),
            transcript = "بتلاه ربه",
        )

        assertEquals(1, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertTrue(analysis.alignmentPath.none { it.matchedBySpokenForm })
    }

    @Test
    fun hamzatQatIsNeverAcceptedAsDroppedHamzatWasl() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "qat:example",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "1:1:1",
                    arabic = "أَكۡرَمَ",
                    normalized = "اكرم",
                    imlaei = "أَكْرَمَ",
                    imlaeiSimple = "اكرم",
                ),
            ),
            transcript = "كرم",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertTrue(analysis.expectedWords.first().comparisonWord.comparisonForm.connectedSpeechComparisonKeys.isEmpty())
    }

    @Test
    fun definiteArticleWaslIsNotAcceptedAsDroppedWrittenAlif() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:2",
            expectedWords = listOf(
                wordWithImlaei(
                    wordId = "2:2:2",
                    arabic = "ٱلۡكِتَٰبُ",
                    normalized = "الكتب",
                    imlaei = "الْكِتَابُ",
                    imlaeiSimple = "الكتاب",
                ),
            ),
            transcript = "لكتاب",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertTrue(analysis.expectedWords.first().comparisonWord.comparisonForm.connectedSpeechComparisonKeys.isEmpty())
    }

    @Test
    fun matchesAyatAlKursiUthmaniFormsAgainstGoogleImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:255",
            expectedWords = listOf(
                word(position = 1, arabic = "لَآ", normalized = "لا"),
                word(position = 2, arabic = "إِلَٰهَ", normalized = "اله"),
                word(position = 3, arabic = "إِلَّا", normalized = "الا"),
                word(position = 4, arabic = "هُوَ", normalized = "هو"),
                word(position = 5, arabic = "لَا", normalized = "لا"),
                word(position = 6, arabic = "تَأۡخُذُهُۥ", normalized = "تاخذه"),
                word(position = 7, arabic = "سِنَةٞ", normalized = "سنة"),
                word(position = 8, arabic = "وَلَا", normalized = "ولا"),
                word(position = 9, arabic = "نَوۡمٞۚ", normalized = "نوم"),
                word(position = 10, arabic = "بِإِذۡنِهِۦۚ", normalized = "باذنه"),
            ),
            transcript = "لا اله الا هو لا تاخذه سنه ولا نوم باذنه",
        )

        assertAllCorrect(analysis, expectedCount = 10)
        assertEquals("اله", analysis.expectedWords[1].matchedTranscriptWord?.comparisonKey)
        assertEquals("سنة", analysis.expectedWords[6].matchedTranscriptWord?.comparisonKey)
        assertEquals("باذنه", analysis.expectedWords[9].matchedTranscriptWord?.comparisonKey)
    }

    @Test
    fun matchesFinalAlifMaqsurahWithDaggerAlifAgainstGoogleImlaSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "87:4",
            expectedWords = listOf(
                word(position = 1, arabic = "وَٱلَّذِيٓ", normalized = "والذي"),
                word(position = 2, arabic = "أَخۡرَجَ", normalized = "اخرج"),
                word(position = 3, arabic = "ٱلۡمَرۡعَىٰ", normalized = "المرعي"),
            ),
            transcript = "والذى اخرج المرعى",
        )

        assertEquals(3, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(
            listOf(
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
                QuranMemorizationWordState.CORRECT,
            ),
            analysis.expectedWords.map { it.state },
        )
        assertEquals("المرعي", analysis.expectedWords[2].comparisonWord.comparisonForm.comparisonKey)
    }

    @Test
    fun matchesOtherFinalAlifMaqsurahDaggerWordsWithoutOneOffFixes() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "87:19",
            expectedWords = listOf(
                word(position = 1, arabic = "وَمُوسَىٰ", normalized = "وموسي"),
                word(position = 2, arabic = "ٱلۡأَعۡلَىٰ", normalized = "الاعلي"),
            ),
            transcript = "وموسى الاعلى",
        )

        assertAllCorrect(analysis, expectedCount = 2)
    }

    @Test
    fun matchesPauseWhenGoogleWritesFinalAlifMaqsurahAsAlif() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:2",
            expectedWords = listOf(
                word(position = 1, arabic = "إِذَا", normalized = "اذا"),
                word(position = 2, arabic = "سَجَىٰ", normalized = "سجي"),
            ),
            transcript = "اذا سجا",
        )

        assertAllCorrect(analysis, expectedCount = 2)
        assertEquals(
            setOf("سجي", "سجا"),
            analysis.expectedWords[1].comparisonWord.comparisonForm.comparisonKeys,
        )
    }

    @Test
    fun matchesPauseWhenGoogleDropsFinalTanweenAlif() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:6",
            expectedWords = listOf(
                word(position = 1, arabic = "يَتِيمٗا", normalized = "يتيما"),
                word(position = 2, arabic = "فَـَٔاوَىٰ", normalized = "فاوي"),
            ),
            transcript = "يتيم فاوى",
        )

        assertAllCorrect(analysis, expectedCount = 2)
        assertEquals(
            setOf("يتيما", "يتيم"),
            analysis.expectedWords.first().comparisonWord.comparisonForm.comparisonKeys,
        )
    }

    @Test
    fun matchesTaMarbutaWhenGoogleWritesFinalHaa() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:11",
            expectedWords = listOf(
                word(position = 1, arabic = "بِنِعۡمَةِ", normalized = "بنعمة"),
                word(position = 2, arabic = "رَبِّكَ", normalized = "ربك"),
                word(position = 3, arabic = "فَحَدِّثۡ", normalized = "فحدث"),
            ),
            transcript = "بنعمه ربك فحدث",
        )

        assertAllCorrect(analysis, expectedCount = 3)
    }

    @Test
    fun matchesContractedLamWhenGoogleSplitsItIntoTwoWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:4",
            expectedWords = listOf(
                word(position = 1, arabic = "وَلَلۡأٓخِرَةُ", normalized = "وللاخرة"),
                word(position = 2, arabic = "خَيۡرٞ", normalized = "خير"),
            ),
            transcript = "ولا الاخره خير",
        )

        assertAllCorrect(analysis, expectedCount = 2)
        assertEquals("ولا الاخره", analysis.expectedWords.first().matchedTranscriptWord?.text)
    }

    @Test
    fun matchesAlLaylWhenQpcShaddaDropsTheRootLam() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:2",
            expectedWords = listOf(
                word(position = 1, arabic = "وَٱلَّيۡلِ", normalized = "واليل"),
            ),
            transcript = "والليل",
        )

        assertAllCorrect(analysis, expectedCount = 1)
    }

    @Test
    fun doesNotAllowPauseEndingRulesToAcceptDifferentWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "93:2",
            expectedWords = listOf(
                word(position = 1, arabic = "سَجَىٰ", normalized = "سجي"),
                word(position = 2, arabic = "يَتِيمٗا", normalized = "يتيما"),
            ),
            transcript = "سجد يتين",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(2, analysis.missingWordCount)
        assertEquals(2, analysis.extraWordCount)
    }

    @Test
    fun matchesJoinedVocativeYaWhenGoogleSplitsItIntoTwoWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "20:11",
            expectedWords = listOf(
                word(position = 1, arabic = "يَٰمُوسَىٰٓ", normalized = "يموسي"),
            ),
            transcript = "يا موسى",
        )

        assertEquals(1, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals("يا موسى", analysis.expectedWords.single().matchedTranscriptWord?.text)
        assertEquals("ياموسي", analysis.expectedWords.single().matchedTranscriptWord?.comparisonKey)
    }

    @Test
    fun matchesJoinedYaAyyuhaWhenGoogleSplitsItIntoTwoWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:21",
            expectedWords = listOf(
                word(position = 1, arabic = "يَٰٓأَيُّهَا", normalized = "يايها"),
                word(position = 2, arabic = "ٱلنَّاسُ", normalized = "الناس"),
            ),
            transcript = "يا ايها الناس",
        )

        assertAllCorrect(analysis, expectedCount = 2)
        assertEquals("يا ايها", analysis.expectedWords.first().matchedTranscriptWord?.text)
    }

    @Test
    fun matchesHamzaCarrierVariationsDeterministically() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:48",
            expectedWords = listOf(
                word(position = 1, arabic = "شَيۡـٔٗا", normalized = "شيا"),
                word(position = 2, arabic = "وَلَا", normalized = "ولا"),
                word(position = 3, arabic = "يُقۡبَلُ", normalized = "يقبل"),
            ),
            transcript = "شيئا ولا يقبل",
        )

        assertAllCorrect(analysis, expectedCount = 3)
    }

    @Test
    fun matchesSmallYaAndSmallWawQpcMarksAgainstModernSpelling() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:25",
            expectedWords = listOf(
                word(position = 1, arabic = "بِهِۦ", normalized = "به"),
                word(position = 2, arabic = "لَهُۥ", normalized = "له"),
                word(position = 3, arabic = "ٱلۡأَنۡهَٰرُ", normalized = "الانهر"),
            ),
            transcript = "به له الانهار",
        )

        assertAllCorrect(analysis, expectedCount = 3)
    }

    @Test
    fun doesNotAcceptMissingDaggerAlifWhereModernArabicRequiresIt() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:2",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱلۡكِتَٰبُ", normalized = "الكتب"),
            ),
            transcript = "الكتب",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals(QuranMemorizationWordState.MISSING, analysis.expectedWords.single().state)
    }

    @Test
    fun doesNotAcceptUthmaniWawAlifFormWhereModernArabicRequiresAlif() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:43",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱلصَّلَوٰةَ", normalized = "الصلوة"),
            ),
            transcript = "الصلوة",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals(QuranMemorizationWordState.MISSING, analysis.expectedWords.single().state)
    }

    @Test
    fun doesNotAcceptUthmaniZakahWawFormWhereModernArabicRequiresAlif() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:43",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱلزَّكَوٰةَ", normalized = "الزكوة"),
            ),
            transcript = "الزكوة",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals(QuranMemorizationWordState.MISSING, analysis.expectedWords.single().state)
    }

    @Test
    fun doesNotTreatUnrelatedFinalHaaWordAsTaMarbutaMatch() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱللَّهِ", normalized = "الله"),
            ),
            transcript = "اللة",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun doesNotTreatDifferentPronounInBiIdhnihiAsMatch() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "2:255",
            expectedWords = listOf(
                word(position = 1, arabic = "بِإِذۡنِهِۦۚ", normalized = "باذنه"),
            ),
            transcript = "باذنك",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun doesNotMatchGenuinelyDifferentWords() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:2",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱلۡحَمۡدُ", normalized = "الحمد"),
            ),
            transcript = "العمد",
        )

        assertEquals(0, analysis.recognizedWordCount)
        assertEquals(1, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
    }

    @Test
    fun exposesComparisonDiagnosticsForFailedMatches() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:2",
            expectedWords = listOf(
                word(position = 1, arabic = "ٱلۡحَمۡدُ", normalized = "الحمد"),
            ),
            transcript = "العمد",
        )

        val diagnostic = analysis.expectedWords.single().diagnostic
        requireNotNull(diagnostic)
        assertEquals("1:1:1", diagnostic.expectedWordId)
        assertEquals("ٱلۡحَمۡدُ", diagnostic.displayedQuranWord)
        assertEquals("الحمد", diagnostic.expectedComparisonKey)
        assertEquals("العمد", diagnostic.transcriptWord)
        assertEquals("العمد", diagnostic.transcriptComparisonKey)
    }

    @Test
    fun marksLowConfidenceMatchedWordsAsUnknown() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:1",
            expectedWords = words("بسم", "الله", "الرحمن"),
            transcript = "بسم الله الرحمن",
            wordTimestamps = listOf(
                SpeechRecognitionWord(word = "بسم", confidence = 0.98f),
                SpeechRecognitionWord(word = "الله", confidence = 0.2f),
                SpeechRecognitionWord(word = "الرحمن", confidence = 0.95f),
            ),
        )

        assertEquals(2, analysis.recognizedWordCount)
        assertEquals(1, analysis.unknownWordCount)
        assertEquals(QuranMemorizationWordState.UNKNOWN, analysis.expectedWords[1].state)
        assertEquals(QuranMemorizationDiagnosticCategory.UNKNOWN, analysis.expectedWords[1].diagnostic?.category)
    }

    @Test
    fun logsPerfectRecitationAttempt() {
        val ayah = ayah(words("قل", "هو", "الله", "احد"))
        val speechResult = speechResult(transcript = "قل هو الله احد")
        val analysis = QuranMemorizationAnalysisEngine.analyze(ayah, speechResult)
        val attempt = QuranMemorizationAttemptFactory.from(
            ayah = ayah,
            durationMs = 3200L,
            speechResult = speechResult,
            analysis = analysis,
            timestampMs = 1234L,
        )

        assertEquals(true, attempt.transcriptionSucceeded)
        assertEquals(true, attempt.perfectMatch)
        assertEquals(4, attempt.recognizedCount)
        assertEquals(0, attempt.missingCount)
        assertEquals(0, attempt.extraCount)
        assertEquals(0, attempt.repeatedCount)
        assertEquals(ayah.words.map { it.wordId }, attempt.expectedWordIds)
        assertEquals(ayah.words.map { it.wordId }, attempt.matchedWordIds)
        assertEquals(emptyList<String>(), attempt.diagnostics)
        assertEquals(4, attempt.expectedComparisonKeys.size)
        assertEquals(4, attempt.transcriptComparisonKeys.size)
    }

    @Test
    fun logsAttemptDiagnosticsThatAgreeWithMissingExtraAndRepeatedCounts() {
        val ayah = ayah(words("بسم", "الله", "الرحمن", "الرحيم"))
        val speechResult = speechResult(transcript = "بسم الله الله يا الرحيم")
        val analysis = QuranMemorizationAnalysisEngine.analyze(ayah, speechResult)
        val attempt = QuranMemorizationAttemptFactory.from(
            ayah = ayah,
            durationMs = 5100L,
            speechResult = speechResult,
            analysis = analysis,
            timestampMs = 5678L,
        )

        assertEquals(analysis.recognizedWordCount, attempt.recognizedCount)
        assertEquals(analysis.missingWordCount, attempt.missingCount)
        assertEquals(analysis.extraWordCount, attempt.extraCount)
        assertEquals(analysis.repeatedWordCount, attempt.repeatedCount)
        assertEquals(listOf("1:1:3"), attempt.missingWordIds)
        assertEquals(listOf("يا"), attempt.extraTranscriptWords)
        assertEquals(listOf("الله"), attempt.repeatedTranscriptWords)
        assertEquals(
            setOf(
                QuranMemorizationDiagnosticCategory.MISSING_WORD,
                QuranMemorizationDiagnosticCategory.EXTRA_WORD,
                QuranMemorizationDiagnosticCategory.REPEATED_WORD,
            ),
            attempt.diagnostics.map { it.category }.toSet(),
        )
    }

    @Test
    fun logsFailedTranscriptionAttemptWithoutAnalysis() {
        val ayah = ayah(words("قل", "هو"))
        val speechResult = SpeechRecognitionResult(
            providerName = "Google Speech",
            modelName = "chirp_3",
            latencyMs = 999L,
            errorMessage = "Transcription failed.",
        )
        val attempt = QuranMemorizationAttemptFactory.from(
            ayah = ayah,
            durationMs = 1000L,
            speechResult = speechResult,
            analysis = null,
            timestampMs = 9999L,
        )

        assertEquals(false, attempt.transcriptionSucceeded)
        assertEquals(false, attempt.perfectMatch)
        assertEquals("Transcription failed.", attempt.errorMessage)
        assertEquals(listOf("1:1:1", "1:1:2"), attempt.expectedWordIds)
        assertEquals(emptyList<String>(), attempt.matchedWordIds)
        assertEquals(emptyList<String>(), attempt.diagnostics)
    }

    @Test
    fun attemptHistoryKeepsOnlyMostRecentFiftyAttempts() {
        QuranMemorizationAttemptHistory.clear()
        val ayah = ayah(words("قل"))
        val speechResult = speechResult(transcript = "قل")
        val analysis = QuranMemorizationAnalysisEngine.analyze(ayah, speechResult)

        repeat(55) { index ->
            QuranMemorizationAttemptHistory.record(
                QuranMemorizationAttemptFactory.from(
                    ayah = ayah,
                    durationMs = index.toLong(),
                    speechResult = speechResult,
                    analysis = analysis,
                    timestampMs = index.toLong(),
                ),
            )
        }

        val recent = QuranMemorizationAttemptHistory.recent()
        assertEquals(50, recent.size)
        assertEquals("1:1-54", recent.first().attemptId)
        assertEquals("1:1-5", recent.last().attemptId)
        QuranMemorizationAttemptHistory.clear()
    }

    @Test
    fun handlesVeryShortAyah() {
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "112:1",
            expectedWords = words("قل"),
            transcript = "قل",
        )

        assertAllCorrect(analysis, expectedCount = 1)
    }

    @Test
    fun handlesLongAyahWithMultipleConsecutiveFailures() {
        val expectedWords = words(
            "بسم",
            "الله",
            "الرحمن",
            "الرحيم",
            "مالك",
            "يوم",
            "الدين",
            "اياك",
            "نعبد",
            "واياك",
            "نستعين",
            "اهدنا",
        )
        val analysis = QuranMemorizationAnalysisEngine.analyze(
            verseKey = "1:long",
            expectedWords = expectedWords,
            transcript = "بسم الله الرحيم مالك يوم الدنيا اياك اياك نستعين",
        )

        assertEquals(7, analysis.recognizedWordCount)
        assertEquals(5, analysis.missingWordCount)
        assertEquals(1, analysis.extraWordCount)
        assertEquals(1, analysis.repeatedWordCount)
        assertEquals(
            analysis.missingWordCount,
            analysis.expectedWords.count { it.diagnostic?.category == QuranMemorizationDiagnosticCategory.MISSING_WORD },
        )
    }

    private fun assertAllCorrect(
        analysis: QuranMemorizationAnalysis,
        expectedCount: Int,
    ) {
        assertEquals(expectedCount, analysis.recognizedWordCount)
        assertEquals(0, analysis.missingWordCount)
        assertEquals(0, analysis.extraWordCount)
        assertEquals(0, analysis.repeatedWordCount)
        assertEquals(List(expectedCount) { QuranMemorizationWordState.CORRECT }, analysis.expectedWords.map { it.state })
    }

    private fun words(vararg values: String): List<QuranWord> =
        values.mapIndexed { index, value ->
            word(
                position = index + 1,
                arabic = value,
                normalized = normalizeArabicTranscript(value).replace(" ", ""),
            )
        }

    private fun ayah(words: List<QuranWord>): QuranAyah =
        QuranAyah(
            verseKey = "1:1",
            surahNumber = 1,
            ayahNumber = 1,
            arabicText = words.joinToString(" ") { it.arabicText },
            words = words,
        )

    private fun speechResult(transcript: String): SpeechRecognitionResult =
        SpeechRecognitionResult(
            transcript = transcript,
            normalizedTranscript = normalizeArabicTranscript(transcript),
            providerName = "Google Speech",
            modelName = "chirp_3",
            confidence = 0.95f,
            latencyMs = 1234L,
        )

    private fun word(
        position: Int,
        arabic: String,
        normalized: String,
    ): QuranWord =
        QuranWord(
            wordId = "1:1:$position",
            surahNumber = 1,
            ayahNumber = 1,
            wordPosition = position,
            arabicText = arabic,
            normalizedArabicText = normalized,
        )

    private fun wordAt(
        surahNumber: Int,
        ayahNumber: Int,
        position: Int,
        arabic: String,
        normalized: String,
    ): QuranWord =
        QuranWord(
            wordId = "$surahNumber:$ayahNumber:$position",
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            wordPosition = position,
            arabicText = arabic,
            normalizedArabicText = normalized,
        )

    private fun wordWithImlaei(
        wordId: String,
        arabic: String,
        normalized: String,
        imlaei: String,
        imlaeiSimple: String,
    ): QuranWord {
        val parts = wordId.split(':').map { it.toInt() }
        return QuranWord(
            wordId = wordId,
            surahNumber = parts[0],
            ayahNumber = parts[1],
            wordPosition = parts[2],
            arabicText = arabic,
            normalizedArabicText = normalized,
            metadata = QuranWordMetadata(
                wordId = wordId,
                arabicText = arabic,
                normalizedArabicText = normalized,
                imlaeiText = imlaei,
                imlaeiSimpleText = imlaeiSimple,
            ),
        )
    }
}
