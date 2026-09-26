package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.sync.record.PilotNote
import com.myvault.app.data.sync.record.RecordSyncPilot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RecordSyncPilotUiState(
    val notes: List<PilotNote> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
internal class RecordSyncPilotViewModel @Inject constructor(private val pilot: RecordSyncPilot) : ViewModel() {
    private val mutableState = MutableStateFlow(RecordSyncPilotUiState())
    val state: StateFlow<RecordSyncPilotUiState> = mutableState

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(notes = pilot.notes())
    }

    fun create(onCreated: (String) -> Unit) = viewModelScope.launch {
        if (mutableState.value.busy) return@launch
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        runCatching { pilot.createTestNote() }.onSuccess { id ->
            mutableState.value = mutableState.value.copy(notes = pilot.notes(), busy = false)
            onCreated(id)
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(busy = false, message = error.message ?: "Could not create test note.")
        }
    }

    fun sync() = viewModelScope.launch {
        if (mutableState.value.busy) return@launch
        mutableState.value = mutableState.value.copy(busy = true, message = "Checking test notes…")
        runCatching { pilot.syncNow() }.onSuccess { result ->
            mutableState.value = mutableState.value.copy(
                notes = pilot.notes(), busy = false,
                message = "Done: ${result.uploaded} sent, ${result.imported} received, ${result.conflicts} conflicts.",
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(busy = false, message = error.message ?: "Test sync failed.")
        }
    }
}
