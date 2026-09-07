package com.myvault.app.data.quran.audio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranTimedRecitationsTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("quran-audio/$name-1.json")!!.readText()

    @Test fun requestedRecitersHaveDistinctVerifiedSourceMappings() {
        for (id in listOf(1, 2, 4, 10, 11, QuranTimedRecitations.YASSER_AL_DOSSARI, QuranTimedRecitations.SAAD_AL_GHAMDI, QuranTimedRecitations.FARES_ABBAD)) {
            assertNotNull(QuranTimedRecitations.sources[id])
        }
        assertNull("Tablawi is not chapter-reciter 11 (al-Qasim)", QuranTimedRecitations.sources.getValue(11).chapterReciterId)
        assertEquals(106, QuranTimedRecitations.sources.getValue(11).mp3QuranRead)
        assertEquals(3, QuranTimedRecitations.additionalReciters.map { it.id }.distinct().size)
        assertTrue(QuranTimedRecitations.additionalReciters.all { it.id > 1_000_000 })
    }

    @Test fun everyWidgetReciterHasARealContinuousSource() {
        assertTrue(QuranTimedRecitations.widgetReciters.isNotEmpty())
        assertEquals(
            QuranTimedRecitations.widgetReciters.map { it.id }.toSet(),
            QuranTimedRecitations.sources.keys,
        )
        assertEquals(
            QuranTimedRecitations.widgetReciters.size,
            QuranTimedRecitations.widgetReciters.distinctBy { it.id }.size,
        )
    }

    @Test fun requestedRecitersRemainSelectableWhenCatalogIsUnavailableWithoutRemovingExistingOnes() {
        val offline = QuranTimedRecitations.includeRequested(emptyList())
        assertEquals(setOf(1, 2, 4, 10, 11, 1_000_092, 1_000_030, 1_000_081), offline.map { it.id }.toSet())
        val existing = listOf(AudioReciterUiModel(3, "Sudais"), AudioReciterUiModel(4, "Shatri"))
        val combined = QuranTimedRecitations.includeRequested(existing)
        assertEquals(existing, combined.take(2))
        assertEquals(combined.size, combined.map { it.id }.distinct().size)
    }

    @Test fun liveFoundationSamplesUseTheirOwnRecordingAndCompleteTimings() {
        val identities = mutableSetOf<String>()
        for ((id, name) in listOf(1 to "basit-mujawwad", 4 to "shatri", 6 to "husary", 9 to "minshawi")) {
            val source = QuranTimedRecitations.sources.getValue(id)
            val timing = QuranTimingMap.parse(JSONObject(fixture(name)), source.chapterReciterId!!, 1, 7, source.folder)
            assertEquals((1..7).map { "1:$it" }, timing.ayahs.map { it.verseKey })
            assertTrue(identities.add(timing.recordingId))
            assertTrue(timing.audioUrl.startsWith(source.folder))
        }
    }

    @Test fun liveMp3QuranSamplesExcludePreambleAndKeepExactSource() {
        for ((id, name) in listOf(2 to "basit-mp3", 10 to "shuraym-mp3", 11 to "tablawi", QuranTimedRecitations.SAAD_AL_GHAMDI to "ghamdi",
            QuranTimedRecitations.YASSER_AL_DOSSARI to "dossari", QuranTimedRecitations.FARES_ABBAD to "fares")) {
            val source = QuranTimedRecitations.sources.getValue(id)
            val timing = QuranTimingMap.parseMp3Quran(JSONArray(fixture(name)), source, 1, 7)
            assertEquals((1..7).map { "1:$it" }, timing.ayahs.map { it.verseKey })
            assertEquals(source.folder + "001.mp3", timing.audioUrl)
            if (timing.ayahs.first().startMs > 0) assertNull(timing.at(0))
        }
    }

    @Test fun missingOrOverlappingMp3QuranTimingsFailClosed() {
        val source = QuranTimedRecitations.sources.getValue(11)
        val missing = JSONArray(fixture("tablawi")).apply { remove(length() - 1) }
        assertThrows(IllegalArgumentException::class.java) { QuranTimingMap.parseMp3Quran(missing, source, 1, 7) }
        val overlap = JSONArray(fixture("tablawi")).apply { getJSONObject(2).put("start_time", 0) }
        assertThrows(IllegalArgumentException::class.java) { QuranTimingMap.parseMp3Quran(overlap, source, 1, 7) }
    }

    @Test fun overlappingAlternativeRecordingIsNotSilentlyRepaired() {
        assertThrows(IllegalArgumentException::class.java) {
            QuranTimingMap.parse(JSONObject(fixture("shuraym")), 10, 1, 7,
                "https://download.quranicaudio.com/qdc/saud_ash-shuraym/murattal/")
        }
    }

    @Test fun completedFullSurahDownloadReusesPersistedExactBoundaries() {
        val metadata = ChapterAudioMetadata(
            reciter = AudioReciterUiModel(QuranTimedRecitations.FARES_ABBAD, "Fares Abbad"),
            surahNumber = 1,
            mode = PlaybackMode.FullSurah,
            audioUrl = "https://server8.mp3quran.net/frs_a/001.mp3",
            timestamps = mapOf("1:1" to 500L, "1:2" to 1_500L, "1:3" to 2_500L),
            verseAudioUrls = emptyMap(),
            localSurahFile = java.io.File("surah_1.mp3"),
            endTimestamps = mapOf("1:1" to 1_400L, "1:2" to 2_400L, "1:3" to 3_500L),
            recordingId = "fares-1",
        )

        val timing = requireNotNull(metadata.toDownloadedTimingMap(ayahCount = 3, durationMs = 3_500L))

        assertEquals("fares-1", timing.recordingId)
        assertEquals(listOf(1_400L, 2_400L, 3_500L), timing.ayahs.map { it.endMs })
    }

    @Test fun olderFullSurahDownloadInfersMissingEndsWithoutRedownload() {
        val metadata = ChapterAudioMetadata(
            reciter = AudioReciterUiModel(QuranTimedRecitations.SAAD_AL_GHAMDI, "Saad al-Ghamdi"),
            surahNumber = 1,
            mode = PlaybackMode.FullSurah,
            audioUrl = "https://server7.mp3quran.net/s_gmd/001.mp3",
            timestamps = mapOf("1:1" to 400L, "1:2" to 1_400L, "1:3" to 2_400L),
            verseAudioUrls = emptyMap(),
            localSurahFile = java.io.File("surah_1.mp3"),
        )

        val timing = requireNotNull(metadata.toDownloadedTimingMap(ayahCount = 3, durationMs = 3_600L))

        assertEquals(listOf(1_400L, 2_400L, 3_600L), timing.ayahs.map { it.endMs })
    }

    @Test fun incompleteOrVerseByVerseMetadataCannotMasqueradeAsOfflineSurah() {
        val metadata = ChapterAudioMetadata(
            reciter = AudioReciterUiModel(4, "Abu Bakr al-Shatri"),
            surahNumber = 1,
            mode = PlaybackMode.VerseByVerse,
            audioUrl = null,
            timestamps = mapOf("1:1" to 0L),
            verseAudioUrls = mapOf("1:1" to "https://example.test/1.mp3"),
            localSurahFile = java.io.File("surah_1.mp3"),
        )

        assertNull(metadata.toDownloadedTimingMap(ayahCount = 1, durationMs = 1_000L))
    }
}
