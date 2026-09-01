package com.myvault.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranTextRepository
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.data.quran.memorization.MemorizationDashboardGroup
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationUiState
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.data.quran.memorization.toSavedAttempt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MemoriseViewModel @Inject constructor(
    private val vaultPreferences: VaultPreferences,
    private val quranTextRepository: QuranTextRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val sessionPositions = context.getSharedPreferences("memorise_surah_positions", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(MemorizationUiState())
    val uiState: StateFlow<MemorizationUiState> = _uiState.asStateFlow()
    private val _sessionState = MutableStateFlow(MemoriseSessionUiState())
    val sessionState: StateFlow<MemoriseSessionUiState> = _sessionState.asStateFlow()

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

    fun openSession(verseKey: String, autoRecord: Boolean = false) {
        val (surahNumber, ayahNumber) = parseVerseKey(verseKey) ?: return
        val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return
        _uiState.value = _uiState.value.copy(selectedSurah = surah, selectedAyah = ayahNumber.coerceIn(1, surah.ayat))
        _sessionState.value = MemoriseSessionUiState(
            active = true,
            loading = true,
            surah = surah,
            targetAyahNumber = ayahNumber.coerceIn(1, surah.ayat),
            autoRecordRequestId = if (autoRecord) System.nanoTime() else 0L,
        )
        viewModelScope.launch {
            runCatching { quranTextRepository.getSurahAyahs(surahNumber) }
                .onSuccess { ayahs ->
                    val target = ayahs.firstOrNull { it.ayahNumber == ayahNumber }
                    _sessionState.value = _sessionState.value.copy(
                        loading = false,
                        ayahs = ayahs,
                        ayah = target,
                        errorMessage = if (target == null) "This ayah could not be loaded." else null,
                    )
                }
                .onFailure {
                    _sessionState.value = _sessionState.value.copy(
                        loading = false,
                        errorMessage = "The selected Qur'an passage could not be loaded.",
                    )
                }
        }
    }

    fun openSurah(surahNumber: Int, preferredAyah: Int? = null) {
        openWholeSurahInternal(surahNumber, preferredAyah, autoRecord = false)
    }

    fun openWholeSurah(surahNumber: Int = _uiState.value.selectedSurah.num) {
        openWholeSurahInternal(surahNumber, preferredAyah = null, autoRecord = true)
    }

    private fun openWholeSurahInternal(surahNumber: Int, preferredAyah: Int?, autoRecord: Boolean) {
        val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return
        val persistedAyah = sessionPositions.getInt("surah_$surahNumber", 0).takeIf { it in 1..surah.ayat }
        val targetAyah = memoriseResumeAyah(
            surah = surah,
            records = _uiState.value.records,
            explicitAyah = preferredAyah?.takeIf { it in 1..surah.ayat } ?: persistedAyah,
        )
        _uiState.value = _uiState.value.copy(selectedSurah = surah, selectedAyah = targetAyah)
        _sessionState.value = MemoriseSessionUiState(
            active = true,
            loading = true,
            wholeSurah = true,
            surah = surah,
            targetAyahNumber = targetAyah,
            statusLabels = _uiState.value.records.forSurahStatusLabels(surahNumber),
            autoRecordRequestId = if (autoRecord) System.nanoTime() else 0L,
        )
        viewModelScope.launch {
            runCatching { quranTextRepository.getSurahAyahs(surah.num, QuranTranslationSource.SahihInternational) }
                .onSuccess { ayahs ->
                    _sessionState.value = _sessionState.value.copy(
                        loading = false,
                        ayahs = ayahs,
                        ayah = ayahs.firstOrNull { it.ayahNumber == targetAyah } ?: ayahs.firstOrNull(),
                        errorMessage = if (ayahs.isEmpty()) "This Surah could not be loaded." else null,
                    )
                }
                .onFailure {
                    _sessionState.value = _sessionState.value.copy(
                        loading = false,
                        errorMessage = "The selected Surah could not be loaded.",
                    )
                }
        }
    }

    fun updateSurahPosition(surahNumber: Int, ayahNumber: Int) {
        val state = _sessionState.value
        if (!state.active || !state.wholeSurah || state.surah.num != surahNumber || ayahNumber !in 1..state.surah.ayat) return
        if (state.targetAyahNumber != ayahNumber) {
            _sessionState.value = state.copy(
                targetAyahNumber = ayahNumber,
                ayah = state.ayahs.firstOrNull { it.ayahNumber == ayahNumber } ?: state.ayah,
            )
        }
        sessionPositions.edit().putInt("surah_$surahNumber", ayahNumber).apply()
    }

    fun openNextAyah(): Boolean {
        val current = _sessionState.value
        if (!current.active || current.wholeSurah) return false
        val next = current.targetAyahNumber + 1
        if (next > current.surah.ayat) return false
        _uiState.value = _uiState.value.copy(selectedAyah = next)
        _sessionState.value = current.copy(
            targetAyahNumber = next,
            ayah = current.ayahs.firstOrNull { it.ayahNumber == next },
            autoRecordRequestId = 0L,
        )
        return true
    }

    fun closeSession() {
        _sessionState.value = MemoriseSessionUiState()
    }

    fun consumeAutoRecordRequest() {
        if (_sessionState.value.autoRecordRequestId != 0L) {
            _sessionState.value = _sessionState.value.copy(autoRecordRequestId = 0L)
        }
    }

    fun setStatus(verseKey: String, status: MemoriseStatusChoice) {
        val (surah, ayah) = parseVerseKey(verseKey) ?: return
        upsertRecord(surah, ayah) { existing, now ->
            val current = existing ?: freshRecord(surah, ayah, now)
            when (status) {
                MemoriseStatusChoice.Memorised -> current.copy(
                    memorizedAt = current.memorizedAt ?: now,
                    isMemorising = false,
                    isRevision = false,
                    isNeedsRevision = false,
                    isIncorrect = false,
                    isWeak = false,
                    lastReviewedAt = now,
                    updatedAt = now,
                )
                MemoriseStatusChoice.InProgress -> current.copy(
                    memorizedAt = null,
                    isMemorising = true,
                    isRevision = false,
                    isNeedsRevision = false,
                    isIncorrect = false,
                    isWeak = false,
                    updatedAt = now,
                )
                MemoriseStatusChoice.Revision -> current.copy(
                    memorizedAt = null,
                    isMemorising = false,
                    isRevision = true,
                    isNeedsRevision = true,
                    isIncorrect = false,
                    isWeak = false,
                    updatedAt = now,
                )
                MemoriseStatusChoice.Incorrect -> current.copy(
                    memorizedAt = null,
                    isMemorising = false,
                    isRevision = false,
                    isNeedsRevision = false,
                    isIncorrect = true,
                    isWeak = false,
                    updatedAt = now,
                )
                MemoriseStatusChoice.Difficult -> current.copy(
                    memorizedAt = null,
                    isMemorising = false,
                    isRevision = false,
                    isNeedsRevision = false,
                    isIncorrect = false,
                    isWeak = true,
                    updatedAt = now,
                )
            }
        }
        if (_sessionState.value.wholeSurah && _sessionState.value.surah.num == surah) {
            _sessionState.value = _sessionState.value.copy(
                statusLabels = _sessionState.value.statusLabels + (ayah to status.label),
            )
        }
    }

    fun recordAttempt(attempt: QuranMemorizationAttempt) {
        viewModelScope.launch { vaultPreferences.addQuranMemorizationAttempt(attempt.toSavedAttempt()) }
    }

    fun recordSurahAttempt(attempt: QuranSurahMemorizationAttempt) {
        viewModelScope.launch { vaultPreferences.addQuranSurahMemorizationAttempt(attempt.toSavedAttempt()) }
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

enum class MemoriseStatusChoice(val label: String) {
    Memorised("Memorised"),
    InProgress("In progress"),
    Revision("Revision"),
    Incorrect("Incorrect"),
    Difficult("Difficult"),
}

data class MemoriseSessionUiState(
    val active: Boolean = false,
    val loading: Boolean = false,
    val wholeSurah: Boolean = false,
    val surah: SurahInfo = quranCatalog.first(),
    val targetAyahNumber: Int = 1,
    val ayah: QuranAyah? = null,
    val ayahs: List<QuranAyah> = emptyList(),
    val statusLabels: Map<Int, String> = emptyMap(),
    val autoRecordRequestId: Long = 0L,
    val errorMessage: String? = null,
)

internal fun memoriseResumeAyah(
    surah: SurahInfo,
    records: List<MemorizationRecord>,
    explicitAyah: Int?,
): Int {
    explicitAyah?.takeIf { it in 1..surah.ayat }?.let { return it }
    val recordsByAyah = records.filter { it.surahNumber == surah.num }.associateBy { it.ayahNumber }
    var highestContiguousMemorised = 0
    for (ayah in 1..surah.ayat) {
        if (recordsByAyah[ayah]?.isMemorized == true) highestContiguousMemorised = ayah else break
    }
    if (highestContiguousMemorised in 1 until surah.ayat) return highestContiguousMemorised + 1
    recordsByAyah.values
        .filter { it.isMemorising || it.isRevision || it.isNeedsRevision || it.isIncorrect || it.isWeak }
        .minByOrNull { it.ayahNumber }
        ?.ayahNumber
        ?.let { return it }
    return 1
}

private fun List<MemorizationRecord>.forSurahStatusLabels(surahNumber: Int): Map<Int, String> =
    filter { it.surahNumber == surahNumber }.associate { record ->
        record.ayahNumber to when {
            record.isMemorized -> MemoriseStatusChoice.Memorised.label
            record.isIncorrect -> MemoriseStatusChoice.Incorrect.label
            record.isWeak -> MemoriseStatusChoice.Difficult.label
            record.isRevision || record.isNeedsRevision -> MemoriseStatusChoice.Revision.label
            else -> MemoriseStatusChoice.InProgress.label
        }
    }

private fun parseVerseKey(verseKey: String): Pair<Int, Int>? {
    val surah = verseKey.substringBefore(':').toIntOrNull() ?: return null
    val ayah = verseKey.substringAfter(':').toIntOrNull() ?: return null
    return surah to ayah
}
