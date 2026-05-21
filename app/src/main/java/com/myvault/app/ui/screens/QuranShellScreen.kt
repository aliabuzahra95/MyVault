package com.myvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
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
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
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
    onIncreaseFontScale: () -> Unit,
    onDecreaseFontScale: () -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
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
                onOpenSettings = onSettingsClick,
                onIncreaseFontScale = onIncreaseFontScale,
                onDecreaseFontScale = onDecreaseFontScale,
                onLastReadAyahChanged = onLastReadAyahChanged,
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
private fun QuranReaderSurface(
    uiState: QuranReaderUiState,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onIncreaseFontScale: () -> Unit,
    onDecreaseFontScale: () -> Unit,
    onLastReadAyahChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val listState = rememberLazyListState()
    val hasBismillahHeader = uiState.selectedSurah.num != 1 && uiState.selectedSurah.num != 9
    var lastScrolledSurah by rememberSaveable { mutableIntStateOf(-1) }

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
            onOpenSettings = onOpenSettings,
            onIncreaseFontScale = onIncreaseFontScale,
            onDecreaseFontScale = onDecreaseFontScale,
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
    }
}

@Composable
private fun QuranTopBar(
    surah: SurahInfo,
    onOpenSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onIncreaseFontScale: () -> Unit,
    onDecreaseFontScale: () -> Unit,
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
            ReaderTopIconButton(onClick = onDecreaseFontScale) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Smaller Arabic text",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onIncreaseFontScale) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Larger Arabic text",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            ReaderTopIconButton(onClick = onOpenSelector) {
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
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
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
            ),
            color = colors.text,
            textAlign = TextAlign.Center,
            lineHeight = 41.sp,
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
) {
    val colors = VaultThemeTokens.colors
    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 15.dp)
            .shadow(
                elevation = 3.dp,
                shape = cardShape,
                clip = false,
            )
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, cardShape)
            .padding(vertical = 10.dp, horizontal = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Transparent)
                        .border(1.dp, colors.border, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ayah.ayahNumber.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                        color = colors.textSecondary,
                    )
                }
            }

            Text(
                text = ayah.arabicText,
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
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
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
            .clip(VaultShapes.md)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), VaultShapes.md)
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
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
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
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "quranFilterBg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "quranFilterBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
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
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = colors.border,
        )
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
        targetValue = if (isCurrent) colors.accentSoft.copy(alpha = 0.82f) else colors.surface,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBg",
    )
    val border by animateColorAsState(
        targetValue = if (isCurrent) colors.accentBorder else colors.border,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) colors.accent else colors.text,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowTitle",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = VaultShapes.lg,
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) colors.accent.copy(alpha = 0.14f) else colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = surah.num.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    color = if (isCurrent) colors.accent else colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
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
                    ),
                    color = if (isCurrent) colors.accent else colors.text,
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
