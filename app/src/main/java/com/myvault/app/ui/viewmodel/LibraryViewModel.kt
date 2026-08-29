package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.local.entity.isCurrentPdfAnnotation
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.LibraryReferencedNote
import com.myvault.app.data.repository.LibrarySnapshotRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.PdfReadingProgressRepository
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.toRelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    val colorKey: String? = null,
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
    val annotationType: String,
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
    val studyNotesLoading: Boolean = false,
    val continueReading: LibraryFileItem? = null,
    val recentFiles: List<LibraryFileItem> = emptyList(),
    val allFolders: List<LibraryFolderItem> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val viewMode: LibraryViewMode = LibraryViewMode.List,
    val importing: Boolean = false,
    val importMessage: String? = null,
    val duplicatePdfImport: PdfDuplicateImportUiState? = null,
)

data class PdfDuplicateImportUiState(
    val fileName: String,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val attachmentRepository: AttachmentRepository,
    private val noteRepository: NoteRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val vaultPreferences: VaultPreferences,
    private val librarySnapshotRepository: LibrarySnapshotRepository,
) : ViewModel() {
    private val folderId: String? = savedStateHandle["libraryFolderId"]
    private val initialLibraryMode: String = savedStateHandle["libraryMode"] ?: FOLDER_MODE_LIBRARY
    private val hasExplicitInitialMode: Boolean = savedStateHandle.contains("libraryMode")
    private val requestedLibraryMode = MutableStateFlow(initialLibraryMode)
    private val secondaryLibraryReady = MutableStateFlow(false)
    private val libraryMode: String get() = requestedLibraryMode.value
    private val isPersonalLibrary: Boolean get() = libraryMode == FOLDER_MODE_PERSONAL_LIBRARY
    private val viewModeLocationKey: String get() = "$libraryMode:${folderId ?: LIBRARY_ROOT_VIEW_MODE_KEY}"
    private val importState = MutableStateFlow(LibraryImportState())
    private val pdfLayer = secondaryLibraryReady.flatMapLatest { ready ->
        if (ready) {
            combine(
                pdfReadingProgressRepository.observeAll(),
                pdfAnnotationRepository.observeAll(),
            ) { progress, annotations -> progress to annotations }
        } else {
            flowOf(emptyList<PdfReadingProgressEntity>() to emptyList<PdfAnnotationEntity>())
        }
    }
    private val libraryDataLayer = combine(
        folderRepository.observeLibraryFolders(),
        attachmentRepository.observeLibraryFiles(),
    ) { folders, allFiles ->
        LibraryDataLayer(folders, allFiles)
    }
    private val preferencesAndImportState = combine(
        vaultPreferences.userPreferences,
        importState,
    ) { preferences, importing -> preferences to importing }
    private val knowledgeLayer = secondaryLibraryReady.flatMapLatest { ready ->
        if (ready) {
            combine(
                knowledgeRepository.observeLibraryReferences(),
                knowledgeRepository.observeTagsByTargetType(KnowledgeRepository.TargetAttachment),
                knowledgeRepository.observeTagsByTargetType(KnowledgeRepository.TargetAnnotation),
            ) { references, attachmentTags, annotationTags ->
                LibraryKnowledgeLayer(references, attachmentTags, annotationTags, emptyList(), studyNotesLoading = false)
            }
        } else {
            flowOf(LibraryKnowledgeLayer(emptyList(), emptyMap(), emptyMap(), emptyList(), studyNotesLoading = false))
        }
    }
    private val studyNoteLinks = MutableStateFlow(LibraryStudyNoteLinks())
    private val librarySupportLayer = combine(knowledgeLayer, studyNoteLinks) { knowledge, studyNotes ->
        knowledge.copy(studyNotes = studyNotes.items, studyNotesLoading = studyNotes.loading)
    }
    private var studyNoteLinksJob: Job? = null
    private var secondaryHydrationJob: Job? = null

    private val _uiState = MutableStateFlow(
        if (hasExplicitInitialMode || folderId != null) {
            snapshotFor(initialLibraryMode) ?: LibraryUiState()
        } else {
            LibraryUiState()
        },
    )
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val liveUiState = combine(
        libraryDataLayer,
        pdfLayer,
        preferencesAndImportState,
        librarySupportLayer,
        requestedLibraryMode,
    ) { libraryData, pdfLayer, preferencesAndImporting, knowledge, libraryMode ->
        val isPersonalLibrary = libraryMode == FOLDER_MODE_PERSONAL_LIBRARY
        val (folders, allFiles) = libraryData
        val (progress, annotations) = pdfLayer
        val (preferences, importing) = preferencesAndImporting
        val modeFolders = folders.filter { it.mode == libraryMode }
        val internalPersonalRoot = modeFolders.firstOrNull {
            it.parentId == null && it.name == PERSONAL_LIBRARY_ROOT_FOLDER_NAME
        }
        val rootParentId = if (isPersonalLibrary) internalPersonalRoot?.id else null
        val currentParentId = folderId ?: rootParentId
        val libraryFolders = if (isPersonalLibrary) {
            modeFolders.filter { it.id != internalPersonalRoot?.id }
        } else {
            modeFolders
        }
        val modeFolderIds = modeFolders.map { it.id }.toSet()
        val progressByAttachment = progress.associateBy { it.attachmentId }
        val activeFiles = allFiles
            .filter { it.deletedAt == null }
            .filter { file ->
                if (isPersonalLibrary) {
                    file.libraryFolderId in modeFolderIds
                } else {
                    file.libraryFolderId == null || file.libraryFolderId in modeFolderIds
                }
            }
        val attachmentsById = activeFiles.associateBy { it.id }
        val currentAnnotations = annotations.filter { it.isCurrentPdfAnnotation() }
        val annotationStatsByAttachment = currentAnnotations
            .filter { it.attachmentId in attachmentsById }
            .groupBy { it.attachmentId }
            .mapValues { (_, items) ->
                LibraryAnnotationStats(
                    highlightCount = items.count { it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT },
                    annotationNoteCount = items.count { !it.noteText.isNullOrBlank() },
                )
            }
        val fileItemsById = activeFiles.associate { file ->
            file.id to file.toLibraryFileItem(progressByAttachment[file.id], annotationStatsByAttachment[file.id])
        }
        val annotationItemsByFolder = currentAnnotations
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
                files.mapNotNull { fileItemsById[it.id] }
                    .sortedWith(compareBy<LibraryFileItem> { it.name.lowercase() }.thenBy { it.id })
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
        val visibleFolders = foldersByParent[currentParentId].orEmpty()
            .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
            .map { it.toLibraryFolderItem(foldersByParent, fileCounts, annotationCounts, filesByFolder, annotationItemsByFolder, depth = 0) }

        val currentFiles = activeFiles.filter { it.libraryFolderId == currentParentId }
        val currentFileItems = currentFiles
            .mapNotNull { fileItemsById[it.id] }
            .sortedWith(compareBy<LibraryFileItem> { it.name.lowercase() }.thenBy { it.id })
        val continueReadingItems = (if (folderId == null) activeFiles else currentFiles)
            .mapNotNull { fileItemsById[it.id] }
        val currentFileIds = (if (folderId == null) activeFiles else currentFiles).map { it.id }.toSet()
        val currentAnnotationIds = annotationItemsByFolder[currentParentId].orEmpty().map { it.id }.toSet()

        LibraryUiState(
            currentFolder = currentFolder,
            folders = visibleFolders,
            files = currentFileItems,
            pinnedFiles = activeFiles
                .filter { it.isPinned }
                .mapNotNull { fileItemsById[it.id] }
                .sortedWith(compareByDescending<LibraryFileItem> { it.lastOpenedAt }.thenByDescending { it.meta }),
            annotations = currentAnnotations
                .filter {
                    if (folderId == null) {
                        (it.displayFolderId == null && it.attachmentId in currentFileIds) ||
                            it.displayFolderId == currentParentId ||
                            it.attachmentId in currentFileIds
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
            studyNotesLoading = knowledge.studyNotesLoading,
            continueReading = continueReadingItems
                .filter { it.mimeType == "application/pdf" && it.pageCount.orZero() > 0 }
                .maxByOrNull { it.lastOpenedAt },
            recentFiles = activeFiles
                .mapNotNull { fileItemsById[it.id] }
                .filter { it.lastOpenedAt > 0L }
                .sortedByDescending { it.lastOpenedAt }
                .take(5),
            allFolders = libraryFolders
                .filter { it.parentId == rootParentId }
                .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
                .map { it.toLibraryFolderItem(foldersByParent, fileCounts, annotationCounts, filesByFolder, annotationItemsByFolder, depth = 0) },
            expandedFolderIds = preferences.expandedFolderIds,
            viewMode = LibraryViewMode.fromStoredValue(
                preferences.libraryViewModesByLocation[viewModeLocationKey] ?: preferences.libraryViewMode,
            ),
            importing = importing.active,
            importMessage = importing.message,
            duplicatePdfImport = importing.duplicatePdf?.let { PdfDuplicateImportUiState(fileName = it.fileName) },
        )
    }

    init {
        viewModelScope.launch {
            pdfAnnotationRepository.cleanupGenuinelyInvalidAnnotations()
        }
        viewModelScope.launch {
            liveUiState.collect { state ->
                _uiState.value = state
                if (secondaryLibraryReady.value) {
                    librarySnapshotRepository.save(libraryMode, folderId, state)
                } else {
                    scheduleSecondaryHydration()
                }
            }
        }
    }

    fun setLibraryMode(mode: String) {
        val previousMode = requestedLibraryMode.value
        if (previousMode != mode) {
            secondaryLibraryReady.value = false
            secondaryHydrationJob?.cancel()
            secondaryHydrationJob = null
        }
        requestedLibraryMode.value = mode
        val snapshot = snapshotFor(mode)
        if (snapshot != null) {
            _uiState.value = snapshot
        } else if (previousMode != mode) {
            _uiState.value = LibraryUiState()
        }
    }

    private fun scheduleSecondaryHydration() {
        if (secondaryHydrationJob?.isActive == true || secondaryLibraryReady.value) return
        secondaryHydrationJob = viewModelScope.launch {
            delay(450L)
            secondaryLibraryReady.value = true
        }
    }

    fun createFolder(parentId: String? = folderId, name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val targetParentId = parentId ?: personalRootFolderIdIfNeeded()
            onCreated(folderRepository.createFolder(parentId = targetParentId, name = name, mode = libraryMode))
        }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch { folderRepository.renameFolder(folderId, name) }
    }

    fun updateFolderColor(folderId: String, colorKey: String?) {
        viewModelScope.launch { folderRepository.updateFolderColor(folderId, colorKey) }
    }

    fun moveFolder(folderId: String, parentId: String?) {
        viewModelScope.launch { folderRepository.moveFolder(folderId, parentId ?: personalRootFolderIdIfNeeded()) }
    }

    fun moveFolderInOrder(folderId: String, direction: Int) {
        viewModelScope.launch { folderRepository.moveFolderWithinSiblings(folderId, direction) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch { folderRepository.deleteFolderTree(folderId) }
    }

    fun setFolderExpanded(folderId: String, expanded: Boolean) {
        viewModelScope.launch {
            val folderIds = vaultPreferences.userPreferences.first().expandedFolderIds.toMutableSet()
            if (expanded) folderIds += folderId else folderIds -= folderId
            vaultPreferences.setExpandedFolderIds(folderIds)
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { vaultPreferences.setLibraryViewMode(viewModeLocationKey, mode.storedValue) }
    }

    fun prepareStudyNoteLinks() {
        if (studyNoteLinksJob != null) return
        studyNoteLinks.value = LibraryStudyNoteLinks(loading = true)
        studyNoteLinksJob = viewModelScope.launch {
            noteRepository.observeAllNotes().collect { notes ->
                studyNoteLinks.value = LibraryStudyNoteLinks(
                    items = notes.map { LibraryStudyNoteItem(id = it.id, title = it.title) },
                    loading = false,
                )
            }
        }
    }

    fun importFile(uri: Uri, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            onImported(attachmentRepository.importLibraryDocument(targetLibraryFolderId(), uri))
        }
    }

    fun importFiles(uris: List<Uri>, onImported: (String) -> Unit = {}) {
        importFilesToFolder(folderId = null, uris = uris, onImported = onImported)
    }

    fun importFilesToFolder(folderId: String?, uris: List<Uri>, onImported: (String) -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val targetFolderId = folderId ?: targetLibraryFolderId()
            val duplicate = uris.firstNotNullOfOrNull { uri ->
                attachmentRepository.findDuplicateLibraryPdf(targetFolderId, uri)?.let { existing ->
                    PendingDuplicatePdfImport(
                        uri = uri,
                        remainingUris = uris.filterNot { it == uri },
                        existingAttachmentId = existing.id,
                        fileName = existing.fileName,
                        targetFolderId = targetFolderId,
                    )
                }
            }
            if (duplicate != null) {
                importState.value = LibraryImportState(duplicatePdf = duplicate)
                return@launch
            }
            importState.value = LibraryImportState(active = true, message = "Importing ${uris.size} file${if (uris.size == 1) "" else "s"}...")
            val result = attachmentRepository.importLibraryDocuments(targetFolderId, uris)
            result.importedIds.firstOrNull()?.let(onImported)
            val message = when {
                result.importedIds.isEmpty() && result.failedCount > 0 -> "Import failed for ${result.failedCount} file${if (result.failedCount == 1) "" else "s"}."
                result.failedCount > 0 -> "Imported ${result.importedIds.size}; skipped ${result.failedCount}."
                else -> "Imported ${result.importedIds.size} file${if (result.importedIds.size == 1) "" else "s"}."
            }
            importState.value = LibraryImportState(active = false, message = message)
        }
    }

    fun replaceDuplicatePdf() {
        val pending = importState.value.duplicatePdf ?: return
        viewModelScope.launch {
            importState.value = LibraryImportState(active = true, message = "Replacing ${pending.fileName}...")
            runCatching { attachmentRepository.replaceLibraryPdf(pending.existingAttachmentId, pending.uri) }
                .onSuccess {
                    if (pending.remainingUris.isNotEmpty()) {
                        importFilesToFolder(pending.targetFolderId, pending.remainingUris)
                    } else {
                        importState.value = LibraryImportState(active = false, message = "Replaced ${pending.fileName}.")
                    }
                }
                .onFailure {
                    importState.value = LibraryImportState(active = false, message = "Replace failed: ${it.message ?: "Unknown error"}")
                }
        }
    }

    fun skipDuplicatePdf() {
        val pending = importState.value.duplicatePdf ?: return
        if (pending.remainingUris.isNotEmpty()) {
            importFilesToFolder(pending.targetFolderId, pending.remainingUris)
        } else {
            importState.value = LibraryImportState(active = false, message = "Skipped ${pending.fileName}.")
        }
    }

    fun clearImportMessage() {
        importState.update { it.copy(message = null) }
    }

    fun renameFile(fileId: String, name: String) {
        viewModelScope.launch { attachmentRepository.renameAttachment(fileId, name) }
    }

    fun moveFile(fileId: String, folderId: String?) {
        viewModelScope.launch { attachmentRepository.moveLibraryAttachment(fileId, folderId ?: personalRootFolderIdIfNeeded()) }
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

    fun createStudyNoteFromAnnotation(annotationId: String, onCreated: (String) -> Unit = {}) {
        val annotation = _uiState.value.findAnnotation(annotationId) ?: return
        viewModelScope.launch {
            val title = annotation.displayTitle
                ?: annotation.notePreview.takeIf { it.isNotBlank() }?.take(48)
                ?: "PDF highlight - page ${annotation.pageIndex + 1}"
            val body = buildString {
                appendLine(title)
                appendLine()
                appendLine("Source: ${annotation.fileName}")
                appendLine("Page: ${annotation.pageIndex + 1}")
                appendLine()
                if (annotation.notePreview.isNotBlank()) {
                    appendLine(annotation.notePreview)
                    appendLine()
                }
                appendLine("Notes:")
            }
            val noteId = noteRepository.createImportedRichTextNote(title = title, text = body, styleMarksJson = "[]")
            knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotation.id)
            onCreated(noteId)
        }
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

    fun exportFile(fileId: String, destination: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { attachmentRepository.exportAttachmentToUri(fileId, destination) }
                .onSuccess { onComplete("File saved to device.") }
                .onFailure { onComplete(it.message ?: "Could not save file.") }
        }
    }

    private suspend fun targetLibraryFolderId(): String? =
        folderId ?: personalRootFolderIdIfNeeded()

    private suspend fun personalRootFolderIdIfNeeded(): String? =
        if (isPersonalLibrary) {
            folderRepository.ensureRootFolderForMode(PERSONAL_LIBRARY_ROOT_FOLDER_NAME, FOLDER_MODE_PERSONAL_LIBRARY)
        } else {
            null
        }

    private fun snapshotFor(mode: String): LibraryUiState? =
        librarySnapshotRepository.load(mode, folderId)
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
        colorKey = colorKey,
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
        annotationType = annotationType,
        displayTitle = displayTitle,
        displayFolderId = displayFolderId,
        notePreview = noteText.orEmpty(),
        updatedAt = updatedAt,
    )

private fun Int?.orZero(): Int = this ?: 0

private fun LibraryUiState.findAnnotation(annotationId: String): LibraryAnnotationItem? =
    sequence {
        yieldAll(annotations)
        allFolders.forEach { folder ->
            folder.flattenLibraryFolders().forEach { nested ->
                yieldAll(nested.annotations)
            }
        }
    }.firstOrNull { it.id == annotationId }

private fun LibraryFolderItem.flattenLibraryFolders(): List<LibraryFolderItem> =
    listOf(this) + children.flatMap { it.flattenLibraryFolders() }

private data class LibraryImportState(
    val active: Boolean = false,
    val message: String? = null,
    val duplicatePdf: PendingDuplicatePdfImport? = null,
)

private data class PendingDuplicatePdfImport(
    val uri: Uri,
    val remainingUris: List<Uri>,
    val existingAttachmentId: String,
    val fileName: String,
    val targetFolderId: String?,
)

private data class LibraryDataLayer(
    val folders: List<FolderEntity>,
    val allFiles: List<AttachmentEntity>,
)

private data class LibraryKnowledgeLayer(
    val references: List<LibraryReferencedNote>,
    val attachmentTags: Map<String, List<KnowledgeTagChip>>,
    val annotationTags: Map<String, List<KnowledgeTagChip>>,
    val studyNotes: List<LibraryStudyNoteItem>,
    val studyNotesLoading: Boolean,
)

private data class LibraryStudyNoteLinks(
    val items: List<LibraryStudyNoteItem> = emptyList(),
    val loading: Boolean = false,
)

private const val LIBRARY_ROOT_VIEW_MODE_KEY = "root"
private const val PERSONAL_LIBRARY_ROOT_FOLDER_NAME = ".personal-library-root"
