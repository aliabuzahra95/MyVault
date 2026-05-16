package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
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
    seeder: DatabaseSeeder,
    attachmentRepository: AttachmentRepository,
) : ViewModel() {
    val attachments: StateFlow<List<AttachmentSample>> =
        attachmentRepository.observeAllCards()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }
}
