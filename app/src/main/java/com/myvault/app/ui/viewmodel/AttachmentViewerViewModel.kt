package com.myvault.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.repository.AttachmentRepository
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
) : ViewModel() {
    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""

    val attachment: StateFlow<AttachmentEntity?> =
        attachmentRepository.observeAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pdfProgress: StateFlow<PdfReadingProgressEntity?> =
        pdfReadingProgressRepository.observeForAttachment(attachmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updatePdfProgress(pageIndex: Int, pageCount: Int) {
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
}
