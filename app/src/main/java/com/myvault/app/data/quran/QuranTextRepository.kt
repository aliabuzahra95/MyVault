package com.myvault.app.data.quran

import android.content.Context
import android.util.LruCache
import androidx.core.text.HtmlCompat
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
    private val surahCache = LruCache<Int, List<QuranAyah>>(8)
    private val jsonMutex = Mutex()
    private val surahMutex = Mutex()
    private var arabicSource: JSONObject? = null
    private var translationByVerse: Map<String, String>? = null
    private var tajweedByVerse: Map<String, List<TajweedAnnotation>>? = null
    private var tafsirByVerse: Map<String, String>? = null
    private var tafsirSources: List<TafsirSourceUiModel>? = null
    private val remoteTafsirCache = LruCache<String, String>(48)

    suspend fun getSurahAyahs(surahNumber: Int): List<QuranAyah> {
        surahCache.get(surahNumber)?.let { return it }
        return surahMutex.withLock {
            surahCache.get(surahNumber) ?: loadSurahAyahs(surahNumber).also { surahCache.put(surahNumber, it) }
        }
    }

    suspend fun getAvailableTafsirSources(): List<TafsirSourceUiModel> = withContext(Dispatchers.IO) {
        tafsirSources ?: fetchAvailableTafsirSources().ifEmpty {
            listOf(TafsirSourceUiModel(MUKHTASAR_TAFSIR_ID, "Mukhtasar"))
        }.also { tafsirSources = it }
    }

    suspend fun getTafsir(verseKey: String, tafsirId: Int = MUKHTASAR_TAFSIR_ID): String {
        return if (tafsirId == MUKHTASAR_TAFSIR_ID) {
            loadTafsirSource()[verseKey].orEmpty()
        } else {
            val cacheKey = tafsirCacheKey(verseKey, tafsirId)
            remoteTafsirCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
                fetchRemoteTafsir(verseKey, tafsirId).also { remoteTafsirCache.put(cacheKey, it) }
            }
        }
    }

    private suspend fun loadSurahAyahs(surahNumber: Int): List<QuranAyah> = withContext(Dispatchers.IO) {
        val source = loadArabicSource()
        val translationSource = loadTranslationSource()
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
                        translation = translationSource[verseKey].orEmpty(),
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

    private suspend fun loadTranslationSource(): Map<String, String> {
        translationByVerse?.let { return it }
        return jsonMutex.withLock {
            translationByVerse ?: withContext(Dispatchers.IO) {
                val source = JSONObject(
                    context.assets.open("Sahih_international.json").bufferedReader().use { it.readText() },
                )
                buildMap {
                    val keys = source.keys()
                    while (keys.hasNext()) {
                        val verseKey = keys.next()
                        val translation = source.optJSONObject(verseKey)?.optString("t").orEmpty().trim()
                        if (translation.isNotBlank()) put(verseKey, translation)
                    }
                }
            }.also { translationByVerse = it }
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

    private fun fetchAvailableTafsirSources(): List<TafsirSourceUiModel> {
        val json = getJson("$workerBaseUrl/proxy/content/api/v4/resources/tafsirs?language=en")
        val tafsirs = json.optJSONArray("tafsirs") ?: return emptyList()
        val remoteSources = desiredTafsirs.mapNotNull { desired ->
            val matchingEntry = buildList {
                for (index in 0 until tafsirs.length()) {
                    val item = tafsirs.optJSONObject(index) ?: continue
                    val searchable = listOf(
                        item.optString("name"),
                        item.optJSONObject("translated_name")?.optString("name").orEmpty(),
                        item.optString("language_name"),
                    ).joinToString(" ")
                    if (desired.matcher.containsMatchIn(searchable)) add(item)
                }
            }.sortedWith(
                compareByDescending<JSONObject> {
                    desired.preferredLanguage?.equals(it.optString("language_name"), ignoreCase = true) == true
                }.thenBy { it.optInt("id") },
            ).firstOrNull() ?: return@mapNotNull null

            TafsirSourceUiModel(
                id = matchingEntry.optInt("id"),
                name = desired.displayName,
            )
        }
        return remoteSources + TafsirSourceUiModel(MUKHTASAR_TAFSIR_ID, "Mukhtasar")
    }

    private fun fetchRemoteTafsir(verseKey: String, tafsirId: Int): String {
        val json = getJson(
            "$workerBaseUrl/proxy/content/api/v4/verses/by_key/$verseKey?language=en&tafsirs=$tafsirId",
        )
        val tafsirArray = json.optJSONObject("verse")
            ?.optJSONArray("tafsirs")
            ?: return ""
        for (index in 0 until tafsirArray.length()) {
            val item = tafsirArray.optJSONObject(index) ?: continue
            if (item.optInt("resource_id") == tafsirId) {
                return normalizeTafsir(item.optString("text"))
            }
        }
        return ""
    }

    private fun getJson(url: String): JSONObject {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) return JSONObject()
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeTafsir(rawHtml: String): String {
        val preparedHtml = rawHtml
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "</p>\n")
            .replace(Regex("(?i)</div>"), "</div>\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "• ")
            .replace(Regex("(?i)</li>"), "\n")
        return HtmlCompat.fromHtml(preparedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\u00A0", " ")
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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

private const val workerBaseUrl = "https://quran-proxy.aliabuhassan1995-054.workers.dev"

private val desiredTafsirs = listOf(
    DesiredTafsir(
        displayName = "Ibn Kathir",
        matcher = Regex("ibn kathir", RegexOption.IGNORE_CASE),
        preferredLanguage = "english",
    ),
    DesiredTafsir(
        displayName = "Al-Tabari",
        matcher = Regex("tabari", RegexOption.IGNORE_CASE),
        preferredLanguage = "arabic",
    ),
    DesiredTafsir(
        displayName = "Al-Qurtubi",
        matcher = Regex("qurtubi", RegexOption.IGNORE_CASE),
        preferredLanguage = "arabic",
    ),
)

private data class DesiredTafsir(
    val displayName: String,
    val matcher: Regex,
    val preferredLanguage: String? = null,
)
