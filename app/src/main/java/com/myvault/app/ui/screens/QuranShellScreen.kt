package com.myvault.app.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReaderUiState
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.arabicTextSize
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.data.quran.translationTextSize
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.buildQuranArabicText
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

private val UthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)

@Composable
fun QuranShellScreen(
    workspaceTitle: String,
    workspaceOptions: List<String>,
    onWorkspaceSelected: (String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
    uiState: QuranReaderUiState,
    onSelectSurah: (Int) -> Unit,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    onToggleTafsir: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onCreateReflectionNote: (QuranAyah, String, String) -> Unit,
    onOpenBookmark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var search by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("All") }
    var selectorOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            VaultTopBar(
                title = workspaceTitle,
                titleContent = {
                    VaultWorkspaceSwitcher(
                        selectedLabel = workspaceTitle,
                        options = workspaceOptions,
                        onSelected = onWorkspaceSelected,
                    )
                },
            ) {
                IconBtn(Icons.Rounded.WbSunny, "Toggle theme", active = true, onClick = onThemeClick)
                IconBtn(Icons.Rounded.Backup, "Quick cloud backup", active = quickBackupRecommended, onClick = onQuickBackupClick)
                IconBtn(Icons.Rounded.Settings, "Settings", onClick = onSettingsClick)
            }

            QuranReaderSurface(
                uiState = uiState,
                onOpenSelector = { selectorOpen = true },
                onSetArabicFontPercent = onSetArabicFontPercent,
                onSetTranslationFontPercent = onSetTranslationFontPercent,
                onSetTranslationEnabled = onSetTranslationEnabled,
                onSetTajweedEnabled = onSetTajweedEnabled,
                onLastReadAyahChanged = onLastReadAyahChanged,
                onToggleTafsir = onToggleTafsir,
                onToggleBookmark = onToggleBookmark,
                onCreateReflectionNote = onCreateReflectionNote,
                onOpenBookmark = onOpenBookmark,
                modifier = Modifier.fillMaxSize(),
            )
        }

        QuranSurahSelectorOverlay(
            visible = selectorOpen,
            selectedSurah = uiState.selectedSurah.num,
            search = search,
            typeFilter = typeFilter,
            onSearchChange = { search = it },
            onTypeFilterChange = { typeFilter = it },
            onDismiss = { selectorOpen = false },
            onSelect = { surah ->
                onSelectSurah(surah.num)
                selectorOpen = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranReaderSurface(
    uiState: QuranReaderUiState,
    onOpenSelector: () -> Unit,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    onToggleTafsir: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onCreateReflectionNote: (QuranAyah, String, String) -> Unit,
    onOpenBookmark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val hasBismillahHeader = uiState.selectedSurah.num != 1 && uiState.selectedSurah.num != 9
    var lastScrolledSurah by rememberSaveable { mutableIntStateOf(-1) }
    var readerOptionsOpen by rememberSaveable { mutableStateOf(false) }
    var ayahActionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reflectionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    val readerOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.selectedSurah.num, uiState.ayahs.size, uiState.loading) {
        if (!uiState.loading && uiState.ayahs.isNotEmpty() && lastScrolledSurah != uiState.selectedSurah.num) {
            lastScrolledSurah = uiState.selectedSurah.num
            val targetIndex = ((uiState.restoredAyah - 1).coerceAtLeast(0) + if (hasBismillahHeader) 1 else 0)
                .coerceAtMost(uiState.ayahs.lastIndex + if (hasBismillahHeader) 1 else 0)
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState, uiState.selectedSurah.num, uiState.ayahs.size, hasBismillahHeader) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .mapNotNull { firstVisibleIndex ->
                val ayahIndex = if (hasBismillahHeader) {
                    (firstVisibleIndex - 1).coerceAtLeast(0)
                } else {
                    firstVisibleIndex.coerceAtLeast(0)
                }
                uiState.ayahs.getOrNull(ayahIndex)?.ayahNumber
            }
            .distinctUntilChanged()
            .collect { ayahNumber ->
                onLastReadAyahChanged(uiState.selectedSurah.num, ayahNumber)
            }
    }

    Column(
        modifier = modifier,
    ) {
        QuranTopBar(
            surah = uiState.selectedSurah,
            onOpenSelector = onOpenSelector,
            onOpenSettings = { readerOptionsOpen = true },
            onOpenBookmarks = { bookmarksOpen = true },
            onOpenSearch = onOpenSelector,
        )

        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hasBismillahHeader) {
                    item(key = "bismillah_${uiState.selectedSurah.num}") {
                        BismillahHeader(
                            surahName = uiState.selectedSurah.name,
                            surahArabic = uiState.selectedSurah.arabic,
                        )
                    }
                } else {
                    item(key = "surah_label_${uiState.selectedSurah.num}") {
                        SurahLabelHeader(
                            surahName = uiState.selectedSurah.name,
                            surahArabic = uiState.selectedSurah.arabic,
                        )
                    }
                }

                items(
                    items = uiState.ayahs,
                    key = { it.verseKey },
                ) { ayah ->
                    AyahRow(
                        ayah = ayah,
                        arabicTextSize = uiState.arabicTextSize,
                        tajweedEnabled = uiState.tajweedEnabled,
                        isBookmarked = ayah.verseKey in uiState.bookmarkedVerseKeys,
                        translation = ayah.translation,
                        translationTextSize = uiState.translationTextSize,
                        translationEnabled = uiState.translationEnabled,
                        tafsir = uiState.tafsirByVerse[ayah.verseKey].orEmpty(),
                        isTafsirExpanded = uiState.expandedTafsirVerseKey == ayah.verseKey,
                        isTafsirLoading = ayah.verseKey in uiState.loadingTafsirVerseKeys,
                        onToggleTafsir = { onToggleTafsir(ayah.verseKey) },
                        onOpenActions = { ayahActionsTarget = ayah.verseKey },
                        onCreateReflectionNote = { reflectionTarget = ayah.verseKey },
                    )
                }
                item("quran_bottom_pad") {
                    Spacer(
                        Modifier
                            .height(12.dp)
                            .navigationBarsPadding(),
                    )
                }
            }
        }

        if (readerOptionsOpen) {
            ModalBottomSheet(
                onDismissRequest = { readerOptionsOpen = false },
                sheetState = readerOptionsSheetState,
                containerColor = colors.elevated,
                contentColor = colors.text,
                scrimColor = colors.scrim,
                tonalElevation = 0.dp,
                dragHandle = {
                    Surface(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 36.dp, height = 4.dp),
                        color = colors.borderStrong,
                        shape = RoundedCornerShape(999.dp),
                    ) {}
                },
            ) {
                QuranReaderOptionsSheet(
                    fontPercent = uiState.arabicFontPercent,
                    translationFontPercent = uiState.translationFontPercent,
                    translationEnabled = uiState.translationEnabled,
                    tajweedEnabled = uiState.tajweedEnabled,
                    onSetArabicFontPercent = onSetArabicFontPercent,
                    onSetTranslationFontPercent = onSetTranslationFontPercent,
                    onSetTranslationEnabled = onSetTranslationEnabled,
                    onSetTajweedEnabled = onSetTajweedEnabled,
                    onDismiss = { readerOptionsOpen = false },
                )
            }
        }

        val selectedAyah = remember(ayahActionsTarget, uiState.ayahs) {
            uiState.ayahs.firstOrNull { it.verseKey == ayahActionsTarget }
        }
        AyahActionsSheet(
            ayah = selectedAyah,
            surah = uiState.selectedSurah,
            isBookmarked = selectedAyah?.verseKey in uiState.bookmarkedVerseKeys,
            onDismiss = { ayahActionsTarget = null },
            onCopy = {
                selectedAyah?.let { ayah ->
                    clipboardManager.setText(
                        AnnotatedString("${uiState.selectedSurah.name} ${uiState.selectedSurah.num}:${ayah.ayahNumber}\n\n${ayah.arabicText}"),
                    )
                }
                ayahActionsTarget = null
            },
            onToggleBookmark = {
                selectedAyah?.let { onToggleBookmark(it.verseKey) }
                ayahActionsTarget = null
            },
            onCreateReflectionNote = {
                reflectionTarget = selectedAyah?.verseKey
                ayahActionsTarget = null
            },
        )

        val reflectionAyah = remember(reflectionTarget, uiState.ayahs) {
            uiState.ayahs.firstOrNull { it.verseKey == reflectionTarget }
        }
        ReflectionEditorSheet(
            ayah = reflectionAyah,
            surah = uiState.selectedSurah,
            onDismiss = { reflectionTarget = null },
            onSave = { title, body ->
                reflectionAyah?.let { onCreateReflectionNote(it, title, body) }
                reflectionTarget = null
            },
        )

        QuranBookmarksSheet(
            visible = bookmarksOpen,
            bookmarkedVerseKeys = uiState.bookmarkedVerseKeys,
            onDismiss = { bookmarksOpen = false },
            onOpenBookmark = { verseKey ->
                bookmarksOpen = false
                onOpenBookmark(verseKey)
            },
        )
    }
}

@Composable
private fun QuranTopBar(
    surah: SurahInfo,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSearch: () -> Unit,
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
            ReaderTopIconButton(onClick = onOpenBookmarks) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Qur'an bookmarks",
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
            ReaderTopIconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search Surah",
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
private fun BismillahHeader(
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
                fontFamily = UthmaniHafsFamily,
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
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranReaderOptionsSheet(
    fontPercent: Int,
    translationFontPercent: Int,
    translationEnabled: Boolean,
    tajweedEnabled: Boolean,
    onSetArabicFontPercent: (Int) -> Unit,
    onSetTranslationFontPercent: (Int) -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onSetTajweedEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            subtitle = "Show Sahih International beneath each ayah.",
            checked = translationEnabled,
            onCheckedChange = onSetTranslationEnabled,
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

@Composable
private fun SurahLabelHeader(
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

@Composable
private fun AyahRow(
    ayah: QuranAyah,
    arabicTextSize: androidx.compose.ui.unit.TextUnit,
    tajweedEnabled: Boolean,
    isBookmarked: Boolean,
    translation: String,
    translationTextSize: androidx.compose.ui.unit.TextUnit,
    translationEnabled: Boolean,
    tafsir: String,
    isTafsirExpanded: Boolean,
    isTafsirLoading: Boolean,
    onToggleTafsir: () -> Unit,
    onOpenActions: () -> Unit,
    onCreateReflectionNote: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val cardShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(9.dp)
    val renderedArabic = remember(ayah.verseKey, ayah.arabicText, ayah.tajweedAnnotations, tajweedEnabled, colors) {
        runCatching {
            buildQuranArabicText(
                text = ayah.arabicText,
                annotations = ayah.tajweedAnnotations,
                tajweedEnabled = tajweedEnabled,
                isDark = colors == DarkVaultColors,
            )
        }.getOrElse {
            Log.w("QuranShellScreen", "Falling back to plain Arabic rendering for ${ayah.verseKey}", it)
            buildQuranArabicText(
                text = ayah.arabicText,
                annotations = emptyList(),
                tajweedEnabled = false,
                isDark = colors == DarkVaultColors,
            )
        }
    }
    val showTafsirExpansion = tafsir.length > 420
    var tafsirTextExpanded by rememberSaveable(ayah.verseKey) { mutableStateOf(false) }
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

            Text(
                text = renderedArabic,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = UthmaniHafsFamily,
                    fontSize = arabicTextSize,
                    fontWeight = FontWeight.Normal,
                    textDirection = TextDirection.Rtl,
                    lineHeight = (arabicTextSize.value * 1.95f).sp,
                ),
                color = colors.text,
                textAlign = TextAlign.Right,
            )

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(if (isTafsirExpanded) colors.surface else Color.Transparent)
                        .border(1.dp, if (isTafsirExpanded) colors.accentBorder else colors.border.copy(alpha = 0.7f), pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleTafsir,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Tafsir",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600),
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
                            indication = null,
                            onClick = onCreateReflectionNote,
                        )
                        .padding(vertical = 6.dp, horizontal = 13.dp),
                ) {
                    Text(
                        text = "Add reflection",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600),
                        color = colors.textSecondary,
                    )
                }
            }

            AnimatedVisibility(
                visible = translationEnabled && translation.isNotBlank(),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) + expandVertically(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 110)) + shrinkVertically(animationSpec = tween(durationMillis = 150)),
            ) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = translationTextSize,
                        lineHeight = (translationTextSize.value * 1.58f).sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
                )
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
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = when {
                                isTafsirLoading -> "Loading tafsir..."
                                tafsir.isNotBlank() -> tafsir
                                else -> "No tafsir is available for this ayah in the current abridged source."
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AyahActionsSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCreateReflectionNote: () -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sectionShape = RoundedCornerShape(16.dp)

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
                .heightIn(max = 560.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ayah actions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                    color = colors.text,
                )
                IconBtn(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close ayah actions",
                    onClick = onDismiss,
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                        color = colors.text,
                    )
                    if (isBookmarked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.accentSoft)
                                .border(1.dp, colors.accentBorder, RoundedCornerShape(9.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "Bookmarked",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                                color = colors.accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ayah.arabicText,
                    style = TextStyle(
                        fontFamily = UthmaniHafsFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        textDirection = TextDirection.Rtl,
                        lineHeight = 39.sp,
                    ),
                    color = colors.text,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape),
            ) {
                AyahActionRow(
                    label = "Copy ayah",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
                AyahActionRow(
                    label = if (isBookmarked) "Remove bookmark" else "Bookmark ayah",
                    icon = Icons.Rounded.Bookmark,
                    onClick = onToggleBookmark,
                )
                AyahActionRow(
                    label = "Add reflection/note",
                    icon = Icons.Rounded.Edit,
                    onClick = onCreateReflectionNote,
                    isLast = true,
                )
            }
        }
    }
}

@Composable
private fun AyahActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = colors.border.copy(alpha = 0.65f),
            thickness = 1.dp,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReflectionEditorSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable(ayah.verseKey) { mutableStateOf("Reflection on ${surah.name} ${surah.num}:${ayah.ayahNumber}") }
    var body by rememberSaveable(ayah.verseKey) { mutableStateOf("") }
    val fieldShape = RoundedCornerShape(16.dp)

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
                .heightIn(max = 620.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Add reflection",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close reflection", onClick = onDismiss)
            }

            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ayah.arabicText,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = UthmaniHafsFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            textDirection = TextDirection.Rtl,
                            lineHeight = 38.sp,
                        ),
                        color = colors.text,
                        textAlign = TextAlign.Right,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ayah.translation.isNotBlank()) {
                        Text(
                            text = ayah.translation,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            QuranTextInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "Reflection title",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = fieldShape,
            )

            QuranTextInput(
                value = body,
                onValueChange = { body = it },
                placeholder = "Write your reflection...",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp),
                shape = fieldShape,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.textMuted,
                )
                Text(
                    text = "Add later from the saved reflection note",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                ReflectionSheetButton(label = "Close", onClick = onDismiss)
                ReflectionSheetButton(
                    label = "Save",
                    filled = true,
                    onClick = { onSave(title, body) },
                )
            }
        }
    }
}

@Composable
private fun QuranTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    val colors = VaultThemeTokens.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ReflectionSheetButton(
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        color = if (filled) colors.accent else colors.textSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) colors.accentSoft else Color.Transparent)
            .border(1.dp, if (filled) colors.accentBorder else colors.border.copy(alpha = 0.72f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuranBookmarksSheet(
    visible: Boolean,
    bookmarkedVerseKeys: Set<String>,
    onDismiss: () -> Unit,
    onOpenBookmark: (String) -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarks = remember(bookmarkedVerseKeys) {
        bookmarkedVerseKeys
            .mapNotNull { key ->
                val surahNum = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                val ayahNum = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
                val surah = quranCatalog.firstOrNull { it.num == surahNum } ?: return@mapNotNull null
                Triple(key, surah, ayahNum)
            }
            .sortedWith(compareBy({ it.second.num }, { it.third }))
    }

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
                .heightIn(max = 560.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "${bookmarks.size} saved ayah${if (bookmarks.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close bookmarks", onClick = onDismiss)
            }

            if (bookmarks.isEmpty()) {
                Surface(
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Bookmarked ayahs will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(bookmarks, key = { it.first }) { (key, surah, ayahNum) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            onClick = { onOpenBookmark(key) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${surah.name} $ayahNum",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                                        color = colors.text,
                                    )
                                    Text(
                                        text = "${surah.type} • Juz ${surah.juz}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                Text(
                                    text = surah.arabic,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = UthmaniHafsFamily,
                                        textDirection = TextDirection.ContentOrRtl,
                                    ),
                                    color = colors.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSurahSelectorOverlay(
    visible: Boolean,
    selectedSurah: Int,
    search: String,
    typeFilter: String,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (SurahInfo) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val dismissInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    val listState: LazyListState = rememberLazyListState()
    val panelShape = RoundedCornerShape(24.dp)
    val filtered = remember(search, typeFilter) {
        quranCatalog.filter { surah ->
            val q = search.trim().lowercase()
            val matchesType = typeFilter == "All" || surah.type == typeFilter
            val matchesSearch = q.isBlank() ||
                surah.name.lowercase().contains(q) ||
                surah.arabic.contains(search.trim()) ||
                surah.num.toString().contains(q)
            matchesType && matchesSearch
        }
    }
    val juzGroups = remember(filtered) { filtered.groupBy { it.juz }.toList() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                initialOffsetY = { -it / 4 },
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { -it / 5 },
            ),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(12.dp, panelShape, clip = false)
                    .clip(panelShape)
                    .background(colors.bg)
                    .border(1.dp, colors.border.copy(alpha = 0.8f), panelShape)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 4.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.border),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Select Surah",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    IconBtn(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuranSearchBar(query = search, onQueryChange = onSearchChange)
                    QuranTypeFilters(selected = typeFilter, onSelected = onTypeFilterChange)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    juzGroups.forEach { (juz, surahs) ->
                        item(key = "juz_$juz") {
                            JuzDivider(juzNumber = juz)
                            Spacer(Modifier.height(6.dp))
                        }
                        items(items = surahs, key = { it.num }) { surah ->
                            SurahRow(
                                surah = surah,
                                isCurrent = surah.num == selectedSurah,
                                onClick = { onSelect(surah) },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search by name, Arabic, or number...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun QuranTypeFilters(
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf("All 114" to "All", "Makki" to "Makki", "Madani" to "Madani").forEach { (label, key) ->
            QuranFilterPill(
                label = label,
                selected = selected == key,
                onClick = { onSelected(key) },
            )
        }
    }
}

@Composable
private fun QuranFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentSoft else Color.Transparent,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterBg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "quranFilterText",
    )

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = textColor,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun JuzDivider(juzNumber: Int) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Juz $juzNumber",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700, letterSpacing = 0.3.sp),
            color = colors.textMuted,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = colors.border,
        )
    }
}

@Composable
private fun SurahRow(
    surah: SurahInfo,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (isCurrent) colors.elevated else colors.surface,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBg",
    )
    val border by animateColorAsState(
        targetValue = if (isCurrent) colors.borderStrong else colors.border,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = colors.text,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowTitle",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.num.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.W800,
                        fontSize = 19.sp,
                    ),
                    color = titleColor,
                )
                Text(
                    text = "${surah.type} • ${surah.ayat} ayat",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = surah.arabic,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = UthmaniHafsFamily,
                        textDirection = TextDirection.ContentOrRtl,
                        fontWeight = FontWeight.W400,
                        fontSize = 20.sp,
                    ),
                    color = colors.text,
                )
                Text(
                    text = "Juz ${surah.juz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}
