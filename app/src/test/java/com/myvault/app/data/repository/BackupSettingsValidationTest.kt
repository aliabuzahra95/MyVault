package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import com.myvault.app.data.quran.QuranTranslationSource

class BackupSettingsValidationTest {
    @Test
    fun validBackupSettingsConvertToPreferences() {
        val preferences = baseSettings()
            .put("themeModeV2", "oled")
            .put("quranBookmarkedVerses", JSONArray(listOf("1:1", "2:255")))
            .put("quranTranslationSource", QuranTranslationSource.Maududi.storedValue)
            .put(
                "quranRecentLocations",
                JSONArray()
                    .put(JSONObject().put("surahNumber", 1).put("ayahNumber", 1).put("lastReadAt", 10L))
                    .put(JSONObject().put("surahNumber", 2).put("ayahNumber", 255).put("lastReadAt", 20L)),
            )
            .put(
                "quranMemorizationRecords",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("verseKey", "2:255")
                            .put("surahNumber", 2)
                            .put("ayahNumber", 255)
                            .put("startedAt", 1L)
                            .put("lastReviewedAt", 2L)
                            .put("reviewCount", 3)
                            .put("memorizedAt", 4L)
                            .put("isRevision", true)
                            .put("isWeak", false)
                            .put("updatedAt", 5L),
                    ),
            )
            .put(
                "quranMemorizationAttempts",
                JSONArray()
                    .put(quranMemorizationAttemptJson(attemptId = "ayah-attempt-1", timestampMs = 30L)),
            )
            .put(
                "quranSurahMemorizationAttempts",
                JSONArray()
                    .put(quranSurahAttemptJson(attemptId = "surah-attempt-1", timestampMs = 40L)),
            )
            .put("expandedFolderIds", JSONArray(listOf("folder-a", "folder-b")))
            .put("libraryViewModesByLocation", JSONObject().put("root", "grid").put("folder-a", "icons"))
            .toValidatedBackupPreferences()

        assertEquals("dark", preferences.theme)
        assertEquals("oled", preferences.themeModeV2)
        assertEquals("personal", preferences.workspace)
        assertEquals(QuranTranslationSource.Maududi.storedValue, preferences.quranTranslationSource)
        assertEquals(setOf("1:1", "2:255"), preferences.quranBookmarkedVerses)
        assertEquals(2, preferences.quranRecentLocations.first().surahNumber)
        assertEquals(1, preferences.quranMemorizationRecords.size)
        assertEquals("ayah-attempt-1", preferences.quranMemorizationAttempts.single().attemptId)
        assertEquals("surah-attempt-1", preferences.quranSurahMemorizationAttempts.single().attemptId)
        assertEquals(setOf("folder-a", "folder-b"), preferences.expandedFolderIds)
        assertEquals(mapOf("root" to "grid", "folder-a" to "icons"), preferences.libraryViewModesByLocation)
    }

    @Test
    fun legacySettingsRemainValidWithoutThemeModeV2() {
        val preferences = baseSettings().toValidatedBackupPreferences()

        assertEquals("dark", preferences.theme)
        assertNull(preferences.themeModeV2)
    }

    @Test
    fun unknownThemeModeV2RemainsAdditiveForSafeResolverFallback() {
        val preferences = baseSettings()
            .put("themeModeV2", "future_theme")
            .toValidatedBackupPreferences()

        assertEquals("dark", preferences.theme)
        assertEquals("future_theme", preferences.themeModeV2)
    }

    @Test
    fun backedUpQuranMemorizationAttemptsAreSortedAndCapped() {
        val ayahAttempts = JSONArray().apply {
            repeat(55) { index ->
                put(quranMemorizationAttemptJson(attemptId = "ayah-attempt-$index", timestampMs = index.toLong()))
            }
        }
        val surahAttempts = JSONArray().apply {
            repeat(55) { index ->
                put(quranSurahAttemptJson(attemptId = "surah-attempt-$index", timestampMs = index.toLong()))
            }
        }

        val preferences = baseSettings()
            .put("quranMemorizationAttempts", ayahAttempts)
            .put("quranSurahMemorizationAttempts", surahAttempts)
            .toValidatedBackupPreferences()

        assertEquals(50, preferences.quranMemorizationAttempts.size)
        assertEquals("ayah-attempt-54", preferences.quranMemorizationAttempts.first().attemptId)
        assertEquals("ayah-attempt-5", preferences.quranMemorizationAttempts.last().attemptId)
        assertEquals(50, preferences.quranSurahMemorizationAttempts.size)
        assertEquals("surah-attempt-54", preferences.quranSurahMemorizationAttempts.first().attemptId)
        assertEquals("surah-attempt-5", preferences.quranSurahMemorizationAttempts.last().attemptId)
    }

    @Test
    fun invalidQuranBookmarkIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put("quranBookmarkedVerses", JSONArray(listOf("999:1")))
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun unsupportedQuranTranslationSourceIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put("quranTranslationSource", "unknown_translation")
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun mismatchedQuranMemorizationRecordIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put(
                    "quranMemorizationRecords",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("verseKey", "2:255")
                                .put("surahNumber", 1)
                                .put("ayahNumber", 1)
                                .put("startedAt", 1L)
                                .put("lastReviewedAt", 2L)
                                .put("reviewCount", 0)
                                .put("updatedAt", 3L),
                        ),
                )
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun mismatchedQuranMemorizationAttemptIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put(
                    "quranMemorizationAttempts",
                    JSONArray()
                        .put(quranMemorizationAttemptJson().put("verseKey", "1:2")),
                )
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun mismatchedQuranSurahAttemptIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put(
                    "quranSurahMemorizationAttempts",
                    JSONArray()
                        .put(quranSurahAttemptJson().put("totalAyahs", 999)),
                )
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun unsafeLibraryDisplayScopeIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put("libraryViewModesByLocation", JSONObject().put("../unsafe", "grid"))
                .toValidatedBackupPreferences()
        }
    }

    @Test
    fun duplicateExpandedFolderStateIsRejectedBeforeRestore() {
        assertThrows(IllegalStateException::class.java) {
            baseSettings()
                .put("expandedFolderIds", JSONArray(listOf("folder-a", "folder-a")))
                .toValidatedBackupPreferences()
        }
    }

    private fun baseSettings(): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("theme", "dark")
            .put("workspace", "personal")
            .put("quranLastReadSurah", 1)
            .put("quranLastReadAyah", 1)
            .put("quranArabicFontPercent", 100)
            .put("quranTranslationFontPercent", 100)
            .put("quranAudioPlaybackSpeed", 1.0)
            .put("libraryViewMode", "list")

    private fun quranMemorizationAttemptJson(
        attemptId: String = "ayah-attempt",
        timestampMs: Long = 10L,
    ): JSONObject =
        JSONObject()
            .put("attemptId", attemptId)
            .put("timestampMs", timestampMs)
            .put("surahNumber", 1)
            .put("ayahNumber", 1)
            .put("verseKey", "1:1")
            .put("durationMs", 3_000L)
            .put("providerName", "Google Speech")
            .put("modelName", "chirp_3")
            .put("latencyMs", 1_000L)
            .put("transcript", "بسم الله")
            .put("normalizedTranscript", "بسم الله")
            .put("recognizedCount", 2)
            .put("missingCount", 0)
            .put("extraCount", 0)
            .put("repeatedCount", 0)
            .put("unknownCount", 0)
            .put("confidence", 0.95)
            .put("overallScore", 100)
            .put("grade", "EXCELLENT")
            .put("recognizedPercentage", 1.0)
            .put("scoreCalculationVersion", "quran_memorization_score_v1")
            .put("status", "PASSED")
            .put("transcriptionSucceeded", true)
            .put("errorMessage", JSONObject.NULL)
            .put("expectedWordIds", JSONArray(listOf("1:1:1", "1:1:2")))
            .put("matchedWordIds", JSONArray(listOf("1:1:1", "1:1:2")))
            .put("missingWordIds", JSONArray())
            .put("extraTranscriptWords", JSONArray())
            .put("repeatedTranscriptWords", JSONArray())

    private fun quranSurahAttemptJson(
        attemptId: String = "surah-attempt",
        timestampMs: Long = 20L,
    ): JSONObject =
        JSONObject()
            .put("attemptId", attemptId)
            .put("timestampMs", timestampMs)
            .put("surahNumber", 1)
            .put("surahName", "Al-Fatihah")
            .put("totalAyahs", 7)
            .put("durationMs", 20_000L)
            .put("providerName", "Google Speech")
            .put("modelName", "chirp_3")
            .put("latencyMs", 2_000L)
            .put("transcript", "بسم الله")
            .put("normalizedTranscript", "بسم الله")
            .put("overallScore", 100)
            .put("grade", "EXCELLENT")
            .put("recognizedPercentage", 1.0)
            .put("scoreCalculationVersion", "quran_memorization_score_v1")
            .put("transcriptionSucceeded", true)
            .put("errorMessage", JSONObject.NULL)
            .put(
                "ayahResults",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("verseKey", "1:1")
                            .put("surahNumber", 1)
                            .put("ayahNumber", 1)
                            .put("expectedWordCount", 2)
                            .put("recognizedCount", 2)
                            .put("missingCount", 0)
                            .put("extraCount", 0)
                            .put("repeatedCount", 0)
                            .put("unknownCount", 0)
                            .put("overallScore", 100)
                            .put("grade", "EXCELLENT")
                            .put("status", "PASSED")
                            .put("missingWordIds", JSONArray())
                            .put("wordResults", JSONArray())
                            .put("extraTranscriptWords", JSONArray())
                            .put("repeatedTranscriptWords", JSONArray()),
                    ),
            )
            .put("ayahsNeedingReview", JSONArray())
            .put("missingWordIds", JSONArray())
            .put("extraTranscriptWords", JSONArray())
            .put("repeatedTranscriptWords", JSONArray())
}
