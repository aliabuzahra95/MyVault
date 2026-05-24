package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.EditorTool
import com.myvault.app.ui.components.EditorToolbar
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.data.repository.AiPromptBuilder
import com.myvault.app.data.repository.AiSuggestion
import com.myvault.app.data.repository.NoteAiAction
import com.myvault.app.data.repository.NoteAiModel
import com.myvault.app.data.repository.NoteAiProvider
import com.myvault.app.data.repository.SelectedTextAiAction
import com.myvault.app.data.repository.displayName
import com.myvault.app.ui.viewmodel.NoteAiChatMessage
import com.myvault.app.ui.viewmodel.NoteAiMessageRole
import com.myvault.app.ui.viewmodel.NoteAiUiState
import com.myvault.app.ui.viewmodel.NoteUiState
import com.myvault.app.ui.viewmodel.NoteTableUiState
import com.myvault.app.ui.viewmodel.SelectedTextAiUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(FlowPreview::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uiState: NoteUiState,
    aiState: NoteAiUiState,
    selectedTextAiState: SelectedTextAiUiState = SelectedTextAiUiState(),
    onBackClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (text: String, styleMarks: List<VaultStyleMark>, noteLinks: List<VaultNoteLink>) -> Unit,
    onRunAiTool: (action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel, title: String, body: String, question: String) -> Unit,
    onRunSelectedTextAi: (action: SelectedTextAiAction, provider: NoteAiProvider, model: NoteAiModel, title: String, body: String, selectedText: String, question: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onClearSelectedTextAi: () -> Unit = {},
    onSelectedTextAiQuestionChange: (question: String, action: SelectedTextAiAction?) -> Unit = { _, _ -> },
    onSendSelectedTextResultToChat: (action: SelectedTextAiAction, selectedText: String, result: String) -> Unit = { _, _, _ -> },
    onClearAiResult: () -> Unit,
    onClearAiConversation: () -> Unit = {},
    onAiProviderSelected: (NoteAiProvider) -> Unit = {},
    onAiModelSelected: (NoteAiModel) -> Unit = {},
    onAiQuestionChange: (String) -> Unit = {},
    onAskAiClick: (selectedText: String?) -> Unit = {},
    modifier: Modifier = Modifier,
    onAttachDocument: (Uri) -> Unit,
    onAttachmentClick: (String) -> Unit = {},
    onPinnedChange: (Boolean) -> Unit = {},
    onFavouriteChange: (Boolean) -> Unit = {},
    onDeleteNote: () -> Unit = {},
    onExportText: (Uri) -> Unit = {},
    onExportPdf: (Uri) -> Unit = {},
    onCreateTable: (rows: Int, columns: Int) -> Unit = { _, _ -> },
    onUpdateTableCell: (tableId: String, row: Int, column: Int, text: String) -> Unit = { _, _, _, _ -> },
    onDeleteTable: (String) -> Unit = {},
    bodyFontSizeSp: Float = 15f,
    autoFocusBody: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val editorScope = rememberCoroutineScope()
    val bodyFocusRequester = remember { FocusRequester() }
    val noteId = uiState.note?.id
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var bodyValue by remember { mutableStateOf(TextFieldValue("")) }
    var styleMarks by remember { mutableStateOf<List<VaultStyleMark>>(emptyList()) }
    var noteLinks by remember { mutableStateOf<List<VaultNoteLink>>(emptyList()) }
    var pendingInlineStyles by remember { mutableStateOf<Set<VaultInlineStyle>>(emptySet()) }
    var loadedNoteId by remember { mutableStateOf<String?>(null) }
    var editorReady by remember { mutableStateOf(false) }
    var lastSavedText by remember { mutableStateOf<String?>(null) }
    var lastSavedMarks by remember { mutableStateOf<List<VaultStyleMark>>(emptyList()) }
    var lastSavedLinks by remember { mutableStateOf<List<VaultNoteLink>>(emptyList()) }
    var linkDialogOpen by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var colorToolbarOpen by remember { mutableStateOf(false) }
    var tableDialogOpen by remember { mutableStateOf(false) }
    var tableDeleteRequest by remember { mutableStateOf<String?>(null) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var intelligentStructureOpen by remember { mutableStateOf(false) }
    var selectedTextTarget by remember { mutableStateOf<SelectedTextTarget?>(null) }
    var replaceAiDialogOpen by remember { mutableStateOf(false) }
    var structureOnlyNotice by remember { mutableStateOf<String?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    var undoHistory by remember(noteId) { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var redoHistory by remember(noteId) { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var restoringHistory by remember(noteId) { mutableStateOf(false) }
    val isPinned = uiState.note?.isPinned == true
    val isFavourite = uiState.note?.isFavourite == true
    val hasTables = uiState.tables.isNotEmpty()
    val tableEditorScrollState = rememberScrollState()
    val breadcrumbItems = remember(uiState.folderPath, title.text) {
        listOf("My Vault") + uiState.folderPath + listOf(title.text.ifBlank { "Note" })
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachDocument)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachDocument)
    }
    val exportTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(onExportText)
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(onExportPdf)
    }
    val supportedTools = remember {
        listOf(
            EditorTool.Heading,
            EditorTool.Heading2,
            EditorTool.Heading3,
            EditorTool.Heading4,
            EditorTool.Bold,
            EditorTool.Italic,
            EditorTool.Underline,
            EditorTool.TextColor,
            EditorTool.BulletList,
            EditorTool.NumberedList,
            EditorTool.Table,
            EditorTool.Link,
            EditorTool.Attachment,
            EditorTool.Image,
        )
    }
    val safeBodyValue = sanitizeVaultTextFieldValue(bodyValue)
    val selectedBodyText = remember(safeBodyValue.text, safeBodyValue.selection) {
        safeBodyValue.selectedTextOrNull()
    }
    val selectedTextChipText = selectedBodyText
    val activeTools = buildSet {
        addAll(activeVaultToolsForSelection(safeBodyValue, styleMarks, pendingInlineStyles))
        if (safeBodyValue.currentLineStartsWith("• ")) add(EditorTool.BulletList)
        if (safeBodyValue.currentLineMatches(Regex("^\\d+\\.\\s.*"))) add(EditorTool.NumberedList)
    }
    val saveStatusLabel = if (editorReady && (bodyValue.text != lastSavedText || styleMarks != lastSavedMarks || noteLinks != lastSavedLinks)) {
        "Saving..."
    } else {
        "Saved"
    }
    val mentionRange = remember(safeBodyValue.text, safeBodyValue.selection) { safeBodyValue.activeMentionRange() }
    val mentionQuery = mentionRange?.let { safeBodyValue.text.substring(it.start + 1, it.end) }.orEmpty()
    val mentionResults = remember(uiState.allNotes, mentionQuery) {
        if (mentionRange == null) {
            emptyList()
        } else {
            uiState.allNotes
                .filter { it.title.contains(mentionQuery, ignoreCase = true) }
                .take(6)
        }
    }
    val latestBodyValue by rememberUpdatedState(bodyValue)
    val latestStyleMarks by rememberUpdatedState(styleMarks)
    val latestNoteLinks by rememberUpdatedState(noteLinks)
    val latestLastSavedText by rememberUpdatedState(lastSavedText)
    val latestLastSavedMarks by rememberUpdatedState(lastSavedMarks)
    val latestLastSavedLinks by rememberUpdatedState(lastSavedLinks)
    val latestOnContentChange by rememberUpdatedState(onContentChange)

    fun currentHistorySnapshot(): EditorHistorySnapshot {
        val safeBody = sanitizeVaultTextFieldValue(bodyValue).withoutComposition()
        return EditorHistorySnapshot(
            title = sanitizeVaultTextFieldValue(title).withoutComposition(),
            body = safeBody,
            styleMarks = sanitizeVaultStyleMarks(styleMarks, safeBody.text.length),
            noteLinks = sanitizeVaultNoteLinks(noteLinks, safeBody.text.length),
            pendingInlineStyles = pendingInlineStyles,
        )
    }

    fun restoreHistorySnapshot(snapshot: EditorHistorySnapshot) {
        val safeBody = sanitizeVaultTextFieldValue(snapshot.body)
        restoringHistory = true
        title = sanitizeVaultTextFieldValue(snapshot.title)
        bodyValue = safeBody
        styleMarks = sanitizeVaultStyleMarks(snapshot.styleMarks, safeBody.text.length)
        noteLinks = sanitizeVaultNoteLinks(snapshot.noteLinks, safeBody.text.length)
        pendingInlineStyles = snapshot.pendingInlineStyles
        onTitleChange(title.text)
        editorScope.launch {
            delay(100)
            restoringHistory = false
        }
    }

    fun undoEditorChange() {
        val current = currentHistorySnapshot()
        val baseHistory = if (undoHistory.lastOrNull()?.hasSameEditorContentAs(current) == true) {
            undoHistory
        } else {
            (undoHistory + current).takeLast(EditorHistoryLimit)
        }
        if (baseHistory.size <= 1) return
        val currentEntry = baseHistory.last()
        val previous = baseHistory[baseHistory.lastIndex - 1]
        undoHistory = baseHistory.dropLast(1)
        redoHistory = (listOf(currentEntry) + redoHistory).take(EditorHistoryLimit)
        restoreHistorySnapshot(previous)
    }

    fun redoEditorChange() {
        val next = redoHistory.firstOrNull() ?: return
        val current = currentHistorySnapshot()
        val baseHistory = if (undoHistory.lastOrNull()?.hasSameEditorContentAs(current) == true) {
            undoHistory
        } else {
            (undoHistory + current).takeLast(EditorHistoryLimit)
        }
        undoHistory = (baseHistory + next).takeLast(EditorHistoryLimit)
        redoHistory = redoHistory.drop(1)
        restoreHistorySnapshot(next)
    }

    val liveHistorySnapshot = currentHistorySnapshot()
    val canUndo = undoHistory.size > 1 || undoHistory.lastOrNull()?.hasSameEditorContentAs(liveHistorySnapshot) == false
    val canRedo = redoHistory.isNotEmpty()

    LaunchedEffect(noteId, uiState.note?.title, uiState.richText) {
        if (noteId != null && loadedNoteId != noteId) {
            val noteTitle = uiState.note.title
            title = TextFieldValue(noteTitle, TextRange(noteTitle.length))
            bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(uiState.richText.text, TextRange.Zero))
            styleMarks = sanitizeVaultStyleMarks(uiState.richText.styleMarks, uiState.richText.text.length)
            noteLinks = sanitizeVaultNoteLinks(uiState.richText.noteLinks, uiState.richText.text.length)
            pendingInlineStyles = emptySet()
            loadedNoteId = noteId
            lastSavedText = uiState.richText.text
            lastSavedMarks = styleMarks
            lastSavedLinks = noteLinks
            undoHistory = listOf(
                EditorHistorySnapshot(
                    title = sanitizeVaultTextFieldValue(title).withoutComposition(),
                    body = sanitizeVaultTextFieldValue(bodyValue).withoutComposition(),
                    styleMarks = sanitizeVaultStyleMarks(styleMarks, bodyValue.text.length),
                    noteLinks = sanitizeVaultNoteLinks(noteLinks, bodyValue.text.length),
                    pendingInlineStyles = pendingInlineStyles,
                ),
            )
            redoHistory = emptyList()
            editorReady = true
        }
    }

    LaunchedEffect(noteId, editorReady, autoFocusBody) {
        if (noteId != null && editorReady && autoFocusBody) {
            delay(120)
            bodyFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(noteId) {
        snapshotFlow { currentHistorySnapshot() }
            .distinctUntilChanged { old, new -> old.hasSameEditorContentAs(new) }
            .debounce(650)
            .collect { snapshot ->
                if (editorReady && noteId != null && !restoringHistory) {
                    if (undoHistory.lastOrNull()?.hasSameEditorContentAs(snapshot) != true) {
                        undoHistory = (undoHistory + snapshot).takeLast(EditorHistoryLimit)
                        redoHistory = emptyList()
                    }
                }
            }
    }

    LaunchedEffect(noteId) {
        snapshotFlow { Triple(bodyValue.text, styleMarks, noteLinks) }
            .distinctUntilChanged()
            .debounce(350)
            .collect { (text, marks, links) ->
                if (editorReady && noteId != null) {
                    if (text != lastSavedText || marks != lastSavedMarks || links != lastSavedLinks) {
                        lastSavedText = text
                        lastSavedMarks = marks
                        lastSavedLinks = links
                        onContentChange(text, marks, links)
                    }
                }
            }
    }

    DisposableEffect(noteId, editorReady) {
        onDispose {
            if (
                editorReady &&
                noteId != null &&
                (latestBodyValue.text != latestLastSavedText || latestStyleMarks != latestLastSavedMarks || latestNoteLinks != latestLastSavedLinks)
            ) {
                latestOnContentChange(latestBodyValue.text, latestStyleMarks, latestNoteLinks)
            }
        }
    }

    fun flushPendingBodySave() {
        if (editorReady && noteId != null && (bodyValue.text != lastSavedText || styleMarks != lastSavedMarks || noteLinks != lastSavedLinks)) {
            lastSavedText = bodyValue.text
            lastSavedMarks = styleMarks
            lastSavedLinks = noteLinks
            onContentChange(bodyValue.text, styleMarks, noteLinks)
        }
    }

    fun updateBody(updatedValue: TextFieldValue) {
        val previousValue = sanitizeVaultTextFieldValue(bodyValue)
        val continuedValue = sanitizeVaultTextFieldValue(continueListOnNewline(previousValue, sanitizeVaultTextFieldValue(updatedValue)))
        styleMarks = sanitizeVaultStyleMarks(handleVaultRichTextChange(
            oldValue = previousValue,
            newValue = continuedValue,
            marks = styleMarks,
            pendingStyles = pendingInlineStyles,
        ), continuedValue.text.length)
        noteLinks = sanitizeVaultNoteLinks(handleVaultNoteLinkChange(previousValue, continuedValue, noteLinks), continuedValue.text.length)
        bodyValue = sanitizeVaultTextFieldValue(continuedValue)
    }

    fun applyBodyTransform(transformedValue: TextFieldValue) {
        val transform = VaultTextTransform(oldValueForMarks = sanitizeVaultTextFieldValue(bodyValue), value = sanitizeVaultTextFieldValue(transformedValue))
        styleMarks = sanitizeVaultStyleMarks(handleVaultRichTextChange(
            oldValue = transform.oldValueForMarks,
            newValue = transform.value,
            marks = styleMarks,
            pendingStyles = pendingInlineStyles,
        ), transform.value.text.length)
        noteLinks = sanitizeVaultNoteLinks(handleVaultNoteLinkChange(transform.oldValueForMarks, transform.value, noteLinks), transform.value.text.length)
        bodyValue = sanitizeVaultTextFieldValue(transform.value)
        pendingInlineStyles = emptySet()
    }

    fun applyRichTextTransform(transform: VaultTextTransform) {
        styleMarks = sanitizeVaultStyleMarks(handleVaultRichTextChange(
            oldValue = transform.oldValueForMarks,
            newValue = transform.value,
            marks = styleMarks,
            pendingStyles = pendingInlineStyles,
        ), transform.value.text.length)
        noteLinks = sanitizeVaultNoteLinks(handleVaultNoteLinkChange(transform.oldValueForMarks, transform.value, noteLinks), transform.value.text.length)
        bodyValue = sanitizeVaultTextFieldValue(transform.value)
        pendingInlineStyles = emptySet()
    }

    fun insertAiResultBelow(result: String, action: NoteAiAction?) {
        if (action.isEditorOutputMode()) {
            val imported = parseRichImport(html = result, plainText = null).document
            val separator = if (bodyValue.text.isBlank()) "" else "\n\n"
            val insertStart = bodyValue.text.length + separator.length
            val updatedText = bodyValue.text + separator + imported.text
            bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(updatedText, selection = TextRange(updatedText.length)))
            styleMarks = sanitizeVaultStyleMarks(
                styleMarks + imported.styleMarks.map { mark ->
                    mark.copy(start = mark.start + insertStart, end = mark.end + insertStart)
                },
                updatedText.length,
            )
            noteLinks = sanitizeVaultNoteLinks(noteLinks, updatedText.length)
            pendingInlineStyles = emptySet()
        } else {
            val separator = if (bodyValue.text.isBlank()) "" else "\n\n"
            val updatedText = bodyValue.text + separator + result.trim()
            applyBodyTransform(bodyValue.copy(text = updatedText, selection = TextRange(updatedText.length)))
        }
        bodyFocusRequester.requestFocus()
    }

    fun replaceBodyWithAiResult(result: String, action: NoteAiAction?) {
        val imported = if (action.isEditorOutputMode()) {
            parseRichImport(html = result, plainText = null).document
        } else {
            VaultRichTextDocument(text = result.trim(), styleMarks = emptyList(), noteLinks = emptyList())
        }
        bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(imported.text, selection = TextRange(imported.text.length)))
        styleMarks = sanitizeVaultStyleMarks(imported.styleMarks, imported.text.length)
        noteLinks = emptyList()
        pendingInlineStyles = emptySet()
        bodyFocusRequester.requestFocus()
    }

    fun runStructureOnlyLocally() {
        val current = sanitizeVaultTextFieldValue(bodyValue)
        val formattedHtml = current.text.toLocalStructureOnlyHtml()
        if (formattedHtml.isBlank()) {
            structureOnlyNotice = "Add note text first."
            return
        }
        val imported = parseRichImport(html = formattedHtml, plainText = null).document
        val cleanBody = sanitizeVaultTextFieldValue(TextFieldValue(imported.text, selection = TextRange(imported.text.length)))
        val cleanMarks = sanitizeVaultStyleMarks(imported.styleMarks, imported.text.length)
        val cleanLinks = sanitizeVaultNoteLinks(imported.noteLinks, imported.text.length)

        bodyValue = cleanBody
        styleMarks = cleanMarks
        noteLinks = cleanLinks
        pendingInlineStyles = emptySet()
        lastSavedText = cleanBody.text
        lastSavedMarks = cleanMarks
        lastSavedLinks = cleanLinks
        onContentChange(cleanBody.text, cleanMarks, cleanLinks)
        structureOnlyNotice = "Structure applied."
        bodyFocusRequester.requestFocus()
    }

    fun insertSelectedTextAiResultBelow(result: String, target: SelectedTextTarget?) {
        val trimmed = result.trim()
        if (trimmed.isBlank()) return
        val safeValue = sanitizeVaultTextFieldValue(bodyValue)
        val insertAt = (target?.end ?: safeValue.selection.end).coerceIn(0, safeValue.text.length)
        val prefix = if (insertAt > 0 && safeValue.text.getOrNull(insertAt - 1) != '\n') "\n\n" else "\n"
        val suffix = if (safeValue.text.getOrNull(insertAt) == '\n') "" else "\n"
        val insertion = prefix + trimmed + suffix
        val updatedText = safeValue.text.replaceRange(insertAt, insertAt, insertion)
        val cursor = insertAt + insertion.length
        applyBodyTransform(safeValue.copy(text = updatedText, selection = TextRange(cursor)))
        bodyFocusRequester.requestFocus()
    }

    fun insertSelectedTextAiResultAtCursor(result: String) {
        val trimmed = result.trim()
        if (trimmed.isBlank()) return
        val safeValue = sanitizeVaultTextFieldValue(bodyValue)
        val insertAt = safeValue.selection.end.coerceIn(0, safeValue.text.length)
        val updatedText = safeValue.text.replaceRange(insertAt, insertAt, trimmed)
        val cursor = insertAt + trimmed.length
        applyBodyTransform(safeValue.copy(text = updatedText, selection = TextRange(cursor)))
        bodyFocusRequester.requestFocus()
    }

    fun insertNoteLink(targetId: String, targetTitle: String) {
        val range = mentionRange ?: return
        val replacement = targetTitle
        val safeValue = sanitizeVaultTextFieldValue(bodyValue)
        val safeRange = TextRange(range.start.coerceIn(0, safeValue.text.length), range.end.coerceIn(0, safeValue.text.length))
        if (safeRange.start >= safeRange.end) return
        val newText = safeValue.text.replaceRange(safeRange.start, safeRange.end, replacement)
        val newSelection = TextRange(safeRange.start + replacement.length)
        val newValue = sanitizeVaultTextFieldValue(safeValue.copy(text = newText, selection = newSelection))
        styleMarks = sanitizeVaultStyleMarks(handleVaultRichTextChange(safeValue, newValue, styleMarks, pendingInlineStyles), newValue.text.length)
        noteLinks = sanitizeVaultNoteLinks(
            handleVaultNoteLinkChange(safeValue, newValue, noteLinks) +
                VaultNoteLink(start = safeRange.start, end = safeRange.start + replacement.length, noteId = targetId),
            newValue.text.length,
        )
        bodyValue = sanitizeVaultTextFieldValue(newValue)
        pendingInlineStyles = emptySet()
    }

    fun applyTool(tool: EditorTool) {
        when (tool) {
            EditorTool.Bold -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Bold)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.Italic -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Italic)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.Underline -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Underline)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.TextColor -> {
                colorToolbarOpen = !colorToolbarOpen
                return
            }
            EditorTool.Heading -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Heading)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.Heading2 -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Heading2)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.Heading3 -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Heading3)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.Heading4 -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Heading4)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.BulletList -> applyRichTextTransform(applyBulletListTransform(bodyValue))
            EditorTool.NumberedList -> applyRichTextTransform(applyNumberedListTransform(bodyValue))
            EditorTool.Table -> {
                tableDialogOpen = true
                return
            }
            EditorTool.Link -> {
                linkUrl = ""
                linkDialogOpen = true
            }
            EditorTool.Attachment -> attachmentPicker.launch(arrayOf("*/*"))
            EditorTool.Image -> imagePicker.launch(arrayOf("image/*"))
            EditorTool.Checklist,
            -> Unit
        }

        bodyFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (colorToolbarOpen) {
                    InlineTextColorToolbar(
                        activeStyles = pendingInlineStyles + activeStylesForToolbar(safeBodyValue, styleMarks),
                        onColorSelected = { selectedStyle ->
                            val safeValue = sanitizeVaultTextFieldValue(bodyValue)
                            val update = if (selectedStyle == null) {
                                clearVaultColorFromToolbar(safeValue, styleMarks, pendingInlineStyles)
                            } else {
                                applyVaultStyleFromToolbar(safeValue, styleMarks, pendingInlineStyles, selectedStyle)
                            }
                            styleMarks = sanitizeVaultStyleMarks(update.marks, safeValue.text.length)
                            pendingInlineStyles = update.pendingStyles
                            bodyFocusRequester.requestFocus()
                        },
                    )
                }
                EditorToolbar(
                    tools = supportedTools,
                    activeTools = activeTools,
                    onToolClick = ::applyTool,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTopBar(
                    onBackClick = {
                        flushPendingBodySave()
                        onBackClick()
                    },
                ) {
                    EditorTopActionButton(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        enabled = canUndo,
                        onClick = ::undoEditorChange,
                    )
                    EditorTopActionButton(
                        icon = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo",
                        enabled = canRedo,
                        onClick = ::redoEditorChange,
                    )
                    IconBtn(
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = "Intelligent Structure",
                        onClick = { intelligentStructureOpen = true },
                    )
                    IconBtn(
                        icon = Icons.Rounded.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        active = isPinned,
                        onClick = { onPinnedChange(!isPinned) },
                    )
                    IconBtn(
                        icon = Icons.Rounded.Star,
                        contentDescription = if (isFavourite) "Unfavourite" else "Favourite",
                        active = isFavourite,
                        onClick = { onFavouriteChange(!isFavourite) },
                    )
                    IconBtn(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        onClick = { moreMenuOpen = true },
                    )
                }

                EditorBreadcrumb(
                    items = breadcrumbItems,
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                )

                Spacer(modifier = Modifier.height(VaultSpacing.md))

                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onTitleChange(it.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.screen),
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { innerTextField ->
                        if (title.text.isBlank()) {
                            Text("Title", style = MaterialTheme.typography.titleLarge, color = colors.textMuted)
                        }
                        innerTextField()
                    },
                )

                Text(
                    text = saveStatusLabel,
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = if (hasTables) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(tableEditorScrollState)
                            .padding(horizontal = VaultSpacing.screen, vertical = 2.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = VaultSpacing.screen, vertical = 2.dp)
                    },
                ) {
                    Box(
                        modifier = if (hasTables) {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 220.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        },
                    ) {
                        BasicTextField(
                            value = safeBodyValue,
                            onValueChange = ::updateBody,
                            modifier = if (hasTables) {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp, max = 220.dp)
                                    .focusRequester(bodyFocusRequester)
                                    .onFocusChanged { bodyFocused = it.isFocused }
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(bodyFocusRequester)
                                    .onFocusChanged { bodyFocused = it.isFocused }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text, fontSize = bodyFontSizeSp.sp),
                            cursorBrush = SolidColor(colors.accent),
                            visualTransformation = remember(styleMarks, noteLinks, colors) {
                                VaultRichTextVisualTransformation(styleMarks, noteLinks, colors)
                            },
                            maxLines = Int.MAX_VALUE,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 10.dp),
                                ) {
                                    if (bodyValue.text.isBlank()) {
                                        Text(
                                            "Start writing...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.textMuted,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    selectedTextChipText?.let { selectedText ->
                        Spacer(modifier = Modifier.height(VaultSpacing.xs))
                        Surface(
                            onClick = {
                                val currentStart = minOf(safeBodyValue.selection.start, safeBodyValue.selection.end)
                                    .coerceIn(0, safeBodyValue.text.length)
                                val currentEnd = maxOf(safeBodyValue.selection.start, safeBodyValue.selection.end)
                                    .coerceIn(0, safeBodyValue.text.length)
                                val previousTarget = selectedTextTarget
                                val start = if (currentStart < currentEnd) currentStart else previousTarget?.start ?: currentStart
                                val end = if (currentStart < currentEnd) currentEnd else previousTarget?.end ?: currentEnd
                                selectedTextTarget = SelectedTextTarget(
                                    text = selectedText,
                                    start = start,
                                    end = end,
                                )
                                onClearSelectedTextAi()
                                onAiQuestionChange(AiPromptBuilder.buildSuggestionPrefill(AiSuggestion.Explain, selectedTextMode = true))
                                onAskAiClick(selectedText)
                            },
                            color = colors.accentSoft,
                            shape = VaultShapes.pill,
                            border = BorderStroke(1.dp, colors.accentBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.accent,
                                )
                                Text(
                                    text = "Ask AI about selection",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                                    color = colors.accent,
                                )
                            }
                        }
                    }

                    if (mentionResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(VaultSpacing.xs))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.elevated,
                            shape = VaultShapes.md,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                mentionResults.forEach { note ->
                                    Surface(
                                        onClick = { insertNoteLink(note.id, note.title) },
                                        color = Color.Transparent,
                                    ) {
                                        Text(
                                            text = note.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                                            color = colors.text,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.tables.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(VaultSpacing.sm))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 420.dp)
                                .padding(bottom = VaultSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        ) {
                            uiState.tables.forEach { table ->
                                key(table.id) {
                                    EditableNoteTableBlock(
                                        table = table,
                                        onCellChange = onUpdateTableCell,
                                        onDelete = { tableDeleteRequest = table.id },
                                    )
                                }
                            }
                        }
                    }

                    if (!bodyFocused && uiState.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(VaultSpacing.md))
                        EditorAttachmentPreviewSection(attachments = uiState.attachments, onAttachmentClick = onAttachmentClick)
                    }
                }
            }

            if (!bodyFocused) {
                Surface(
                    onClick = {
                        selectedTextTarget = null
                        onAskAiClick(null)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = VaultSpacing.screen, bottom = VaultSpacing.sm),
                    color = colors.accent,
                    contentColor = Color.White,
                    shape = VaultShapes.pill,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (linkDialogOpen) {
        AlertDialog(
            onDismissRequest = { linkDialogOpen = false },
            title = { Text("Add link") },
            text = {
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    singleLine = true,
                    label = { Text("URL") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (linkUrl.isNotBlank()) {
                            applyBodyTransform(bodyValue.insertText(linkUrl))
                        }
                        linkDialogOpen = false
                        bodyFocusRequester.requestFocus()
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { linkDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (tableDialogOpen) {
        TableSizeDialog(
            onDismiss = { tableDialogOpen = false },
            onSizeSelected = { rows, columns ->
                onCreateTable(rows, columns)
                tableDialogOpen = false
            },
        )
    }

    tableDeleteRequest?.let { tableId ->
        AlertDialog(
            onDismissRequest = { tableDeleteRequest = null },
            title = { Text("Delete table?") },
            text = { Text("This table will be removed from the note.") },
            confirmButton = {
                Button(
                    onClick = {
                        tableDeleteRequest = null
                        onDeleteTable(tableId)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tableDeleteRequest = null }) {
                    Text("Keep")
                }
            },
        )
    }

    if (moreMenuOpen) {
        AlertDialog(
            onDismissRequest = { moreMenuOpen = false },
            confirmButton = {},
            title = {
                Text(
                    text = uiState.note?.title?.takeIf { it.isNotBlank() } ?: "Note actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    EditorActionRow("Attach file", Icons.Rounded.AttachFile) {
                        moreMenuOpen = false
                        attachmentPicker.launch(arrayOf("*/*"))
                    }
                    EditorActionRow(if (isPinned) "Unpin note" else "Pin note", Icons.Rounded.PushPin) {
                        onPinnedChange(!isPinned)
                        moreMenuOpen = false
                    }
                    EditorActionRow(if (isFavourite) "Remove favourite" else "Add favourite", Icons.Rounded.Star) {
                        onFavouriteChange(!isFavourite)
                        moreMenuOpen = false
                    }
                    EditorActionRow("Export as TXT", Icons.Rounded.AttachFile) {
                        moreMenuOpen = false
                        exportTextLauncher.launch("${uiState.note?.title?.toSafeFileName() ?: "note"}.txt")
                    }
                    EditorActionRow("Export as PDF", Icons.Rounded.AttachFile) {
                        moreMenuOpen = false
                        exportPdfLauncher.launch("${uiState.note?.title?.toSafeFileName() ?: "note"}.pdf")
                    }
                    EditorActionRow("Delete note", Icons.Rounded.Delete, destructive = true) {
                        moreMenuOpen = false
                        deleteDialogOpen = true
                    }
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (intelligentStructureOpen) {
        IntelligentStructureSheet(
            aiState = aiState,
            onProviderSelected = onAiProviderSelected,
            onModelSelected = onAiModelSelected,
            onDismiss = {
                intelligentStructureOpen = false
                structureOnlyNotice = null
            },
            structureOnlyNotice = structureOnlyNotice,
            onRun = { action ->
                structureOnlyNotice = null
                val request = when (action) {
                    NoteAiAction.StructureOnly -> "Structure this note very carefully. Preserve the exact wording. Do not add, remove, paraphrase, summarise, or rewrite any words. Only organise the existing content with headings, subheadings, paragraphs, lists, bold, emphasis, and blockquotes where appropriate."
                    NoteAiAction.IntelligentStructure -> "Intelligently structure this note."
                    else -> action.displayName
                }
                onRunAiTool(action, aiState.provider, aiState.model, title.text, bodyValue.text, request)
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(aiState.result))
            },
            onInsertBelow = {
                insertAiResultBelow(aiState.result, aiState.action)
                intelligentStructureOpen = false
                onClearAiResult()
            },
            onReplace = { replaceAiDialogOpen = true },
            onClearResult = onClearAiResult,
        )
    }

    if (replaceAiDialogOpen) {
        AlertDialog(
            onDismissRequest = { replaceAiDialogOpen = false },
            title = { Text("Replace note body?") },
            text = { Text("This will replace the current body text with the AI result. Your title will stay the same.") },
            confirmButton = {
                Button(
                    onClick = {
                        replaceAiDialogOpen = false
                        replaceBodyWithAiResult(aiState.result, aiState.action)
                        intelligentStructureOpen = false
                        onClearAiResult()
                    },
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { replaceAiDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Move note to Recently Deleted?") },
            text = { Text("You can restore this note from Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteDialogOpen = false
                        onDeleteNote()
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }
}

private const val EditorHistoryLimit = 80

@Composable
private fun InlineTextColorToolbar(
    activeStyles: Set<VaultInlineStyle>,
    onColorSelected: (VaultInlineStyle?) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val options = remember {
        listOf(
            TextColorOption("Red", Color(0xFFE5484D), VaultInlineStyle.ColorRed),
            TextColorOption("Orange", Color(0xFFF97316), VaultInlineStyle.ColorOrange),
            TextColorOption("Green", Color(0xFF2F9E66), VaultInlineStyle.ColorGreen),
            TextColorOption("Blue", Color(0xFF2F80ED), VaultInlineStyle.ColorBlue),
            TextColorOption("Purple", Color(0xFF8B5CF6), VaultInlineStyle.ColorPurple),
            TextColorOption("Pink", Color(0xFFDB2777), VaultInlineStyle.ColorPink),
            TextColorOption("Slate", Color(0xFF64748B), VaultInlineStyle.ColorSlate),
            TextColorOption("Default", colors.text, null),
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.elevated,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Colour",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            options.forEach { option ->
                val active = option.style != null && option.style in activeStyles
                Surface(
                    onClick = { onColorSelected(option.style) },
                    modifier = Modifier.size(32.dp),
                    color = if (active) colors.accentSoft else colors.surface,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(if (active) 22.dp else 19.dp),
                            color = option.color,
                            shape = VaultShapes.pill,
                            border = BorderStroke(1.dp, colors.border),
                            content = {},
                        )
                    }
                }
            }
        }
    }
}

private fun activeStylesForToolbar(
    value: TextFieldValue,
    marks: List<VaultStyleMark>,
): Set<VaultInlineStyle> {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val cursor = safeValue.selection.start.coerceIn(0, safeValue.text.length)
    return sanitizeVaultStyleMarks(marks, safeValue.text.length)
        .filter { cursor >= it.start && cursor < it.end }
        .mapTo(linkedSetOf()) { it.style }
}

private data class EditorHistorySnapshot(
    val title: TextFieldValue,
    val body: TextFieldValue,
    val styleMarks: List<VaultStyleMark>,
    val noteLinks: List<VaultNoteLink>,
    val pendingInlineStyles: Set<VaultInlineStyle>,
)

private fun TextFieldValue.withoutComposition(): TextFieldValue = copy(composition = null)

private fun EditorHistorySnapshot.hasSameEditorContentAs(other: EditorHistorySnapshot): Boolean =
    title.text == other.title.text &&
        body.text == other.body.text &&
        styleMarks == other.styleMarks &&
        noteLinks == other.noteLinks &&
        pendingInlineStyles == other.pendingInlineStyles

@Composable
private fun EditorTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
        color = colors.surface,
        contentColor = if (enabled) colors.textSecondary else colors.textMuted,
        border = BorderStroke(1.dp, if (enabled) colors.border else colors.border.copy(alpha = 0.55f)),
        shape = VaultShapes.sm,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EditorBreadcrumb(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xxs),
    ) {
        items.forEachIndexed { index, item ->
            val isCurrentNote = index == items.lastIndex
            Text(
                text = item,
                modifier = Modifier.widthIn(max = if (isCurrentNote) 96.dp else 116.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrentNote) colors.textSecondary else colors.textMuted,
            )
            if (!isCurrentNote) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textMuted,
                )
            }
        }
    }
}

private fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun IntelligentStructureSheet(
    aiState: NoteAiUiState,
    onProviderSelected: (NoteAiProvider) -> Unit,
    onModelSelected: (NoteAiModel) -> Unit,
    onDismiss: () -> Unit,
    structureOnlyNotice: String? = null,
    onRun: (NoteAiAction) -> Unit,
    onCopy: () -> Unit,
    onInsertBelow: () -> Unit,
    onReplace: () -> Unit,
    onClearResult: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editorOutputReady = aiState.result.isNotBlank() && aiState.action.isEditorOutputMode()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevated,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp),
                color = colors.borderStrong,
                shape = VaultShapes.pill,
                content = {},
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = VaultSpacing.screen)
                .padding(bottom = VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        color = colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.accentBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(19.dp), tint = colors.accent)
                        }
                    }
                    Column {
                        Text(
                            text = "Intelligent Structure",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = "Restructure, format, headings, colour coding",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteAiProvider.entries.forEach { provider ->
                    AiActionChip(
                        label = provider.displayName,
                        active = aiState.provider == provider,
                        enabled = !aiState.loading,
                        onClick = { onProviderSelected(provider) },
                    )
                }
                NoteAiModel.entries.forEach { model ->
                    AiActionChip(
                        label = model.chipLabel(aiState.provider),
                        active = aiState.model == model,
                        enabled = !aiState.loading,
                        onClick = { onModelSelected(model) },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    Text(
                        text = "Choose Structure Only when you want AI to organise the note strongly without changing the wording. Choose Intelligent Structure when you want stronger restructuring.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    if (structureOnlyNotice != null) {
                        Text(
                            text = structureOnlyNotice,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                            color = colors.accent,
                        )
                    }
                    Button(
                        onClick = { onRun(NoteAiAction.StructureOnly) },
                        enabled = !aiState.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (aiState.loading && aiState.action == NoteAiAction.StructureOnly) aiState.progressLabel ?: "Structuring..." else "Run Structure Only")
                    }
                    OutlinedButton(
                        onClick = { onRun(NoteAiAction.IntelligentStructure) },
                        enabled = !aiState.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (aiState.loading && aiState.action == NoteAiAction.IntelligentStructure) aiState.progressLabel ?: "Structuring..." else "Run Intelligent Structure")
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    when {
                        aiState.loading -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(aiState.progressLabel ?: "Structuring note...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                        editorOutputReady -> {
                            AiEditorOutputPreview(
                                action = aiState.action,
                                result = aiState.result,
                                onCopy = onCopy,
                                onInsertBelow = onInsertBelow,
                                onReplace = onReplace,
                                onDismiss = onClearResult,
                            )
                        }
                        aiState.error != null -> {
                            Text(
                                text = aiState.error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                                color = colors.warning,
                            )
                        }
                        else -> {
                            Text(
                                text = "Run Structure Only to preserve the note's exact wording, or Intelligent Structure for stronger AI restructuring.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AskAiSheet(
    aiState: NoteAiUiState,
    selectedText: String? = null,
    onProviderSelected: (NoteAiProvider) -> Unit,
    onModelSelected: (NoteAiModel) -> Unit,
    onQuestionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit,
    onClearConversation: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val conversationScrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(aiState.messages.size, aiState.loading, aiState.error, aiState.action) {
        conversationScrollState.animateScrollTo(conversationScrollState.maxValue)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevated,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp),
                color = colors.borderStrong,
                shape = VaultShapes.pill,
                content = {},
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = VaultSpacing.screen)
                .padding(bottom = VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        color = colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.accentBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = colors.accent,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = if (selectedText.isNullOrBlank()) {
                                "Current note only · ${aiState.provider.displayName} · ${aiState.model.shortLabel(aiState.provider)}"
                            } else {
                                "Selected text focus · ${aiState.provider.displayName} · ${aiState.model.shortLabel(aiState.provider)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteAiProvider.entries.forEach { provider ->
                    AiActionChip(
                        label = provider.displayName,
                        active = aiState.provider == provider,
                        enabled = !aiState.loading,
                        onClick = { onProviderSelected(provider) },
                    )
                }
                NoteAiModel.entries.forEach { model ->
                    AiActionChip(
                        label = model.chipLabel(aiState.provider),
                        active = aiState.model == model,
                        enabled = !aiState.loading,
                        onClick = { onModelSelected(model) },
                    )
                }
                TextButton(onClick = onClearConversation, enabled = !aiState.loading && aiState.messages.isNotEmpty()) {
                    Text("Clear")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.elevated,
                            shape = VaultShapes.md,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 84.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }
                    AiSuggestionGrid(
                        enabled = !aiState.loading,
                        selectedTextMode = !selectedText.isNullOrBlank(),
                        onSuggestionClick = { suggestion ->
                            onQuestionChange(AiPromptBuilder.buildSuggestionPrefill(suggestion, selectedTextMode = !selectedText.isNullOrBlank()))
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(conversationScrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    if (aiState.messages.isEmpty() && !aiState.loading) {
                        Text(
                            text = "Ask naturally about this note. Suggestions draft prompts only; you stay in control before sending.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                    aiState.messages.forEach { message ->
                        AiChatBubble(message = message)
                    }
                    if (aiState.loading) {
                        Surface(
                            color = colors.elevated,
                            shape = VaultShapes.md,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(aiState.progressLabel ?: "Thinking...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            aiState.error?.takeIf { error ->
                aiState.messages.lastOrNull()?.content != error
            }?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.warningSoft,
                    shape = VaultShapes.md,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                        color = colors.warning,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = aiState.question,
                        onValueChange = onQuestionChange,
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 3,
                        placeholder = { Text("Ask My AI about this note...") },
                        enabled = !aiState.loading,
                    )
                    Button(
                        onClick = onRun,
                        enabled = !aiState.loading && aiState.question.isNotBlank(),
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SelectedTextAiSheet(
    selectedText: String,
    state: SelectedTextAiUiState,
    provider: NoteAiProvider,
    model: NoteAiModel,
    onDismiss: () -> Unit,
    onRun: (SelectedTextAiAction) -> Unit,
    onQuestionChange: (String, SelectedTextAiAction?) -> Unit,
    onCopy: () -> Unit,
    onInsertBelowSelection: () -> Unit,
    onInsertAtCursor: () -> Unit,
    onSendToChat: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resultScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevated,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp),
                color = colors.borderStrong,
                shape = VaultShapes.pill,
                content = {},
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = VaultSpacing.screen)
                .padding(bottom = VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        color = colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.accentBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                        }
                    }
                    Column {
                        Text(
                            text = "Ask AI about selection",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = "${provider.displayName} · ${model.shortLabel(provider)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Selected text",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                        color = colors.textMuted,
                    )
                    Text(
                        text = selectedText.ifBlank { "No text selected." },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = colors.text,
                    )
                }
            }

            AiSuggestionGrid(
                enabled = !state.loading && selectedText.isNotBlank(),
                selectedTextMode = true,
                onSuggestionClick = { suggestion ->
                    onQuestionChange(
                        AiPromptBuilder.buildSuggestionPrefill(suggestion, selectedTextMode = true),
                        suggestion.toSelectedTextAction(),
                    )
                },
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    when {
                        state.loading -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Thinking about the selected text...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        state.error != null -> {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                                color = colors.warning,
                            )
                        }
                        state.result.isNotBlank() -> {
                            Text(
                                text = state.action?.displayName ?: "Result",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                                color = colors.textMuted,
                            )
                            Text(
                                text = state.result,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(resultScroll),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = colors.text,
                            )
                        }
                        else -> {
                            Text(
                                text = "Choose a focused action for the highlighted text. This stays separate from the main My AI chat unless you send it there.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }

            if (state.result.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                ) {
                    TextButton(onClick = onCopy, enabled = !state.loading) { Text("Copy") }
                    TextButton(onClick = onInsertBelowSelection, enabled = !state.loading) { Text("Insert below selection") }
                    TextButton(onClick = onInsertAtCursor, enabled = !state.loading) { Text("Insert at cursor") }
                    TextButton(onClick = onSendToChat, enabled = !state.loading) { Text("Send to chat") }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.question,
                        onValueChange = { onQuestionChange(it, state.action) },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 3,
                        placeholder = { Text("Ask about the selected text...") },
                        enabled = !state.loading,
                    )
                    Button(
                        onClick = { onRun(state.action ?: SelectedTextAiAction.Ask) },
                        enabled = !state.loading && selectedText.isNotBlank() && state.question.isNotBlank(),
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiEditorOutputPreview(
    action: NoteAiAction?,
    result: String,
    onCopy: () -> Unit,
    onInsertBelow: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val previewDocument = remember(action, result) {
        parseRichImport(html = result, plainText = null).document
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.elevated,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.borderStrong),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    color = colors.accentSoft,
                    shape = VaultShapes.sm,
                    border = BorderStroke(1.dp, colors.accentBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action?.displayName ?: "Structured preview",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                    )
                    Text(
                        text = "Preview before applying to your note",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp),
                color = colors.surface,
                shape = VaultShapes.md,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = buildVaultAnnotatedString(
                        text = previewDocument.text,
                        marks = previewDocument.styleMarks,
                        colors = colors,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = colors.text,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                TextButton(onClick = onReplace) { Text("Replace note") }
                TextButton(onClick = onInsertBelow) { Text("Insert below") }
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun AiChatBubble(message: NoteAiChatMessage) {
    val colors = VaultThemeTokens.colors
    val isUser = message.role == NoteAiMessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 520.dp),
            color = when (message.role) {
                NoteAiMessageRole.User -> colors.accentSoft
                NoteAiMessageRole.Assistant -> colors.elevated
                NoteAiMessageRole.Error -> colors.warningSoft
            },
            shape = VaultShapes.lg,
            border = BorderStroke(
                1.dp,
                when (message.role) {
                    NoteAiMessageRole.User -> colors.accentBorder
                    NoteAiMessageRole.Assistant -> colors.border
                    NoteAiMessageRole.Error -> colors.border
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when (message.role) {
                        NoteAiMessageRole.User -> "You"
                        NoteAiMessageRole.Assistant -> message.action?.displayName ?: "My AI"
                        NoteAiMessageRole.Error -> "Error"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                    color = if (message.role == NoteAiMessageRole.Error) colors.warning else colors.textMuted,
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = if (message.role == NoteAiMessageRole.Error) colors.warning else colors.text,
                )
            }
        }
    }
}

@Composable
private fun AiActionChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(44.dp),
        color = if (active) colors.accentSoft else colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                color = if (active) colors.accent else colors.text,
            )
        }
    }
}

@Composable
private fun AiSuggestionGrid(
    enabled: Boolean,
    selectedTextMode: Boolean,
    onSuggestionClick: (AiSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AiSuggestion.entries.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowActions.forEach { suggestion ->
                    SelectedTextActionButton(
                        label = suggestion.displayName,
                        active = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSuggestionClick(suggestion) },
                    )
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        if (!selectedTextMode) {
            Text(
                text = "Tap a suggestion to draft a prompt, then edit it and press Send.",
                style = MaterialTheme.typography.labelSmall,
                color = VaultThemeTokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SelectedTextActionButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        color = if (active) colors.accentSoft else colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                color = if (active) colors.accent else colors.textSecondary,
            )
        }
    }
}

private fun AiSuggestion.toSelectedTextAction(): SelectedTextAiAction =
    when (this) {
        AiSuggestion.Explain -> SelectedTextAiAction.Explain
        AiSuggestion.Simplify -> SelectedTextAiAction.Simplify
        AiSuggestion.Terminology -> SelectedTextAiAction.Terminology
        AiSuggestion.Compare -> SelectedTextAiAction.ComparePositions
        AiSuggestion.RelatedConcepts -> SelectedTextAiAction.RelatedConcepts
        AiSuggestion.ObjectionResponse -> SelectedTextAiAction.ObjectionResponse
        AiSuggestion.StudyQuestions -> SelectedTextAiAction.StudyQuestions
    }

private fun NoteAiModel.shortLabel(provider: NoteAiProvider): String =
    when (this) {
        NoteAiModel.Gemini25Flash -> if (provider == NoteAiProvider.ChatGPT) "GPT Mini" else "Gemini 2.5"
        NoteAiModel.Gemini3Pro -> if (provider == NoteAiProvider.ChatGPT) "GPT Full" else "Gemini 3.1 Pro"
    }

private fun NoteAiModel.chipLabel(provider: NoteAiProvider): String =
    when (this) {
        NoteAiModel.Gemini25Flash -> if (provider == NoteAiProvider.ChatGPT) "GPT Mini · Fast" else "Gemini 2.5 · Fast"
        NoteAiModel.Gemini3Pro -> if (provider == NoteAiProvider.ChatGPT) "GPT Full · Best overall" else "Gemini 3.1 Pro · Deep"
    }

private data class SelectedTextTarget(
    val text: String,
    val start: Int,
    val end: Int,
)

private fun TextFieldValue.selectedTextOrNull(): String? {
    val safeValue = sanitizeVaultTextFieldValue(this)
    if (safeValue.selection.collapsed) return null
    val start = minOf(safeValue.selection.start, safeValue.selection.end).coerceIn(0, safeValue.text.length)
    val end = maxOf(safeValue.selection.start, safeValue.selection.end).coerceIn(0, safeValue.text.length)
    if (start >= end) return null
    return safeValue.text.substring(start, end).takeIf { it.isNotBlank() }
}


private fun String.toLocalStructureOnlyHtml(): String {
    val clean = trim()
    if (clean.isBlank()) return ""

    val existingHtml = Regex("""</?(p|h1|h2|h3|ul|ol|li|blockquote)\b""", RegexOption.IGNORE_CASE)
    if (existingHtml.containsMatchIn(clean)) return clean

    val paragraphs = clean
        .lines()
        .map { it.trim() }

    val output = StringBuilder()
    var inUnorderedList = false
    var inOrderedList = false

    fun closeLists() {
        if (inUnorderedList) {
            output.append("</ul>\n")
            inUnorderedList = false
        }
        if (inOrderedList) {
            output.append("</ol>\n")
            inOrderedList = false
        }
    }

    fun looksLikeHeading(line: String): Boolean {
        if (line.length > 90) return false
        if (line.endsWith(".") || line.endsWith(",") || line.endsWith("،") || line.endsWith(";") || line.endsWith("؛")) return false
        if (line.startsWith("-") || line.startsWith("•") || line.startsWith("*")) return false
        return true
    }

    paragraphs.forEach { line ->
        if (line.isBlank()) {
            closeLists()
            return@forEach
        }

        val bullet = Regex("""^[-•*]\s+(.+)$""").matchEntire(line)
        val numbered = Regex("""^\d+[.)]\s+(.+)$""").matchEntire(line)

        when {
            bullet != null -> {
                if (inOrderedList) {
                    output.append("</ol>\n")
                    inOrderedList = false
                }
                if (!inUnorderedList) {
                    output.append("<ul>\n")
                    inUnorderedList = true
                }
                output.append("<li>").append(bullet.groupValues[1].escapeVaultHtml()).append("</li>\n")
            }
            numbered != null -> {
                if (inUnorderedList) {
                    output.append("</ul>\n")
                    inUnorderedList = false
                }
                if (!inOrderedList) {
                    output.append("<ol>\n")
                    inOrderedList = true
                }
                output.append("<li>").append(numbered.groupValues[1].escapeVaultHtml()).append("</li>\n")
            }
            looksLikeHeading(line) -> {
                closeLists()
                output.append("<h2>").append(line.escapeVaultHtml()).append("</h2>\n")
            }
            else -> {
                closeLists()
                output.append("<p>").append(line.escapeVaultHtml()).append("</p>\n")
            }
        }
    }

    closeLists()
    return output.toString().trim()
}

private fun String.escapeVaultHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun TextFieldValue.insertText(textToInsert: String): TextFieldValue {
    val safeValue = sanitizeVaultTextFieldValue(this)
    val start = minOf(safeValue.selection.start, safeValue.selection.end).coerceIn(0, safeValue.text.length)
    val end = maxOf(safeValue.selection.start, safeValue.selection.end).coerceIn(0, safeValue.text.length)
    val newText = safeValue.text.replaceRange(start, end, textToInsert)
    val cursor = start + textToInsert.length
    return sanitizeVaultTextFieldValue(safeValue.copy(text = newText, selection = TextRange(cursor, cursor)))
}

private fun NoteAiAction?.isEditorOutputMode(): Boolean =
    this == NoteAiAction.StructureOnly || this == NoteAiAction.IntelligentStructure || this == NoteAiAction.CleanFormat || this == NoteAiAction.FormatNote

private fun TextFieldValue.currentLineStartsWith(prefix: String): Boolean {
    val line = currentLine()
    return line.startsWith(prefix)
}

private fun TextFieldValue.currentLineMatches(regex: Regex): Boolean = regex.matches(currentLine())

internal fun TextFieldValue.currentLine(): String {
    val safeValue = sanitizeVaultTextFieldValue(this)
    if (safeValue.text.isEmpty()) return ""
    val cursor = safeValue.selection.start.coerceIn(0, safeValue.text.length)
    val start = if (cursor == 0) {
        0
    } else {
        safeValue.text.lastIndexOf('\n', startIndex = cursor - 1).let { if (it == -1) 0 else it + 1 }
    }
    val end = safeValue.text.indexOf('\n', startIndex = cursor).let { if (it == -1) safeValue.text.length else it }
    return if (start <= end) safeValue.text.substring(start, end) else ""
}

@Composable
private fun TextColorDialog(
    onDismiss: () -> Unit,
    onColorSelected: (VaultInlineStyle?) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val options = remember {
        listOf(
            TextColorOption("Red", Color(0xFFE5484D), VaultInlineStyle.ColorRed),
            TextColorOption("Orange", Color(0xFFF97316), VaultInlineStyle.ColorOrange),
            TextColorOption("Green", Color(0xFF2F9E66), VaultInlineStyle.ColorGreen),
            TextColorOption("Blue", Color(0xFF2F80ED), VaultInlineStyle.ColorBlue),
            TextColorOption("Purple", Color(0xFF8B5CF6), VaultInlineStyle.ColorPurple),
            TextColorOption("Pink", Color(0xFFDB2777), VaultInlineStyle.ColorPink),
            TextColorOption("Slate", Color(0xFF64748B), VaultInlineStyle.ColorSlate),
            TextColorOption("Default", colors.text, null),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Text colour",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                color = colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                options.chunked(4).forEach { rowOptions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowOptions.forEach { option ->
                            Surface(
                                onClick = { onColorSelected(option.style) },
                                modifier = Modifier.size(46.dp),
                                color = colors.surface,
                                shape = VaultShapes.md,
                                border = BorderStroke(1.dp, colors.border),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        color = option.color,
                                        shape = VaultShapes.pill,
                                        border = BorderStroke(1.dp, colors.border),
                                        content = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = colors.elevated,
        tonalElevation = 0.dp,
    )
}

private data class TextColorOption(
    val label: String,
    val color: Color,
    val style: VaultInlineStyle?,
)

@Composable
private fun EditableNoteTableBlock(
    table: NoteTableUiState,
    onCellChange: (tableId: String, row: Int, column: Int, text: String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Table",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
                IconBtn(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete table",
                    onClick = onDelete,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(table.rowCount) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(table.columnCount) { column ->
                            key(table.id, row, column) {
                                val value = table.cells.getOrNull(row)?.getOrNull(column).orEmpty()
                                Surface(
                                    modifier = Modifier.width(116.dp),
                                    color = colors.elevated,
                                    shape = VaultShapes.sm,
                                    border = BorderStroke(1.dp, colors.border),
                                ) {
                                    TableCellField(
                                        value = value,
                                        onValueChange = { onCellChange(table.id, row, column, it) },
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

@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun TableCellField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(value) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    val latestFieldValue by rememberUpdatedState(fieldValue)
    val latestLastSaved by rememberUpdatedState(lastSaved)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(value, focused) {
        if (!focused && value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
            lastSaved = value
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { fieldValue.text }
            .distinctUntilChanged()
            .debounce(350)
            .collect { text ->
                if (text != lastSaved) {
                    lastSaved = text
                    onValueChange(text)
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (latestFieldValue.text != latestLastSaved) {
                latestOnValueChange(latestFieldValue.text)
            }
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { fieldValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (focused && !nowFocused && fieldValue.text != lastSaved) {
                    lastSaved = fieldValue.text
                    onValueChange(fieldValue.text)
                }
                if (!focused && nowFocused) {
                    scope.launch {
                        delay(250)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
                focused = nowFocused
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { innerTextField ->
            if (fieldValue.text.isBlank()) {
                Text(
                    text = "Cell",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            innerTextField()
        },
    )
}

@Composable
private fun TableSizeDialog(
    onDismiss: () -> Unit,
    onSizeSelected: (rows: Int, columns: Int) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var customRows by remember { mutableStateOf("") }
    var customColumns by remember { mutableStateOf("") }
    val parsedRows = customRows.toIntOrNull()?.coerceIn(1, 10)
    val parsedColumns = customColumns.toIntOrNull()?.coerceIn(1, 10)
    val canCreateCustom = parsedRows != null && parsedColumns != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Insert table",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                color = colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                listOf(2 to 2, 2 to 3, 3 to 3, 3 to 4, 4 to 4).forEach { (rows, columns) ->
                    Surface(
                        onClick = { onSizeSelected(rows, columns) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$rows x $columns table",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                                color = colors.text,
                            )
                            Text(
                                text = "${rows * columns} cells",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(VaultSpacing.xs))
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = customRows,
                        onValueChange = { customRows = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Rows") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("x", style = MaterialTheme.typography.titleMedium, color = colors.textMuted)
                    OutlinedTextField(
                        value = customColumns,
                        onValueChange = { customColumns = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Columns") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Button(
                    onClick = {
                        if (parsedRows != null && parsedColumns != null) {
                            onSizeSelected(parsedRows, parsedColumns)
                        }
                    },
                    enabled = canCreateCustom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create custom table")
                }
                Text(
                    text = "Maximum 10 x 10",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        },
        confirmButton = {},
        containerColor = colors.elevated,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun EditorActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = colors.accentSoft,
                shape = VaultShapes.sm,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (destructive) colors.warning else colors.accent,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = if (destructive) colors.warning else colors.text,
            )
        }
    }
}

@Composable
private fun EditorAttachmentPreviewSection(
    attachments: List<AttachmentEntity>,
    onAttachmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
        Text(
            text = "Attachments",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = VaultThemeTokens.colors.textMuted,
        )
        attachments.forEach { attachment ->
            when {
                attachment.mimeType.startsWith("image/") -> {
                    EditorImageAttachmentPreview(attachment = attachment, onClick = { onAttachmentClick(attachment.id) })
                }
                attachment.mimeType == "application/pdf" -> {
                    EditorPdfAttachmentPreview(attachment = attachment, onClick = { onAttachmentClick(attachment.id) })
                }
                else -> {
                    EditorAttachmentRow(attachment, onClick = { onAttachmentClick(attachment.id) })
                }
            }
        }
    }
}

@Composable
private fun EditorImageAttachmentPreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, attachment.localPath) {
        value = withContext(Dispatchers.IO) { decodeEditorPreviewBitmap(attachment.localPath, maxSize = 1400) }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            val loadedBitmap = bitmap
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 48.dp)
                }
            }
            AttachmentPreviewCaption(attachment)
        }
    }
}

@Composable
private fun EditorPdfAttachmentPreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 88.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${attachment.sizeLabel()} · PDF preview", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = colors.accent)
            }
        }
    }
}

@Composable
private fun AttachmentPreviewCaption(attachment: AttachmentEntity) {
    val colors = VaultThemeTokens.colors
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${attachment.sizeLabel()} · Tap to open", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

@Composable
private fun EditorAttachmentRow(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Text("${attachment.sizeLabel()} · ${attachment.kindLabel()}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

private fun decodeEditorPreviewBitmap(localPath: String, maxSize: Int): Bitmap? =
    runCatching {
        val file = File(localPath)
        if (!file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }.getOrNull()

private fun TextFieldValue.activeMentionRange(): TextRange? {
    if (!selection.collapsed) return null
    val cursor = selection.start.coerceIn(0, text.length)
    if (cursor == 0) return null
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val at = text.lastIndexOf('@', (cursor - 1).coerceAtLeast(0))
    if (at < lineStart) return null
    val query = text.substring(at + 1, cursor)
    if (query.length > 40 || query.any { it == '\n' || it == '\t' }) return null
    return TextRange(at, cursor)
}
