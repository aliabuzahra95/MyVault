package com.myvault.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.narration.NarrationUiState
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay

@Composable
fun NarrationMiniPlayer(
    state: NarrationUiState,
    onPrimaryAction: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onProgressTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val isBusy = state.status == NarrationPlaybackStatus.Preparing || state.status == NarrationPlaybackStatus.Generating
    val isPlaying = state.status == NarrationPlaybackStatus.Playing
    val isPdf = state.noteId?.startsWith("attachment:") == true
    val sourceLabel = if (isPdf) "PDF narration" else "Note narration"
    val providerLabel = when (state.voice.lowercase()) {
        "device" -> "Device TTS"
        else -> state.voice.removeSuffix("Neural").replaceFirstChar { it.uppercase() }
    }
    var expanded by remember(state.noteId) { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val duration = state.totalDurationMs.coerceAtLeast(1L)
    val position = if (isDragging) dragPosition else state.totalPositionMs.toFloat()
    val progress = (position / duration.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(state.status) {
        while (state.status == NarrationPlaybackStatus.Playing || state.status == NarrationPlaybackStatus.Paused) {
            onProgressTick()
            delay(250L)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (expanded) 154.dp else 58.dp)
            .animateContentSize(tween(210)),
        color = colors.surface.copy(alpha = 0.97f),
        contentColor = colors.text,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 10.dp,
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (expanded) 10.dp else 8.dp, vertical = if (expanded) 10.dp else 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(31.dp).background(colors.accentSoft, VaultShapes.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Headphones,
                                null,
                                Modifier.size(17.dp),
                                tint = colors.accent,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.noteTitle.ifBlank { if (isPdf) "PDF narration" else "Note narration" },
                                fontSize = 10.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.W700,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "$sourceLabel · $providerLabel",
                                fontSize = 8.7.sp,
                                lineHeight = 10.sp,
                                color = if (state.error == null) colors.textMuted else colors.warning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            if (expanded) "Collapse narration player" else "Expand narration player",
                            Modifier.size(17.dp),
                            tint = colors.textMuted,
                        )
                    }
                    NarrationControl(
                        onClick = onPrimaryAction,
                        active = true,
                        enabled = !isBusy,
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = colors.accent)
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Pause narration" else "Play narration",
                                Modifier.size(17.dp),
                                tint = colors.accent,
                            )
                        }
                    }
                    NarrationControl(onClick = onStop) {
                        Icon(Icons.Rounded.Stop, "Stop narration", Modifier.size(17.dp), tint = colors.textMuted)
                    }
                }

                if (expanded) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 9.dp)) {
                        Slider(
                            value = position.coerceIn(0f, duration.toFloat()),
                            onValueChange = {
                                isDragging = true
                                dragPosition = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onSeek(dragPosition.toLong())
                            },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier.fillMaxWidth().height(30.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.border,
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatNarrationTime(position.toLong()), fontSize = 8.6.sp, color = colors.textMuted)
                            Text(formatNarrationTime(state.totalDurationMs), fontSize = 8.6.sp, color = colors.textMuted)
                        }
                        Text(
                            state.label.ifBlank { if (isPdf) "Reading the current PDF" else "Following the current sentence" },
                            modifier = Modifier.padding(top = 7.dp),
                            fontSize = 9.4.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp)
                        .background(colors.border, VaultShapes.pill),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(colors.accent, VaultShapes.pill),
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrationControl(
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
        shape = VaultShapes.md,
        color = if (active) colors.accentSoft else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center, content = { content() })
    }
}

private fun formatNarrationTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
