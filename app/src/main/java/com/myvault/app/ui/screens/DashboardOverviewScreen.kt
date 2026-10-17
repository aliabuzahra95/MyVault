package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.myvault.app.data.repository.DashboardActivityItem
import com.myvault.app.data.repository.DashboardActivityState
import com.myvault.app.ui.model.*
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.theme.contentTitleFontWeight
import com.myvault.app.ui.theme.LocalHighContrastTitles
import com.myvault.app.ui.viewmodel.HomeQuranContinue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardOverviewScreen(
    shortcuts: List<DashboardShortcut>,
    activityState: DashboardActivityState,
    quranContinue: HomeQuranContinue?,
    onMenuClick: () -> Unit,
    onOpenShortcut: (DashboardShortcut) -> Unit,
    onUnpinShortcut: (DashboardShortcut) -> Unit,
    onOpenQuran: (String) -> Unit,
    onOpenActivity: (DashboardActivityItem) -> Unit,
    onViewAllLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var allPins by rememberSaveable { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenuClick) { Icon(Icons.Rounded.Menu, "Open navigation", tint = colors.textSecondary) }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text("Continue where you left off", fontSize = 12.sp, color = colors.textMuted)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("dashboard-scroll"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (shortcuts.isNotEmpty()) item(key = "pinned") {
                OverviewSection("Pinned", onViewAll = { allPins = true }, pinned = true) {
                    val grid = rememberLazyGridState()
                    BoxWithConstraints {
                        val width = (maxWidth - 8.dp) / 2
                        val density = LocalDensity.current
                        val measurer = rememberTextMeasurer()
                        val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp,
                            fontWeight = contentTitleFontWeight(LocalHighContrastTitles.current, FontWeight.Medium),
                            textDirection = TextDirection.Content, lineBreak = LineBreak.Heading)
                        val metaStyle = LocalTextStyle.current.copy(fontSize = 11.sp, lineHeight = 15.sp)
                        val cardHeight = remember(shortcuts, width, density, measurer, titleStyle, metaStyle) {
                            val titleWidth = with(density) { (width - 43.dp).roundToPx().coerceAtLeast(1) }
                            val metaWidth = with(density) { (width - 20.dp).roundToPx().coerceAtLeast(1) }
                            shortcuts.maxOf { shortcut ->
                                val titleHeight = measurer.measure(shortcut.title, titleStyle, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = titleWidth)).size.height
                                val metaHeight = measurer.measure(shortcut.metadata, metaStyle, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = metaWidth)).size.height
                                with(density) { (titleHeight + metaHeight).toDp() } + 28.dp
                            }.coerceAtLeast(90.dp)
                        }
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2), state = grid,
                            modifier = Modifier.fillMaxWidth().height(cardHeight * 2 + 8.dp).testTag("dashboard-pins"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shortcuts, key = { it.key }) { shortcut ->
                                ShortcutCard(shortcut, { onOpenShortcut(shortcut) }, Modifier.width(width))
                            }
                        }
                    }
                    val pages = (shortcuts.size + 3) / 4
                    if (pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(pages.coerceAtMost(10)) { page ->
                            Box(Modifier.padding(3.dp).size(6.dp).background(
                                if (page == (grid.firstVisibleItemIndex / 4).coerceAtMost(9)) colors.accent else colors.border, CircleShape))
                        }
                    }
                }
            }
            item(key = "continue") {
                OverviewSection("Continue") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContinueOverviewCard("QUR'AN", quranContinue?.surahName ?: "No saved reading",
                            quranContinue?.let { "Last read · ${it.surahNumber}:${it.ayahNumber}" } ?: "No saved position",
                            Icons.AutoMirrored.Rounded.MenuBook,
                            quranContinue?.let { { onOpenQuran(it.verseKey) } },
                            Modifier.weight(1f).height((64 + 110 * fontScale).dp))
                        ContinueOverviewCard("STUDY", activityState.lastNote?.title ?: "No recent study note",
                            "Last opened note", Icons.Rounded.Description,
                            activityState.lastNote?.let { { onOpenActivity(it) } },
                            Modifier.weight(1f).height((64 + 110 * fontScale).dp))
                    }
                }
            }
            item(key = "library") {
                OverviewSection("Continue reading · Library", onViewAll = onViewAllLibrary) {
                    val recents = dashboardLibraryRecents(activityState.libraryRecents + activityState.recents)
                    if (recents.isEmpty()) Text("No recently opened documents", color = colors.textMuted, fontSize = 13.sp)
                    recents.forEach { item ->
                        Surface(onClick = { onOpenActivity(item) }, shape = VaultShapes.md,
                            color = colors.inset, modifier = Modifier.fillMaxWidth().testTag("recent:${item.destinationId}")) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Description, null, Modifier.size(22.dp), tint = colors.accent)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OverviewTitle(item.title)
                                    Text(dashboardReadingPosition(item.pageIndex, item.pageCount), fontSize = 12.sp,
                                        color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = colors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (allPins) ModalBottomSheet(onDismissRequest = { allPins = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Pinned", style = MaterialTheme.typography.titleLarge) }
            items(shortcuts, key = { it.key }) { shortcut ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShortcutCard(shortcut, { allPins = false; onOpenShortcut(shortcut) }, Modifier.weight(1f))
                    IconButton(onClick = { onUnpinShortcut(shortcut) }) {
                        Icon(Icons.Rounded.PushPin, "Unpin from Dashboard")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(label: String, onViewAll: (() -> Unit)? = null, pinned: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.text, modifier = Modifier.weight(1f, fill = false))
                if (pinned) Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = colors.accent)
            }
            if (onViewAll != null) TextButton(onClick = onViewAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("View all", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(14.dp))
            }
        }
        content()
    }
}

@Composable
private fun OverviewTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = contentTitleFontWeight(LocalHighContrastTitles.current, FontWeight.Medium),
        color = VaultThemeTokens.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content, lineBreak = LineBreak.Heading))
}

@Composable
private fun ShortcutCard(shortcut: DashboardShortcut, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VaultThemeTokens.colors
    Surface(onClick = onOpen, modifier = modifier.testTag("pin:${shortcut.key}"), shape = VaultShapes.md, color = colors.surface,
        border = BorderStroke(1.dp, colors.border)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (shortcut.kind == DashboardShortcutKind.Folder) Icons.Rounded.Folder else Icons.Rounded.Description,
                    null, Modifier.size(17.dp), tint = if (shortcut.kind == DashboardShortcutKind.Document) colors.accent else colors.textSecondary)
                OverviewTitle(shortcut.title, Modifier.weight(1f))
            }
            Text(shortcut.metadata, fontSize = 11.sp, lineHeight = 15.sp, color = colors.textMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ContinueOverviewCard(type: String, title: String, metadata: String, icon: ImageVector,
    onOpen: (() -> Unit)?, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Surface(onClick = { onOpen?.invoke() }, enabled = onOpen != null, modifier = modifier,
        shape = VaultShapes.md, color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = colors.accent)
                Text(type, fontSize = 11.sp, color = colors.textSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OverviewTitle(title)
                Text(metadata, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Continue", fontSize = 13.sp, color = if (onOpen == null) colors.textMuted else colors.accent)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.accent)
            }
        }
    }
}
