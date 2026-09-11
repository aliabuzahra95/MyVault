package com.myvault.app.ui.quran

import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.SurahInfo
import java.text.Normalizer
import java.util.Locale

internal enum class ReflectionSort(val label: String) {
    Newest("Newest first"), Oldest("Oldest first"), QuranOrder("Qur’an order")
}

/** Presentation-only index; records and canonical names are never rewritten. */
internal class ReflectionListIndex(reflections: List<QuranReflectionItem>, catalog: List<SurahInfo>) {
    private val entries = reflections.map { reflection ->
        val surah = catalog.firstOrNull { it.num == reflection.surahNumber }
        Entry(reflection, normalize("${reflection.reflectionBody} ${reflection.surahName} ${surah?.name.orEmpty()} ${surah?.arabic.orEmpty()}"))
    }

    fun select(query: String, surah: Int?, sort: ReflectionSort): List<QuranReflectionItem> {
        val normalized = normalize(query)
        val reference = Reference.matchEntire(normalized)
        val tokens = normalized.split(' ').filter(String::isNotEmpty)
        val selected = entries.asSequence().filter { entry ->
            (surah == null || entry.item.surahNumber == surah) &&
                if (reference != null) {
                    entry.item.surahNumber == reference.groupValues[1].toIntOrNull() &&
                        entry.item.ayahNumber == reference.groupValues[2].toIntOrNull()
                } else {
                    tokens.all { entry.text.contains(it) }
                }
        }.map { it.item }
        val comparator = when (sort) {
            ReflectionSort.Newest -> compareByDescending<QuranReflectionItem> { it.updatedAt }
            ReflectionSort.Oldest -> compareBy<QuranReflectionItem> { it.updatedAt }
            ReflectionSort.QuranOrder -> compareBy<QuranReflectionItem> { it.surahNumber }.thenBy { it.ayahNumber }
        }.thenBy { it.noteId }
        return selected.sortedWith(comparator).toList()
    }

    private data class Entry(val item: QuranReflectionItem, val text: String)

    companion object {
        val Marks = Regex("[\\p{M}ـ]")
        val Separators = Regex("[^\\p{L}\\p{N}]+")
        val LongVowels = Regex("([aeiou])\\1+")
        val Reference = Regex("(\\d{1,3}) (\\d{1,3})")
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT).replace(Marks, "")
            .replace('ٱ', 'ا').replace('ى', 'ي')
            .map { c -> if (c.isDigit()) Character.digit(c, 10).digitToChar() else c }.joinToString("")
            .replace(LongVowels, "$1").replace(Separators, " ").trim()
    }
}

/** Group the existing sorted results in first-occurrence order, as in the approved mockup.
 * Date sorting orders groups by their newest/oldest member and preserves that order within groups.
 * The original records, timestamps, IDs and select comparator are not changed.
 */
internal fun groupReflections(results: List<QuranReflectionItem>): Map<Int, List<QuranReflectionItem>> =
    results.groupBy { it.surahNumber }

internal fun reflectionSummary(results: List<QuranReflectionItem>, selectedSurah: SurahInfo?): String {
    val count = results.size
    val noun = if (count == 1) "reflection" else "reflections"
    return if (selectedSurah != null) "$count $noun in ${selectedSurah.name}" else {
        val surahs = results.map { it.surahNumber }.distinct().size
        "$count $noun · $surahs ${if (surahs == 1) "Surah" else "Surahs"}"
    }
}

internal fun searchReflectionSurahs(query: String, catalog: List<SurahInfo>): List<SurahInfo> {
    val normalized = ReflectionListIndex.normalize(query)
    if (normalized.isEmpty()) return catalog
    normalized.toIntOrNull()?.let { number -> return catalog.filter { it.num == number } }
    val tokens = normalized.split(' ').filter(String::isNotEmpty)
    return catalog.filter { surah ->
        val names = ReflectionListIndex.normalize("${surah.name} ${surah.arabic}")
        tokens.all(names::contains)
    }
}

internal fun reflectionEmptyTitle(hasReflections: Boolean, query: String, selectedSurah: SurahInfo?): String = when {
    !hasReflections -> "No reflections yet"
    query.isBlank() && selectedSurah != null -> "No reflections in ${selectedSurah.name}"
    else -> "No reflections found"
}

internal fun exactReflectionTarget(
    noteId: String?, verseKey: String?, reflections: Map<String, List<QuranReflectionItem>>,
): QuranReflectionItem? = verseKey?.let { key ->
    reflections[key]?.firstOrNull { it.noteId == noteId && it.verseKey == key }
}
