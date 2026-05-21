package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.QuranCatalogRepository
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.QuranTextRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class QuranReaderViewModel @Inject constructor(
    private val quranCatalogRepository: QuranCatalogRepository,
    private val quranTextRepository: QuranTextRepository,
    private val vaultPreferences: VaultPreferences,
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuranReaderUiState())
    val uiState: StateFlow<QuranReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastSavedPosition: Pair<Int, Int>? = null

    init {
        viewModelScope.launch {
            val preferences = vaultPreferences.userPreferences.first()
            loadSurah(
                surahNumber = preferences.quranLastReadSurah,
                restoredAyah = preferences.quranLastReadAyah,
                fontPercent = preferences.quranArabicFontPercent,
                tajweedEnabled = preferences.quranTajweedEnabled,
                bookmarkedVerseKeys = preferences.quranBookmarkedVerses,
            )
        }
    }

    fun selectSurah(surahNumber: Int) {
        val restoredAyah = if (_uiState.value.selectedSurah.num == surahNumber) {
            _uiState.value.restoredAyah
        } else {
            1
        }
        loadSurah(
            surahNumber = surahNumber,
            restoredAyah = restoredAyah,
            fontPercent = _uiState.value.arabicFontPercent,
            tajweedEnabled = _uiState.value.tajweedEnabled,
            bookmarkedVerseKeys = _uiState.value.bookmarkedVerseKeys,
        )
    }

    fun updateLastReadPosition(surahNumber: Int, ayahNumber: Int) {
        val safeSurah = surahNumber.coerceAtLeast(1)
        val safeAyah = ayahNumber.coerceAtLeast(1)
        val position = safeSurah to safeAyah
        if (lastSavedPosition == position) return
        lastSavedPosition = position
        _uiState.value = _uiState.value.copy(restoredAyah = safeAyah)
        viewModelScope.launch {
            vaultPreferences.setQuranReadingPosition(safeSurah, safeAyah)
        }
    }

    fun increaseArabicFont() {
        setArabicFontPercent(_uiState.value.arabicFontPercent + 8)
    }

    fun decreaseArabicFont() {
        setArabicFontPercent(_uiState.value.arabicFontPercent - 8)
    }

    fun setTajweedEnabled(enabled: Boolean) {
        if (_uiState.value.tajweedEnabled == enabled) return
        _uiState.value = _uiState.value.copy(tajweedEnabled = enabled)
        viewModelScope.launch {
            vaultPreferences.setQuranTajweedEnabled(enabled)
        }
    }

    fun toggleTafsir(verseKey: String) {
        val nextExpanded = if (_uiState.value.expandedTafsirVerseKey == verseKey) null else verseKey
        _uiState.value = _uiState.value.copy(expandedTafsirVerseKey = nextExpanded)
        if (nextExpanded == null || verseKey in _uiState.value.tafsirByVerse || verseKey in _uiState.value.loadingTafsirVerseKeys) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingTafsirVerseKeys = _uiState.value.loadingTafsirVerseKeys + verseKey,
            )
            val tafsir = runCatching { quranTextRepository.getTafsir(verseKey) }.getOrDefault("")
            _uiState.value = _uiState.value.copy(
                tafsirByVerse = if (tafsir.isNotBlank()) _uiState.value.tafsirByVerse + (verseKey to tafsir) else _uiState.value.tafsirByVerse,
                loadingTafsirVerseKeys = _uiState.value.loadingTafsirVerseKeys - verseKey,
            )
        }
    }

    fun toggleBookmark(verseKey: String) {
        val updated = _uiState.value.bookmarkedVerseKeys.toMutableSet().apply {
            if (!add(verseKey)) remove(verseKey)
        }
        _uiState.value = _uiState.value.copy(bookmarkedVerseKeys = updated)
        viewModelScope.launch {
            vaultPreferences.setQuranBookmarkedVerses(updated)
        }
    }

    fun createReflectionNoteForAyah(ayah: QuranAyah, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val folderId = folderRepository.ensureRootFolderForMode(
                name = "Quran Reflections",
                mode = FOLDER_MODE_STUDY,
            )
            val surah = _uiState.value.selectedSurah
            val title = "${surah.name} ${surah.num}:${ayah.ayahNumber}"
            val noteId = noteRepository.createNote(folderId = folderId, title = title)
            val body = buildString {
                append(title)
                append("\n\n")
                append(ayah.arabicText)
                append("\n\n")
                append("Reflection:")
            }
            noteRepository.saveRichText(noteId = noteId, text = body, styleMarksJson = "[]")
            onCreated(noteId)
        }
    }

    private fun setArabicFontPercent(percent: Int) {
        val clamped = percent.coerceIn(70, 140)
        if (_uiState.value.arabicFontPercent == clamped) return
        _uiState.value = _uiState.value.copy(arabicFontPercent = clamped)
        viewModelScope.launch {
            vaultPreferences.setQuranArabicFontPercent(clamped)
        }
    }

    private fun loadSurah(
        surahNumber: Int,
        restoredAyah: Int,
        fontPercent: Int,
        tajweedEnabled: Boolean,
        bookmarkedVerseKeys: Set<String>,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val surah = quranCatalogRepository.surah(surahNumber) ?: quranCatalogRepository.surahs().first()
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                restoredAyah = restoredAyah.coerceIn(1, surah.ayat),
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                expandedTafsirVerseKey = null,
                loading = true,
            )
            val ayahs = quranTextRepository.getSurahAyahs(surah.num)
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                ayahs = ayahs,
                restoredAyah = restoredAyah.coerceIn(1, ayahs.lastOrNull()?.ayahNumber ?: surah.ayat),
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                expandedTafsirVerseKey = null,
                loading = false,
            )
        }
    }
}
