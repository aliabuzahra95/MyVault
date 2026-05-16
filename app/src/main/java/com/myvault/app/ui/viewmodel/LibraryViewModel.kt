package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.PdfReadingProgressRepository
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.toRelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryViewMode(val storedValue: String, val label: String) {
    List("list", "List"),
    Grid("grid", "Grid"),
    Compact("compact", "Compact");

    companion object {
        fun fromStoredValue(value: String): LibraryViewMode =
            entries.firstOrNull { it.storedValue == value } ?: List
    }
}

data class LibraryFolderItem(
    val id: String,
    val name: String,
    val count: Int,
    val depth: Int = 0,
    val files: List<LibraryFileItem> = emptyList(),
    val children: List<LibraryFolderItem> = emptyList(),
)

data class LibraryFileItem(
    val id: String,
    val name: String,
    val kind: String,
    val size: String,
    val meta: String,
    val mimeType: String,
    val localPath: String,
    val pageIndex: Int? = null,
    val pageCount: Int? = null,
    val progressPercent: Float? = null,
    val lastOpenedAt: Long = 0L,
)

data class LibraryUiState(
    val currentFolder: FolderEntity? = null,
    val folders: List<LibraryFolderItem> = emptyList(),
    val files: List<LibraryFileItem> = emptyList(),
    val continueReading: LibraryFileItem? = null,
    val allFolders: List<LibraryFolderItem> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val viewMode: LibraryViewMode = LibraryViewMode.List,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val attachmentRepository: AttachmentRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val folderId: String? = savedStateHandle["libraryFolderId"]

    val uiState: StateFlow<LibraryUiState> = combine(
        folderRepository.observeLibraryFolders(),
        attachmentRepository.observeLibraryFiles(),
        if (folderId == null) attachmentRepository.observeRootLibraryFiles() else attachmentRepository.observeForLibraryFolder(folderId),
        pdfReadingProgressRepository.observeAll(),
        vaultPreferences.userPreferences,
    ) { folders, allFiles, currentFiles, progress, preferences ->
        val libraryFolders = folders.filter { it.mode == FOLDER_MODE_LIBRARY }
        val progressByAttachment = progress.associateBy { it.attachmentId }
        val filesByFolder = allFiles
            .filter { it.deletedAt == null }
            .groupBy { it.libraryFolderId }
            .mapValues { (_, files) ->
                files.map { it.toLibraryFileItem(progressByAttachment[it.id]) }
                    .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenBy { it.name.lowercase() })
            }
        val fileCounts = allFiles
            .filter { it.deletedAt == null }
            .mapNotNull { it.libraryFolderId }
            .groupingBy { it }
            .eachCount()
        val currentFolder = folderId?.let { id -> libraryFolders.firstOrNull { it.id == id } }
        val visibleFolders = libraryFolders
            .filter { it.parentId == folderId }
            .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
            .map { it.toLibraryFolderItem(libraryFolders, fileCounts, filesByFolder, depth = 0) }

        val currentFileItems = currentFiles
            .map { it.toLibraryFileItem(progressByAttachment[it.id]) }
            .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenByDescending { it.meta })
        val continueReadingItems = (if (folderId == null) allFiles else currentFiles)
            .map { it.toLibraryFileItem(progressByAttachment[it.id]) }

        LibraryUiState(
            currentFolder = currentFolder,
            folders = visibleFolders,
            files = currentFileItems,
            continueReading = continueReadingItems
                .filter { it.mimeType == "application/pdf" && it.pageCount.orZero() > 0 }
                .maxByOrNull { it.lastOpenedAt },
            allFolders = libraryFolders
                .filter { it.parentId == null }
                .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
                .map { it.toLibraryFolderItem(libraryFolders, fileCounts, filesByFolder, depth = 0) },
            expandedFolderIds = preferences.expandedFolderIds,
            viewMode = LibraryViewMode.fromStoredValue(preferences.libraryViewMode),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun createFolder(parentId: String? = folderId, name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            onCreated(folderRepository.createFolder(parentId = parentId, name = name, mode = FOLDER_MODE_LIBRARY))
        }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch { folderRepository.renameFolder(folderId, name) }
    }

    fun moveFolder(folderId: String, parentId: String?) {
        viewModelScope.launch { folderRepository.moveFolder(folderId, parentId) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch { folderRepository.deleteFolderTree(folderId) }
    }

    fun setFolderExpanded(folderId: String, expanded: Boolean) {
        viewModelScope.launch {
            val folderIds = uiState.value.expandedFolderIds.toMutableSet()
            if (expanded) folderIds += folderId else folderIds -= folderId
            vaultPreferences.setExpandedFolderIds(folderIds)
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { vaultPreferences.setLibraryViewMode(mode.storedValue) }
    }

    fun importFile(uri: Uri, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            onImported(attachmentRepository.importLibraryDocument(folderId, uri))
        }
    }
}

private fun FolderEntity.toLibraryFolderItem(
    allFolders: List<FolderEntity>,
    fileCounts: Map<String, Int>,
    filesByFolder: Map<String?, List<LibraryFileItem>>,
    depth: Int,
): LibraryFolderItem {
    val children = allFolders
        .filter { it.parentId == id }
        .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
        .map { it.toLibraryFolderItem(allFolders, fileCounts, filesByFolder, depth + 1) }
    return LibraryFolderItem(
        id = id,
        name = name,
        count = fileCounts[id].orZero() + children.sumOf { it.count },
        depth = depth,
        files = filesByFolder[id].orEmpty(),
        children = children,
    )
}

private fun AttachmentEntity.toLibraryFileItem(progress: PdfReadingProgressEntity?): LibraryFileItem =
    LibraryFileItem(
        id = id,
        name = fileName,
        kind = kindLabel(),
        size = sizeLabel(),
        meta = if (progress != null) {
            "Opened ${progress.lastOpenedAt.toRelativeTime()}"
        } else {
            "Added ${createdAt.toRelativeTime()}"
        },
        mimeType = mimeType,
        localPath = localPath,
        pageIndex = progress?.pageIndex,
        pageCount = progress?.pageCount,
        progressPercent = progress?.progressPercent,
        lastOpenedAt = progress?.lastOpenedAt ?: 0L,
    )

private fun Int?.orZero(): Int = this ?: 0
