package com.myvault.app.ui.quran

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.myvault.app.data.quran.tafsirCacheKey
import com.myvault.app.data.quran.translationTextSize
import com.myvault.app.ui.theme.LightVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranReaderSurface(
    uiState: QuranReaderUiState,
    onOpenNavigation: () -> Unit,
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
    onOpenReflectionsHub: () -> Unit,
    onOpenReciterPicker: (QuranAyah) -> Unit,
    onOpenReciterPreferencePicker: (QuranAyah) -> Unit,
    onDismissReciterPicker: () -> Unit,
    onSelectAudioReciter: (AudioReciterUiModel) -> Unit,
    onPlayAudioForAyah: (QuranAyah) -> Unit,
    onToggleAudioPlayback: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudioTo: (Long) -> Unit,
    onSetAudioSpeed: (Float) -> Unit,
    onSetAudioListeningMode: (com.myvault.app.data.quran.audio.QuranListeningMode) -> Unit,
    onSkipAudioBy: (Long) -> Unit,
    onPlayAdjacentAudio: (Int) -> Unit,
    onChooseOtherReciter: () -> Unit,
    onRefreshAudioDownloads: (AudioReciterUiModel) -> Unit,
    onDownloadSurahAudio: (AudioReciterUiModel, Int) -> Unit,
    onMemoriseFromHere: (QuranAyah) -> Unit,
    onPendingScrollHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val readerCanvas = if (colors == LightVaultColors) Color(0xFFFBFAF6) else colors.bg
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var lastScrolledSurah by rememberSaveable { mutableIntStateOf(-1) }
    var selectedVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var recentsOpen by rememberSaveable { mutableStateOf(false) }
    var downloadsOpen by rememberSaveable { mutableStateOf(false) }
    var moreTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionEditTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var followRecitation by remember { mutableStateOf(false) }
    val headerCount = 1
    val currentAyah by remember(uiState.ayahs, listState) {
        derivedStateOf {
            (listState.firstVisibleItemIndex - headerCount + 1).coerceIn(1, uiState.selectedSurah.ayat)
        }
    }

    BackHandler(
        enabled = uiState.expandedTafsirVerseKey != null || optionsOpen || overflowOpen || bookmarksOpen || recentsOpen || downloadsOpen ||
            moreTargetKey != null || reflectionTargetKey != null || selectedWordId != null,
    ) {
        when {
            uiState.expandedTafsirVerseKey != null -> onToggleTafsir(uiState.expandedTafsirVerseKey)
            selectedWordId != null -> selectedWordId = null
            reflectionTargetKey != null -> reflectionTargetKey = null
            moreTargetKey != null -> moreTargetKey = null
            downloadsOpen -> downloadsOpen = false
            recentsOpen -> recentsOpen = false
            bookmarksOpen -> bookmarksOpen = false
            optionsOpen -> optionsOpen = false
            overflowOpen -> overflowOpen = false
        }
    }

    LaunchedEffect(uiState.selectedSurah.num, uiState.ayahs.size, uiState.loading) {
        if (!uiState.loading && uiState.ayahs.isNotEmpty() && lastScrolledSurah != uiState.selectedSurah.num) {
            lastScrolledSurah = uiState.selectedSurah.num
            listState.scrollToItem((uiState.restoredAyah - 1 + headerCount).coerceIn(0, uiState.ayahs.lastIndex + headerCount))
        }
    }
    LaunchedEffect(uiState.pendingScrollVerseKey, uiState.loading, uiState.ayahs.size) {
        val key = uiState.pendingScrollVerseKey ?: return@LaunchedEffect
        followRecitation = false
        if (uiState.loading) return@LaunchedEffect
        val index = uiState.ayahs.indexOfFirst { it.verseKey == key }
        if (index >= 0) {
            listState.scrollToItem(index + headerCount)
            onPendingScrollHandled()
        }
    }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            delay(1600)
            savedMessage = null
        }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) followRecitation = false }
    }
    LaunchedEffect(uiState.playingVerseKey, followRecitation) {
        if (!followRecitation || uiState.pendingScrollVerseKey != null || uiState.loading) return@LaunchedEffect
        val index = uiState.ayahs.indexOfFirst { it.verseKey == uiState.playingVerseKey }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index + headerCount }) {
            listState.animateScrollToItem(index + headerCount)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(readerCanvas)) {
        Column(Modifier.fillMaxSize()) {
            FrozenQuranTopBar(
                surah = uiState.selectedSurah,
                currentAyah = currentAyah,
                onOpenNavigation = onOpenNavigation,
                onOpenSelector = onOpenSelector,
                onOpenOverflow = { overflowOpen = true },
            )
            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(readerCanvas),
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (uiState.miniPlayer != null) 106.dp else 22.dp),
                ) {
                    item(key = "surah_header_${uiState.selectedSurah.num}") {
                        if (uiState.selectedSurah.num != 1 && uiState.selectedSurah.num != 9) {
                            BismillahHeader(uiState.selectedSurah.name, uiState.selectedSurah.arabic)
                        } else {
                            SurahLabelHeader(uiState.selectedSurah.name, uiState.selectedSurah.arabic)
                        }
                    }
                    items(uiState.ayahs, key = { it.verseKey }, contentType = { "quran-document-ayah" }) { ayah ->
                        FrozenQuranAyah(
                            ayah = ayah,
                            selected = selectedVerseKey == ayah.verseKey,
                            arabicTextSize = uiState.arabicTextSize,
                            tajweedEnabled = uiState.tajweedEnabled,
                            translation = ayah.translation,
                            translationFootnotes = ayah.translationFootnotes,
                            translationSourceKey = uiState.translationSource.storedValue,
                            translationTextSize = uiState.translationTextSize,
                            translationEnabled = uiState.translationEnabled,
                            reflections = uiState.reflectionsByVerse[ayah.verseKey].orEmpty(),
                            isBookmarked = ayah.verseKey in uiState.bookmarkedVerseKeys,
                            isAudioPlaying = uiState.playingVerseKey == ayah.verseKey && uiState.miniPlayer?.isPlaying == true,
                            isAudioLoading = uiState.audioLoadingVerseKey == ayah.verseKey,
                            onSelect = { selectedVerseKey = ayah.verseKey.takeUnless { it == selectedVerseKey } },
                            onDoubleClick = {
                                onLastReadAyahChanged(uiState.selectedSurah.num, ayah.ayahNumber)
                                savedMessage = "Reading position saved"
                            },
                            onListen = { onPlayAudioForAyah(ayah) },
                            onToggleTafsir = { onToggleTafsir(ayah.verseKey) },
                            onReflect = {
                                reflectionEditTargetId = uiState.reflectionsByVerse[ayah.verseKey]?.firstOrNull()?.noteId
                                reflectionTargetKey = ayah.verseKey
                            },
                            onEditReflection = { reflection ->
                                reflectionEditTargetId = reflection.noteId
                                reflectionTargetKey = ayah.verseKey
                            },
                            onCopy = { clipboard.setText(AnnotatedString("${uiState.selectedSurah.name} ${uiState.selectedSurah.num}:${ayah.ayahNumber}\n\n${ayah.arabicText}")) },
                            onToggleBookmark = { onToggleBookmark(ayah.verseKey) },
                            onMore = { moreTargetKey = ayah.verseKey },
                            onWordClick = { selectedWordId = it.wordId },
                        )
                    }
                    uiState.audioStatusMessage?.let { status ->
                        item(key = "quran_audio_status") {
                            Text(
                                text = status,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                                color = if (uiState.audioStatusIsError) Color(0xFFE06666) else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item("quran_bottom_pad") { Spacer(Modifier.height(12.dp).navigationBarsPadding()) }
                }
            }
        }

        uiState.miniPlayer?.let { player ->
            QuranAudioMiniPlayer(
                surahName = com.myvault.app.data.quran.quranCatalog.firstOrNull { it.num == player.verseKey.substringBefore(':').toIntOrNull() }?.name ?: uiState.selectedSurah.name,
                player = player,
                onTogglePlayback = onToggleAudioPlayback,
                onSkipBack = { onSkipAudioBy(-10_000) },
                onSkipForward = { onSkipAudioBy(10_000) },
                onPreviousAyah = { onPlayAdjacentAudio(-1) },
                onNextAyah = { onPlayAdjacentAudio(1) },
                onSeekTo = onSeekAudioTo,
                onSetSpeed = onSetAudioSpeed,
                onSetListeningMode = onSetAudioListeningMode,
                followRecitation = followRecitation,
                onFollowRecitation = { followRecitation = it },
                onChooseOtherReciter = onChooseOtherReciter,
                onClose = onStopAudio,
                onDownloadCurrentSurah = { uiState.selectedAudioReciter?.let { onDownloadSurahAudio(it, uiState.selectedSurah.num) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(),
            )
        }

        AnimatedVisibility(
            visible = savedMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (uiState.miniPlayer != null) 118.dp else 24.dp).navigationBarsPadding(),
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = colors.elevated, border = BorderStroke(1.dp, colors.border), shadowElevation = 6.dp) {
                Text(savedMessage.orEmpty(), modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = colors.text)
            }
        }

        QuranReaderOverflowSheet(
            visible = overflowOpen,
            onDismiss = { overflowOpen = false },
            onBookmarks = { overflowOpen = false; bookmarksOpen = true },
            onRecentLocations = { overflowOpen = false; recentsOpen = true },
            onReflections = { overflowOpen = false; onOpenReflectionsHub() },
            onAudioDownloads = { overflowOpen = false; downloadsOpen = true },
            onReaderSettings = { overflowOpen = false; optionsOpen = true },
        )
        QuranRecentLocationsSheet(
            visible = recentsOpen,
            recents = uiState.recentLocations,
            onDismiss = { recentsOpen = false },
            onOpen = { recentsOpen = false; onOpenBookmark(it) },
        )
        QuranBookmarksSheet(
            visible = bookmarksOpen,
            bookmarkedVerseKeys = uiState.bookmarkedVerseKeys,
            onDismiss = { bookmarksOpen = false },
            onOpenBookmark = { bookmarksOpen = false; onOpenBookmark(it) },
            onRemoveBookmark = onToggleBookmark,
        )
        if (optionsOpen) {
            ModalBottomSheet(
                onDismissRequest = { optionsOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.elevated,
                contentColor = colors.text,
                scrimColor = colors.scrim,
                tonalElevation = 0.dp,
            ) {
                QuranReaderOptionsSheet(
                    fontPercent = uiState.arabicFontPercent,
                    translationFontPercent = uiState.translationFontPercent,
                    translationEnabled = uiState.translationEnabled,
                    translationSource = uiState.translationSource,
                    translationSourceLoading = uiState.translationSourceLoading,
                    translationSourceMessage = uiState.translationSourceMessage,
                    tajweedEnabled = uiState.tajweedEnabled,
                    reciterName = uiState.selectedAudioReciter?.name.orEmpty(),
                    tafsirSources = uiState.availableTafsirSources,
                    selectedTafsirSourceId = uiState.selectedTafsirSourceId,
                    onSetArabicFontPercent = onSetArabicFontPercent,
                    onSetTranslationFontPercent = onSetTranslationFontPercent,
                    onSetTranslationEnabled = onSetTranslationEnabled,
                    onSetTranslationSource = onSetTranslationSource,
                    onSetTajweedEnabled = onSetTajweedEnabled,
                    onChooseReciter = {
                        uiState.ayahs.getOrNull((currentAyah - 1).coerceAtLeast(0))?.let(onOpenReciterPreferencePicker)
                        optionsOpen = false
                    },
                    onSelectTafsirSource = onSelectTafsirSource,
                    onDismiss = { optionsOpen = false },
                )
            }
        }

        val reflectionAyah = uiState.ayahs.firstOrNull { it.verseKey == reflectionTargetKey }
        val editReflection = reflectionTargetKey?.let(uiState.reflectionsByVerse::get)?.firstOrNull { it.noteId == reflectionEditTargetId }
        ReflectionEditorSheet(
            ayah = reflectionAyah,
            surah = uiState.selectedSurah,
            existingReflection = editReflection,
            onDismiss = { reflectionTargetKey = null; reflectionEditTargetId = null },
            onSave = { title, body ->
                reflectionAyah?.let { ayah ->
                    if (editReflection == null) onCreateReflectionNote(ayah, title, body)
                    else onUpdateReflection(editReflection.noteId, ayah, title, body)
                }
                reflectionTargetKey = null
                reflectionEditTargetId = null
            },
            onDelete = {
                editReflection?.let { onDeleteReflection(it.noteId) }
                reflectionTargetKey = null
                reflectionEditTargetId = null
            },
        )
        val moreAyah = uiState.ayahs.firstOrNull { it.verseKey == moreTargetKey }
        QuranAyahMoreSheet(
            ayah = moreAyah,
            onDismiss = { moreTargetKey = null },
            onMemoriseFromHere = { moreAyah?.let(onMemoriseFromHere); moreTargetKey = null },
        )
        val selectedWord = selectedWordId?.let { id -> uiState.ayahs.asSequence().flatMap { it.words.asSequence() }.firstOrNull { it.wordId == id } }
        QuranWordInfoSheet(selectedWord, onDismiss = { selectedWordId = null })
        val tafsirAyah = uiState.ayahs.firstOrNull { it.verseKey == uiState.expandedTafsirVerseKey }
        QuranTafsirSheet(
            ayah = tafsirAyah,
            tafsir = tafsirAyah?.let { uiState.tafsirByVerse[tafsirCacheKey(it.verseKey, uiState.selectedTafsirSourceId)] }.orEmpty(),
            sources = uiState.availableTafsirSources,
            selectedSourceId = uiState.selectedTafsirSourceId,
            loading = tafsirAyah?.let { tafsirCacheKey(it.verseKey, uiState.selectedTafsirSourceId) in uiState.loadingTafsirVerseKeys } == true,
            onSelectSource = onSelectTafsirSource,
            onDismiss = { uiState.expandedTafsirVerseKey?.let(onToggleTafsir) },
            onRetry = { onSelectTafsirSource(uiState.selectedTafsirSourceId) },
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
            visible = downloadsOpen,
            reciters = uiState.availableReciters,
            downloadStates = uiState.audioDownloadStates,
            onDismiss = { downloadsOpen = false },
            onRefreshForReciter = onRefreshAudioDownloads,
            onDownload = onDownloadSurahAudio,
        )
    }
}
