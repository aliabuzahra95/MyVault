package com.myvault.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.BuildConfig
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
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
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.parseVaultRichTextDocument
import com.myvault.app.ui.screens.toJsonArrayString
import com.myvault.app.ui.screens.toNoteLinksJsonArrayString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val savedStateHandle: SavedStateHandle,
    private val attachmentRepository: AttachmentRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val narrationController: NarrationController,
    private val pdfHighlightClipCoordinator: PdfHighlightClipCoordinator,
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
    private val activeNotepadNoteId = MutableStateFlow<String?>(null)
    private val notepadSaveJobs = mutableMapOf<String, Job>()
    private val secondaryPdfId = savedStateHandle.getStateFlow<String?>(SecondaryPdfIdKey, null)
    private var secondaryPdfProgressSaveJob: Job? = null
    private var pendingSecondaryProgress: Triple<String, Int, Int>? = null

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
        noteRepository.observeNotesForMode(FOLDER_MODE_STUDY)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryPdfs: StateFlow<List<AttachmentEntity>> =
        attachmentRepository.observeLibraryFiles()
            .map { files ->
                files.filter { file ->
                    file.deletedAt == null &&
                        (file.mimeType == "application/pdf" || file.fileName.endsWith(".pdf", ignoreCase = true))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val secondaryPdfAttachment: StateFlow<AttachmentEntity?> = secondaryPdfId
        .flatMapLatest { id -> id?.let(attachmentRepository::observeAttachment) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val secondaryPdfProgress: StateFlow<PdfReadingProgressEntity?> = secondaryPdfId
        .flatMapLatest { id -> id?.let(pdfReadingProgressRepository::observeForAttachment) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfNotepad: StateFlow<PdfNotepadUiState> = activeNotepadNoteId
        .flatMapLatest { noteId ->
            if (noteId == null) {
                flowOf(PdfNotepadUiState())
            } else {
                combine(
                    noteRepository.observeNote(noteId),
                    noteRepository.observeRawBlocks(noteId),
                ) { note, blocks ->
                    val richText = blocks.firstOrNull { it.type == "rich_text" }
                        ?.content
                        ?.let(::parseVaultRichTextDocument)
                        ?: VaultRichTextDocument(note?.bodyPlainText.orEmpty(), emptyList())
                    PdfNotepadUiState(note = note, document = richText)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PdfNotepadUiState())

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

    fun selectSecondaryPdf(id: String) {
        if (id.isBlank() || id == attachmentId) return
        savedStateHandle[SecondaryPdfIdKey] = id
    }

    fun clearSecondaryPdf() {
        secondaryPdfProgressSaveJob?.cancel()
        pendingSecondaryProgress = null
        savedStateHandle[SecondaryPdfIdKey] = null
    }

    fun updateSecondaryPdfProgress(pageIndex: Int, pageCount: Int) {
        val id = secondaryPdfId.value ?: return
        if (pageCount <= 0) return
        val safePage = pageIndex.coerceIn(0, pageCount - 1)
        val update = Triple(id, safePage, pageCount)
        if (pendingSecondaryProgress == update) return
        pendingSecondaryProgress = update
        secondaryPdfProgressSaveJob?.cancel()
        secondaryPdfProgressSaveJob = viewModelScope.launch {
            delay(PdfProgressSaveDebounceMs)
            val pending = pendingSecondaryProgress ?: return@launch
            pdfReadingProgressRepository.updateProgress(pending.first, pending.second, pending.third)
            if (pendingSecondaryProgress == pending) pendingSecondaryProgress = null
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

    fun openPdfNotepad(onReady: () -> Unit = {}) {
        val selectedId = activeNotepadNoteId.value
        if (selectedId != null && studyNotes.value.any { it.id == selectedId }) {
            onReady()
            return
        }
        studyNotes.value.firstOrNull()?.let { note ->
            activeNotepadNoteId.value = note.id
            onReady()
            return
        }
        createPdfNotepadNote(onReady = { onReady() })
    }

    fun selectPdfNotepadNote(noteId: String) {
        if (studyNotes.value.any { it.id == noteId }) activeNotepadNoteId.value = noteId
    }

    fun createPdfNotepadNote(onReady: (String) -> Unit = {}) {
        val title = attachment.value?.fileName
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it notes" }
            ?: "PDF notes"
        viewModelScope.launch {
            val noteId = noteRepository.createRichTextNote(
                folderId = null,
                title = title,
                text = "",
                styleMarksJson = "[]",
            )
            activeNotepadNoteId.value = noteId
            onReady(noteId)
        }
    }

    fun savePdfNotepad(noteId: String, document: VaultRichTextDocument, immediate: Boolean = false) {
        notepadSaveJobs.remove(noteId)?.cancel()
        notepadSaveJobs[noteId] = viewModelScope.launch {
            if (!immediate) delay(PdfNotepadSaveDebounceMs)
            noteRepository.saveRichText(
                noteId = noteId,
                text = document.text,
                styleMarksJson = document.styleMarks.toJsonArrayString(),
                noteLinksJson = document.noteLinks.toNoteLinksJsonArrayString(),
            )
            notepadSaveJobs.remove(noteId)
        }
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
            val noteId = noteRepository.createRichTextNote(
                folderId = null,
                title = title,
                text = body,
                styleMarksJson = "[]",
            )
            knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotationId)
            onCreated(noteId)
        }
    }

    fun clipPdfHighlightToNote(
        annotationId: String,
        destinationNoteId: String?,
        onComplete: (noteId: String?, message: String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            runCatching {
                pdfHighlightClipCoordinator.clipToNote(annotationId, destinationNoteId)
            }.onSuccess { result ->
                val label = if (result.imageCount == 1) "Highlight clipped to note." else "${result.imageCount} highlight clips added."
                onComplete(result.noteId, label)
            }.onFailure { error ->
                onComplete(null, error.message ?: "Could not clip this highlight.")
            }
        }
    }
}

private const val PdfProgressSaveDebounceMs = 450L
private const val PdfNotepadSaveDebounceMs = 350L
private const val SecondaryPdfIdKey = "secondaryPdfId"

data class PdfNotepadUiState(
    val note: NoteEntity? = null,
    val document: VaultRichTextDocument = VaultRichTextDocument("", emptyList()),
)

data class DocumentTextUiState(
    val isSupported: Boolean = false,
    val isLoading: Boolean = false,
    val text: String = "",
    val error: String? = null,
)
