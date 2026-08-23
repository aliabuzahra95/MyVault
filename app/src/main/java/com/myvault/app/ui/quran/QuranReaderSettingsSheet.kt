package com.myvault.app.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.BuildConfig
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.ui.theme.VaultThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranReaderOptionsSheet(
    fontPercent: Int,
    translationFontPercent: Int,
    translationEnabled: Boolean,
    translationSource: QuranTranslationSource,
    translationSourceLoading: Boolean,
    translationSourceMessage: String?,
    tajweedEnabled: Boolean,
    wordDebugEnabled: Boolean,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTranslationSource: (QuranTranslationSource) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onSetWordDebugEnabled: (Boolean) -> Unit,
    onOpenAudioDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
            subtitle = "Show the selected English translation beneath each ayah.",
            checked = translationEnabled,
            onCheckedChange = onSetTranslationEnabled,
        )
        QuranTranslationSourceSetting(
            selectedSource = translationSource,
            loading = translationSourceLoading,
            message = translationSourceMessage,
            onSelected = onSetTranslationSource,
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
        if (BuildConfig.DEBUG) {
            QuranToggleSetting(
                title = "Word IDs",
                subtitle = "Developer check: show surah:ayah:wordPosition under each rendered word.",
                checked = wordDebugEnabled,
                onCheckedChange = onSetWordDebugEnabled,
            )
        }
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
private fun QuranTranslationSourceSetting(
    selectedSource: QuranTranslationSource,
    loading: Boolean,
    message: String?,
    onSelected: (QuranTranslationSource) -> Unit,
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
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "Translation source",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700),
                color = colors.text,
            )
            QuranTranslationSource.entries.forEach { source ->
                val selected = source == selectedSource
                Surface(
                    onClick = { if (!loading) onSelected(source) },
                    color = if (selected) colors.accentSoft else colors.elevated,
                    border = BorderStroke(
                        1.dp,
                        if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f),
                    ),
                    shape = RoundedCornerShape(13.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    width = if (selected) 5.dp else 1.dp,
                                    color = if (selected) colors.accent else colors.borderStrong,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = source.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                                color = if (selected) colors.accent else colors.text,
                            )
                            Text(
                                text = source.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (loading && source != selectedSource) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.8.dp,
                                color = colors.accent,
                            )
                        }
                    }
                }
            }
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                )
            }
            Text(
                text = "The Maududi translation is included with MyVault for permanent offline reading (source: Tanzil.net). Explanatory footnotes are provided through Quran Foundation and remain cached temporarily after they are opened.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
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
