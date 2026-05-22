package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.QuranCatalogRepository
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.QuranTextRepository
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioPickerAyah
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.PlaybackMode
import com.myvault.app.data.quran.audio.QuranAudioPlayer
import com.myvault.app.data.quran.audio.QuranAudioRepository
import com.myvault.app.data.quran.audio.SurahDownloadState
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
    private val quranAudioRepository: QuranAudioRepository,
    private val quranAudioPlayer: QuranAudioPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuranReaderUiState())
    val uiState: StateFlow<QuranReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastSavedPosition: Pair<Int, Int>? = null
    private var audioProgressJob: Job? = null

    init {
        viewModelScope.launch {
            val preferences = vaultPreferences.userPreferences.first()
            loadSurah(
                surahNumber = preferences.quranLastReadSurah,
                restoredAyah = preferences.quranLastReadAyah,
                fontPercent = preferences.quranArabicFontPercent,
                translationFontPercent = preferences.quranTranslationFontPercent,
                translationEnabled = preferences.quranTranslationEnabled,
                tajweedEnabled = preferences.quranTajweedEnabled,
                bookmarkedVerseKeys = preferences.quranBookmarkedVerses,
                recentLocations = preferences.quranRecentLocations,
            )
            _uiState.value = _uiState.value.copy(
                audioPlaybackSpeed = preferences.quranAudioPlaybackSpeed.coerceIn(0.5f, 2f),
            )
            launch {
                val reciters = runCatching { quranAudioRepository.getSupportedReciters() }.getOrDefault(emptyList())
                val selectedReciter = reciters.firstOrNull { it.id == preferences.quranAudioReciterId }
                    ?: reciters.firstOrNull()
                _uiState.value = _uiState.value.copy(
                availableReciters = reciters,
                selectedAudioReciter = selectedReciter,
                )
            }
        }
        viewModelScope.launch {
            quranAudioPlayer.playbackState.collect { playback ->
                val current = _uiState.value
                _uiState.value = current.copy(miniPlayer = current.buildMiniPlayer(playback))
            }
        }
        viewModelScope.launch {
            quranAudioRepository.surahDownloadStates.collect { states ->
                _uiState.value = _uiState.value.copy(audioDownloadStates = states)
            }
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
            translationFontPercent = _uiState.value.translationFontPercent,
            translationEnabled = _uiState.value.translationEnabled,
            tajweedEnabled = _uiState.value.tajweedEnabled,
            bookmarkedVerseKeys = _uiState.value.bookmarkedVerseKeys,
            recentLocations = _uiState.value.recentLocations,
        )
    }

    fun openBookmarkedAyah(verseKey: String) {
        val surah = verseKey.substringBefore(':').toIntOrNull() ?: return
        val ayah = verseKey.substringAfter(':').toIntOrNull() ?: return
        loadSurah(
            surahNumber = surah,
            restoredAyah = ayah,
            fontPercent = _uiState.value.arabicFontPercent,
            translationFontPercent = _uiState.value.translationFontPercent,
            translationEnabled = _uiState.value.translationEnabled,
            tajweedEnabled = _uiState.value.tajweedEnabled,
            bookmarkedVerseKeys = _uiState.value.bookmarkedVerseKeys,
            recentLocations = _uiState.value.recentLocations,
        )
    }

    fun updateLastReadPosition(surahNumber: Int, ayahNumber: Int) {
        val safeSurah = surahNumber.coerceAtLeast(1)
        val safeAyah = ayahNumber.coerceAtLeast(1)
        val position = safeSurah to safeAyah
        if (lastSavedPosition == position) return
        lastSavedPosition = position
        _uiState.value = _uiState.value.copy(
            restoredAyah = safeAyah,
            recentLocations = _uiState.value.recentLocations.updatedWith(safeSurah, safeAyah),
        )
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

    fun setTranslationEnabled(enabled: Boolean) {
        if (_uiState.value.translationEnabled == enabled) return
        _uiState.value = _uiState.value.copy(translationEnabled = enabled)
        viewModelScope.launch {
            vaultPreferences.setQuranTranslationEnabled(enabled)
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

    fun openReciterPicker(ayah: QuranAyah) {
        _uiState.value = _uiState.value.copy(
            reciterPickerAyah = AudioPickerAyah(verseKey = ayah.verseKey, ayahNumber = ayah.ayahNumber),
            audioStatusMessage = null,
            audioStatusIsError = false,
        )
        if (_uiState.value.availableReciters.isEmpty()) {
            viewModelScope.launch {
                val reciters = runCatching { quranAudioRepository.getSupportedReciters() }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    availableReciters = reciters,
                    selectedAudioReciter = _uiState.value.selectedAudioReciter ?: reciters.firstOrNull(),
                )
            }
        }
    }

    fun dismissReciterPicker() {
        _uiState.value = _uiState.value.copy(reciterPickerAyah = null)
    }

    fun playWithReciter(reciter: AudioReciterUiModel) {
        val pickerAyah = _uiState.value.reciterPickerAyah ?: return
        _uiState.value = _uiState.value.copy(
            reciterPickerAyah = null,
            selectedAudioReciter = reciter,
        )
        viewModelScope.launch {
            vaultPreferences.setQuranAudioReciterId(reciter.id)
        }
        playAudio(pickerAyah.verseKey, reciter)
    }

    fun playAudioForAyah(ayah: QuranAyah) {
        if (_uiState.value.playingVerseKey == ayah.verseKey && quranAudioPlayer.hasActiveMedia()) {
            toggleAudioPlayback()
            return
        }
        val selected = _uiState.value.selectedAudioReciter
        if (selected == null) {
            openReciterPicker(ayah)
        } else {
            playAudio(ayah.verseKey, selected)
        }
    }

    fun chooseOtherReciterForCurrentAudio() {
        val verseKey = _uiState.value.playingVerseKey ?: return
        val ayah = _uiState.value.ayahs.firstOrNull { it.verseKey == verseKey } ?: return
        openReciterPicker(ayah)
    }

    fun toggleAudioPlayback() {
        if (quranAudioPlayer.isPlaying()) {
            quranAudioPlayer.pause()
        } else if (quranAudioPlayer.hasActiveMedia()) {
            quranAudioPlayer.resume()
        } else {
            val verseKey = _uiState.value.playingVerseKey ?: _uiState.value.ayahs.firstOrNull()?.verseKey ?: return
            val selected = _uiState.value.selectedAudioReciter ?: return
            playAudio(verseKey, selected)
        }
    }

    fun stopAudio() {
        audioProgressJob?.cancel()
        quranAudioPlayer.stop()
        _uiState.value = _uiState.value.copy(
            playingVerseKey = null,
            audioLoadingVerseKey = null,
            miniPlayer = null,
        )
    }

    fun seekAudioTo(positionMs: Long) {
        quranAudioPlayer.seekTo(positionMs)
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2f)
        quranAudioPlayer.setPlaybackSpeed(clamped)
        _uiState.value = _uiState.value.copy(
            audioPlaybackSpeed = clamped,
            miniPlayer = _uiState.value.miniPlayer?.copy(playbackSpeed = clamped),
        )
        viewModelScope.launch {
            vaultPreferences.setQuranAudioPlaybackSpeed(clamped)
        }
    }

    fun skipAudioBy(deltaMs: Long) {
        quranAudioPlayer.seekTo(quranAudioPlayer.currentPositionMs() + deltaMs)
    }

    fun playAdjacentAudio(delta: Int) {
        val currentVerseKey = _uiState.value.playingVerseKey ?: return
        val currentIndex = _uiState.value.ayahs.indexOfFirst { it.verseKey == currentVerseKey }
        val nextAyah = _uiState.value.ayahs.getOrNull(currentIndex + delta) ?: return
        val selected = _uiState.value.selectedAudioReciter ?: return
        playAudio(nextAyah.verseKey, selected)
    }

    fun createReflectionNoteForAyah(ayah: QuranAyah, title: String, body: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val folderId = folderRepository.ensureRootFolderForMode(
                name = "Quran Reflections",
                mode = FOLDER_MODE_STUDY,
            )
            val surah = _uiState.value.selectedSurah
            val reference = "${surah.name} ${surah.num}:${ayah.ayahNumber}"
            val noteTitle = title.ifBlank { "Reflection on $reference" }
            val noteId = noteRepository.createNote(folderId = folderId, title = noteTitle)
            val noteBody = buildString {
                append(noteTitle)
                append("\n\n")
                append("Source: ")
                append(reference)
                append("\n\n")
                append(ayah.arabicText)
                if (ayah.translation.isNotBlank()) {
                    append("\n\n")
                    append(ayah.translation)
                }
                append("\n\n")
                append(body.ifBlank { "Reflection:" })
            }
            noteRepository.saveRichText(noteId = noteId, text = noteBody, styleMarksJson = "[]")
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

    private fun playAudio(verseKey: String, reciter: AudioReciterUiModel) {
        val surah = _uiState.value.selectedSurah
        val ayah = _uiState.value.ayahs.firstOrNull { it.verseKey == verseKey } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                audioLoadingVerseKey = verseKey,
                audioStatusMessage = "Preparing audio...",
                audioStatusIsError = false,
            )
            runCatching {
                val metadata = quranAudioRepository.getChapterAudio(reciter, surah.num)
                val file = quranAudioRepository.ensurePlaybackFile(metadata, verseKey)
                val startMs = metadata.timestamps[verseKey] ?: 0L
                quranAudioPlayer.play(
                    file = file,
                    startMs = startMs,
                    speed = _uiState.value.audioPlaybackSpeed,
                    verseByVerse = metadata.mode == PlaybackMode.VerseByVerse,
                    onStarted = {
                        val playbackState = quranAudioPlayer.playbackState.value
                        _uiState.value = _uiState.value.copy(
                            playingVerseKey = verseKey,
                            audioLoadingVerseKey = null,
                            audioStatusMessage = null,
                            audioStatusIsError = false,
                            miniPlayer = AudioMiniPlayerUiState(
                                verseKey = verseKey,
                                ayahNumber = ayah.ayahNumber,
                                reciterName = reciter.name,
                                isPlaying = true,
                                playbackSpeed = _uiState.value.audioPlaybackSpeed,
                                progressMs = playbackState.currentPositionMs,
                                durationMs = playbackState.durationMs,
                            ),
                        )
                        startAudioProgressTicker()
                        updateLastReadPosition(surah.num, ayah.ayahNumber)
                    },
                    onCompleted = {
                        _uiState.value = _uiState.value.copy(playingVerseKey = null, audioLoadingVerseKey = null)
                    },
                    onError = {
                        _uiState.value = _uiState.value.copy(
                            playingVerseKey = null,
                            audioLoadingVerseKey = null,
                            audioStatusMessage = "Audio could not be played.",
                            audioStatusIsError = true,
                        )
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    playingVerseKey = null,
                    audioLoadingVerseKey = null,
                    audioStatusMessage = error.message ?: "Audio could not be prepared.",
                    audioStatusIsError = true,
                )
            }
        }
    }

    private fun startAudioProgressTicker() {
        audioProgressJob?.cancel()
        audioProgressJob = viewModelScope.launch {
            while (quranAudioPlayer.hasActiveMedia()) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    miniPlayer = current.buildMiniPlayer(quranAudioPlayer.playbackState.value),
                )
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    fun refreshAudioDownloadStates(reciter: AudioReciterUiModel) {
        viewModelScope.launch {
            com.myvault.app.data.quran.quranCatalog.forEach { surah ->
                quranAudioRepository.refreshSurahDownloadState(reciter, surah.num)
            }
        }
    }

    fun downloadSurahAudio(reciter: AudioReciterUiModel, surahNumber: Int) {
        viewModelScope.launch {
            quranAudioRepository.downloadSurah(reciter, surahNumber)
        }
    }

    fun setArabicFontPercentFromSlider(percent: Int) {
        setArabicFontPercent(percent)
    }

    fun setTranslationFontPercent(percent: Int) {
        val clamped = percent.coerceIn(80, 130)
        if (_uiState.value.translationFontPercent == clamped) return
        _uiState.value = _uiState.value.copy(translationFontPercent = clamped)
        viewModelScope.launch {
            vaultPreferences.setQuranTranslationFontPercent(clamped)
        }
    }

    private fun loadSurah(
        surahNumber: Int,
        restoredAyah: Int,
        fontPercent: Int,
        translationFontPercent: Int,
        translationEnabled: Boolean,
        tajweedEnabled: Boolean,
        bookmarkedVerseKeys: Set<String>,
        recentLocations: List<com.myvault.app.data.quran.QuranRecentLocation>,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val surah = quranCatalogRepository.surah(surahNumber) ?: quranCatalogRepository.surahs().first()
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                restoredAyah = restoredAyah.coerceIn(1, surah.ayat),
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                translationFontPercent = translationFontPercent.coerceIn(80, 130),
                translationEnabled = translationEnabled,
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                recentLocations = recentLocations,
                expandedTafsirVerseKey = null,
                loading = true,
            )
            val ayahs = quranTextRepository.getSurahAyahs(surah.num)
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                ayahs = ayahs,
                restoredAyah = restoredAyah.coerceIn(1, ayahs.lastOrNull()?.ayahNumber ?: surah.ayat),
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                translationFontPercent = translationFontPercent.coerceIn(80, 130),
                translationEnabled = translationEnabled,
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                recentLocations = recentLocations,
                expandedTafsirVerseKey = null,
                loading = false,
            )
        }
    }

    override fun onCleared() {
        quranAudioPlayer.release()
        super.onCleared()
    }
}

private fun QuranReaderUiState.buildMiniPlayer(
    playback: QuranAudioPlayer.PlaybackState,
): AudioMiniPlayerUiState? {
    val verseKey = playingVerseKey ?: return null
    if (!playback.hasActiveMedia) return null
    val ayahNumber = ayahs.firstOrNull { it.verseKey == verseKey }?.ayahNumber ?: return null
    return AudioMiniPlayerUiState(
        verseKey = verseKey,
        ayahNumber = ayahNumber,
        reciterName = selectedAudioReciter?.name.orEmpty(),
        isPlaying = playback.isPlaying,
        playbackSpeed = audioPlaybackSpeed,
        progressMs = playback.currentPositionMs,
        durationMs = playback.durationMs,
    )
}

private fun List<com.myvault.app.data.quran.QuranRecentLocation>.updatedWith(
    surahNumber: Int,
    ayahNumber: Int,
): List<com.myvault.app.data.quran.QuranRecentLocation> =
    (
        listOf(
            com.myvault.app.data.quran.QuranRecentLocation(
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                lastReadAt = System.currentTimeMillis(),
            ),
        ) + filterNot { it.surahNumber == surahNumber }
    ).sortedByDescending { it.lastReadAt }.take(5)
