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
}
