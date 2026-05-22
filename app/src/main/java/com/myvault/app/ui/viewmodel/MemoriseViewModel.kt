package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.data.quran.memorization.MemorizationDashboardGroup
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MemoriseViewModel @Inject constructor(
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemorizationUiState())
    val uiState: StateFlow<MemorizationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            vaultPreferences.userPreferences.collect { preferences ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    records = preferences.quranMemorizationRecords,
                    selectedAyah = current.selectedAyah.coerceIn(1, current.selectedSurah.ayat),
                )
            }
        }
    }

    fun selectGroup(group: MemorizationDashboardGroup) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun selectSurah(surahNumber: Int) {
        val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return
        _uiState.value = _uiState.value.copy(
            selectedSurah = surah,
            selectedAyah = _uiState.value.selectedAyah.coerceIn(1, surah.ayat),
        )
    }

    fun selectAyah(ayahNumber: Int) {
        _uiState.value = _uiState.value.copy(
            selectedAyah = ayahNumber.coerceIn(1, _uiState.value.selectedSurah.ayat),
        )
    }

    fun startSelectedAyah() {
        val state = _uiState.value
        upsertRecord(state.selectedSurah.num, state.selectedAyah) { existing, now ->
            existing?.copy(
                lastReviewedAt = now,
                reviewCount = existing.reviewCount + 1,
                updatedAt = now,
            ) ?: MemorizationRecord(
                verseKey = "${state.selectedSurah.num}:${state.selectedAyah}",
                surahNumber = state.selectedSurah.num,
                ayahNumber = state.selectedAyah,
                startedAt = now,
                lastReviewedAt = now,
                reviewCount = 1,
                memorizedAt = null,
                isRevision = false,
                isWeak = false,
                updatedAt = now,
            )
        }
    }

    fun markReviewed(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            existing?.copy(
                lastReviewedAt = now,
                reviewCount = existing.reviewCount + 1,
                updatedAt = now,
            ) ?: freshRecord(surah, ayah, now, reviewCount = 1)
        }
    }

    fun toggleMemorized(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            val current = existing ?: freshRecord(surah, ayah, now)
            current.copy(
                memorizedAt = if (current.memorizedAt == null) now else null,
                lastReviewedAt = now,
                updatedAt = now,
            )
        }
    }

    fun toggleRevision(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            val current = existing ?: freshRecord(surah, ayah, now)
            current.copy(
                isRevision = !current.isRevision,
                updatedAt = now,
            )
        }
    }

    fun toggleWeak(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            val current = existing ?: freshRecord(surah, ayah, now)
            current.copy(
                isWeak = !current.isWeak,
                updatedAt = now,
            )
        }
    }

    private fun upsertRecord(
        surahNumber: Int,
        ayahNumber: Int,
        build: (MemorizationRecord?, Long) -> MemorizationRecord,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val verseKey = "$surahNumber:$ayahNumber"
            val updated = (_uiState.value.records.filterNot { it.verseKey == verseKey } + build(
                _uiState.value.records.firstOrNull { it.verseKey == verseKey },
                now,
            )).sortedByDescending { it.updatedAt }
            vaultPreferences.setQuranMemorizationRecords(updated)
        }
    }

    private fun freshRecord(
        surahNumber: Int,
        ayahNumber: Int,
        now: Long,
        reviewCount: Int = 0,
    ): MemorizationRecord =
        MemorizationRecord(
            verseKey = "$surahNumber:$ayahNumber",
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            startedAt = now,
            lastReviewedAt = now,
            reviewCount = reviewCount,
            memorizedAt = null,
            isRevision = false,
            isWeak = false,
            updatedAt = now,
        )
}

private fun parseVerseKey(verseKey: String): Pair<Int, Int>? {
    val surah = verseKey.substringBefore(':').toIntOrNull() ?: return null
    val ayah = verseKey.substringAfter(':').toIntOrNull() ?: return null
    return surah to ayah
}
