package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.SearchResultCard
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onNoteClick: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val query = uiState.query
    var filter by remember { mutableStateOf(SearchFilter.All) }
    val showNotes = filter == SearchFilter.All || filter == SearchFilter.Notes
    val showFolders = filter == SearchFilter.All || filter == SearchFilter.Folders
    BackHandler(enabled = query.isNotBlank()) {
        onQueryChange("")
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.bg) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = VaultSpacing.huge),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            item { SearchHeader(query = query, onQueryChange = onQueryChange, onBackClick = onBackClick) }
            item { SearchFilters(selected = filter, onSelected = { filter = it }) }
            if (showNotes) {
                item { SectionLabel(label = "Notes · ${uiState.notes.size}") }
                if (query.isBlank()) {
                    item { EmptySearchMessage("Start typing to search your notes") }
                } else if (uiState.notes.isEmpty()) {
                    item { EmptySearchMessage("No matching notes") }
                } else {
                    items(
                        items = uiState.notes,
                        key = { it.id },
                        contentType = { "search-note" },
                    ) { result ->
                        SearchResultCard(
                            result = result,
                            query = query,
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            onClick = { onNoteClick(result.id) },
                        )
                    }
                }
            }
            if (showFolders) {
                item { SectionLabel(label = "Folders · ${uiState.folders.size}") }
                if (query.isBlank()) {
                    item { EmptySearchMessage("Folder matches will appear here") }
                } else if (uiState.folders.isEmpty()) {
                    item { EmptySearchMessage("No matching folders") }
                } else {
                    items(
                        items = uiState.folders,
                        key = { it.id },
                        contentType = { "search-folder" },
                    ) { folder -> FolderResultRow(name = folder.name, parent = folder.parentId ?: "Workspace") }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, onBackClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Rounded.Close, "Back", onClick = onBackClick)
        Surface(
            modifier = Modifier.weight(1f),
            color = colors.surface,
            shape = VaultShapes.md,
            border = BorderStroke(1.dp, colors.accentBorder),
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text("Search vault", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                        }
                        inner()
                    },
                )
                IconBtn(Icons.Rounded.Close, "Clear search", onClick = { onQueryChange("") })
            }
        }
    }
}

@Composable
private fun SearchFilters(selected: SearchFilter, onSelected: (SearchFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        items(SearchFilter.entries, key = { it.name }) { filter ->
            SearchFilterChip(filter.label, selected = filter == selected, onClick = { onSelected(filter) })
        }
    }
}

@Composable
private fun SearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = if (selected) colors.accent else colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = if (selected) Color.White else colors.textSecondary,
        )
    }
}

@Composable
private fun EmptySearchMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
        style = MaterialTheme.typography.bodySmall,
        color = VaultThemeTokens.colors.textMuted,
    )
}

private enum class SearchFilter(val label: String) {
    All("All"),
    Notes("Notes"),
    Folders("Folders"),
}

@Composable
private fun FolderResultRow(name: String, parent: String) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(18.dp), tint = colors.accent)
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text)
                Text(parent, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}
