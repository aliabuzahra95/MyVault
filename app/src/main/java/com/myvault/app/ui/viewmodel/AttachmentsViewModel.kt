package com.myvault.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.ui.screens.AttachmentSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    seeder: DatabaseSeeder,
    attachmentRepository: AttachmentRepository,
) : ViewModel() {
    private val mode: String = savedStateHandle["mode"] ?: FOLDER_MODE_STUDY
    val attachments: StateFlow<List<AttachmentSample>> =
        attachmentRepository.observeCardsForMode(mode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }
}
