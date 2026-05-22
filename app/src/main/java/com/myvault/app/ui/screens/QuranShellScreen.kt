package com.myvault.app.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.arabicTextSize
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.SurahDownloadState
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.data.quran.translationTextSize
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.buildQuranArabicText
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private val UthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)

@Composable
fun QuranShellScreen(
    workspaceTitle: String,
    workspaceOptions: List<String>,
    onWorkspaceSelected: (String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
    uiState: QuranReaderUiState,
    onSelectSurah: (Int) -> Unit,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    onToggleTafsir: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onCreateReflectionNote: (QuranAyah, String, String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var search by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("All") }
    var selectorOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectorOpen) {
        selectorOpen = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            VaultTopBar(
                title = workspaceTitle,
                titleContent = {
                    VaultWorkspaceSwitcher(
                        selectedLabel = workspaceTitle,
                        options = workspaceOptions,
                        onSelected = onWorkspaceSelected,
                    )
                },
            ) {
                IconBtn(Icons.Rounded.WbSunny, "Toggle theme", active = true, onClick = onThemeClick)
                IconBtn(Icons.Rounded.Backup, "Quick cloud backup", active = quickBackupRecommended, onClick = onQuickBackupClick)
                IconBtn(Icons.Rounded.Settings, "Settings", onClick = onSettingsClick)
            }

            QuranReaderSurface(
                uiState = uiState,
                onOpenSelector = { selectorOpen = true },
                onSetArabicFontPercent = onSetArabicFontPercent,
                onSetTranslationFontPercent = onSetTranslationFontPercent,
                onSetTranslationEnabled = onSetTranslationEnabled,
                onSetTajweedEnabled = onSetTajweedEnabled,
                onLastReadAyahChanged = onLastReadAyahChanged,
                onToggleTafsir = onToggleTafsir,
                onToggleBookmark = onToggleBookmark,
                onCreateReflectionNote = onCreateReflectionNote,
                onOpenBookmark = onOpenBookmark,
                onOpenReciterPicker = onOpenReciterPicker,
                onDismissReciterPicker = onDismissReciterPicker,
                onSelectAudioReciter = onSelectAudioReciter,
                onPlayAudioForAyah = onPlayAudioForAyah,
                onToggleAudioPlayback = onToggleAudioPlayback,
                onStopAudio = onStopAudio,
                onSeekAudioTo = onSeekAudioTo,
                onSetAudioSpeed = onSetAudioSpeed,
                onSkipAudioBy = onSkipAudioBy,
                onPlayAdjacentAudio = onPlayAdjacentAudio,
                onChooseOtherReciter = onChooseOtherReciter,
                onRefreshAudioDownloads = onRefreshAudioDownloads,
                onDownloadSurahAudio = onDownloadSurahAudio,
                modifier = Modifier.fillMaxSize(),
            )
        }

        QuranSurahSelectorOverlay(
            visible = selectorOpen,
            selectedSurah = uiState.selectedSurah.num,
            search = search,
            typeFilter = typeFilter,
            onSearchChange = { search = it },
            onTypeFilterChange = { typeFilter = it },
            onDismiss = { selectorOpen = false },
            onSelect = { surah ->
                onSelectSurah(surah.num)
                selectorOpen = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranReaderSurface(
    uiState: QuranReaderUiState,
    onOpenSelector: () -> Unit,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    onToggleTafsir: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onCreateReflectionNote: (QuranAyah, String, String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val hasBismillahHeader = uiState.selectedSurah.num != 1 && uiState.selectedSurah.num != 9
    val continuityItemCount = 1 + if (uiState.recentLocations.isNotEmpty()) 1 else 0
    val readerHeaderItemCount = continuityItemCount + 1
    var lastScrolledSurah by rememberSaveable { mutableIntStateOf(-1) }
    var readerOptionsOpen by rememberSaveable { mutableStateOf(false) }
    var ayahActionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var audioDownloadsOpen by rememberSaveable { mutableStateOf(false) }
    val readerOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = readerOptionsOpen || ayahActionsTarget != null || reflectionTarget != null || bookmarksOpen || audioDownloadsOpen) {
        when {
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

    LaunchedEffect(listState, uiState.selectedSurah.num, uiState.ayahs.size, readerHeaderItemCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .mapNotNull { firstVisibleIndex ->
                val ayahIndex = (firstVisibleIndex - readerHeaderItemCount).coerceAtLeast(0)
                uiState.ayahs.getOrNull(ayahIndex)?.ayahNumber
            }
            .distinctUntilChanged()
            .collect { ayahNumber ->
                onLastReadAyahChanged(uiState.selectedSurah.num, ayahNumber)
            }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            QuranTopBar(
                surah = uiState.selectedSurah,
                onOpenSelector = onOpenSelector,
                onOpenSettings = { readerOptionsOpen = true },
                onOpenBookmarks = { bookmarksOpen = true },
                onOpenSearch = onOpenSelector,
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
                item(key = "quran_continue_${uiState.selectedSurah.num}") {
                    val resumeIndex = ((uiState.restoredAyah - 1).coerceAtLeast(0) + readerHeaderItemCount)
                        .coerceAtMost(uiState.ayahs.lastIndex + readerHeaderItemCount)
                    QuranContinueReadingCard(
                        surah = uiState.selectedSurah,
                        ayahNumber = uiState.restoredAyah,
                        onClick = {
                            scope.launch { listState.animateScrollToItem(resumeIndex) }
                        },
                    )
                }
                if (uiState.recentLocations.isNotEmpty()) {
                    item(key = "quran_recent_surahs") {
                        QuranRecentSurahsRow(
                            recents = uiState.recentLocations,
                            onOpen = { location ->
                                val sameSurah = location.surahNumber == uiState.selectedSurah.num
                                if (sameSurah) {
                                    onLastReadAyahChanged(location.surahNumber, location.ayahNumber)
                                    val index = ((location.ayahNumber - 1).coerceAtLeast(0) + readerHeaderItemCount)
                                        .coerceAtMost(uiState.ayahs.lastIndex + readerHeaderItemCount)
                                    scope.launch { listState.animateScrollToItem(index) }
                                } else {
                                    onOpenBookmark("${location.surahNumber}:${location.ayahNumber}")
                                }
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
                ) { ayah ->
                    AyahRow(
                        ayah = ayah,
                        arabicTextSize = uiState.arabicTextSize,
                        tajweedEnabled = uiState.tajweedEnabled,
                        isBookmarked = ayah.verseKey in uiState.bookmarkedVerseKeys,
                        translation = ayah.translation,
                        translationTextSize = uiState.translationTextSize,
                        translationEnabled = uiState.translationEnabled,
                        tafsir = uiState.tafsirByVerse[ayah.verseKey].orEmpty(),
                        isTafsirExpanded = uiState.expandedTafsirVerseKey == ayah.verseKey,
                        isTafsirLoading = ayah.verseKey in uiState.loadingTafsirVerseKeys,
                        onToggleTafsir = { onToggleTafsir(ayah.verseKey) },
                        onOpenActions = { ayahActionsTarget = ayah.verseKey },
                        onCreateReflectionNote = { reflectionTarget = ayah.verseKey },
                        isAudioPlaying = uiState.playingVerseKey == ayah.verseKey && uiState.miniPlayer?.isPlaying == true,
                        isAudioLoading = uiState.audioLoadingVerseKey == ayah.verseKey,
                        onPlayAudio = { onPlayAudioForAyah(ayah) },
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
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            )
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
                    tajweedEnabled = uiState.tajweedEnabled,
                    onSetArabicFontPercent = onSetArabicFontPercent,
                    onSetTranslationFontPercent = onSetTranslationFontPercent,
                    onSetTranslationEnabled = onSetTranslationEnabled,
                    onSetTajweedEnabled = onSetTajweedEnabled,
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
                reflectionTarget = selectedAyah?.verseKey
                ayahActionsTarget = null
            },
        )

        val reflectionAyah = remember(reflectionTarget, uiState.ayahs) {
            uiState.ayahs.firstOrNull { it.verseKey == reflectionTarget }
        }
        ReflectionEditorSheet(
            ayah = reflectionAyah,
            surah = uiState.selectedSurah,
            onDismiss = { reflectionTarget = null },
            onSave = { title, body ->
                reflectionAyah?.let { onCreateReflectionNote(it, title, body) }
                reflectionTarget = null
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

@Composable
private fun QuranTopBar(
    surah: SurahInfo,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 15.dp, vertical = 7.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenSelector,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = surah.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select Surah",
                        tint = colors.textMuted,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Text(
                    text = "${surah.ayat} ayat · ${surah.type} · Juz ${surah.juz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderTopIconButton(onClick = onOpenBookmarks) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Qur'an bookmarks",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Qur'an settings",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search Surah",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderTopIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.82f))
            .border(1.dp, colors.border.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun QuranContinueReadingCard(
    surah: SurahInfo,
    ayahNumber: Int,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 8.dp, bottom = 2.dp),
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.accentBorder.copy(alpha = 0.82f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accentBorder, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.num.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                    color = colors.accent,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Continue reading",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                    color = colors.accent,
                )
                Text(
                    text = "${surah.name} · Ayah ${ayahNumber.coerceIn(1, surah.ayat)}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${surah.ayat} ayat · ${surah.type} · Juz ${surah.juz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = surah.arabic,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = UthmaniHafsFamily,
                    textDirection = TextDirection.ContentOrRtl,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.text,
            )
        }
    }
}

@Composable
private fun QuranRecentSurahsRow(
    recents: List<com.myvault.app.data.quran.QuranRecentLocation>,
    onOpen: (com.myvault.app.data.quran.QuranRecentLocation) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Recent Surahs",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recents.take(5), key = { "${it.surahNumber}:${it.lastReadAt}" }) { recent ->
                val surah = quranCatalog.firstOrNull { it.num == recent.surahNumber } ?: return@items
                QuranRecentSurahChip(
                    surah = surah,
                    ayahNumber = recent.ayahNumber,
                    onClick = { onOpen(recent) },
                )
            }
        }
    }
}

@Composable
private fun QuranRecentSurahChip(
    surah: SurahInfo,
    ayahNumber: Int,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = colors.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .width(132.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = surah.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Ayah ${ayahNumber.coerceIn(1, surah.ayat)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = surah.arabic,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = UthmaniHafsFamily,
                    textDirection = TextDirection.ContentOrRtl,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BismillahHeader(
    surahName: String,
    surahArabic: String,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = UthmaniHafsFamily,
                textDirection = TextDirection.Rtl,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
            ),
            color = colors.text,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "SURAH ${"$surahName · $surahArabic".uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.W800,
                letterSpacing = 1.sp,
            ),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranReaderOptionsSheet(
    fontPercent: Int,
    translationFontPercent: Int,
    translationEnabled: Boolean,
    tajweedEnabled: Boolean,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onOpenAudioDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Qur'an display",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
            color = colors.text,
        )
        Text(
            text = "Adjust how the reader feels while keeping the page calm.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        QuranSliderSetting(
            title = "Arabic size",
            value = fontPercent.coerceIn(70, 140),
            valueRange = 70f..140f,
            onValueChange = onSetArabicFontPercent,
        )
        QuranToggleSetting(
            title = "Translation",
            subtitle = "Show Sahih International beneath each ayah.",
            checked = translationEnabled,
            onCheckedChange = onSetTranslationEnabled,
        )
        QuranSliderSetting(
            title = "Translation size",
            value = translationFontPercent.coerceIn(80, 130),
            valueRange = 80f..130f,
            onValueChange = onSetTranslationFontPercent,
        )
        QuranToggleSetting(
            title = "Tajweed",
            subtitle = "Apply Qur'anic Threads tajweed colours over the Uthmani script.",
            checked = tajweedEnabled,
            onCheckedChange = onSetTajweedEnabled,
        )
        QuranActionSetting(
            title = "Audio downloads",
            subtitle = "Choose a reciter and save Surahs for offline playback.",
            action = "Open",
            onClick = onOpenAudioDownloads,
        )
        Surface(
            onClick = onDismiss,
            color = colors.accentSoft,
            contentColor = colors.accent,
            border = BorderStroke(1.dp, colors.accentBorder),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                )
            }
        }
    }
}

@Composable
private fun QuranActionSetting(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuranSliderSetting(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
                Text(
                    text = "$value%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.borderStrong,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QuranToggleSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.accent,
                    checkedTrackColor = colors.accentSoft,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.borderStrong,
                ),
            )
        }
    }
}

@Composable
private fun SurahLabelHeader(
    surahName: String,
    surahArabic: String,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SURAH ${"$surahName · $surahArabic".uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.W800,
                letterSpacing = 1.sp,
            ),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AyahRow(
    ayah: QuranAyah,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    isBookmarked: Boolean,
    translation: String,
    translationTextSize: androidx.compose.ui.unit.TextUnit,
    translationEnabled: Boolean,
    tafsir: String,
    isTafsirExpanded: Boolean,
    isTafsirLoading: Boolean,
    onToggleTafsir: () -> Unit,
    onOpenActions: () -> Unit,
    onCreateReflectionNote: () -> Unit,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    onPlayAudio: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val cardShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(9.dp)
    val renderedArabic = remember(ayah.verseKey, ayah.arabicText, ayah.tajweedAnnotations, tajweedEnabled, colors) {
        runCatching {
            buildQuranArabicText(
                text = ayah.arabicText,
                annotations = ayah.tajweedAnnotations,
                tajweedEnabled = tajweedEnabled,
                isDark = colors == DarkVaultColors,
            )
        }.getOrElse {
            Log.w("QuranShellScreen", "Falling back to plain Arabic rendering for ${ayah.verseKey}", it)
            buildQuranArabicText(
                text = ayah.arabicText,
                annotations = emptyList(),
                tajweedEnabled = false,
                isDark = colors == DarkVaultColors,
            )
        }
    }
    val showTafsirExpansion = tafsir.length > 420
    var tafsirTextExpanded by rememberSaveable(ayah.verseKey) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 15.dp)
            .shadow(
                elevation = 3.dp,
                shape = cardShape,
                clip = false,
            )
            .clip(cardShape)
            .background(colors.surface.copy(alpha = if (colors == DarkVaultColors) 0.96f else 1f))
            .border(1.dp, colors.surface.copy(alpha = if (colors == DarkVaultColors) 0.96f else 1f), cardShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onOpenActions,
            )
            .padding(vertical = 10.dp, horizontal = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Transparent)
                        .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ayah.ayahNumber.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W600,
                            fontSize = 11.sp,
                        ),
                        color = colors.textSecondary,
                    )
                }
                if (isBookmarked) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(Color.Transparent)
                            .border(1.dp, colors.accentBorder, pillShape)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "Bookmarked",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                            color = colors.accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = colors.textMuted,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenActions,
                        )
                        .padding(4.dp)
                        .size(18.dp),
                )
            }

            Text(
                text = renderedArabic,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = UthmaniHafsFamily,
                    fontSize = arabicTextSize,
                    fontWeight = FontWeight.Normal,
                    textDirection = TextDirection.Rtl,
                    lineHeight = (arabicTextSize.value * 1.95f).sp,
                ),
                color = colors.text,
                textAlign = TextAlign.Right,
            )

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.Transparent)
                        .border(1.dp, if (isTafsirExpanded) colors.accentBorder else colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onToggleTafsir,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Tafsir",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600),
                        color = if (isTafsirExpanded) colors.accent else colors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.Transparent)
                        .border(1.dp, colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onCreateReflectionNote,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Add reflection",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600),
                        color = colors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onPlayAudio,
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAudioLoading) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isAudioPlaying) "Pause ayah audio" else "Play ayah audio",
                            tint = if (isAudioPlaying) colors.text else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = translationEnabled && translation.isNotBlank(),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) + expandVertically(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 110)) + shrinkVertically(animationSpec = tween(durationMillis = 150)),
            ) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = translationTextSize,
                        lineHeight = (translationTextSize.value * 1.58f).sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
                )
            }

            AnimatedVisibility(
                visible = isTafsirExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 170)) + expandVertically(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 130)) + shrinkVertically(animationSpec = tween(durationMillis = 180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(cardShape)
                        .background(colors.elevated)
                        .border(1.dp, colors.border.copy(alpha = 0.82f), cardShape)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = "TAFSIR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W800,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = when {
                                isTafsirLoading -> "Loading tafsir..."
                                tafsir.isNotBlank() -> tafsir
                                else -> "No tafsir is available for this ayah in the current abridged source."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                            color = colors.textSecondary,
                            maxLines = if (tafsirTextExpanded || !showTafsirExpansion || isTafsirLoading) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isTafsirLoading && showTafsirExpansion) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.78f), pillShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { tafsirTextExpanded = !tafsirTextExpanded },
                                )
                                .padding(vertical = 5.dp, horizontal = 10.dp),
                        ) {
                            Text(
                                text = if (tafsirTextExpanded) "Show less" else "Read more",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranReciterPickerSheet(
    visible: Boolean,
    ayahNumber: Int,
    reciters: List<AudioReciterUiModel>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AudioReciterUiModel) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rowShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 480.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Play Ayah $ayahNumber",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "Choose a reciter",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close reciters", onClick = onDismiss)
            }

            Text(
                text = "RECITERS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, letterSpacing = 1.sp),
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                when {
                    isLoading -> item {
                        Text(
                            text = "Loading reciters...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        )
                    }
                    reciters.isEmpty() -> item {
                        Text(
                            text = "No supported reciters are available right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        )
                    }
                    else -> items(reciters, key = { it.id }) { reciter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)
                                .clip(rowShape)
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.78f), rowShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(reciter) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = reciter.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                                    color = colors.text,
                                )
                                Text(
                                    text = "Starts playback from this ayah",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accentSoft)
                                    .border(1.dp, colors.accentBorder, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play with ${reciter.name}",
                                    tint = colors.accent,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranAudioDownloadsSheet(
    visible: Boolean,
    reciters: List<AudioReciterUiModel>,
    downloadStates: Map<String, SurahDownloadState>,
    onDismiss: () -> Unit,
    onRefreshForReciter: (AudioReciterUiModel) -> Unit,
    onDownload: (AudioReciterUiModel, Int) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedReciter by remember { mutableStateOf<AudioReciterUiModel?>(null) }

    LaunchedEffect(selectedReciter?.id) {
        selectedReciter?.let(onRefreshForReciter)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = selectedReciter?.name ?: "Choose a reciter for offline Surah downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close audio downloads", onClick = onDismiss)
            }

            if (selectedReciter == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Downloaded Surahs stay separated by reciter. Audio files are not included in vault backups; they can be downloaded again on another device.",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }
                    if (reciters.isEmpty()) {
                        item {
                            Text(
                                text = "Loading reciters...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        items(reciters, key = { it.id }) { reciter ->
                            AudioReciterDownloadChoiceRow(
                                reciter = reciter,
                                onClick = { selectedReciter = reciter },
                            )
                        }
                    }
                }
            } else {
                val reciter = selectedReciter ?: return@Column
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reciter.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                                    color = colors.text,
                                )
                                Text(
                                    text = "Tap any Surah to download it for offline playback.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.elevated)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { selectedReciter = null }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "Change",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                    items(quranCatalog, key = { it.num }) { surah ->
                        val state = downloadStates["${reciter.id}:${surah.num}"] ?: SurahDownloadState.NotDownloaded
                        SurahDownloadRow(
                            surah = surah,
                            state = state,
                            onDownload = { onDownload(reciter, surah.num) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioReciterDownloadChoiceRow(
    reciter: AudioReciterUiModel,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reciter.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
            Text(
                text = "Manage offline Surah downloads for this reciter",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.elevated)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Select",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SurahDownloadRow(
    surah: SurahInfo,
    state: SurahDownloadState,
    onDownload: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.num.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                )
                Text(
                    text = "${surah.ayat} ayat",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        when (state) {
            SurahDownloadState.NotDownloaded -> DownloadStatusButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download Surah", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            is SurahDownloadState.Downloading -> {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.progressPercent.coerceIn(0, 100) / 100f },
                        color = colors.accent,
                        trackColor = colors.elevated,
                        strokeWidth = 2.6.dp,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = "${state.progressPercent.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.W800),
                        color = colors.accent,
                    )
                }
            }
            SurahDownloadState.Downloaded -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.accentSoft)
                            .border(1.dp, colors.accentBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Downloaded", tint = colors.accent, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                        color = colors.accent,
                    )
                }
            }
            is SurahDownloadState.Failed -> DownloadStatusButton(onClick = onDownload) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Retry download", tint = Color(0xFFE06666), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DownloadStatusButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.elevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun QuranAudioMiniPlayer(
    surahName: String,
    player: AudioMiniPlayerUiState,
    onTogglePlayback: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousAyah: () -> Unit,
    onNextAyah: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onChooseOtherReciter: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(22.dp)
    val speedOptions = listOf(0.5f, 1f, 1.5f, 2f)
    var sliderPosition by remember(player.progressMs, player.durationMs) {
        mutableStateOf(player.progressMs.toFloat())
    }

    Box(
        modifier = modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.98f))
            .border(1.dp, colors.border.copy(alpha = 0.86f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.reciterName.ifBlank { "Qur'an audio" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, letterSpacing = 0.8.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$surahName · Ayah ${player.ayahNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.elevated)
                            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onChooseOtherReciter,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Reciters",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                            color = colors.textSecondary,
                        )
                    }
                    speedOptions.forEach { speed ->
                        val selected = player.playbackSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) colors.accentSoft else colors.elevated)
                                .border(1.dp, if (selected) colors.accentBorder else colors.border, RoundedCornerShape(9.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSetSpeed(speed) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (speed == 1f) "1x" else "${speed}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                                color = if (selected) colors.accent else colors.textSecondary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Close player",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Slider(
                value = sliderPosition.coerceIn(0f, player.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeekTo(sliderPosition.roundToLong()) },
                valueRange = 0f..player.durationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.borderStrong,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatMillis(sliderPosition.roundToLong()), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Text(formatMillis(player.durationMs), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AudioPlayerButton(icon = Icons.Rounded.SkipPrevious, onClick = onPreviousAyah)
                AudioPlayerButton(icon = Icons.Rounded.Replay10, onClick = onSkipBack)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.accentSoft)
                        .border(1.dp, colors.accentBorder, RoundedCornerShape(17.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTogglePlayback,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = colors.accent,
                        modifier = Modifier.size(23.dp),
                    )
                }
                AudioPlayerButton(icon = Icons.Rounded.Forward10, onClick = onSkipForward)
                AudioPlayerButton(icon = Icons.Rounded.SkipNext, onClick = onNextAyah)
            }
        }
    }
}

@Composable
private fun AudioPlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(colors.elevated)
            .border(1.dp, colors.border, RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AyahActionsSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCreateReflectionNote: () -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sectionShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 560.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ayah actions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                    color = colors.text,
                )
                IconBtn(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close ayah actions",
                    onClick = onDismiss,
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                        color = colors.text,
                    )
                    if (isBookmarked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.accentSoft)
                                .border(1.dp, colors.accentBorder, RoundedCornerShape(9.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "Bookmarked",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                                color = colors.accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ayah.arabicText,
                    style = TextStyle(
                        fontFamily = UthmaniHafsFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        textDirection = TextDirection.Rtl,
                        lineHeight = 39.sp,
                    ),
                    color = colors.text,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape),
            ) {
                AyahActionRow(
                    label = "Copy ayah",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
                AyahActionRow(
                    label = if (isBookmarked) "Remove bookmark" else "Bookmark ayah",
                    icon = Icons.Rounded.Bookmark,
                    onClick = onToggleBookmark,
                )
                AyahActionRow(
                    label = "Add reflection/note",
                    icon = Icons.Rounded.Edit,
                    onClick = onCreateReflectionNote,
                    isLast = true,
                )
            }
        }
    }
}

@Composable
private fun AyahActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = colors.border.copy(alpha = 0.65f),
            thickness = 1.dp,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReflectionEditorSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable(ayah.verseKey) { mutableStateOf("Reflection on ${surah.name} ${surah.num}:${ayah.ayahNumber}") }
    var body by rememberSaveable(ayah.verseKey) { mutableStateOf("") }
    val fieldShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Add reflection",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close reflection", onClick = onDismiss)
            }

            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ayah.arabicText,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = UthmaniHafsFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            textDirection = TextDirection.Rtl,
                            lineHeight = 38.sp,
                        ),
                        color = colors.text,
                        textAlign = TextAlign.Right,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ayah.translation.isNotBlank()) {
                        Text(
                            text = ayah.translation,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            QuranTextInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "Reflection title",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = fieldShape,
            )

            QuranTextInput(
                value = body,
                onValueChange = { body = it },
                placeholder = "Write your reflection...",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp),
                shape = fieldShape,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.textMuted,
                )
                Text(
                    text = "Add later from the saved reflection note",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                ReflectionSheetButton(label = "Close", onClick = onDismiss)
                ReflectionSheetButton(
                    label = "Save",
                    filled = true,
                    onClick = { onSave(title, body) },
                )
            }
        }
    }
}

@Composable
private fun QuranTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    val colors = VaultThemeTokens.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ReflectionSheetButton(
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        color = if (filled) colors.accent else colors.textSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) colors.accentSoft else Color.Transparent)
            .border(1.dp, if (filled) colors.accentBorder else colors.border.copy(alpha = 0.72f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranBookmarksSheet(
    visible: Boolean,
    bookmarkedVerseKeys: Set<String>,
    onDismiss: () -> Unit,
    onOpenBookmark: (String) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarks = remember(bookmarkedVerseKeys) {
        bookmarkedVerseKeys
            .mapNotNull { key ->
                val surahNum = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                val ayahNum = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
                val surah = quranCatalog.firstOrNull { it.num == surahNum } ?: return@mapNotNull null
                Triple(key, surah, ayahNum)
            }
            .sortedWith(compareBy({ it.second.num }, { it.third }))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 560.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "${bookmarks.size} saved ayah${if (bookmarks.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close bookmarks", onClick = onDismiss)
            }

            if (bookmarks.isEmpty()) {
                Surface(
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Bookmarked ayahs will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(bookmarks, key = { it.first }) { (key, surah, ayahNum) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            onClick = { onOpenBookmark(key) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${surah.name} $ayahNum",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                                        color = colors.text,
                                    )
                                    Text(
                                        text = "${surah.type} • Juz ${surah.juz}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                Text(
                                    text = surah.arabic,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = UthmaniHafsFamily,
                                        textDirection = TextDirection.ContentOrRtl,
                                    ),
                                    color = colors.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSurahSelectorOverlay(
    visible: Boolean,
    selectedSurah: Int,
    search: String,
    typeFilter: String,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (SurahInfo) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val dismissInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    val listState: LazyListState = rememberLazyListState()
    val panelShape = RoundedCornerShape(24.dp)
    val filtered = remember(search, typeFilter) {
        quranCatalog.filter { surah ->
            val q = search.trim().lowercase()
            val matchesType = typeFilter == "All" || surah.type == typeFilter
            val matchesSearch = q.isBlank() ||
                surah.name.lowercase().contains(q) ||
                surah.arabic.contains(search.trim()) ||
                surah.num.toString().contains(q)
            matchesType && matchesSearch
        }
    }
    val juzGroups = remember(filtered) { filtered.groupBy { it.juz }.toList() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                initialOffsetY = { -it / 4 },
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { -it / 5 },
            ),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(12.dp, panelShape, clip = false)
                    .clip(panelShape)
                    .background(colors.bg)
                    .border(1.dp, colors.border.copy(alpha = 0.8f), panelShape)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 4.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.border),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Select Surah",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    IconBtn(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuranSearchBar(query = search, onQueryChange = onSearchChange)
                    QuranTypeFilters(selected = typeFilter, onSelected = onTypeFilterChange)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    juzGroups.forEach { (juz, surahs) ->
                        item(key = "juz_$juz") {
                            JuzDivider(juzNumber = juz)
                            Spacer(Modifier.height(6.dp))
                        }
                        items(items = surahs, key = { it.num }) { surah ->
                            SurahRow(
                                surah = surah,
                                isCurrent = surah.num == selectedSurah,
                                onClick = { onSelect(surah) },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search by name, Arabic, or number...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun QuranTypeFilters(
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf("All 114" to "All", "Makki" to "Makki", "Madani" to "Madani").forEach { (label, key) ->
            QuranFilterPill(
                label = label,
                selected = selected == key,
                onClick = { onSelected(key) },
            )
        }
    }
}

@Composable
private fun QuranFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentSoft else Color.Transparent,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterBg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterText",
    )

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = textColor,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun JuzDivider(juzNumber: Int) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Juz $juzNumber",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700, letterSpacing = 0.3.sp),
            color = colors.textMuted,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = colors.border,
        )
    }
}

@Composable
private fun SurahRow(
    surah: SurahInfo,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (isCurrent) colors.elevated else colors.surface,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBg",
    )
    val border by animateColorAsState(
        targetValue = if (isCurrent) colors.borderStrong else colors.border,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = colors.text,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowTitle",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.num.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.W800,
                        fontSize = 19.sp,
                    ),
                    color = titleColor,
                )
                Text(
                    text = "${surah.type} • ${surah.ayat} ayat",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = surah.arabic,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = UthmaniHafsFamily,
                        textDirection = TextDirection.ContentOrRtl,
                        fontWeight = FontWeight.W400,
                        fontSize = 20.sp,
                    ),
                    color = colors.text,
                )
                Text(
                    text = "Juz ${surah.juz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}
