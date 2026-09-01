package com.myvault.app.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranRecentLocation
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.QuranTranslationFootnote
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.TafsirSourceUiModel
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
internal fun FrozenQuranTopBar(
    surah: SurahInfo,
    currentAyah: Int,
    onOpenNavigation: () -> Unit,
    onOpenSelector: () -> Unit,
    onOpenOverflow: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrozenQuranIconButton(Icons.Rounded.Menu, "Open navigation", onOpenNavigation)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSelector,
                )
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = surah.arabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = "Ayah ${currentAyah.coerceIn(1, surah.ayat)} of ${surah.ayat}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
        FrozenQuranIconButton(Icons.Rounded.MoreVert, "Qur'an options", onOpenOverflow)
    }
}

@Composable
private fun FrozenQuranIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = colors.textSecondary, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun FrozenQuranAyah(
    ayah: QuranAyah,
    selected: Boolean,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    translation: String,
    translationFootnotes: List<QuranTranslationFootnote>,
    translationSourceKey: String,
    translationTextSize: androidx.compose.ui.unit.TextUnit,
    translationEnabled: Boolean,
    reflections: List<QuranReflectionItem>,
    isBookmarked: Boolean,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    onSelect: () -> Unit,
    onDoubleClick: () -> Unit,
    onListen: () -> Unit,
    onToggleTafsir: () -> Unit,
    onReflect: () -> Unit,
    onEditReflection: (QuranReflectionItem) -> Unit,
    onCopy: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMore: () -> Unit,
    onWordClick: (QuranWord) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var expandedFootnoteId by rememberSaveable(
        ayah.surahNumber,
        ayah.ayahNumber,
        translationSourceKey,
    ) { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(10.dp)
    val selectedBackground by animateColorAsState(
        targetValue = if (selected) colors.accentSoft.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = 155, easing = FastOutSlowInEasing),
        label = "ayah-selected-surface",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(shape)
            .background(selectedBackground)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
                onDoubleClick = onDoubleClick,
                onLongClick = onSelect,
            )
            .padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = ayah.ayahNumber.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.accent,
                modifier = Modifier.padding(top = 7.dp, end = 8.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                QuranWordFlow(
                    ayah = ayah,
                    arabicTextSize = arabicTextSize,
                    tajweedEnabled = tajweedEnabled,
                    memorizationConcealAmount = null,
                    wordDebugEnabled = false,
                    onWordClick = onWordClick,
                )
            }
            if (isBookmarked) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Bookmarked",
                    tint = colors.accent,
                    modifier = Modifier.padding(start = 5.dp, top = 6.dp).size(15.dp),
                )
            }
        }

        if (translationEnabled && translation.isNotBlank()) {
            FrozenTranslation(
                translation = translation,
                footnotes = translationFootnotes,
                textSize = translationTextSize,
                expandedFootnoteId = expandedFootnoteId,
                onExpandedFootnoteChange = { expandedFootnoteId = it },
            )
        }

        if (reflections.isNotEmpty()) {
            val first = reflections.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditReflection(first) }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.width(2.dp).height(34.dp).background(colors.accent))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = first.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (reflections.size == 1) first.reflectionPreview else "${reflections.size} reflections",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(105)) + slideInVertically(tween(125, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(75)) + slideOutVertically(tween(90, easing = FastOutSlowInEasing)) { it / 8 },
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.8f), RoundedCornerShape(9.dp)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                item { FrozenAyahAction(if (isAudioPlaying) "Pause" else "Listen", if (isAudioPlaying) Icons.Rounded.Pause else Icons.Outlined.PlayCircle, isAudioLoading, onListen) }
                item { FrozenAyahAction("Tafsir", Icons.AutoMirrored.Outlined.MenuBook, false, onToggleTafsir) }
                item { FrozenAyahAction("Reflect", Icons.Outlined.EditNote, false, onReflect) }
                item { FrozenAyahAction("Copy", Icons.Rounded.ContentCopy, false, onCopy) }
                item { FrozenAyahAction(if (isBookmarked) "Remove" else "Bookmark", if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder, false, onToggleBookmark) }
                item { FrozenAyahAction("More", Icons.Outlined.MoreHoriz, false, onMore) }
            }
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.55f), thickness = 1.dp)
    }
}

@Composable
private fun FrozenAyahAction(label: String, icon: ImageVector, loading: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = colors.accent)
        } else {
            Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun FrozenTranslation(
    translation: String,
    footnotes: List<QuranTranslationFootnote>,
    textSize: androidx.compose.ui.unit.TextUnit,
    expandedFootnoteId: String?,
    onExpandedFootnoteChange: (String?) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val expandedFootnote = footnotes.firstOrNull { it.id == expandedFootnoteId }
    var displayedFootnote by remember(footnotes) { mutableStateOf<QuranTranslationFootnote?>(null) }
    LaunchedEffect(expandedFootnote) {
        if (expandedFootnote != null) displayedFootnote = expandedFootnote
    }
    val annotated = remember(translation, footnotes, colors.accent, expandedFootnoteId) {
        buildAnnotatedString {
            append(translation)
            footnotes.forEach { footnote ->
                val start = footnote.markerStart.coerceIn(0, length)
                val end = footnote.markerEndExclusive.coerceIn(start, length)
                if (start < end) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = footnote.id,
                            styles = TextLinkStyles(SpanStyle(color = colors.accent, fontWeight = FontWeight.W800, baselineShift = BaselineShift.Superscript)),
                            linkInteractionListener = {
                                onExpandedFootnoteChange(nextExpandedFootnoteId(expandedFootnoteId, footnote.id))
                            },
                        ),
                        start,
                        end,
                    )
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSize, lineHeight = (textSize.value * 1.55f).sp),
            color = colors.textSecondary,
        )
        AnimatedVisibility(
            visible = expandedFootnote != null,
            enter = fadeIn(tween(150)) + expandVertically(tween(190)),
            exit = fadeOut(tween(110)) + shrinkVertically(tween(150)),
        ) {
            displayedFootnote?.let { footnote ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.elevated)
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("FOOTNOTE ${footnote.label}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800), color = colors.accent)
                    Text(footnote.text, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = colors.textSecondary)
                }
            }
        }
    }
}

internal fun nextExpandedFootnoteId(currentId: String?, tappedId: String): String? =
    tappedId.takeUnless { it == currentId }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranTafsirSheet(
    ayah: QuranAyah?,
    tafsir: String,
    sources: List<TafsirSourceUiModel>,
    selectedSourceId: Int,
    loading: Boolean,
    onSelectSource: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    if (ayah == null) return
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tafsir", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W800), color = colors.text)
                    Text("Ayah ${ayah.ayahNumber}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
                FrozenQuranIconButton(Icons.Rounded.Close, "Close Tafsir", onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
            ) {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(sources, key = { it.id }) { source ->
                            val selected = source.id == selectedSourceId
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                                color = if (selected) colors.accent else colors.textSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) colors.accentSoft else colors.bg)
                                    .border(1.dp, if (selected) colors.accentBorder else colors.border, RoundedCornerShape(999.dp))
                                    .clickable { onSelectSource(source.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                    ) {
                        when {
                            loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp, color = colors.accent)
                                Text("Loading tafsir...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                            tafsir.isNotBlank() -> Text(
                                tafsir,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = colors.textSecondary,
                            )
                            else -> Text(
                                "Tafsir unavailable. Tap to retry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.clickable(onClick = onRetry),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranReaderOverflowSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onBookmarks: () -> Unit,
    onRecentLocations: () -> Unit,
    onReflections: () -> Unit,
    onAudioDownloads: () -> Unit,
    onReaderSettings: () -> Unit,
) {
    if (!visible) return
    FrozenQuranActionSheet(title = "Qur'an", onDismiss = onDismiss) {
        FrozenQuranSheetRow("Bookmarks", Icons.Outlined.BookmarkBorder, onBookmarks)
        FrozenQuranSheetRow("Recent locations", Icons.Outlined.History, onRecentLocations)
        FrozenQuranSheetRow("Reflections", Icons.Outlined.EditNote, onReflections)
        HorizontalDivider(color = VaultThemeTokens.colors.border.copy(alpha = 0.7f))
        FrozenQuranSheetRow("Audio downloads", Icons.Outlined.Download, onAudioDownloads)
        FrozenQuranSheetRow("Reader settings", Icons.Outlined.Settings, onReaderSettings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranRecentLocationsSheet(
    visible: Boolean,
    recents: List<QuranRecentLocation>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    if (!visible) return
    FrozenQuranActionSheet(title = "Recent locations", onDismiss = onDismiss) {
        if (recents.isEmpty()) {
            Text("Recent reading locations will appear here.", style = MaterialTheme.typography.bodyMedium, color = VaultThemeTokens.colors.textSecondary, modifier = Modifier.padding(14.dp))
        } else {
            recents.take(5).forEach { recent ->
                val surah = quranCatalog.firstOrNull { it.num == recent.surahNumber } ?: return@forEach
                FrozenQuranSheetRow("${surah.name} ${recent.ayahNumber}", Icons.Outlined.History) {
                    onOpen("${recent.surahNumber}:${recent.ayahNumber}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranAyahMoreSheet(
    ayah: QuranAyah?,
    onDismiss: () -> Unit,
    onMemoriseFromHere: () -> Unit,
) {
    if (ayah == null) return
    FrozenQuranActionSheet(title = "Ayah ${ayah.ayahNumber}", onDismiss = onDismiss) {
        FrozenQuranSheetRow("Memorise from here", Icons.Outlined.LibraryBooks, onMemoriseFromHere)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrozenQuranActionSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W800), color = colors.text, modifier = Modifier.weight(1f))
                FrozenQuranIconButton(Icons.Rounded.Close, "Close", onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
            ) {
                Column { content() }
            }
        }
    }
}

@Composable
private fun FrozenQuranSheetRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(19.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600), color = colors.text)
    }
}
