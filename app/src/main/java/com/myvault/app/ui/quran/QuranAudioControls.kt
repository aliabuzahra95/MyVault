package com.myvault.app.ui.quran

import androidx.compose.material3.SegmentedButton

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.QuranAudioDownloadService
import com.myvault.app.data.quran.audio.SurahDownloadState
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlin.math.roundToLong
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QuranReciterPickerSheet(
    visible: Boolean,
    ayahNumber: Int,
    reciters: List<AudioReciterUiModel>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AudioReciterUiModel) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val localContext = LocalContext.current
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
internal fun QuranAudioDownloadsSheet(
    visible: Boolean,
    reciters: List<AudioReciterUiModel>,
    downloadStates: Map<String, SurahDownloadState>,
    onDismiss: () -> Unit,
    onRefreshForReciter: (AudioReciterUiModel) -> Unit,
    onDownload: (AudioReciterUiModel, Int) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val localContext = LocalContext.current
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
                val missingSurahs = remember(reciter.id, downloadStates) {
                    quranCatalog.filter { surah ->
                        when (downloadStates["${reciter.id}:${surah.num}"]) {
                            SurahDownloadState.Downloaded,
                            SurahDownloadState.Preparing,
                            is SurahDownloadState.Queued,
                            is SurahDownloadState.Downloading,
                            -> false
                            else -> true
                        }
                    }.map { it.num }.toIntArray()
                }
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
                                    text = "Download one Surah, or queue every missing Surah in the background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.accentSoft)
                                        .border(1.dp, colors.accentBorder, RoundedCornerShape(10.dp))
                                        .clickable(
                                            enabled = missingSurahs.isNotEmpty(),
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            QuranAudioDownloadService.startDownloads(
                                                context = localContext.applicationContext,
                                                reciter = reciter,
                                                surahNumbers = missingSurahs,
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Download all",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                                        color = if (missingSurahs.isEmpty()) colors.textMuted else colors.accent,
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
                    }
                    items(quranCatalog, key = { it.num }) { surah ->
                        val state = downloadStates["${reciter.id}:${surah.num}"] ?: SurahDownloadState.NotDownloaded
                        SurahDownloadRow(
                            surah = surah,
                            state = state,
                            onDownload = {
                                QuranAudioDownloadService.startDownload(
                                    context = localContext.applicationContext,
                                    reciter = reciter,
                                    surahNumber = surah.num,
                                )
                            },
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
            SurahDownloadState.Preparing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.4.dp,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Preparing",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                        color = colors.accent,
                    )
                }
            }
            is SurahDownloadState.Queued -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentSoft)
                        .border(1.dp, colors.accentBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queued ${state.position}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                        color = colors.accent,
                    )
                }
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
internal fun QuranAudioMiniPlayer(
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
    onDownloadCurrentSurah: () -> Unit,
    onSetListeningMode: (com.myvault.app.data.quran.audio.QuranListeningMode) -> Unit,
    followRecitation: Boolean,
    onFollowRecitation: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(22.dp)
    val speedOptions = listOf(0.5f, 1f, 1.5f, 2f)
    var sliderPosition by remember(player.progressMs, player.durationMs) {
        mutableFloatStateOf(player.progressMs.toFloat())
    }
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        val progress = if (player.durationMs > 0L) {
            (player.progressMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = modifier
                .shadow(9.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.99f))
                .border(1.dp, colors.border.copy(alpha = 0.86f), RoundedCornerShape(16.dp))
                .clickable { expanded = true },
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.accentSoft).clickable(onClick = onTogglePlayback),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause" else "Play",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "$surahName · Ayah ${player.ayahNumber}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = player.reciterName.ifBlank { "Qur'an audio" },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.KeyboardArrowUp, "Expand player", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Icon(
                        Icons.Rounded.Close,
                        "Close player",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp).clickable(onClick = onClose),
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        }
        return
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
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { expanded = false },
                            ),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp).clickable(onClick = onClose),
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                com.myvault.app.data.quran.audio.QuranListeningMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = player.listeningMode == mode,
                        onClick = { onSetListeningMode(mode) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(if (index == 0) "This ayah" else "Continue Surah", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Follow recitation", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                androidx.compose.material3.Switch(checked = followRecitation, onCheckedChange = onFollowRecitation, enabled = player.synchronized)
            }

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

            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                    .clickable(onClick = onDownloadCurrentSurah)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Download, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Text("Download this Surah", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text)
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
