package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.LibraryReferencedNote
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.PdfReadingProgressRepository
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.toRelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryViewMode(val storedValue: String, val label: String) {
    List("list", "List"),
    Grid("grid", "Grid"),
    Icons("icons", "Icons");

    companion object {
        fun fromStoredValue(value: String): LibraryViewMode =
            entries.firstOrNull { it.storedValue == value } ?: if (value == "compact") Icons else List
    }
}

data class LibraryFolderItem(
    val id: String,
    val name: String,
    val count: Int,
    val depth: Int = 0,
    val files: List<LibraryFileItem> = emptyList(),
    val annotations: List<LibraryAnnotationItem> = emptyList(),
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
    val pinned: Boolean = false,
    val highlightCount: Int = 0,
    val annotationNoteCount: Int = 0,
)

data class LibraryAnnotationItem(
    val id: String,
    val attachmentId: String,
    val fileName: String,
    val pageIndex: Int,
    val color: String,
    val displayTitle: String?,
    val displayFolderId: String?,
    val notePreview: String,
    val updatedAt: Long,
)

data class LibraryStudyNoteItem(
    val id: String,
    val title: String,
)

data class LibraryUiState(
    val currentFolder: FolderEntity? = null,
    val folders: List<LibraryFolderItem> = emptyList(),
    val files: List<LibraryFileItem> = emptyList(),
    val pinnedFiles: List<LibraryFileItem> = emptyList(),
    val annotations: List<LibraryAnnotationItem> = emptyList(),
    val references: List<LibraryReferencedNote> = emptyList(),
    val attachmentTags: Map<String, List<KnowledgeTagChip>> = emptyMap(),
    val annotationTags: Map<String, List<KnowledgeTagChip>> = emptyMap(),
    val studyNotes: List<LibraryStudyNoteItem> = emptyList(),
    val continueReading: LibraryFileItem? = null,
    val recentFiles: List<LibraryFileItem> = emptyList(),
    val allFolders: List<LibraryFolderItem> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val viewMode: LibraryViewMode = LibraryViewMode.List,
    val importing: Boolean = false,
    val importMessage: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val attachmentRepository: AttachmentRepository,
    private val noteRepository: NoteRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val folderId: String? = savedStateHandle["libraryFolderId"]
    private val viewModeLocationKey = folderId ?: LIBRARY_ROOT_VIEW_MODE_KEY
    private val importState = MutableStateFlow(LibraryImportState())
    private val pdfLayer = combine(
        pdfReadingProgressRepository.observeAll(),
        pdfAnnotationRepository.observeAll(),
    ) { progress, annotations -> progress to annotations }
    private val libraryDataLayer = combine(
        folderRepository.observeLibraryFolders(),
        attachmentRepository.observeLibraryFiles(),
        if (folderId == null) attachmentRepository.observeRootLibraryFiles() else attachmentRepository.observeForLibraryFolder(folderId),
    ) { folders, allFiles, currentFiles ->
        LibraryDataLayer(folders, allFiles, currentFiles)
    }
    private val preferencesAndImportState = combine(
        vaultPreferences.userPreferences,
        importState,
    ) { preferences, importing -> preferences to importing }
    private val knowledgeLayer = combine(
        knowledgeRepository.observeLibraryReferences(),
        knowledgeRepository.observeTagsByTargetType(KnowledgeRepository.TargetAttachment),
        knowledgeRepository.observeTagsByTargetType(KnowledgeRepository.TargetAnnotation),
        noteRepository.observeAllNotes(),
    ) { references, attachmentTags, annotationTags, studyNotes ->
        LibraryKnowledgeLayer(references, attachmentTags, annotationTags, studyNotes.map { LibraryStudyNoteItem(id = it.id, title = it.title) })
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryDataLayer,
        pdfLayer,
        preferencesAndImportState,
        knowledgeLayer,
    ) { libraryData, pdfLayer, preferencesAndImporting, knowledge ->
        val (folders, allFiles, currentFiles) = libraryData
        val (progress, annotations) = pdfLayer
        val (preferences, importing) = preferencesAndImporting
        val libraryFolders = folders.filter { it.mode == FOLDER_MODE_LIBRARY }
        val progressByAttachment = progress.associateBy { it.attachmentId }
        val activeFiles = allFiles.filter { it.deletedAt == null }
        val attachmentsById = activeFiles.associateBy { it.id }
        val annotationStatsByAttachment = annotations
            .filter { it.attachmentId in attachmentsById }
            .groupBy { it.attachmentId }
            .mapValues { (_, items) ->
                LibraryAnnotationStats(
                    highlightCount = items.size,
                    annotationNoteCount = items.count { !it.noteText.isNullOrBlank() },
                )
            }
        val annotationItemsByFolder = annotations
            .filter { !it.noteText.isNullOrBlank() }
            .mapNotNull { annotation ->
                attachmentsById[annotation.attachmentId]?.let { attachment ->
                    (annotation.displayFolderId ?: annotation.libraryFolderId) to annotation.toLibraryAnnotationItem(attachment)
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, items) -> items.sortedByDescending { it.updatedAt } }
        val filesByFolder = activeFiles
            .filter { it.deletedAt == null }
            .groupBy { it.libraryFolderId }
            .mapValues { (_, files) ->
                files.map { it.toLibraryFileItem(progressByAttachment[it.id], annotationStatsByAttachment[it.id]) }
                    .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenBy { it.name.lowercase() })
            }
        val fileCounts = activeFiles
            .mapNotNull { it.libraryFolderId }
            .groupingBy { it }
            .eachCount()
        val annotationCounts = annotationItemsByFolder
            .mapNotNull { (folderId, items) -> folderId?.let { it to items.size } }
            .toMap()
        val foldersByParent = libraryFolders.groupBy { it.parentId }
        val currentFolder = folderId?.let { id -> libraryFolders.firstOrNull { it.id == id } }
        val visibleFolders = foldersByParent[folderId].orEmpty()
            .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
            .map { it.toLibraryFolderItem(foldersByParent, fileCounts, annotationCounts, filesByFolder, annotationItemsByFolder, depth = 0) }

        val currentFileItems = currentFiles
            .map { it.toLibraryFileItem(progressByAttachment[it.id], annotationStatsByAttachment[it.id]) }
            .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenByDescending { it.meta })
        val continueReadingItems = (if (folderId == null) allFiles else currentFiles)
            .map { it.toLibraryFileItem(progressByAttachment[it.id], annotationStatsByAttachment[it.id]) }
        val currentFileIds = (if (folderId == null) allFiles else currentFiles).map { it.id }.toSet()
        val currentAnnotationIds = annotationItemsByFolder[folderId].orEmpty().map { it.id }.toSet()

        LibraryUiState(
            currentFolder = currentFolder,
            folders = visibleFolders,
            files = currentFileItems,
            pinnedFiles = allFiles
                .filter { it.isPinned }
                .map { it.toLibraryFileItem(progressByAttachment[it.id], annotationStatsByAttachment[it.id]) }
                .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenByDescending { it.meta }),
            annotations = annotations
                .filter {
                    !it.noteText.isNullOrBlank() &&
                        if (folderId == null) {
                            it.displayFolderId == null || it.attachmentId in currentFileIds
                        } else {
                            it.displayFolderId == folderId ||
                                (it.displayFolderId == null && it.attachmentId in currentFileIds)
                        }
                }
                .mapNotNull { annotation ->
                    attachmentsById[annotation.attachmentId]?.let { attachment ->
                        annotation.toLibraryAnnotationItem(attachment)
                    }
                }
                .sortedByDescending { it.updatedAt },
            references = knowledge.references
                .filter { it.attachmentId in currentFileIds }
                .sortedBy { it.noteTitle.lowercase() },
            attachmentTags = knowledge.attachmentTags,
            annotationTags = knowledge.annotationTags.filterKeys { it in currentAnnotationIds },
            studyNotes = knowledge.studyNotes,
            continueReading = continueReadingItems
                .filter { it.mimeType == "application/pdf" && it.pageCount.orZero() > 0 }
                .maxByOrNull { it.lastOpenedAt },
            recentFiles = allFiles
                .map { it.toLibraryFileItem(progressByAttachment[it.id], annotationStatsByAttachment[it.id]) }
                .filter { it.lastOpenedAt > 0L }
                .sortedByDescending { it.lastOpenedAt }
                .take(8),
            allFolders = libraryFolders
                .filter { it.parentId == null }
                .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
                .map { it.toLibraryFolderItem(foldersByParent, fileCounts, annotationCounts, filesByFolder, annotationItemsByFolder, depth = 0) },
            expandedFolderIds = preferences.expandedFolderIds,
            viewMode = LibraryViewMode.fromStoredValue(
                preferences.libraryViewModesByLocation[viewModeLocationKey] ?: preferences.libraryViewMode,
            ),
            importing = importing.active,
            importMessage = importing.message,
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
        viewModelScope.launch { vaultPreferences.setLibraryViewMode(viewModeLocationKey, mode.storedValue) }
    }

    fun importFile(uri: Uri, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            onImported(attachmentRepository.importLibraryDocument(folderId, uri))
        }
    }

    fun importFiles(uris: List<Uri>, onImported: (String) -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importState.value = LibraryImportState(active = true, message = "Importing ${uris.size} file${if (uris.size == 1) "" else "s"}...")
            val result = attachmentRepository.importLibraryDocuments(folderId, uris)
            result.importedIds.firstOrNull()?.let(onImported)
            val message = when {
                result.importedIds.isEmpty() && result.failedCount > 0 -> "Import failed for ${result.failedCount} file${if (result.failedCount == 1) "" else "s"}."
                result.failedCount > 0 -> "Imported ${result.importedIds.size}; skipped ${result.failedCount}."
                else -> "Imported ${result.importedIds.size} file${if (result.importedIds.size == 1) "" else "s"}."
            }
            importState.value = LibraryImportState(active = false, message = message)
        }
    }

    fun clearImportMessage() {
        importState.update { it.copy(message = null) }
    }

    fun renameFile(fileId: String, name: String) {
        viewModelScope.launch { attachmentRepository.renameAttachment(fileId, name) }
    }

    fun moveFile(fileId: String, folderId: String?) {
        viewModelScope.launch { attachmentRepository.moveLibraryAttachment(fileId, folderId) }
    }

    fun setFilePinned(fileId: String, pinned: Boolean) {
        viewModelScope.launch { attachmentRepository.setPinned(fileId, pinned) }
    }

    fun renameAnnotation(annotationId: String, title: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateDisplayTitle(annotationId, title) }
    }

    fun moveAnnotation(annotationId: String, folderId: String?) {
        viewModelScope.launch { pdfAnnotationRepository.updateDisplayFolder(annotationId, folderId) }
    }

    fun deleteAnnotationNote(annotationId: String) {
        viewModelScope.launch { pdfAnnotationRepository.deleteNoteOnly(annotationId) }
    }

    fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch { pdfAnnotationRepository.delete(annotationId) }
    }

    fun linkAnnotationToStudyNote(annotationId: String, noteId: String) {
        viewModelScope.launch { knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotationId) }
    }

    fun addAttachmentTag(fileId: String, name: String) {
        viewModelScope.launch { knowledgeRepository.addTag(KnowledgeRepository.TargetAttachment, fileId, name) }
    }

    fun removeAttachmentTag(fileId: String, tagId: String) {
        viewModelScope.launch { knowledgeRepository.removeTag(KnowledgeRepository.TargetAttachment, fileId, tagId) }
    }

    fun addAnnotationTag(annotationId: String, name: String) {
        viewModelScope.launch { knowledgeRepository.addTag(KnowledgeRepository.TargetAnnotation, annotationId, name) }
    }

    fun removeAnnotationTag(annotationId: String, tagId: String) {
        viewModelScope.launch { knowledgeRepository.removeTag(KnowledgeRepository.TargetAnnotation, annotationId, tagId) }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch { attachmentRepository.deleteAttachment(fileId) }
    }
}

private fun FolderEntity.toLibraryFolderItem(
    foldersByParent: Map<String?, List<FolderEntity>>,
    fileCounts: Map<String, Int>,
    annotationCounts: Map<String, Int>,
    filesByFolder: Map<String?, List<LibraryFileItem>>,
    annotationsByFolder: Map<String?, List<LibraryAnnotationItem>>,
    depth: Int,
): LibraryFolderItem {
    val children = foldersByParent[id].orEmpty()
        .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
        .map { it.toLibraryFolderItem(foldersByParent, fileCounts, annotationCounts, filesByFolder, annotationsByFolder, depth + 1) }
    return LibraryFolderItem(
        id = id,
        name = name,
        count = fileCounts[id].orZero() + annotationCounts[id].orZero() + children.sumOf { it.count },
        depth = depth,
        files = filesByFolder[id].orEmpty(),
        annotations = annotationsByFolder[id].orEmpty(),
        children = children,
    )
}

private fun AttachmentEntity.toLibraryFileItem(
    progress: PdfReadingProgressEntity?,
    annotationStats: LibraryAnnotationStats?,
): LibraryFileItem =
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
        pinned = isPinned,
        highlightCount = annotationStats?.highlightCount.orZero(),
        annotationNoteCount = annotationStats?.annotationNoteCount.orZero(),
    )

private data class LibraryAnnotationStats(
    val highlightCount: Int,
    val annotationNoteCount: Int,
)

private fun PdfAnnotationEntity.toLibraryAnnotationItem(attachment: AttachmentEntity): LibraryAnnotationItem =
    LibraryAnnotationItem(
        id = id,
        attachmentId = attachmentId,
        fileName = attachment.fileName,
        pageIndex = pageIndex,
        color = color,
        displayTitle = displayTitle,
        displayFolderId = displayFolderId,
        notePreview = noteText.orEmpty(),
        updatedAt = updatedAt,
    )

private fun Int?.orZero(): Int = this ?: 0

private data class LibraryImportState(
    val active: Boolean = false,
    val message: String? = null,
)

private data class LibraryDataLayer(
    val folders: List<FolderEntity>,
    val allFiles: List<AttachmentEntity>,
    val currentFiles: List<AttachmentEntity>,
)

private data class LibraryKnowledgeLayer(
    val references: List<LibraryReferencedNote>,
    val attachmentTags: Map<String, List<KnowledgeTagChip>>,
    val annotationTags: Map<String, List<KnowledgeTagChip>>,
    val studyNotes: List<LibraryStudyNoteItem>,
)

private const val LIBRARY_ROOT_VIEW_MODE_KEY = "root"
