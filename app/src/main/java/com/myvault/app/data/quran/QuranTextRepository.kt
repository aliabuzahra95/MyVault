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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranTextRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val surahCache = LruCache<String, List<QuranAyah>>(16)
    private val maududiSurahCachedAt = mutableMapOf<String, Long>()
    private val jsonMutex = Mutex()
    private val surahMutex = Mutex()
    private val searchMutex = Mutex()
    private val ayahSearchIndexMutex = Mutex()
    private var ayahSearchIndex: Map<String, String>? = null
    private var sahihTranslationByVerse: Map<String, QuranTranslationContent>? = null
    private var maududiTranslationByVerse: Map<String, QuranTranslationContent>? = null
    private var tajweedByVerse: Map<String, List<TajweedAnnotation>>? = null
    private var wordMetadataSource: QuranWordMetadataSource? = null
    private var tafsirByVerse: Map<String, String>? = null
    private var tafsirSources: List<TafsirSourceUiModel>? = null
    private var localSearchIndex: List<Pair<SurahInfo, QuranAyah>>? = null
    private var corpusSearchIndex: List<QuranCorpusSearchItem>? = null
    private val corpusSearchMutex = Mutex()
    private val remoteTafsirCache = LruCache<String, String>(48)

    suspend fun searchLocalCorpus(query: String, limit: Int = 20): List<QuranSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        parseQuranSearchReference(trimmed)?.let { (surahNumber, ayahNumber) ->
            val surah = quranCatalog.firstOrNull { it.num == surahNumber }
            if (surah != null && ayahNumber in 1..surah.ayat) {
                return@withContext getSurahAyahs(surahNumber)
                    .firstOrNull { it.ayahNumber == ayahNumber }
                    ?.let { listOf(it.toSearchResult(surah)) }
                    .orEmpty()
            }
        }

        val normalizedQuery = trimmed.normalizedQuranSearchText()
        if (normalizedQuery.length < 2) return@withContext emptyList()
        val hits = mutableListOf<QuranSearchResult>()
        quranCatalog.forEach { surah ->
            if (surah.matchesSearchName(normalizedQuery)) {
                getSurahAyahs(surah.num).firstOrNull()?.let { hits += it.toSearchResult(surah) }
            }
        }
        if (hits.size >= limit) return@withContext hits.take(limit)

        val corpus = getCorpusSearchIndex()
        val isArabic = trimmed.any { it in '\u0600'..'\u06FF' }
        val normAr1 = normalizeQuranArabicText(trimmed, expandDaggerAlif = true)
        val normAr2 = normalizeQuranArabicText(trimmed, expandDaggerAlif = false)
        val normAr3 = normAr1.replace('ء', 'و').replace('ئ', 'ي')
        val normEn = trimmed.lowercase()

        for (item in corpus) {
            val matches = if (isArabic) {
                (normAr1.isNotBlank() && item.normalizedArabic.contains(normAr1)) ||
                    (normAr2.isNotBlank() && item.normalizedArabic.contains(normAr2)) ||
                    (normAr3.isNotBlank() && item.normalizedArabic.contains(normAr3))
            } else {
                (normEn.length >= 2 && item.normalizedTranslation.contains(normEn)) ||
                    (normEn.length >= 2 && item.surah.name.lowercase().contains(normEn))
            }
            if (matches) {
                if (hits.none { it.verseKey == item.verseKey }) {
                    hits += QuranSearchResult(
                        verseKey = item.verseKey,
                        surahName = item.surah.name,
                        reference = "${item.surah.num}:${item.ayahNumber}",
                        snippet = item.arabicText.take(96).ifBlank { item.translation.take(140) },
                    )
                }
                if (hits.size >= limit) return@withContext hits
            }
        }
        hits
    }

    suspend fun getCorpusSearchIndex(): List<QuranCorpusSearchItem> {
        corpusSearchIndex?.let { return it }
        return corpusSearchMutex.withLock {
            corpusSearchIndex ?: withContext(Dispatchers.Default) {
                val arabicIndex = getAyahSearchIndex()
                val translationIndex = runCatching { loadSahihTranslationSource() }.getOrDefault(emptyMap())
                val catalogByNum = quranCatalog.associateBy { it.num }
                arabicIndex.mapNotNull { (verseKey, arabicText) ->
                    val surahNum = verseKey.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                    val ayahNum = verseKey.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
                    val surah = catalogByNum[surahNum] ?: return@mapNotNull null
                    val translation = translationIndex[verseKey]?.text.orEmpty()
                    QuranCorpusSearchItem(
                        surah = surah,
                        ayahNumber = ayahNum,
                        verseKey = verseKey,
                        arabicText = arabicText,
                        normalizedArabic = arabicText.toNormalizedQuranCorpusForms(),
                        translation = translation,
                        normalizedTranslation = translation.lowercase(),
                    )
                }
            }.also { corpusSearchIndex = it }
        }
    }

    suspend fun getAyahSearchIndex(): Map<String, String> {
        ayahSearchIndex?.let { return it }
        return ayahSearchIndexMutex.withLock {
            ayahSearchIndex ?: loadArabicSource().let { source ->
                withContext(Dispatchers.Default) {
                    source.toQuranAyahSearchIndex()
                }
            }.also { ayahSearchIndex = it }
        }
    }

    suspend fun getSurahAyahs(
        surahNumber: Int,
        translationSource: QuranTranslationSource = QuranTranslationSource.SahihInternational,
    ): List<QuranAyah> {
        val cacheKey = "${translationSource.storedValue}:$surahNumber"
        surahCache.get(cacheKey)?.let { cached ->
            if (translationSource != QuranTranslationSource.Maududi || isFreshMaududiMemoryEntry(cacheKey)) {
                return cached
            }
            surahCache.remove(cacheKey)
            maududiSurahCachedAt.remove(cacheKey)
        }
        return surahMutex.withLock {
            surahCache.get(cacheKey)?.takeIf {
                translationSource != QuranTranslationSource.Maududi || isFreshMaududiMemoryEntry(cacheKey)
            } ?: loadSurahAyahs(surahNumber, translationSource).also {
                surahCache.put(cacheKey, it)
                if (translationSource == QuranTranslationSource.Maududi) {
                    maududiSurahCachedAt[cacheKey] = maududiCacheFile(surahNumber)
                        .lastModified()
                }
            }
        }
    }

    /**
     * Enriches the already-displayable bundled Maududi translation with Quran.com footnotes.
     * This is deliberately separate from [getSurahAyahs] so a network request can never delay
     * opening a surah. A fresh disk cache is reused without contacting the network.
     */
    suspend fun refreshMaududiSurahAyahs(surahNumber: Int): List<QuranAyah>? =
        withContext(Dispatchers.IO) {
            if (readMaududiCache(surahNumber) != null) return@withContext null

            val json = getJsonOrThrow(
                "$workerBaseUrl/proxy/content/api/v4/quran/translations/$MAUDUDI_TRANSLATION_RESOURCE_ID" +
                    "?chapter_number=$surahNumber&foot_notes=true&fields=verse_key",
            )
            if (json.toMaududiTranslations().isEmpty()) return@withContext null
            writeMaududiCache(surahNumber, json)

            surahMutex.withLock {
                val cacheKey = "${QuranTranslationSource.Maududi.storedValue}:$surahNumber"
                loadSurahAyahs(surahNumber, QuranTranslationSource.Maududi).also { ayahs ->
                    surahCache.put(cacheKey, ayahs)
                    maududiSurahCachedAt[cacheKey] = maududiCacheFile(surahNumber).lastModified()
                }
            }
        }

    private fun isFreshMaududiMemoryEntry(cacheKey: String): Boolean {
        val cachedAt = maududiSurahCachedAt[cacheKey] ?: return false
        return System.currentTimeMillis() - cachedAt in 0..QURAN_FOUNDATION_CACHE_MAX_AGE_MS
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
                fetchRemoteTafsir(verseKey, tafsirId).also { tafsir ->
                    // Do not turn a transient network failure or an incomplete upstream response
                    // into a process-lifetime "not available" result. A later expansion may retry.
                    if (tafsir.isNotBlank()) remoteTafsirCache.put(cacheKey, tafsir)
                }
            }
        }
    }

    private suspend fun loadSurahAyahs(
        surahNumber: Int,
        selectedTranslationSource: QuranTranslationSource,
    ): List<QuranAyah> = withContext(Dispatchers.IO) {
        val source = loadArabicSource()
        val translationSource = loadTranslationSource(selectedTranslationSource, surahNumber)
        val tajweedSource = loadTajweedSource()
        val wordMetadataSource = loadWordMetadataSource()
        source.keys().asSequence()
            .filter { it.substringBefore(':').toIntOrNull() == surahNumber }
            .mapNotNull { verseKey ->
                source.optJSONObject(verseKey)?.let { verse ->
                    val translation = translationSource[verseKey]
                    QuranAyah(
                        verseKey = verseKey,
                        surahNumber = verse.optInt("surah"),
                        ayahNumber = verse.optInt("ayah"),
                        arabicText = stripTrailingVerseNumber(verse.optString("text").orEmpty()),
                        translation = translation?.text.orEmpty(),
                        translationFootnotes = translation?.footnotes.orEmpty(),
                        tajweedAnnotations = tajweedSource[verseKey].orEmpty(),
                    ).withIndexedWords(wordMetadataSource.metadataByWordId)
                }
            }
            .sortedBy { it.ayahNumber }
            .toList()
    }

    private suspend fun loadArabicSource(): JSONObject {
        return withContext(Dispatchers.IO) { QuranCanonicalSource.json(context) }
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

    private suspend fun loadWordMetadataSource(): QuranWordMetadataSource {
        wordMetadataSource?.let { return it }
        return jsonMutex.withLock {
            wordMetadataSource ?: withContext(Dispatchers.IO) {
                readWordMetadataAsset()
            }.also { wordMetadataSource = it }
        }
    }

    private fun readWordMetadataAsset(): QuranWordMetadataSource {
        val rawJson = runCatching {
            context.assets.open(QURAN_WORD_METADATA_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            return QuranWordMetadataSource()
        }
        if (rawJson.isBlank()) return QuranWordMetadataSource()
        val trimmed = rawJson.trim()
        return runCatching {
            val records = when {
                trimmed.startsWith("[") -> JSONArray(trimmed).toWordMetadataRecords()
                else -> JSONObject(trimmed).toWordMetadataRecords()
            }
            val imlaeiByWordId = readImlaeiComparisonAsset()
            records.map { metadata ->
                imlaeiByWordId[metadata.wordId]?.let { imlaei ->
                    metadata.copy(
                        imlaeiText = imlaei.imlaeiText,
                        imlaeiSimpleText = imlaei.imlaeiSimpleText,
                    )
                } ?: metadata
            }.toWordMetadataSource()
        }.getOrElse {
            QuranWordMetadataSource()
        }
    }

    private fun readImlaeiComparisonAsset(): Map<String, QuranImlaeiComparisonWord> {
        val rawJson = runCatching {
            context.assets.open(QURAN_IMLAEI_COMPARISON_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            return emptyMap()
        }
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            val source = JSONObject(rawJson)
            val records = source.optJSONArray("records") ?: JSONArray()
            buildMap {
                for (index in 0 until records.length()) {
                    val item = records.optJSONObject(index) ?: continue
                    val wordId = item.optString("wordId").trim()
                    if (wordId.isBlank()) continue
                    val imlaeiText = item.optString("imlaeiText").trim()
                    val imlaeiSimpleText = item.optString("imlaeiSimpleText").trim()
                    if (imlaeiText.isBlank() && imlaeiSimpleText.isBlank()) continue
                    put(
                        wordId,
                        QuranImlaeiComparisonWord(
                            wordId = wordId,
                            imlaeiText = imlaeiText,
                            imlaeiSimpleText = imlaeiSimpleText,
                        ),
                    )
                }
            }
        }.getOrElse {
            emptyMap()
        }
    }

    private suspend fun loadTranslationSource(
        source: QuranTranslationSource,
        surahNumber: Int,
    ): Map<String, QuranTranslationContent> = when (source) {
        QuranTranslationSource.SahihInternational -> loadSahihTranslationSource()
        QuranTranslationSource.Maududi -> loadMaududiTranslationSource(surahNumber)
    }

    private suspend fun loadSahihTranslationSource(): Map<String, QuranTranslationContent> {
        sahihTranslationByVerse?.let { return it }
        return jsonMutex.withLock {
            sahihTranslationByVerse ?: withContext(Dispatchers.IO) {
                val source = JSONObject(
                    context.assets.open("Sahih_international.json").bufferedReader().use { it.readText() },
                )
                buildMap {
                    val keys = source.keys()
                    while (keys.hasNext()) {
                        val verseKey = keys.next()
                        val translation = source.optJSONObject(verseKey)?.optString("t").orEmpty().trim()
                        if (translation.isNotBlank()) {
                            put(verseKey, QuranTranslationContent(text = translation))
                        }
                    }
                }
            }.also { sahihTranslationByVerse = it }
        }
    }

    private suspend fun loadMaududiTranslationSource(
        surahNumber: Int,
    ): Map<String, QuranTranslationContent> {
        val bundledTranslations = loadBundledMaududiTranslationSource()
        val cached = readMaududiCache(surahNumber)
        if (cached != null) {
            cached.toMaududiTranslations().takeIf { it.isNotEmpty() }?.let {
                return bundledTranslations + it
            }
            maududiCacheFile(surahNumber).delete()
        }
        return bundledTranslations
    }

    private suspend fun loadBundledMaududiTranslationSource(): Map<String, QuranTranslationContent> {
        maududiTranslationByVerse?.let { return it }
        return jsonMutex.withLock {
            maududiTranslationByVerse ?: withContext(Dispatchers.IO) {
                val translations = context.assets.open(MAUDUDI_TRANSLATION_ASSET)
                    .bufferedReader()
                    .use { reader -> parseMaududiTranslationAsset(reader.readText()) }
                    .mapValues { (_, text) -> QuranTranslationContent(text = text) }
                if (translations.size != EXPECTED_QURAN_VERSE_COUNT) {
                    throw IOException(
                        "Bundled Maududi translation contains ${translations.size} verses; " +
                            "$EXPECTED_QURAN_VERSE_COUNT were expected",
                    )
                }
                translations
            }.also { maududiTranslationByVerse = it }
        }
    }

    private fun readMaududiCache(surahNumber: Int): JSONObject? {
        val file = maududiCacheFile(surahNumber)
        if (!file.exists()) return null
        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs !in 0..QURAN_FOUNDATION_CACHE_MAX_AGE_MS) {
            file.delete()
            return null
        }
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrElse {
                file.delete()
                null
            }
    }

    private fun writeMaududiCache(surahNumber: Int, json: JSONObject) {
        runCatching {
            val file = maududiCacheFile(surahNumber)
            file.parentFile?.mkdirs()
            file.writeText(json.toString(), Charsets.UTF_8)
        }
    }

    private fun maududiCacheFile(surahNumber: Int) =
        context.cacheDir.resolve("quran-translations/maududi-$surahNumber.json")

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
        return normalizeTafsir(json.selectRemoteTafsirHtml(tafsirId))
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

    private fun getJsonOrThrow(url: String): JSONObject {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("Qur'an translation request failed with HTTP $responseCode")
            }
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

data class QuranSearchResult(
    val verseKey: String,
    val surahName: String,
    val reference: String,
    val snippet: String,
)

internal fun parseQuranSearchReference(query: String): Pair<Int, Int>? {
    val normalized = query.trim().lowercase()
    Regex("^(\\d{1,3})\\s*[: ]\\s*(\\d{1,3})$").matchEntire(normalized)?.let {
        return it.groupValues[1].toInt() to it.groupValues[2].toInt()
    }
    Regex("^surah\\s+(\\d{1,3})\\s+(?:verse|ayah)\\s+(\\d{1,3})$").matchEntire(normalized)?.let {
        return it.groupValues[1].toInt() to it.groupValues[2].toInt()
    }
    val named = Regex("^(.+?)\\s+(\\d{1,3})$").matchEntire(query.trim()) ?: return null
    val requestedName = named.groupValues[1].normalizedQuranSearchText()
    val surah = quranCatalog.firstOrNull { it.matchesSearchName(requestedName) } ?: return null
    return surah.num to named.groupValues[2].toInt()
}

private fun SurahInfo.matchesSearchName(normalizedQuery: String): Boolean {
    val names = listOf(name, arabic).map(String::normalizedQuranSearchText)
    return names.any { candidate ->
        candidate == normalizedQuery ||
            candidate.removeSuffix("h") == normalizedQuery.removeSuffix("h") ||
            candidate.contains(normalizedQuery)
    }
}

internal fun normalizeQuranArabicText(value: String, expandDaggerAlif: Boolean): String {
    var text = value.replace("\u0640", "")
    text = text.replace("و\u0670ة", "اة")
        .replace("و\u0670ت", "ات")
        .replace("وة", "اة")
        .replace("وت", "ات")
    text = if (expandDaggerAlif) {
        text.replace('\u0670', 'ا')
    } else {
        text.replace("\u0670", "")
    }
    text = text.replace(Regex("[\\u064B-\\u065F\\u06D6-\\u06ED\\u0610-\\u061A]"), "")
    text = text.replace('ٱ', 'ا')
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
    text = text.replace("ءا", "ا")
    text = text.replace('ؤ', 'ء')
        .replace('ئ', 'ء')
    text = text.replace('ى', 'ي')
    text = text.replace('ة', 'ه')
    text = text.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    text = text.replace(Regex("[\\d٠-٩]+"), " ")
    return text.replace(Regex("\\s+"), " ").trim()
}

internal fun String.toNormalizedQuranCorpusForms(): String {
    val n1 = normalizeQuranArabicText(this, expandDaggerAlif = true)
    val n2 = normalizeQuranArabicText(this, expandDaggerAlif = false)
    val n1Fixed = n1
        .replace("الرحمان", "الرحمن")
        .replace("هاذا", "هذا")
        .replace("ذالك", "ذلك")
        .replace("السموات", "السماوات")
        .replace("الاه", "اله")
    val n3 = n1.replace('ء', 'و').replace('ئ', 'ي')
    return "$n1 $n1Fixed $n2 $n3"
}

private fun String.normalizedQuranSearchText(): String =
    normalizeQuranArabicText(this, expandDaggerAlif = true)

private fun QuranAyah.toSearchResult(surah: SurahInfo): QuranSearchResult = QuranSearchResult(
    verseKey = verseKey,
    surahName = surah.name,
    reference = "$surahNumber:$ayahNumber",
    snippet = arabicText.take(96).ifBlank { translation.take(140) },
)

private data class QuranTranslationContent(
    val text: String,
    val footnotes: List<QuranTranslationFootnote> = emptyList(),
)

/**
 * Grouped tafsir responses can contain more than one row for the same resource, including an
 * empty placeholder before the populated commentary. Prefer the most complete non-empty row.
 */
internal fun JSONObject.selectRemoteTafsirHtml(tafsirId: Int): String {
    val tafsirs = optJSONObject("verse")?.optJSONArray("tafsirs") ?: return ""
    return buildList {
        for (index in 0 until tafsirs.length()) {
            val item = tafsirs.optJSONObject(index) ?: continue
            if (item.optInt("resource_id") != tafsirId) continue
            item.optString("text").trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }.maxByOrNull(String::length).orEmpty()
}

internal fun parseMaududiTranslationAsset(rawText: String): Map<String, String> = buildMap {
    rawText.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith('#')) return@forEach
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) return@forEach
        val surahNumber = parts[0].toIntOrNull() ?: return@forEach
        val ayahNumber = parts[1].toIntOrNull() ?: return@forEach
        val translation = parts[2].trim()
        if (surahNumber !in 1..114 || ayahNumber <= 0 || translation.isBlank()) return@forEach
        put("$surahNumber:$ayahNumber", translation)
    }
}

private fun JSONObject.toMaududiTranslations(): Map<String, QuranTranslationContent> {
    val translations = optJSONArray("translations") ?: return emptyMap()
    return buildMap {
        for (index in 0 until translations.length()) {
            val item = translations.optJSONObject(index) ?: continue
            val verseKey = item.optString("verse_key").trim()
            val rawTranslation = item.optString("text")
            if (verseKey.isBlank() || rawTranslation.isBlank()) continue
            put(
                verseKey,
                rawTranslation.toTranslationContent(item.optJSONObject("foot_notes")),
            )
        }
    }
}

private fun String.toTranslationContent(footnotesJson: JSONObject?): QuranTranslationContent {
    val matches = translationFootnoteRegex.findAll(this).toList()
    if (matches.isEmpty()) {
        return QuranTranslationContent(text = htmlToReaderText(this))
    }

    val templatedHtml = buildString {
        var sourceIndex = 0
        matches.forEachIndexed { index, match ->
            append(this@toTranslationContent.substring(sourceIndex, match.range.first))
            append(translationFootnoteToken(index))
            sourceIndex = match.range.last + 1
        }
        append(this@toTranslationContent.substring(sourceIndex))
    }
    val templatedText = htmlToReaderText(templatedHtml)
    val parsedFootnotes = mutableListOf<QuranTranslationFootnote>()
    val finalText = buildString {
        var sourceIndex = 0
        matches.forEachIndexed { index, match ->
            val token = translationFootnoteToken(index)
            val tokenIndex = templatedText.indexOf(token, startIndex = sourceIndex)
            if (tokenIndex < 0) return@forEachIndexed
            append(templatedText.substring(sourceIndex, tokenIndex))
            val footnoteId = match.groupValues[1]
            val label = htmlToReaderText(match.groupValues[2]).ifBlank { (index + 1).toString() }
            val markerStart = length
            append(label)
            parsedFootnotes += QuranTranslationFootnote(
                id = footnoteId,
                label = label,
                text = htmlToReaderText(footnotesJson?.optString(footnoteId).orEmpty()),
                markerStart = markerStart,
                markerEndExclusive = length,
            )
            sourceIndex = tokenIndex + token.length
        }
        append(templatedText.substring(sourceIndex))
    }
    return QuranTranslationContent(
        text = finalText,
        footnotes = parsedFootnotes.filter { it.text.isNotBlank() },
    )
}

private fun htmlToReaderText(rawHtml: String): String {
    if (rawHtml.isBlank()) return ""
    val preparedHtml = rawHtml
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "</p>\n")
        .replace(Regex("(?i)</div>"), "</div>\n")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("(?i)</li>"), "\n")
    return HtmlCompat.fromHtml(preparedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace("\u00A0", " ")
        .replace(Regex("[ \\t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun translationFootnoteToken(index: Int): String = "MYVAULTFOOTNOTE${index}TOKEN"

private val translationFootnoteRegex =
    Regex("(?is)<sup\\b[^>]*foot_note\\s*=\\s*[\\\"']?(\\d+)[\\\"']?[^>]*>(.*?)</sup>")

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

internal fun JSONObject.toQuranAyahSearchIndex(): Map<String, String> =
    buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val verseKey = keys.next()
            val text = optJSONObject(verseKey)
                ?.optString("text")
                .orEmpty()
                .let(::stripTrailingVerseNumber)
                .trim()
            if (text.isNotBlank()) put(verseKey, text)
        }
    }

private fun QuranAyah.withIndexedWords(metadataByWordId: Map<String, QuranWordMetadata>): QuranAyah = copy(
    words = buildQuranWords(
        surahNumber = surahNumber,
        ayahNumber = ayahNumber,
        arabicText = arabicText,
        metadataByWordId = metadataByWordId,
    ),
)

private fun buildQuranWords(
    surahNumber: Int,
    ayahNumber: Int,
    arabicText: String,
    metadataByWordId: Map<String, QuranWordMetadata>,
): List<QuranWord> {
    if (arabicText.isBlank()) return emptyList()
    val matches = Regex("\\S+").findAll(arabicText).toList()
    var wordPosition = 0
    return matches.mapNotNull { match ->
        val wordText = match.value
        val normalized = normalizeArabicWord(wordText)
        if (normalized.isBlank()) return@mapNotNull null
        wordPosition += 1
        val wordId = "$surahNumber:$ayahNumber:$wordPosition"
        val baseWord = QuranWord(
            wordId = wordId,
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            wordPosition = wordPosition,
            arabicText = wordText,
            normalizedArabicText = normalized,
            charStart = match.range.first,
            charEnd = match.range.last + 1,
        )
        val metadata = metadataByWordId[wordId]?.takeIf { it.alignsWith(baseWord) }
        QuranWord(
            wordId = wordId,
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            wordPosition = wordPosition,
            arabicText = wordText,
            normalizedArabicText = normalized,
            metadata = metadata,
            charStart = match.range.first,
            charEnd = match.range.last + 1,
        )
    }
}

private fun normalizeArabicWord(word: String): String =
    word
        .replace("\u0640", "")
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        .replace(Regex("[ۚۖۗۘۙۛۜ۝۞,.;:!?؟،؛ـ\\-()\\[\\]{}]"), "")
        .replace('ٱ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .trim()

private fun QuranWordMetadata.alignsWith(word: QuranWord): Boolean =
    wordId == word.wordId &&
        arabicText.isNotBlank() &&
        (
            arabicText == word.arabicText ||
                normalizedArabicText.isNotBlank() && normalizedArabicText == word.normalizedArabicText
            )

private fun JSONArray.toWordMetadataRecords(): List<QuranWordMetadata> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.toWordMetadata()?.let(::add)
    }
}

private fun JSONObject.toWordMetadataRecords(): List<QuranWordMetadata> {
    optJSONArray("records")?.let { return it.toWordMetadataRecords() }
    return buildList {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            optJSONObject(key)?.toWordMetadata(defaultWordId = key)?.let(::add)
        }
    }
}

private fun JSONObject.toWordMetadata(defaultWordId: String? = null): QuranWordMetadata? {
    val wordId = optString("wordId")
        .ifBlank { optString("word_id") }
        .ifBlank { optString("id") }
        .ifBlank { defaultWordId.orEmpty() }
        .trim()
    if (wordId.isBlank()) return null
    val arabicText = optString("arabicText")
        .ifBlank { optString("arabic") }
        .ifBlank { optString("uthmani") }
        .ifBlank { optString("word") }
        .trim()
    if (arabicText.isBlank()) return null
    val normalizedArabicText = optString("normalizedArabicText")
        .ifBlank { optString("normalized_arabic") }
        .ifBlank { normalizeArabicWord(arabicText) }
    return QuranWordMetadata(
        wordId = wordId,
        arabicText = arabicText,
        normalizedArabicText = normalizedArabicText,
        imlaeiText = optCleanString("imlaeiText") ?: optCleanString("text_imlaei"),
        imlaeiSimpleText = optCleanString("imlaeiSimpleText") ?: optCleanString("text_imlaei_simple"),
        translation = optCleanString("translation"),
        transliteration = optCleanString("transliteration"),
        root = optCleanString("root"),
        lemma = optCleanString("lemma"),
        definition = optCleanString("definition") ?: optCleanString("meaning"),
        source = optCleanString("source"),
    )
}

private fun JSONObject.optCleanString(name: String): String? =
    optString(name).trim().takeIf { it.isNotBlank() }

private fun List<QuranWordMetadata>.toWordMetadataSource(): QuranWordMetadataSource {
    val grouped = groupBy { it.wordId }
    val duplicateWordIds = grouped.filterValues { it.size > 1 }.keys.sorted()
    return QuranWordMetadataSource(
        metadataByWordId = grouped
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() },
        duplicateWordIds = duplicateWordIds,
    )
}

private data class QuranWordMetadataSource(
    val metadataByWordId: Map<String, QuranWordMetadata> = emptyMap(),
    val duplicateWordIds: List<String> = emptyList(),
)

private data class QuranImlaeiComparisonWord(
    val wordId: String,
    val imlaeiText: String,
    val imlaeiSimpleText: String,
)

private const val QURAN_WORD_METADATA_ASSET = "quran_word_metadata.json"
private const val QURAN_IMLAEI_COMPARISON_ASSET = "quran_imlaei_comparison_corpus.json"
private const val MAUDUDI_TRANSLATION_ASSET = "Maududi_en_tanzil.txt"
private const val MAUDUDI_TRANSLATION_RESOURCE_ID = 95
private const val EXPECTED_QURAN_VERSE_COUNT = 6_236
private const val QURAN_FOUNDATION_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L

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
