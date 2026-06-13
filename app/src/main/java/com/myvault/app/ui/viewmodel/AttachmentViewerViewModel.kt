package com.myvault.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.narration.NarrationController
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.DocumentTextExtractor
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.PdfReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val narrationController: NarrationController,
) : ViewModel() {
    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    val initialPageIndex: Int = savedStateHandle["page"] ?: -1
    private val _resolvedInitialPageIndex = MutableStateFlow<Int?>(initialPageIndex.takeIf { it >= 0 })
    val resolvedInitialPageIndex: StateFlow<Int?> = _resolvedInitialPageIndex
    private var lastSavedPdfPage: Int? = null
    private var lastSavedPdfPageCount: Int? = null
    private val pdfSecondaryDataEnabled = MutableStateFlow(false)

    init {
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
        pdfSecondaryDataEnabled.value = true
    }

    fun updatePdfProgress(pageIndex: Int, pageCount: Int) {
        if (lastSavedPdfPage == pageIndex && lastSavedPdfPageCount == pageCount) return
        lastSavedPdfPage = pageIndex
        lastSavedPdfPageCount = pageCount
        viewModelScope.launch {
            pdfReadingProgressRepository.updateProgress(attachmentId, pageIndex, pageCount)
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
            Log.d("MyVaultPdfHighlight", "ViewModel highlight insert result=$saved attachmentId=$attachmentId")
            onSaved(saved)
        }
    }

    fun updatePdfHighlightColor(annotationId: String, color: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateColor(annotationId, color) }
    }

    fun updatePdfAnnotationNote(annotationId: String, noteText: String) {
        viewModelScope.launch { pdfAnnotationRepository.updateNote(annotationId, noteText) }
    }

    fun deletePdfAnnotation(annotationId: String) {
        viewModelScope.launch { pdfAnnotationRepository.delete(annotationId) }
    }
}

data class DocumentTextUiState(
    val isSupported: Boolean = false,
    val isLoading: Boolean = false,
    val text: String = "",
    val error: String? = null,
)
