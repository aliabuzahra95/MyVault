@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.PdfNotepadUiState

@Composable
internal fun PdfStudyNotepadSheet(
    state: PdfNotepadUiState,
    notes: List<NoteEntity>,
    onSelectNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSave: (String, VaultRichTextDocument, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = null,
    ) {
        PdfStudyNotepadContent(
            state = state,
            notes = notes,
            onSelectNote = onSelectNote,
            onCreateNote = onCreateNote,
            onOpenNote = onOpenNote,
            onSave = onSave,
            onDismiss = onDismiss,
            showDragHandle = true,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        )
    }
}

@Composable
internal fun PdfStudyNotepadPane(
    state: PdfNotepadUiState,
    notes: List<NoteEntity>,
    onSelectNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSave: (String, VaultRichTextDocument, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = VaultThemeTokens.colors.surface) {
        PdfStudyNotepadContent(
            state = state,
            notes = notes,
            onSelectNote = onSelectNote,
            onCreateNote = onCreateNote,
            onOpenNote = onOpenNote,
            onSave = onSave,
            onDismiss = onDismiss,
            showDragHandle = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PdfStudyNotepadContent(
    state: PdfNotepadUiState,
    notes: List<NoteEntity>,
    onSelectNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSave: (String, VaultRichTextDocument, Boolean) -> Unit,
    onDismiss: () -> Unit,
    showDragHandle: Boolean,
    modifier: Modifier,
) {
    val colors = VaultThemeTokens.colors
    val note = state.note
    var value by remember(note?.id) {
        mutableStateOf(TextFieldValue(state.document.text, TextRange(state.document.text.length)))
    }
    var marks by remember(note?.id) { mutableStateOf(state.document.styleMarks) }
    var links by remember(note?.id) { mutableStateOf(state.document.noteLinks) }
    var pendingStyles by remember(note?.id) { mutableStateOf(emptySet<VaultInlineStyle>()) }
    var noteMenuOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(note?.id, state.document) {
        if (note != null && value.text != state.document.text) {
            value = TextFieldValue(state.document.text, TextRange(state.document.text.length))
            marks = state.document.styleMarks
            links = state.document.noteLinks
            pendingStyles = emptySet()
        }
    }

    fun currentDocument() = VaultRichTextDocument(value.text, marks, links)
    fun saveCurrent(immediate: Boolean) {
        note?.id?.let { onSave(it, currentDocument(), immediate) }
    }
    fun dismissSafely() {
        saveCurrent(true)
        onDismiss()
    }
    fun toggleStyle(style: VaultInlineStyle) {
        val update = applyVaultStyleFromToolbar(value, marks, pendingStyles, style)
        marks = update.marks
        pendingStyles = update.pendingStyles
        saveCurrent(false)
    }

    val latestDocument by androidx.compose.runtime.rememberUpdatedState(currentDocument())
    val latestNoteId by androidx.compose.runtime.rememberUpdatedState(note?.id)
    DisposableEffect(note?.id) {
        onDispose {
            latestNoteId?.let { onSave(it, latestDocument, true) }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = VaultSpacing.screen),
    ) {
        if (showDragHandle) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = VaultShapes.pill,
                color = colors.border,
            ) {
                Spacer(Modifier.size(width = 42.dp, height = 4.dp))
            }
        }
        Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp), tint = colors.accent)
                Text(
                    "PDF notepad",
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W800,
                )
                note?.let {
                    IconButton(onClick = { saveCurrent(true); onOpenNote(it.id) }) {
                        Icon(Icons.Rounded.OpenInNew, "Open note", Modifier.size(19.dp), tint = colors.textSecondary)
                    }
                }
                IconButton(onClick = ::dismissSafely) {
                    Icon(Icons.Rounded.Close, "Close notepad", Modifier.size(20.dp), tint = colors.textSecondary)
                }
        }

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { noteMenuOpen = true },
                        shape = VaultShapes.md,
                        color = colors.inset,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                note?.title ?: "Loading note…",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                color = colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                            )
                            Icon(Icons.Rounded.ArrowDropDown, "Switch note", tint = colors.textMuted)
                        }
                    }
                    DropdownMenu(
                        expanded = noteMenuOpen,
                        onDismissRequest = { noteMenuOpen = false },
                        containerColor = colors.surface,
                    ) {
                        notes.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.title, maxLines = 1, color = colors.text) },
                                onClick = {
                                    saveCurrent(true)
                                    noteMenuOpen = false
                                    onSelectNote(candidate.id)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { saveCurrent(true); onCreateNote() }) {
                    Icon(Icons.Rounded.Add, "Create new Study note", tint = colors.accent)
                }
        }

        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PdfNotepadFormatButton("B", FontWeight.W800, onClick = { toggleStyle(VaultInlineStyle.Bold) })
                PdfNotepadFormatButton("I", fontStyle = FontStyle.Italic, onClick = { toggleStyle(VaultInlineStyle.Italic) })
                PdfNotepadFormatButton("U", decoration = TextDecoration.Underline, onClick = { toggleStyle(VaultInlineStyle.Underline) })
                PdfNotepadFormatButton("H", FontWeight.W800, onClick = { toggleStyle(VaultInlineStyle.Heading2) })
                PdfNotepadFormatButton("❝", onClick = { toggleStyle(VaultInlineStyle.Quote) })
        }

        Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = VaultShapes.md,
                color = colors.bg,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { updated ->
                        val oldValue = value
                        marks = handleVaultRichTextChange(oldValue, updated, marks, pendingStyles)
                        links = handleVaultNoteLinkChange(oldValue, updated, links)
                        value = updated
                        saveCurrent(false)
                    },
                    modifier = Modifier.fillMaxSize().padding(16.dp).focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = colors.text,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    visualTransformation = VaultRichTextVisualTransformation(marks, links, colors),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize()) {
                            if (value.text.isEmpty()) {
                                Text("Start writing…", color = colors.textMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
        }
        TextButton(
                onClick = ::dismissSafely,
                modifier = Modifier.align(Alignment.End).padding(vertical = 8.dp),
        ) {
            Text("Done", color = colors.accent, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun PdfNotepadFormatButton(
    label: String,
    weight: FontWeight = FontWeight.W500,
    fontStyle: FontStyle = FontStyle.Normal,
    decoration: TextDecoration? = null,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    TextButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Text(
            label,
            color = colors.textSecondary,
            fontWeight = weight,
            fontStyle = fontStyle,
            textDecoration = decoration,
            fontSize = 14.sp,
        )
    }
}
