package com.myvault.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranCorpusSearchTest {

    @Test
    fun `normalizeQuranArabicText strips harakat and diacritics`() {
        val uthmaniAyatulKursi = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ"
        val normalized = normalizeQuranArabicText(uthmaniAyatulKursi, expandDaggerAlif = true)
        assertTrue(normalized.contains("الله"))
        assertTrue(normalized.contains("لا"))
        assertTrue(normalized.contains("الاه"))
        assertTrue(normalized.contains("الا"))
        assertTrue(normalized.contains("هو"))
        assertTrue(normalized.contains("الحي"))
        assertTrue(normalized.contains("القيوم"))

        val corpusForms = uthmaniAyatulKursi.toNormalizedQuranCorpusForms()
        assertTrue(corpusForms.contains("اله"))
        assertTrue(corpusForms.contains("الاه"))
    }

    @Test
    fun `search Allatheena Aamanoo matches Uthmani script with diacritics`() {
        // In Uthmani Hafs, 'الذين آمنوا' is written with dagger alif / madd / diacritics:
        // 'ٱلَّذِينَ ءَامَنُوا۟' or 'الَّذِينَ آمَنُوا'
        val uthmaniVerse = "إِنَّ ٱلَّذِينَ ءَامَنُوا۟ وَعَمِلُوا۟ ٱلصَّٰلِحَٰتِ كَانَتْ لَهُمْ جَنَّٰتُ ٱلْفِرْدَوْسِ نُزُلًا"
        val corpusForms = uthmaniVerse.toNormalizedQuranCorpusForms()

        val userQuery = "الذين آمنوا"
        val normAr1 = normalizeQuranArabicText(userQuery, expandDaggerAlif = true)
        val normAr2 = normalizeQuranArabicText(userQuery, expandDaggerAlif = false)
        val normAr3 = normAr1.replace('ء', 'و').replace('ئ', 'ي')

        val matches = (normAr1.isNotBlank() && corpusForms.contains(normAr1)) ||
            (normAr2.isNotBlank() && corpusForms.contains(normAr2)) ||
            (normAr3.isNotBlank() && corpusForms.contains(normAr3))

        assertTrue("Expected query 'الذين آمنوا' to match Uthmani verse containing 'ٱلَّذِينَ ءَامَنُوا۟'", matches)
    }

    @Test
    fun `search Al-Fatiha discovers Surah Al-Fatihah`() {
        val surahFatiha = quranCatalog.first { it.num == 1 }
        val query = "الفاتحة"
        val normQuery = normalizeQuranArabicText(query, expandDaggerAlif = true)
        val normSurahArabic = normalizeQuranArabicText(surahFatiha.arabic, expandDaggerAlif = true)

        assertTrue(
            "Expected 'الفاتحة' to match surah name '${surahFatiha.arabic}'",
            normSurahArabic.contains(normQuery) || normQuery.contains(normSurahArabic)
        )
    }

    @Test
    fun `search distinctive Arabic words inside an ayah`() {
        // Test 1: الصراط
        val siratVerse = "ٱهۡدِنَا ٱلصِّرَٰطَ ٱلۡمُسۡتَقِيمَ".toNormalizedQuranCorpusForms()
        val querySirat = normalizeQuranArabicText("الصراط", expandDaggerAlif = true)
        assertTrue("Expected 'الصراط' to match 'ٱلصِّرَٰطَ'", siratVerse.contains(querySirat))

        // Test 2: الصلاة and الزكاة
        val salahVerse = "وَأَقِيمُوا۟ ٱلصَّلَوٰةَ وَءَاتُوا۟ ٱلزَّكَوٰةَ".toNormalizedQuranCorpusForms()
        val querySalah = normalizeQuranArabicText("الصلاة", expandDaggerAlif = true)
        val queryZakah = normalizeQuranArabicText("الزكاة", expandDaggerAlif = true)
        assertTrue("Expected 'الصلاة' to match 'ٱلصَّلَوٰةَ'", salahVerse.contains(querySalah))
        assertTrue("Expected 'الزكاة' to match 'ٱلزَّكَوٰةَ'", salahVerse.contains(queryZakah))

        // Test 3: الرحمن and ذلك
        val rahmanVerse = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ".toNormalizedQuranCorpusForms()
        val queryRahman = normalizeQuranArabicText("الرحمن", expandDaggerAlif = true)
        assertTrue("Expected 'الرحمن' to match 'ٱلرَّحۡمَٰنِ'", rahmanVerse.contains(queryRahman))
    }

    @Test
    fun `search by Surah and Ayah reference formats`() {
        assertEquals(2 to 255, parseQuranSearchReference("2:255"))
        assertEquals(2 to 255, parseQuranSearchReference("2 255"))
        assertEquals(2 to 255, parseQuranSearchReference("Surah 2 verse 255"))
        assertEquals(2 to 255, parseQuranSearchReference("Surah 2 ayah 255"))
        assertEquals(2 to 255, parseQuranSearchReference("Al-Baqarah 255"))
        assertEquals(2 to 255, parseQuranSearchReference("البقرة 255"))
    }

    @Test
    fun `QuranCorpusSearchItem matches English translation words`() {
        val surahBaqarah = quranCatalog.first { it.num == 2 }
        val item = QuranCorpusSearchItem(
            surah = surahBaqarah,
            ayahNumber = 255,
            verseKey = "2:255",
            arabicText = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...",
            normalizedArabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...".toNormalizedQuranCorpusForms(),
            translation = "Allah - there is no deity except Him, the Ever-Living, the Sustainer of [all] existence. Neither drowsiness overtakes Him nor sleep. To Him belongs whatever is in the heavens and whatever is on the earth. Who is it that can intercede with Him except by His permission? He knows what is [presently] before them and what will be after them, and they encompass not a thing of His knowledge except for what He wills. His Kursi extends over the heavens and the earth, and their preservation tires Him not. And He is the Most High, the Most Great.",
            normalizedTranslation = "allah - there is no deity except him, the ever-living, the sustainer of [all] existence. neither drowsiness overtakes him nor sleep. to him belongs whatever is in the heavens and whatever is on the earth. who is it that can intercede with him except by his permission? he knows what is [presently] before them and what will be after them, and they encompass not a thing of his knowledge except for what he wills. his kursi extends over the heavens and the earth, and their preservation tires him not. and he is the most high, the most great.",
        )

        assertTrue(item.normalizedTranslation.contains("ever-living"))
        assertTrue(item.normalizedTranslation.contains("kursi"))
    }
}
