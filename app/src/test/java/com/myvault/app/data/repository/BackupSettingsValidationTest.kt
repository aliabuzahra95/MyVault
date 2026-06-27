package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSettingsValidationTest {
    @Test
    fun validBackupSettingsConvertToPreferences() {
        val preferences = baseSettings()
            .put("quranBookmarkedVerses", JSONArray(listOf("1:1", "2:255")))
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
            .put("expandedFolderIds", JSONArray(listOf("folder-a", "folder-b")))
            .put("libraryViewModesByLocation", JSONObject().put("root", "grid").put("folder-a", "icons"))
            .toValidatedBackupPreferences()

        assertEquals("dark", preferences.theme)
        assertEquals("personal", preferences.workspace)
        assertEquals(setOf("1:1", "2:255"), preferences.quranBookmarkedVerses)
        assertEquals(2, preferences.quranRecentLocations.first().surahNumber)
        assertEquals(1, preferences.quranMemorizationRecords.size)
        assertEquals(setOf("folder-a", "folder-b"), preferences.expandedFolderIds)
        assertEquals(mapOf("root" to "grid", "folder-a" to "icons"), preferences.libraryViewModesByLocation)
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
}
