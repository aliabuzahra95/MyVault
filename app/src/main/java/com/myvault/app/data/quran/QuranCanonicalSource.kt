package com.myvault.app.data.quran

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private val arabicVerseNumberDigits = setOf(
    '٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩',
)

data class CanonicalArabicAyah(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabicText: String,
)

/** One process-wide read of the same bundled corpus used by the in-app reader. */
object QuranCanonicalSource {
    @Volatile
    private var source: JSONObject? = null
    private val surahCache = ConcurrentHashMap<Int, List<CanonicalArabicAyah>>()

    fun json(context: Context): JSONObject = source ?: synchronized(this) {
        source ?: JSONObject(
            context.applicationContext.assets.open("qpc_hafs.json")
                .bufferedReader()
                .use { it.readText() },
        ).also { source = it }
    }

    fun surah(context: Context, surahNumber: Int): List<CanonicalArabicAyah> =
        surahCache[surahNumber] ?: buildSurah(json(context), surahNumber).also { ayahs ->
            surahCache.putIfAbsent(surahNumber, ayahs)
        }

    internal fun buildSurah(source: JSONObject, surahNumber: Int): List<CanonicalArabicAyah> =
        source.keys().asSequence()
            .filter { it.substringBefore(':').toIntOrNull() == surahNumber }
            .mapNotNull { verseKey ->
                source.optJSONObject(verseKey)?.let { verse ->
                    CanonicalArabicAyah(
                        verseKey = verseKey,
                        surahNumber = verse.optInt("surah"),
                        ayahNumber = verse.optInt("ayah"),
                        arabicText = stripTrailingVerseNumber(verse.optString("text").orEmpty()),
                    )
                }
            }
            .sortedBy { it.ayahNumber }
            .toList()
}

internal fun stripTrailingVerseNumber(text: String): String {
    var index = text.length - 1
    while (index >= 0 && (text[index].isWhitespace() || text[index] in arabicVerseNumberDigits)) {
        index--
    }
    return text.substring(0, index + 1).trimEnd()
}
