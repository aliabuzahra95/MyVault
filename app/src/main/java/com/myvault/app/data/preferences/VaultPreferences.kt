package com.myvault.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myvault.app.ui.theme.VaultThemeMode
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
    val defaultNoteView: String = "reading",
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
    val expandedFolderIds: Set<String> = emptySet(),
    val libraryViewMode: String = "list",
    val libraryViewModesByLocation: Map<String, String> = emptyMap(),
)

@Singleton
class VaultPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
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
                defaultNoteView = preferences[Keys.DefaultNoteView] ?: "reading",
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
                expandedFolderIds = preferences[Keys.ExpandedFolderIds].orEmpty(),
                libraryViewMode = preferences[Keys.LibraryViewMode] ?: "list",
                libraryViewModesByLocation = preferences[Keys.LibraryViewModesByLocation].orEmpty()
                    .mapNotNull { entry ->
                        val separator = entry.indexOf('=')
                        if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                    }
                    .toMap(),
            )
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

    suspend fun setNotePreview(notePreview: String) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.NotePreview] = notePreview
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
        }
    }

    suspend fun setQuranArabicFontPercent(percent: Int) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.QuranArabicFontPercent] = percent.coerceIn(70, 140)
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
        val DefaultNoteView: Preferences.Key<String> = stringPreferencesKey("default_note_view")
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
        val ExpandedFolderIds: Preferences.Key<Set<String>> = stringSetPreferencesKey("expanded_folder_ids")
        val LibraryViewMode: Preferences.Key<String> = stringPreferencesKey("library_view_mode")
        val LibraryViewModesByLocation: Preferences.Key<Set<String>> = stringSetPreferencesKey("library_view_modes_by_location")
    }
}

const val WORKSPACE_PERSONAL = "personal"
const val WORKSPACE_ISLAMIC_CORPUS = "islamic_corpus"
