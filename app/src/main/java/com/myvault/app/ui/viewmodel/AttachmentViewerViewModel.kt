package com.myvault.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.BuildConfig
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.narration.NarrationController
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.DocumentTextExtractor
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.LibraryReferencedNote
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.PdfAnnotationSegmentInput
import com.myvault.app.data.repository.PdfReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val attachmentRepository: AttachmentRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val narrationController: NarrationController,
) : ViewModel() {
    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    val initialPageIndex: Int = savedStateHandle["page"] ?: -1
    private val _resolvedInitialPageIndex = MutableStateFlow<Int?>(initialPageIndex.takeIf { it >= 0 })
    val resolvedInitialPageIndex: StateFlow<Int?> = _resolvedInitialPageIndex
    private var lastSavedPdfPage: Int? = null
    private var lastSavedPdfPageCount: Int? = null
    private var pendingPdfPage: Int? = null
    private var pendingPdfPageCount: Int? = null
    private var pdfProgressSaveJob: Job? = null
    private val pdfSecondaryDataEnabled = MutableStateFlow(false)
    private var pdfSecondaryDataJob: Job? = null

    init {
        viewModelScope.launch {
            pdfAnnotationRepository.cleanupGenuinelyInvalidAnnotations()
        }
        if (initialPageIndex < 0) {
            viewModelScope.launch {
                _resolvedInitialPageIndex.value = runCatching {
                    pdfReadingProgressRepository.getForAttachment(attachmentId)?.pageIndex?.coerceAtLeast(0) ?: 0
                }.getOrDefault(0)
            }
        }
    }

    val attachment: StateFlow<AttachmentEntity?> =
        attachmentRepository.observeAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val azureNarrationProgress =
        narrationController.progressFor("attachment:$attachmentId")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfProgress: StateFlow<PdfReadingProgressEntity?> =
        pdfSecondaryDataEnabled.flatMapLatest { enabled ->
            if (enabled) {
                pdfReadingProgressRepository.observeForAttachment(attachmentId)
            } else {
                flowOf(null)
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfAnnotations: StateFlow<List<PdfAnnotationEntity>> =
        pdfSecondaryDataEnabled.flatMapLatest { enabled ->
            if (enabled) {
                pdfAnnotationRepository.observeForAttachment(attachmentId)
            } else {
                flowOf(emptyList())
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pdfAnnotationSegments: StateFlow<List<PdfAnnotationSegmentEntity>> =
        pdfSecondaryDataEnabled.flatMapLatest { enabled ->
            if (enabled) {
                pdfAnnotationRepository.observeSegmentsForAttachment(attachmentId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val studyNotes: StateFlow<List<NoteEntity>> =
        noteRepository.observeAllNotes()
            .map { notes -> notes.filter { it.deletedAt == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pdfReferences: StateFlow<List<LibraryReferencedNote>> =
        knowledgeRepository.observeLibraryReferences()
            .map { references -> references.filter { it.attachmentId == attachmentId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val annotationTags: StateFlow<Map<String, List<KnowledgeTagChip>>> =
        knowledgeRepository.observeTagsByTargetType(KnowledgeRepository.TargetAnnotation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val attachmentTags: StateFlow<List<KnowledgeTagChip>> =
        knowledgeRepository.observeTagsFor(KnowledgeRepository.TargetAttachment, attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documentText: StateFlow<DocumentTextUiState> =
        attachment.flatMapLatest { file ->
            if (file == null || !DocumentTextExtractor.isSupported(file.fileName, file.mimeType)) {
                flowOf(DocumentTextUiState())
            } else {
                flow {
                    emit(DocumentTextUiState(isSupported = true, isLoading = true))
                    val result = runCatching {
                        DocumentTextExtractor.extract(file.fileName, file.mimeType, file.localPath)
                    }
                    emit(
                        result.fold(
                            onSuccess = { DocumentTextUiState(isSupported = true, text = it) },
                            onFailure = {
                                DocumentTextUiState(
                                    isSupported = true,
                                    error = it.message ?: "Could not read this document.",
                                )
                            },
                        ),
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentTextUiState())

    fun loadPdfSecondaryData() {
        if (pdfSecondaryDataEnabled.value || pdfSecondaryDataJob?.isActive == true) return
        pdfSecondaryDataJob = viewModelScope.launch {
            delay(220)
            pdfSecondaryDataEnabled.value = true
        }
    }

    fun updatePdfProgress(pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0) return
        val safePage = pageIndex.coerceIn(0, pageCount - 1)
        if (lastSavedPdfPage == safePage && lastSavedPdfPageCount == pageCount) return
        if (pendingPdfPage == safePage && pendingPdfPageCount == pageCount) return
        pendingPdfPage = safePage
        pendingPdfPageCount = pageCount
        pdfProgressSaveJob?.cancel()
        pdfProgressSaveJob = viewModelScope.launch {
            delay(PdfProgressSaveDebounceMs)
            val pageToSave = pendingPdfPage ?: return@launch
            val countToSave = pendingPdfPageCount ?: return@launch
            pdfReadingProgressRepository.updateProgress(attachmentId, pageToSave, countToSave)
            lastSavedPdfPage = pageToSave
            lastSavedPdfPageCount = countToSave
            pendingPdfPage = null
            pendingPdfPageCount = null
        }
    }

    fun deleteAttachment(onDeleted: () -> Unit) {
        viewModelScope.launch {
            attachmentRepository.deleteAttachment(attachmentId)
            onDeleted()
        }
    }

    fun exportAttachment(destination: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { attachmentRepository.exportAttachmentToUri(attachmentId, destination) }
                .onSuccess { onComplete("File saved to device.") }
                .onFailure { onComplete(it.message ?: "Could not save file.") }
        }
    }

    fun startAzureNarration() {
        val file = attachment.value ?: return
        val text = documentText.value.text
        if (text.isBlank()) return
        narrationController.startAzure("attachment:${file.id}", file.fileName, text)
    }

    fun resumeAzureNarration() {
        val file = attachment.value ?: return
        val text = documentText.value.text
        if (text.isBlank()) return
        narrationController.startAzure("attachment:${file.id}", file.fileName, text, resume = true)
    }

    fun startAzureNarrationFromSelection(startOffset: Int) {
        val file = attachment.value ?: return
        val text = documentText.value.text
        if (text.isBlank()) return
        narrationController.startAzure("attachment:${file.id}", file.fileName, text, bodyStartOffset = startOffset)
    }

    fun startOpenAiNarration(selection: String? = null) {
        val file = attachment.value ?: return
        val text = selection?.trim().takeUnless { it.isNullOrBlank() } ?: documentText.value.text
        if (text.isBlank()) return
        narrationController.start("attachment:${file.id}", file.fileName, text)
    }

    fun startDeviceNarration(selection: String? = null) {
        val file = attachment.value ?: return
        val text = selection?.trim().takeUnless { it.isNullOrBlank() } ?: documentText.value.text
        if (text.isBlank()) return
        narrationController.startDevice("attachment:${file.id}", file.fileName, text)
    }

    fun startAzureNarration(selection: String?) {
        val file = attachment.value ?: return
        val text = selection?.trim().takeUnless { it.isNullOrBlank() } ?: documentText.value.text
        if (text.isBlank()) return
        narrationController.startAzure("attachment:${file.id}", file.fileName, text)
    }

    fun addPdfHighlight(
        libraryFolderId: String?,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: String,
        onSaved: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val saved = pdfAnnotationRepository.addHighlight(
                attachmentId = attachmentId,
                libraryFolderId = libraryFolderId,
                pageIndex = pageIndex,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                color = color,
            )
            if (BuildConfig.DEBUG) {
                Log.d("MyVaultPdfHighlight", "ViewModel highlight insert result=$saved attachmentId=$attachmentId")
            }
            onSaved(saved)
        }
    }

    fun addPdfSelectedTextAnnotation(
        libraryFolderId: String?,
        selectedText: String,
        segments: List<PdfAnnotationSegmentInput>,
        color: String,
        noteText: String?,
        onSaved: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            onSaved(
                pdfAnnotationRepository.addSelectedTextAnnotation(
                    attachmentId = attachmentId,
                    libraryFolderId = libraryFolderId,
                    segments = segments,
                    selectedText = selectedText,
                    color = color,
                    noteText = noteText,
                ),
            )
        }
    }


    fun addPdfPageNote(
        libraryFolderId: String?,
        pageIndex: Int,
        noteText: String,
        onSaved: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val saved = pdfAnnotationRepository.addPageNote(
                attachmentId = attachmentId,
                libraryFolderId = libraryFolderId,
                pageIndex = pageIndex,
                noteText = noteText,
            )
            onSaved(saved)
        }
    }

    fun updatePdfHighlightColor(annotationId: String, color: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateColor(annotationId, color) }
    }

    fun updatePdfAnnotationNote(annotationId: String, noteText: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateNote(annotationId, noteText) }
    }

    fun addPdfTextBox(
        libraryFolderId: String?,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        text: String,
        color: String,
        textSize: Float,
        backgroundColor: String,
        onSaved: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val saved = pdfAnnotationRepository.addTextBox(
                attachmentId = attachmentId,
                libraryFolderId = libraryFolderId,
                pageIndex = pageIndex,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                text = text,
                color = color,
                textSize = textSize,
                backgroundColor = backgroundColor,
            )
            onSaved(saved)
        }
    }

    fun updatePdfTextBox(annotationId: String, text: String, color: String, textSize: Float, backgroundColor: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateTextBox(annotationId, text, color, textSize, backgroundColor) }
    }

    fun updatePdfTextBoxBounds(annotationId: String, left: Float, top: Float, right: Float, bottom: Float) {
        viewModelScope.launch { pdfAnnotationRepository.updateBounds(annotationId, left, top, right, bottom) }
    }

    fun deletePdfAnnotation(annotationId: String) {
        viewModelScope.launch { pdfAnnotationRepository.delete(annotationId) }
    }

    fun addAttachmentTag(name: String) {
        viewModelScope.launch {
            knowledgeRepository.addTag(KnowledgeRepository.TargetAttachment, attachmentId, name)
        }
    }

    fun removeAttachmentTag(tagId: String) {
        viewModelScope.launch {
            knowledgeRepository.removeTag(KnowledgeRepository.TargetAttachment, attachmentId, tagId)
        }
    }

    fun addAnnotationTag(annotationId: String, name: String) {
        viewModelScope.launch {
            knowledgeRepository.addTag(KnowledgeRepository.TargetAnnotation, annotationId, name)
        }
    }

    fun removeAnnotationTag(annotationId: String, tagId: String) {
        viewModelScope.launch {
            knowledgeRepository.removeTag(KnowledgeRepository.TargetAnnotation, annotationId, tagId)
        }
    }

    fun linkAnnotationToStudyNote(annotationId: String, noteId: String) {
        viewModelScope.launch { knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotationId) }
    }

    fun createStudyNoteFromAnnotation(annotationId: String, onCreated: (String) -> Unit = {}) {
        val annotation = pdfAnnotations.value.firstOrNull { it.id == annotationId } ?: return
        val file = attachment.value ?: return
        viewModelScope.launch {
            val excerpt = annotation.selectedText?.trim().orEmpty()
                .ifBlank { annotation.noteText?.trim().orEmpty() }
            val title = annotation.displayTitle
                ?: excerpt.takeIf { it.isNotBlank() }?.take(48)
                ?: "PDF highlight - page ${annotation.pageIndex + 1}"
            val body = buildString {
                appendLine(title)
                appendLine()
                appendLine("Source: ${file.fileName}")
                appendLine("Page: ${annotation.pageIndex + 1}")
                if (excerpt.isNotBlank()) {
                    appendLine()
                    appendLine(excerpt)
                }
                appendLine()
                appendLine("Notes:")
            }
            val noteId = noteRepository.createImportedRichTextNote(
                title = title,
                text = body,
                styleMarksJson = "[]",
            )
            knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotationId)
            onCreated(noteId)
        }
    }
}

private const val PdfProgressSaveDebounceMs = 450L

data class DocumentTextUiState(
    val isSupported: Boolean = false,
    val isLoading: Boolean = false,
    val text: String = "",
    val error: String? = null,
)
