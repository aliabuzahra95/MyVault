package com.myvault.app.data.quran.audio

import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest

enum class QuranListeningMode { ThisAyah, ContinueSurah }

data class QuranAyahTiming(val verseKey: String, val startMs: Long, val endMs: Long)

/** Boundaries belong to one exact resource, never merely to a reciter's name. */
data class QuranTimingMap(
    val recordingId: String,
    val surah: Int,
    val audioUrl: String,
    val ayahs: List<QuranAyahTiming>,
) {
    fun at(positionMs: Long): QuranAyahTiming? {
        // Keep the preceding ayah through natural silence; a preamble has no ayah.
        return ayahs.lastOrNull { it.startMs <= positionMs }
    }

    fun ayah(number: Int): QuranAyahTiming? = ayahs.getOrNull(number - 1)

    fun validateDuration(durationMs: Long) {
        require(durationMs > 0 && ayahs.last().endMs <= durationMs + 250L) { "Recording and ayah timings do not match." }
        require(durationMs - ayahs.last().endMs < 30_000L) { "Recording has unaccounted trailing audio." }
    }

    companion object {
        fun parse(json: JSONObject, chapterReciter: Int, surah: Int, ayahCount: Int,
                  expectedFolder: String = "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/"): QuranTimingMap {
            val audio = json.getJSONObject("audio_file")
            require(audio.getInt("chapter_id") == surah) { "Audio returned for a different Surah." }
            val url = audio.getString("audio_url")
            require(url.startsWith(expectedFolder)) { "Unverified recording identity." }
            val raw = audio.optJSONArray("timestamps") ?: error("This recording has no ayah timings.")
            require(raw.length() == ayahCount) { "This recording has incomplete ayah timings." }
            val timings = (0 until raw.length()).map { index ->
                val item = raw.getJSONObject(index)
                val key = item.getString("verse_key")
                val start = item.getLong("timestamp_from")
                val end = item.getLong("timestamp_to")
                require(key == "$surah:${index + 1}" && start >= 0 && end > start) { "Invalid ayah timing." }
                QuranAyahTiming(key, start, end)
            }
            require(timings.zipWithNext().all { (a, b) -> b.startMs >= a.endMs }) { "Overlapping ayah timings." }
            val identity = "$chapterReciter:${audio.getLong("id")}:$url:${timings.joinToString()}"
            val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
            return QuranTimingMap(hash, surah, url, timings)
        }

        internal fun parseMp3Quran(raw: JSONArray, source: QuranTimedRecitation, surah: Int, ayahCount: Int): QuranTimingMap {
            require(source.mp3QuranRead != null) { "Missing MP3Quran recording identity." }
            val entries = (0 until raw.length()).map(raw::getJSONObject)
            val preambles = entries.filter { it.getInt("ayah") == 0 }
            require(preambles.size <= 1 && (preambles.isEmpty() || entries.first() === preambles.single())) { "Unexpected preamble position." }
            val verses = entries.filter { it.getInt("ayah") != 0 }
            require(verses.size == ayahCount) { "This recording has incomplete ayah timings." }
            val timings = verses.mapIndexed { index, item ->
                val start = item.getLong("start_time")
                val end = item.getLong("end_time")
                require(item.getInt("ayah") == index + 1 && start >= 0 && end > start) { "Invalid ayah timing." }
                QuranAyahTiming("$surah:${index + 1}", start, end)
            }
            require(timings.zipWithNext().all { (a, b) -> b.startMs >= a.endMs }) { "Overlapping ayah timings." }
            preambles.firstOrNull()?.let {
                require(it.getLong("start_time") == 0L && it.getLong("end_time") in 0..timings.first().startMs) { "Preamble overlaps the first ayah." }
            }
            val url = source.folder + surah.toString().padStart(3, '0') + ".mp3"
            val identity = "mp3quran:${source.mp3QuranRead}:$url:${timings.joinToString()}"
            val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
            return QuranTimingMap(hash, surah, url, timings)
        }
    }
}
