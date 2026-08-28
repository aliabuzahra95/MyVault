@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myvault.app.ui.screens

import android.graphics.RectF
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.pdf.PdfRect
import androidx.pdf.PdfPoint
import androidx.pdf.view.PdfView as AndroidxPdfView
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.local.entity.isCompatibilityPreservedPdfTextBox
import com.myvault.app.data.local.entity.isCurrentPdfAnnotation
import com.myvault.app.data.local.entity.resolvedGeometrySegments
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.LibraryReferencedNote
import com.myvault.app.data.repository.PdfAnnotationSegmentInput
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay
import java.io.File

private enum class PdfReaderSheet {
    None,
    SelectionColours,
    SelectionNote,
    SelectionMore,
    LocalActivity,
    CurrentPage,
    PageNote,
    AnnotationActions,
    AnnotationColours,
    AnnotationTags,
    StudyLink,
    Listen,
    PageJump,
}

private enum class PdfActivityFilter(val label: String) {
    All("All"),
    Highlights("Highlights"),
    Notes("Notes"),
    StudyLinks("Study links"),
}

@Composable
internal fun FrozenPdfReaderScreen(
    attachment: AttachmentEntity,
    progress: PdfReadingProgressEntity?,
    annotations: List<PdfAnnotationEntity>,
    annotationSegments: List<PdfAnnotationSegmentEntity>,
    studyNotes: List<NoteEntity>,
    references: List<LibraryReferencedNote>,
    annotationTags: Map<String, List<KnowledgeTagChip>>,
    initialPageIndex: Int?,
    onMenuClick: () -> Unit,
    onProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit,
    onFirstLoaded: () -> Unit,
    onAddDrawHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String, onSaved: (Boolean) -> Unit) -> Unit,
    onAddSelectedTextAnnotation: (libraryFolderId: String?, selectedText: String, segments: List<PdfAnnotationSegmentInput>, color: String, noteText: String?, onSaved: (String?) -> Unit) -> Unit,
    onUpdateAnnotationColor: (annotationId: String, color: String) -> Unit,
    onUpdateAnnotationNote: (annotationId: String, noteText: String) -> Unit,
    onAddPageNote: (libraryFolderId: String?, pageIndex: Int, noteText: String, onSaved: (Boolean) -> Unit) -> Unit,
    onDeleteAnnotation: (annotationId: String) -> Unit,
    onAddAnnotationTag: (annotationId: String, name: String) -> Unit,
    onRemoveAnnotationTag: (annotationId: String, tagId: String) -> Unit,
    onLinkAnnotationToStudyNote: (annotationId: String, noteId: String) -> Unit,
    onCreateStudyNoteFromAnnotation: (annotationId: String, onCreated: (String) -> Unit) -> Unit,
    onOpenStudyNote: (noteId: String) -> Unit,
    onStartDeviceNarration: (selection: String?) -> Unit,
    onStartOpenAiNarration: (selection: String?) -> Unit,
    onStartAzureNarration: (selection: String?) -> Unit,
    narrationMiniPlayerVisible: Boolean = false,
    narrationMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val clipboard = LocalClipboardManager.current
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    var pdfView by remember(attachment.id) { mutableStateOf<AndroidxPdfView?>(null) }
    var pdfReady by remember(attachment.id) { mutableStateOf(false) }
    var pageCount by remember(attachment.id) { mutableIntStateOf(progress?.pageCount ?: 0) }
    var pageIndex by remember(attachment.id) {
        mutableIntStateOf(initialPageIndex?.coerceAtLeast(0) ?: progress?.pageIndex?.coerceAtLeast(0) ?: 0)
    }
    var savedPageApplied by remember(attachment.id) { mutableStateOf(initialPageIndex == null || initialPageIndex <= 0) }
    var viewportTick by remember(attachment.id) { mutableLongStateOf(0L) }
    var gestureActive by remember(attachment.id) { mutableStateOf(false) }
    var error by remember(attachment.id) { mutableStateOf<String?>(null) }
    var selection by remember(attachment.id) { mutableStateOf<PdfTextSelectionUi?>(null) }
    var sheet by remember(attachment.id) { mutableStateOf(PdfReaderSheet.None) }
    var selectedAnnotationId by remember(attachment.id) { mutableStateOf<String?>(null) }
    var editingAnnotationNoteId by remember(attachment.id) { mutableStateOf<String?>(null) }
    var pendingPersistedAnnotationId by remember(attachment.id) { mutableStateOf<String?>(null) }
    var noteDraft by remember(attachment.id) { mutableStateOf("") }
    var tagDraft by remember(attachment.id) { mutableStateOf("") }
    var drawHighlightMode by remember(attachment.id) { mutableStateOf(false) }
    var drawColour by remember(attachment.id) { mutableStateOf("yellow") }
    var drawColourPickerOpen by remember(attachment.id) { mutableStateOf(false) }
    var overflowOpen by remember(attachment.id) { mutableStateOf(false) }
    var immersive by remember(attachment.id) { mutableStateOf(false) }
    var activityOpen by remember(attachment.id) { mutableStateOf(false) }
    var activitySearchOpen by remember(attachment.id) { mutableStateOf(false) }
    var activityQuery by remember(attachment.id) { mutableStateOf("") }
    var activityFilter by remember(attachment.id) { mutableStateOf(PdfActivityFilter.All) }
    var localActivityFilter by remember(attachment.id) { mutableStateOf(PdfActivityFilter.All) }
    var jumpDraft by remember(attachment.id) { mutableStateOf("") }
    var transientMessage by remember(attachment.id) { mutableStateOf<String?>(null) }

    val visibleAnnotations = remember(annotations) {
        annotations.filter { it.isCurrentPdfAnnotation() || it.isCompatibilityPreservedPdfTextBox() }
    }
    val selectedAnnotation = remember(selectedAnnotationId, visibleAnnotations) {
        visibleAnnotations.firstOrNull { it.id == selectedAnnotationId }
    }
    val currentPageAnnotations = remember(pageIndex, visibleAnnotations) {
        visibleAnnotations.filter { it.pageIndex == pageIndex }
    }
    val currentPageReferences = remember(pageIndex, references) {
        references.filter { it.pageIndex == pageIndex }
    }
    val currentPageNoteCount = remember(currentPageAnnotations) {
        currentPageAnnotations.count {
            it.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE ||
                (it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT && !it.noteText.isNullOrBlank())
        }
    }
    val highlightCount = remember(visibleAnnotations) {
        visibleAnnotations.count { it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT }
    }
    val noteCount = remember(visibleAnnotations) {
        visibleAnnotations.count {
            it.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE || !it.noteText.isNullOrBlank()
        }
    }
    val annotationPillBottom = if (narrationMiniPlayerVisible) narrationMiniPlayerHeight + 10.dp else 16.dp

    BackHandler(
        enabled = drawColourPickerOpen ||
            sheet != PdfReaderSheet.None ||
            drawHighlightMode ||
            selection != null ||
            overflowOpen ||
            activityOpen ||
            immersive,
    ) {
        when {
            drawColourPickerOpen -> drawColourPickerOpen = false
            sheet == PdfReaderSheet.LocalActivity -> sheet = PdfReaderSheet.None
            drawHighlightMode -> drawHighlightMode = false
            sheet == PdfReaderSheet.SelectionColours ||
                sheet == PdfReaderSheet.SelectionNote ||
                sheet == PdfReaderSheet.SelectionMore -> sheet = PdfReaderSheet.None
            selection != null -> selection = null
            sheet != PdfReaderSheet.None -> sheet = PdfReaderSheet.None
            overflowOpen -> overflowOpen = false
            activityOpen -> activityOpen = false
            immersive -> immersive = false
        }
    }

    LaunchedEffect(pdfReady, initialPageIndex, pageCount, pdfView) {
        val view = pdfView ?: return@LaunchedEffect
        val target = initialPageIndex ?: return@LaunchedEffect
        if (!pdfReady || savedPageApplied || pageCount <= 0) return@LaunchedEffect
        val safe = target.coerceIn(0, pageCount - 1)
        savedPageApplied = true
        view.scrollToPage(safe)
        pageIndex = safe
        onProgressChanged(safe, pageCount)
    }

    LaunchedEffect(pdfReady, gestureActive) {
        if (!pdfReady || !gestureActive) return@LaunchedEffect
        while (gestureActive) {
            withFrameNanos { }
            viewportTick += 1
        }
    }

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            delay(1_500)
            transientMessage = null
        }
    }

    LaunchedEffect(pendingPersistedAnnotationId, visibleAnnotations) {
        val pending = pendingPersistedAnnotationId ?: return@LaunchedEffect
        if (visibleAnnotations.any { it.id == pending }) {
            selectedAnnotationId = pending
            pendingPersistedAnnotationId = null
        }
    }

    fun persistSelection(
        color: String,
        noteText: String? = null,
        afterSave: (String) -> Unit = {},
    ) {
        val current = selection ?: return
        if (current.text.isBlank() || current.bounds.isEmpty()) return
        val segments = current.bounds.map { rect ->
            PdfAnnotationSegmentInput(
                pageIndex = rect.pageNum,
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }
        onAddSelectedTextAnnotation(
            attachment.libraryFolderId,
            current.text,
            segments,
            color,
            noteText,
        ) { id ->
            if (id == null) {
                transientMessage = "Annotation not saved"
            } else {
                pendingPersistedAnnotationId = id
                afterSave(id)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.inset),
    ) {
        if (file.exists()) {
            AndroidxPdfViewer(
                file = file,
                modifier = Modifier.fillMaxSize(),
                annotations = visibleAnnotations,
                annotationSegments = annotationSegments,
                viewportTick = viewportTick,
                pageCount = pageCount,
                highlightColor = drawColour,
                drawHighlightMode = drawHighlightMode,
                textBoxMode = false,
                annotationPickMode = false,
                onCreateTextBox = {},
                onUpdateTextBoxBounds = { _, _ -> },
                onSelectAnnotation = { annotation ->
                    selectedAnnotationId = annotation.id
                    sheet = PdfReaderSheet.AnnotationActions
                },
                onAddHighlightRect = { rect, color ->
                    onAddDrawHighlight(
                        attachment.libraryFolderId,
                        rect.pageNum,
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        color,
                    ) { saved ->
                        transientMessage = if (saved) "Highlight saved" else "Highlight not saved"
                    }
                },
                onTextSelectionChanged = { selection = it },
                onUnclaimedSingleTap = {
                    if (
                        immersive &&
                        !activityOpen &&
                        sheet == PdfReaderSheet.None &&
                        selection == null &&
                        !drawHighlightMode
                    ) {
                        immersive = false
                    }
                },
                onPdfViewReady = { view ->
                    if (pdfView === view) return@AndroidxPdfViewer
                    pdfView = view
                    view.addOnGestureStateChangedListener(
                        object : AndroidxPdfView.OnGestureStateChangedListener {
                            override fun onGestureStateChanged(newState: Int) {
                                gestureActive = newState != AndroidxPdfView.GESTURE_STATE_IDLE
                                viewportTick += 1
                            }
                        },
                    )
                    view.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int ->
                        if (pdfReady) viewportTick += 1
                    }
                    view.addOnFirstContentLoadListener(
                        object : AndroidxPdfView.OnFirstContentLoadListener {
                            override fun onFirstContentLoad() {
                                pdfReady = true
                                pageCount = view.pdfDocument?.pageCount ?: pageCount
                                if (pageCount > 0) {
                                    pageIndex = view.firstVisiblePage.coerceIn(0, pageCount - 1)
                                    onProgressChanged(pageIndex, pageCount)
                                }
                                onFirstLoaded()
                            }
                        },
                    )
                    view.addOnViewportChangedListener(
                        object : AndroidxPdfView.OnViewportChangedListener {
                            override fun onViewportChanged(
                                firstVisiblePage: Int,
                                visiblePagesCount: Int,
                                pageLocations: android.util.SparseArray<RectF>,
                                zoom: Float,
                            ) {
                                val count = view.pdfDocument?.pageCount ?: pageCount
                                if (count <= 0) return
                                val safe = firstVisiblePage.coerceIn(0, count - 1)
                                pageCount = count
                                if (safe != pageIndex) {
                                    pageIndex = safe
                                    onProgressChanged(safe, count)
                                }
                                viewportTick += 1
                            }
                        },
                    )
                },
                onError = { error = it.message ?: "Unable to open this PDF" },
            )
        } else {
            FrozenPdfCanvasState("PDF unavailable", "The source file could not be found on this device.")
        }

        if (!immersive && !activityOpen) {
            FrozenPdfHeader(
                attachment = attachment,
                pageIndex = pageIndex,
                pageCount = pageCount,
                onMenuClick = onMenuClick,
                onPageClick = {
                    jumpDraft = (pageIndex + 1).toString()
                    sheet = PdfReaderSheet.PageJump
                },
                overflowOpen = overflowOpen,
                onOverflowOpenChange = { overflowOpen = it },
                onActivity = {
                    overflowOpen = false
                    activityOpen = true
                },
                onListen = {
                    overflowOpen = false
                    sheet = PdfReaderSheet.Listen
                },
                onGoToPage = {
                    overflowOpen = false
                    jumpDraft = (pageIndex + 1).toString()
                    sheet = PdfReaderSheet.PageJump
                },
                onImmersive = {
                    overflowOpen = false
                    immersive = true
                },
            )
        }

        if (pdfReady && currentPageNoteCount > 0 && !activityOpen && sheet == PdfReaderSheet.None) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { sheet = PdfReaderSheet.CurrentPage },
                shape = VaultShapes.sm,
                color = colors.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.StickyNote2, null, Modifier.size(13.dp), tint = colors.textSecondary)
                    Text(currentPageNoteCount.toString(), fontSize = 10.sp, color = colors.textSecondary)
                }
            }
        }

        selection?.takeIf { it.text.isNotBlank() && !drawHighlightMode && !activityOpen }?.let {
            FrozenPdfSelectionToolbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (immersive) 142.dp else 198.dp)
                    .zIndex(6f),
                onHighlight = { sheet = PdfReaderSheet.SelectionColours },
                onNote = {
                    editingAnnotationNoteId = null
                    noteDraft = ""
                    sheet = PdfReaderSheet.SelectionNote
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(it.text))
                    transientMessage = "Copied"
                },
                onMore = { sheet = PdfReaderSheet.SelectionMore },
            )
        }

        if (pdfReady && !activityOpen && sheet == PdfReaderSheet.None && selection == null) {
            FrozenPdfAnnotationPill(
                highlightCount = highlightCount,
                noteCount = noteCount,
                color = drawColour,
                drawMode = drawHighlightMode,
                colorPickerOpen = drawColourPickerOpen,
                onDrawHighlight = {
                    drawHighlightMode = true
                    drawColourPickerOpen = false
                },
                onToggleColorPicker = { drawColourPickerOpen = !drawColourPickerOpen },
                onColorChange = {
                    drawColour = it
                    drawColourPickerOpen = false
                },
                onOpenActivity = { sheet = PdfReaderSheet.LocalActivity },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = annotationPillBottom)
                    .zIndex(6f),
            )
        }

        if (drawHighlightMode && !activityOpen && sheet == PdfReaderSheet.None) {
            FrozenDrawHighlightBar(
                color = drawColour,
                onClose = {
                    drawHighlightMode = false
                    drawColourPickerOpen = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = annotationPillBottom + 55.dp)
                    .zIndex(7f),
            )
        }

        transientMessage?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                shape = VaultShapes.pill,
                color = colors.elevated,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = VaultSpacing.md, vertical = VaultSpacing.sm),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = colors.text,
                )
            }
        }

        error?.let { FrozenPdfCanvasState("PDF could not be displayed", it) }

        if (activityOpen) {
            FrozenPdfActivity(
                attachment = attachment,
                annotations = visibleAnnotations,
                references = references,
                searchOpen = activitySearchOpen,
                query = activityQuery,
                filter = activityFilter,
                onBack = { activityOpen = false },
                onSearchOpenChange = { activitySearchOpen = it },
                onQueryChange = { activityQuery = it },
                onFilterChange = { activityFilter = it },
                onAnnotationClick = { annotation ->
                    pdfView?.scrollToAnnotation(annotation, annotationSegments)
                    pageIndex = annotation.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    activityOpen = false
                },
                onAnnotationActions = { annotation ->
                    if (annotation.isCompatibilityPreservedPdfTextBox()) {
                        transientMessage = "Historic text box is read-only"
                    } else {
                        selectedAnnotationId = annotation.id
                        sheet = PdfReaderSheet.AnnotationActions
                    }
                },
                onReferenceClick = { reference -> onOpenStudyNote(reference.noteId) },
            )
        }
    }

    when (sheet) {
        PdfReaderSheet.None -> Unit
        PdfReaderSheet.SelectionColours -> FrozenColourSheet(
            title = "Highlight",
            onDismiss = { sheet = PdfReaderSheet.None },
            onSelect = { color ->
                persistSelection(color = color) { transientMessage = "Highlight saved" }
                sheet = PdfReaderSheet.None
            },
        )
        PdfReaderSheet.SelectionNote -> FrozenTextEntrySheet(
            title = if (editingAnnotationNoteId == null) "Add note" else "Edit note",
            label = "Note",
            value = noteDraft,
            onValueChange = { noteDraft = it },
            onDismiss = { sheet = PdfReaderSheet.None },
            onSave = {
                val existingId = editingAnnotationNoteId
                if (existingId == null) {
                    persistSelection(color = "yellow", noteText = noteDraft) { transientMessage = "Note saved" }
                } else {
                    onUpdateAnnotationNote(existingId, noteDraft)
                    transientMessage = "Note updated"
                    editingAnnotationNoteId = null
                }
                sheet = PdfReaderSheet.None
            },
        )
        PdfReaderSheet.SelectionMore -> FrozenActionSheet(
            title = "Selected text",
            onDismiss = { sheet = PdfReaderSheet.None },
            actions = listOf(
                FrozenPdfAction("Listen from here", Icons.Rounded.Headphones) {
                    sheet = PdfReaderSheet.Listen
                },
                FrozenPdfAction("Link to Study note", Icons.Rounded.Link) {
                    persistSelection("yellow") { id ->
                        selectedAnnotationId = id
                        sheet = PdfReaderSheet.StudyLink
                    }
                },
                FrozenPdfAction("Create Study note", Icons.Rounded.NoteAdd) {
                    persistSelection("yellow") { id ->
                        onCreateStudyNoteFromAnnotation(id, onOpenStudyNote)
                        sheet = PdfReaderSheet.None
                    }
                },
                FrozenPdfAction("Add tag", Icons.Rounded.Sell) {
                    persistSelection("yellow") { id ->
                        selectedAnnotationId = id
                        tagDraft = ""
                        sheet = PdfReaderSheet.AnnotationTags
                    }
                },
            ),
        )
        PdfReaderSheet.LocalActivity -> FrozenLocalPdfActivitySheet(
            annotations = visibleAnnotations,
            references = references,
            filter = localActivityFilter,
            onFilterChange = { localActivityFilter = it },
            onDismiss = { sheet = PdfReaderSheet.None },
            onAnnotationClick = { annotation ->
                pdfView?.scrollToAnnotation(annotation, annotationSegments)
                pageIndex = annotation.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                sheet = PdfReaderSheet.None
            },
            onAnnotationActions = { annotation ->
                if (annotation.isCompatibilityPreservedPdfTextBox()) {
                    transientMessage = "Historic text box is read-only"
                } else {
                    selectedAnnotationId = annotation.id
                    sheet = PdfReaderSheet.AnnotationActions
                }
            },
            onReferenceClick = { onOpenStudyNote(it.noteId) },
            onViewAllActivity = {
                sheet = PdfReaderSheet.None
                activityOpen = true
            },
        )
        PdfReaderSheet.CurrentPage -> FrozenCurrentPageSheet(
            pageIndex = pageIndex,
            annotations = currentPageAnnotations,
            references = currentPageReferences,
            onDismiss = { sheet = PdfReaderSheet.None },
            onAnnotation = { annotation ->
                selectedAnnotationId = annotation.id
                sheet = PdfReaderSheet.AnnotationActions
            },
            onDrawHighlight = {
                sheet = PdfReaderSheet.None
                drawHighlightMode = true
            },
            onAddPageNote = {
                noteDraft = ""
                sheet = PdfReaderSheet.PageNote
            },
            onReference = { onOpenStudyNote(it.noteId) },
        )
        PdfReaderSheet.PageNote -> FrozenTextEntrySheet(
            title = "Page ${pageIndex + 1} note",
            label = "Note",
            value = noteDraft,
            onValueChange = { noteDraft = it },
            onDismiss = { sheet = PdfReaderSheet.None },
            onSave = {
                onAddPageNote(attachment.libraryFolderId, pageIndex, noteDraft) { saved ->
                    transientMessage = if (saved) "Page note saved" else "Page note not saved"
                }
                sheet = PdfReaderSheet.None
            },
        )
        PdfReaderSheet.AnnotationActions -> selectedAnnotation?.let { annotation ->
            FrozenAnnotationActionsSheet(
                annotation = annotation,
                onDismiss = { sheet = PdfReaderSheet.None },
                onEditNote = {
                    editingAnnotationNoteId = annotation.id
                    noteDraft = annotation.noteText.orEmpty()
                    sheet = PdfReaderSheet.SelectionNote
                },
                onColours = { sheet = PdfReaderSheet.AnnotationColours },
                onTags = {
                    tagDraft = ""
                    sheet = PdfReaderSheet.AnnotationTags
                },
                onLink = { sheet = PdfReaderSheet.StudyLink },
                onCreateStudyNote = {
                    onCreateStudyNoteFromAnnotation(annotation.id, onOpenStudyNote)
                    sheet = PdfReaderSheet.None
                },
                onDelete = {
                    onDeleteAnnotation(annotation.id)
                    sheet = PdfReaderSheet.None
                },
            )
        }
        PdfReaderSheet.AnnotationColours -> FrozenColourSheet(
            title = "Change colour",
            onDismiss = { sheet = PdfReaderSheet.None },
            onSelect = { color ->
                selectedAnnotationId?.let { onUpdateAnnotationColor(it, color) }
                sheet = PdfReaderSheet.None
            },
        )
        PdfReaderSheet.AnnotationTags -> selectedAnnotation?.let { annotation ->
            FrozenTagsSheet(
                tags = annotationTags[annotation.id].orEmpty(),
                value = tagDraft,
                onValueChange = { tagDraft = it },
                onAdd = {
                    if (tagDraft.isNotBlank()) onAddAnnotationTag(annotation.id, tagDraft.trim())
                    tagDraft = ""
                },
                onRemove = { onRemoveAnnotationTag(annotation.id, it.id) },
                onDismiss = { sheet = PdfReaderSheet.None },
            )
        }
        PdfReaderSheet.StudyLink -> selectedAnnotation?.let { annotation ->
            FrozenStudyLinkSheet(
                notes = studyNotes,
                onDismiss = { sheet = PdfReaderSheet.None },
                onSelect = { note ->
                    onLinkAnnotationToStudyNote(annotation.id, note.id)
                    sheet = PdfReaderSheet.None
                    transientMessage = "Study note linked"
                },
            )
        }
        PdfReaderSheet.Listen -> FrozenListenSheet(
            onDismiss = { sheet = PdfReaderSheet.None },
            onDevice = {
                onStartDeviceNarration(selection?.text)
                sheet = PdfReaderSheet.None
            },
            onOpenAi = {
                onStartOpenAiNarration(selection?.text)
                sheet = PdfReaderSheet.None
            },
            onAzure = {
                onStartAzureNarration(selection?.text)
                sheet = PdfReaderSheet.None
            },
        )
        PdfReaderSheet.PageJump -> FrozenPageJumpSheet(
            value = jumpDraft,
            pageCount = pageCount,
            onValueChange = { jumpDraft = it.filter(Char::isDigit) },
            onDismiss = { sheet = PdfReaderSheet.None },
            onGo = {
                val page = jumpDraft.toIntOrNull()
                if (page != null && page in 1..pageCount) {
                    pdfView?.scrollToPage(page - 1)
                    pageIndex = page - 1
                    onProgressChanged(page - 1, pageCount)
                    sheet = PdfReaderSheet.None
                }
            },
        )
    }
}

@Composable
private fun FrozenPdfHeader(
    attachment: AttachmentEntity,
    pageIndex: Int,
    pageCount: Int,
    onMenuClick: () -> Unit,
    onPageClick: () -> Unit,
    overflowOpen: Boolean,
    onOverflowOpenChange: (Boolean) -> Unit,
    onActivity: () -> Unit,
    onListen: () -> Unit,
    onGoToPage: () -> Unit,
    onImmersive: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().height(58.dp).zIndex(5f),
        color = colors.bg.copy(alpha = 0.98f),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Rounded.Menu, "Open Explorer", Modifier.size(20.dp), tint = colors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.W800,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Library",
                    fontSize = 9.5.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onPageClick) {
                Text(
                    text = "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}",
                    fontSize = 10.5.sp,
                    color = colors.textMuted,
                )
            }
            Box {
                IconButton(onClick = { onOverflowOpenChange(true) }) {
                    Icon(Icons.Rounded.MoreHoriz, "PDF options", Modifier.size(19.dp), tint = colors.text)
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { onOverflowOpenChange(false) },
                    containerColor = colors.surface,
                    shape = VaultShapes.md,
                ) {
                    Text(
                        "READING",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.W700,
                        color = colors.textMuted,
                    )
                    FrozenDropdownItem("PDF Activity", Icons.Rounded.History, onActivity)
                    FrozenDropdownItem("Listen", Icons.Rounded.Headphones, onListen)
                    FrozenDropdownItem("Go to page", Icons.Rounded.StickyNote2, onGoToPage)
                    FrozenDropdownItem("Immersive mode", Icons.Rounded.Fullscreen, onImmersive)
                }
            }
        }
    }
}

@Composable
private fun FrozenDropdownItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    DropdownMenuItem(
        text = { Text(label, fontSize = 12.sp, color = colors.text) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp), tint = colors.textSecondary) },
        onClick = onClick,
    )
}

@Composable
private fun FrozenPdfSelectionToolbar(
    modifier: Modifier,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 6.dp,
    ) {
        Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FrozenSelectionAction("Highlight", Icons.Rounded.Draw, true, onHighlight)
            FrozenSelectionAction("Note", Icons.Rounded.StickyNote2, false, onNote)
            FrozenSelectionAction("Copy", Icons.Rounded.ContentCopy, false, onCopy)
            IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreHoriz, "More selected-text actions", Modifier.size(17.dp), tint = colors.text)
            }
        }
    }
}

@Composable
private fun FrozenSelectionAction(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = VaultShapes.sm,
        color = if (selected) colors.accentSoft else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = if (selected) colors.accent else colors.text)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.W700, color = if (selected) colors.accent else colors.text)
        }
    }
}

private data class FrozenPdfAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun FrozenActionSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<FrozenPdfAction>,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader(title, onDismiss)
        actions.forEach { action ->
            FrozenSheetRow(action.label, action.icon, action.onClick)
        }
        Spacer(Modifier.height(VaultSpacing.xxl))
    }
}

@Composable
private fun FrozenColourSheet(title: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val colors = VaultThemeTokens.colors
    val choices = listOf(
        "yellow" to Color(0xFFFFE27A),
        "blue" to Color(0xFF9ED8FF),
        "green" to Color(0xFFAEE8C3),
        "pink" to Color(0xFFFFB4C8),
        "orange" to Color(0xFFFFC58A),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader(title, onDismiss)
        Row(
            modifier = Modifier.fillMaxWidth().padding(VaultSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            choices.forEach { (name, color) ->
                Surface(
                    modifier = Modifier.size(42.dp).clickable { onSelect(name) },
                    shape = CircleShape,
                    color = color,
                    border = BorderStroke(1.dp, colors.border),
                ) {}
            }
        }
        Spacer(Modifier.height(VaultSpacing.xxl))
    }
}

@Composable
private fun FrozenTextEntrySheet(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader(title, onDismiss)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
            minLines = 3,
        )
        Button(
            onClick = onSave,
            enabled = value.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(VaultSpacing.screen),
        ) { Text("Save") }
        Spacer(Modifier.height(VaultSpacing.sm))
    }
}

@Composable
private fun FrozenCurrentPageSheet(
    pageIndex: Int,
    annotations: List<PdfAnnotationEntity>,
    references: List<LibraryReferencedNote>,
    onDismiss: () -> Unit,
    onAnnotation: (PdfAnnotationEntity) -> Unit,
    onDrawHighlight: () -> Unit,
    onAddPageNote: () -> Unit,
    onReference: (LibraryReferencedNote) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val highlights = annotations.filter {
        it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT && it.noteText.isNullOrBlank()
    }
    val notes = annotations.filter {
        it.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE ||
            (it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT && !it.noteText.isNullOrBlank())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text(
                "PAGE ${pageIndex + 1}",
                modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                fontSize = 8.sp,
                fontWeight = FontWeight.W700,
                color = colors.textMuted,
            )
            FrozenSheetHeader("Annotations", onDismiss)
            FrozenSectionLabel("HIGHLIGHTS")
            highlights.forEach { FrozenAnnotationRow(it, onClick = { onAnnotation(it) }) }
            FrozenSheetRow("Draw Highlight", Icons.Rounded.Draw, onDrawHighlight, "Mark a rectangular region on this page")
            FrozenSectionLabel("NOTES")
            notes.forEach { FrozenAnnotationRow(it, onClick = { onAnnotation(it) }) }
            FrozenSheetRow("Add page note", Icons.Rounded.Add, onAddPageNote, "Anchor a note to page ${pageIndex + 1}")
            FrozenSectionLabel("STUDY LINKS")
            references.forEach { reference ->
                FrozenSheetRow(reference.noteTitle, Icons.Rounded.Link, { onReference(reference) }, "Linked Study note")
            }
            Spacer(Modifier.height(VaultSpacing.xxl))
        }
    }
}

@Composable
private fun FrozenLocalPdfActivitySheet(
    annotations: List<PdfAnnotationEntity>,
    references: List<LibraryReferencedNote>,
    filter: PdfActivityFilter,
    onFilterChange: (PdfActivityFilter) -> Unit,
    onDismiss: () -> Unit,
    onAnnotationClick: (PdfAnnotationEntity) -> Unit,
    onAnnotationActions: (PdfAnnotationEntity) -> Unit,
    onReferenceClick: (LibraryReferencedNote) -> Unit,
    onViewAllActivity: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val filteredAnnotations = remember(annotations, filter) {
        annotations.filter { annotation ->
            when (filter) {
                PdfActivityFilter.All -> true
                PdfActivityFilter.Highlights ->
                    annotation.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT && annotation.noteText.isNullOrBlank()
                PdfActivityFilter.Notes ->
                    annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE || !annotation.noteText.isNullOrBlank()
                PdfActivityFilter.StudyLinks -> false
            }
        }
    }
    val filteredReferences = remember(references, filter) {
        references.takeIf { filter == PdfActivityFilter.All || filter == PdfActivityFilter.StudyLinks }.orEmpty()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 460.dp)) {
            FrozenSheetHeader("PDF annotations", onDismiss)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PdfActivityFilter.entries.forEach { choice ->
                    FilterChip(
                        selected = filter == choice,
                        onClick = { onFilterChange(choice) },
                        label = { Text(choice.label, fontSize = 10.5.sp) },
                        leadingIcon = if (filter == choice) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(13.dp)) }
                        } else null,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                filteredAnnotations.forEach { annotation ->
                    FrozenActivityAnnotationRow(
                        annotation = annotation,
                        onClick = { onAnnotationClick(annotation) },
                        onActions = { onAnnotationActions(annotation) },
                    )
                }
                filteredReferences.forEach { reference ->
                    FrozenActivityReferenceRow(reference, onClick = { onReferenceClick(reference) })
                }
                if (filteredAnnotations.isEmpty() && filteredReferences.isEmpty()) {
                    Text(
                        "No ${filter.label.lowercase()} in this PDF",
                        modifier = Modifier.padding(VaultSpacing.screen),
                        fontSize = 13.sp,
                        color = colors.textMuted,
                    )
                }
            }
            TextButton(
                onClick = onViewAllActivity,
                modifier = Modifier.align(Alignment.End).padding(horizontal = VaultSpacing.md, vertical = 4.dp),
            ) {
                Text("View all activity", fontSize = 12.5.sp, fontWeight = FontWeight.W700)
            }
        }
    }
}

@Composable
private fun FrozenAnnotationActionsSheet(
    annotation: PdfAnnotationEntity,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onColours: () -> Unit,
    onTags: () -> Unit,
    onLink: () -> Unit,
    onCreateStudyNote: () -> Unit,
    onDelete: () -> Unit,
) {
    if (annotation.isCompatibilityPreservedPdfTextBox()) return
    val actions = buildList {
        if (!annotation.noteText.isNullOrBlank()) add(FrozenPdfAction("Edit note", Icons.Rounded.EditNote, onEditNote))
        if (annotation.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT) {
            add(FrozenPdfAction("Change colour", Icons.Rounded.Draw, onColours))
        }
        add(FrozenPdfAction("Tags", Icons.Rounded.Sell, onTags))
        add(FrozenPdfAction("Link to Study note", Icons.Rounded.Link, onLink))
        add(FrozenPdfAction("Create Study note", Icons.Rounded.NoteAdd, onCreateStudyNote))
        add(FrozenPdfAction("Delete", Icons.Rounded.DeleteOutline, onDelete))
    }
    FrozenActionSheet(
        title = when (annotation.annotationType) {
            PdfAnnotationEntity.TYPE_PAGE_NOTE -> "Page note"
            PdfAnnotationEntity.TYPE_TEXT_BOX -> "Historic text box"
            else -> "Highlight"
        },
        onDismiss = onDismiss,
        actions = actions,
    )
}

private fun AndroidxPdfView.scrollToAnnotation(
    annotation: PdfAnnotationEntity,
    annotationSegments: List<PdfAnnotationSegmentEntity>,
) {
    val geometry = annotation.resolvedGeometrySegments(annotationSegments).firstOrNull()
    val page = geometry?.pageIndex ?: annotation.pageIndex
    val centerX = geometry?.let { (it.left + it.right) / 2f } ?: (annotation.left + annotation.right) / 2f
    val centerY = geometry?.let { (it.top + it.bottom) / 2f } ?: (annotation.top + annotation.bottom) / 2f
    if (page < 0 || !centerX.isFinite() || !centerY.isFinite()) {
        scrollToPage(annotation.pageIndex.coerceAtLeast(0))
        return
    }
    runCatching { scrollToPosition(PdfPoint(page, centerX, centerY)) }
        .onFailure { scrollToPage(page) }
}

@Composable
private fun FrozenTagsSheet(
    tags: List<KnowledgeTagChip>,
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (KnowledgeTagChip) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader("Annotation tags", onDismiss)
        tags.forEach { tag ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Sell, null, Modifier.size(16.dp), tint = colors.textSecondary)
                Text(tag.name, Modifier.weight(1f).padding(horizontal = VaultSpacing.sm), color = colors.text)
                IconButton(onClick = { onRemove(tag) }) {
                    Icon(Icons.Rounded.Close, "Remove ${tag.name}", Modifier.size(16.dp), tint = colors.textMuted)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(VaultSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(value, onValueChange, modifier = Modifier.weight(1f), label = { Text("New tag") }, singleLine = true)
            IconButton(onClick = onAdd, enabled = value.isNotBlank()) {
                Icon(Icons.Rounded.Add, "Add tag")
            }
        }
        Spacer(Modifier.height(VaultSpacing.xxl))
    }
}

@Composable
private fun FrozenStudyLinkSheet(notes: List<NoteEntity>, onDismiss: () -> Unit, onSelect: (NoteEntity) -> Unit) {
    val colors = VaultThemeTokens.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(notes, query) {
        notes.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader("Link to Study note", onDismiss)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search notes") },
            singleLine = true,
        )
        Column(modifier = Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
            filtered.forEach { note ->
                FrozenSheetRow(note.title.ifBlank { "Untitled note" }, Icons.Rounded.StickyNote2, { onSelect(note) })
            }
        }
        Spacer(Modifier.height(VaultSpacing.xxl))
    }
}

@Composable
private fun FrozenListenSheet(onDismiss: () -> Unit, onDevice: () -> Unit, onOpenAi: () -> Unit, onAzure: () -> Unit) {
    FrozenActionSheet(
        title = "Listen",
        onDismiss = onDismiss,
        actions = listOf(
            FrozenPdfAction("Device TTS", Icons.Rounded.Headphones, onDevice),
            FrozenPdfAction("OpenAI TTS", Icons.Rounded.Headphones, onOpenAi),
            FrozenPdfAction("Azure Speech", Icons.Rounded.Headphones, onAzure),
        ),
    )
}

@Composable
private fun FrozenPageJumpSheet(
    value: String,
    pageCount: Int,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGo: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        FrozenSheetHeader("Go to page", onDismiss)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
            label = { Text("Page 1–${pageCount.coerceAtLeast(1)}") },
            singleLine = true,
        )
        Button(
            onClick = onGo,
            enabled = value.toIntOrNull() in 1..pageCount.coerceAtLeast(1),
            modifier = Modifier.fillMaxWidth().padding(VaultSpacing.screen),
        ) { Text("Go") }
        Spacer(Modifier.height(VaultSpacing.sm))
    }
}

@Composable
private fun FrozenPdfAnnotationPill(
    highlightCount: Int,
    noteCount: Int,
    color: String,
    drawMode: Boolean,
    colorPickerOpen: Boolean,
    onDrawHighlight: () -> Unit,
    onToggleColorPicker: () -> Unit,
    onColorChange: (String) -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Box(modifier = modifier) {
        if (colorPickerOpen) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 55.dp)
                    .zIndex(2f),
                shape = VaultShapes.lg,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    listOf("yellow", "blue", "green", "pink").forEach { option ->
                        Surface(
                            onClick = { onColorChange(option) },
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                if (color == option) 2.dp else 1.dp,
                                if (color == option) colors.text else colors.border,
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .background(pdfColour(option), CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.width(252.dp).height(47.dp).zIndex(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = colors.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 7.dp,
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onDrawHighlight,
                    modifier = Modifier.height(35.dp),
                    shape = VaultShapes.md,
                    color = if (drawMode) colors.accentSoft else Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.BorderColor,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (drawMode) colors.accent else colors.text,
                        )
                        Text(
                            "Highlight",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.W700,
                            color = if (drawMode) colors.accent else colors.text,
                        )
                    }
                }
                Surface(
                    onClick = onToggleColorPicker,
                    modifier = Modifier.size(width = 35.dp, height = 35.dp),
                    shape = VaultShapes.md,
                    color = Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(18.dp).background(pdfColour(color), CircleShape))
                    }
                }
                Surface(
                    onClick = onOpenActivity,
                    modifier = Modifier.weight(1f).height(35.dp),
                    shape = VaultShapes.md,
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 7.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "$highlightCount highlight${if (highlightCount == 1) "" else "s"}",
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = colors.textSecondary,
                        )
                        Text(
                            "$noteCount note${if (noteCount == 1) "" else "s"}",
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrozenDrawHighlightBar(
    color: String,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.width(250.dp).height(43.dp),
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.BorderColor, null, Modifier.size(16.dp), tint = colors.textSecondary)
                Column {
                    Text("Draw Highlight", fontSize = 10.5.sp, fontWeight = FontWeight.W700, color = colors.text)
                    Text("Drag a rectangle · $color", fontSize = 8.5.sp, color = colors.textSecondary)
                }
            }
            Surface(
                onClick = onClose,
                modifier = Modifier.height(31.dp),
                shape = VaultShapes.md,
                color = colors.accent,
            ) {
                Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                    Text("Exit", fontSize = 10.5.sp, fontWeight = FontWeight.W700, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FrozenPdfActivity(
    attachment: AttachmentEntity,
    annotations: List<PdfAnnotationEntity>,
    references: List<LibraryReferencedNote>,
    searchOpen: Boolean,
    query: String,
    filter: PdfActivityFilter,
    onBack: () -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (PdfActivityFilter) -> Unit,
    onAnnotationClick: (PdfAnnotationEntity) -> Unit,
    onAnnotationActions: (PdfAnnotationEntity) -> Unit,
    onReferenceClick: (LibraryReferencedNote) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val annotationEntries = remember(annotations, query, filter) {
        annotations.filter { annotation ->
            val typeMatch = when (filter) {
                PdfActivityFilter.All -> true
                PdfActivityFilter.Highlights -> annotation.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT && annotation.noteText.isNullOrBlank()
                PdfActivityFilter.Notes -> annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE || !annotation.noteText.isNullOrBlank()
                PdfActivityFilter.StudyLinks -> false
            }
            val text = annotation.selectedText.orEmpty() + " " + annotation.noteText.orEmpty()
            typeMatch && (query.isBlank() || text.contains(query, ignoreCase = true))
        }
    }
    val referenceEntries = remember(references, query, filter) {
        references.filter {
            (filter == PdfActivityFilter.All || filter == PdfActivityFilter.StudyLinks) &&
                (query.isBlank() || it.noteTitle.contains(query, ignoreCase = true))
        }
    }
    val pages = (annotationEntries.map { it.pageIndex } + referenceEntries.map { it.pageIndex }).distinct().sorted()
    Surface(modifier = Modifier.fillMaxSize().zIndex(10f), color = colors.bg) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to PDF", Modifier.size(20.dp), tint = colors.text)
                }
                Column(Modifier.weight(1f)) {
                    Text("PDF Activity", fontSize = 15.sp, fontWeight = FontWeight.W800, color = colors.text)
                    Text(attachment.fileName, fontSize = 10.5.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onSearchOpenChange(!searchOpen) }) {
                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "Search PDF activity", Modifier.size(20.dp), tint = colors.text)
                }
            }
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
                    placeholder = { Text("Search activity") },
                    singleLine = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PdfActivityFilter.entries.forEach { choice ->
                    FilterChip(
                        selected = filter == choice,
                        onClick = { onFilterChange(choice) },
                        label = { Text(choice.label, fontSize = 10.5.sp) },
                        leadingIcon = if (filter == choice) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp)) }
                        } else null,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                pages.forEach { page ->
                    FrozenSectionLabel("PAGE ${page + 1}")
                    annotationEntries.filter { it.pageIndex == page }.forEach { annotation ->
                        FrozenActivityAnnotationRow(annotation, { onAnnotationClick(annotation) }, { onAnnotationActions(annotation) })
                    }
                    referenceEntries.filter { it.pageIndex == page }.forEach { reference ->
                        FrozenActivityReferenceRow(reference, { onReferenceClick(reference) })
                    }
                }
                if (pages.isEmpty()) {
                    Text(
                        "No matching PDF activity",
                        modifier = Modifier.padding(VaultSpacing.screen),
                        color = colors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrozenActivityAnnotationRow(annotation: PdfAnnotationEntity, onClick: () -> Unit, onActions: () -> Unit) {
    val colors = VaultThemeTokens.colors
    val isNote = annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE || !annotation.noteText.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = VaultSpacing.screen, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isNote) Icons.Rounded.StickyNote2 else Icons.Rounded.BorderColor,
            if (isNote) "Note" else "Highlight",
            Modifier.size(17.dp),
            tint = if (isNote) colors.textSecondary else pdfColour(annotation.color),
        )
        Column(Modifier.weight(1f).padding(horizontal = VaultSpacing.md)) {
            Text(annotationActivityTitle(annotation), fontSize = 13.2.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(annotationActivitySubtitle(annotation), fontSize = 10.5.sp, color = colors.textMuted)
        }
        IconButton(onClick = onActions) {
            Icon(Icons.Rounded.MoreHoriz, "Annotation actions", Modifier.size(17.dp), tint = colors.textMuted)
        }
    }
    HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
}

@Composable
private fun FrozenActivityReferenceRow(reference: LibraryReferencedNote, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = VaultSpacing.screen, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Link, "Study link", Modifier.size(17.dp), tint = colors.textSecondary)
        Column(Modifier.weight(1f).padding(horizontal = VaultSpacing.md)) {
            Text(reference.noteTitle, fontSize = 13.2.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Linked Study note", fontSize = 10.5.sp, color = colors.textMuted)
        }
        Icon(Icons.Rounded.BookmarkBorder, null, Modifier.size(16.dp), tint = colors.textMuted)
    }
    HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
}

@Composable
private fun FrozenAnnotationRow(annotation: PdfAnnotationEntity, onClick: () -> Unit) {
    val isNote = annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE || !annotation.noteText.isNullOrBlank()
    FrozenSheetRow(
        label = annotationActivityTitle(annotation),
        icon = if (isNote) Icons.Rounded.StickyNote2 else Icons.Rounded.BorderColor,
        onClick = onClick,
        subtitle = annotationActivitySubtitle(annotation),
        iconTint = if (isNote) null else pdfColour(annotation.color),
    )
}

@Composable
private fun FrozenSheetHeader(title: String, onDismiss: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.W800, color = colors.text)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, "Close", Modifier.size(18.dp), tint = colors.text)
        }
    }
}

@Composable
private fun FrozenSectionLabel(label: String) {
    val colors = VaultThemeTokens.colors
    Text(
        label,
        modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = 7.dp),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.W800,
        color = colors.textMuted,
    )
}

@Composable
private fun FrozenSheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    iconTint: Color? = null,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = VaultSpacing.screen, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = iconTint ?: colors.textSecondary)
        Column(Modifier.weight(1f).padding(horizontal = VaultSpacing.md)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 10.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun FrozenPdfCanvasState(title: String, message: String) {
    val colors = VaultThemeTokens.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.inset), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.W800, color = colors.text)
            Text(message, fontSize = 11.sp, color = colors.textMuted)
        }
    }
}

private fun annotationActivityTitle(annotation: PdfAnnotationEntity): String =
    annotation.noteText?.takeIf { it.isNotBlank() }
        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
        ?: when (annotation.annotationType) {
            PdfAnnotationEntity.TYPE_PAGE_NOTE -> "Page note"
            PdfAnnotationEntity.TYPE_TEXT_BOX -> "Historic text box"
            else -> "Highlight"
        }

private fun annotationActivitySubtitle(annotation: PdfAnnotationEntity): String = when {
    annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE -> "Page note"
    annotation.annotationType == PdfAnnotationEntity.TYPE_TEXT_BOX -> "Read-only historic text box"
    !annotation.noteText.isNullOrBlank() && !annotation.selectedText.isNullOrBlank() -> "Selected-text note"
    else -> "${annotation.color.replaceFirstChar(Char::uppercase)} highlight"
}

private fun pdfColour(name: String): Color = when (name) {
    "blue" -> Color(0xFF9ED8FF)
    "green" -> Color(0xFFAEE8C3)
    "red" -> Color(0xFFFF8F94)
    "pink" -> Color(0xFFFFB4C8)
    "orange" -> Color(0xFFFFC58A)
    else -> Color(0xFFFFE27A)
}
