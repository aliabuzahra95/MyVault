package com.myvault.app.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.QuranTranslationFootnote
import com.myvault.app.data.quran.QuranWord
import com.myvault.app.data.quran.TafsirSourceUiModel
import com.myvault.app.data.quran.memorization.AyahMemorizationStatus
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
@Composable
internal fun AyahRow(
    ayah: QuranAyah,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    isBookmarked: Boolean,
    translation: String,
    translationFootnotes: List<QuranTranslationFootnote>,
    translationTextSize: androidx.compose.ui.unit.TextUnit,
    translationEnabled: Boolean,
    reflections: List<QuranReflectionItem>,
    tafsir: String,
    tafsirSources: List<TafsirSourceUiModel>,
    selectedTafsirSourceId: Int,
    isTafsirExpanded: Boolean,
    isTafsirLoading: Boolean,
    onToggleTafsir: () -> Unit,
    onSelectTafsirSource: (Int) -> Unit,
    onOpenActions: () -> Unit,
    onCreateReflectionNote: () -> Unit,
    onEditReflection: (QuranReflectionItem) -> Unit,
    onSaveReadingPosition: () -> Unit,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    onPlayAudio: () -> Unit,
    isMemorizationActive: Boolean,
    isMemorized: Boolean,
    isMemorizationNeedsRevision: Boolean,
    isMemorizationIncorrect: Boolean,
    isMemorizationWeak: Boolean,
    memorizationAttemptStatus: AyahMemorizationStatus,
    hasMemorizationReview: Boolean,
    memorizationConcealAmount: MemorizationConcealAmount?,
    isMemorizationPanelExpanded: Boolean,
    wordDebugEnabled: Boolean,
    onWordClick: (QuranWord) -> Unit,
    onOpenMemorization: () -> Unit,
    onOpenAiListen: () -> Unit,
    onOpenMemorizationReview: () -> Unit,
    onStartMemorizing: () -> Unit,
    onRemoveMemorizing: () -> Unit,
    onToggleMemorized: () -> Unit,
    onMarkRevised: () -> Unit,
    onToggleNeedsRevision: () -> Unit,
    onToggleIncorrect: () -> Unit,
    onToggleWeakMemorization: () -> Unit,
    onSetMemorizationConcealAmount: (MemorizationConcealAmount?) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val cardShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(9.dp)
    val actionPillTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )
    val showTafsirExpansion = tafsir.length > 420
    var tafsirTextExpanded by rememberSaveable(ayah.verseKey) { mutableStateOf(false) }
    var expandedTranslationFootnoteId by rememberSaveable(ayah.verseKey, translation) { mutableStateOf<String?>(null) }
    var testMenuOpen by rememberSaveable(ayah.verseKey) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 15.dp)
            .shadow(
                elevation = 3.dp,
                shape = cardShape,
                clip = false,
            )
            .clip(cardShape)
            .background(colors.surface.copy(alpha = if (colors == DarkVaultColors) 0.96f else 1f))
            .border(1.dp, colors.surface.copy(alpha = if (colors == DarkVaultColors) 0.96f else 1f), cardShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = onSaveReadingPosition,
                onLongClick = onOpenActions,
            )
            .padding(vertical = 10.dp, horizontal = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Transparent)
                        .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ayah.ayahNumber.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W600,
                            fontSize = 11.sp,
                        ),
                        color = colors.textSecondary,
                    )
                }
                if (isBookmarked) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(Color.Transparent)
                            .border(1.dp, colors.accentBorder, pillShape)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "Bookmarked",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                            color = colors.accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (isMemorizationActive && !isMemorized) {
                    Text(
                        text = memorizationConcealAmount?.badgeLabel ?: "Memorising",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                        color = colors.accent,
                        modifier = Modifier
                            .clip(pillShape)
                            .background(colors.accentSoft)
                            .border(1.dp, colors.accentBorder, pillShape)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (isMemorizationWeak) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.elevated)
                            .border(1.dp, colors.borderStrong, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = "Difficult",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                QuranMemorizationAttemptStatusIndicator(
                    status = memorizationAttemptStatus,
                    onClick = onOpenMemorizationReview.takeIf { hasMemorizationReview },
                )
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(28.dp)
                        .clip(pillShape)
                        .background(colors.accentSoft)
                        .border(1.dp, colors.accentBorder, pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenAiListen,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Start AI Listen",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(pillShape)
                        .background(if (isMemorizationActive) colors.accentSoft else Color.Transparent)
                        .border(1.dp, if (isMemorizationActive) colors.accentBorder else colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenMemorization,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = "Open memorisation",
                        tint = if (isMemorizationActive) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                if (isMemorized) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.accentSoft)
                            .border(1.dp, colors.accentBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Memorised",
                            tint = colors.accent,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = colors.textMuted,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenActions,
                        )
                        .padding(4.dp)
                        .size(18.dp),
                )
            }

            QuranWordFlow(
                ayah = ayah,
                arabicTextSize = arabicTextSize,
                tajweedEnabled = tajweedEnabled,
                memorizationConcealAmount = memorizationConcealAmount,
                wordDebugEnabled = wordDebugEnabled,
                onWordClick = onWordClick,
            )

            if (memorizationConcealAmount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(pillShape)
                        .background(colors.accentSoft)
                        .border(1.dp, colors.accentBorder, pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSetMemorizationConcealAmount(null) },
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "Reveal ayah",
                        style = actionPillTextStyle,
                        color = colors.accent,
                    )
                }
            }

            AnimatedVisibility(
                visible = translationEnabled && translation.isNotBlank(),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) + expandVertically(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 110)) + shrinkVertically(animationSpec = tween(durationMillis = 150)),
            ) {
                val annotatedTranslation = remember(
                    translation,
                    translationFootnotes,
                    colors.accent,
                    expandedTranslationFootnoteId,
                ) {
                    buildAnnotatedString {
                        append(translation)
                        translationFootnotes.forEach { footnote ->
                            val start = footnote.markerStart.coerceIn(0, length)
                            val end = footnote.markerEndExclusive.coerceIn(start, length)
                            if (start < end) {
                                addLink(
                                    clickable = LinkAnnotation.Clickable(
                                        tag = footnote.id,
                                        styles = TextLinkStyles(
                                            style = SpanStyle(
                                                color = colors.accent,
                                                fontWeight = FontWeight.W800,
                                                baselineShift = BaselineShift.Superscript,
                                            ),
                                        ),
                                        linkInteractionListener = {
                                            expandedTranslationFootnoteId = footnote.id
                                                .takeUnless { footnote.id == expandedTranslationFootnoteId }
                                        },
                                    ),
                                    start = start,
                                    end = end,
                                )
                            }
                        }
                    }
                }
                val expandedFootnote = translationFootnotes
                    .firstOrNull { it.id == expandedTranslationFootnoteId }
                Column(
                    modifier = Modifier.padding(bottom = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = annotatedTranslation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textSecondary,
                            fontSize = translationTextSize,
                            lineHeight = (translationTextSize.value * 1.58f).sp,
                        ),
                    )
                    if (translationFootnotes.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Footnotes",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                                color = colors.textMuted,
                                modifier = Modifier.padding(vertical = 5.dp),
                            )
                            translationFootnotes.forEach { footnote ->
                                val selected = footnote.id == expandedTranslationFootnoteId
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) colors.accentSoft else colors.elevated)
                                        .border(
                                            1.dp,
                                            if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f),
                                            CircleShape,
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = LocalIndication.current,
                                            onClick = {
                                                expandedTranslationFootnoteId =
                                                    footnote.id.takeUnless { selected }
                                            },
                                        )
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = footnote.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                                        color = if (selected) colors.accent else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = expandedFootnote != null,
                        enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(190)),
                        exit = fadeOut(animationSpec = tween(110)) + shrinkVertically(animationSpec = tween(150)),
                    ) {
                        expandedFootnote?.let { footnote ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.elevated)
                                    .border(1.dp, colors.border.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "FOOTNOTE ${footnote.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.W900,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = colors.accent,
                                )
                                SelectionContainer {
                                    Text(
                                        text = footnote.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.Transparent)
                        .border(1.dp, if (isTafsirExpanded) colors.accentBorder else colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onToggleTafsir,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Tafsir",
                        style = actionPillTextStyle,
                        color = if (isTafsirExpanded) colors.accent else colors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.Transparent)
                        .border(1.dp, colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onCreateReflectionNote,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Add reflection",
                        style = actionPillTextStyle,
                        color = colors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onPlayAudio,
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAudioLoading) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isAudioPlaying) "Pause ayah audio" else "Play ayah audio",
                            tint = if (isAudioPlaying) colors.text else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isMemorizationPanelExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)) + expandVertically(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 140)) + shrinkVertically(animationSpec = tween(durationMillis = 180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(cardShape)
                        .background(colors.elevated)
                        .border(1.dp, colors.border.copy(alpha = 0.78f), cardShape)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "MEMORISATION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, letterSpacing = 1.sp),
                            color = colors.textMuted,
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            QuranMemorizationButton(
                                label = memorizationConcealAmount?.badgeLabel ?: "Hide",
                                selected = memorizationConcealAmount != null,
                                onClick = { testMenuOpen = true },
                                trailingIcon = true,
                            )
                            DropdownMenu(
                                expanded = testMenuOpen,
                                onDismissRequest = { testMenuOpen = false },
                                modifier = Modifier
                                    .width(178.dp)
                                    .shadow(12.dp, RoundedCornerShape(14.dp), clip = false)
                                    .background(colors.elevated, RoundedCornerShape(14.dp))
                                    .border(1.dp, colors.border.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(14.dp),
                                containerColor = colors.elevated,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                properties = PopupProperties(focusable = true),
                            ) {
                                QuranMemorizationHideMenuItem(
                                    label = "Show All",
                                    selected = memorizationConcealAmount == null,
                                    onClick = {
                                        testMenuOpen = false
                                        onSetMemorizationConcealAmount(null)
                                    },
                                )
                                QuranMemorizationHideMenuItem(
                                    label = "Hide Half",
                                    selected = memorizationConcealAmount == MemorizationConcealAmount.Half,
                                    onClick = {
                                        testMenuOpen = false
                                        onStartMemorizing()
                                        onSetMemorizationConcealAmount(MemorizationConcealAmount.Half)
                                    },
                                )
                                QuranMemorizationHideMenuItem(
                                    label = "Hide All",
                                    selected = memorizationConcealAmount == MemorizationConcealAmount.Full,
                                    onClick = {
                                        testMenuOpen = false
                                        onStartMemorizing()
                                        onSetMemorizationConcealAmount(MemorizationConcealAmount.Full)
                                    },
                                )
                            }
                        }
                        QuranMemorizationButton(
                            label = if (isMemorizationActive) "Remove memorising" else "Mark memorising",
                            selected = isMemorizationActive,
                            onClick = if (isMemorizationActive) onRemoveMemorizing else onStartMemorizing,
                        )
                        QuranMemorizationButton(
                            label = if (isMemorized) "Remove memorised" else "Mark memorised",
                            selected = isMemorized,
                            onClick = onToggleMemorized,
                        )
                        QuranMemorizationButton(
                            label = "Mark revised",
                            selected = false,
                            onClick = onMarkRevised,
                        )
                        QuranMemorizationButton(
                            label = if (isMemorizationNeedsRevision) "Clear revision" else "Needs revision",
                            selected = isMemorizationNeedsRevision,
                            onClick = onToggleNeedsRevision,
                        )
                        QuranMemorizationButton(
                            label = if (isMemorizationIncorrect) "Clear incorrect" else "Incorrect",
                            selected = isMemorizationIncorrect,
                            onClick = onToggleIncorrect,
                        )
                        QuranMemorizationButton(
                            label = if (isMemorizationWeak) "Difficult" else "Mark difficult",
                            selected = isMemorizationWeak,
                            onClick = onToggleWeakMemorization,
                        )
                        QuranMemorizationButton(
                            label = "AI Listen",
                            selected = false,
                            onClick = onOpenAiListen,
                        )
                    }
                }
            }

            if (reflections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reflections.forEach { reflection ->
                        QuranAyahReflectionCard(reflection = reflection, onClick = { onEditReflection(reflection) })
                    }
                }
            }

            AnimatedVisibility(
                visible = isTafsirExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 170)) + expandVertically(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 130)) + shrinkVertically(animationSpec = tween(durationMillis = 180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(cardShape)
                        .background(colors.elevated)
                        .border(1.dp, colors.border.copy(alpha = 0.82f), cardShape)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = "TAFSIR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W800,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.textMuted,
                    )
                    if (tafsirSources.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(end = 4.dp),
                        ) {
                            items(tafsirSources, key = { it.id }) { source ->
                                val selected = source.id == selectedTafsirSourceId
                                Box(
                                    modifier = Modifier
                                        .clip(pillShape)
                                        .background(if (selected) colors.accentSoft else colors.surface)
                                        .border(
                                            1.dp,
                                            if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f),
                                            pillShape,
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onSelectTafsirSource(source.id) },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                                        color = if (selected) colors.accent else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = when {
                                isTafsirLoading -> "Loading tafsir..."
                                tafsir.isNotBlank() -> tafsir
                                else -> "No tafsir is available for this ayah in the selected source."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                            color = colors.textSecondary,
                            maxLines = if (tafsirTextExpanded || !showTafsirExpansion || isTafsirLoading) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isTafsirLoading && showTafsirExpansion) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.78f), pillShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { tafsirTextExpanded = !tafsirTextExpanded },
                                )
                                .padding(vertical = 5.dp, horizontal = 10.dp),
                        ) {
                            Text(
                                text = if (tafsirTextExpanded) "Show less" else "Read more",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
