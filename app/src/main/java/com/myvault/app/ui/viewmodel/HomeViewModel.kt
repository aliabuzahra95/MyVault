package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.FolderStickyNoteRepository
import com.myvault.app.data.repository.HomeSnapshotRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.SearchRepository
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.QuranReflectionRepository
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.QuranReflectionSummary
import com.myvault.app.data.quran.QURAN_REFLECTION_FOLDER_NAME
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
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
    val searchAttachments: List<AttachmentSample> = emptyList(),
    val searchTags: List<String> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val notePreviewLines: Int = 0,
    val showFullNoteTitles: Boolean = false,
    val pinnedExpanded: Boolean = false,
    val quranReflectionSummary: QuranReflectionSummary = QuranReflectionSummary(),
    val quranReflectionItems: List<QuranReflectionItem> = emptyList(),
)

private val EmptySearchResults = Triple(
    emptyList<SearchResultData>(),
    emptyList<FolderEntity>(),
    emptyList<String>(),
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    seeder: DatabaseSeeder,
    private val folderRepository: FolderRepository,
    private val stickyNoteRepository: FolderStickyNoteRepository,
    private val noteRepository: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val searchRepository: SearchRepository,
    private val vaultPreferences: VaultPreferences,
    private val quranReflectionRepository: QuranReflectionRepository,
    private val homeSnapshotRepository: HomeSnapshotRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val debouncedSearchQuery = searchQuery
        .debounce(300)
        .distinctUntilChanged()

    private val searchResults = debouncedSearchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            flowOf(EmptySearchResults)
        } else {
            combine(
                searchRepository.searchNotes(query),
                searchRepository.searchFolders(query),
                searchRepository.searchTags(query),
            ) { notes, folders, tags ->
                Triple(notes, folders, tags)
            }
        }
    }

    private fun homeContentForMode(mode: String) = combine(
        noteRepository.observePinnedCards(),
        attachmentRepository.observeCardsForMode(mode),
        folderRepository.observeWorkspaceTree(mode),
    ) { pinned, attachments, tree ->
        HomeContent(
            pinnedNotes = pinned,
            attachments = attachments,
            tree = if (mode == FOLDER_MODE_STUDY) tree.withoutQuranReflectionFolder() else tree,
        )
    }

    private val quranReflectionHomeState = combine(
        quranReflectionRepository.observeReflectionSummary(),
        quranReflectionRepository.observeReflectionItems(),
    ) { summary, items -> summary to items }

    val uiState: StateFlow<HomeUiState> = combine(
        homeContentForMode(FOLDER_MODE_STUDY),
        searchQuery,
        searchResults,
        vaultPreferences.userPreferences,
        quranReflectionHomeState,
    ) { content, query, results, preferences, quranReflectionHomeState ->
        val (quranReflectionSummary, quranReflectionItems) = quranReflectionHomeState
        content.toUiState(
            query = query,
            results = results,
            expandedFolderIds = preferences.expandedFolderIds,
            notePreviewLines = preferences.notePreview.toPreviewLines(),
            showFullNoteTitles = preferences.showFullNoteTitles,
            pinnedExpanded = preferences.pinnedExpandedByMode[FOLDER_MODE_STUDY] ?: false,
            quranReflectionSummary = quranReflectionSummary,
            quranReflectionItems = quranReflectionItems,
        )
    }
        .onEach { state -> homeSnapshotRepository.save(FOLDER_MODE_STUDY, state) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            homeSnapshotRepository.load(FOLDER_MODE_STUDY) ?: HomeUiState(),
        )

    val personalUiState: StateFlow<HomeUiState> = combine(
        homeContentForMode(FOLDER_MODE_PERSONAL),
        searchQuery,
        searchResults,
        vaultPreferences.userPreferences,
    ) { content, query, results, preferences ->
        content.toUiState(
            query = query,
            results = results,
            expandedFolderIds = preferences.expandedFolderIds,
            notePreviewLines = preferences.notePreview.toPreviewLines(),
            showFullNoteTitles = preferences.showFullNoteTitles,
            pinnedExpanded = preferences.pinnedExpandedByMode[FOLDER_MODE_PERSONAL] ?: false,
            quranReflectionSummary = QuranReflectionSummary(),
            quranReflectionItems = emptyList(),
        )
    }
        .onEach { state -> homeSnapshotRepository.save(FOLDER_MODE_PERSONAL, state) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            homeSnapshotRepository.load(FOLDER_MODE_PERSONAL) ?: HomeUiState(),
        )

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }

    fun createNote(folderId: String? = null, mode: String = FOLDER_MODE_STUDY, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val targetFolderId = folderId ?: if (mode == FOLDER_MODE_PERSONAL) {
                folderRepository.ensureRootFolderForMode(name = "Inbox", mode = FOLDER_MODE_PERSONAL)
            } else {
                null
            }
            onCreated(noteRepository.createNote(folderId = targetFolderId))
        }
    }

    fun createNoteFromSharedText(
        text: String,
        mode: String = FOLDER_MODE_STUDY,
        onCreated: (String) -> Unit,
    ) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val targetFolderId = if (mode == FOLDER_MODE_PERSONAL) {
                folderRepository.ensureRootFolderForMode(name = "Inbox", mode = FOLDER_MODE_PERSONAL)
            } else {
                null
            }
            val noteId = noteRepository.createNote(
                folderId = targetFolderId,
                title = cleaned.firstTitleLine(),
            )
            noteRepository.saveRichText(noteId = noteId, text = cleaned, styleMarksJson = "[]")
            onCreated(noteId)
        }
    }

    fun importDocument(uri: Uri, mode: String = FOLDER_MODE_STUDY, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val fileName = attachmentRepository.displayName(uri).ifBlank { "Imported file" }
            val targetFolderId = if (mode == FOLDER_MODE_PERSONAL) {
                folderRepository.ensureRootFolderForMode(name = "Inbox", mode = FOLDER_MODE_PERSONAL)
            } else {
                null
            }
            val noteId = noteRepository.createNote(folderId = targetFolderId, title = fileName.substringBeforeLast('.'))
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

    fun createFolder(
        parentId: String? = null,
        name: String,
        mode: String = FOLDER_MODE_STUDY,
        description: String? = null,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            onCreated(folderRepository.createFolder(parentId = parentId, name = name, mode = mode, description = description))
        }
    }

    fun updateFolderDetails(folderId: String, name: String, description: String?) {
        viewModelScope.launch { folderRepository.updateFolderDetails(folderId, name, description) }
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

    fun setPinnedExpanded(mode: String, expanded: Boolean) {
        viewModelScope.launch { vaultPreferences.setPinnedExpanded(mode, expanded) }
    }

    fun setFolderExpanded(folderId: String, expanded: Boolean) {
        viewModelScope.launch {
            val folderIds = vaultPreferences.userPreferences.first().expandedFolderIds.toMutableSet()
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

    fun createSubNote(parentNoteId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val parent = noteRepository.getNote(parentNoteId) ?: return@launch
            onCreated(
                noteRepository.createNote(
                    folderId = parent.folderId,
                    title = "Untitled sub-note",
                    parentNoteId = parent.id,
                ),
            )
        }
    }

    fun moveNote(noteId: String, folderId: String?) {
        viewModelScope.launch { noteRepository.moveNote(noteId, folderId) }
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

    fun createStickyNote(folderId: String, text: String) {
        viewModelScope.launch { stickyNoteRepository.create(folderId, text) }
    }
}

private data class HomeContent(
    val pinnedNotes: List<VaultNoteCardData>,
    val attachments: List<AttachmentSample>,
    val tree: List<VaultTreeItem>,
)

private fun List<VaultTreeItem>.withoutQuranReflectionFolder(): List<VaultTreeItem> =
    filterNot { item ->
        item.type == VaultTreeItemType.Folder &&
            item.name.equals(QURAN_REFLECTION_FOLDER_NAME, ignoreCase = true)
    }.map { item -> item.copy(children = item.children.withoutQuranReflectionFolder()) }

private fun String.firstTitleLine(): String {
    val line = lines()
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("[#*_`>\\-]+"), "")
        ?.trim()
        .orEmpty()
    return line.ifBlank { "Untitled note" }.take(56)
}

private fun HomeContent.toUiState(
    query: String,
    results: Triple<List<SearchResultData>, List<FolderEntity>, List<String>>,
    expandedFolderIds: Set<String>,
    notePreviewLines: Int,
    showFullNoteTitles: Boolean,
    pinnedExpanded: Boolean,
    quranReflectionSummary: QuranReflectionSummary,
    quranReflectionItems: List<QuranReflectionItem>,
): HomeUiState {
    val visibleIds = tree.visibleTreeIds()
    return HomeUiState(
        pinnedNotes = pinnedNotes.filter { it.id in visibleIds.noteIds },
        attachments = attachments,
        workspace = tree,
        searchQuery = query,
        searchNotes = results.first.filter { it.id in visibleIds.noteIds },
        searchFolders = results.second.filter { it.id in visibleIds.folderIds },
        searchAttachments = attachments.filter {
            query.isNotBlank() &&
                (it.name.contains(query, ignoreCase = true) || it.note.contains(query, ignoreCase = true))
        }.take(5),
        searchTags = results.third,
        expandedFolderIds = expandedFolderIds,
        notePreviewLines = notePreviewLines,
        showFullNoteTitles = showFullNoteTitles,
        pinnedExpanded = pinnedExpanded,
        quranReflectionSummary = quranReflectionSummary,
        quranReflectionItems = quranReflectionItems.take(8),
    )
}

private data class VisibleTreeIds(
    val noteIds: Set<String>,
    val folderIds: Set<String>,
)

private fun List<VaultTreeItem>.visibleTreeIds(): VisibleTreeIds {
    val noteIds = LinkedHashSet<String>()
    val folderIds = LinkedHashSet<String>()
    forEach { item -> item.collectVisibleIds(noteIds, folderIds) }
    return VisibleTreeIds(noteIds = noteIds, folderIds = folderIds)
}

private fun VaultTreeItem.collectVisibleIds(noteIds: MutableSet<String>, folderIds: MutableSet<String>) {
    when (type) {
        VaultTreeItemType.Note -> noteIds += id
        VaultTreeItemType.Folder -> folderIds += id
    }
    children.forEach { it.collectVisibleIds(noteIds, folderIds) }
}

private fun String.toPreviewLines(): Int = when (this) {
    "one" -> 1
    "two" -> 2
    else -> 0
}
