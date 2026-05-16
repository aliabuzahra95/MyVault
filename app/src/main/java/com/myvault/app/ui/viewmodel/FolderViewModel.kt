package com.myvault.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.previewText
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.ui.screens.FolderCardSample
import com.myvault.app.ui.screens.FolderNoteSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val folder: FolderEntity? = null,
    val subfolders: List<FolderCardSample> = emptyList(),
    val notes: List<FolderNoteSample> = emptyList(),
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    seeder: DatabaseSeeder,
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {
    private val folderId: String = savedStateHandle["folderId"] ?: "aqeedah"

    val uiState: StateFlow<FolderUiState> = combine(
        folderRepository.observeFolder(folderId),
        folderRepository.observeSubfolders(folderId),
        noteRepository.observeForFolder(folderId),
    ) { folder, subfolders, notes ->
        FolderUiState(
            folder = folder,
            subfolders = subfolders.map { FolderCardSample(it.id, it.name, "0 notes") },
            notes = notes.map {
                FolderNoteSample(
                    id = it.id,
                    title = it.title,
                    meta = "Edited ${it.updatedAt.toRelativeTime()}",
                    pinned = it.isPinned,
                    favourite = it.isFavourite,
                    preview = it.bodyPlainText.previewText(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }

    fun createNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(noteRepository.createNote(folderId = folderId))
        }
    }

    fun createSubfolder(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(folderRepository.createFolder(parentId = folderId, name = name))
        }
    }
}
