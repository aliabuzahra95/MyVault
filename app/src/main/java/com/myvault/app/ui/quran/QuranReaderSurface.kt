package com.myvault.app.ui.quran

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.data.quran.arabicTextSize
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.memorization.AyahMemorizationStatus
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.data.quran.speech.SpeechRecognitionProviderType
import com.myvault.app.data.quran.tafsirCacheKey
import com.myvault.app.data.quran.translationTextSize
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QuranReaderSurface(
    uiState: QuranReaderUiState,
    onOpenSelector: () -> Unit,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTranslationSource: (QuranTranslationSource) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    onToggleTafsir: (String) -> Unit,
    onSelectTafsirSource: (Int) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onCreateReflectionNote: (QuranAyah, String, String) -> Unit,
    onUpdateReflection: (String, QuranAyah, String, String) -> Unit,
    onDeleteReflection: (String) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onOpenReciterPicker: (QuranAyah) -> Unit,
    onDismissReciterPicker: () -> Unit,
    onSelectAudioReciter: (AudioReciterUiModel) -> Unit,
    onPlayAudioForAyah: (QuranAyah) -> Unit,
    onToggleAudioPlayback: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudioTo: (Long) -> Unit,
    onSetAudioSpeed: (Float) -> Unit,
    onSkipAudioBy: (Long) -> Unit,
    onPlayAdjacentAudio: (Int) -> Unit,
    onChooseOtherReciter: () -> Unit,
    onRefreshAudioDownloads: (AudioReciterUiModel) -> Unit,
    onDownloadSurahAudio: (AudioReciterUiModel, Int) -> Unit,
    onStartMemorizingAyah: (QuranAyah) -> Unit,
    onRemoveMemorizingAyah: (QuranAyah) -> Unit,
    onToggleMemorizedAyah: (QuranAyah) -> Unit,
    onMarkRevisedAyah: (QuranAyah) -> Unit,
    onAiListenAttemptCompleted: (QuranMemorizationAttempt) -> Unit,
    onSurahTestAttemptCompleted: (QuranSurahMemorizationAttempt) -> Unit,
    onMarkCurrentSurahMemorized: () -> Unit,
    onToggleNeedsRevisionMemorization: (QuranAyah) -> Unit,
    onToggleIncorrectMemorization: (QuranAyah) -> Unit,
    onToggleWeakMemorization: (QuranAyah) -> Unit,
    onSetMemorizationConcealAmount: (String, MemorizationConcealAmount?) -> Unit,
    onPendingScrollHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val hasBismillahHeader = uiState.selectedSurah.num != 1 && uiState.selectedSurah.num != 9
    var lastScrolledSurah by rememberSaveable { mutableIntStateOf(-1) }
    var readerOptionsOpen by rememberSaveable { mutableStateOf(false) }
    var ayahActionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionEditTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var audioDownloadsOpen by rememberSaveable { mutableStateOf(false) }
    var memorizationPanelVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var aiListenVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var surahTestOpen by rememberSaveable { mutableStateOf(false) }
    var restoredSurahTestAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    var readerAyahReviewVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var readingPositionSavedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var quranWordDebugEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedSpeechProviderName by rememberSaveable { mutableStateOf(SpeechRecognitionProviderType.GoogleChirp.name) }
    val selectedSpeechProviderType = remember(selectedSpeechProviderName) {
        SpeechRecognitionProviderType.fromName(selectedSpeechProviderName)
    }
    val readerOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memorizationRecordsByVerse = remember(uiState.memorizationRecords) {
        uiState.memorizationRecords.associateBy { it.verseKey }
    }
    val currentSurahMemorized = remember(uiState.selectedSurah.num, uiState.selectedSurah.ayat, uiState.memorizationRecords) {
        val memorizedAyahs = uiState.memorizationRecords
            .asSequence()
            .filter { it.surahNumber == uiState.selectedSurah.num && it.isMemorized }
            .map { it.ayahNumber }
            .toSet()
        memorizedAyahs.size >= uiState.selectedSurah.ayat
    }
    val latestSurahTestAttempt = remember(uiState.selectedSurah.num, uiState.surahMemorizationAttempts) {
        uiState.surahMemorizationAttempts
            .filter { it.surahNumber == uiState.selectedSurah.num }
            .maxByOrNull { it.timestampMs }
    }
    val latestSurahAyahResults = remember(latestSurahTestAttempt) {
        latestSurahTestAttempt
            ?.ayahResults
            ?.associateBy { it.verseKey }
            .orEmpty()
    }
    val readerAyahReviewResult = remember(readerAyahReviewVerseKey, latestSurahAyahResults) {
        readerAyahReviewVerseKey?.let(latestSurahAyahResults::get)
    }
    val restoredSurahTestAttempt = remember(restoredSurahTestAttemptId, uiState.surahMemorizationAttempts) {
        restoredSurahTestAttemptId?.let { attemptId ->
            uiState.surahMemorizationAttempts.firstOrNull { it.attemptId == attemptId }
        }
    }
    val readerHeaderItemCount = 1 + if (latestSurahTestAttempt != null) 1 else 0

    BackHandler(enabled = readerOptionsOpen || ayahActionsTarget != null || reflectionTarget != null || bookmarksOpen || audioDownloadsOpen || memorizationPanelVerseKey != null || aiListenVerseKey != null || surahTestOpen || selectedWordId != null || readerAyahReviewVerseKey != null) {
        when {
            readerAyahReviewVerseKey != null -> readerAyahReviewVerseKey = null
            selectedWordId != null -> selectedWordId = null
            surahTestOpen -> {
                surahTestOpen = false
                restoredSurahTestAttemptId = null
            }
            aiListenVerseKey != null -> aiListenVerseKey = null
            memorizationPanelVerseKey != null -> memorizationPanelVerseKey = null
            reflectionTarget != null -> reflectionTarget = null
            ayahActionsTarget != null -> ayahActionsTarget = null
            audioDownloadsOpen -> audioDownloadsOpen = false
            bookmarksOpen -> bookmarksOpen = false
            readerOptionsOpen -> readerOptionsOpen = false
        }
    }

    LaunchedEffect(uiState.selectedSurah.num, uiState.ayahs.size, uiState.loading) {
        if (!uiState.loading && uiState.ayahs.isNotEmpty() && lastScrolledSurah != uiState.selectedSurah.num) {
            lastScrolledSurah = uiState.selectedSurah.num
            val targetIndex = ((uiState.restoredAyah - 1).coerceAtLeast(0) + readerHeaderItemCount)
                .coerceAtMost(uiState.ayahs.lastIndex + readerHeaderItemCount)
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(uiState.pendingScrollVerseKey, uiState.loading, uiState.ayahs.size) {
        val verseKey = uiState.pendingScrollVerseKey ?: return@LaunchedEffect
        if (uiState.loading || uiState.ayahs.isEmpty()) return@LaunchedEffect
        val ayahIndex = uiState.ayahs.indexOfFirst { it.verseKey == verseKey }
        if (ayahIndex >= 0) {
            listState.scrollToItem((ayahIndex + readerHeaderItemCount).coerceAtLeast(0))
            onPendingScrollHandled()
        }
    }

    LaunchedEffect(readingPositionSavedMessage) {
        if (readingPositionSavedMessage != null) {
            delay(1600L)
            readingPositionSavedMessage = null
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            QuranTopBar(
                surah = uiState.selectedSurah,
                isSurahMemorized = currentSurahMemorized,
                onOpenSelector = onOpenSelector,
                onOpenSettings = { readerOptionsOpen = true },
                onOpenBookmarks = { bookmarksOpen = true },
                onMarkSurahMemorized = {
                    onMarkCurrentSurahMemorized()
                    readingPositionSavedMessage = if (currentSurahMemorized) {
                        "${uiState.selectedSurah.name} removed from memorised"
                    } else {
                        "${uiState.selectedSurah.name} marked memorised"
                    }
                },
                onOpenSearch = onOpenSelector,
                onOpenSurahTest = {
                    restoredSurahTestAttemptId = null
                    surahTestOpen = true
                },
            )

            if (uiState.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (uiState.miniPlayer != null) 214.dp else 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                latestSurahTestAttempt?.let { attempt ->
                    item(key = "quran_last_surah_test_${attempt.attemptId}") {
                        QuranLastSurahTestChip(
                            attempt = attempt,
                            onClick = {
                                restoredSurahTestAttemptId = attempt.attemptId
                                surahTestOpen = true
                            },
                        )
                    }
                }
                if (hasBismillahHeader) {
                    item(key = "bismillah_${uiState.selectedSurah.num}") {
                        BismillahHeader(
                            surahName = uiState.selectedSurah.name,
                            surahArabic = uiState.selectedSurah.arabic,
                        )
                    }
                } else {
                    item(key = "surah_label_${uiState.selectedSurah.num}") {
                        SurahLabelHeader(
                            surahName = uiState.selectedSurah.name,
                            surahArabic = uiState.selectedSurah.arabic,
                        )
                    }
                }

                items(
                    items = uiState.ayahs,
                    key = { it.verseKey },
                    contentType = { "quran-ayah" },
                ) { ayah ->
                    val memorizationRecord = memorizationRecordsByVerse[ayah.verseKey]
                    val ayahAttemptSnapshot = uiState.memorizationAttemptStatuses[ayah.verseKey]
                    val latestSurahAyahResult = latestSurahAyahResults[ayah.verseKey]
                    val latestSurahResultIsVisible = (
                        latestSurahTestAttempt != null &&
                            latestSurahAyahResult != null &&
                            latestSurahTestAttempt.timestampMs >= (ayahAttemptSnapshot?.lastAttemptAtMs ?: Long.MIN_VALUE)
                        )
                    val latestVisibleAttemptStatus = if (latestSurahResultIsVisible) {
                        latestSurahAyahResult.status
                    } else {
                        ayahAttemptSnapshot?.status ?: AyahMemorizationStatus.NOT_ATTEMPTED
                    }
                    val displayedMemorizationStatus = when {
                        memorizationRecord?.isIncorrect == true -> AyahMemorizationStatus.INCORRECT
                        memorizationRecord?.isNeedsRevision == true || memorizationRecord?.isRevision == true -> AyahMemorizationStatus.NEEDS_REVIEW
                        memorizationRecord?.isMemorized == true -> AyahMemorizationStatus.PASSED
                        else -> latestVisibleAttemptStatus
                    }
                    AyahRow(
                        ayah = ayah,
                        arabicTextSize = uiState.arabicTextSize,
                        tajweedEnabled = uiState.tajweedEnabled,
                        isBookmarked = ayah.verseKey in uiState.bookmarkedVerseKeys,
                        translation = ayah.translation,
                        translationFootnotes = ayah.translationFootnotes,
                        translationTextSize = uiState.translationTextSize,
                        translationEnabled = uiState.translationEnabled,
                        reflections = uiState.reflectionsByVerse[ayah.verseKey].orEmpty(),
                        tafsir = uiState.tafsirByVerse[tafsirCacheKey(ayah.verseKey, uiState.selectedTafsirSourceId)].orEmpty(),
                        tafsirSources = uiState.availableTafsirSources,
                        selectedTafsirSourceId = uiState.selectedTafsirSourceId,
                        isTafsirExpanded = uiState.expandedTafsirVerseKey == ayah.verseKey,
                        isTafsirLoading = tafsirCacheKey(ayah.verseKey, uiState.selectedTafsirSourceId) in uiState.loadingTafsirVerseKeys,
                        onToggleTafsir = { onToggleTafsir(ayah.verseKey) },
                        onSelectTafsirSource = onSelectTafsirSource,
                        onOpenActions = { ayahActionsTarget = ayah.verseKey },
                        onCreateReflectionNote = {
                            reflectionEditTargetId = uiState.reflectionsByVerse[ayah.verseKey]?.firstOrNull()?.noteId
                            reflectionTarget = ayah.verseKey
                        },
                        onEditReflection = { reflection ->
                            reflectionEditTargetId = reflection.noteId
                            reflectionTarget = ayah.verseKey
                        },
                        onSaveReadingPosition = {
                            onLastReadAyahChanged(uiState.selectedSurah.num, ayah.ayahNumber)
                            readingPositionSavedMessage = "Saved as current reading position"
                        },
                        isAudioPlaying = uiState.playingVerseKey == ayah.verseKey && uiState.miniPlayer?.isPlaying == true,
                        isAudioLoading = uiState.audioLoadingVerseKey == ayah.verseKey,
                        onPlayAudio = { onPlayAudioForAyah(ayah) },
                        isMemorizationActive = memorizationRecord?.isMemorising == true,
                        isMemorized = memorizationRecord?.isMemorized == true,
                        isMemorizationNeedsRevision = memorizationRecord?.isNeedsRevision == true || memorizationRecord?.isRevision == true,
                        isMemorizationIncorrect = memorizationRecord?.isIncorrect == true,
                        isMemorizationWeak = memorizationRecord?.isWeak == true,
                        memorizationAttemptStatus = displayedMemorizationStatus,
                        hasMemorizationReview = latestSurahResultIsVisible && latestSurahAyahResult.wordResults.isNotEmpty(),
                        memorizationConcealAmount = if (uiState.memorizationConcealedVerseKey == ayah.verseKey) {
                            uiState.memorizationConcealAmount
                        } else {
                            null
                        },
                        isMemorizationPanelExpanded = memorizationPanelVerseKey == ayah.verseKey,
                        wordDebugEnabled = quranWordDebugEnabled,
                        onWordClick = { word -> selectedWordId = word.wordId },
                        onOpenMemorization = {
                            memorizationPanelVerseKey = if (memorizationPanelVerseKey == ayah.verseKey) null else ayah.verseKey
                        },
                        onOpenAiListen = {
                            onStartMemorizingAyah(ayah)
                            aiListenVerseKey = ayah.verseKey
                        },
                        onOpenMemorizationReview = {
                            if (latestSurahResultIsVisible && latestSurahAyahResult.wordResults.isNotEmpty()) {
                                readerAyahReviewVerseKey = ayah.verseKey
                            }
                        },
                        onStartMemorizing = { onStartMemorizingAyah(ayah) },
                        onRemoveMemorizing = { onRemoveMemorizingAyah(ayah) },
                        onToggleMemorized = { onToggleMemorizedAyah(ayah) },
                        onMarkRevised = { onMarkRevisedAyah(ayah) },
                        onToggleNeedsRevision = { onToggleNeedsRevisionMemorization(ayah) },
                        onToggleIncorrect = { onToggleIncorrectMemorization(ayah) },
                        onToggleWeakMemorization = { onToggleWeakMemorization(ayah) },
                        onSetMemorizationConcealAmount = { amount -> onSetMemorizationConcealAmount(ayah.verseKey, amount) },
                    )
                }
                    if (uiState.audioStatusMessage != null) {
                        item(key = "quran_audio_status") {
                            Text(
                                text = uiState.audioStatusMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                                color = if (uiState.audioStatusIsError) Color(0xFFE06666) else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
                            )
                        }
                    }
                item("quran_bottom_pad") {
                    Spacer(
                        Modifier
                            .height(12.dp)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
        }

        uiState.miniPlayer?.let { player ->
            QuranAudioMiniPlayer(
                surahName = uiState.selectedSurah.name,
                player = player,
                onTogglePlayback = onToggleAudioPlayback,
                onSkipBack = { onSkipAudioBy(-10_000L) },
                onSkipForward = { onSkipAudioBy(10_000L) },
                onPreviousAyah = { onPlayAdjacentAudio(-1) },
                onNextAyah = { onPlayAdjacentAudio(1) },
                onSeekTo = onSeekAudioTo,
                onSetSpeed = onSetAudioSpeed,
                onChooseOtherReciter = onChooseOtherReciter,
                onClose = onStopAudio,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 86.dp)
                    .navigationBarsPadding(),
            )
        }

        AnimatedVisibility(
            visible = readingPositionSavedMessage != null,
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(180)) { it / 2 },
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = if (uiState.miniPlayer != null) 252.dp else 98.dp)
                .navigationBarsPadding(),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.elevated.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.82f)),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = readingPositionSavedMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                )
            }
        }

        if (readerOptionsOpen) {
            ModalBottomSheet(
                onDismissRequest = { readerOptionsOpen = false },
                sheetState = readerOptionsSheetState,
                containerColor = colors.elevated,
                contentColor = colors.text,
                scrimColor = colors.scrim,
                tonalElevation = 0.dp,
                dragHandle = {
                    Surface(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 36.dp, height = 4.dp),
                        color = colors.borderStrong,
                        shape = RoundedCornerShape(999.dp),
                    ) {}
                },
            ) {
                QuranReaderOptionsSheet(
                    fontPercent = uiState.arabicFontPercent,
                    translationFontPercent = uiState.translationFontPercent,
                    translationEnabled = uiState.translationEnabled,
                    translationSource = uiState.translationSource,
                    translationSourceLoading = uiState.translationSourceLoading,
                    translationSourceMessage = uiState.translationSourceMessage,
                    tajweedEnabled = uiState.tajweedEnabled,
                    wordDebugEnabled = quranWordDebugEnabled,
                    onSetArabicFontPercent = onSetArabicFontPercent,
                    onSetTranslationFontPercent = onSetTranslationFontPercent,
                    onSetTranslationEnabled = onSetTranslationEnabled,
                    onSetTranslationSource = onSetTranslationSource,
                    onSetTajweedEnabled = onSetTajweedEnabled,
                    onSetWordDebugEnabled = { quranWordDebugEnabled = it },
                    onOpenAudioDownloads = {
                        readerOptionsOpen = false
                        audioDownloadsOpen = true
                    },
                    onDismiss = { readerOptionsOpen = false },
                )
            }
        }

        val selectedAyah = remember(ayahActionsTarget, uiState.ayahs) {
            uiState.ayahs.firstOrNull { it.verseKey == ayahActionsTarget }
        }
        AyahActionsSheet(
            ayah = selectedAyah,
            surah = uiState.selectedSurah,
            isBookmarked = selectedAyah?.verseKey in uiState.bookmarkedVerseKeys,
            onDismiss = { ayahActionsTarget = null },
            onCopy = {
                selectedAyah?.let { ayah ->
                    clipboardManager.setText(
                        AnnotatedString("${uiState.selectedSurah.name} ${uiState.selectedSurah.num}:${ayah.ayahNumber}\n\n${ayah.arabicText}"),
                    )
                }
                ayahActionsTarget = null
            },
            onToggleBookmark = {
                selectedAyah?.let { onToggleBookmark(it.verseKey) }
                ayahActionsTarget = null
            },
            onCreateReflectionNote = {
                reflectionEditTargetId = selectedAyah?.verseKey
                    ?.let(uiState.reflectionsByVerse::get)
                    ?.firstOrNull()
                    ?.noteId
                reflectionTarget = selectedAyah?.verseKey
                ayahActionsTarget = null
            },
        )

        val selectedWord = remember(selectedWordId, uiState.ayahs) {
            selectedWordId?.let { wordId ->
                uiState.ayahs.asSequence()
                    .flatMap { it.words.asSequence() }
                    .firstOrNull { it.wordId == wordId }
            }
        }
        QuranWordInfoSheet(
            word = selectedWord,
            onDismiss = { selectedWordId = null },
        )

        val aiListenAyah = remember(aiListenVerseKey, uiState.ayahs) {
            aiListenVerseKey?.let { verseKey -> uiState.ayahs.firstOrNull { it.verseKey == verseKey } }
        }
        QuranAiListenSheet(
            ayah = aiListenAyah,
            surahName = uiState.selectedSurah.name,
            selectedProviderType = selectedSpeechProviderType,
            onSelectedProviderTypeChange = { selectedSpeechProviderName = it.name },
            onAttemptCompleted = onAiListenAttemptCompleted,
            onDismiss = { aiListenVerseKey = null },
        )

        QuranSurahTestSheet(
            visible = surahTestOpen,
            surah = uiState.selectedSurah,
            ayahs = uiState.ayahs,
            restoredAttempt = restoredSurahTestAttempt,
            selectedProviderType = selectedSpeechProviderType,
            onSelectedProviderTypeChange = { selectedSpeechProviderName = it.name },
            onAttemptCompleted = onSurahTestAttemptCompleted,
            onOpenAyah = { ayahNumber ->
                val ayahIndex = uiState.ayahs.indexOfFirst { it.ayahNumber == ayahNumber }
                if (ayahIndex >= 0) {
                    scope.launch { listState.animateScrollToItem(ayahIndex + readerHeaderItemCount) }
                }
            },
            onDismiss = {
                surahTestOpen = false
                restoredSurahTestAttemptId = null
            },
        )

        QuranReaderAyahReviewSheet(
            result = readerAyahReviewResult,
            onDismiss = { readerAyahReviewVerseKey = null },
        )

        val reflectionAyah = remember(reflectionTarget, uiState.ayahs) {
            uiState.ayahs.firstOrNull { it.verseKey == reflectionTarget }
        }
        val reflectionEditTarget = remember(reflectionTarget, reflectionEditTargetId, uiState.reflectionsByVerse) {
            reflectionTarget
                ?.let(uiState.reflectionsByVerse::get)
                ?.firstOrNull { it.noteId == reflectionEditTargetId }
        }
        ReflectionEditorSheet(
            ayah = reflectionAyah,
            surah = uiState.selectedSurah,
            existingReflection = reflectionEditTarget,
            onDismiss = {
                reflectionTarget = null
                reflectionEditTargetId = null
            },
            onSave = { title, body ->
                reflectionAyah?.let { ayah ->
                    if (reflectionEditTarget != null) {
                        onUpdateReflection(reflectionEditTarget.noteId, ayah, title, body)
                    } else {
                        onCreateReflectionNote(ayah, title, body)
                    }
                }
                reflectionTarget = null
                reflectionEditTargetId = null
            },
            onDelete = {
                reflectionEditTarget?.let { onDeleteReflection(it.noteId) }
                reflectionTarget = null
                reflectionEditTargetId = null
            },
        )

        QuranBookmarksSheet(
            visible = bookmarksOpen,
            bookmarkedVerseKeys = uiState.bookmarkedVerseKeys,
            onDismiss = { bookmarksOpen = false },
            onOpenBookmark = { verseKey ->
                bookmarksOpen = false
                onOpenBookmark(verseKey)
            },
        )

        QuranReciterPickerSheet(
            visible = uiState.reciterPickerAyah != null,
            ayahNumber = uiState.reciterPickerAyah?.ayahNumber ?: 1,
            reciters = uiState.availableReciters,
            isLoading = uiState.availableReciters.isEmpty(),
            onDismiss = onDismissReciterPicker,
            onSelect = onSelectAudioReciter,
        )

        QuranAudioDownloadsSheet(
            visible = audioDownloadsOpen,
            reciters = uiState.availableReciters,
            downloadStates = uiState.audioDownloadStates,
            onDismiss = { audioDownloadsOpen = false },
            onRefreshForReciter = onRefreshAudioDownloads,
            onDownload = onDownloadSurahAudio,
        )
    }
}
