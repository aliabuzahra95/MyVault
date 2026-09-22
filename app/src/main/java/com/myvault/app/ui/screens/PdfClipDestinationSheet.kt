@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
internal fun PdfClipDestinationSheet(
    notes: List<NoteEntity>,
    onCreateNew: () -> Unit,
    onSelect: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(notes, query) {
        val term = query.trim()
        if (term.isBlank()) notes else notes.filter {
            it.title.contains(term, ignoreCase = true) || it.bodyPlainText.contains(term, ignoreCase = true)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clip to Note", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.W800)
                Text("Choose an Islamic Study note", color = colors.textMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary) }
        }
        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
            shape = VaultShapes.md,
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("Create New Note", Modifier.padding(start = 8.dp), fontWeight = FontWeight.W700)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 6.dp),
            placeholder = { Text("Search Study notes") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = VaultShapes.md,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = VaultSpacing.screen,
                end = VaultSpacing.screen,
                top = 6.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { note ->
                Surface(onClick = { onSelect(note) }, shape = VaultShapes.sm, color = colors.inset) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            note.title,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No Study notes found", color = colors.textMuted, modifier = Modifier.padding(vertical = 20.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
