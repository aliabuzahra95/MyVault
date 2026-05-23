package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.repository.SearchRepository
import com.myvault.app.ui.components.SearchResultData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val notes: List<SearchResultData> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<String> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    seeder: DatabaseSeeder,
    private val searchRepository: SearchRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val searchResults = query
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { currentQuery ->
            combine(
                searchRepository.searchNotes(currentQuery),
                searchRepository.searchFolders(currentQuery),
                searchRepository.searchTags(currentQuery),
            ) { notes, folders, tags ->
                Triple(notes, folders, tags)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(query, searchResults) { currentQuery, results ->
        SearchUiState(
            query = currentQuery,
            notes = results.first,
            folders = results.second,
            tags = results.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
