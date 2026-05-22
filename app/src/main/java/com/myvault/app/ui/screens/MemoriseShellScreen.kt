package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.memorization.MemorizationDashboardGroup
import com.myvault.app.data.quran.memorization.MemorizationDashboardItem
import com.myvault.app.data.quran.memorization.MemorizationOverview
import com.myvault.app.data.quran.memorization.MemorizationUiState
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.launch

@Composable
fun MemoriseShellScreen(
    uiState: MemorizationUiState,
    workspaceTitle: String,
    workspaceOptions: List<String>,
    onWorkspaceSelected: (String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
    onSelectGroup: (MemorizationDashboardGroup) -> Unit,
    onSelectSurah: (Int) -> Unit,
    onSelectAyah: (Int) -> Unit,
    onStartSelectedAyah: () -> Unit,
    onMarkReviewed: (String) -> Unit,
    onToggleMemorized: (String) -> Unit,
    onToggleRevision: (String) -> Unit,
    onToggleWeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var startSheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
        ) {
            item {
                MemoriseHeader(onStart = { startSheetOpen = true })
            }

            item {
                MemoriseMetrics(
                    overview = uiState.overview,
                    selectedGroup = uiState.selectedGroup,
                    onSelectGroup = onSelectGroup,
                )
            }

            uiState.continueItem?.let { item ->
                item {
                    MemoriseFocusCard(
                        item = item,
                        onReviewed = { onMarkReviewed(item.record.verseKey) },
                        onToggleMemorized = { onToggleMemorized(item.record.verseKey) },
                    )
                }
            }

            item {
                MemoriseGroupFilters(
                    selectedGroup = uiState.selectedGroup,
                    onSelectGroup = onSelectGroup,
                )
            }

            if (uiState.dashboardItems.isEmpty()) {
                item {
                    MemoriseEmptyState(onStart = { startSheetOpen = true })
                }
            } else {
                items(uiState.dashboardItems, key = { it.record.verseKey }) { item ->
                    MemoriseRecordRow(
                        item = item,
                        onReviewed = { onMarkReviewed(item.record.verseKey) },
                        onToggleMemorized = { onToggleMemorized(item.record.verseKey) },
                        onToggleRevision = { onToggleRevision(item.record.verseKey) },
                        onToggleWeak = { onToggleWeak(item.record.verseKey) },
                    )
                }
            }
        }
    }

    MemoriseStartSheet(
        visible = startSheetOpen,
        selectedSurah = uiState.selectedSurah,
        selectedAyah = uiState.selectedAyah,
        onDismiss = { startSheetOpen = false },
        onSelectSurah = onSelectSurah,
        onSelectAyah = onSelectAyah,
        onStart = {
            onStartSelectedAyah()
            startSheetOpen = false
        },
    )
}

@Composable
private fun MemoriseHeader(onStart: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Memorise",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W900),
                color = colors.text,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Your focused Qur'an memorisation space",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        MemoriseSmallButton(label = "Start", selected = true, onClick = onStart)
    }
}

@Composable
private fun MemoriseMetrics(
    overview: MemorizationOverview,
    selectedGroup: MemorizationDashboardGroup,
    onSelectGroup: (MemorizationDashboardGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MemoriseMetricCard("Started", overview.startedCount.toString(), selectedGroup == MemorizationDashboardGroup.Started, { onSelectGroup(MemorizationDashboardGroup.Started) }, Modifier.weight(1f))
            MemoriseMetricCard("Ayahs", overview.memorizedCount.toString(), selectedGroup == MemorizationDashboardGroup.Memorised, { onSelectGroup(MemorizationDashboardGroup.Memorised) }, Modifier.weight(1f))
            MemoriseMetricCard("Revision", overview.revisionCount.toString(), selectedGroup == MemorizationDashboardGroup.Revision, { onSelectGroup(MemorizationDashboardGroup.Revision) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MemoriseMetricCard("Difficult", overview.difficultCount.toString(), selectedGroup == MemorizationDashboardGroup.Difficult, { onSelectGroup(MemorizationDashboardGroup.Difficult) }, Modifier.weight(1f))
            MemoriseMetricCard("Surahs", overview.memorizedSurahCount.toString(), false, {}, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MemoriseMetricCard(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900), color = if (selected) colors.accent else colors.text)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700), color = colors.textSecondary)
    }
}

@Composable
private fun MemoriseFocusCard(
    item: MemorizationDashboardItem,
    onReviewed: () -> Unit,
    onToggleMemorized: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.accentBorder.copy(alpha = 0.76f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(colors.accentSoft, RoundedCornerShape(14.dp))
                    .border(1.dp, colors.accentBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = colors.accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Continue Memorising", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W900), color = colors.textMuted)
                Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W900), color = colors.text)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
                MemoriseSmallButton("Reviewed", false, onReviewed)
                MemoriseSmallButton(if (item.record.isMemorized) "Unmark" else "Memorised", item.record.isMemorized, onToggleMemorized)
            }
        }
    }
}

@Composable
private fun MemoriseGroupFilters(
    selectedGroup: MemorizationDashboardGroup,
    onSelectGroup: (MemorizationDashboardGroup) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MemorizationDashboardGroup.entries.forEach { group ->
            MemoriseSmallButton(
                label = group.label,
                selected = selectedGroup == group,
                onClick = { onSelectGroup(group) },
            )
        }
    }
}

@Composable
private fun MemoriseRecordRow(
    item: MemorizationDashboardItem,
    onReviewed: () -> Unit,
    onToggleMemorized: () -> Unit,
    onToggleRevision: () -> Unit,
    onToggleWeak: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.record.ayahNumber.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900), color = colors.textMuted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W900), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            MemoriseSmallButton("Reviewed", false, onReviewed)
            MemoriseSmallButton("Memorised", item.record.isMemorized, onToggleMemorized)
            MemoriseIconButton(Icons.Rounded.Flag, item.record.isRevision, onToggleRevision)
            MemoriseIconButton(Icons.Rounded.CheckCircle, item.record.isWeak, onToggleWeak)
        }
    }
}

@Composable
private fun MemoriseEmptyState(onStart: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("No ayahs here yet", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W900), color = colors.text)
            Text("Start with one ayah. The dashboard will grow as you review, mark difficult ayahs, and complete memorisation.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            MemoriseSmallButton("Start memorising", true, onStart)
        }
    }
}

@Composable
private fun MemoriseSmallButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.elevated)
            .border(1.dp, if (selected) colors.accentBorder else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W800),
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun MemoriseIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else colors.elevated)
            .border(1.dp, if (selected) colors.accentBorder else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(15.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriseStartSheet(
    visible: Boolean,
    selectedSurah: SurahInfo,
    selectedAyah: Int,
    onDismiss: () -> Unit,
    onSelectSurah: (Int) -> Unit,
    onSelectAyah: (Int) -> Unit,
    onStart: () -> Unit,
) {
    if (!visible) return
    val colors = VaultThemeTokens.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSurahs by rememberSaveable { mutableStateOf(false) }

    fun close() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
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
                .heightIn(max = 640.dp)
                .padding(bottom = 10.dp),
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
                    Text("Start Memorising", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900), color = colors.text)
                    Text("${selectedSurah.name} • Ayah $selectedAyah", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                IconBtn(Icons.Rounded.Close, "Close", onClick = ::close)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 4.dp),
            ) {
                item {
                    MemoriseSelectorCard(
                        title = selectedSurah.name,
                        subtitle = "${selectedSurah.arabic} • ${selectedSurah.ayat} ayat",
                        onClick = { showSurahs = !showSurahs },
                    )
                }
                if (showSurahs) {
                    items(quranCatalog, key = { it.num }) { surah ->
                        MemoriseSelectorCard(
                            title = "${surah.num}. ${surah.name}",
                            subtitle = "${surah.arabic} • ${surah.ayat} ayat",
                            selected = surah.num == selectedSurah.num,
                            onClick = {
                                onSelectSurah(surah.num)
                                showSurahs = false
                            },
                        )
                    }
                } else {
                    item {
                        Text("Choose ayah", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, letterSpacing = 1.sp), color = colors.textMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                    items((1..selectedSurah.ayat).toList(), key = { it }) { ayah ->
                        MemoriseSelectorCard(
                            title = "Ayah $ayah",
                            subtitle = "${selectedSurah.name} ${selectedSurah.num}:$ayah",
                            selected = ayah == selectedAyah,
                            onClick = { onSelectAyah(ayah) },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        MemoriseSmallButton("Start ayah", true, onStart)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoriseSelectorCard(
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W900), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
