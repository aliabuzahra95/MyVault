package com.myvault.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.isCurrentPdfAnnotation
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfActivityGroup(
    val attachmentId: String,
    val fileName: String,
    val totalCount: Int,
    val lastActivityAt: Long,
    val activities: List<LibraryAnnotationItem>,
)

data class PdfActivityFeedUiState(
    val pdfActivities: List<PdfActivityGroup> = emptyList(),
    val expandedPdfIds: Set<String> = emptySet(),
    val libraryMode: String = "library",
    val searchQuery: String = "",
    val selectedActivityIds: Set<String> = emptySet(),
)

@HiltViewModel
class PdfActivityFeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val attachmentRepository: AttachmentRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
) : ViewModel() {
    val libraryMode: String = savedStateHandle["libraryMode"] ?: "library"
    private val isPersonalLibrary = libraryMode == FOLDER_MODE_PERSONAL_LIBRARY
    private val expandedPdfIds = MutableStateFlow<Set<String>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val selectedActivityIds = MutableStateFlow<Set<String>>(emptySet())

    private val filterState = combine(
        expandedPdfIds,
        searchQuery,
        selectedActivityIds
    ) { expanded, query, selected ->
        Triple(expanded, query, selected)
    }

    val uiState: StateFlow<PdfActivityFeedUiState> = combine(
        folderRepository.observeLibraryFolders(),
        attachmentRepository.observeLibraryFiles(),
        pdfAnnotationRepository.observeAll(),
        filterState,
    ) { folders, allFiles, annotations, (expandedIds, query, selectedIds) ->
        val modeFolders = folders.filter { it.mode == libraryMode }
        val modeFolderIds = modeFolders.map { it.id }.toSet()

        val activeFiles = allFiles
            .filter { it.deletedAt == null }
            .filter { file ->
                if (isPersonalLibrary) {
                    file.libraryFolderId in modeFolderIds
                } else {
                    file.libraryFolderId == null || file.libraryFolderId in modeFolderIds
                }
            }
        val filesById = activeFiles.associateBy { it.id }

        val normalizedQuery = query.trim().lowercase()

        val mappedAnnotations = annotations
            .filter { it.isCurrentPdfAnnotation() && it.attachmentId in filesById }
            .mapNotNull { annotation ->
                filesById[annotation.attachmentId]?.let { file ->
                    val matchesQuery = if (normalizedQuery.isBlank()) {
                        true
                    } else {
                        file.fileName.lowercase().contains(normalizedQuery) ||
                            annotation.displayTitle?.lowercase()?.contains(normalizedQuery) == true ||
                            annotation.noteText?.lowercase()?.contains(normalizedQuery) == true
                    }

                    if (matchesQuery) {
                        LibraryAnnotationItem(
                            id = annotation.id,
                            attachmentId = annotation.attachmentId,
                            fileName = file.fileName,
                            pageIndex = annotation.pageIndex,
                            color = annotation.color,
                            annotationType = annotation.annotationType,
                            displayTitle = annotation.displayTitle,
                            displayFolderId = annotation.displayFolderId,
                            notePreview = annotation.noteText.orEmpty(),
                            updatedAt = annotation.updatedAt,
                        )
                    } else null
                }
            }

        val groups = mappedAnnotations
            .groupBy { it.attachmentId }
            .map { (attachmentId, items) ->
                val file = filesById[attachmentId]!!
                val chronologicalItems = items.sortedWith(compareBy<LibraryAnnotationItem> { it.pageIndex }.thenBy { it.updatedAt })
                PdfActivityGroup(
                    attachmentId = attachmentId,
                    fileName = file.fileName,
                    totalCount = items.size,
                    lastActivityAt = items.maxOfOrNull { it.updatedAt } ?: 0L,
                    activities = chronologicalItems,
                )
            }
            .sortedByDescending { it.lastActivityAt }

        // If searching, auto-expand any matched groups so the user immediately sees highlights
        val finalExpandedIds = if (normalizedQuery.isNotBlank()) {
            expandedIds + groups.map { it.attachmentId }.toSet()
        } else {
            expandedIds
        }

        PdfActivityFeedUiState(
            pdfActivities = groups,
            expandedPdfIds = finalExpandedIds,
            libraryMode = libraryMode,
            searchQuery = query,
            selectedActivityIds = selectedIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PdfActivityFeedUiState(),
    )

    fun toggleExpanded(attachmentId: String) {
        expandedPdfIds.update { current ->
            if (attachmentId in current) current - attachmentId else current + attachmentId
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleSelection(activityId: String) {
        selectedActivityIds.update { current ->
            if (activityId in current) current - activityId else current + activityId
        }
    }

    fun clearSelection() {
        selectedActivityIds.value = emptySet()
    }

    fun updateActivityDetails(activityId: String, title: String, description: String) {
        viewModelScope.launch {
            pdfAnnotationRepository.updateDisplayTitle(activityId, title)
            pdfAnnotationRepository.updateNote(activityId, description)
        }
    }

    fun deleteSelected() {
        val ids = selectedActivityIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                pdfAnnotationRepository.delete(id)
            }
            clearSelection()
        }
    }

    fun createStudyNoteFromSelected(onCreated: (String) -> Unit) {
        val selectedIds = selectedActivityIds.value
        val activities = uiState.value.pdfActivities.flatMap { it.activities }.filter { it.id in selectedIds }
        if (activities.isEmpty()) return
        viewModelScope.launch {
            val title = "Merged PDF Highlights - ${activities.size} items"
            val body = buildString {
                appendLine(title)
                appendLine()
                activities.forEach { act ->
                    appendLine("---")
                    appendLine("Source: ${act.fileName} (Page ${act.pageIndex + 1})")
                    val label = if (act.annotationType == "page_note") "PDF Note" else "Highlight"
                    appendLine("Type: $label")
                    if (act.displayTitle != null) {
                        appendLine("Title: ${act.displayTitle}")
                    }
                    if (act.notePreview.isNotBlank()) {
                        appendLine("Content: ${act.notePreview}")
                    }
                    appendLine()
                }
            }
            val noteId = noteRepository.createImportedRichTextNote(title = title, text = body, styleMarksJson = "[]")
            activities.forEach { act ->
                knowledgeRepository.createSourceLinkFromAnnotation(noteId, act.id)
            }
            clearSelection()
            onCreated(noteId)
        }
    }

}
