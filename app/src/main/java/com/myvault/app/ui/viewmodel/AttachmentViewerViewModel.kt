package com.myvault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.data.repository.PdfReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val attachmentRepository: AttachmentRepository,
    private val pdfReadingProgressRepository: PdfReadingProgressRepository,
    private val pdfAnnotationRepository: PdfAnnotationRepository,
) : ViewModel() {
    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    val initialPageIndex: Int = savedStateHandle["page"] ?: -1
    private var lastSavedPdfPage: Int? = null
    private var lastSavedPdfPageCount: Int? = null

    val attachment: StateFlow<AttachmentEntity?> =
        attachmentRepository.observeAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfProgress: StateFlow<PdfReadingProgressEntity?> =
        pdfReadingProgressRepository.observeForAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfAnnotations: StateFlow<List<PdfAnnotationEntity>> =
        pdfAnnotationRepository.observeForAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
