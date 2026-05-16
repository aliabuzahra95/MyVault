package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.SearchRepository
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.ui.components.SearchResultData
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.screens.AttachmentSample
import com.myvault.app.ui.screens.parseRichImport
import com.myvault.app.ui.screens.toJsonArrayString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val pinnedNotes: List<VaultNoteCardData> = emptyList(),
    val attachments: List<AttachmentSample> = emptyList(),
    val workspace: List<VaultTreeItem> = emptyList(),
    val searchQuery: String = "",
    val searchNotes: List<SearchResultData> = emptyList(),
    val searchFolders: List<FolderEntity> = emptyList(),
    val searchTags: List<String> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val notePreviewLines: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    seeder: DatabaseSeeder,
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val searchRepository: SearchRepository,
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val searchResults = searchQuery.flatMapLatest { query ->
        combine(
            searchRepository.searchNotes(query),
            searchRepository.searchFolders(query),
            searchRepository.searchTags(query),
        ) { notes, folders, tags ->
            Triple(notes, folders, tags)
        }
    }

    private val homeContent = combine(
        noteRepository.observePinnedCards(),
        attachmentRepository.observeAllCards(),
        folderRepository.observeWorkspaceTree(FOLDER_MODE_STUDY),
        folderRepository.observeWorkspaceTree(FOLDER_MODE_PERSONAL),
    ) { pinned, attachments, studyTree, personalTree ->
        HomeContent(
            pinnedNotes = pinned,
            attachments = attachments,
            studyTree = studyTree,
            personalTree = personalTree,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        homeContent,
        searchQuery,
        searchResults,
        vaultPreferences.userPreferences,
    ) { content, query, results, preferences ->
        content.toUiState(
            mode = FOLDER_MODE_STUDY,
            query = query,
            results = results,
            expandedFolderIds = preferences.expandedFolderIds,
            notePreviewLines = preferences.notePreview.toPreviewLines(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val personalUiState: StateFlow<HomeUiState> = combine(
        homeContent,
        searchQuery,
        searchResults,
        vaultPreferences.userPreferences,
    ) { content, query, results, preferences ->
        content.toUiState(
            mode = FOLDER_MODE_PERSONAL,
            query = query,
            results = results,
            expandedFolderIds = preferences.expandedFolderIds,
            notePreviewLines = preferences.notePreview.toPreviewLines(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }

    fun createNote(folderId: String? = null, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(noteRepository.createNote(folderId = folderId))
        }
    }

    fun importDocument(uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val fileName = attachmentRepository.displayName(uri).ifBlank { "Imported file" }
            val noteId = noteRepository.createNote(folderId = null, title = fileName.substringBeforeLast('.'))
            attachmentRepository.attachDocument(noteId, uri)
            val imported = parseRichImport(html = null, plainText = "Imported file: $fileName")
            noteRepository.saveRichText(
                noteId = noteId,
                text = imported.document.text,
                styleMarksJson = imported.document.styleMarks.toJsonArrayString(),
            )
            onImported(noteId)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun createFolder(parentId: String? = null, name: String, mode: String = FOLDER_MODE_STUDY, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(folderRepository.createFolder(parentId = parentId, name = name, mode = mode))
        }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch { folderRepository.renameFolder(folderId, name) }
    }

    fun moveFolder(folderId: String, parentId: String?) {
        viewModelScope.launch { folderRepository.moveFolder(folderId, parentId) }
    }

    fun moveFolderInOrder(folderId: String, direction: Int) {
        viewModelScope.launch { folderRepository.moveFolderWithinSiblings(folderId, direction) }
    }

    fun moveFolderToMode(folderId: String, mode: String) {
        viewModelScope.launch { folderRepository.moveFolderToMode(folderId, mode) }
    }

    fun setFolderExpanded(folderId: String, expanded: Boolean) {
        viewModelScope.launch {
            val folderIds = uiState.value.expandedFolderIds.toMutableSet()
            if (expanded) {
                folderIds += folderId
            } else {
                folderIds -= folderId
            }
            vaultPreferences.setExpandedFolderIds(folderIds)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch { folderRepository.deleteFolderTree(folderId) }
    }

    fun renameNote(noteId: String, title: String) {
        viewModelScope.launch { noteRepository.updateTitle(noteId, title) }
    }

    fun moveNote(noteId: String, folderId: String?) {
        viewModelScope.launch { noteRepository.moveNote(noteId, folderId) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { noteRepository.deleteNote(noteId) }
    }

    fun setNotePinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { noteRepository.setPinned(noteId, pinned) }
    }

    fun setNoteFavourite(noteId: String, favourite: Boolean) {
        viewModelScope.launch { noteRepository.setFavourite(noteId, favourite) }
    }
}

private data class HomeContent(
    val pinnedNotes: List<VaultNoteCardData>,
    val attachments: List<AttachmentSample>,
    val studyTree: List<VaultTreeItem>,
    val personalTree: List<VaultTreeItem>,
)

private fun HomeContent.toUiState(
    mode: String,
    query: String,
    results: Triple<List<SearchResultData>, List<FolderEntity>, List<String>>,
    expandedFolderIds: Set<String>,
    notePreviewLines: Int,
): HomeUiState {
    val tree = if (mode == FOLDER_MODE_PERSONAL) personalTree else studyTree
    val visibleItems = tree.flatMap { it.flattenItems() }
    val visibleNoteIds = visibleItems.filter { it.type == VaultTreeItemType.Note }.map { it.id }.toSet()
    val visibleFolderIds = visibleItems.filter { it.type == VaultTreeItemType.Folder }.map { it.id }.toSet()
    return HomeUiState(
        pinnedNotes = pinnedNotes.filter { it.id in visibleNoteIds },
        attachments = attachments,
        workspace = tree,
        searchQuery = query,
        searchNotes = results.first.filter { it.id in visibleNoteIds },
        searchFolders = results.second.filter { it.id in visibleFolderIds },
        searchTags = results.third,
        expandedFolderIds = expandedFolderIds,
        notePreviewLines = notePreviewLines,
    )
}

private fun VaultTreeItem.flattenItems(): List<VaultTreeItem> =
    listOf(this) + children.flatMap { it.flattenItems() }

private fun String.toPreviewLines(): Int = when (this) {
    "one" -> 1
    "two" -> 2
    else -> 0
}
