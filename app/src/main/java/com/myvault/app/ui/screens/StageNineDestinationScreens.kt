package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.HomeQuranContinue
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.repository.DashboardActivityItem
import com.myvault.app.data.repository.DashboardActivityKind
import com.myvault.app.data.repository.DashboardActivityState

@Composable
internal fun FrozenDestinationHeader(
    title: String,
    subtitle: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Menu, "Open navigation", Modifier.size(20.dp), tint = colors.textSecondary)
            }
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.W800, color = colors.text)
            Text(subtitle, fontSize = 11.sp, color = colors.textMuted)
        }
    }
}

@Composable
internal fun FrozenDashboardScreen(
    continueFile: LibraryFileItem?,
    activityState: DashboardActivityState,
    pinnedNotes: List<VaultNoteCardData>,
    pinnedFiles: List<LibraryFileItem>,
    quranContinue: HomeQuranContinue?,
    reflections: List<QuranReflectionItem>,
    onMenuClick: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenQuran: (String) -> Unit,
    onOpenActivity: (DashboardActivityItem) -> Unit,
    onOpenReflection: (QuranReflectionItem) -> Unit,
    onViewAllReflections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Dashboard", "Continue where you left off", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                DashboardSection("Continue") {
                    DashboardContinueGrid(
                        continueFile = continueFile,
                        activityState = activityState,
                        quranContinue = quranContinue,
                        onOpenFile = onOpenFile,
                        onOpenQuran = onOpenQuran,
                        onOpenActivity = onOpenActivity,
                    )
                }
            }
            if (activityState.recents.isNotEmpty()) {
                item {
                    DashboardSection("Recent", "Recently opened") {
                        Column {
                            activityState.recents.take(4).forEach { recent ->
                                val icon = when (recent.kind) {
                                    DashboardActivityKind.Note -> Icons.Rounded.Description
                                    DashboardActivityKind.Library -> Icons.Rounded.PictureAsPdf
                                    DashboardActivityKind.Course -> Icons.Rounded.School
                                }
                                DashboardRow(recent.title, recent.context, icon) {
                                    onOpenActivity(recent)
                                }
                            }
                        }
                    }
                }
            }
            if (reflections.isNotEmpty()) {
                item {
                    DashboardSection("Reflections", actionLabel = "View all", onAction = onViewAllReflections) {
                        Column {
                            reflections.take(8).forEach { reflection ->
                                DashboardReflectionRow(
                                    reflection = reflection,
                                    onClick = { onOpenReflection(reflection) },
                                )
                            }
                        }
                    }
                }
            }
            if (pinnedNotes.isNotEmpty() || pinnedFiles.isNotEmpty()) {
                item {
                    DashboardSection("Pinned", "${pinnedNotes.size + pinnedFiles.size} items") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(pinnedNotes, key = { "note:${it.id}" }) { note ->
                                DashboardPinnedItem(note.title, "Study", onClick = { onOpenNote(note.id) })
                            }
                            items(pinnedFiles, key = { "file:${it.id}" }) { file ->
                                DashboardPinnedItem(file.name, "Library", onClick = { onOpenFile(file.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContinueGrid(
    continueFile: LibraryFileItem?,
    activityState: DashboardActivityState,
    quranContinue: HomeQuranContinue?,
    onOpenFile: (String) -> Unit,
    onOpenQuran: (String) -> Unit,
    onOpenActivity: (DashboardActivityItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardQuranContinueCard(quranContinue, onOpenQuran, Modifier.weight(1f).height(132.dp))
            DashboardActivityContinueCard(
                type = "Notes",
                item = activityState.lastNote,
                emptyLabel = "No recent note",
                icon = Icons.Rounded.Description,
                onOpen = onOpenActivity,
                modifier = Modifier.weight(1f).height(132.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val libraryActivity = activityState.lastLibrary
            if (libraryActivity != null) {
                DashboardActivityContinueCard(
                    type = "Library",
                    item = libraryActivity,
                    emptyLabel = "No recent file",
                    icon = Icons.Rounded.PictureAsPdf,
                    onOpen = onOpenActivity,
                    modifier = Modifier.weight(1f).height(132.dp),
                )
            } else {
                DashboardPdfContinueCard(continueFile, onOpenFile, Modifier.weight(1f).height(132.dp))
            }
            DashboardActivityContinueCard(
                type = "Courses",
                item = activityState.lastCourse,
                emptyLabel = "No recent course note",
                icon = Icons.Rounded.School,
                onOpen = onOpenActivity,
                modifier = Modifier.weight(1f).height(132.dp),
            )
        }
    }
}

@Composable
private fun DashboardPdfContinueCard(
    file: LibraryFileItem?,
    onOpenFile: (String) -> Unit,
    modifier: Modifier,
) {
    DashboardContinueCard(
        type = "PDF",
        title = file?.name?.withoutPdfExtension() ?: "No recent file",
        metadata = when {
            file?.pageIndex != null && file.pageCount != null -> "Page ${file.pageIndex + 1} of ${file.pageCount}"
            file?.pageIndex != null -> "Page ${file.pageIndex + 1}"
            file != null -> "Document"
            else -> "Library"
        },
        icon = Icons.Rounded.PictureAsPdf,
        onClick = file?.let { { onOpenFile(it.id) } },
        modifier = modifier,
    )
}

@Composable
private fun DashboardQuranContinueCard(
    quran: HomeQuranContinue?,
    onOpenQuran: (String) -> Unit,
    modifier: Modifier,
) {
    DashboardContinueCard(
        type = "Qur'an",
        title = quran?.surahName ?: "No recent location",
        metadata = quran?.let { "${it.surahNumber}:${it.ayahNumber}" } ?: "Qur'an",
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        onClick = quran?.let { { onOpenQuran(it.verseKey) } },
        modifier = modifier,
    )
}

@Composable
private fun DashboardActivityContinueCard(
    type: String,
    item: DashboardActivityItem?,
    emptyLabel: String,
    icon: ImageVector,
    onOpen: (DashboardActivityItem) -> Unit,
    modifier: Modifier,
) {
    val metadata = when {
        item == null -> type
        item.kind == DashboardActivityKind.Library && item.pageIndex != null && item.pageCount != null ->
            "Page ${item.pageIndex + 1} of ${item.pageCount}"
        item.kind == DashboardActivityKind.Library && item.pageIndex != null -> "Page ${item.pageIndex + 1}"
        else -> item.context
    }
    DashboardContinueCard(
        type = type,
        title = item?.title ?: emptyLabel,
        metadata = metadata,
        icon = icon,
        onClick = item?.let { { onOpen(it) } },
        modifier = modifier,
    )
}

@Composable
private fun DashboardContinueCard(
    type: String,
    title: String,
    metadata: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(16.dp), tint = colors.textSecondary)
                Text(type.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.W700, color = colors.textMuted)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontSize = 12.5.sp,
                    lineHeight = 14.5.sp,
                    fontWeight = FontWeight.W700,
                    color = colors.text,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(metadata, fontSize = 9.5.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Continue", fontSize = 9.5.sp, fontWeight = FontWeight.W700, color = colors.textSecondary)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(12.dp), tint = colors.textMuted)
            }
        }
    }
}

@Composable
private fun DashboardReflectionRow(reflection: QuranReflectionItem, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
        shape = VaultShapes.md,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(
                    "${reflection.surahName} · ${reflection.surahNumber}:${reflection.ayahNumber}",
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = colors.text,
                )
                Text(
                    reflection.reflectionPreview,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(15.dp), tint = colors.textMuted)
        }
    }
}

private fun String.withoutPdfExtension(): String =
    if (endsWith(".pdf", ignoreCase = true)) dropLast(4) else this

@Composable
internal fun FrozenFavouritesScreen(
    favourites: List<com.myvault.app.ui.components.VaultTreeItem>,
    onMenuClick: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Favourites", "Saved across your workspace", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
        ) {
            item {
                Text(
                    "Notes you marked as favourites.",
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                )
            }
            if (favourites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.65f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(23.dp), tint = colors.textMuted)
                        Spacer(Modifier.height(7.dp))
                        Text("No favourites", fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
                        Text("Favourite a note to keep it here.", fontSize = 10.5.sp, color = colors.textMuted)
                    }
                }
            } else {
                items(favourites, key = { it.id }) { item ->
                    DashboardRow(
                        title = item.name,
                        meta = "Study · Note",
                        icon = Icons.Rounded.Star,
                        titleFontSize = 13.sp,
                        metaFontSize = 10.5.sp,
                    ) { onOpenNote(item.id) }
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(
    label: String,
    meta: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.W800, color = colors.textMuted)
            when {
                actionLabel != null && onAction != null -> Surface(
                    onClick = onAction,
                    color = Color.Transparent,
                    shape = VaultShapes.sm,
                ) {
                    Text(
                        actionLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.W700,
                        color = colors.textSecondary,
                    )
                }
                meta != null -> Text(meta, fontSize = 10.5.sp, color = colors.textMuted)
            }
        }
        content()
    }
}

@Composable
internal fun DashboardRow(
    title: String,
    meta: String,
    icon: ImageVector,
    outlined: Boolean = false,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    metaFontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (outlined) 61.dp else 52.dp),
        shape = if (outlined) VaultShapes.lg else VaultShapes.md,
        color = if (outlined) colors.surface else Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, colors.border) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = titleFontSize, fontWeight = FontWeight.W700, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = metaFontSize, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.textMuted)
        }
    }
}

@Composable
private fun DashboardPinnedItem(title: String, meta: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 154.dp, height = 58.dp),
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PushPin, null, Modifier.size(15.dp), tint = colors.accent)
            Column(Modifier.padding(start = 5.dp)) {
                Text(title, fontSize = 12.2.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = 10.2.sp, color = colors.textMuted)
            }
        }
    }
}
