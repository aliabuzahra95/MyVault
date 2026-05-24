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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.narration.NarrationConfig
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarrationConfig.VoiceOptions.forEach { voice ->
                    val selected = selectedVoice.equals(voice, ignoreCase = true)
                    CompactChoiceChip(
                        label = voice.replaceFirstChar { it.uppercase() },
                        selected = selected,
                        enabled = !isBusy,
                        onClick = { if (!selected) onVoiceChange(voice) },
                    )
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
