package com.myvault.app.data.quran.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranAudioRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _surahDownloadStates = MutableStateFlow<Map<String, SurahDownloadState>>(emptyMap())
    val surahDownloadStates: StateFlow<Map<String, SurahDownloadState>> = _surahDownloadStates.asStateFlow()

    suspend fun getSupportedReciters(): List<AudioReciterUiModel> = withContext(Dispatchers.IO) {
        recitersMutex.withLock {
            cachedReciters ?: QuranTimedRecitations.includeRequested(
                runCatching { fetchSupportedReciters() }.getOrDefault(emptyList()).ifEmpty { fallbackReciters },
            ).also { cachedReciters = it }
        }
    }

    internal suspend fun fullSurahResponse(chapterReciterId: Int, surah: Int): JSONObject = withContext(Dispatchers.IO) {
        getJson("$workerBaseUrl/proxy/content/api/v4/chapter_recitations/$chapterReciterId/$surah?segments=true")
    }

    internal suspend fun mp3QuranTimings(read: Int, surah: Int): JSONArray = withContext(Dispatchers.IO) {
        JSONArray(getBody("https://www.mp3quran.net/api/v3/ayat_timing?surah=$surah&read=$read"))
    }

    suspend fun getChapterAudio(
        reciter: AudioReciterUiModel,
        surahNumber: Int,
    ): ChapterAudioMetadata = withContext(Dispatchers.IO) {
        val cacheKey = "${reciter.id}:$surahNumber"
        metadataMutex.withLock {
            chapterMetadataCache[cacheKey]
                ?: loadLocalChapterMetadata(reciter, surahNumber)
                ?: fetchChapterAudio(reciter, surahNumber).also {
                    chapterMetadataCache[cacheKey] = it
                    persistChapterMetadata(it)
                }
        }
    }

    suspend fun ensurePlaybackFile(
        metadata: ChapterAudioMetadata,
        verseKey: String,
    ): File = withContext(Dispatchers.IO) {
        when (metadata.mode) {
            PlaybackMode.FullSurah -> {
                if (!metadata.localSurahFile.exists()) {
                    val audioUrl = metadata.audioUrl ?: error("Missing surah audio URL.")
                    downloadToFile(audioUrl, metadata.localSurahFile)
                }
                metadata.localSurahFile
            }
            PlaybackMode.VerseByVerse -> {
                val file = verseAudioFile(metadata.reciter.id, metadata.surahNumber, verseKey)
                if (!file.exists()) {
                    val verseUrl = metadata.verseAudioUrls[verseKey] ?: error("Missing verse audio URL for $verseKey.")
                    downloadToFile(verseUrl, file)
                }
                file
            }
        }
    }

    fun currentDownloadState(reciterId: Int, surahNumber: Int): SurahDownloadState {
        val key = downloadKey(reciterId, surahNumber)
        return surahDownloadStates.value[key]
            ?: if (completionMarkerFile(reciterId, surahNumber).exists()) {
                SurahDownloadState.Downloaded
            } else {
                SurahDownloadState.NotDownloaded
            }
    }

    suspend fun refreshSurahDownloadState(
        reciter: AudioReciterUiModel,
        surahNumber: Int,
    ): SurahDownloadState = withContext(Dispatchers.IO) {
        val existing = surahDownloadStates.value[downloadKey(reciter.id, surahNumber)]
        if (existing is SurahDownloadState.Downloading) return@withContext existing
        val metadata = loadLocalChapterMetadata(reciter, surahNumber)
        val resolved = if (completionMarkerFile(reciter.id, surahNumber).exists() || metadata?.let(::isChapterCached) == true) {
            writeCompletionMarker(reciter.id, surahNumber)
            SurahDownloadState.Downloaded
        } else {
            SurahDownloadState.NotDownloaded
        }
        setDownloadState(reciter.id, surahNumber, resolved)
        resolved
    }

    suspend fun downloadSurah(
        reciter: AudioReciterUiModel,
        surahNumber: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val current = currentDownloadState(reciter.id, surahNumber)
        if (
            current is SurahDownloadState.Preparing ||
            current is SurahDownloadState.Downloading ||
            current is SurahDownloadState.Downloaded
        ) {
            return@withContext Result.success(Unit)
        }
        setDownloadState(reciter.id, surahNumber, SurahDownloadState.Preparing)
        runCatching {
            val metadata = getChapterAudio(reciter, surahNumber)
            setDownloadState(reciter.id, surahNumber, SurahDownloadState.Downloading(1))
            downloadChapterAudio(metadata) { progress ->
                setDownloadState(reciter.id, surahNumber, SurahDownloadState.Downloading(progress.coerceIn(1, 100)))
            }
            writeCompletionMarker(reciter.id, surahNumber)
            persistChapterMetadata(metadata)
            setDownloadState(reciter.id, surahNumber, SurahDownloadState.Downloaded)
        }.onFailure { error ->
            completionMarkerFile(reciter.id, surahNumber).delete()
            setDownloadState(
                reciter.id,
                surahNumber,
                SurahDownloadState.Failed(error.message ?: "Download failed."),
            )
        }
    }

    fun markQueued(
        reciterId: Int,
        surahNumber: Int,
        position: Int,
    ) {
        val current = currentDownloadState(reciterId, surahNumber)
        if (current is SurahDownloadState.Downloaded || current is SurahDownloadState.Downloading || current is SurahDownloadState.Preparing) {
            return
        }
        setDownloadState(reciterId, surahNumber, SurahDownloadState.Queued(position))
    }

    private fun isChapterCached(metadata: ChapterAudioMetadata): Boolean {
        return when (metadata.mode) {
            PlaybackMode.FullSurah -> metadata.localSurahFile.exists()
            PlaybackMode.VerseByVerse -> metadata.verseAudioUrls.isNotEmpty() &&
                metadata.verseAudioUrls.keys.all { verseKey ->
                    verseAudioFile(metadata.reciter.id, metadata.surahNumber, verseKey).exists()
                }
        }
    }

    private fun downloadChapterAudio(
        metadata: ChapterAudioMetadata,
        onProgress: (Int) -> Unit,
    ) {
        completionMarkerFile(metadata.reciter.id, metadata.surahNumber).delete()
        when (metadata.mode) {
            PlaybackMode.FullSurah -> {
                val audioUrl = metadata.audioUrl ?: error("Missing surah audio URL.")
                downloadToFile(audioUrl, metadata.localSurahFile, onProgress)
            }
            PlaybackMode.VerseByVerse -> {
                val totalFiles = metadata.verseAudioUrls.size.coerceAtLeast(1)
                metadata.verseAudioUrls.entries.forEachIndexed { index, entry ->
                    val destination = verseAudioFile(metadata.reciter.id, metadata.surahNumber, entry.key)
                    downloadToFile(entry.value, destination) { fileProgress ->
                        val overall = (((index + (fileProgress / 100f)) / totalFiles.toFloat()) * 100f).toInt()
                        onProgress(overall.coerceIn(0, 100))
                    }
                }
            }
        }
        onProgress(100)
    }

    private fun fetchSupportedReciters(): List<AudioReciterUiModel> {
        val json = getJson("$workerBaseUrl/proxy/content/api/v4/resources/recitations?language=en")
        val recitations = json.optJSONArray("recitations") ?: return emptyList()
        return desiredReciters.mapNotNull { desired ->
            val matches = buildList {
                for (index in 0 until recitations.length()) {
                    val item = recitations.optJSONObject(index) ?: continue
                    val searchableName = listOf(
                        item.optString("reciter_name"),
                        item.optJSONObject("translated_name")?.optString("name").orEmpty(),
                        item.optString("style"),
                    ).joinToString(" ")
                    if (desired.matcher.containsMatchIn(searchableName)) add(item)
                }
            }
            val selected = matches
                .sortedWith(
                    compareByDescending<JSONObject> { it.optString("style") == desired.preferredStyle }
                        .thenBy { it.optInt("id") },
                )
                .firstOrNull()
                ?: return@mapNotNull null
            AudioReciterUiModel(id = selected.optInt("id"), name = desired.displayName)
        }
    }

    private fun fetchChapterAudio(
        reciter: AudioReciterUiModel,
        surahNumber: Int,
    ): ChapterAudioMetadata {
        if (reciter.id in QuranTimedRecitations.additionalReciters.map { it.id }) {
            val source = QuranTimedRecitations.sources.getValue(reciter.id)
            val raw = JSONArray(getBody("https://www.mp3quran.net/api/v3/ayat_timing?surah=$surahNumber&read=${source.mp3QuranRead}"))
            val count = com.myvault.app.data.quran.quranCatalog.first { it.num == surahNumber }.ayat
            val timing = QuranTimingMap.parseMp3Quran(raw, source, surahNumber, count)
            return ChapterAudioMetadata(reciter, surahNumber, PlaybackMode.FullSurah, timing.audioUrl,
                timing.ayahs.associate { it.verseKey to it.startMs }, emptyMap(), surahAudioFile(reciter.id, surahNumber))
        }
        val timingsJson = getJson(
            "$workerBaseUrl/proxy/content/api/v4/chapter_recitations/${reciter.id}/$surahNumber?segments=true",
        )
        val audioFile = timingsJson.optJSONObject("audio_file")
        val fullAudioUrl = audioFile?.optString("audio_url").orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(::resolveAudioUrl)
        val timestamps = parseTimestamps(audioFile)
        val verseUrls = fetchVerseAudioUrls(reciter.id, surahNumber)

        if (verseUrls.isNotEmpty()) {
            return ChapterAudioMetadata(
                reciter = reciter,
                surahNumber = surahNumber,
                mode = PlaybackMode.VerseByVerse,
                audioUrl = fullAudioUrl,
                timestamps = timestamps,
                verseAudioUrls = verseUrls,
                localSurahFile = surahAudioFile(reciter.id, surahNumber),
            )
        }
        if (fullAudioUrl != null) {
            return ChapterAudioMetadata(
                reciter = reciter,
                surahNumber = surahNumber,
                mode = PlaybackMode.FullSurah,
                audioUrl = fullAudioUrl,
                timestamps = timestamps,
                verseAudioUrls = emptyMap(),
                localSurahFile = surahAudioFile(reciter.id, surahNumber),
            )
        }
        error("No playable audio metadata found for ${reciter.name}.")
    }

    private fun fetchVerseAudioUrls(reciterId: Int, surahNumber: Int): Map<String, String> {
        var page = 1
        val urlsByVerse = linkedMapOf<String, String>()
        while (true) {
            val json = getJson(
                "$workerBaseUrl/proxy/content/api/v4/verses/by_chapter/$surahNumber?language=en&audio=$reciterId&fields=verse_key&per_page=50&page=$page",
            )
            val verses = json.optJSONArray("verses") ?: break
            if (verses.length() == 0) break
            for (index in 0 until verses.length()) {
                val item = verses.optJSONObject(index) ?: continue
                val verseKey = item.optString("verse_key")
                val audioUrl = item.optJSONObject("audio")?.optString("url").orEmpty()
                if (verseKey.isNotBlank() && audioUrl.isNotBlank()) {
                    urlsByVerse[verseKey] = resolveAudioUrl(audioUrl)
                }
            }
            val nextPage = json.optJSONObject("pagination")?.optInt("next_page") ?: 0
            if (nextPage <= 0 || nextPage == page) break
            page = nextPage
        }
        return urlsByVerse
    }

    private fun parseTimestamps(audioFile: JSONObject?): Map<String, Long> {
        val timestamps = audioFile?.optJSONArray("timestamps") ?: return emptyMap()
        return buildMap {
            for (index in 0 until timestamps.length()) {
                val item = timestamps.optJSONObject(index) ?: continue
                val verseKey = item.optString("verse_key")
                if (verseKey.isNotBlank()) put(verseKey, item.optLong("timestamp_from"))
            }
        }
    }

    private fun getJson(url: String): JSONObject = JSONObject(getBody(url))

    private fun getBody(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) error(body.ifBlank { "Audio request failed with code $responseCode." })
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(url: String, destination: File, onProgress: (Int) -> Unit = {}) {
        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(errorBody.ifBlank { "Audio download failed with code $responseCode." })
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                        }
                        read = input.read(buffer)
                    }
                }
            }
            moveFileAtomically(tempFile, destination)
            onProgress(100)
        } finally {
            connection.disconnect()
            if (tempFile.exists() && !destination.exists()) tempFile.delete()
        }
    }

    private fun surahAudioFile(reciterId: Int, surahNumber: Int): File {
        return File(File(context.filesDir, "quran_audio/$reciterId"), "surah_$surahNumber.mp3")
    }

    private fun verseAudioFile(reciterId: Int, surahNumber: Int, verseKey: String): File {
        return File(File(context.filesDir, "quran_audio/$reciterId/surah_$surahNumber"), "${verseKey.replace(':', '_')}.mp3")
    }

    private fun chapterMetadataFile(reciterId: Int, surahNumber: Int): File {
        return File(File(context.filesDir, "quran_audio/$reciterId"), "surah_$surahNumber.metadata.json")
    }

    private fun completionMarkerFile(reciterId: Int, surahNumber: Int): File {
        return File(File(context.filesDir, "quran_audio/$reciterId"), "surah_$surahNumber.complete")
    }

    private fun writeCompletionMarker(reciterId: Int, surahNumber: Int) {
        val marker = completionMarkerFile(reciterId, surahNumber)
        marker.parentFile?.mkdirs()
        marker.writeText("complete")
    }

    private fun persistChapterMetadata(metadata: ChapterAudioMetadata) {
        val file = chapterMetadataFile(metadata.reciter.id, metadata.surahNumber)
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val json = JSONObject().apply {
            put("reciterId", metadata.reciter.id)
            put("reciterName", metadata.reciter.name)
            put("surahNumber", metadata.surahNumber)
            put("mode", metadata.mode.name)
            put("audioUrl", metadata.audioUrl.orEmpty())
            put("timestamps", JSONObject().apply {
                metadata.timestamps.forEach { (verseKey, timestamp) -> put(verseKey, timestamp) }
            })
            put("verseAudioUrls", JSONObject().apply {
                metadata.verseAudioUrls.forEach { (verseKey, audioUrl) -> put(verseKey, audioUrl) }
            })
        }
        tempFile.writeText(json.toString())
        moveFileAtomically(tempFile, file)
    }

    private fun loadLocalChapterMetadata(
        reciter: AudioReciterUiModel,
        surahNumber: Int,
    ): ChapterAudioMetadata? {
        val metadataFile = chapterMetadataFile(reciter.id, surahNumber)
        if (!metadataFile.exists()) return null
        return runCatching {
            val json = JSONObject(metadataFile.readText())
            ChapterAudioMetadata(
                reciter = reciter,
                surahNumber = surahNumber,
                mode = PlaybackMode.valueOf(json.optString("mode", PlaybackMode.FullSurah.name)),
                audioUrl = json.optString("audioUrl").takeIf { it.isNotBlank() },
                timestamps = json.optJSONObject("timestamps").toLongMap(),
                verseAudioUrls = json.optJSONObject("verseAudioUrls").toStringMap(),
                localSurahFile = surahAudioFile(reciter.id, surahNumber),
            )
        }.getOrNull()
    }

    private fun moveFileAtomically(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (source.absolutePath == destination.absolutePath) return
        if (destination.exists()) destination.delete()
        if (source.renameTo(destination)) return
        source.copyTo(destination, overwrite = true)
        if (!source.delete()) source.deleteOnExit()
    }

    private fun JSONObject?.toLongMap(): Map<String, Long> {
        val source = this ?: return emptyMap()
        return buildMap {
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, source.optLong(key))
            }
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        val source = this ?: return emptyMap()
        return buildMap {
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, source.optString(key))
            }
        }
    }

    private fun resolveAudioUrl(rawUrl: String): String {
        return when {
            rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("http://") -> "https://${rawUrl.removePrefix("http://")}"
            rawUrl.startsWith("//") -> "https:$rawUrl"
            else -> "$verseAudioBaseUrl/$rawUrl"
        }
    }

    private fun setDownloadState(reciterId: Int, surahNumber: Int, state: SurahDownloadState) {
        _surahDownloadStates.update { current ->
            current + (downloadKey(reciterId, surahNumber) to state)
        }
    }

    private fun downloadKey(reciterId: Int, surahNumber: Int): String = "$reciterId:$surahNumber"

    private data class DesiredReciter(
        val displayName: String,
        val matcher: Regex,
        val preferredStyle: String? = null,
    )

    private companion object {
        const val workerBaseUrl = "https://quran-proxy.aliabuhassan1995-054.workers.dev"
        const val verseAudioBaseUrl = "https://verses.quran.com"

        val fallbackReciters = listOf(
            AudioReciterUiModel(id = 7, name = "Mishary al-Afasy"),
            AudioReciterUiModel(id = 3, name = "Abdur-Rahman as-Sudais"),
        )

        val desiredReciters = listOf(
            DesiredReciter("Abdul Basit (Mujawwad)", Regex("abdul.?bas(et|it)", RegexOption.IGNORE_CASE), "Mujawwad"),
            DesiredReciter("Abdul Basit (Murattal)", Regex("abdul.?bas(et|it)", RegexOption.IGNORE_CASE), "Murattal"),
            DesiredReciter("Abdur-Rahman as-Sudais", Regex("sudais", RegexOption.IGNORE_CASE)),
            DesiredReciter("Abu Bakr al-Shatri", Regex("shatri", RegexOption.IGNORE_CASE)),
            DesiredReciter("Hani ar-Rifai", Regex("rifai", RegexOption.IGNORE_CASE)),
            DesiredReciter("Husary", Regex("husary", RegexOption.IGNORE_CASE)),
            DesiredReciter("Husary (Muallim)", Regex("husary", RegexOption.IGNORE_CASE), "Muallim"),
            DesiredReciter("Mishary al-Afasy", Regex("mishari|mishary|afasy", RegexOption.IGNORE_CASE)),
            DesiredReciter("Mohamed Siddiq al-Minshawi (Mujawwad)", Regex("minshawi", RegexOption.IGNORE_CASE), "Mujawwad"),
            DesiredReciter("Mohamed Siddiq al-Minshawi (Murattal)", Regex("minshawi", RegexOption.IGNORE_CASE), "Murattal"),
            DesiredReciter("Sa`ud ash-Shuraym", Regex("shuraym|shuraim", RegexOption.IGNORE_CASE)),
            DesiredReciter("Mohamed al-Tablawi", Regex("tablawi", RegexOption.IGNORE_CASE)),
        )

        val recitersMutex = Mutex()
        val metadataMutex = Mutex()
        var cachedReciters: List<AudioReciterUiModel>? = null
        val chapterMetadataCache = mutableMapOf<String, ChapterAudioMetadata>()
    }
}
