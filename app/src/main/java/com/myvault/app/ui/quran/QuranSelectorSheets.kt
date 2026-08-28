package com.myvault.app.ui.quran

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val QuranSelectorUthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)
private data class QuranAyahSelectorResult(
    val surah: SurahInfo,
    val ayahNumber: Int,
    val arabicText: String,
)
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QuranBookmarksSheet(
    visible: Boolean,
    bookmarkedVerseKeys: Set<String>,
    onDismiss: () -> Unit,
    onOpenBookmark: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = surah.arabic,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = QuranSelectorUthmaniHafsFamily,
                                            textDirection = TextDirection.ContentOrRtl,
                                        ),
                                        color = colors.text,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Remove bookmark",
                                        tint = colors.accent,
                                        modifier = Modifier
                                            .padding(start = 10.dp)
                                            .size(18.dp)
                                            .clickable { onRemoveBookmark(key) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuranSurahSelectorOverlay(
    visible: Boolean,
    selectedSurah: Int,
    search: String,
    typeFilter: String,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (SurahInfo) -> Unit,
    onSelectAyah: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val listState: LazyListState = rememberLazyListState()
    var searchVisible by remember(visible) { mutableStateOf(search.isNotBlank()) }
    var ayahSearchIndex by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(visible) {
        if (visible && ayahSearchIndex.isEmpty()) {
            ayahSearchIndex = withContext(Dispatchers.IO) {
                JSONObject(
                    context.assets.open("qpc_hafs.json").bufferedReader().use { it.readText() },
                ).toAyahSearchIndex()
            }
        }
    }
    val ayahResults = remember(search, typeFilter, ayahSearchIndex) {
        buildQuranAyahSelectorResults(search, typeFilter, ayahSearchIndex)
    }
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
        enter = fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(colors.bg)
                    .padding(bottom = 8.dp)
                    .align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Choose Surah",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                            color = colors.text,
                        )
                        Text(
                            text = quranCatalog.firstOrNull { it.num == selectedSurah }
                                ?.let { "Currently reading ${it.name}" }
                                .orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Surahs",
                            tint = colors.text,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { searchVisible = !searchVisible }
                                .padding(10.dp),
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = colors.text,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(onClick = onDismiss)
                                .padding(10.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (searchVisible) {
                        QuranSearchBar(
                            query = search,
                            onQueryChange = onSearchChange,
                        )
                    }
                    QuranTypeFilters(selected = typeFilter, onSelected = onTypeFilterChange)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${filtered.size} Surahs",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = "Tap to open",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (ayahResults.isNotEmpty()) {
                        item(key = "ayah_results_label") {
                            JuzDivider(juzNumber = 0, label = "Ayah results")
                            Spacer(Modifier.height(2.dp))
                        }
                        items(
                            items = ayahResults,
                            key = { "${it.surah.num}:${it.ayahNumber}" },
                            contentType = { "ayah-search-result" },
                        ) { result ->
                            QuranAyahSearchResultRow(
                                result = result,
                                onClick = { onSelectAyah("${result.surah.num}:${result.ayahNumber}") },
                            )
                        }
                    }
                    juzGroups.forEach { (juz, surahs) ->
                        item(key = "juz_$juz") {
                            JuzDivider(juzNumber = juz)
                            Spacer(Modifier.height(2.dp))
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
    focusRequester: FocusRequester? = null,
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
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
        modifier = Modifier.padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf("All 114" to "All", "Meccan" to "Makki", "Medinan" to "Madani").forEach { (label, key) ->
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun JuzDivider(
    juzNumber: Int,
    label: String = "Juz $juzNumber",
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700, letterSpacing = 0.3.sp),
            color = colors.textMuted,
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
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) colors.accent else colors.text,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "surahRowTitle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 46.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 2.dp, height = 20.dp)
                        .background(colors.accent),
                )
            }
            Text(
                text = surah.num.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W500),
                color = colors.textSecondary,
                modifier = Modifier.padding(start = if (isCurrent) 10.dp else 0.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = surah.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W700),
                color = titleColor,
            )
            Text(
                text = "${surah.type} · ${surah.ayat} ayat",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = colors.textSecondary,
            )
        }

        Text(
            text = surah.arabic,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = QuranSelectorUthmaniHafsFamily,
                textDirection = TextDirection.ContentOrRtl,
                fontWeight = FontWeight.W400,
                fontSize = 21.sp,
            ),
            color = colors.text,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun QuranAyahSearchResultRow(
    result: QuranAyahSelectorResult,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.accentBorder.copy(alpha = 0.72f)),
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accentBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${result.surah.num}:${result.ayahNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                    color = colors.accent,
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = result.surah.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = result.surah.arabic,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = QuranSelectorUthmaniHafsFamily,
                            textDirection = TextDirection.ContentOrRtl,
                            fontWeight = FontWeight.W400,
                        ),
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = result.arabicText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = QuranSelectorUthmaniHafsFamily,
                        textDirection = TextDirection.Rtl,
                        lineHeight = 27.sp,
                    ),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Right,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun buildQuranAyahSelectorResults(
    query: String,
    typeFilter: String,
    ayahSearchIndex: Map<String, String>,
): List<QuranAyahSelectorResult> {
    if (ayahSearchIndex.isEmpty()) return emptyList()
    val numbers = Regex("\\d+").findAll(query).mapNotNull { it.value.toIntOrNull() }.toList()
    if (numbers.size < 2) return emptyList()
    val surahNumber = numbers[0]
    val ayahNumber = numbers[1]
    val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return emptyList()
    if (typeFilter != "All" && surah.type != typeFilter) return emptyList()
    if (ayahNumber !in 1..surah.ayat) return emptyList()
    val verseKey = "$surahNumber:$ayahNumber"
    val text = ayahSearchIndex[verseKey].orEmpty()
    if (text.isBlank()) return emptyList()
    return listOf(QuranAyahSelectorResult(surah = surah, ayahNumber = ayahNumber, arabicText = text))
}

private fun JSONObject.toAyahSearchIndex(): Map<String, String> =
    buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val verseKey = keys.next()
            val text = optJSONObject(verseKey)
                ?.optString("text")
                .orEmpty()
                .stripQuranTrailingVerseMarker()
                .trim()
            if (text.isNotBlank()) put(verseKey, text)
        }
    }

private fun String.stripQuranTrailingVerseMarker(): String =
    replace(Regex("\\s*[۝۞]?\\s*[\\u0660-\\u0669٠-٩]+\\s*$"), "").trim()
