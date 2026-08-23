package com.myvault.app.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.memorization.QuranMemorizationWordState
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationTestMode
import com.myvault.app.data.quran.speech.SpeechRecognitionProviderType
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlin.math.roundToLong

private val QuranMemorizationUthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)
internal enum class QuranAiListenStage {
    Ready,
    Recording,
    Paused,
    Finished,
    Transcribing,
}

internal const val SURAH_TEST_MAX_AYAHS = 30
internal const val SURAH_TEST_MAX_WORDS = 260

@Composable
internal fun QuranAiListenAnalysisMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface.copy(alpha = 0.7f))
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
            color = colors.textSecondary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W900),
            color = valueColor ?: colors.text,
            maxLines = 1,
        )
    }
}

@Composable
internal fun QuranAiListenSpeechTranscript(result: SpeechRecognitionResult) {
    val colors = VaultThemeTokens.colors
    var expanded by remember(result.transcript, result.normalizedTranscript) { mutableStateOf(false) }
    val metadata = buildList {
        add("${result.providerName} · ${result.modelName}")
        add("Latency ${formatSpeechLatency(result.latencyMs)}")
        result.confidence?.let { add("Confidence ${formatSpeechConfidence(it)}") }
        if (result.wordTimestamps.isNotEmpty()) add("${result.wordTimestamps.size} word timings")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Speech transcript",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                color = colors.textSecondary,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        Text(
            text = metadata.joinToString(separator = " • "),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700, lineHeight = 18.sp),
            color = colors.textSecondary,
        )
        AnimatedVisibility(visible = expanded) {
            if (result.transcript.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = result.transcript,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = QuranMemorizationUthmaniHafsFamily,
                            fontSize = 20.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = colors.text,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = "No speech transcript returned.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun quranAnalysisStateColor(state: QuranMemorizationWordState): Color {
    val colors = VaultThemeTokens.colors
    return when (state) {
        QuranMemorizationWordState.CORRECT -> colors.text
        QuranMemorizationWordState.MISSING -> Color(0xFFFF5A5F)
        QuranMemorizationWordState.EXTRA -> Color(0xFFFFA726)
        QuranMemorizationWordState.REPEATED -> Color(0xFF5B8CFF)
        QuranMemorizationWordState.UNKNOWN -> colors.textSecondary
    }
}

@Composable
internal fun QuranAiListenMic(stage: QuranAiListenStage) {
    val colors = VaultThemeTokens.colors
    val infiniteTransition = rememberInfiniteTransition(label = "ai_listen_mic")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ai_listen_mic_scale",
    )
    val active = stage == QuranAiListenStage.Recording
    Box(
        modifier = Modifier
            .size(92.dp)
            .graphicsLayer {
                scaleX = if (active) pulse else 1f
                scaleY = if (active) pulse else 1f
            }
            .clip(CircleShape)
            .background(if (active) colors.accentSoft else colors.elevated)
            .border(1.dp, if (active) colors.accentBorder else colors.border.copy(alpha = 0.78f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = if (active) colors.accent else colors.textSecondary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
internal fun QuranSpeechProviderSelector(
    selectedProviderType: SpeechRecognitionProviderType,
    onSelectedProviderTypeChange: (SpeechRecognitionProviderType) -> Unit,
    enabled: Boolean,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.elevated.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.72f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SpeechRecognitionProviderType.entries.forEach { providerType ->
            val selected = providerType == selectedProviderType
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.accentSoft else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accentBorder else Color.Transparent,
                        shape = RoundedCornerShape(9.dp),
                    )
                    .clickable(enabled = enabled) { onSelectedProviderTypeChange(providerType) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = providerType.shortName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900, letterSpacing = 0.4.sp),
                    color = when {
                        selected -> colors.accent
                        enabled -> colors.textSecondary
                        else -> colors.textMuted
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun QuranSurahTestModeSelector(
    selectedMode: QuranSurahMemorizationTestMode,
    onSelectedModeChange: (QuranSurahMemorizationTestMode) -> Unit,
    enabled: Boolean,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuranSurahMemorizationTestMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.accentSoft else colors.elevated)
                    .border(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onSelectedModeChange(mode) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
                    color = if (selected) colors.accent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun QuranAiListenActionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.elevated)
            .border(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W900),
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
        )
    }
}

internal fun formatQuranRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

internal fun formatSpeechLatency(latencyMs: Long): String =
    if (latencyMs < 1000L) {
        "${latencyMs}ms"
    } else {
        "%.1fs".format(latencyMs / 1000.0)
    }

internal fun formatSpeechConfidence(confidence: Float): String =
    "${(confidence * 100f).roundToLong()}%"
