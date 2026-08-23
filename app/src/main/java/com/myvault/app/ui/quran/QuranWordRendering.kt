package com.myvault.app.ui.quran

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.BuildConfig
import com.myvault.app.R
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.TajweedAnnotation
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.buildQuranArabicText
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlin.math.ceil

private val QuranWordRenderingUthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuranWordFlow(
    ayah: QuranAyah,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    memorizationConcealAmount: MemorizationConcealAmount?,
    wordDebugEnabled: Boolean,
    onWordClick: (QuranWord) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val words = ayah.words
    if (words.isEmpty() || !wordDebugEnabled) {
        val renderedArabic = remember(ayah.verseKey, ayah.arabicText, ayah.tajweedAnnotations, tajweedEnabled, colors, memorizationConcealAmount) {
            buildSafeArabicText(
                text = ayah.arabicText,
                annotations = ayah.tajweedAnnotations,
                tajweedEnabled = tajweedEnabled,
                isDark = colors == DarkVaultColors,
                memorizationConcealAmount = memorizationConcealAmount,
                debugLabel = ayah.verseKey,
            )
        }
        Text(
            text = renderedArabic,
            modifier = Modifier.fillMaxWidth(),
            style = quranArabicTextStyle(arabicTextSize),
            color = colors.text,
            textAlign = TextAlign.Right,
        )
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { word ->
                QuranWordChip(
                    word = word,
                    ayah = ayah,
                    arabicTextSize = arabicTextSize,
                    tajweedEnabled = tajweedEnabled,
                    memorizationConcealAmount = memorizationConcealAmount,
                    wordDebugEnabled = wordDebugEnabled,
                    onClick = { onWordClick(word) },
                )
            }
        }
    }
}

@Composable
private fun QuranWordChip(
    word: QuranWord,
    ayah: QuranAyah,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    memorizationConcealAmount: MemorizationConcealAmount?,
    wordDebugEnabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val renderedWord = remember(word.wordId, word.arabicText, ayah.tajweedAnnotations, tajweedEnabled, colors, memorizationConcealAmount) {
        buildSafeArabicText(
            text = word.arabicText,
            annotations = ayah.tajweedAnnotations.localAnnotationsFor(word),
            tajweedEnabled = tajweedEnabled,
            isDark = colors == DarkVaultColors,
            memorizationConcealAmount = memorizationConcealAmount,
            debugLabel = word.wordId,
        )
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = renderedWord,
            style = quranArabicTextStyle(arabicTextSize),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        if (wordDebugEnabled) {
            Text(
                text = word.wordId,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.W800),
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun quranArabicTextStyle(arabicTextSize: androidx.compose.ui.unit.TextUnit): TextStyle =
    TextStyle(
        fontFamily = QuranWordRenderingUthmaniHafsFamily,
        fontSize = arabicTextSize,
        fontWeight = FontWeight.Normal,
        textDirection = TextDirection.Rtl,
        lineHeight = (arabicTextSize.value * 1.95f).sp,
    )

private fun buildSafeArabicText(
    text: String,
    annotations: List<TajweedAnnotation>,
    tajweedEnabled: Boolean,
    isDark: Boolean,
    memorizationConcealAmount: MemorizationConcealAmount?,
    debugLabel: String,
): AnnotatedString {
    return runCatching {
        buildMemorizationDisplayText(
            buildQuranArabicText(
                text = text,
                annotations = annotations,
                tajweedEnabled = tajweedEnabled,
                isDark = isDark,
            ),
            memorizationConcealAmount,
        )
    }.getOrElse {
        if (BuildConfig.DEBUG) {
            Log.w("QuranShellScreen", "Falling back to plain Arabic rendering for $debugLabel", it)
        }
        buildMemorizationDisplayText(
            buildQuranArabicText(
                text = text,
                annotations = emptyList(),
                tajweedEnabled = false,
                isDark = isDark,
            ),
            memorizationConcealAmount,
        )
    }
}

private fun List<TajweedAnnotation>.localAnnotationsFor(word: QuranWord): List<TajweedAnnotation> {
    if (word.charStart < 0 || word.charEnd <= word.charStart) return emptyList()
    return mapNotNull { annotation ->
        if (annotation.end < word.charStart || annotation.start >= word.charEnd) return@mapNotNull null
        val localStart = maxOf(annotation.start, word.charStart) - word.charStart
        val localEnd = minOf(annotation.end, word.charEnd - 1) - word.charStart
        if (localStart <= localEnd) {
            TajweedAnnotation(start = localStart, end = localEnd, rule = annotation.rule)
        } else {
            null
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QuranWordInfoSheet(
    word: QuranWord?,
    onDismiss: () -> Unit,
) {
    if (word == null) return
    val colors = VaultThemeTokens.colors
    val metadata = word.metadata
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Qur'an word",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = word.wordId,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.accent,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close word details", onClick = onDismiss)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = word.arabicText,
                        style = quranArabicTextStyle(30.sp),
                        color = colors.text,
                        textAlign = TextAlign.Right,
                    )
                    QuranWordDetailRow("Position", "Surah ${word.surahNumber}, Ayah ${word.ayahNumber}, Word ${word.wordPosition}")
                    QuranWordDetailRow("Normalised", word.normalizedArabicText.ifBlank { "Unavailable" })
                    QuranWordDetailRow(
                        "Metadata",
                        if (metadata != null) "Aligned by exact word ID" else "Metadata not available for this word yet",
                    )
                    QuranWordDetailRow("Root", metadata?.root ?: "Metadata not available yet")
                    QuranWordDetailRow("Lemma", metadata?.lemma ?: "Metadata not available yet")
                    QuranWordDetailRow("Translation", metadata?.translation ?: "Metadata not available yet")
                    QuranWordDetailRow("Transliteration", metadata?.transliteration ?: "Metadata not available yet")
                    QuranWordDetailRow("Meaning", metadata?.definition ?: "Metadata not available yet")
                    if (metadata?.source != null) {
                        QuranWordDetailRow("Source", metadata.source)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranWordDetailRow(label: String, value: String) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
            color = colors.textMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun buildMemorizationDisplayText(
    source: AnnotatedString,
    concealAmount: MemorizationConcealAmount?,
): AnnotatedString {
    if (concealAmount == null || source.text.isBlank()) return source
    val nonSpaceIndexes = source.text.indices.filterNot { source.text[it].isWhitespace() }
    if (nonSpaceIndexes.isEmpty()) return source
    val concealedCount = ceil(nonSpaceIndexes.size * concealAmount.concealedFraction.toDouble())
        .toInt()
        .coerceIn(1, nonSpaceIndexes.size)
    val concealedIndexes = nonSpaceIndexes.take(concealedCount).toSet()
    val hiddenText = source.text.mapIndexed { index, char ->
        when {
            index !in concealedIndexes -> char
            char.isWhitespace() -> char
            else -> 'ـ'
        }
    }.joinToString(separator = "")

    return AnnotatedString.Builder(hiddenText).apply {
        source.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        source.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        source.getStringAnnotations(start = 0, end = source.length).forEach { annotation ->
            addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }
    }.toAnnotatedString()
}
