package com.myvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.ui.quran.QuranReaderSurface
import com.myvault.app.ui.quran.QuranSurahSelectorOverlay
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultThemeTokens


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
                IconBtn(Icons.Rounded.Settings, "App settings", onClick = onSettingsClick)
            }

            QuranReaderSurface(
                uiState = uiState,
                onOpenSelector = { selectorOpen = true },
                onSetArabicFontPercent = onSetArabicFontPercent,
                onSetTranslationFontPercent = onSetTranslationFontPercent,
                onSetTranslationEnabled = onSetTranslationEnabled,
                onSetTranslationSource = onSetTranslationSource,
                onSetTajweedEnabled = onSetTajweedEnabled,
                onLastReadAyahChanged = onLastReadAyahChanged,
                onToggleTafsir = onToggleTafsir,
                onSelectTafsirSource = onSelectTafsirSource,
                onToggleBookmark = onToggleBookmark,
                onCreateReflectionNote = onCreateReflectionNote,
                onUpdateReflection = onUpdateReflection,
                onDeleteReflection = onDeleteReflection,
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
                onStartMemorizingAyah = onStartMemorizingAyah,
                onRemoveMemorizingAyah = onRemoveMemorizingAyah,
                onToggleMemorizedAyah = onToggleMemorizedAyah,
                onMarkRevisedAyah = onMarkRevisedAyah,
                onAiListenAttemptCompleted = onAiListenAttemptCompleted,
                onSurahTestAttemptCompleted = onSurahTestAttemptCompleted,
                onMarkCurrentSurahMemorized = onMarkCurrentSurahMemorized,
                onToggleNeedsRevisionMemorization = onToggleNeedsRevisionMemorization,
                onToggleIncorrectMemorization = onToggleIncorrectMemorization,
                onToggleWeakMemorization = onToggleWeakMemorization,
                onSetMemorizationConcealAmount = onSetMemorizationConcealAmount,
                onPendingScrollHandled = onPendingScrollHandled,
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
            onSelectAyah = { verseKey ->
                onOpenBookmark(verseKey)
                selectorOpen = false
            },
        )
    }
}
