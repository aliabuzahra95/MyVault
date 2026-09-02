package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.QuranCatalogRepository
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReflectionRepository
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.QuranTextRepository
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.data.quran.MUKHTASAR_TAFSIR_ID
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioPickerAyah
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.PlaybackMode
import com.myvault.app.data.quran.audio.QuranAudioPlayer
import com.myvault.app.data.quran.audio.QuranAudioRepository
import com.myvault.app.data.quran.audio.SurahDownloadState
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationRepeatMode
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranMemorizationSavedAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.data.quran.memorization.toSavedAttempt
import com.myvault.app.data.quran.memorization.toStatusSnapshot
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.quran.tafsirCacheKey
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
    private val quranReflectionRepository: QuranReflectionRepository,
    private val quranAudioRepository: QuranAudioRepository,
    private val quranAudioPlayer: QuranAudioPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuranReaderUiState())
    val uiState: StateFlow<QuranReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastSavedPosition: Pair<Int, Int>? = null
    private var audioProgressJob: Job? = null
    private var audioPrepareJob: Job? = null
    private var audioRequestGeneration = 0L
    private var reciterPickerStartsPlayback = true

    init {
        viewModelScope.launch {
            val preferences = vaultPreferences.userPreferences.first()
            loadSurah(
                surahNumber = preferences.quranLastReadSurah,
                restoredAyah = preferences.quranLastReadAyah,
                fontPercent = preferences.quranArabicFontPercent,
                translationFontPercent = preferences.quranTranslationFontPercent,
                translationEnabled = preferences.quranTranslationEnabled,
                translationSource = QuranTranslationSource.fromStoredValue(preferences.quranTranslationSource),
                tajweedEnabled = preferences.quranTajweedEnabled,
                bookmarkedVerseKeys = preferences.quranBookmarkedVerses,
                recentLocations = preferences.quranRecentLocations,
            )
            _uiState.value = _uiState.value.copy(
                audioPlaybackSpeed = preferences.quranAudioPlaybackSpeed.coerceIn(0.5f, 2f),
            )
            launch {
                val tafsirSources = runCatching { quranTextRepository.getAvailableTafsirSources() }
                    .getOrDefault(emptyList())
                val selectedSourceId = preferences.quranTafsirSourceId
                    .takeIf { sourceId -> tafsirSources.any { it.id == sourceId } }
                    ?: tafsirSources.firstOrNull()?.id
                    ?: MUKHTASAR_TAFSIR_ID
                _uiState.value = _uiState.value.copy(
                    availableTafsirSources = tafsirSources,
                    selectedTafsirSourceId = selectedSourceId,
                )
            }
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
        viewModelScope.launch {
            quranReflectionRepository.observeReflectionItems().collect { reflections ->
                _uiState.value = _uiState.value.copy(reflectionsByVerse = reflections.groupBy { it.verseKey })
            }
        }
        viewModelScope.launch {
            vaultPreferences.userPreferences.collect { preferences ->
                _uiState.value = _uiState.value.copy(
                    memorizationRecords = preferences.quranMemorizationRecords,
                    memorizationAttemptStatuses = preferences.quranMemorizationAttempts.latestStatusSnapshots(),
                    surahMemorizationAttempts = preferences.quranSurahMemorizationAttempts,
                )
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
            translationSource = _uiState.value.translationSource,
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
            pendingScrollVerseKey = verseKey,
            fontPercent = _uiState.value.arabicFontPercent,
            translationFontPercent = _uiState.value.translationFontPercent,
            translationEnabled = _uiState.value.translationEnabled,
            translationSource = _uiState.value.translationSource,
            tajweedEnabled = _uiState.value.tajweedEnabled,
            bookmarkedVerseKeys = _uiState.value.bookmarkedVerseKeys,
            recentLocations = _uiState.value.recentLocations,
        )
    }

    fun consumePendingScrollVerse() {
        if (_uiState.value.pendingScrollVerseKey != null) {
            _uiState.value = _uiState.value.copy(pendingScrollVerseKey = null)
        }
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

    fun setTranslationSource(source: QuranTranslationSource) {
        if (_uiState.value.translationSource == source && !_uiState.value.translationSourceLoading) return
        viewModelScope.launch {
            vaultPreferences.setQuranTranslationSource(source)
        }
        loadSurah(
            surahNumber = _uiState.value.selectedSurah.num,
            restoredAyah = _uiState.value.restoredAyah,
            fontPercent = _uiState.value.arabicFontPercent,
            translationFontPercent = _uiState.value.translationFontPercent,
            translationEnabled = _uiState.value.translationEnabled,
            translationSource = source,
            tajweedEnabled = _uiState.value.tajweedEnabled,
            bookmarkedVerseKeys = _uiState.value.bookmarkedVerseKeys,
            recentLocations = _uiState.value.recentLocations,
        )
    }

    fun toggleTafsir(verseKey: String) {
        val nextExpanded = if (_uiState.value.expandedTafsirVerseKey == verseKey) null else verseKey
        _uiState.value = _uiState.value.copy(expandedTafsirVerseKey = nextExpanded)
        if (nextExpanded == null) {
            return
        }
        loadTafsirIfNeeded(verseKey, _uiState.value.selectedTafsirSourceId)
    }

    fun selectTafsirSource(sourceId: Int) {
        if (_uiState.value.selectedTafsirSourceId == sourceId) {
            _uiState.value.expandedTafsirVerseKey?.let { loadTafsirIfNeeded(it, sourceId) }
            return
        }
        _uiState.value = _uiState.value.copy(selectedTafsirSourceId = sourceId)
        viewModelScope.launch {
            vaultPreferences.setQuranTafsirSourceId(sourceId)
        }
        val expandedVerse = _uiState.value.expandedTafsirVerseKey ?: return
        loadTafsirIfNeeded(expandedVerse, sourceId)
    }

    private fun loadTafsirIfNeeded(verseKey: String, sourceId: Int) {
        val cacheKey = tafsirCacheKey(verseKey, sourceId)
        if (cacheKey in _uiState.value.tafsirByVerse || cacheKey in _uiState.value.loadingTafsirVerseKeys) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingTafsirVerseKeys = _uiState.value.loadingTafsirVerseKeys + cacheKey,
            )
            val tafsir = runCatching { quranTextRepository.getTafsir(verseKey, sourceId) }.getOrDefault("")
            _uiState.value = _uiState.value.copy(
                tafsirByVerse = if (tafsir.isNotBlank()) _uiState.value.tafsirByVerse + (cacheKey to tafsir) else _uiState.value.tafsirByVerse,
                loadingTafsirVerseKeys = _uiState.value.loadingTafsirVerseKeys - cacheKey,
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
        reciterPickerStartsPlayback = true
        showReciterPicker(ayah)
    }

    fun openReciterPreferencePicker(ayah: QuranAyah) {
        reciterPickerStartsPlayback = false
        showReciterPicker(ayah)
    }

    private fun showReciterPicker(ayah: QuranAyah) {
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
        reciterPickerStartsPlayback = true
        _uiState.value = _uiState.value.copy(reciterPickerAyah = null)
    }

    fun playWithReciter(reciter: AudioReciterUiModel) {
        val pickerAyah = _uiState.value.reciterPickerAyah ?: return
        val activeVerseKey = _uiState.value.playingVerseKey
        val shouldStartPlayback = reciterPickerStartsPlayback || activeVerseKey != null || quranAudioPlayer.hasActiveMedia()
        reciterPickerStartsPlayback = true
        _uiState.value = _uiState.value.copy(
            reciterPickerAyah = null,
            selectedAudioReciter = reciter,
            miniPlayer = _uiState.value.miniPlayer?.copy(reciterName = reciter.name),
        )
        viewModelScope.launch {
            vaultPreferences.setQuranAudioReciterId(reciter.id)
        }
        if (shouldStartPlayback) {
            playAudio(activeVerseKey ?: pickerAyah.verseKey, reciter)
        }
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
        audioRequestGeneration += 1
        audioPrepareJob?.cancel()
        audioPrepareJob = null
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
            val existing = _uiState.value.reflectionsByVerse[ayah.verseKey]?.firstOrNull()
            if (existing != null) {
                persistReflection(existing.noteId, ayah, title, body)
                onCreated(existing.noteId)
                return@launch
            }
            val folderId = folderRepository.ensureRootFolderForMode(
                name = "Quran Reflections",
                mode = FOLDER_MODE_STUDY,
            )
            val surah = _uiState.value.selectedSurah
            val reference = "${surah.name} ${surah.num}:${ayah.ayahNumber}"
            val noteTitle = title.ifBlank { "Reflection on $reference" }
            val noteId = noteRepository.createNote(folderId = folderId, title = noteTitle)
            val noteBody = buildReflectionNoteBody(noteTitle, reference, ayah, body)
            noteRepository.saveRichText(noteId = noteId, text = noteBody, styleMarksJson = "[]")
            onCreated(noteId)
        }
    }

    fun updateReflectionForAyah(noteId: String, ayah: QuranAyah, title: String, body: String) {
        viewModelScope.launch {
            persistReflection(noteId, ayah, title, body)
        }
    }

    fun deleteReflection(noteId: String) {
        viewModelScope.launch { noteRepository.deleteNote(noteId) }
    }

    private suspend fun persistReflection(noteId: String, ayah: QuranAyah, title: String, body: String) {
        val surah = _uiState.value.selectedSurah
        val reference = "${surah.name} ${surah.num}:${ayah.ayahNumber}"
        val noteTitle = title.ifBlank { "Reflection on $reference" }
        val noteBody = buildReflectionNoteBody(noteTitle, reference, ayah, body)
        noteRepository.updateTitle(noteId, noteTitle)
        noteRepository.saveRichText(noteId = noteId, text = noteBody, styleMarksJson = "[]")
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
        val requestGeneration = ++audioRequestGeneration
        audioPrepareJob?.cancel()
        audioProgressJob?.cancel()
        quranAudioPlayer.stop()
        _uiState.value = _uiState.value.copy(
            selectedAudioReciter = reciter,
            playingVerseKey = verseKey,
            audioLoadingVerseKey = verseKey,
            audioStatusMessage = "Preparing audio...",
            audioStatusIsError = false,
            miniPlayer = AudioMiniPlayerUiState(
                verseKey = verseKey,
                ayahNumber = ayah.ayahNumber,
                reciterName = reciter.name,
                isPlaying = false,
                playbackSpeed = _uiState.value.audioPlaybackSpeed,
                progressMs = 0L,
                durationMs = 0L,
            ),
        )
        audioPrepareJob = viewModelScope.launch {
            runCatching {
                val metadata = quranAudioRepository.getChapterAudio(reciter, surah.num)
                val file = quranAudioRepository.ensurePlaybackFile(metadata, verseKey)
                if (requestGeneration != audioRequestGeneration) return@launch
                val startMs = metadata.timestamps[verseKey] ?: 0L
                quranAudioPlayer.play(
                    file = file,
                    startMs = startMs,
                    speed = _uiState.value.audioPlaybackSpeed,
                    verseByVerse = metadata.mode == PlaybackMode.VerseByVerse,
                    onStarted = {
                        if (requestGeneration != audioRequestGeneration) return@play
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
                    },
                    onCompleted = {
                        if (requestGeneration != audioRequestGeneration) return@play
                        _uiState.value = _uiState.value.copy(playingVerseKey = null, audioLoadingVerseKey = null)
                    },
                    onError = {
                        if (requestGeneration != audioRequestGeneration) return@play
                        _uiState.value = _uiState.value.copy(
                            playingVerseKey = null,
                            audioLoadingVerseKey = null,
                            audioStatusMessage = "Audio could not be played.",
                            audioStatusIsError = true,
                        )
                    },
                )
            }.onFailure { error ->
                if (requestGeneration != audioRequestGeneration) return@onFailure
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
                val playback = quranAudioPlayer.playbackState.value.copy(
                    currentPositionMs = quranAudioPlayer.currentPositionMs(),
                    durationMs = quranAudioPlayer.durationMs(),
                )
                _uiState.value = current.copy(
                    miniPlayer = current.buildMiniPlayer(playback),
                )
                kotlinx.coroutines.delay(250L)
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

    fun startMemorizingAyah(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            (existing ?: ayah.toMemorizationRecord(now)).copy(
                lastReviewedAt = now,
                reviewCount = (existing?.reviewCount ?: 0).coerceAtLeast(0),
                isMemorising = true,
                updatedAt = now,
            )
        }
    }

    fun removeMemorizingAyah(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                isMemorising = false,
                updatedAt = now,
            )
        }
    }

    fun toggleMemorizedAyah(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                memorizedAt = if (current.isMemorized) null else now,
                lastReviewedAt = now,
                reviewCount = current.reviewCount + 1,
                isNeedsRevision = if (current.isMemorized) current.isNeedsRevision else false,
                isIncorrect = if (current.isMemorized) current.isIncorrect else false,
                updatedAt = now,
            )
        }
    }

    fun markRevisedAyah(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                lastReviewedAt = now,
                reviewCount = current.reviewCount + 1,
                isNeedsRevision = false,
                isRevision = false,
                isIncorrect = false,
                updatedAt = now,
            )
        }
    }

    fun markCurrentSurahMemorized() {
        viewModelScope.launch {
            val state = _uiState.value
            val surah = state.selectedSurah
            val now = System.currentTimeMillis()
            val currentRecords = vaultPreferences.userPreferences.first().quranMemorizationRecords
            val existingByVerse = currentRecords.associateBy { it.verseKey }
            val shouldUnmark = (1..surah.ayat).all { ayahNumber ->
                existingByVerse["${surah.num}:$ayahNumber"]?.isMemorized == true
            }
            val surahRecords = (1..surah.ayat).map { ayahNumber ->
                val verseKey = "${surah.num}:$ayahNumber"
                val existing = existingByVerse[verseKey]
                existing?.copy(
                    lastReviewedAt = now,
                    reviewCount = if (existing.reviewCount == 0) 1 else existing.reviewCount,
                    memorizedAt = if (shouldUnmark) null else existing.memorizedAt ?: now,
                    updatedAt = now,
                ) ?: MemorizationRecord(
                    verseKey = verseKey,
                    surahNumber = surah.num,
                    ayahNumber = ayahNumber,
                    startedAt = now,
                    lastReviewedAt = now,
                    reviewCount = 1,
                    memorizedAt = if (shouldUnmark) null else now,
                    isRevision = false,
                    isWeak = false,
                    updatedAt = now,
                )
            }
            val updated = (currentRecords.filterNot { it.surahNumber == surah.num } + surahRecords)
                .sortedWith(compareBy<MemorizationRecord> { it.surahNumber }.thenBy { it.ayahNumber })
            _uiState.value = _uiState.value.copy(memorizationRecords = updated)
            vaultPreferences.setQuranMemorizationRecords(updated)
        }
    }

    fun toggleWeakMemorization(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                isWeak = !current.isWeak,
                updatedAt = now,
            )
        }
    }

    fun toggleNeedsRevisionMemorization(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                isNeedsRevision = !current.isNeedsRevision,
                isRevision = !current.isNeedsRevision,
                isIncorrect = if (!current.isNeedsRevision) false else current.isIncorrect,
                updatedAt = now,
            )
        }
    }

    fun toggleIncorrectMemorization(ayah: QuranAyah) {
        updateMemorizationRecord(ayah) { existing, now ->
            val current = existing ?: ayah.toMemorizationRecord(now)
            current.copy(
                isIncorrect = !current.isIncorrect,
                isNeedsRevision = if (!current.isIncorrect) false else current.isNeedsRevision,
                isRevision = if (!current.isIncorrect) false else current.isRevision,
                updatedAt = now,
            )
        }
    }

    fun setMemorizationConcealAmount(verseKey: String, amount: MemorizationConcealAmount?) {
        _uiState.value = _uiState.value.copy(
            memorizationConcealedVerseKey = if (amount == null) null else verseKey,
            memorizationConcealAmount = amount,
        )
    }

    fun setMemorizationRepeatMode(ayah: QuranAyah, mode: MemorizationRepeatMode) {
        _uiState.value = _uiState.value.copy(
            memorizationRepeatVerseKey = ayah.verseKey,
            memorizationRepeatMode = mode,
        )
        playAudioForAyah(ayah)
    }

    fun stopMemorizationRepeat() {
        _uiState.value = _uiState.value.copy(
            memorizationRepeatVerseKey = null,
            memorizationRepeatMode = null,
        )
    }

    fun recordAiListenAttempt(attempt: QuranMemorizationAttempt) {
        val savedAttempt = attempt.toSavedAttempt()
        _uiState.value = _uiState.value.copy(
            memorizationAttemptStatuses = _uiState.value.memorizationAttemptStatuses + (savedAttempt.verseKey to savedAttempt.toStatusSnapshot()),
        )
        viewModelScope.launch {
            vaultPreferences.addQuranMemorizationAttempt(savedAttempt)
        }
    }

    fun recordSurahTestAttempt(attempt: QuranSurahMemorizationAttempt) {
        val savedAttempt = attempt.toSavedAttempt()
        _uiState.value = _uiState.value.copy(
            surahMemorizationAttempts = (
                _uiState.value.surahMemorizationAttempts
                    .filterNot { it.attemptId == savedAttempt.attemptId } + savedAttempt
                ).sortedByDescending { it.timestampMs },
        )
        viewModelScope.launch {
            vaultPreferences.addQuranSurahMemorizationAttempt(savedAttempt)
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
        pendingScrollVerseKey: String? = null,
        fontPercent: Int,
        translationFontPercent: Int,
        translationEnabled: Boolean,
        translationSource: QuranTranslationSource,
        tajweedEnabled: Boolean,
        bookmarkedVerseKeys: Set<String>,
        recentLocations: List<com.myvault.app.data.quran.QuranRecentLocation>,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val surah = quranCatalogRepository.surah(surahNumber) ?: quranCatalogRepository.surahs().first()
            val safeRestoredAyah = restoredAyah.coerceIn(1, surah.ayat)
            val safePendingScrollVerseKey = pendingScrollVerseKey?.let {
                normalizedQuranVerseKey(surah.num, safeRestoredAyah, surah.ayat)
            }
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                restoredAyah = safeRestoredAyah,
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                translationFontPercent = translationFontPercent.coerceIn(80, 130),
                translationEnabled = translationEnabled,
                translationSourceLoading = translationSource != _uiState.value.translationSource,
                translationSourceMessage = null,
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                recentLocations = recentLocations,
                pendingScrollVerseKey = safePendingScrollVerseKey,
                expandedTafsirVerseKey = null,
                loading = true,
            )
            val requestedAyahs = runCatching {
                quranTextRepository.getSurahAyahs(surah.num, translationSource)
            }
            val (ayahs, loadedTranslationSource, translationMessage) = requestedAyahs.fold(
                onSuccess = { Triple(it, translationSource, null) },
                onFailure = {
                    if (translationSource == QuranTranslationSource.SahihInternational) {
                        _uiState.value = _uiState.value.copy(
                            loading = false,
                            translationSourceLoading = false,
                            translationSourceMessage = "The translation could not be loaded.",
                        )
                        return@launch
                    }
                    Triple(
                        quranTextRepository.getSurahAyahs(
                            surah.num,
                            QuranTranslationSource.SahihInternational,
                        ),
                        QuranTranslationSource.SahihInternational,
                        "Tafheem-ul-Quran could not be loaded. Sahih International is shown for now.",
                    )
                },
            )
            _uiState.value = _uiState.value.copy(
                selectedSurah = surah,
                ayahs = ayahs,
                restoredAyah = restoredAyah.coerceIn(1, ayahs.lastOrNull()?.ayahNumber ?: surah.ayat),
                arabicFontPercent = fontPercent.coerceIn(70, 140),
                translationFontPercent = translationFontPercent.coerceIn(80, 130),
                translationEnabled = translationEnabled,
                translationSource = loadedTranslationSource,
                translationSourceLoading = false,
                translationSourceMessage = translationMessage,
                tajweedEnabled = tajweedEnabled,
                bookmarkedVerseKeys = bookmarkedVerseKeys,
                recentLocations = recentLocations,
                pendingScrollVerseKey = pendingScrollVerseKey?.let {
                    normalizedQuranVerseKey(
                        surah.num,
                        restoredAyah,
                        ayahs.lastOrNull()?.ayahNumber ?: surah.ayat,
                    )
                },
                expandedTafsirVerseKey = null,
                loading = false,
            )

            if (loadedTranslationSource == QuranTranslationSource.Maududi) {
                val enrichedAyahs = runCatching {
                    quranTextRepository.refreshMaududiSurahAyahs(surah.num)
                }.getOrNull()
                val current = _uiState.value
                if (
                    enrichedAyahs != null &&
                    current.selectedSurah.num == surah.num &&
                    current.translationSource == QuranTranslationSource.Maududi
                ) {
                    _uiState.value = current.copy(ayahs = enrichedAyahs)
                }
            }
        }
    }

    private fun updateMemorizationRecord(
        ayah: QuranAyah,
        build: (MemorizationRecord?, Long) -> MemorizationRecord,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentRecords = vaultPreferences.userPreferences.first().quranMemorizationRecords
            val existing = currentRecords.firstOrNull { it.verseKey == ayah.verseKey }
            val updatedRecord = build(existing, now)
            val updated = (currentRecords.filterNot { it.verseKey == ayah.verseKey } + updatedRecord)
                .sortedWith(compareBy<MemorizationRecord> { it.surahNumber }.thenBy { it.ayahNumber })
            _uiState.value = _uiState.value.copy(memorizationRecords = updated)
            vaultPreferences.setQuranMemorizationRecords(updated)
        }
    }

    override fun onCleared() {
        quranAudioPlayer.release()
        super.onCleared()
    }
}

internal fun normalizedQuranVerseKey(surahNumber: Int, requestedAyah: Int, lastAyah: Int): String =
    "$surahNumber:${requestedAyah.coerceIn(1, lastAyah.coerceAtLeast(1))}"

private fun QuranReaderUiState.buildMiniPlayer(
    playback: QuranAudioPlayer.PlaybackState,
): AudioMiniPlayerUiState? {
    val verseKey = playingVerseKey ?: return null
    val ayahNumber = ayahs.firstOrNull { it.verseKey == verseKey }?.ayahNumber ?: return null
    if (!playback.hasActiveMedia && audioLoadingVerseKey != verseKey) return null
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

private fun QuranAyah.toMemorizationRecord(now: Long): MemorizationRecord =
    MemorizationRecord(
        verseKey = verseKey,
        surahNumber = surahNumber,
        ayahNumber = ayahNumber,
        startedAt = now,
        lastReviewedAt = now,
        reviewCount = 0,
        memorizedAt = null,
        isRevision = false,
        isWeak = false,
        updatedAt = now,
    )

private fun List<QuranMemorizationSavedAttempt>.latestStatusSnapshots() =
    groupBy { it.verseKey }
        .mapValues { (_, attempts) ->
            attempts.maxBy { it.timestampMs }.toStatusSnapshot()
        }

private fun buildReflectionNoteBody(title: String, reference: String, ayah: QuranAyah, body: String): String = buildString {
    append(title)
    append("\n\nSource: ")
    append(reference)
    append("\n\n")
    append(ayah.arabicText)
    if (ayah.translation.isNotBlank()) {
        append("\n\n")
        append(ayah.translation)
    }
    append("\n\nReflection:\n\n")
    append(body)
}
