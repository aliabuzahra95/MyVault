package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.repository.DashboardActivityRepository
import com.myvault.app.data.repository.DashboardActivityState
import com.myvault.app.data.repository.DashboardActivityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardActivityViewModel @Inject constructor(
    private val repository: DashboardActivityRepository,
) : ViewModel() {
    val state: StateFlow<DashboardActivityState> = repository.availableState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardActivityState(),
    )

    fun openActivity(item: DashboardActivityItem, onResolved: (DashboardActivityItem?) -> Unit) {
        viewModelScope.launch { onResolved(repository.resolveActivity(item)) }
    }

    fun recordNoteOpened(noteId: String) {
        viewModelScope.launch { repository.recordNoteOpened(noteId) }
    }

    fun recordLibraryOpened(attachmentId: String) {
        viewModelScope.launch { repository.recordLibraryOpened(attachmentId) }
    }

    fun updateLibraryProgress(attachmentId: String, pageIndex: Int, pageCount: Int) {
        viewModelScope.launch { repository.updateLibraryProgress(attachmentId, pageIndex, pageCount) }
    }
}
