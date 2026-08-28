package com.myvault.app.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import com.myvault.app.data.quran.QuranTranslationSource
import com.myvault.app.data.quran.TafsirSourceUiModel
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
    reciterName: String,
    tafsirSources: List<TafsirSourceUiModel>,
    selectedTafsirSourceId: Int,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTranslationSource: (QuranTranslationSource) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onChooseReciter: () -> Unit,
    onSelectTafsirSource: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var tafsirExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = "QUR'AN ONLY",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
            color = colors.accent,
            modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reader settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                color = colors.text,
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close reader settings",
                tint = colors.text,
                modifier = Modifier.size(40.dp).clickable(onClick = onDismiss).padding(10.dp),
            )
        }
        Text(
            text = "Reading and recitation preferences",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        QuranSettingsSectionLabel("DISPLAY")
        QuranCompactSettingRow(
            title = "Arabic text size",
            subtitle = "${fontPercent.coerceIn(70, 140)}%",
            trailing = {
                QuranTextSizeButton("A−") { onSetArabicFontPercent((fontPercent - 10).coerceAtLeast(70)) }
                Spacer(Modifier.width(5.dp))
                QuranTextSizeButton("A+") { onSetArabicFontPercent((fontPercent + 10).coerceAtMost(140)) }
            },
        )
        QuranCompactToggleRow(
            title = "Translation",
            subtitle = "Show beneath Arabic",
            checked = translationEnabled,
            onCheckedChange = onSetTranslationEnabled,
        )
        QuranCompactSettingRow(
            title = "Translation source",
            subtitle = "One active translation",
            trailing = {
                QuranTranslationSource.entries.forEach { source ->
                    val selected = source == translationSource
                    Text(
                        text = if (source == QuranTranslationSource.SahihInternational) "Sahih International" else "Maududi",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) colors.accentSoft else colors.surface)
                            .border(1.dp, if (selected) colors.accentBorder else colors.border, RoundedCornerShape(8.dp))
                            .clickable(enabled = !translationSourceLoading) { onSetTranslationSource(source) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            },
        )
        if (!translationSourceMessage.isNullOrBlank()) {
            Text(
                text = translationSourceMessage,
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
        QuranCompactSettingRow(
            title = "Translation text size",
            subtitle = "Translation only · ${translationFontPercent.coerceIn(80, 130)}%",
            trailing = {
                Slider(
                    value = translationFontPercent.coerceIn(80, 130).toFloat(),
                    onValueChange = { onSetTranslationFontPercent(it.toInt()) },
                    valueRange = 80f..130f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.borderStrong,
                    ),
                    modifier = Modifier.width(152.dp),
                )
            },
        )
        QuranCompactToggleRow(
            title = "Tajweed colouring",
            subtitle = "Subtle pronunciation guide",
            checked = tajweedEnabled,
            onCheckedChange = onSetTajweedEnabled,
        )
        QuranSettingsSectionLabel("AUDIO")
        QuranCompactActionRow(
            title = "Reciter",
            subtitle = reciterName.ifBlank { "Choose the voice used for Qur'an audio." },
            onClick = onChooseReciter,
        )
        QuranSettingsSectionLabel("TAFSIR")
        QuranCompactActionRow(
            title = "Default Tafsir source",
            subtitle = tafsirSources.firstOrNull { it.id == selectedTafsirSourceId }?.name ?: "Choose source",
            onClick = { tafsirExpanded = !tafsirExpanded },
        )
        if (tafsirSources.isNotEmpty()) {
            if (tafsirExpanded) {
                tafsirSources.forEach { source ->
                    val selected = source.id == selectedTafsirSourceId
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                        color = if (selected) colors.accent else colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTafsirSource(source.id)
                                tafsirExpanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranSettingsSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
        color = VaultThemeTokens.colors.accent,
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
    )
}

@Composable
private fun QuranCompactSettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
    HorizontalDivider(color = colors.border)
}

@Composable
private fun QuranCompactToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    QuranCompactSettingRow(title, subtitle) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VaultThemeTokens.colors.accent,
                uncheckedThumbColor = VaultThemeTokens.colors.textSecondary,
                uncheckedTrackColor = VaultThemeTokens.colors.surface,
                uncheckedBorderColor = VaultThemeTokens.colors.borderStrong,
            ),
            modifier = Modifier.size(width = 48.dp, height = 30.dp),
        )
    }
}

@Composable
private fun QuranCompactActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = colors.border)
}

@Composable
private fun QuranTextSizeButton(label: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
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
