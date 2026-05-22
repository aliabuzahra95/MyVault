package com.myvault.app.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.repository.BackupRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.StorageRepository
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.sync.DriveSyncResult
import com.myvault.app.data.sync.DriveRestoreState
import com.myvault.app.data.sync.GoogleDriveIncrementalSyncRepository
import com.myvault.app.data.sync.GoogleDriveRestoreController
import com.myvault.app.data.supabase.SupabaseAuthRepository
import com.myvault.app.data.supabase.SupabaseSession
import com.myvault.app.data.supabase.SupabaseSessionStore
import com.myvault.app.ui.theme.VaultThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: VaultPreferences,
    private val backupRepository: BackupRepository,
    private val storageRepository: StorageRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val googleDriveSyncRepository: GoogleDriveIncrementalSyncRepository,
    private val googleDriveRestoreController: GoogleDriveRestoreController,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    supabaseSessionStore: SupabaseSessionStore,
) : ViewModel() {
    val userPreferences: StateFlow<VaultUserPreferences> =
        preferences.userPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VaultUserPreferences(),
        )
    private val _storageLabel = MutableStateFlow("Calculating...")
    val storageLabel: StateFlow<String> = _storageLabel
    val driveRestoreState: StateFlow<DriveRestoreState> = googleDriveRestoreController.state
    val supabaseSession: StateFlow<SupabaseSession> =
        supabaseSessionStore.session.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SupabaseSession(),
        )
    val recentlyDeleted: StateFlow<RecentlyDeletedUiState> =
        combine(
            noteRepository.observeDeletedNotes(),
            folderRepository.observeDeletedFolders(),
            folderRepository.observeAllFoldersIncludingDeleted(),
        ) { notes, deletedFolders, allFolders ->
            val allFoldersById = allFolders.associateBy { it.id }
            RecentlyDeletedUiState(
                notes = notes
                    .filterNot { note -> note.folderId.hasDeletedFolderAncestor(allFoldersById) }
                    .map { DeletedItemUiState(it.id, it.title, "Note", it.deletedAt ?: it.updatedAt) },
                folders = deletedFolders
                    .filterNot { folder -> folder.parentId.hasDeletedFolderAncestor(allFoldersById) }
                    .map { DeletedItemUiState(it.id, it.name, "Folder", it.deletedAt ?: it.updatedAt) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentlyDeletedUiState())

    init {
        refreshStorage()
    }

    fun setTheme(mode: VaultThemeMode) {
        viewModelScope.launch { preferences.setTheme(mode) }
    }

    fun setWorkspace(workspace: String) {
        viewModelScope.launch { preferences.setWorkspace(workspace) }
    }

    fun setAccentColor(accentColor: String) {
        viewModelScope.launch { preferences.setAccentColor(accentColor) }
    }

    fun setFontSize(fontSize: String) {
        viewModelScope.launch { preferences.setFontSize(fontSize) }
    }

    fun setDashboardFontSize(fontSize: String) {
        viewModelScope.launch { preferences.setDashboardFontSize(fontSize) }
    }

    fun setNoteFontSize(fontSize: String) {
        viewModelScope.launch { preferences.setNoteFontSize(fontSize) }
    }

    fun setNotePreview(notePreview: String) {
        viewModelScope.launch { preferences.setNotePreview(notePreview) }
    }

    fun setDefaultNoteView(defaultNoteView: String) {
        viewModelScope.launch { preferences.setDefaultNoteView(defaultNoteView) }
    }

    fun setSecurityLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSecurityLockEnabled(enabled) }
    }

    fun setSecurityLockTimeout(timeoutMs: Long) {
        viewModelScope.launch { preferences.setSecurityLockTimeout(timeoutMs) }
    }

    fun refreshStorage() {
        viewModelScope.launch { _storageLabel.value = storageRepository.vaultStorageLabel() }
    }

    fun exportBackup(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { backupRepository.exportBackup(uri) }
                .onSuccess {
                    preferences.markLocalBackupNow()
                    onComplete("Backup saved: ${it.noteCount} notes, ${it.attachmentCount} attachments")
                }
                .onFailure {
                    onComplete("Backup failed: ${it.message ?: "Unknown error"}")
                }
        }
    }

    fun restoreBackup(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { backupRepository.restoreBackup(uri) }
                .onSuccess {
                    refreshStorage()
                    onComplete("Restore complete: ${it.noteCount} notes, ${it.attachmentCount} attachments")
                }
                .onFailure {
                    onComplete("Restore failed: ${it.message ?: "Unknown error"}")
                }
        }
    }

    fun googleDriveSignInIntent(): Intent =
        googleDriveSyncRepository.signInIntent()

    fun handleGoogleDriveSignInResult(data: Intent?, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            onComplete(googleDriveSyncRepository.handleSignInResult(data).displayMessage())
        }
    }

    fun pushGoogleDriveSync(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            onComplete(googleDriveSyncRepository.pushToDrive().displayMessage())
        }
    }

    fun pullGoogleDriveSync(onComplete: (String) -> Unit) {
        googleDriveRestoreController.startRestore { result ->
            if (result is DriveSyncResult.Success) refreshStorage()
            onComplete(result.displayMessage())
        }
    }

    fun dismissDriveRestoreMessage() {
        googleDriveRestoreController.dismissMessage()
    }

    fun checkGoogleDriveUpdates(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            onComplete(googleDriveSyncRepository.checkForRemoteUpdates().displayMessage())
        }
    }

    fun signInSupabaseAi(email: String, password: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            supabaseAuthRepository.signInWithPassword(email, password)
                .onSuccess { onComplete("ChatGPT AI connected as ${it.email}.") }
                .onFailure { onComplete("ChatGPT AI login failed: ${it.message ?: "Unknown error"}") }
        }
    }

    fun signOutSupabaseAi(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            supabaseAuthRepository.signOut()
            onComplete("ChatGPT AI signed out.")
        }
    }

    fun restoreNote(noteId: String) {
        viewModelScope.launch { noteRepository.restoreNote(noteId) }
    }

    fun permanentlyDeleteNote(noteId: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                backupRepository.createSafetyBackup("before-permanent-delete-note")
                noteRepository.permanentlyDeleteNote(noteId)
            }.onSuccess {
                refreshStorage()
                onComplete("Item permanently deleted. A safety backup was saved first.")
            }.onFailure {
                onComplete("Permanent delete stopped: ${it.message ?: "Unable to create safety backup first."}")
            }
        }
    }

    fun restoreFolder(folderId: String) {
        viewModelScope.launch { folderRepository.restoreFolderTree(folderId) }
    }

    fun permanentlyDeleteFolder(folderId: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                backupRepository.createSafetyBackup("before-permanent-delete-folder")
                folderRepository.permanentlyDeleteFolderTree(folderId)
            }.onSuccess {
                refreshStorage()
                onComplete("Folder permanently deleted. A safety backup was saved first.")
            }.onFailure {
                onComplete("Permanent delete stopped: ${it.message ?: "Unable to create safety backup first."}")
            }
        }
    }

    fun permanentlyDeleteAllRecentlyDeleted(onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                backupRepository.createSafetyBackup("before-empty-recently-deleted")
                val current = recentlyDeleted.value
                current.folders.forEach { folderRepository.permanentlyDeleteFolderTree(it.id) }
                current.notes.forEach { noteRepository.permanentlyDeleteNote(it.id) }
            }.onSuccess {
                refreshStorage()
                onComplete("Recently Deleted was emptied. A safety backup was saved first.")
            }.onFailure {
                onComplete("Delete all stopped: ${it.message ?: "Unable to create safety backup first."}")
            }
        }
    }
}

data class RecentlyDeletedUiState(
    val notes: List<DeletedItemUiState> = emptyList(),
    val folders: List<DeletedItemUiState> = emptyList(),
)

data class DeletedItemUiState(
    val id: String,
    val title: String,
    val kind: String,
    val deletedAt: Long,
)

private fun DriveSyncResult.displayMessage(): String =
    when (this) {
        is DriveSyncResult.Success -> message
        is DriveSyncResult.Conflict -> message
        is DriveSyncResult.Skipped -> message
        is DriveSyncResult.Failure -> message
    }

private fun String?.hasDeletedFolderAncestor(
    foldersById: Map<String, com.myvault.app.data.local.entity.FolderEntity>,
): Boolean {
    var currentId = this
    val seen = mutableSetOf<String>()
    while (currentId != null && currentId !in seen) {
        seen += currentId
        val folder = foldersById[currentId] ?: return false
        if (folder.deletedAt != null) return true
        currentId = folder.parentId
    }
    return false
}
