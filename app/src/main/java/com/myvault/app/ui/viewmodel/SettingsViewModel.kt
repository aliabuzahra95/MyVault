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
import com.myvault.app.data.preferences.AzureSpeechSettings
import com.myvault.app.data.sync.DriveSyncResult
import com.myvault.app.data.sync.DriveAuthorizationResult
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: VaultPreferences,
    private val backupRepository: BackupRepository,
    private val storageRepository: StorageRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val googleDriveSyncRepository: GoogleDriveIncrementalSyncRepository,
    private val googleDriveRestoreController: Provider<GoogleDriveRestoreController>,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    supabaseSessionStore: SupabaseSessionStore,
) : ViewModel() {
    val userPreferences: StateFlow<VaultUserPreferences> =
        preferences.userPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VaultUserPreferences(),
        )
    val azureSpeechSettings: StateFlow<AzureSpeechSettings> =
        preferences.azureSpeechSettings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AzureSpeechSettings(),
        )
    private val _storageLabel = MutableStateFlow("Calculating...")
    val storageLabel: StateFlow<String> = _storageLabel
    private val _driveRestoreState = MutableStateFlow(DriveRestoreState())
    val driveRestoreState: StateFlow<DriveRestoreState> = _driveRestoreState
    private var driveRestoreJob: Job? = null
    private var pendingDriveOperation: PendingDriveOperation? = null
    val supabaseSession: StateFlow<SupabaseSession> =
        supabaseSessionStore.session.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SupabaseSession(),
        )
    private val _recentlyDeleted = MutableStateFlow(RecentlyDeletedUiState())
    val recentlyDeleted: StateFlow<RecentlyDeletedUiState> = _recentlyDeleted
    private val _recentlyDeletedLoaded = MutableStateFlow(false)
    val recentlyDeletedLoaded: StateFlow<Boolean> = _recentlyDeletedLoaded
    private var recentlyDeletedJob: Job? = null

    fun observeDriveRestoreState() {
        if (driveRestoreJob != null) return
        driveRestoreJob = viewModelScope.launch {
            googleDriveRestoreController.get().state.collect { state ->
                _driveRestoreState.value = state
            }
        }
    }

    fun observeRecentlyDeleted() {
        if (recentlyDeletedJob != null) return
        _recentlyDeletedLoaded.value = true
        recentlyDeletedJob = viewModelScope.launch {
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
            }.collect { state ->
                _recentlyDeleted.value = state
            }
        }
    }

    fun setTheme(mode: VaultThemeMode) {
        viewModelScope.launch { preferences.setTheme(mode) }
    }

    fun setMaterialYouEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setMaterialYouEnabled(enabled) }
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

    fun setShowFullNoteTitles(show: Boolean) {
        viewModelScope.launch { preferences.setShowFullNoteTitles(show) }
    }

    fun setShowFullFileTitles(show: Boolean) {
        viewModelScope.launch { preferences.setShowFullFileTitles(show) }
    }

    fun setDefaultNoteView(defaultNoteView: String) {
        viewModelScope.launch { preferences.setDefaultNoteView(defaultNoteView) }
    }

    fun setNarrationProvider(provider: String) {
        viewModelScope.launch { preferences.setNarrationProvider(provider) }
    }

    fun setAzureSpeechSettings(apiKey: String, region: String, voice: String, arabicVoice: String) {
        viewModelScope.launch { preferences.setAzureSpeechSettings(apiKey, region, voice, arabicVoice) }
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
                    val missingFilesMessage = if (it.missingAttachmentCount > 0) {
                        ". ${it.missingAttachmentCount} unavailable attachment file(s) were skipped; all other vault data was restored"
                    } else {
                        ""
                    }
                    onComplete("Restore complete: ${it.noteCount} notes, ${it.attachmentCount} attachments$missingFilesMessage")
                }
                .onFailure {
                    onComplete("Restore failed: ${it.message ?: "Unknown error"}")
                }
        }
    }

    fun prepareGoogleDriveSignIn(onReady: (Intent) -> Unit, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { googleDriveSyncRepository.prepareSignInIntent() }
                .onSuccess(onReady)
                .onFailure { onComplete("Could not open Google Drive login: ${it.message ?: "Unknown error"}") }
        }
    }

    fun handleGoogleDriveSignInResult(
        data: Intent?,
        onAuthorizationRequired: (Intent) -> Unit,
        onComplete: (String) -> Unit,
    ) {
        viewModelScope.launch {
            handleDriveAuthorizationResult(
                result = googleDriveSyncRepository.handleSignInResult(data),
                onAuthorizationRequired = onAuthorizationRequired,
                onComplete = onComplete,
            )
        }
    }

    fun handleGoogleDriveConsentResult(granted: Boolean, onComplete: (String) -> Unit) {
        if (!granted) {
            pendingDriveOperation = null
            onComplete("Google Drive permission was not granted. Backup and restore were not started.")
            return
        }
        viewModelScope.launch {
            when (val result = googleDriveSyncRepository.prepareDriveAuthorization()) {
                is DriveAuthorizationResult.Ready -> {
                    val operation = pendingDriveOperation
                    pendingDriveOperation = null
                    if (operation == null) {
                        onComplete(result.message)
                    } else {
                        enqueueDriveOperation(operation, onComplete)
                    }
                }
                is DriveAuthorizationResult.ConsentRequired -> {
                    pendingDriveOperation = null
                    onComplete("Google Drive permission was not completed. Tap Login and approve access, then retry.")
                }
                is DriveAuthorizationResult.Failure -> {
                    pendingDriveOperation = null
                    onComplete(result.message)
                }
            }
        }
    }

    fun pushGoogleDriveSync(onAuthorizationRequired: (Intent) -> Unit, onComplete: (String) -> Unit) {
        authorizeAndStartDriveOperation(PendingDriveOperation.Push, onAuthorizationRequired, onComplete)
    }

    fun forcePushGoogleDriveSync(onAuthorizationRequired: (Intent) -> Unit, onComplete: (String) -> Unit) {
        authorizeAndStartDriveOperation(PendingDriveOperation.ForcePush, onAuthorizationRequired, onComplete)
    }

    fun pullGoogleDriveSync(onAuthorizationRequired: (Intent) -> Unit, onComplete: (String) -> Unit) {
        authorizeAndStartDriveOperation(PendingDriveOperation.Pull, onAuthorizationRequired, onComplete)
    }

    private fun authorizeAndStartDriveOperation(
        operation: PendingDriveOperation,
        onAuthorizationRequired: (Intent) -> Unit,
        onComplete: (String) -> Unit,
    ) {
        pendingDriveOperation = operation
        viewModelScope.launch {
            handleDriveAuthorizationResult(
                result = googleDriveSyncRepository.prepareDriveAuthorization(),
                onAuthorizationRequired = onAuthorizationRequired,
                onComplete = onComplete,
            )
        }
    }

    private fun handleDriveAuthorizationResult(
        result: DriveAuthorizationResult,
        onAuthorizationRequired: (Intent) -> Unit,
        onComplete: (String) -> Unit,
    ) {
        when (result) {
            is DriveAuthorizationResult.Ready -> {
                val operation = pendingDriveOperation
                pendingDriveOperation = null
                if (operation == null) {
                    onComplete(result.message)
                } else {
                    enqueueDriveOperation(operation, onComplete)
                }
            }
            is DriveAuthorizationResult.ConsentRequired -> {
                onComplete(result.message)
                onAuthorizationRequired(result.intent)
            }
            is DriveAuthorizationResult.Failure -> {
                pendingDriveOperation = null
                onComplete(result.message)
            }
        }
    }

    private fun enqueueDriveOperation(operation: PendingDriveOperation, onComplete: (String) -> Unit) {
        observeDriveRestoreState()
        val controller = googleDriveRestoreController.get()
        val callback: (DriveSyncResult) -> Unit = { result ->
            if (operation == PendingDriveOperation.Pull && result is DriveSyncResult.Success) refreshStorage()
            onComplete(result.displayMessage())
        }
        when (operation) {
            PendingDriveOperation.Push -> controller.startPush(callback)
            PendingDriveOperation.ForcePush -> controller.startForcePush(callback)
            PendingDriveOperation.Pull -> controller.startRestore(callback)
        }
    }

    fun dismissDriveRestoreMessage() {
        observeDriveRestoreState()
        googleDriveRestoreController.get().dismissMessage()
    }

    fun checkGoogleDriveUpdates(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            onComplete(googleDriveSyncRepository.checkForRemoteUpdates().displayMessage())
        }
    }

    fun signInFormattingAccount(email: String, password: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            supabaseAuthRepository.signInWithPassword(email, password)
                .onSuccess { onComplete("ChatGPT formatting connected as ${it.email}.") }
                .onFailure { onComplete("ChatGPT formatting login failed: ${it.message ?: "Unknown error"}") }
        }
    }

    fun signOutFormattingAccount(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            supabaseAuthRepository.signOut()
            onComplete("ChatGPT formatting signed out.")
        }
    }

    fun restoreNote(noteId: String) {
        viewModelScope.launch { noteRepository.restoreNote(noteId) }
    }

    fun permanentlyDeleteNote(noteId: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                noteRepository.permanentlyDeleteNote(noteId)
            }.onSuccess {
                refreshStorage()
                onComplete("Item permanently deleted.")
            }.onFailure {
                onComplete("Permanent delete failed: ${it.message ?: "Unknown error"}")
            }
        }
    }

    fun restoreFolder(folderId: String) {
        viewModelScope.launch { folderRepository.restoreFolderTree(folderId) }
    }

    fun permanentlyDeleteFolder(folderId: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                folderRepository.permanentlyDeleteFolderTree(folderId)
            }.onSuccess {
                refreshStorage()
                onComplete("Folder permanently deleted.")
            }.onFailure {
                onComplete("Permanent delete failed: ${it.message ?: "Unknown error"}")
            }
        }
    }

    fun permanentlyDeleteAllRecentlyDeleted(onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                observeRecentlyDeleted()
                val current = recentlyDeleted.value
                current.folders.forEach { folderRepository.permanentlyDeleteFolderTree(it.id) }
                current.notes.forEach { noteRepository.permanentlyDeleteNote(it.id) }
            }.onSuccess {
                refreshStorage()
                onComplete("Recently Deleted was emptied.")
            }.onFailure {
                onComplete("Delete all failed: ${it.message ?: "Unknown error"}")
            }
        }
    }
}

private enum class PendingDriveOperation {
    Push,
    ForcePush,
    Pull,
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
