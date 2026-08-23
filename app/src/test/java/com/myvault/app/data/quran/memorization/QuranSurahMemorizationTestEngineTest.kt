package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.data.quran.speech.normalizeArabicTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuranSurahMemorizationTestEngineTest {
    @Test
    fun analysesPerfectShortSurahAsPassed() {
        val ayahs = listOf(
            ayah(112, 1, "قل", "هو", "الله", "احد"),
            ayah(112, 2, "الله", "الصمد"),
        )
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(112, "Al-Ikhlas", ayahs.size),
            ayahs = ayahs,
            speechResult = speechResult("قل هو الله احد الله الصمد"),
        )

        requireNotNull(analysis)
        assertEquals(6, analysis.recognizedCount)
        assertEquals(0, analysis.missingCount)
        assertEquals(0, analysis.extraCount)
        assertEquals(100, analysis.overallScore)
        assertEquals(QuranMemorizationScoreGrade.EXCELLENT, analysis.grade)
        assertEquals(emptyList<String>(), analysis.ayahsNeedingReview)
        assertEquals(
            listOf(AyahMemorizationStatus.PASSED, AyahMemorizationStatus.PASSED),
            analysis.ayahResults.map { it.status },
        )
    }

    @Test
    fun marksOnlyAffectedAyahAsNeedingReviewWhenWordIsMissing() {
        val ayahs = listOf(
            ayah(112, 1, "قل", "هو", "الله", "احد"),
            ayah(112, 2, "الله", "الصمد"),
        )
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(112, "Al-Ikhlas", ayahs.size),
            ayahs = ayahs,
            speechResult = speechResult("قل هو الله احد الله"),
        )

        requireNotNull(analysis)
        assertEquals(1, analysis.missingCount)
        assertEquals(listOf("112:2"), analysis.ayahsNeedingReview)
        assertEquals(AyahMemorizationStatus.PASSED, analysis.ayahResults[0].status)
        assertEquals(AyahMemorizationStatus.INCORRECT, analysis.ayahResults[1].status)
        assertEquals(listOf("112:2:2"), analysis.missingWordIds)
    }

    @Test
    fun continueRevisionDoesNotMarkUnattemptedRemainingAyahsMissing() {
        val ayahs = listOf(
            ayah(112, 1, "قل", "هو", "الله", "احد"),
            ayah(112, 2, "الله", "الصمد"),
            ayah(112, 3, "لم", "يلد"),
        )
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(112, "Al-Ikhlas", ayahs.size),
            ayahs = ayahs,
            speechResult = speechResult("قل هو الله احد الله الصمد"),
            testMode = QuranSurahMemorizationTestMode.CONTINUE_REVISION,
        )

        requireNotNull(analysis)
        assertEquals(QuranSurahMemorizationTestMode.CONTINUE_REVISION, analysis.testMode)
        assertEquals(2, analysis.totalAyahs)
        assertEquals(0, analysis.missingCount)
        assertEquals(listOf("112:1", "112:2"), analysis.ayahResults.map { it.verseKey })
        assertEquals(emptyList<String>(), analysis.ayahsNeedingReview)
    }

    @Test
    fun acceptsAdDuhaGoogleTranscriptStyleWithoutFalseMissingWords() {
        val ayahs = adDuhaAyahs()
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(93, "Ad-Dhuhaa", ayahs.size),
            ayahs = ayahs,
            speechResult = speechResult(
                "والضحى والليل اذا سجا ما ودعك ربك وما قلا " +
                    "ولا الاخره خير لك من الاولى ولسوف يعطيك ربك فترضى " +
                    "الم يجدك يتيم فاوى ووجدك ضالا فهدى ووجدك عائل فاغنى " +
                    "فاما اليتيم فلا تقهر واما السائل فلا تنهر واما بنعمه ربك فحدث",
            ),
        )

        requireNotNull(analysis)
        assertEquals(40, analysis.recognizedCount)
        assertEquals(0, analysis.missingCount)
        assertEquals(0, analysis.extraCount)
        assertEquals(0, analysis.repeatedCount)
        assertEquals(QuranMemorizationScoreGrade.EXCELLENT, analysis.grade)
        assertEquals(emptyList<String>(), analysis.ayahsNeedingReview)
        assertEquals(40, analysis.alignmentPath.count { it.action == QuranMemorizationAlignmentAction.MATCH })
    }

    @Test
    fun acceptsAtTinGoogleTranscriptStyleWithoutFalseMissingWords() {
        val ayahs = atTinAyahs()
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(95, "At-Tin", ayahs.size),
            ayahs = ayahs,
            speechResult = speechResult(
                "والتين والزيتون وطور سنين وهذا البلد الامين " +
                    "لقد خلقنا الانسان في احسن تقويم ثم رددناه اسفل سافلين " +
                    "الا الذين امنوا وعملوا الصالحات فلهم اجر غير ممنون " +
                    "فما يكذبك بعد بالدين اليس الله باحكم الحاكمين",
            ),
        )

        requireNotNull(analysis)
        assertEquals(34, analysis.recognizedCount)
        assertEquals(0, analysis.missingCount)
        assertEquals(0, analysis.extraCount)
        assertEquals(0, analysis.repeatedCount)
        assertEquals(QuranMemorizationScoreGrade.EXCELLENT, analysis.grade)
        assertEquals(emptyList<String>(), analysis.ayahsNeedingReview)
    }

    @Test
    fun savesAndRestoresSurahAttempt() {
        val ayahs = listOf(
            ayah(112, 1, "قل", "هو", "الله", "احد"),
        )
        val speech = speechResult("قل هو الله احد")
        val analysis = QuranSurahMemorizationTestEngine.analyze(
            surah = surah(112, "Al-Ikhlas", ayahs.size),
            ayahs = ayahs,
            speechResult = speech,
        )
        val saved = QuranSurahMemorizationAttemptFactory.from(
            surah = surah(112, "Al-Ikhlas", ayahs.size),
            ayahs = ayahs,
            durationMs = 4200L,
            speechResult = speech,
            analysis = analysis,
            timestampMs = 99L,
        ).toSavedAttempt()

        val restored = saved.toSurahAttemptPreferenceEntry().toQuranSurahMemorizationSavedAttemptOrNull()

        assertNotNull(restored)
        requireNotNull(restored)
        assertEquals(saved.attemptId, restored.attemptId)
        assertEquals(saved.surahNumber, restored.surahNumber)
        assertEquals(saved.overallScore, restored.overallScore)
        assertEquals(saved.ayahResults.single().verseKey, restored.ayahResults.single().verseKey)
    }

    private fun surah(number: Int, name: String, ayahCount: Int): SurahInfo =
        SurahInfo(
            num = number,
            name = name,
            arabic = name,
            ayat = ayahCount,
            type = "Makki",
            juz = 30,
        )

    private fun ayah(surahNumber: Int, ayahNumber: Int, vararg words: String): QuranAyah =
        QuranAyah(
            verseKey = "$surahNumber:$ayahNumber",
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            arabicText = words.joinToString(" "),
            words = words.mapIndexed { index, value ->
                QuranWord(
                    wordId = "$surahNumber:$ayahNumber:${index + 1}",
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber,
                    wordPosition = index + 1,
                    arabicText = value,
                    normalizedArabicText = normalizeArabicTranscript(value).replace(" ", ""),
                )
            },
        )

    private fun adDuhaAyahs(): List<QuranAyah> =
        listOf(
            ayahFromPairs(
                93,
                1,
                "وَٱلضُّحَىٰ" to "والضحي",
            ),
            ayahFromPairs(
                93,
                2,
                "وَٱلَّيۡلِ" to "واليل",
                "إِذَا" to "اذا",
                "سَجَىٰ" to "سجي",
            ),
            ayahFromPairs(
                93,
                3,
                "مَا" to "ما",
                "وَدَّعَكَ" to "ودعك",
                "رَبُّكَ" to "ربك",
                "وَمَا" to "وما",
                "قَلَىٰ" to "قلي",
            ),
            ayahFromPairs(
                93,
                4,
                "وَلَلۡأٓخِرَةُ" to "وللاخرة",
                "خَيۡرٞ" to "خير",
                "لَّكَ" to "لك",
                "مِنَ" to "من",
                "ٱلۡأُولَىٰ" to "الاولي",
            ),
            ayahFromPairs(
                93,
                5,
                "وَلَسَوۡفَ" to "ولسوف",
                "يُعۡطِيكَ" to "يعطيك",
                "رَبُّكَ" to "ربك",
                "فَتَرۡضَىٰٓ" to "فترضي",
            ),
            ayahFromPairs(
                93,
                6,
                "أَلَمۡ" to "الم",
                "يَجِدۡكَ" to "يجدك",
                "يَتِيمٗا" to "يتيما",
                "فَـَٔاوَىٰ" to "فاوي",
            ),
            ayahFromPairs(
                93,
                7,
                "وَوَجَدَكَ" to "ووجدك",
                "ضَآلّٗا" to "ضالا",
                "فَهَدَىٰ" to "فهدي",
            ),
            ayahFromPairs(
                93,
                8,
                "وَوَجَدَكَ" to "ووجدك",
                "عَآئِلٗا" to "عائلا",
                "فَأَغۡنَىٰ" to "فاغني",
            ),
            ayahFromPairs(
                93,
                9,
                "فَأَمَّا" to "فاما",
                "ٱلۡيَتِيمَ" to "اليتيم",
                "فَلَا" to "فلا",
                "تَقۡهَرۡ" to "تقهر",
            ),
            ayahFromPairs(
                93,
                10,
                "وَأَمَّا" to "واما",
                "ٱلسَّآئِلَ" to "السائل",
                "فَلَا" to "فلا",
                "تَنۡهَرۡ" to "تنهر",
            ),
            ayahFromPairs(
                93,
                11,
                "وَأَمَّا" to "واما",
                "بِنِعۡمَةِ" to "بنعمة",
                "رَبِّكَ" to "ربك",
                "فَحَدِّثۡ" to "فحدث",
            ),
        )

    private fun atTinAyahs(): List<QuranAyah> =
        listOf(
            ayahFromPairs(
                95,
                1,
                "وَٱلتِّينِ" to "والتين",
                "وَٱلزَّيۡتُونِ" to "والزيتون",
            ),
            ayahFromPairs(
                95,
                2,
                "وَطُورِ" to "وطور",
                "سِينِينَ" to "سينين",
            ),
            ayahFromPairs(
                95,
                3,
                "وَهَٰذَا" to "وهذا",
                "ٱلۡبَلَدِ" to "البلد",
                "ٱلۡأَمِينِ" to "الامين",
            ),
            ayahFromPairs(
                95,
                4,
                "لَقَدۡ" to "لقد",
                "خَلَقۡنَا" to "خلقنا",
                "ٱلۡإِنسَٰنَ" to "الانسن",
                "فِيٓ" to "في",
                "أَحۡسَنِ" to "احسن",
                "تَقۡوِيمٖ" to "تقويم",
            ),
            ayahFromPairs(
                95,
                5,
                "ثُمَّ" to "ثم",
                "رَدَدۡنَٰهُ" to "رددنه",
                "أَسۡفَلَ" to "اسفل",
                "سَٰفِلِينَ" to "سفلين",
            ),
            ayahFromPairs(
                95,
                6,
                "إِلَّا" to "الا",
                "ٱلَّذِينَ" to "الذين",
                "ءَامَنُواْ" to "ءامنوا",
                "وَعَمِلُواْ" to "وعملوا",
                "ٱلصَّٰلِحَٰتِ" to "الصلحت",
                "فَلَهُمۡ" to "فلهم",
                "أَجۡرٌ" to "اجر",
                "غَيۡرُ" to "غير",
                "مَمۡنُونٖ" to "ممنون",
            ),
            ayahFromPairs(
                95,
                7,
                "فَمَا" to "فما",
                "يُكَذِّبُكَ" to "يكذبك",
                "بَعۡدُ" to "بعد",
                "بِٱلدِّينِ" to "بالدين",
            ),
            ayahFromPairs(
                95,
                8,
                "أَلَيۡسَ" to "اليس",
                "ٱللَّهُ" to "الله",
                "بِأَحۡكَمِ" to "باحكم",
                "ٱلۡحَٰكِمِينَ" to "الحكمين",
            ),
        )

    private fun ayahFromPairs(
        surahNumber: Int,
        ayahNumber: Int,
        vararg words: Pair<String, String>,
    ): QuranAyah =
        QuranAyah(
            verseKey = "$surahNumber:$ayahNumber",
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            arabicText = words.joinToString(" ") { it.first },
            words = words.mapIndexed { index, (arabic, normalized) ->
                QuranWord(
                    wordId = "$surahNumber:$ayahNumber:${index + 1}",
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber,
                    wordPosition = index + 1,
                    arabicText = arabic,
                    normalizedArabicText = normalized,
                )
            },
        )

    private fun speechResult(transcript: String): SpeechRecognitionResult =
        SpeechRecognitionResult(
            transcript = transcript,
            normalizedTranscript = normalizeArabicTranscript(transcript),
            providerName = "Google Speech",
            modelName = "chirp_3",
            confidence = 0.95f,
            latencyMs = 1200L,
        )
}
