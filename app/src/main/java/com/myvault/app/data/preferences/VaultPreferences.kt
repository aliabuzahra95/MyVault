package com.myvault.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.data.narration.AzureNarrationConfig
import com.myvault.app.data.narration.NarrationProvider
import com.myvault.app.data.quran.QuranRecentLocation
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.toMemorizationRecordOrNull
import com.myvault.app.data.quran.memorization.toPreferenceEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vaultDataStore by preferencesDataStore(name = "vault_preferences")

data class VaultUserPreferences(
    val theme: VaultThemeMode = VaultThemeMode.Auto,
    val workspace: String = WORKSPACE_ISLAMIC_CORPUS,
    val accentColor: String = "#5B8DEF",
    val fontSize: String = "medium",
    val dashboardFontSize: String = "medium",
    val noteFontSize: String = "medium",
    val notePreview: String = "off",
    val showFullNoteTitles: Boolean = false,
    val showFullFileTitles: Boolean = false,
    val defaultNoteView: String = "reading",
    val narrationProvider: String = NarrationProvider.Device.storedValue,
    val autoTagSuggestions: Boolean = true,
    val securityLockEnabled: Boolean = false,
    val securityLockTimeoutMs: Long = 30_000L,
    val lastLocalBackupAt: Long = 0L,
    val googleDriveAccountEmail: String = "",
    val lastGoogleDriveSyncAt: Long = 0L,
    val lastGoogleDriveManifestAt: Long = 0L,
    val quranLastReadSurah: Int = 1,
    val quranLastReadAyah: Int = 1,
    val quranArabicFontPercent: Int = 100,
    val quranTranslationFontPercent: Int = 100,
    val quranTranslationEnabled: Boolean = true,
    val quranTajweedEnabled: Boolean = false,
    val quranTafsirSourceId: Int = -1,
    val quranAudioReciterId: Int = 0,
    val quranAudioPlaybackSpeed: Float = 1f,
    val quranBookmarkedVerses: Set<String> = emptySet(),
    val quranRecentLocations: List<QuranRecentLocation> = emptyList(),
    val quranMemorizationRecords: List<MemorizationRecord> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val libraryViewMode: String = "list",
    val libraryViewModesByLocation: Map<String, String> = emptyMap(),
)

data class AzureSpeechSettings(
    val apiKey: String = "",
    val region: String = AzureNarrationConfig.DEFAULT_REGION,
    val voice: String = AzureNarrationConfig.DEFAULT_VOICE,
    val arabicVoice: String = AzureNarrationConfig.DEFAULT_ARABIC_VOICE,
)

@Singleton
class VaultPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val startupCache = context.getSharedPreferences("vault_startup_preferences", Context.MODE_PRIVATE)

    val userPreferences: Flow<VaultUserPreferences> =
        context.vaultDataStore.data.map { preferences ->
            VaultUserPreferences(
                theme = VaultThemeMode.fromStoredValue(preferences[Keys.Theme]),
                workspace = preferences[Keys.Workspace] ?: WORKSPACE_ISLAMIC_CORPUS,
                accentColor = preferences[Keys.AccentColor] ?: "#5B8DEF",
                fontSize = preferences[Keys.FontSize] ?: "medium",
                dashboardFontSize = preferences[Keys.DashboardFontSize] ?: preferences[Keys.FontSize] ?: "medium",
                noteFontSize = preferences[Keys.NoteFontSize] ?: preferences[Keys.FontSize] ?: "medium",
                notePreview = preferences[Keys.NotePreview] ?: "off",
                showFullNoteTitles = preferences[Keys.ShowFullNoteTitles] ?: false,
                showFullFileTitles = preferences[Keys.ShowFullFileTitles] ?: false,
                defaultNoteView = preferences[Keys.DefaultNoteView] ?: "reading",
                narrationProvider = preferences[Keys.NarrationProvider] ?: NarrationProvider.Device.storedValue,
                autoTagSuggestions = preferences[Keys.AutoTagSuggestions] ?: true,
                securityLockEnabled = preferences[Keys.SecurityLockEnabled] ?: false,
                securityLockTimeoutMs = preferences[Keys.SecurityLockTimeoutMs] ?: 30_000L,
                lastLocalBackupAt = preferences[Keys.LastLocalBackupAt] ?: 0L,
                googleDriveAccountEmail = preferences[Keys.GoogleDriveAccountEmail].orEmpty(),
                lastGoogleDriveSyncAt = preferences[Keys.LastGoogleDriveSyncAt] ?: 0L,
                lastGoogleDriveManifestAt = preferences[Keys.LastGoogleDriveManifestAt] ?: 0L,
                quranLastReadSurah = preferences[Keys.QuranLastReadSurah] ?: 1,
                quranLastReadAyah = preferences[Keys.QuranLastReadAyah] ?: 1,
                quranArabicFontPercent = preferences[Keys.QuranArabicFontPercent] ?: 100,
                quranTranslationFontPercent = preferences[Keys.QuranTranslationFontPercent] ?: 100,
                quranTranslationEnabled = preferences[Keys.QuranTranslationEnabled] ?: true,
                quranTajweedEnabled = preferences[Keys.QuranTajweedEnabled] ?: false,
                quranTafsirSourceId = preferences[Keys.QuranTafsirSourceId] ?: -1,
                quranAudioReciterId = preferences[Keys.QuranAudioReciterId] ?: 0,
                quranAudioPlaybackSpeed = preferences[Keys.QuranAudioPlaybackSpeed] ?: 1f,
                quranBookmarkedVerses = preferences[Keys.QuranBookmarkedVerses].orEmpty(),
                quranRecentLocations = preferences[Keys.QuranRecentLocations].orEmpty().toQuranRecentLocations(),
                quranMemorizationRecords = preferences[Keys.QuranMemorizationRecords].orEmpty()
                    .mapNotNull { it.toMemorizationRecordOrNull() },
                expandedFolderIds = preferences[Keys.ExpandedFolderIds].orEmpty(),
                libraryViewMode = preferences[Keys.LibraryViewMode] ?: "list",
                libraryViewModesByLocation = preferences[Keys.LibraryViewModesByLocation].orEmpty()
                    .mapNotNull { entry ->
                        val separator = entry.indexOf('=')
                        if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                    }
                    .toMap(),
            ).also(::cacheStartupPreferences)
        }

    val azureSpeechSettings: Flow<AzureSpeechSettings> =
        context.vaultDataStore.data.map { preferences ->
            val legacyVoice = preferences[Keys.AzureSpeechVoice]
            AzureSpeechSettings(
                apiKey = preferences[Keys.AzureSpeechApiKey].orEmpty(),
                region = preferences[Keys.AzureSpeechRegion] ?: AzureNarrationConfig.DEFAULT_REGION,
                voice = legacyVoice
                    ?.takeIf { it in AzureNarrationConfig.EnglishVoiceOptions }
                    ?: AzureNarrationConfig.DEFAULT_VOICE,
                arabicVoice = preferences[Keys.AzureSpeechArabicVoice]
                    ?: legacyVoice?.takeIf { it in AzureNarrationConfig.ArabicVoiceOptions }
                    ?: AzureNarrationConfig.DEFAULT_ARABIC_VOICE,
            )
        }

    fun cachedStartupPreferences(): VaultUserPreferences =
        VaultUserPreferences(
            dashboardFontSize = startupCache.getString(Keys.CachedDashboardFontSize, null) ?: "medium",
        )

    private fun cacheStartupPreferences(preferences: VaultUserPreferences) {
        startupCache.edit()
            .putString(Keys.CachedDashboardFontSize, preferences.dashboardFontSize)
            .apply()
    }

    suspend fun setTheme(theme: VaultThemeMode) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.Theme] = theme.storedValue
        }
    }

    suspend fun setWorkspace(workspace: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.Workspace] = workspace
        }
    }

    suspend fun setAccentColor(accentColor: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.AccentColor] = accentColor
        }
    }

    suspend fun setFontSize(fontSize: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.FontSize] = fontSize
        }
    }

    suspend fun setDashboardFontSize(fontSize: String) {
        startupCache.edit().putString(Keys.CachedDashboardFontSize, fontSize).apply()
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.DashboardFontSize] = fontSize
        }
    }

    suspend fun setNoteFontSize(fontSize: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.NoteFontSize] = fontSize
        }
    }

    suspend fun setDefaultNoteView(defaultNoteView: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.DefaultNoteView] = defaultNoteView
        }
    }

    suspend fun setNarrationProvider(provider: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.NarrationProvider] = NarrationProvider.fromStoredValue(provider).storedValue
        }
    }

    suspend fun setAzureSpeechSettings(apiKey: String, region: String, voice: String, arabicVoice: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.AzureSpeechApiKey] = apiKey.trim()
            preferences[Keys.AzureSpeechRegion] = region.trim().lowercase().ifBlank { AzureNarrationConfig.DEFAULT_REGION }
            preferences[Keys.AzureSpeechVoice] = voice.trim().ifBlank { AzureNarrationConfig.DEFAULT_VOICE }
            preferences[Keys.AzureSpeechArabicVoice] = arabicVoice.trim().ifBlank { AzureNarrationConfig.DEFAULT_ARABIC_VOICE }
        }
    }

    suspend fun setNotePreview(notePreview: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.NotePreview] = notePreview
        }
    }

    suspend fun setShowFullNoteTitles(show: Boolean) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.ShowFullNoteTitles] = show
        }
    }

    suspend fun setShowFullFileTitles(show: Boolean) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.ShowFullFileTitles] = show
        }
    }

    suspend fun setSecurityLockEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.SecurityLockEnabled] = enabled
        }
    }

    suspend fun setSecurityLockTimeout(timeoutMs: Long) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.SecurityLockTimeoutMs] = timeoutMs
        }
    }

    suspend fun markLocalBackupNow() {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.LastLocalBackupAt] = System.currentTimeMillis()
        }
    }

    suspend fun setGoogleDriveAccountEmail(email: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.GoogleDriveAccountEmail] = email
        }
    }

    suspend fun markGoogleDriveSync(cloudManifestAt: Long) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.LastGoogleDriveSyncAt] = System.currentTimeMillis()
            preferences[Keys.LastGoogleDriveManifestAt] = cloudManifestAt
        }
    }

    suspend fun setQuranReadingPosition(surahNumber: Int, ayahNumber: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranLastReadSurah] = surahNumber.coerceAtLeast(1)
            preferences[Keys.QuranLastReadAyah] = ayahNumber.coerceAtLeast(1)
            val updated = preferences[Keys.QuranRecentLocations].orEmpty()
                .toQuranRecentLocations()
                .updatedWith(surahNumber = surahNumber, ayahNumber = ayahNumber)
            preferences[Keys.QuranRecentLocations] = updated.toPreferenceSet()
        }
    }

    suspend fun setQuranArabicFontPercent(percent: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranArabicFontPercent] = percent.coerceIn(70, 140)
        }
    }

    suspend fun setQuranTranslationFontPercent(percent: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranTranslationFontPercent] = percent.coerceIn(80, 130)
        }
    }

    suspend fun setQuranTranslationEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranTranslationEnabled] = enabled
        }
    }

    suspend fun setQuranTajweedEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranTajweedEnabled] = enabled
        }
    }

    suspend fun setQuranTafsirSourceId(sourceId: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranTafsirSourceId] = sourceId
        }
    }

    suspend fun setQuranAudioReciterId(reciterId: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranAudioReciterId] = reciterId.coerceAtLeast(0)
        }
    }

    suspend fun setQuranAudioPlaybackSpeed(speed: Float) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranAudioPlaybackSpeed] = speed.coerceIn(0.5f, 2f)
        }
    }

    suspend fun setQuranBookmarkedVerses(verseKeys: Set<String>) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranBookmarkedVerses] = verseKeys
        }
    }

    suspend fun restoreBackedUpPreferences(backup: VaultBackupPreferences) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.Theme] = backup.theme
            preferences[Keys.Workspace] = backup.workspace
            preferences[Keys.AccentColor] = backup.accentColor
            preferences[Keys.FontSize] = backup.fontSize
            preferences[Keys.DashboardFontSize] = backup.dashboardFontSize
            preferences[Keys.NoteFontSize] = backup.noteFontSize
            preferences[Keys.NotePreview] = backup.notePreview
            preferences[Keys.DefaultNoteView] = backup.defaultNoteView
            preferences[Keys.AutoTagSuggestions] = backup.autoTagSuggestions
            preferences[Keys.SecurityLockEnabled] = backup.securityLockEnabled
            preferences[Keys.SecurityLockTimeoutMs] = backup.securityLockTimeoutMs
            preferences[Keys.QuranLastReadSurah] = backup.quranLastReadSurah.coerceAtLeast(1)
            preferences[Keys.QuranLastReadAyah] = backup.quranLastReadAyah.coerceAtLeast(1)
            preferences[Keys.QuranArabicFontPercent] = backup.quranArabicFontPercent.coerceIn(70, 140)
            preferences[Keys.QuranTranslationFontPercent] = backup.quranTranslationFontPercent.coerceIn(80, 130)
            preferences[Keys.QuranTranslationEnabled] = backup.quranTranslationEnabled
            preferences[Keys.QuranTajweedEnabled] = backup.quranTajweedEnabled
            preferences[Keys.QuranTafsirSourceId] = backup.quranTafsirSourceId
            preferences[Keys.QuranAudioReciterId] = backup.quranAudioReciterId.coerceAtLeast(0)
            preferences[Keys.QuranAudioPlaybackSpeed] = backup.quranAudioPlaybackSpeed.coerceIn(0.5f, 2f)
            preferences[Keys.QuranBookmarkedVerses] = backup.quranBookmarkedVerses
            preferences[Keys.QuranRecentLocations] = backup.quranRecentLocations.toPreferenceSet()
            preferences[Keys.QuranMemorizationRecords] = backup.quranMemorizationRecords.map { it.toPreferenceEntry() }.toSet()
            preferences[Keys.ExpandedFolderIds] = backup.expandedFolderIds
            preferences[Keys.LibraryViewMode] = backup.libraryViewMode
            preferences[Keys.LibraryViewModesByLocation] = backup.libraryViewModesByLocation
                .map { (key, value) -> "$key=$value" }
                .toSet()
        }
    }

    suspend fun setQuranMemorizationRecords(records: List<MemorizationRecord>) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranMemorizationRecords] = records.map { it.toPreferenceEntry() }.toSet()
        }
    }

    suspend fun setExpandedFolderIds(folderIds: Set<String>) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.ExpandedFolderIds] = folderIds
        }
    }

    suspend fun setLibraryViewMode(mode: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.LibraryViewMode] = mode
        }
    }

    suspend fun setLibraryViewMode(locationKey: String, mode: String) {
        context.vaultDataStore.edit { preferences ->
            val updated = preferences[Keys.LibraryViewModesByLocation].orEmpty()
                .mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                }
                .toMap()
                .toMutableMap()
                .apply { this[locationKey] = mode }
            preferences[Keys.LibraryViewModesByLocation] = updated.map { (key, value) -> "$key=$value" }.toSet()
        }
    }

    private object Keys {
        val Theme: Preferences.Key<String> = stringPreferencesKey("theme")
        val Workspace: Preferences.Key<String> = stringPreferencesKey("workspace")
        val AccentColor: Preferences.Key<String> = stringPreferencesKey("accent_color")
        val FontSize: Preferences.Key<String> = stringPreferencesKey("font_size")
        val DashboardFontSize: Preferences.Key<String> = stringPreferencesKey("dashboard_font_size")
        val NoteFontSize: Preferences.Key<String> = stringPreferencesKey("note_font_size")
        val NotePreview: Preferences.Key<String> = stringPreferencesKey("note_preview")
        val ShowFullNoteTitles: Preferences.Key<Boolean> = booleanPreferencesKey("show_full_note_titles")
        val ShowFullFileTitles: Preferences.Key<Boolean> = booleanPreferencesKey("show_full_file_titles")
        val DefaultNoteView: Preferences.Key<String> = stringPreferencesKey("default_note_view")
        val NarrationProvider: Preferences.Key<String> = stringPreferencesKey("narration_provider")
        val AzureSpeechApiKey: Preferences.Key<String> = stringPreferencesKey("azure_speech_api_key")
        val AzureSpeechRegion: Preferences.Key<String> = stringPreferencesKey("azure_speech_region")
        val AzureSpeechVoice: Preferences.Key<String> = stringPreferencesKey("azure_speech_voice")
        val AzureSpeechArabicVoice: Preferences.Key<String> = stringPreferencesKey("azure_speech_arabic_voice")
        val AutoTagSuggestions: Preferences.Key<Boolean> = booleanPreferencesKey("auto_tag_suggestions")
        val SecurityLockEnabled: Preferences.Key<Boolean> = booleanPreferencesKey("security_lock_enabled")
        val SecurityLockTimeoutMs: Preferences.Key<Long> = longPreferencesKey("security_lock_timeout_ms")
        val LastLocalBackupAt: Preferences.Key<Long> = longPreferencesKey("last_local_backup_at")
        val GoogleDriveAccountEmail: Preferences.Key<String> = stringPreferencesKey("google_drive_account_email")
        val LastGoogleDriveSyncAt: Preferences.Key<Long> = longPreferencesKey("last_google_drive_sync_at")
        val LastGoogleDriveManifestAt: Preferences.Key<Long> = longPreferencesKey("last_google_drive_manifest_at")
        val QuranLastReadSurah: Preferences.Key<Int> = intPreferencesKey("quran_last_read_surah")
        val QuranLastReadAyah: Preferences.Key<Int> = intPreferencesKey("quran_last_read_ayah")
        val QuranArabicFontPercent: Preferences.Key<Int> = intPreferencesKey("quran_arabic_font_percent")
        val QuranTranslationFontPercent: Preferences.Key<Int> = intPreferencesKey("quran_translation_font_percent")
        val QuranTranslationEnabled: Preferences.Key<Boolean> = booleanPreferencesKey("quran_translation_enabled")
        val QuranTajweedEnabled: Preferences.Key<Boolean> = booleanPreferencesKey("quran_tajweed_enabled")
        val QuranTafsirSourceId: Preferences.Key<Int> = intPreferencesKey("quran_tafsir_source_id")
        val QuranAudioReciterId: Preferences.Key<Int> = intPreferencesKey("quran_audio_reciter_id")
        val QuranAudioPlaybackSpeed: Preferences.Key<Float> = floatPreferencesKey("quran_audio_playback_speed")
        val QuranBookmarkedVerses: Preferences.Key<Set<String>> = stringSetPreferencesKey("quran_bookmarked_verses")
        val QuranRecentLocations: Preferences.Key<Set<String>> = stringSetPreferencesKey("quran_recent_locations")
        val QuranMemorizationRecords: Preferences.Key<Set<String>> = stringSetPreferencesKey("quran_memorization_records")
        val ExpandedFolderIds: Preferences.Key<Set<String>> = stringSetPreferencesKey("expanded_folder_ids")
        val LibraryViewMode: Preferences.Key<String> = stringPreferencesKey("library_view_mode")
        val LibraryViewModesByLocation: Preferences.Key<Set<String>> = stringSetPreferencesKey("library_view_modes_by_location")
        const val CachedDashboardFontSize: String = "dashboard_font_size"
    }
}

const val WORKSPACE_PERSONAL = "personal"
const val WORKSPACE_ISLAMIC_CORPUS = "islamic_corpus"

data class VaultBackupPreferences(
    val theme: String,
    val workspace: String,
    val accentColor: String,
    val fontSize: String,
    val dashboardFontSize: String,
    val noteFontSize: String,
    val notePreview: String,
    val defaultNoteView: String,
    val autoTagSuggestions: Boolean,
    val securityLockEnabled: Boolean,
    val securityLockTimeoutMs: Long,
    val quranLastReadSurah: Int,
    val quranLastReadAyah: Int,
    val quranArabicFontPercent: Int,
    val quranTranslationFontPercent: Int,
    val quranTranslationEnabled: Boolean,
    val quranTajweedEnabled: Boolean,
    val quranTafsirSourceId: Int = -1,
    val quranAudioReciterId: Int,
    val quranAudioPlaybackSpeed: Float,
    val quranBookmarkedVerses: Set<String>,
    val quranRecentLocations: List<QuranRecentLocation>,
    val quranMemorizationRecords: List<MemorizationRecord> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val libraryViewMode: String = "list",
    val libraryViewModesByLocation: Map<String, String> = emptyMap(),
)

private fun Set<String>.toQuranRecentLocations(): List<QuranRecentLocation> =
    mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 3) return@mapNotNull null
        QuranRecentLocation(
            surahNumber = parts[0].toIntOrNull()?.coerceAtLeast(1) ?: return@mapNotNull null,
            ayahNumber = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return@mapNotNull null,
            lastReadAt = parts[2].toLongOrNull()?.coerceAtLeast(0L) ?: return@mapNotNull null,
        )
    }
        .sortedByDescending { it.lastReadAt }
        .take(QURAN_RECENT_LIMIT)

private fun List<QuranRecentLocation>.updatedWith(surahNumber: Int, ayahNumber: Int): List<QuranRecentLocation> =
    (
        listOf(
            QuranRecentLocation(
                surahNumber = surahNumber.coerceAtLeast(1),
                ayahNumber = ayahNumber.coerceAtLeast(1),
                lastReadAt = System.currentTimeMillis(),
            ),
        ) + filterNot { it.surahNumber == surahNumber }
    )
        .sortedByDescending { it.lastReadAt }
        .take(QURAN_RECENT_LIMIT)

private fun List<QuranRecentLocation>.toPreferenceSet(): Set<String> =
    take(QURAN_RECENT_LIMIT)
        .map { "${it.surahNumber}:${it.ayahNumber}:${it.lastReadAt}" }
        .toSet()

private const val QURAN_RECENT_LIMIT = 5
