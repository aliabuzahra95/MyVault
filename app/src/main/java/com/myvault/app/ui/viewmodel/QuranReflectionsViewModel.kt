package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.QuranReflectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QuranReflectionsUiState(
    val reflections: List<QuranReflectionItem> = emptyList(),
)

@HiltViewModel
class QuranReflectionsViewModel @Inject constructor(
    quranReflectionRepository: QuranReflectionRepository,
) : ViewModel() {
    val uiState: StateFlow<QuranReflectionsUiState> =
        quranReflectionRepository.observeReflectionItems()
            .map { QuranReflectionsUiState(reflections = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuranReflectionsUiState())
}
