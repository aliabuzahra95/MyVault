package com.myvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    files: List<LibraryFileItem>,
    courses: List<CourseEntity>,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onNoteClick: (String) -> Unit,
    onFolderClick: (FolderEntity) -> Unit,
    onFileClick: (String) -> Unit,
    onCourseClick: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val query = uiState.query
    val normalized = query.trim()
    val matchingFiles = if (normalized.isBlank()) emptyList() else files.filter { it.name.contains(normalized, true) }
    val matchingCourses = if (normalized.isBlank()) emptyList() else courses.filter { it.title.contains(normalized, true) }
    val hasResults = uiState.notes.isNotEmpty() || uiState.folders.isNotEmpty() || matchingFiles.isNotEmpty() || matchingCourses.isNotEmpty()
    BackHandler(enabled = query.isNotBlank()) { onQueryChange("") }

    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Search", "Across MyVault", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    color = colors.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.border),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 13.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = colors.textMuted)
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text, fontSize = 12.5.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { inner ->
                                if (query.isBlank()) Text("Search across MyVault", fontSize = 12.5.sp, color = colors.textMuted)
                                inner()
                            },
                        )
                        if (query.isNotBlank()) {
                            Surface(onClick = { onQueryChange("") }, modifier = Modifier.size(31.dp), shape = VaultShapes.sm, color = Color.Transparent) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, "Clear search", Modifier.size(17.dp), tint = colors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
            if (normalized.isBlank()) {
                item { SearchEmptyState("Search across MyVault", "Find notes, folders, files and courses.") }
            } else if (!hasResults) {
                item { SearchEmptyState("No results", "Try a different title or folder name.") }
            } else {
                if (uiState.notes.isNotEmpty()) {
                    item { SearchSectionLabel("Notes") }
                    items(uiState.notes, key = { "note:${it.id}" }) { result ->
                        SearchRow(result.title, result.folder, Icons.Rounded.Description) { onNoteClick(result.id) }
                    }
                }
                if (uiState.folders.isNotEmpty()) {
                    item { SearchSectionLabel("Folders") }
                    items(uiState.folders, key = { "folder:${it.id}" }) { folder ->
                        SearchRow(folder.name, "${folder.mode.searchWorkspaceLabel()} · Folder", Icons.Rounded.Folder) { onFolderClick(folder) }
                    }
                }
                if (matchingFiles.isNotEmpty()) {
                    item { SearchSectionLabel("Files & PDFs") }
                    items(matchingFiles, key = { "file:${it.id}" }) { file ->
                        SearchRow(file.name, "Library · ${file.kind}", Icons.Rounded.PictureAsPdf) { onFileClick(file.id) }
                    }
                }
                if (matchingCourses.isNotEmpty()) {
                    item { SearchSectionLabel("Courses") }
                    items(matchingCourses, key = { "course:${it.id}" }) { course ->
                        SearchRow(course.title, "Course", Icons.Rounded.School) { onCourseClick(course.id) }
                    }
                }
                item { Spacer(Modifier.height(VaultSpacing.huge)) }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(title: String, subtitle: String) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth().height(390.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(23.dp), tint = colors.textMuted)
        Spacer(Modifier.height(7.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
        Text(subtitle, fontSize = 10.5.sp, color = colors.textMuted)
    }
}

@Composable
private fun SearchSectionLabel(label: String) {
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 3.dp),
        fontSize = 9.3.sp,
        fontWeight = FontWeight.W700,
        color = VaultThemeTokens.colors.textMuted,
    )
}

@Composable
private fun SearchRow(title: String, meta: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = VaultShapes.md, color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 11.7.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = 9.5.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.textMuted)
        }
    }
}

private fun String.searchWorkspaceLabel(): String = when {
    contains("library", ignoreCase = true) -> "Library"
    contains("personal", ignoreCase = true) -> "Personal"
    startsWith("course:") -> "Course"
    else -> "Study"
}
