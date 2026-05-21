package com.myvault.app.data.quran

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private var tajweedByVerse: Map<String, List<TajweedAnnotation>>? = null
    private var tafsirByVerse: Map<String, String>? = null

    suspend fun getSurahAyahs(surahNumber: Int): List<QuranAyah> {
        surahCache[surahNumber]?.let { return it }
        return surahMutex.withLock {
            surahCache[surahNumber] ?: loadSurahAyahs(surahNumber).also { surahCache[surahNumber] = it }
        }
    }

    suspend fun getTafsir(verseKey: String): String {
        return loadTafsirSource()[verseKey].orEmpty()
    }

    private suspend fun loadSurahAyahs(surahNumber: Int): List<QuranAyah> = withContext(Dispatchers.IO) {
        val source = loadArabicSource()
        val tajweedSource = loadTajweedSource()
        source.keys().asSequence()
            .filter { it.substringBefore(':').toIntOrNull() == surahNumber }
            .mapNotNull { verseKey ->
                source.optJSONObject(verseKey)?.let { verse ->
                    QuranAyah(
                        verseKey = verseKey,
                        surahNumber = verse.optInt("surah"),
                        ayahNumber = verse.optInt("ayah"),
                        arabicText = stripTrailingVerseNumber(verse.optString("text").orEmpty()),
                        tajweedAnnotations = tajweedSource[verseKey].orEmpty(),
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

    private suspend fun loadTajweedSource(): Map<String, List<TajweedAnnotation>> {
        tajweedByVerse?.let { return it }
        return jsonMutex.withLock {
            tajweedByVerse ?: withContext(Dispatchers.IO) {
                val array = JSONArray(
                    context.assets.open("Tajweed.json").bufferedReader().use { it.readText() },
                )
                buildMap {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val surah = item.optInt("surah")
                        val ayah = item.optInt("ayah")
                        if (surah <= 0 || ayah <= 0) continue
                        val verseKey = "$surah:$ayah"
                        val annotations = item.optJSONArray("annotations").toTajweedAnnotations()
                        put(verseKey, annotations)
                    }
                }
            }.also { tajweedByVerse = it }
        }
    }

    private suspend fun loadTafsirSource(): Map<String, String> {
        tafsirByVerse?.let { return it }
        return jsonMutex.withLock {
            tafsirByVerse ?: withContext(Dispatchers.IO) {
                val source = JSONObject(
                    context.assets.open("abridged_tafsir.json").bufferedReader().use { it.readText() },
                )
                buildMap {
                    val keys = source.keys()
                    while (keys.hasNext()) {
                        val verseKey = keys.next()
                        val tafsir = source.optJSONObject(verseKey)?.optString("text").orEmpty().trim()
                        if (tafsir.isNotBlank()) {
                            put(verseKey, tafsir)
                        }
                    }
                }
            }.also { tafsirByVerse = it }
        }
    }
}

private fun JSONArray?.toTajweedAnnotations(): List<TajweedAnnotation> = buildList {
    val array = this@toTajweedAnnotations ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            TajweedAnnotation(
                start = item.optInt("start"),
                end = item.optInt("end"),
                rule = item.optString("rule"),
            ),
        )
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
