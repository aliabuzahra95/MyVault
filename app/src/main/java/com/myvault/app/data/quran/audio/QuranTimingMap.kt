package com.myvault.app.data.quran.audio

import org.json.JSONObject
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
        fun parse(json: JSONObject, chapterReciter: Int, surah: Int, ayahCount: Int): QuranTimingMap {
            val audio = json.getJSONObject("audio_file")
            require(audio.getInt("chapter_id") == surah) { "Audio returned for a different Surah." }
            val url = audio.getString("audio_url")
            require(url.startsWith("https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/")) { "Unverified recording identity." }
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
    }
}
