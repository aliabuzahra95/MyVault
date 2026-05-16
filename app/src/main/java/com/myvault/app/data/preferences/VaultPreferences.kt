package com.myvault.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val lastCloudBackupAt: Long = 0L,
    val expandedFolderIds: Set<String> = emptySet(),
)

@Singleton
class VaultPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    val userPreferences: Flow<VaultUserPreferences> =
        context.vaultDataStore.data.map { preferences ->
            VaultUserPreferences(
                theme = VaultThemeMode.fromStoredValue(preferences[Keys.Theme]),
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
                lastCloudBackupAt = preferences[Keys.LastCloudBackupAt] ?: 0L,
                expandedFolderIds = preferences[Keys.ExpandedFolderIds].orEmpty(),
            )
        }

    suspend fun setTheme(theme: VaultThemeMode) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.Theme] = theme.storedValue
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

    suspend fun markCloudBackupNow() {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.LastCloudBackupAt] = System.currentTimeMillis()
        }
    }

    suspend fun setExpandedFolderIds(folderIds: Set<String>) {
        context.vaultDataStore.edit { preferences ->
            preferences[Keys.ExpandedFolderIds] = folderIds
        }
    }

    private object Keys {
        val Theme: Preferences.Key<String> = stringPreferencesKey("theme")
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
        val LastCloudBackupAt: Preferences.Key<Long> = longPreferencesKey("last_cloud_backup_at")
        val ExpandedFolderIds: Preferences.Key<Set<String>> = stringSetPreferencesKey("expanded_folder_ids")
    }
}
