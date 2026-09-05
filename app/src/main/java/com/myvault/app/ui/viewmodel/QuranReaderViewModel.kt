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
import com.myvault.app.data.quran.audio.QuranListeningMode
import com.myvault.app.data.quran.audio.QuranPlaybackController
import com.myvault.app.data.quran.audio.QuranPlaybackStatus
import com.myvault.app.data.quran.audio.QuranAudioRepository
import com.myvault.app.data.quran.audio.SurahDownloadState
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationRepeatMode
import com.myvault.app.data.quran.memorization.QuranMemorizationSavedAttempt
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
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@HiltViewModel
class QuranReaderViewModel @Inject constructor(
    private val quranCatalogRepository: QuranCatalogRepository,
    private val quranTextRepository: QuranTextRepository,
    private val vaultPreferences: VaultPreferences,
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val quranReflectionRepository: QuranReflectionRepository,
    private val quranAudioRepository: QuranAudioRepository,
    private val playbackController: QuranPlaybackController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuranReaderUiState())
    val uiState: StateFlow<QuranReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastSavedPosition: Pair<Int, Int>? = null
    private var reciterPickerStartsPlayback = true
    private val initialNavigation = QuranInitialNavigation()

    init {
        viewModelScope.launch {
            val preferences = vaultPreferences.userPreferences.first()
            val target = initialNavigation.initialize(preferences.quranLastReadSurah, preferences.quranLastReadAyah)
            loadSurah(
                surahNumber = target.surah,
                restoredAyah = target.ayah,
                pendingScrollVerseKey = if (target.exact) "${target.surah}:${target.ayah}" else null,
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
            playbackController.state.collect { playback ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    playingVerseKey = playback.verseKey,
                    audioLoadingVerseKey = playback.verseKey.takeIf { playback.status == QuranPlaybackStatus.Preparing },
                    audioStatusMessage = playback.message,
                    audioStatusIsError = playback.status == QuranPlaybackStatus.Error,
                    selectedAudioReciter = playback.reciter ?: current.selectedAudioReciter,
                    miniPlayer = (playback.verseKey ?: "${playback.surah}:1".takeIf { playback.active })?.let { key ->
                        AudioMiniPlayerUiState(key, key.substringAfter(':').toInt(), playback.reciter?.name.orEmpty(),
                            playback.isPlaying, playback.speed, playback.positionMs, playback.durationMs,
                            playback.mode, playback.synchronized)
                    },
                )
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
        if (initialNavigation.request(surahNumber, restoredAyah) == null) return
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
        if (initialNavigation.request(surah, ayah) == null) return
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
        val shouldStartPlayback = reciterPickerStartsPlayback || activeVerseKey != null || playbackController.state.value.active
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
            playAudio(activeVerseKey ?: pickerAyah.verseKey, reciter, playbackController.state.value.mode)
        }
    }

    fun playAudioForAyah(ayah: QuranAyah) {
        if (_uiState.value.playingVerseKey == ayah.verseKey && playbackController.state.value.active) {
            if (playbackController.state.value.mode != QuranListeningMode.ThisAyah) {
                playbackController.setMode(QuranListeningMode.ThisAyah)
                return
            }
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

    fun toggleAudioPlayback() = playbackController.toggle()

    fun stopAudio() = playbackController.stop()

    fun seekAudioTo(positionMs: Long) = playbackController.seek(positionMs)

    fun setAudioListeningMode(mode: QuranListeningMode) = playbackController.setMode(mode)

    fun setAudioPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2f)
        playbackController.speed(clamped)
        _uiState.value = _uiState.value.copy(audioPlaybackSpeed = clamped)
        viewModelScope.launch { vaultPreferences.setQuranAudioPlaybackSpeed(clamped) }
    }

    fun skipAudioBy(deltaMs: Long) = playbackController.seek(playbackController.state.value.positionMs + deltaMs)

    fun playAdjacentAudio(delta: Int) = playbackController.adjacent(delta)

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

    private fun playAudio(verseKey: String, reciter: AudioReciterUiModel, mode: QuranListeningMode = QuranListeningMode.ThisAyah) {
        val surah = verseKey.substringBefore(':').toIntOrNull() ?: return
        val ayah = verseKey.substringAfter(':').toIntOrNull() ?: return
        playbackController.requestPlay(surah, ayah, reciter, mode)
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

    suspend fun getAyahSearchIndex(): Map<String, String> = quranTextRepository.getAyahSearchIndex()

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
            coroutineContext.ensureActive()
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
            coroutineContext.ensureActive()
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

}

internal fun normalizedQuranVerseKey(surahNumber: Int, requestedAyah: Int, lastAyah: Int): String =
    "$surahNumber:${requestedAyah.coerceIn(1, lastAyah.coerceAtLeast(1))}"

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
