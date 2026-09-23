@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myvault.app.ui.screens

import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.pdf.view.PdfView as AndroidxPdfView
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import java.io.File

internal enum class PdfCompanionMode {
    None,
    Note,
    Pdf,
}

internal enum class PdfCompanionSource {
    Study,
    Library,
}

internal fun supportsPdfCompanionPane(widthDp: Int): Boolean = widthDp >= 700

internal fun eligibleCompanionPdfs(
    attachments: List<AttachmentEntity>,
    currentAttachmentId: String,
): List<AttachmentEntity> = attachments
    .asSequence()
    .filter { it.id != currentAttachmentId && it.deletedAt == null }
    .filter { it.mimeType == "application/pdf" || it.fileName.endsWith(".pdf", ignoreCase = true) }
    .filter { File(it.localPath).exists() }
    .sortedBy { it.fileName.lowercase() }
    .toList()

internal fun matchingCompanionNotes(notes: List<NoteEntity>, query: String): List<NoteEntity> {
    val term = query.trim()
    return if (term.isBlank()) notes else notes.filter { note ->
        note.title.contains(term, ignoreCase = true) ||
            note.bodyPlainText.contains(term, ignoreCase = true)
    }
}

internal fun activeCompanionClipNoteId(mode: PdfCompanionMode, noteId: String?): String? =
    noteId?.takeIf { mode == PdfCompanionMode.Note }

@Composable
internal fun PdfCompanionPickerSheet(
    currentAttachmentId: String,
    libraryPdfs: List<AttachmentEntity>,
    studyNotes: List<NoteEntity>,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPdf: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var source by remember { mutableStateOf(PdfCompanionSource.Study) }
    var query by remember { mutableStateOf("") }
    val pdfCandidates = remember(libraryPdfs, currentAttachmentId, query) {
        eligibleCompanionPdfs(libraryPdfs, currentAttachmentId).filter { pdf ->
            query.isBlank() || pdf.fileName.contains(query.trim(), ignoreCase = true)
        }
    }
    val noteCandidates = remember(studyNotes, query) { matchingCompanionNotes(studyNotes, query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Open beside this PDF",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.W800,
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                PdfCompanionSource.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = source == option,
                        onClick = {
                            source = option
                            query = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, PdfCompanionSource.entries.size),
                        label = { Text(if (option == PdfCompanionSource.Study) "Study" else "Library") },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = {
                    Text(if (source == PdfCompanionSource.Study) "Search Study notes" else "Search Library PDFs")
                },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                if (source == PdfCompanionSource.Study) {
                    item(key = "create-note") {
                        ListItem(
                            headlineContent = { Text("Create New Note", color = colors.text, fontWeight = FontWeight.W700) },
                            supportingContent = { Text("Create it in Islamic Study", color = colors.textMuted) },
                            leadingContent = { Icon(Icons.Rounded.Add, null, tint = colors.accent) },
                            modifier = Modifier.clickable(onClick = onCreateNote),
                        )
                    }
                    if (noteCandidates.isEmpty()) {
                        item {
                            Text(
                                if (query.isBlank()) "No Study notes yet." else "No Study note found.",
                                modifier = Modifier.padding(20.dp),
                                color = colors.textMuted,
                            )
                        }
                    }
                    items(noteCandidates, key = NoteEntity::id) { note ->
                        ListItem(
                            headlineContent = {
                                Text(note.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                note.bodyPlainText.takeIf(String::isNotBlank)?.let { body ->
                                    Text(body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            leadingContent = { Icon(Icons.Rounded.EditNote, null, tint = colors.textSecondary) },
                            modifier = Modifier.clickable { onOpenNote(note.id) },
                        )
                    }
                } else {
                    if (pdfCandidates.isEmpty()) {
                        item {
                            Text(
                                if (query.isBlank()) "No other downloaded PDFs are available." else "No PDF found.",
                                modifier = Modifier.padding(20.dp),
                                color = colors.textMuted,
                            )
                        }
                    }
                    items(pdfCandidates, key = AttachmentEntity::id) { pdf ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    pdf.fileName,
                                    color = colors.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.PictureAsPdf, null, tint = colors.textSecondary) },
                            modifier = Modifier.clickable { onOpenPdf(pdf.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecondaryPdfReaderPane(
    attachment: AttachmentEntity,
    progress: PdfReadingProgressEntity?,
    onProgressChanged: (Int, Int) -> Unit,
    onChangePdf: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    var pdfView by remember(attachment.id) { mutableStateOf<AndroidxPdfView?>(null) }
    var pdfReady by remember(attachment.id) { mutableStateOf(false) }
    var pageCount by remember(attachment.id) { mutableIntStateOf(progress?.pageCount ?: 0) }
    var pageIndex by remember(attachment.id) { mutableIntStateOf(progress?.pageIndex ?: 0) }
    var savedPageApplied by remember(attachment.id) { mutableStateOf(false) }
    var viewportTick by remember(attachment.id) { mutableLongStateOf(0L) }
    var error by remember(attachment.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfReady, pageCount, pdfView, attachment.id) {
        val view = pdfView ?: return@LaunchedEffect
        if (!pdfReady || savedPageApplied || pageCount <= 0) return@LaunchedEffect
        val safe = (progress?.pageIndex ?: 0).coerceIn(0, pageCount - 1)
        savedPageApplied = true
        view.scrollToPage(safe)
        pageIndex = safe
        onProgressChanged(safe, pageCount)
    }

    Surface(modifier = modifier, color = colors.inset) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "Close second pane", tint = colors.text)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        attachment.fileName,
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                    )
                }
                IconButton(onClick = onChangePdf) {
                    Icon(Icons.Rounded.SwapHoriz, "Choose another PDF", tint = colors.textSecondary)
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    error != null -> Text(
                        error.orEmpty(),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = colors.textMuted,
                    )
                    !file.exists() -> Text(
                        "This PDF is not available on this device.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = colors.textMuted,
                    )
                    else -> AndroidxPdfViewer(
                        file = file,
                        modifier = Modifier.fillMaxSize(),
                        annotations = emptyList(),
                        viewportTick = viewportTick,
                        pageCount = pageCount,
                        highlightColor = "yellow",
                        drawHighlightMode = false,
                        textBoxMode = false,
                        annotationPickMode = false,
                        onCreateTextBox = {},
                        onUpdateTextBoxBounds = { _, _ -> },
                        onSelectAnnotation = {},
                        onAddHighlightRect = { _, _ -> },
                        onTextSelectionChanged = {},
                        onPdfViewReady = { view ->
                            if (pdfView !== view) {
                                pdfView = view
                                view.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int ->
                                    if (pdfReady) viewportTick += 1
                                }
                                view.addOnFirstContentLoadListener(
                                    object : AndroidxPdfView.OnFirstContentLoadListener {
                                        override fun onFirstContentLoad() {
                                            pdfReady = true
                                            pageCount = view.pdfDocument?.pageCount ?: pageCount
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
                                            val safe = selectPdfReadingPage(
                                                pages = (0 until pageLocations.size()).map { index ->
                                                    val bounds = pageLocations.valueAt(index)
                                                    VisibleReadingPage(
                                                        pageLocations.keyAt(index),
                                                        bounds.top,
                                                        bounds.bottom,
                                                    )
                                                },
                                                viewportHeight = view.height.toFloat(),
                                                fallback = firstVisiblePage,
                                            ).coerceIn(0, count - 1)
                                            pageCount = count
                                            if (savedPageApplied && safe != pageIndex) {
                                                pageIndex = safe
                                                onProgressChanged(safe, count)
                                            }
                                            viewportTick += 1
                                        }
                                    },
                                )
                            }
                        },
                        onError = { error = it.message ?: "Unable to open this PDF" },
                    )
                }
            }
        }
    }
}
