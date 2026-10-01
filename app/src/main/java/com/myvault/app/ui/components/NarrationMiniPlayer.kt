package com.myvault.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.narration.AzureNarrationConfig
import com.myvault.app.data.narration.GeminiNarrationConfig
import com.myvault.app.data.narration.NarrationConfig
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.narration.NarrationProvider
import com.myvault.app.data.narration.NarrationUiState
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay

@Composable
fun NarrationMiniPlayer(
    state: NarrationUiState,
    onPrimaryAction: () -> Unit,
    onStop: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onProgressTick: () -> Unit,
    onProviderChange: (NarrationProvider) -> Unit = {},
    onVoiceChange: (String) -> Unit = {},
    onTuck: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val isBusy = state.status == NarrationPlaybackStatus.Preparing || state.status == NarrationPlaybackStatus.Generating
    val isPlaying = state.status == NarrationPlaybackStatus.Playing
    val isPdf = state.noteId?.startsWith("attachment:") == true
    val voiceDisplay = if (state.provider == NarrationProvider.Device) "" else " · ${state.voice}"
    val providerVoiceLabel = "${state.provider.label}$voiceDisplay"

    var expanded by remember(state.noteId) { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val duration = state.totalDurationMs.coerceAtLeast(1L)
    val position = if (isDragging) dragPosition else state.totalPositionMs.toFloat()
    val progress = (position / duration.toFloat()).coerceIn(0f, 1f)

    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    LaunchedEffect(state.status) {
        while (state.status == NarrationPlaybackStatus.Playing || state.status == NarrationPlaybackStatus.Paused) {
            onProgressTick()
            delay(250L)
        }
    }

    val cardShape = if (expanded) RoundedCornerShape(20.dp) else RoundedCornerShape(16.dp)

    val tuckGestureModifier = Modifier.pointerInput(expanded) {
        var totalDragY = 0f
        detectVerticalDragGestures(
            onDragStart = { totalDragY = 0f },
            onDragCancel = { totalDragY = 0f },
            onDragEnd = { totalDragY = 0f },
            onVerticalDrag = { change, dragAmount ->
                totalDragY += dragAmount
                if (totalDragY > 26f) {
                    change.consume()
                    totalDragY = 0f
                    if (expanded) {
                        expanded = false
                    } else {
                        onTuck()
                    }
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (expanded) 228.dp else 60.dp)
            .animateContentSize(tween(210)),
        color = colors.surface.copy(alpha = 0.99f),
        contentColor = colors.text,
        shape = cardShape,
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.88f)),
        shadowElevation = if (expanded) 12.dp else 8.dp,
    ) {
        Box(modifier = tuckGestureModifier) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.border.copy(alpha = 0.8f))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = if (expanded) 10.dp else 6.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentSoft)
                                .border(1.dp, colors.accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = colors.accent,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.noteTitle.ifBlank { if (isPdf) "Library Document" else "Note Narration" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.error != null) {
                                Text(
                                    state.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.warning,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (isBusy) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = colors.accent,
                                    )
                                    Text(
                                        state.label.ifBlank { "Preparing narration..." },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(VaultShapes.pill)
                                            .clickable { showModelPickerSheet = true }
                                            .padding(horizontal = 2.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(VaultShapes.pill)
                                                .background(colors.accentSoft)
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = "✦ ${state.provider.label}",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.accent,
                                            )
                                        }
                                        if (state.provider != NarrationProvider.Device) {
                                            Text(
                                                text = "·",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted,
                                            )
                                            Text(
                                                text = state.voice,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Compact controls (visible when collapsed)
                    if (!expanded) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            IconBtn(
                                icon = Icons.Rounded.Replay10,
                                contentDescription = "Rewind 10s",
                                modifier = Modifier.size(32.dp),
                                onClick = onRewind10,
                            )

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPlaying) colors.accent else colors.accentSoft)
                                    .clickable(enabled = !isBusy, onClick = onPrimaryAction),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                                } else {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause narration" else "Play narration",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isPlaying) Color.White else colors.accent,
                                    )
                                }
                            }

                            IconBtn(
                                icon = Icons.Rounded.Forward10,
                                contentDescription = "Forward 10s",
                                modifier = Modifier.size(32.dp),
                                onClick = onForward10,
                            )

                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Expand narration player",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { expanded = true },
                                tint = colors.textSecondary,
                            )

                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Hide player to background",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(onClick = onTuck),
                                tint = colors.textSecondary,
                            )
                        }
                    } else {
                        // Expanded header controls: collapse chevron + close/stop button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse narration player",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { expanded = false },
                                tint = colors.textSecondary,
                            )
                            IconBtn(
                                icon = Icons.Rounded.Close,
                                contentDescription = "Stop narration",
                                modifier = Modifier.size(32.dp),
                                onClick = onStop,
                            )
                        }
                    }
                }

                // Expanded Section: Scrubber, Timestamps, Model & Voice Pill, Full Controls
                if (expanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.border,
                            ),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatNarrationTime(position.toLong()), fontSize = 11.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                            Text(formatNarrationTime(state.totalDurationMs), fontSize = 11.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                        }

                        // Model & Voice Chip Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { showModelPickerSheet = true },
                                shape = VaultShapes.pill,
                                color = colors.accentSoft,
                                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(13.dp), tint = colors.accent)
                                    Text(
                                        text = providerVoiceLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent,
                                        maxLines = 1,
                                    )
                                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(14.dp), tint = colors.accent)
                                }
                            }

                            Text(
                                text = state.label.ifBlank { if (isPlaying) "Playing" else if (state.status == NarrationPlaybackStatus.Paused) "Paused" else "Ready" },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }

                        // Bottom Action Row: Speed Selector + Rewind + Big Play/Pause + Forward + Stop
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Playback Speed Selector with Dropdown
                            Box {
                                Surface(
                                    onClick = { showSpeedMenu = true },
                                    shape = VaultShapes.pill,
                                    color = colors.elevated,
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "${state.speed}x",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accent,
                                        )
                                        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(14.dp), tint = colors.accent)
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = "${speed}x",
                                                        fontWeight = if (speed == state.speed) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (speed == state.speed) colors.accent else colors.text,
                                                    )
                                                    if (speed == state.speed) {
                                                        Icon(
                                                            Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = colors.accent,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onSpeedChange(speed)
                                                showSpeedMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            // Center Playback Controls: -10s, Big Play/Pause, +10s
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.elevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isBusy, onClick = onRewind10),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                        .clickable(enabled = !isBusy, onClick = onPrimaryAction),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(
                                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause narration" else "Play narration",
                                            modifier = Modifier.size(26.dp),
                                            tint = Color.White,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.elevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isBusy, onClick = onForward10),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // Right Stop Button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.elevated)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .clickable(onClick = onStop),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = "Stop narration",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Bottom slim progress bar when collapsed
            if (!expanded) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        }
    }

    if (showModelPickerSheet) {
        NarrationModelPickerSheet(
            currentProvider = state.provider,
            currentVoice = state.voice,
            onProviderChange = onProviderChange,
            onVoiceChange = onVoiceChange,
            onDismiss = { showModelPickerSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarrationModelPickerSheet(
    currentProvider: NarrationProvider,
    currentVoice: String,
    onProviderChange: (NarrationProvider) -> Unit,
    onVoiceChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    .padding(top = 10.dp, bottom = 4.dp)
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
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Models & Voices",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "Select an AI narration model and voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close model picker", onClick = onDismiss)
            }

            Text(
                text = "AUDIO MODELS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.W900,
                    letterSpacing = 1.sp,
                ),
                color = colors.textMuted,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NarrationProvider.entries.forEach { provider ->
                    NarrationModelCard(
                        provider = provider,
                        isSelected = provider == currentProvider,
                        onClick = { onProviderChange(provider) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Voices Section
            when (currentProvider) {
                NarrationProvider.GeminiFlashLite, NarrationProvider.GeminiFlash -> {
                    Text(
                        text = "GEMINI VOICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W900,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val voices = listOf(
                        "Puck" to "Balanced & Natural",
                        "Charon" to "Deep & Authoritative",
                        "Kore" to "Calm & Academic",
                        "Fenrir" to "Bold & Expressive",
                        "Aoede" to "Soft & Gentle",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        voices.chunked(2).forEach { rowVoices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowVoices.forEach { (voiceName, persona) ->
                                    VoiceSelectionChip(
                                        name = voiceName,
                                        persona = persona,
                                        isSelected = voiceName.equals(currentVoice, ignoreCase = true),
                                        onClick = { onVoiceChange(voiceName) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowVoices.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                NarrationProvider.OpenAi -> {
                    Text(
                        text = "OPENAI VOICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W900,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VoiceSelectionChip(
                            name = "Cedar",
                            persona = "Warm & Natural",
                            isSelected = "cedar".equals(currentVoice, ignoreCase = true),
                            onClick = { onVoiceChange("cedar") },
                            modifier = Modifier.weight(1f),
                        )
                        VoiceSelectionChip(
                            name = "Marin",
                            persona = "Clear & Crisp",
                            isSelected = "marin".equals(currentVoice, ignoreCase = true),
                            onClick = { onVoiceChange("marin") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                NarrationProvider.Azure -> {
                    Text(
                        text = "AZURE ENGLISH VOICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W900,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    val enVoices = listOf(
                        "en-AU-NatashaNeural" to "Natasha (AU)",
                        "en-AU-WilliamNeural" to "William (AU)",
                        "en-US-JennyNeural" to "Jenny (US)",
                        "en-US-GuyNeural" to "Guy (US)",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        enVoices.chunked(2).forEach { rowVoices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowVoices.forEach { (voiceId, label) ->
                                    VoiceSelectionChip(
                                        name = label,
                                        persona = "Neural",
                                        isSelected = voiceId.equals(currentVoice, ignoreCase = true),
                                        onClick = { onVoiceChange(voiceId) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "AZURE ARABIC VOICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W900,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    val arVoices = listOf(
                        "ar-SA-HamedNeural" to "Hamed (SA)",
                        "ar-SA-ZariyahNeural" to "Zariyah (SA)",
                        "ar-EG-SalmaNeural" to "Salma (EG)",
                        "ar-EG-ShakirNeural" to "Shakir (EG)",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        arVoices.chunked(2).forEach { rowVoices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowVoices.forEach { (voiceId, label) ->
                                    VoiceSelectionChip(
                                        name = label,
                                        persona = "Arabic",
                                        isSelected = voiceId.equals(currentVoice, ignoreCase = true),
                                        onClick = { onVoiceChange(voiceId) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                NarrationProvider.Device -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "Device narration uses the default speech engine and voice configured on your Android device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = colors.accent,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrationModelCard(
    provider: NarrationProvider,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val (icon, badge, description) = when (provider) {
        NarrationProvider.GeminiFlashLite -> Triple(
            Icons.Rounded.AutoAwesome,
            "SMART · ULTRA LOW COST",
            "Natural cadence with smart structure pacing",
        )
        NarrationProvider.GeminiFlash -> Triple(
            Icons.Rounded.AutoAwesome,
            "STUDIO QUALITY",
            "Rich, expressive delivery for deep study",
        )
        NarrationProvider.Device -> Triple(
            Icons.Rounded.PhoneAndroid,
            "FREE · OFFLINE",
            "Zero data usage, instant playback using device TTS",
        )
        NarrationProvider.OpenAi -> Triple(
            Icons.Rounded.Mic,
            "CLOUD TTS",
            "OpenAI audio synthesis with Cedar and Marin",
        )
        NarrationProvider.Azure -> Triple(
            Icons.Rounded.Cloud,
            "NEURAL CLOUD",
            "Microsoft Azure neural speech with Arabic support",
        )
    }

    val cardShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) colors.accentSoft else colors.surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accent else colors.border.copy(alpha = 0.8f),
                shape = cardShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.elevated)
                .border(1.dp, if (isSelected) colors.accent.copy(alpha = 0.35f) else colors.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (isSelected) colors.accent else colors.textSecondary)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                )
                Box(
                    modifier = Modifier
                        .clip(VaultShapes.pill)
                        .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.inset)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) colors.accent else colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.accent else colors.inset)
                .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun VoiceSelectionChip(
    name: String,
    persona: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) colors.accentSoft else colors.surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accent else colors.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = if (isSelected) colors.accent else colors.text,
            )
            Text(
                text = persona,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) colors.accent.copy(alpha = 0.8f) else colors.textMuted,
            )
        }
        if (isSelected) {
            Icon(
                Icons.Rounded.Check,
                null,
                modifier = Modifier.size(16.dp),
                tint = colors.accent,
            )
        }
    }
}

private fun formatNarrationTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
