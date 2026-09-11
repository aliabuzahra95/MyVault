package com.myvault.app.ui.quran

import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.quranCatalog
import org.junit.Assert.*
import org.junit.Test

class ReflectionListIndexTest {
    private fun item(id: String, surah: Int, ayah: Int, time: Long, body: String) = QuranReflectionItem(
        id, "Reflection", quranCatalog.first { it.num == surah }.name, surah, ayah, "$surah:$ayah",
        "canonical Arabic should not be searched", "translation should not be searched", body, body, time,
    )
    private val a = item("a", 8, 53, 30, "Change the favour bestowed upon a people.")
    private val b = item("b", 22, 11, 20, "Faith in hardship. تأمل في الإيمان")
    private val c = item("c", 22, 12, 40, "الله يعلم ما في القلوب")
    private val d = item("d", 2, 183, 10, "A long reflection. ".repeat(100) + "Final searchable sentence")
    private val entries = listOf(a, b, c, d)
    private val index = ReflectionListIndex(entries, quranCatalog)
    private fun find(query: String, surah: Int? = null) = index.select(query, surah, ReflectionSort.Newest).map { it.noteId }

    @Test fun bodySearchIncludesTheEntireReflectionNotOnlyThePreview() {
        assertEquals(listOf("a"), find("CHANGE THE FAVOUR"))
        assertEquals(listOf("d"), find("Final searchable sentence"))
        assertTrue(find("translation should not").isEmpty())
    }
    @Test fun surahSearchHandlesFormattingAndLongVowels() {
        listOf("Anfal", "Al-Anfal", "AL ANFĀL", "Al-Anfaal", "الأنفال", "الانفال").forEach {
            assertEquals(it, listOf("a"), find(it))
        }
        assertEquals(listOf("c", "b"), find("Hajj"))
    }
    @Test fun numericAyahSearchIsExactAndSupportsArabicDigits() {
        listOf("8:53", "8 53", "٨:٥٣", " 8 : 53 ").forEach { assertEquals(listOf("a"), find(it)) }
        assertTrue(find("8:5").isEmpty())
        assertEquals(listOf("b"), find("22:11"))
    }
    @Test fun searchAndSurahFilterAreCombined() {
        assertEquals(listOf("b"), find("faith", 22))
        assertTrue(find("faith", 8).isEmpty())
        assertEquals(listOf("c", "b"), find("", 22))
    }
    @Test fun allSortsUseExistingTimeOrNumericQuranIdentity() {
        assertEquals(listOf("c", "a", "b", "d"), index.select("", null, ReflectionSort.Newest).map { it.noteId })
        assertEquals(listOf("d", "b", "a", "c"), index.select("", null, ReflectionSort.Oldest).map { it.noteId })
        assertEquals(listOf("d", "a", "b", "c"), index.select("", null, ReflectionSort.QuranOrder).map { it.noteId })
    }
    @Test fun arabicDiacriticsAndMixedContentRemainSearchableAndUnchanged() {
        assertEquals(listOf("b"), find("تأمل في الايمان"))
        assertEquals(listOf("c"), find("اللَّه"))
        assertSame(b, index.select("faith", null, ReflectionSort.Newest).single())
    }
    @Test fun sameAyahHasIndependentStableIdsAndDeterministicTies() {
        val second = a.copy(noteId = "second")
        assertEquals(listOf("a", "second"), ReflectionListIndex(listOf(second, a), quranCatalog)
            .select("", null, ReflectionSort.Newest).map { it.noteId })
        assertSame(second, exactReflectionTarget("second", "8:53", mapOf("8:53" to listOf(a, second))))
        assertNull(exactReflectionTarget("missing", "8:53", mapOf("8:53" to listOf(a))))
        assertNull(exactReflectionTarget("a", "22:11", mapOf("8:53" to listOf(a))))
    }
    @Test fun rebuiltIndexReflectsAddEditDeleteAndEmptyCollections() {
        assertTrue(ReflectionListIndex(emptyList(), quranCatalog).select("", null, ReflectionSort.Newest).isEmpty())
        val edited = b.copy(reflectionBody = "Edited reminder", updatedAt = 60)
        val changed = ReflectionListIndex(listOf(edited, c, d), quranCatalog)
        assertEquals(listOf("b"), changed.select("edited", null, ReflectionSort.Newest).map { it.noteId })
        assertTrue(changed.select("8:53", null, ReflectionSort.Newest).isEmpty())
        assertEquals(listOf("b", "c", "d"), changed.select("", null, ReflectionSort.Newest).map { it.noteId })
    }
    @Test fun largeCollectionFiltersWithoutLosingStableIdentity() {
        val many = (1..5000).map { a.copy(noteId = "fixture-$it", ayahNumber = it % 75 + 1, verseKey = "8:${it % 75 + 1}") }
        val results = ReflectionListIndex(many, quranCatalog).select("8:53", 8, ReflectionSort.QuranOrder)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.verseKey == "8:53" })
        assertEquals(results.size, results.map { it.noteId }.toSet().size)
    }

    @Test fun quranGroupsUseNumericIdentityAndPreserveStableRecords() {
        val groups = groupReflections(index.select("", null, ReflectionSort.QuranOrder))
        assertEquals(listOf(2, 8, 22), groups.keys.toList())
        assertEquals(listOf(11, 12), groups.getValue(22).map { it.ayahNumber })
        assertSame(b, groups.getValue(22).first())
    }

    @Test fun dateGroupsFollowFirstOccurrenceAndSortWithinEachSurah() {
        val newest = groupReflections(index.select("", null, ReflectionSort.Newest))
        assertEquals(listOf(22, 8, 2), newest.keys.toList())
        assertEquals(listOf("c", "b"), newest.getValue(22).map { it.noteId })
        val oldest = groupReflections(index.select("", null, ReflectionSort.Oldest))
        assertEquals(listOf(2, 22, 8), oldest.keys.toList())
        assertEquals(listOf("b", "c"), oldest.getValue(22).map { it.noteId })
    }

    @Test fun filterSortAndClearFilterKeepConsistentGroupsAndSummaries() {
        val hajj = quranCatalog.first { it.num == 22 }
        val filtered = index.select("", 22, ReflectionSort.Newest)
        assertEquals(listOf(22), groupReflections(filtered).keys.toList())
        assertEquals("2 reflections in Al-Hajj", reflectionSummary(filtered, hajj))
        val cleared = index.select("", null, ReflectionSort.QuranOrder)
        assertEquals("4 reflections · 3 Surahs", reflectionSummary(cleared, null))
        assertEquals("1 reflection · 1 Surah", reflectionSummary(index.select("faith", null, ReflectionSort.Oldest), null))
        assertEquals("0 reflections · 0 Surahs", reflectionSummary(emptyList(), null))
        assertEquals("0 reflections in Al-Hajj", reflectionSummary(emptyList(), hajj))
    }

    @Test fun surahSheetUsesCompleteCatalogIncludingSurahsWithoutReflections() {
        assertSame(quranCatalog, searchReflectionSurahs("", quranCatalog))
        assertEquals(114, searchReflectionSurahs(" ", quranCatalog).size)
        assertEquals(listOf(114), searchReflectionSurahs("114", quranCatalog).map { it.num })
        assertEquals(listOf(2), searchReflectionSurahs("٢", quranCatalog).map { it.num })
        assertTrue(index.select("", 114, ReflectionSort.QuranOrder).isEmpty())
    }

    @Test fun surahSheetSearchAcceptsTransliterationArabicAndNumberWithoutRewritingNames() {
        listOf("Baqara", "AL BAQARA", "البقرة", "البَقَرَةِ", "2").forEach { query ->
            val result = searchReflectionSurahs(query, quranCatalog).single()
            assertEquals(2, result.num)
            assertSame(quranCatalog[1], result)
        }
        assertEquals(listOf(8), searchReflectionSurahs("Anfal", quranCatalog).map { it.num })
        assertTrue(searchReflectionSurahs("not a surah", quranCatalog).isEmpty())
        assertTrue(searchReflectionSurahs("115", quranCatalog).isEmpty())
    }

    @Test fun emptyCopyDistinguishesCollectionSearchAndSelectedSurah() {
        val hajj = quranCatalog.first { it.num == 22 }
        assertEquals("No reflections yet", reflectionEmptyTitle(false, "", null))
        assertEquals("No reflections found", reflectionEmptyTitle(true, "unmatched", hajj))
        assertEquals("No reflections in Al-Hajj", reflectionEmptyTitle(true, "", hajj))
    }

    @Test fun legacyZeroTimestampsAndSameTimeTiesKeepExistingIdFallback() {
        val legacy = ReflectionListIndex(listOf(a.copy(noteId = "z", updatedAt = 0), a.copy(noteId = "a", updatedAt = 0)), quranCatalog)
        ReflectionSort.entries.forEach { order ->
            assertEquals(listOf("a", "z"), legacy.select("", null, order).map { it.noteId })
        }
    }
}
