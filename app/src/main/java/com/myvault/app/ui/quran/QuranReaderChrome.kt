package com.myvault.app.ui.quran

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.QuranRecentLocation
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationSavedAttempt
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.theme.VaultThemeTokens

private val QuranReaderChromeUthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)

@Composable
internal fun QuranTopBar(
    surah: SurahInfo,
    isSurahMemorized: Boolean,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onMarkSurahMemorized: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSurahTest: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 15.dp, vertical = 7.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenSelector,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = surah.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select Surah",
                        tint = colors.textMuted,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Text(
                    text = "${surah.ayat} ayat · ${surah.type} · Juz ${surah.juz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderTopIconButton(onClick = onOpenSurahTest) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Test this Surah",
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenBookmarks) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Qur'an bookmarks",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onMarkSurahMemorized) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = if (isSurahMemorized) "Surah memorised" else "Mark Surah memorised",
                    tint = if (isSurahMemorized) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search Surah",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Qur'an settings",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderTopIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.82f))
            .border(1.dp, colors.border.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun QuranLastSurahTestChip(
    attempt: QuranSurahMemorizationSavedAttempt,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val reviewCount = attempt.ayahsNeedingReview.size
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 0.dp, bottom = 4.dp),
        color = colors.elevated.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accentBorder, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Last Surah Test",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                    color = colors.accent,
                    maxLines = 1,
                )
                Text(
                    text = "${attempt.grade.label} · ${attempt.overallScore}% · ${
                        if (reviewCount == 0) "No ayahs need review" else "$reviewCount ayahs need review"
                    }",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun QuranRecentSurahsRow(
    recents: List<QuranRecentLocation>,
    onOpen: (QuranRecentLocation) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Recent Surahs",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recents.take(5), key = { "${it.surahNumber}:${it.lastReadAt}" }) { recent ->
                val surah = quranCatalog.firstOrNull { it.num == recent.surahNumber } ?: return@items
                QuranRecentSurahChip(
                    surah = surah,
                    ayahNumber = recent.ayahNumber,
                    onClick = { onOpen(recent) },
                )
            }
        }
    }
}

@Composable
private fun QuranRecentSurahChip(
    surah: SurahInfo,
    ayahNumber: Int,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = colors.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .width(132.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = surah.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Ayah ${ayahNumber.coerceIn(1, surah.ayat)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = surah.arabic,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = QuranReaderChromeUthmaniHafsFamily,
                    textDirection = TextDirection.ContentOrRtl,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BismillahHeader(
    surahName: String,
    surahArabic: String,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = QuranReaderChromeUthmaniHafsFamily,
                textDirection = TextDirection.Rtl,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
            ),
            color = colors.text,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "SURAH ${"$surahName · $surahArabic".uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.W800,
                letterSpacing = 1.sp,
            ),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
internal fun SurahLabelHeader(
    surahName: String,
    surahArabic: String,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SURAH ${"$surahName · $surahArabic".uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.W800,
                letterSpacing = 1.sp,
            ),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
    }
}
