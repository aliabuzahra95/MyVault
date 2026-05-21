package com.myvault.app.data.quran

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranTextRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val surahCache = mutableMapOf<Int, List<QuranAyah>>()
    private val jsonMutex = Mutex()
    private val surahMutex = Mutex()
    private var arabicSource: JSONObject? = null

    suspend fun getSurahAyahs(surahNumber: Int): List<QuranAyah> {
        surahCache[surahNumber]?.let { return it }
        return surahMutex.withLock {
            surahCache[surahNumber] ?: loadSurahAyahs(surahNumber).also { surahCache[surahNumber] = it }
        }
    }

    private suspend fun loadSurahAyahs(surahNumber: Int): List<QuranAyah> = withContext(Dispatchers.IO) {
        val source = loadArabicSource()
        source.keys().asSequence()
            .filter { it.substringBefore(':').toIntOrNull() == surahNumber }
            .mapNotNull { verseKey ->
                source.optJSONObject(verseKey)?.let { verse ->
                    QuranAyah(
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

    private suspend fun loadArabicSource(): JSONObject {
        arabicSource?.let { return it }
        return jsonMutex.withLock {
            arabicSource ?: withContext(Dispatchers.IO) {
                JSONObject(
                    context.assets.open("qpc_hafs.json").bufferedReader().use { it.readText() },
                )
            }.also { arabicSource = it }
        }
    }
}

private fun stripTrailingVerseNumber(text: String): String {
    var index = text.length - 1
    while (index >= 0 && (text[index].isWhitespace() || text[index] in arabicDigitSet)) {
        index--
    }
    return text.substring(0, index + 1).trimEnd()
}

private val arabicDigitSet = setOf(
    '٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩',
)
