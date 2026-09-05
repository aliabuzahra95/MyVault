package com.myvault.app.data.quran.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import com.myvault.app.data.quran.quranCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class QuranFullSurahSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: QuranAudioRepository,
) {
    private val mutex = Mutex()
    private val directory get() = File(context.cacheDir, "quran_continuous_v1").apply { mkdirs() }

    // Verified mapping between the separate ayah-recitation and chapter-reciter catalogs.
    fun supports(reciter: AudioReciterUiModel): Boolean = reciter.id in QuranTimedRecitations.sources

    suspend fun resolve(reciter: AudioReciterUiModel, surah: Int): Pair<QuranTimingMap, File> = withContext(Dispatchers.IO) {
        require(supports(reciter)) { "Continuous synchronized playback is not available for this reciter. This ayah remains available." }
        val source = QuranTimedRecitations.sources.getValue(reciter.id)
        val count = quranCatalog.firstOrNull { it.num == surah }?.ayat ?: error("Unknown Surah.")
        mutex.withLock {
            val metadata = File(directory, "${source.cacheKey}-$surah.json")
            val json = if (metadata.isFile) runCatching { JSONObject(metadata.readText()) }.getOrNull() else null
            val response = json ?: source.chapterReciterId?.let { repository.fullSurahResponse(it, surah) }
                ?: JSONObject().put("timings", repository.mp3QuranTimings(source.mp3QuranRead!!, surah))
            val timing = source.chapterReciterId?.let { QuranTimingMap.parse(response, it, surah, count, source.folder) }
                ?: QuranTimingMap.parseMp3Quran(response.getJSONArray("timings"), source, surah, count)
            coroutineContext.ensureActive()
            val file = File(directory, "${timing.recordingId}.mp3")
            if (file.isFile && runCatching { validate(file, timing) }.isFailure) file.delete()
            if (!file.isFile) {
                val connection = URL(timing.audioUrl).openConnection() as HttpURLConnection
                val temporary = File(directory, "${timing.recordingId}.part")
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                try {
                    require(connection.responseCode == 200) { "Full-Surah audio could not be downloaded (${connection.responseCode})." }
                    val length = connection.contentLengthLong
                    require(length in 1..MAX_RECORDING_BYTES) { "Recording size is unavailable or exceeds the playback cache limit." }
                    trimCache(length, file.name)
                    require(directory.usableSpace > length + 32L * 1024 * 1024) { "Not enough free space for this Surah recording." }
                    var copied = 0L
                    connection.inputStream.use { input -> temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            require(copied <= length) { "Unexpected recording download length." }
                            output.write(buffer, 0, read)
                        }
                    } }
                    require(copied == length) { "Recording download was interrupted. Please retry." }
                    validate(temporary, timing)
                    coroutineContext.ensureActive()
                    check(temporary.renameTo(file)) { "The recording could not be stored." }
                } finally {
                    connection.disconnect()
                    temporary.delete()
                }
            }
            val pending = File(directory, "${source.cacheKey}-$surah.json.part")
            pending.writeText(response.toString())
            check(pending.renameTo(metadata)) { "Audio timings could not be stored." }
            file.setLastModified(System.currentTimeMillis())
            timing to file
        }
    }

    private fun validate(file: File, timing: QuranTimingMap) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            timing.validateDuration(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
        } finally { retriever.release() }
    }

    private fun trimCache(incoming: Long, keep: String) {
        val files = directory.listFiles().orEmpty().filter { it.extension == "mp3" && it.name != keep }.sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes + incoming <= MAX_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) bytes -= size
        }
    }

    private companion object {
        const val MAX_RECORDING_BYTES = 256L * 1024 * 1024
        const val MAX_CACHE_BYTES = 384L * 1024 * 1024
    }
}
