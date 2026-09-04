package com.myvault.app.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Title
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.BuildConfig
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.repository.SourceReferenceCard
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.ui.components.EditorTool
import com.myvault.app.ui.components.EditorToolbar
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.NoteActionSheet
import com.myvault.app.ui.components.NoteModalActionRow
import com.myvault.app.ui.components.NoteSheetAction
import com.myvault.app.ui.components.NoteSheetSection
import com.myvault.app.ui.components.NoteWorkspaceHeader
import com.myvault.app.ui.components.VaultModal
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.data.formatting.NoteFormattingAction
import com.myvault.app.data.formatting.NoteFormattingModel
import com.myvault.app.data.formatting.NoteFormattingProvider
import com.myvault.app.data.formatting.NoteFormattingUiState
import com.myvault.app.ui.viewmodel.NoteUiState
import com.myvault.app.ui.viewmodel.NoteTableUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(FlowPreview::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uiState: NoteUiState,
    formattingState: NoteFormattingUiState,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (text: String, styleMarks: List<VaultStyleMark>, noteLinks: List<VaultNoteLink>) -> Unit,
    onRunFormattingTool: (action: NoteFormattingAction, provider: NoteFormattingProvider, model: NoteFormattingModel, title: String, body: String) -> Unit,
    onClearFormattingResult: () -> Unit,
    onFormattingProviderSelected: (NoteFormattingProvider) -> Unit = {},
    onFormattingModelSelected: (NoteFormattingModel) -> Unit = {},
    onAzureListenFromHere: (title: String, body: String, startOffset: Int) -> Unit = { _, _, _ -> },
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
    onNoteLinkClick: (String) -> Unit = {},
    onSourceReferenceClick: (String, Int) -> Unit = { _, _ -> },
    onRemoveSourceReference: (String) -> Unit = {},
    onAddKnowledgeTag: (String) -> Unit = {},
    onRemoveKnowledgeTag: (String) -> Unit = {},
    onRestoreVersion: (String) -> Unit = {},
    bodyFontSizeSp: Float = 15f,
    autoFocusBody: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val editorScope = rememberCoroutineScope()
    val bodyFocusRequester = remember { FocusRequester() }
    val bodyBringIntoViewRequester = remember { BringIntoViewRequester() }
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
    var paragraphStyleOpen by remember { mutableStateOf(false) }
    var moreFormattingOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var noteInfoOpen by remember { mutableStateOf(false) }
    var knowledgeOpen by remember { mutableStateOf(false) }
    var attachmentsOpen by remember { mutableStateOf(false) }
    var versionHistoryOpen by remember { mutableStateOf(false) }
    var versionToRestore by remember { mutableStateOf<String?>(null) }
    var tagDialogOpen by remember { mutableStateOf(false) }
    var removeTagDialogOpen by remember { mutableStateOf(false) }
    var sourceReferenceToRemove by remember { mutableStateOf<SourceReferenceCard?>(null) }
    var tagDraft by remember { mutableStateOf("") }
    var intelligentStructureOpen by remember { mutableStateOf(false) }
    var replaceAiDialogOpen by remember { mutableStateOf(false) }
    var structureOnlyNotice by remember { mutableStateOf<String?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    var bodyTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var undoHistory by remember(noteId) { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var redoHistory by remember(noteId) { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var restoringHistory by remember(noteId) { mutableStateOf(false) }
    val isPinned = uiState.note?.isPinned == true
    val isFavourite = uiState.note?.isFavourite == true
    val hasTables = uiState.tables.isNotEmpty()
    val bodyEditorScrollState = rememberScrollState()
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
            EditorTool.Undo,
            EditorTool.Redo,
            EditorTool.Paragraph,
            EditorTool.Bold,
            EditorTool.Italic,
            EditorTool.Underline,
            EditorTool.TextColor,
            EditorTool.BulletList,
            EditorTool.NumberedList,
            EditorTool.Quote,
            EditorTool.More,
        )
    }
    val safeBodyValue = sanitizeVaultTextFieldValue(bodyValue)
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val selectedBodyText = remember(safeBodyValue.text, safeBodyValue.selection) {
        safeBodyValue.selectedTextOrNull()
    }
    val selectedTextChipText = selectedBodyText
    val bodyBottomComfortPadding = if (selectedTextChipText != null) 64.dp else 10.dp

    LaunchedEffect(
        bodyFocused,
        safeBodyValue.selection.start,
        safeBodyValue.selection.end,
        safeBodyValue.text.length,
        bodyTextLayoutResult,
        imeBottom,
    ) {
        if (!bodyFocused || safeBodyValue.text.isEmpty()) return@LaunchedEffect
        val textLayout = bodyTextLayoutResult ?: return@LaunchedEffect
        val selection = safeBodyValue.selection
        val selectionStart = minOf(selection.start, selection.end).coerceIn(0, safeBodyValue.text.lastIndex)
        val selectionEnd = maxOf(selection.start, selection.end).coerceIn(selectionStart + 1, safeBodyValue.text.length)
        val activeOffset = when {
            selection.collapsed -> selection.end.coerceIn(0, safeBodyValue.text.length)
            selection.end > selection.start -> (selection.end - 1).coerceIn(0, safeBodyValue.text.lastIndex)
            else -> selection.end.coerceIn(0, safeBodyValue.text.lastIndex)
        }
        val activeRect = if (selection.collapsed) {
            textLayout.getCursorRect(activeOffset)
        } else {
            textLayout.getBoundingBox(activeOffset)
        }
        val startRect = textLayout.getBoundingBox(selectionStart)
        val endRect = textLayout.getBoundingBox((selectionEnd - 1).coerceIn(0, safeBodyValue.text.lastIndex))
        val completeSelectionRect = Rect(
            left = minOf(startRect.left, endRect.left),
            top = minOf(startRect.top, endRect.top),
            right = maxOf(startRect.right, endRect.right),
            bottom = maxOf(startRect.bottom, endRect.bottom),
        )
        val maxComfortableSelectionHeight = with(density) { 180.dp.toPx() }
        val targetRect = if (!selection.collapsed && completeSelectionRect.height <= maxComfortableSelectionHeight) {
            completeSelectionRect
        } else {
            activeRect
        }
        val topPadding = with(density) { 14.dp.toPx() }
        val bottomPadding = with(density) {
            if (selection.collapsed) 30.dp.toPx() else 52.dp.toPx()
        }
        bodyBringIntoViewRequester.bringIntoView(
            Rect(
                left = targetRect.left,
                top = (targetRect.top - topPadding).coerceAtLeast(0f),
                right = targetRect.right.coerceAtLeast(targetRect.left + 1f),
                bottom = targetRect.bottom + bottomPadding,
            ),
        )
    }
    val activeTools = buildSet {
        addAll(activeVaultToolsForSelection(safeBodyValue, styleMarks, pendingInlineStyles))
        if (safeBodyValue.currentLineStartsWith("• ")) add(EditorTool.BulletList)
        if (safeBodyValue.currentLineMatches(Regex("^\\d+\\.\\s.*"))) add(EditorTool.NumberedList)
    }
    val wordCount = remember(bodyValue.text) { bodyValue.text.split(Regex("\\s+")).count { it.isNotBlank() } }
    val charCount = remember(bodyValue.text) { bodyValue.text.length }
    val numberFormat = remember { NumberFormat.getNumberInstance() }

    val saveStatusLabel = if (editorReady && (bodyValue.text != lastSavedText || styleMarks != lastSavedMarks || noteLinks != lastSavedLinks)) {
        "Saving... · Editing"
    } else {
        "Saved · Editing"
    }
    val noteBreadcrumb = remember(uiState.folderPath) {
        (listOf("My Vault") + uiState.folderPath).joinToString(" / ")
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

    val liveHistorySnapshot = remember(title, bodyValue, styleMarks, noteLinks, pendingInlineStyles) {
        currentHistorySnapshot()
    }
    val canUndo = undoHistory.size > 1 || undoHistory.lastOrNull()?.hasSameEditorContentAs(liveHistorySnapshot) == false
    val canRedo = redoHistory.isNotEmpty()

    LaunchedEffect(noteId, uiState.note?.title, uiState.richText) {
        if (noteId != null && loadedNoteId != noteId) {
            val noteTitle = uiState.note.title
            title = TextFieldValue(noteTitle, TextRange(noteTitle.length))
            bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(uiState.richText.text, TextRange(uiState.richText.text.length)))
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
        val safeUpdatedValue = sanitizeVaultTextFieldValue(updatedValue)
        if (safeUpdatedValue.text == previousValue.text) {
            bodyValue = safeUpdatedValue
            return
        }

        val continuedValue = sanitizeVaultTextFieldValue(continueListOnNewline(previousValue, safeUpdatedValue))
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

    fun insertAiResultBelow(result: String, action: NoteFormattingAction?) {
        if (action.isEditorOutputMode()) {
            traceStructureOnlyEditorStage(context, "04-before-editor-insert-html", result, action)
            val imported = parseRichImport(html = result, plainText = null).document
            traceStructureOnlyEditorStage(context, "05-after-editor-import-text", imported.text, action)
            val separator = if (bodyValue.text.isBlank()) "" else "\n\n"
            val insertStart = bodyValue.text.length + separator.length
            val updatedText = bodyValue.text + separator + imported.text
            bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(updatedText, selection = TextRange(updatedText.length)))
            traceStructureOnlyEditorStage(context, "06-editor-applied-text", updatedText, action)
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

    fun replaceBodyWithAiResult(result: String, action: NoteFormattingAction?) {
        traceStructureOnlyEditorStage(context, "04-before-editor-insert-html", result, action)
        val imported = if (action.isEditorOutputMode()) {
            parseRichImport(html = result, plainText = null).document
        } else {
            VaultRichTextDocument(text = result.trim(), styleMarks = emptyList(), noteLinks = emptyList())
        }
        traceStructureOnlyEditorStage(context, "05-after-editor-import-text", imported.text, action)
        bodyValue = sanitizeVaultTextFieldValue(TextFieldValue(imported.text, selection = TextRange(imported.text.length)))
        traceStructureOnlyEditorStage(context, "06-editor-applied-text", bodyValue.text, action)
        styleMarks = sanitizeVaultStyleMarks(imported.styleMarks, imported.text.length)
        noteLinks = emptyList()
        pendingInlineStyles = emptySet()
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
            EditorTool.Undo -> {
                undoEditorChange()
                return
            }
            EditorTool.Redo -> {
                redoEditorChange()
                return
            }
            EditorTool.Paragraph -> {
                paragraphStyleOpen = true
                return
            }
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
            EditorTool.Quote -> {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = applyVaultStyleFromToolbar(value, styleMarks, pendingInlineStyles, VaultInlineStyle.Quote)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
            }
            EditorTool.More -> {
                moreFormattingOpen = true
                return
            }
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
                    disabledTools = buildSet {
                        if (!canUndo) add(EditorTool.Undo)
                        if (!canRedo) add(EditorTool.Redo)
                    },
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
                NoteWorkspaceHeader(
                    breadcrumb = noteBreadcrumb,
                    status = saveStatusLabel,
                    onMenuClick = {
                        flushPendingBodySave()
                        onMenuClick()
                    },
                    onMoreClick = { moreMenuOpen = true },
                )

                Spacer(modifier = Modifier.height(VaultSpacing.sm))

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
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    bodyFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(bodyEditorScrollState)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    bodyFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                        },
                    ) {
                        BasicTextField(
                            value = safeBodyValue,
                            onValueChange = ::updateBody,
                            modifier = if (hasTables) {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp, max = 220.dp)
                                    .bringIntoViewRequester(bodyBringIntoViewRequester)
                                    .focusRequester(bodyFocusRequester)
                                    .onFocusChanged { bodyFocused = it.isFocused }
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bodyBringIntoViewRequester)
                                    .focusRequester(bodyFocusRequester)
                                    .onFocusChanged { bodyFocused = it.isFocused }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.text,
                                fontSize = bodyFontSizeSp.sp,
                                textDirection = TextDirection.Content,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            visualTransformation = remember(styleMarks, noteLinks, colors) {
                                VaultRichTextVisualTransformation(styleMarks, noteLinks, colors)
                            },
                            maxLines = Int.MAX_VALUE,
                            onTextLayout = { bodyTextLayoutResult = it },
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = if (hasTables) {
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = bodyBottomComfortPadding)
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = bodyBottomComfortPadding)
                                    },
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

                    if (uiState.attachmentsLoading || uiState.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(VaultSpacing.md))
                        EditorAttachmentPreviewSection(
                            attachments = uiState.attachments,
                            loading = uiState.attachmentsLoading,
                            attachmentCount = uiState.attachmentCount,
                            onAttachmentClick = onAttachmentClick,
                        )
                    }
                }
            }

            if (selectedTextChipText != null) {
                Surface(
                    onClick = {
                        val start = minOf(safeBodyValue.selection.start, safeBodyValue.selection.end)
                            .coerceIn(0, safeBodyValue.text.length)
                        onAzureListenFromHere(title.text, safeBodyValue.text, start)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = VaultSpacing.screen, bottom = 8.dp),
                    color = colors.elevated,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(14.dp), tint = colors.accent)
                        Text(
                            "Listen from here",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                            color = colors.text,
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
        NoteActionSheet(
            title = "Note actions",
            onDismiss = { moreMenuOpen = false },
            sections = listOf(
                NoteSheetSection(
                    label = "Note",
                    actions = listOf(
                        NoteSheetAction(
                            label = if (isPinned) "Unpin" else "Pin",
                            icon = Icons.Rounded.PushPin,
                            selected = isPinned,
                            onClick = {
                                onPinnedChange(!isPinned)
                                moreMenuOpen = false
                            },
                        ),
                        NoteSheetAction(
                            label = if (isFavourite) "Unfavourite" else "Favourite",
                            icon = Icons.Rounded.Star,
                            selected = isFavourite,
                            onClick = {
                                onFavouriteChange(!isFavourite)
                                moreMenuOpen = false
                            },
                        ),
                        NoteSheetAction("Note info", Icons.Rounded.Info, subtitle = "Updated, words and characters", onClick = {
                            moreMenuOpen = false
                            noteInfoOpen = true
                        }),
                    ),
                ),
                NoteSheetSection(
                    label = "Content",
                    actions = listOf(
                        NoteSheetAction("Knowledge & references", Icons.Rounded.Link, subtitle = "Tags, backlinks and PDF sources", onClick = {
                            moreMenuOpen = false
                            knowledgeOpen = true
                        }),
                        NoteSheetAction("Attachments", Icons.Rounded.AttachFile, subtitle = "Files and images linked to this note", onClick = {
                            moreMenuOpen = false
                            attachmentsOpen = true
                        }),
                        NoteSheetAction("Version history", Icons.Rounded.History, subtitle = "Restore an earlier saved snapshot", onClick = {
                            moreMenuOpen = false
                            versionHistoryOpen = true
                        }),
                    ),
                ),
                NoteSheetSection(
                    label = "Tools",
                    actions = listOf(
                        NoteSheetAction("Export", Icons.Rounded.FileDownload, subtitle = "TXT or PDF", onClick = {
                            moreMenuOpen = false
                            exportOpen = true
                        }),
                        NoteSheetAction(
                            label = "Structure & Format",
                            icon = Icons.Rounded.AutoAwesome,
                            onClick = {
                                moreMenuOpen = false
                                intelligentStructureOpen = true
                            },
                        ),
                    ),
                ),
                NoteSheetSection(
                    label = "Delete",
                    actions = listOf(
                        NoteSheetAction(
                            label = "Delete note",
                            icon = Icons.Rounded.Delete,
                            onClick = {
                                moreMenuOpen = false
                                deleteDialogOpen = true
                            },
                            destructive = true,
                        ),
                    ),
                ),
            ),
        )
    }

    if (noteInfoOpen) {
        VaultModal(title = "Note info", onDismiss = { noteInfoOpen = false }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            ) {
                NoteInfoStat(
                    label = "Updated",
                    value = uiState.note?.updatedAt?.toRelativeTime() ?: "Unknown",
                    modifier = Modifier.weight(1f),
                )
                NoteInfoStat(
                    label = "Words",
                    value = numberFormat.format(wordCount),
                    modifier = Modifier.weight(1f),
                )
                NoteInfoStat(
                    label = "Characters",
                    value = numberFormat.format(charCount),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (knowledgeOpen) {
        VaultModal(title = "Knowledge & references", onDismiss = { knowledgeOpen = false }) {
            Text(
                "TAGS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            if (uiState.knowledgeTags.isEmpty()) {
                Text("No tags yet", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                KnowledgeTagRow(tags = uiState.knowledgeTags)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                TextButton(onClick = {
                    knowledgeOpen = false
                    tagDraft = ""
                    tagDialogOpen = true
                }) { Text("Add tag") }
                if (uiState.knowledgeTags.isNotEmpty()) {
                    TextButton(onClick = {
                        knowledgeOpen = false
                        removeTagDialogOpen = true
                    }) { Text("Remove tag") }
                }
            }
            Text(
                "BACKLINKS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            if (uiState.backlinks.isEmpty()) {
                Text("No notes link here", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.backlinks.take(5).forEach { backlink ->
                    BacklinkCard(
                        title = backlink.title,
                        preview = backlink.preview,
                        onClick = {
                            knowledgeOpen = false
                            onNoteLinkClick(backlink.id)
                        },
                        compact = true,
                    )
                }
            }
            Text(
                "PDF SOURCES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            if (uiState.sourceReferences.isEmpty()) {
                Text("No PDF sources linked", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.sourceReferences.take(5).forEach { source ->
                    SourceReferenceCardRow(
                        source = source,
                        onClick = {
                            knowledgeOpen = false
                            onSourceReferenceClick(source.attachmentId, source.pageIndex)
                        },
                        onLongPress = { sourceReferenceToRemove = source },
                        compact = true,
                    )
                }
            }
        }
    }

    if (attachmentsOpen) {
        VaultModal(title = "Attachments", onDismiss = { attachmentsOpen = false }) {
            if (uiState.attachmentsLoading && uiState.attachments.isEmpty()) {
                Text("Loading attachments...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else if (uiState.attachments.isEmpty()) {
                Text("No files or images attached", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.attachments.forEach { attachment ->
                    AttachmentSheetRow(
                        attachment = attachment,
                        onClick = {
                            attachmentsOpen = false
                            onAttachmentClick(attachment.id)
                        },
                    )
                }
            }
            Text(
                "ADD",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            NoteModalActionRow(
                NoteSheetAction("Attach file", Icons.Rounded.AttachFile, onClick = {
                    attachmentsOpen = false
                    attachmentPicker.launch(arrayOf("*/*"))
                }),
            )
            NoteModalActionRow(
                NoteSheetAction("Attach image", Icons.Rounded.Image, onClick = {
                    attachmentsOpen = false
                    imagePicker.launch(arrayOf("image/*"))
                }),
            )
        }
    }

    if (versionHistoryOpen) {
        VaultModal(title = "Version history", onDismiss = { versionHistoryOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.accentSoft,
                shape = VaultShapes.md,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                Row(
                    modifier = Modifier.padding(VaultSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                    Column {
                        Text(
                            "Current",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                            color = colors.accent,
                        )
                        Text(
                            "${numberFormat.format(wordCount)} words · ${numberFormat.format(charCount)} characters",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            if (uiState.versions.isEmpty()) {
                Text("No saved versions yet", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.versions.take(12).forEach { version ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(VaultSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    version.createdAt.toRelativeTime(),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                                    color = colors.text,
                                )
                                Text(
                                    "${numberFormat.format(version.wordCount)} words · ${numberFormat.format(version.characterCount)} characters",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textMuted,
                                )
                            }
                            TextButton(onClick = { versionToRestore = version.id }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }

    versionToRestore?.let { versionId ->
        AlertDialog(
            onDismissRequest = { versionToRestore = null },
            title = { Text("Restore this version?") },
            text = { Text("Your current note will be saved as a version first, then this older version will be restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreVersion(versionId)
                        versionToRestore = null
                        versionHistoryOpen = false
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { versionToRestore = null }) { Text("Cancel") }
            },
        )
    }

    if (tagDialogOpen) {
        AlertDialog(
            onDismissRequest = { tagDialogOpen = false },
            title = { Text("Add tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                    if (uiState.knowledgeTags.isNotEmpty()) {
                        KnowledgeTagRow(tags = uiState.knowledgeTags)
                    }
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        singleLine = true,
                        label = { Text("Tag") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddKnowledgeTag(tagDraft)
                        tagDraft = ""
                        tagDialogOpen = false
                    },
                    enabled = tagDraft.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { tagDialogOpen = false }) { Text("Close") }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (removeTagDialogOpen) {
        AlertDialog(
            onDismissRequest = { removeTagDialogOpen = false },
            title = { Text("Remove tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    uiState.knowledgeTags.forEach { tag ->
                        NoteModalActionRow(
                            NoteSheetAction(
                                label = tag.name,
                                icon = Icons.Rounded.LocalOffer,
                                destructive = true,
                                onClick = {
                                    onRemoveKnowledgeTag(tag.id)
                                    removeTagDialogOpen = false
                                },
                            ),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { removeTagDialogOpen = false }) { Text("Cancel") }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    sourceReferenceToRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceReferenceToRemove = null },
            title = { Text("Remove source reference?") },
            text = {
                Text(
                    "This only unlinks the reference from this note. It will not delete the note, PDF, or annotation.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveSourceReference(source.id)
                        sourceReferenceToRemove = null
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { sourceReferenceToRemove = null }) { Text("Cancel") }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (paragraphStyleOpen) {
        fun applyParagraphTool(tool: EditorTool) {
            if (tool == EditorTool.Paragraph) {
                val value = sanitizeVaultTextFieldValue(bodyValue)
                val update = clearVaultHeadingFromToolbar(value, styleMarks, pendingInlineStyles)
                styleMarks = sanitizeVaultStyleMarks(update.marks, value.text.length)
                pendingInlineStyles = update.pendingStyles
                bodyFocusRequester.requestFocus()
            } else {
                applyTool(tool)
            }
            paragraphStyleOpen = false
        }
        NoteActionSheet(
            title = "Text style",
            onDismiss = { paragraphStyleOpen = false },
            sections = listOf(
                NoteSheetSection(
                    label = "Format",
                    actions = listOf(
                        NoteSheetAction("Paragraph", Icons.Rounded.Notes, onClick = { applyParagraphTool(EditorTool.Paragraph) }),
                        NoteSheetAction("Heading 1", Icons.Rounded.Title, onClick = { applyParagraphTool(EditorTool.Heading) }),
                        NoteSheetAction("Heading 2", Icons.Rounded.Title, onClick = { applyParagraphTool(EditorTool.Heading2) }),
                        NoteSheetAction("Heading 3", Icons.Rounded.Title, onClick = { applyParagraphTool(EditorTool.Heading3) }),
                        NoteSheetAction("Heading 4", Icons.Rounded.Title, onClick = { applyParagraphTool(EditorTool.Heading4) }),
                    ),
                ),
            ),
        )
    }

    if (moreFormattingOpen) {
        NoteActionSheet(
            title = "More formatting",
            onDismiss = { moreFormattingOpen = false },
            sections = listOf(
                NoteSheetSection(
                    label = "Insert",
                    actions = listOf(
                        NoteSheetAction("Table", Icons.Rounded.TableChart, onClick = {
                            moreFormattingOpen = false
                            tableDialogOpen = true
                        }),
                        NoteSheetAction("Add web link", Icons.Rounded.Link, onClick = {
                            moreFormattingOpen = false
                            linkUrl = ""
                            linkDialogOpen = true
                        }),
                        NoteSheetAction("Link to note", Icons.Rounded.Notes, onClick = {
                            moreFormattingOpen = false
                            val value = sanitizeVaultTextFieldValue(bodyValue)
                            val selection = value.selection
                            val updatedText = value.text.replaceRange(selection.min, selection.max, "@")
                            updateBody(value.copy(text = updatedText, selection = TextRange(selection.min + 1)))
                            bodyFocusRequester.requestFocus()
                        }),
                        NoteSheetAction("Attach file", Icons.Rounded.AttachFile, onClick = {
                            moreFormattingOpen = false
                            attachmentPicker.launch(arrayOf("*/*"))
                        }),
                        NoteSheetAction("Attach image", Icons.Rounded.Image, onClick = {
                            moreFormattingOpen = false
                            imagePicker.launch(arrayOf("image/*"))
                        }),
                    ),
                ),
            ),
        )
    }

    if (exportOpen) {
        NoteActionSheet(
            title = "Export",
            onDismiss = { exportOpen = false },
            sections = listOf(
                NoteSheetSection(
                    label = "Format",
                    actions = listOf(
                        NoteSheetAction("Text file", Icons.Rounded.FileDownload, onClick = {
                            exportOpen = false
                            exportTextLauncher.launch("${uiState.note?.title?.toSafeFileName() ?: "note"}.txt")
                        }),
                        NoteSheetAction("PDF", Icons.Rounded.FileDownload, onClick = {
                            exportOpen = false
                            exportPdfLauncher.launch("${uiState.note?.title?.toSafeFileName() ?: "note"}.pdf")
                        }),
                    ),
                ),
            ),
        )
    }

    if (intelligentStructureOpen) {
        IntelligentStructureSheet(
            formattingState = formattingState,
            onProviderSelected = onFormattingProviderSelected,
            onModelSelected = onFormattingModelSelected,
            onDismiss = {
                intelligentStructureOpen = false
                structureOnlyNotice = null
            },
            structureOnlyNotice = structureOnlyNotice,
            onRun = { action ->
                structureOnlyNotice = null
                onRunFormattingTool(action, formattingState.provider, formattingState.model, title.text, bodyValue.text)
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(formattingState.result))
            },
            onInsertBelow = {
                insertAiResultBelow(formattingState.result, formattingState.action)
                intelligentStructureOpen = false
                onClearFormattingResult()
            },
            onReplace = { replaceAiDialogOpen = true },
            onClearResult = onClearFormattingResult,
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
                        replaceBodyWithAiResult(formattingState.result, formattingState.action)
                        intelligentStructureOpen = false
                        onClearFormattingResult()
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

private const val EditorHistoryLimit = 48

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

private fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun IntelligentStructureSheet(
    formattingState: NoteFormattingUiState,
    onProviderSelected: (NoteFormattingProvider) -> Unit,
    onModelSelected: (NoteFormattingModel) -> Unit,
    onDismiss: () -> Unit,
    structureOnlyNotice: String? = null,
    onRun: (NoteFormattingAction) -> Unit,
    onCopy: () -> Unit,
    onInsertBelow: () -> Unit,
    onReplace: () -> Unit,
    onClearResult: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editorOutputReady = formattingState.result.isNotBlank() && formattingState.action.isEditorOutputMode()

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
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                    Column {
                        Text(
                            text = "Intelligent Structure",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = "Restructure, format, headings, colour coding",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteFormattingProvider.entries.forEach { provider ->
                    CompactChip(
                        label = provider.displayName,
                        active = formattingState.provider == provider,
                        enabled = !formattingState.loading,
                        onClick = { onProviderSelected(provider) },
                    )
                }
                Box(modifier = Modifier.height(14.dp).width(1.dp).background(colors.borderStrong))
                NoteFormattingModel.entries.forEach { model ->
                    val label = when (model) {
                        NoteFormattingModel.Fast -> formattingState.provider.noteFormattingModelLabel(fast = true)
                        NoteFormattingModel.Smart -> formattingState.provider.noteFormattingModelLabel(fast = false)
                    }
                    CompactChip(
                        label = label,
                        active = formattingState.model == model,
                        enabled = !formattingState.loading,
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
                        text = "Choose Structure Only for lossless formatting: stronger hierarchy, spacing, headings and lists without changing your wording. Choose Intelligent Structure when you want AI to rewrite and reorganise more freely.",
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
                        onClick = { onRun(NoteFormattingAction.StructureOnly) },
                        enabled = !formattingState.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (formattingState.loading && formattingState.action == NoteFormattingAction.StructureOnly) formattingState.progressLabel ?: "Structuring..." else "Run Structure Only")
                    }
                    OutlinedButton(
                        onClick = { onRun(NoteFormattingAction.IntelligentStructure) },
                        enabled = !formattingState.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (formattingState.loading && formattingState.action == NoteFormattingAction.IntelligentStructure) formattingState.progressLabel ?: "Structuring..." else "Run Intelligent Structure")
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
                        formattingState.loading -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(formattingState.progressLabel ?: "Structuring note...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                        editorOutputReady -> {
                            FormattingEditorOutputPreview(
                                action = formattingState.action,
                                result = formattingState.result,
                                onCopy = onCopy,
                                onInsertBelow = onInsertBelow,
                                onReplace = onReplace,
                                onDismiss = onClearResult,
                            )
                        }
                        formattingState.error != null -> {
                            Text(
                                text = formattingState.error,
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

@Composable
private fun CompactChip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        color = if (active) colors.accentSoft else colors.elevated,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.W800 else FontWeight.W600), color = if (active) colors.accent else colors.textSecondary)
        }
    }
}


@Composable
private fun FormattingEditorOutputPreview(
    action: NoteFormattingAction?,
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

private fun NoteFormattingAction?.isEditorOutputMode(): Boolean =
    this == NoteFormattingAction.StructureOnly || this == NoteFormattingAction.IntelligentStructure || this == NoteFormattingAction.CleanFormat || this == NoteFormattingAction.FormatNote

private fun traceStructureOnlyEditorStage(context: Context, stage: String, content: String, action: NoteFormattingAction?) {
    if (action != NoteFormattingAction.StructureOnly || !BuildConfig.DEBUG) return
    Log.d(
        "MyVaultStructureOnly",
        "$stage chars=${content.length} ul=${content.contains("<ul", ignoreCase = true)} ol=${content.contains("<ol", ignoreCase = true)} li=${content.contains("<li", ignoreCase = true)} bullets=${content.contains("•")} numbered=${Regex("(?m)^\\s*\\d+\\.\\s").containsMatchIn(content)}",
    )
    runCatching {
        val dir = java.io.File(context.filesDir, "ai_debug/structure_only").apply { mkdirs() }
        java.io.File(dir, "$stage.html").writeText(content, Charsets.UTF_8)
    }.onFailure { error ->
        Log.w("MyVaultStructureOnly", "Unable to save $stage trace: ${error.message}")
    }
}

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
private fun EditorAttachmentPreviewSection(
    attachments: List<AttachmentEntity>,
    loading: Boolean,
    attachmentCount: Int,
    onAttachmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        Text(
            text = "Attachments",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = VaultThemeTokens.colors.textMuted,
        )
        if (loading && attachments.isEmpty()) {
            EditorAttachmentHydrationPlaceholder(count = attachmentCount)
        } else {
            attachments.forEach { attachment ->
                AttachmentSheetRow(
                    attachment = attachment,
                    onClick = { onAttachmentClick(attachment.id) },
                )
            }
        }
    }
}

@Composable
private fun EditorAttachmentHydrationPlaceholder(count: Int) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface.copy(alpha = 0.72f),
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(colors.inset, VaultShapes.md),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(19.dp), tint = colors.textMuted)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (count > 1) "Loading attachments..." else "Loading attachment...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
                Text(
                    text = "Preparing file preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
}

private fun NoteFormattingProvider.noteFormattingModelLabel(fast: Boolean): String =
    when (this) {
        NoteFormattingProvider.ChatGPT -> if (fast) "GPT Mini" else "GPT Full"
        NoteFormattingProvider.Kimi -> if (fast) "Kimi Fast" else "Kimi Smart"
        NoteFormattingProvider.Gemini -> if (fast) "Gemini Flash" else "Gemini Pro"
    }

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
