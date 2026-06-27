package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.narration.NarrationConfig
import com.myvault.app.data.narration.AzureNarrationConfig
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.narration.NarrationUiState
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun NarrationMiniPlayer(
    state: NarrationUiState,
    selectedVoice: String,
    onVoiceChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBy: (Long) -> Unit,
    onProgressTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val isBusy = state.status == NarrationPlaybackStatus.Preparing || state.status == NarrationPlaybackStatus.Generating
    val isPlaying = state.status == NarrationPlaybackStatus.Playing
    val canPrimaryAct = !isBusy
    val subtitle = when {
        state.error != null -> state.error
        state.totalChunks > 1 && state.status == NarrationPlaybackStatus.Generating -> "${state.label}"
        state.label.isNotBlank() -> state.label
        else -> state.noteTitle.ifBlank { "Note narration" }
    }
    val canSeek = state.totalDurationMs > 0L && state.status != NarrationPlaybackStatus.Error
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val sliderPosition = if (isDragging) dragPosition else state.totalPositionMs.toFloat()
    val sliderMax = state.totalDurationMs.coerceAtLeast(1L).toFloat()

    LaunchedEffect(state.status) {
        while (state.status == NarrationPlaybackStatus.Playing || state.status == NarrationPlaybackStatus.Paused) {
            onProgressTick()
            delay(250L)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.elevated.copy(alpha = 0.98f),
        contentColor = colors.text,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            ) {
                Surface(
                    onClick = onPrimaryAction,
                    enabled = canPrimaryAct,
                    modifier = Modifier.size(36.dp),
                    color = if (isPlaying) colors.accentSoft else colors.surface,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, if (isPlaying) colors.accentBorder else colors.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent,
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause narration" else "Play narration",
                                modifier = Modifier.size(19.dp),
                                tint = if (isPlaying) colors.accent else colors.textSecondary,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listen Mode",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                        maxLines = 1,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.error != null) colors.warning else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = onStop,
                    modifier = Modifier.size(34.dp),
                    color = colors.surface,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop narration",
                            modifier = Modifier.size(17.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
                Surface(
                    onClick = onStop,
                    modifier = Modifier.size(34.dp),
                    color = colors.surface,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close narration",
                            modifier = Modifier.size(17.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
            if (canSeek) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = sliderPosition.coerceIn(0f, sliderMax),
                        onValueChange = { value ->
                            isDragging = true
                            dragPosition = value
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeek(dragPosition.toLong())
                        },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.border,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatNarrationTime(sliderPosition.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        Text(
                            text = formatNarrationTime(state.totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                    if (state.voice != DeviceNarrationVoice) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { onSkipBy(-10_000L) },
                                modifier = Modifier.size(34.dp),
                                color = colors.surface,
                                shape = VaultShapes.pill,
                                border = BorderStroke(1.dp, colors.border),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay10,
                                        contentDescription = "Rewind 10 seconds",
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.textSecondary,
                                    )
                                }
                            }
                            Box(modifier = Modifier.size(width = 18.dp, height = 1.dp))
                            Surface(
                                onClick = { onSkipBy(10_000L) },
                                modifier = Modifier.size(34.dp),
                                color = colors.surface,
                                shape = VaultShapes.pill,
                                border = BorderStroke(1.dp, colors.border),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forward10,
                                        contentDescription = "Forward 10 seconds",
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.voice == DeviceNarrationVoice) {
                    CompactChoiceChip(
                        label = "Device",
                        selected = true,
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    val voiceOptions = if (state.voice in AzureNarrationConfig.VoiceOptions) {
                        AzureNarrationConfig.EnglishVoiceOptions
                    } else {
                        NarrationConfig.VoiceOptions
                    }
                    voiceOptions.forEach { voice ->
                        val selected = selectedVoice.equals(voice, ignoreCase = true)
                        CompactChoiceChip(
                            label = voice.removeSuffix("Neural").replaceFirstChar { it.uppercase() },
                            selected = selected,
                            enabled = !isBusy,
                            onClick = { if (!selected) onVoiceChange(voice) },
                        )
                    }
                }
                NarrationConfig.SpeedOptions.forEach { speed ->
                    val selected = state.speed == speed
                    CompactChoiceChip(
                        label = if (speed == 1f) "1x" else "${speed}x",
                        selected = selected,
                        enabled = !isBusy,
                        onClick = { onSpeedChange(speed) },
                    )
                }
            }
        }
    }
}

private const val DeviceNarrationVoice = "device"

private fun formatNarrationTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun CompactChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        color = if (selected) colors.accentSoft else colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (selected) colors.accentBorder else colors.border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
            color = if (selected) colors.accent else colors.textMuted,
        )
    }
}
