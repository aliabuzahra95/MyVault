package com.myvault.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.FolderStickyNoteEntity
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.FolderStickyNoteRepository
import com.myvault.app.data.repository.CourseRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val folder: FolderEntity? = null,
    val stickyNotes: List<FolderStickyNoteEntity> = emptyList(),
    val contents: List<VaultTreeItem> = emptyList(),
    val workspace: List<VaultTreeItem> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    seeder: DatabaseSeeder,
    private val folderRepository: FolderRepository,
    private val stickyNoteRepository: FolderStickyNoteRepository,
    private val noteRepository: NoteRepository,
    private val courseRepository: CourseRepository,
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val folderId: String = savedStateHandle.get<String>("folderId").orEmpty()
    private val expansionPrefix = "folder:$folderId:"

    val uiState: StateFlow<FolderUiState> = combine(
        folderRepository.observeFolder(folderId),
        stickyNoteRepository.observeForFolder(folderId),
        folderRepository.observeFolderContents(folderId),
        folderRepository.observeWorkspaceTreeForFolder(folderId),
        vaultPreferences.userPreferences.map { preferences -> preferences.expandedFolderIds },
    ) { folder, stickyNotes, contents, workspace, expandedFolderIds ->
        FolderUiState(
            folder = folder,
            stickyNotes = stickyNotes,
            contents = contents,
            workspace = workspace,
            expandedFolderIds = expandedFolderIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }

    fun createNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(noteRepository.createNote(folderId = folderId))
        }
    }

    fun recordCourseNoteOpened(noteId: String) {
        viewModelScope.launch {
            val mode = uiState.value.folder?.mode.orEmpty()
            if (mode.startsWith("course:")) {
                courseRepository.markNoteOpened(mode.removePrefix("course:"), noteId)
            }
        }
    }

    fun createSubfolder(name: String, description: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(folderRepository.createFolder(parentId = folderId, name = name, description = description))
        }
    }

    fun updateFolderDetails(name: String, description: String?) {
        viewModelScope.launch { folderRepository.updateFolderDetails(folderId, name, description) }
    }

    fun moveCurrentFolder(parentId: String?) {
        viewModelScope.launch { folderRepository.moveFolder(folderId, parentId) }
    }

    fun deleteCurrentFolder(onDeleted: () -> Unit) {
        viewModelScope.launch {
            folderRepository.deleteFolderTree(folderId)
            onDeleted()
        }
    }

    fun moveItemInOrder(itemId: String, type: VaultTreeItemType, direction: Int) {
        viewModelScope.launch { folderRepository.moveTreeItemWithinSiblings(itemId, type, direction) }
    }

    fun setFolderExpanded(id: String, expanded: Boolean) {
        viewModelScope.launch {
            val expandedIds = vaultPreferences.userPreferences.first().expandedFolderIds.toMutableSet()
            val key = "$expansionPrefix$id"
            if (expanded) expandedIds += key else expandedIds -= key
            vaultPreferences.setExpandedFolderIds(expandedIds)
        }
    }

    fun createSubNote(parentNoteId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val parent = noteRepository.getNote(parentNoteId) ?: return@launch
            onCreated(noteRepository.createNote(parent.folderId, "Untitled sub-note", parent.id))
        }
    }

    fun renameNote(noteId: String, title: String) {
        viewModelScope.launch { noteRepository.updateTitle(noteId, title) }
    }

    fun moveNote(noteId: String, targetFolderId: String?) {
        viewModelScope.launch { noteRepository.moveNote(noteId, targetFolderId) }
    }

    fun moveNoteToMode(noteId: String, mode: String) {
        viewModelScope.launch { noteRepository.moveNoteToMode(noteId, mode) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { noteRepository.deleteNote(noteId) }
    }

    fun setNotePinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { noteRepository.setPinned(noteId, pinned) }
    }

    fun setNoteFolderPinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { noteRepository.setFolderPinned(noteId, pinned) }
    }

    fun setNoteFavourite(noteId: String, favourite: Boolean) {
        viewModelScope.launch { noteRepository.setFavourite(noteId, favourite) }
    }

    fun createStickyNote(text: String) {
        viewModelScope.launch { stickyNoteRepository.create(folderId, text) }
    }

    fun updateStickyNote(id: String, text: String) {
        viewModelScope.launch { stickyNoteRepository.update(id, text) }
    }

    fun deleteStickyNote(id: String) {
        viewModelScope.launch { stickyNoteRepository.delete(id) }
    }
}
