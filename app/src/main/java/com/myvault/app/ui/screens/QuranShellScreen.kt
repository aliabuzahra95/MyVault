package com.myvault.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun QuranShellScreen(
    workspaceTitle: String,
    workspaceOptions: List<String>,
    onWorkspaceSelected: (String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            QuranSurahList(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun QuranSurahList(
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var search by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("All") }
    var selectedSurah by rememberSaveable { mutableStateOf(1) }
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

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = VaultSpacing.screen,
            end = VaultSpacing.screen,
            top = 8.dp,
            bottom = 132.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = "Qur'an",
                style = MaterialTheme.typography.displayMedium,
                color = colors.text,
            )
            Text(
                text = "Select Surah",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            QuranSearchBar(
                query = search,
                onQueryChange = { search = it },
            )
        }

        item {
            QuranTypeFilters(
                selected = typeFilter,
                onSelected = { typeFilter = it },
            )
        }

        selectedSurah.let { number ->
            quranCatalog.firstOrNull { it.num == number }?.let { surah ->
                item(key = "selected_${surah.num}") {
                    QuranSelectedSurahNotice(surah = surah)
                }
            }
        }

        juzGroups.forEach { (juz, surahs) ->
            item(key = "juz_$juz") {
                JuzDivider(juzNumber = juz)
            }
            items(items = surahs, key = { it.num }) { surah ->
                SurahRow(
                    surah = surah,
                    isCurrent = surah.num == selectedSurah,
                    onClick = { selectedSurah = surah.num },
                )
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
            .clip(VaultShapes.pill)
            .background(bg)
            .border(1.5.dp, border, VaultShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 14.dp),
    )
}

@Composable
private fun QuranSelectedSurahNotice(surah: SurahInfo) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = VaultShapes.md,
        color = colors.accentSoft.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, colors.accentBorder.copy(alpha = 0.8f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "${surah.name} selected. Reader opens in the next migration pass.",
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = colors.accent,
        )
    }
}

@Composable
private fun JuzDivider(juzNumber: Int) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "JUZ $juzNumber",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.9.sp,
                fontWeight = FontWeight.W800,
            ),
            color = colors.textSecondary.copy(alpha = 0.72f),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.accent.copy(alpha = 0.14f),
            thickness = 1.dp,
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
    val rowBg by animateColorAsState(
        targetValue = if (isCurrent) colors.elevated.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "surahRowBg",
    )
    val rowBorder by animateColorAsState(
        targetValue = if (isCurrent) colors.border.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "surahRowBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VaultShapes.md)
            .background(rowBg)
            .border(width = 1.dp, color = rowBorder, shape = VaultShapes.md)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, colors.border.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = surah.num.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = surah.type,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${surah.ayat} ayat",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }

        Text(
            text = surah.arabic,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.End,
            ),
            color = colors.textSecondary,
            maxLines = 1,
        )

        if (isCurrent) {
            Text(
                text = ">",
                color = colors.textMuted,
                fontSize = 18.sp,
            )
        }
    }
}
