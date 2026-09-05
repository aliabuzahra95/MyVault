package com.myvault.app.data.quran.audio

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranPlaybackTimelineTest {
    private fun json(surah: Int = 1) = JSONObject(requireNotNull(javaClass.getResource("/quran-audio/afasy-$surah.json")).readText())
    private fun timing() = QuranTimingMap.parse(json(), 7, 1, 7)

    @Test fun `live Fatiha metadata uses milliseconds and matches decoded recording duration`() {
        val map = timing()
        assertEquals(6090L, map.ayah(2)!!.startMs)
        assertEquals("1:1", map.at(6089)!!.verseKey)
        assertEquals("1:2", map.at(6090)!!.verseKey)
        assertEquals("1:5", map.at(25_000)!!.verseKey)
        assertEquals("1:7", map.at(45_000)!!.verseKey)
        map.validateDuration(46_447)
    }

    @Test fun `other live candidates require full canonical coverage`() {
        for ((surah, count) in listOf(2 to 286, 4 to 176, 9 to 129)) {
            val map = QuranTimingMap.parse(json(surah), 7, surah, count)
            assertEquals(count, map.ayahs.size)
        }
    }

    @Test fun `missing duplicate out of order and negative intervals rejected`() {
        for (variant in 0..3) {
            val body = json()
            val values = body.getJSONObject("audio_file").getJSONArray("timestamps")
            when (variant) {
                0 -> values.remove(2)
                1 -> values.getJSONObject(1).put("verse_key", "1:1")
                2 -> values.getJSONObject(1).put("timestamp_from", 4000)
                3 -> values.getJSONObject(0).put("timestamp_from", -1)
            }
            assertThrows(IllegalArgumentException::class.java) { QuranTimingMap.parse(body, 7, 1, 7) }
        }
    }

    @Test fun `different track and duration cannot reuse timings`() {
        val body = json()
        body.getJSONObject("audio_file").put("audio_url", "https://example.com/another.mp3")
        assertThrows(IllegalArgumentException::class.java) { QuranTimingMap.parse(body, 7, 1, 7) }
        assertThrows(IllegalArgumentException::class.java) { timing().validateDuration(30_000) }
        body.getJSONObject("audio_file").put("audio_url", timing().audioUrl).put("id", 999)
        assertNotEquals(timing().recordingId, QuranTimingMap.parse(body, 7, 1, 7).recordingId)
    }

    @Test fun `single pauses at exact boundary continuous crosses without another resource`() {
        val policy = QuranPlaybackTimeline(timing(), 1, QuranListeningMode.ThisAyah)
        assertFalse(policy.shouldPause(6000))
        assertTrue(policy.shouldPause(6090))
        assertNull(policy.changeMode(QuranListeningMode.ContinueSurah, 6000))
        assertFalse(policy.shouldPause(6090))
        assertFalse(policy.shouldPause(33_250))
        assertEquals("1:7", policy.timing.at(33_250)!!.verseKey)
    }

    @Test fun `switching to single finishes currently playing ayah`() {
        val policy = QuranPlaybackTimeline(timing(), 1, QuranListeningMode.ContinueSurah)
        assertNull(policy.changeMode(QuranListeningMode.ThisAyah, 25_000))
        assertEquals("1:5", policy.target.verseKey)
        assertFalse(policy.shouldPause(27_659))
        assertTrue(policy.shouldPause(27_660))
    }

    @Test fun `continue after automatic boundary uses next ayah but never wraps`() {
        val policy = QuranPlaybackTimeline(timing(), 1, QuranListeningMode.ThisAyah)
        policy.reachedBoundary()
        assertEquals(6090L, policy.changeMode(QuranListeningMode.ContinueSurah, 6090))
        assertFalse(policy.boundaryReached)
        val final = QuranPlaybackTimeline(timing(), 7, QuranListeningMode.ThisAyah)
        final.reachedBoundary()
        assertNull(final.changeMode(QuranListeningMode.ContinueSurah, 46_490))
        assertTrue(final.boundaryReached)
    }

    @Test fun `seek resets stop target independent of speed or wall time`() {
        val policy = QuranPlaybackTimeline(timing(), 1, QuranListeningMode.ThisAyah)
        policy.seek(25_000)
        assertEquals("1:5", policy.target.verseKey)
        assertFalse(policy.shouldPause(26_000))
        policy.seek(1000)
        assertEquals("1:1", policy.target.verseKey)
        assertTrue(policy.shouldPause(6090))
    }

    @Test fun `manually paused mode switch requests no seek or resume`() {
        val policy = QuranPlaybackTimeline(timing(), 2, QuranListeningMode.ThisAyah)
        assertNull(policy.changeMode(QuranListeningMode.ContinueSurah, 9000))
        assertNull(policy.changeMode(QuranListeningMode.ThisAyah, 9000))
    }
}
