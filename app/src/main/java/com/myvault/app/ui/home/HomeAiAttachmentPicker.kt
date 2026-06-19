package com.myvault.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ai.home.HomeAiAttachableItem
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun HomeAiAttachmentPicker(
    items: List<HomeAiAttachableItem>,
    selectedItems: List<HomeAiAttachableItem>,
    onToggle: (HomeAiAttachableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(items, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            items
        } else {
            items.filter {
                it.title.contains(normalized, ignoreCase = true) ||
                    it.subtitle.contains(normalized, ignoreCase = true) ||
                    it.type.label.contains(normalized, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.surface,
            shape = VaultShapes.lg,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp)
                        .background(colors.inset, VaultShapes.md)
                        .border(1.dp, colors.border, VaultShapes.md)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = colors.text,
                            lineHeight = 18.sp,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                    )
                    if (query.isBlank()) {
                        Text(
                            text = "Search note titles...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (filteredItems.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "No matching note titles",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    items(filteredItems, key = { "${it.type}:${it.id}" }) { item ->
                        val selected = selectedItems.any { it.id == item.id && it.type == item.type }
                        VaultAiSelectableRow(
                            title = item.title,
                            subtitle = item.subtitle.takeIf { it.isNotBlank() },
                            sourceType = item.type,
                            selected = selected,
                            enabled = true,
                            onClick = { onToggle(item) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
