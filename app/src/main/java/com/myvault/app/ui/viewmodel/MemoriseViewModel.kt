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
                    attempts = preferences.quranMemorizationAttempts,
                    surahAttempts = preferences.quranSurahMemorizationAttempts,
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

    fun markSelectedSurahMemorized() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = System.currentTimeMillis()
            val existingByVerse = state.records.associateBy { it.verseKey }
            val surahRecords = (1..state.selectedSurah.ayat).map { ayah ->
                val verseKey = "${state.selectedSurah.num}:$ayah"
                val existing = existingByVerse[verseKey]
                existing?.copy(
                    lastReviewedAt = now,
                    reviewCount = if (existing.reviewCount == 0) 1 else existing.reviewCount,
                    memorizedAt = existing.memorizedAt ?: now,
                    updatedAt = now,
                ) ?: MemorizationRecord(
                    verseKey = verseKey,
                    surahNumber = state.selectedSurah.num,
                    ayahNumber = ayah,
                    startedAt = now,
                    lastReviewedAt = now,
                    reviewCount = 1,
                    memorizedAt = now,
                    isRevision = false,
                    isWeak = false,
                    updatedAt = now,
                )
            }
            val updated = (state.records.filterNot { it.surahNumber == state.selectedSurah.num } + surahRecords)
                .sortedByDescending { it.updatedAt }
            vaultPreferences.setQuranMemorizationRecords(updated)
        }
    }

    fun markReviewed(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            existing?.copy(
                lastReviewedAt = now,
                reviewCount = existing.reviewCount + 1,
                isNeedsRevision = false,
                isIncorrect = false,
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
                isIncorrect = false,
                isNeedsRevision = if (current.memorizedAt == null) false else current.isNeedsRevision,
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
                isNeedsRevision = !current.isRevision,
                isIncorrect = if (!current.isRevision) false else current.isIncorrect,
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

    fun toggleIncorrect(verseKey: String) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            val current = existing ?: freshRecord(surah, ayah, now)
            current.copy(
                isIncorrect = !current.isIncorrect,
                isNeedsRevision = if (!current.isIncorrect) false else current.isNeedsRevision,
                isRevision = if (!current.isIncorrect) false else current.isRevision,
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
