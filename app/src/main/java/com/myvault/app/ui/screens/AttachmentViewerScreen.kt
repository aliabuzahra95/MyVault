package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.listener.OnDrawListener
import com.ahmer.pdfviewer.listener.OnErrorListener
import com.ahmer.pdfviewer.listener.OnLoadCompleteListener
import com.ahmer.pdfviewer.listener.OnPageChangeListener
import com.ahmer.pdfviewer.listener.OnPageErrorListener
import com.ahmer.pdfviewer.listener.OnTapListener
import com.ahmer.pdfviewer.util.FitPolicy
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.util.openAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AttachmentViewerScreen(
    attachment: AttachmentEntity?,
    pdfProgress: PdfReadingProgressEntity? = null,
    pdfAnnotations: List<PdfAnnotationEntity> = emptyList(),
    initialPageIndex: Int = -1,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPdfProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
    onAddPdfHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdatePdfHighlightColor: (annotationId: String, color: String) -> Unit = { _, _ -> },
    onUpdatePdfAnnotationNote: (annotationId: String, noteText: String) -> Unit = { _, _ -> },
    onDeletePdfAnnotation: (annotationId: String) -> Unit = {},
    onDeleteAttachment: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenTopBar(onBackClick = onBackClick) {
                if (attachment != null) {
                    if (attachment.mimeType != "application/pdf") {
                        IconBtn(Icons.AutoMirrored.Rounded.OpenInNew, "Open externally") {
                            openAttachment(context, attachment)
                        }
                    }
                    IconBtn(Icons.Rounded.Delete, "Delete attachment") {
                        deleteConfirmOpen = true
                    }
                }
            }

            if (attachment == null) {
                AttachmentViewerEmpty("Attachment not found")
            } else {
                val isPdf = attachment.mimeType == "application/pdf"
                if (!isPdf) {
                    AttachmentViewerHeader(attachment)
                }
                when {
                    isPdf -> PdfAttachmentViewer(
                        attachment = attachment,
                        progress = pdfProgress,
                        annotations = pdfAnnotations,
                        initialPageIndex = initialPageIndex,
                        onProgressChanged = onPdfProgressChanged,
                        onAddHighlight = onAddPdfHighlight,
                        onUpdateHighlightColor = onUpdatePdfHighlightColor,
                        onUpdateAnnotationNote = onUpdatePdfAnnotationNote,
                        onDeleteAnnotation = onDeletePdfAnnotation,
                    )
                    attachment.mimeType.startsWith("image/") -> ImageAttachmentViewer(attachment)
                    else -> UnsupportedAttachmentViewer(attachment)
                }
            }
        }
    }

    if (deleteConfirmOpen && attachment != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete attachment?") },
            text = { Text("${attachment.fileName} will be removed from this note. Your note text will stay untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDeleteAttachment()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }
}

@Composable
private fun AttachmentViewerHeader(attachment: AttachmentEntity) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${attachment.kindLabel()} · ${attachment.sizeLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun ImageAttachmentViewer(attachment: AttachmentEntity) {
    var bitmap by remember(attachment.localPath) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(attachment.localPath) {
        val result = loadImageBitmap(attachment.localPath)
        bitmap = result.getOrNull()
        error = result.exceptionOrNull()?.message
    }

    ZoomableAttachmentCanvas {
        val loadedBitmap = bitmap
        when {
            loadedBitmap != null -> Image(
                bitmap = loadedBitmap,
                contentDescription = attachment.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            error != null -> AttachmentViewerEmpty(error ?: "Unable to load image")
            else -> AttachmentViewerEmpty("Loading image...")
        }
    }
}

@Composable
private fun PdfAttachmentViewer(
    attachment: AttachmentEntity,
    progress: PdfReadingProgressEntity?,
    annotations: List<PdfAnnotationEntity>,
    initialPageIndex: Int,
    onProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit,
    onAddHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String) -> Unit,
    onUpdateHighlightColor: (annotationId: String, color: String) -> Unit,
    onUpdateAnnotationNote: (annotationId: String, noteText: String) -> Unit,
    onDeleteAnnotation: (annotationId: String) -> Unit,
) {
    var pageCount by remember(attachment.localPath) { mutableIntStateOf(progress?.pageCount ?: 0) }
    var pageIndex by remember(attachment.id) {
        mutableIntStateOf(initialPageIndex.takeIf { it >= 0 } ?: progress?.pageIndex?.coerceAtLeast(0) ?: 0)
    }
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }
    var highlighterMode by remember(attachment.id) { mutableStateOf(false) }
    var highlightColor by remember(attachment.id) { mutableStateOf("yellow") }
    var selectedAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var annotationDeleteRequest by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var noteDialogAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var pdfView by remember(attachment.id) { mutableStateOf<PDFView?>(null) }
    var dragStart by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var dragEnd by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var dragStartPagePoint by remember(attachment.id) { mutableStateOf<PagePoint?>(null) }
    val annotationsByPage = remember(annotations) {
        annotations.groupBy { it.pageIndex }
    }
    val currentAnnotationsByPage by rememberUpdatedState(annotationsByPage)
    val pdfSurfaceColor = VaultThemeTokens.colors.inset.toArgb()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            error != null -> AttachmentViewerEmpty(error ?: "Unable to load PDF")
            else -> {
                val file = remember(attachment.localPath) { File(attachment.localPath) }
                if (!file.exists() || !file.isFile) {
                    AttachmentViewerEmpty("PDF file is missing")
                } else {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VaultThemeTokens.colors.inset),
                        factory = { context ->
                            PDFView(context, null).apply {
                                pdfView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                setBackgroundColor(pdfSurfaceColor)
                                setMinZoom(1f)
                                setMidZoom(2.25f)
                                setMaxZoom(5f)
                                fromFile(file)
                                    .enableSwipe(true)
                                    .swipeHorizontal(false)
                                    .enableDoubleTap(true)
                                    .defaultPage(pageIndex)
                                    .enableAnnotationRendering(false)
                                    .enableAntialiasing(true)
                                    .spacing(4)
                                    .autoSpacing(false)
                                    .pageFitPolicy(FitPolicy.WIDTH)
                                    .fitEachPage(true)
                                    .pageSnap(false)
                                    .pageFling(false)
                                    .onLoad(object : OnLoadCompleteListener {
                                        override fun loadComplete(totalPages: Int) {
                                            pageCount = totalPages
                                            error = null
                                            val targetPage = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                                            pageIndex = targetPage
                                            jumpTo(targetPage, false)
                                            onProgressChanged(targetPage, totalPages)
                                        }
                                    })
                                    .onPageChange(object : OnPageChangeListener {
                                        override fun onPageChanged(page: Int, totalPages: Int) {
                                            pageIndex = page
                                            pageCount = totalPages
                                            onProgressChanged(page.coerceIn(0, (totalPages - 1).coerceAtLeast(0)), totalPages)
                                        }
                                    })
                                    .onDrawAll(object : OnDrawListener {
                                        override fun onLayerDrawn(
                                            canvas: android.graphics.Canvas?,
                                            pageWidth: Float,
                                            pageHeight: Float,
                                            currentPage: Int,
                                        ) {
                                            if (canvas == null) return
                                            drawPdfHighlights(
                                                canvas = canvas,
                                                pageWidth = pageWidth,
                                                pageHeight = pageHeight,
                                                annotations = currentAnnotationsByPage[currentPage].orEmpty(),
                                            )
                                        }
                                    })
                                    .onTap(object : OnTapListener {
                                        override fun onTap(e: MotionEvent?): Boolean {
                                            if (e == null) return false
                                            if (highlighterMode) return true
                                            val point = toNormalizedPagePoint(e.x, e.y, clampToPage = false) ?: return false
                                            val annotation = currentAnnotationsByPage[point.pageIndex]
                                                .orEmpty()
                                                .lastOrNull { point.offset.x in it.left..it.right && point.offset.y in it.top..it.bottom }
                                            return if (annotation != null) {
                                                selectedAnnotation = annotation
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    })
                                    .onError(object : OnErrorListener {
                                        override fun onError(t: Throwable?) {
                                            error = t?.message ?: "Unable to load PDF"
                                        }
                                    })
                                    .onPageError(object : OnPageErrorListener {
                                        override fun onPageError(page: Int, t: Throwable?) {
                                            error = "Unable to load page ${page + 1}: ${t?.message ?: "unknown error"}"
                                        }
                                    })
                                    .load()
                            }
                        },
                        update = { view ->
                            if (pdfView !== view) {
                                pdfView = view
                            }
                            view.setBackgroundColor(pdfSurfaceColor)
                        },
                        onRelease = { view ->
                            if (pdfView === view) pdfView = null
                            view.recycle()
                        },
                    )
                }

                if (highlighterMode) {
                    PdfHighlightDragLayer(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f),
                        draftStart = dragStart,
                        draftEnd = dragEnd,
                        draftColor = highlightColor,
                        onDragStart = { offset ->
                            dragStart = offset
                            dragEnd = offset
                            dragStartPagePoint = pdfView?.toNormalizedPagePoint(offset.x, offset.y, clampToPage = false)
                        },
                        onDrag = { offset ->
                            dragEnd = offset
                        },
                        onDragEnd = {
                            val view = pdfView
                            val start = dragStartPagePoint
                            val endOffset = dragEnd
                            if (view != null && start != null && endOffset != null) {
                                val end = view.toNormalizedPagePoint(
                                    x = endOffset.x,
                                    y = endOffset.y,
                                    preferredPage = start.pageIndex,
                                    clampToPage = true,
                                )
                                if (end != null && end.pageIndex == start.pageIndex) {
                                    val left = minOf(start.offset.x, end.offset.x)
                                    val top = minOf(start.offset.y, end.offset.y)
                                    val right = maxOf(start.offset.x, end.offset.x)
                                    val bottom = maxOf(start.offset.y, end.offset.y)
                                    if ((right - left) > 0.01f && (bottom - top) > 0.01f) {
                                        onAddHighlight(attachment.libraryFolderId, start.pageIndex, left, top, right, bottom, highlightColor)
                                    }
                                }
                            }
                            dragStart = null
                            dragEnd = null
                            dragStartPagePoint = null
                        },
                    )
                }

                if (pageCount <= 0) {
                    AttachmentViewerEmpty("Loading PDF...")
                } else {
                    PdfDocumentTitleOverlay(attachment = attachment)
                    PdfReadingProgressOverlay(
                        pageIndex = pageIndex.coerceIn(0, pageCount - 1),
                        pageCount = pageCount,
                    )
                }

                PdfReaderControls(
                    modifier = Modifier.zIndex(3f),
                    highlighterMode = highlighterMode,
                    highlightColor = highlightColor,
                    onHighlighterModeChange = { enabled ->
                        highlighterMode = enabled
                        dragStart = null
                        dragEnd = null
                        dragStartPagePoint = null
                    },
                    onHighlightColorChange = { highlightColor = it },
                )
            }
        }
    }

    LaunchedEffect(annotations, pdfView) {
        pdfView?.invalidate()
    }

    selectedAnnotation?.let { annotation ->
        PdfAnnotationActionsDialog(
            annotation = annotation,
            onDismiss = { selectedAnnotation = null },
            onColorSelected = { color ->
                onUpdateHighlightColor(annotation.id, color)
                selectedAnnotation = null
            },
            onAddNote = {
                noteDraft = annotation.noteText.orEmpty()
                noteDialogAnnotation = annotation
                selectedAnnotation = null
            },
            onDelete = {
                selectedAnnotation = null
                annotationDeleteRequest = annotation
            },
        )
    }

    annotationDeleteRequest?.let { annotation ->
        AlertDialog(
            onDismissRequest = { annotationDeleteRequest = null },
            title = { Text("Delete highlight?") },
            text = {
                Text(
                    "This removes the highlight and any quick note attached to it. The PDF file will not be changed.",
                    color = VaultThemeTokens.colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAnnotation(annotation.id)
                        annotationDeleteRequest = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { annotationDeleteRequest = null }) {
                    Text("Cancel")
                }
            },
            containerColor = VaultThemeTokens.colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    noteDialogAnnotation?.let { annotation ->
        AlertDialog(
            onDismissRequest = { noteDialogAnnotation = null },
            title = { Text("Annotation note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    minLines = 4,
                    label = { Text("Quick note") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateAnnotationNote(annotation.id, noteDraft)
                        noteDialogAnnotation = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogAnnotation = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PdfHighlightDragLayer(
    modifier: Modifier,
    draftStart: Offset?,
    draftEnd: Offset?,
    draftColor: String,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Canvas(
        modifier = modifier.pointerInput(draftColor) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                onDragStart(down.position)
                val pointerId = down.id

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()
                        ?: break

                    onDrag(change.position)
                    change.consume()

                    if (!change.pressed) {
                        onDragEnd()
                        break
                    }
                }
            }
        },
    ) {
        if (draftStart != null && draftEnd != null) {
            val left = minOf(draftStart.x, draftEnd.x)
            val top = minOf(draftStart.y, draftEnd.y)
            val right = maxOf(draftStart.x, draftEnd.x)
            val bottom = maxOf(draftStart.y, draftEnd.y)
            drawRoundRect(
                color = draftColor.toPdfHighlightColor().copy(alpha = 0.26f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun UnsupportedAttachmentViewer(attachment: AttachmentEntity) {
    val context = LocalContext.current
    AttachmentCanvas {
        Surface(
            onClick = { openAttachment(context, attachment) },
            color = VaultThemeTokens.colors.surface,
            shape = VaultShapes.md,
            border = BorderStroke(1.dp, VaultThemeTokens.colors.border),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(20.dp), tint = VaultThemeTokens.colors.accent)
                Column {
                    Text("Preview not available yet", style = MaterialTheme.typography.bodyMedium, color = VaultThemeTokens.colors.text)
                    Text("Tap to open with another app", style = MaterialTheme.typography.bodySmall, color = VaultThemeTokens.colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PdfDocumentTitleOverlay(attachment: AttachmentEntity) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(1f)
            .padding(start = VaultSpacing.screen, top = VaultSpacing.xs, end = 128.dp),
        color = colors.elevated.copy(alpha = 0.9f),
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${attachment.kindLabel()} · ${attachment.sizeLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.PdfReadingProgressOverlay(
    pageIndex: Int,
    pageCount: Int,
) {
    val colors = VaultThemeTokens.colors
    val percent = ((pageIndex + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
    if (pageCount <= 0) return
    Surface(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .zIndex(1f)
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.elevated.copy(alpha = 0.84f),
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp,
    ) {
        Text(
            text = "Page ${pageIndex + 1} of $pageCount · ${(percent * 100).toInt()}%",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun BoxScope.PdfReaderControls(
    modifier: Modifier = Modifier,
    highlighterMode: Boolean,
    highlightColor: String,
    onHighlighterModeChange: (Boolean) -> Unit,
    onHighlightColorChange: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
        color = colors.elevated.copy(alpha = 0.94f),
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, if (highlighterMode) colors.accent.copy(alpha = 0.55f) else colors.border),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = { onHighlighterModeChange(!highlighterMode) },
                shape = VaultShapes.pill,
                color = if (highlighterMode) colors.accent.copy(alpha = 0.16f) else colors.surface,
                border = BorderStroke(1.dp, if (highlighterMode) colors.accent.copy(alpha = 0.5f) else colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (highlighterMode) "Done" else "Highlight",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = if (highlighterMode) colors.accent else colors.textSecondary,
                    )
                }
            }
            PdfHighlightColors.forEach { color ->
                Surface(
                    onClick = { onHighlightColorChange(color) },
                    modifier = Modifier.size(30.dp),
                    shape = VaultShapes.pill,
                    color = color.toPdfHighlightColor().copy(
                        alpha = when {
                            !highlighterMode -> 0.22f
                            color == highlightColor -> 0.92f
                            else -> 0.46f
                        },
                    ),
                    border = BorderStroke(
                        width = if (highlighterMode && color == highlightColor) 2.dp else 1.dp,
                        color = if (highlighterMode && color == highlightColor) colors.text else colors.border,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PdfAnnotationActionsDialog(
    annotation: PdfAnnotationEntity,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onAddNote: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (annotation.noteText.isNullOrBlank()) "Highlight" else "Annotation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Page ${annotation.pageIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultThemeTokens.colors.textMuted,
                )
                if (!annotation.noteText.isNullOrBlank()) {
                    Surface(
                        color = VaultThemeTokens.colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, VaultThemeTokens.colors.border),
                    ) {
                        Text(
                            text = annotation.noteText.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultThemeTokens.colors.text,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfHighlightColors.forEach { color ->
                        Surface(
                            onClick = { onColorSelected(color) },
                            modifier = Modifier.size(30.dp),
                            shape = VaultShapes.pill,
                            color = color.toPdfHighlightColor().copy(alpha = if (annotation.color == color) 0.9f else 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (annotation.color == color) VaultThemeTokens.colors.textSecondary else VaultThemeTokens.colors.border,
                            ),
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddNote) {
                Text(if (annotation.noteText.isNullOrBlank()) "Add note" else "Edit note")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun ZoomableAttachmentCanvas(
    resetKey: Any? = Unit,
    content: @Composable () -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale == 1f) {
            Offset.Zero
        } else {
            offset + panChange
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultThemeTokens.colors.inset)
            .padding(PaddingValues(horizontal = VaultSpacing.screen, vertical = VaultSpacing.md)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformableState),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun AttachmentCanvas(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultThemeTokens.colors.inset)
            .padding(PaddingValues(horizontal = VaultSpacing.screen, vertical = VaultSpacing.md)),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}

@Composable
private fun AttachmentViewerEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VaultThemeTokens.colors.textMuted)
    }
}

private suspend fun loadImageBitmap(path: String): Result<ImageBitmap> = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "Image file is missing" }
        decodeScaledBitmap(file, maxSize = 1800)?.asImageBitmap()
            ?: error("Unable to decode image")
    }
}

private fun decodeScaledBitmap(file: File, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private val PdfHighlightColors = listOf("yellow", "blue", "green", "red")

private fun String.toPdfHighlightColor(): Color =
    when (lowercase()) {
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFD84D)
    }

private data class PagePoint(
    val pageIndex: Int,
    val offset: Offset,
)

private fun PDFView.toNormalizedPagePoint(
    x: Float,
    y: Float,
    preferredPage: Int? = null,
    clampToPage: Boolean,
): PagePoint? {
    val file = pdfFile ?: return null
    val zoom = zoom
    val pageCount = file.pagesCount
    if (pageCount <= 0) return null

    val candidatePages = if (preferredPage != null) {
        listOf(preferredPage.coerceIn(0, pageCount - 1))
    } else {
        (0 until pageCount).toList()
    }

    candidatePages.forEach { page ->
        val point = toNormalizedPointOnPage(
            page = page,
            x = x,
            y = y,
            zoom = zoom,
            clampToPage = clampToPage,
        )
        if (point != null) return point
    }
    return null
}

private fun PDFView.toNormalizedPointOnPage(
    page: Int,
    x: Float,
    y: Float,
    zoom: Float,
    clampToPage: Boolean,
): PagePoint? {
    val file = pdfFile ?: return null
    val pageSize = file.getScaledPageSize(page, zoom)
    if (pageSize.width <= 0f || pageSize.height <= 0f) return null

    val pageLeft = currentXOffset + file.getSecondaryPageOffset(page, zoom)
    val pageTop = currentYOffset + file.getPageOffset(page, zoom)
    val pageRight = pageLeft + pageSize.width
    val pageBottom = pageTop + pageSize.height
    val withinPage = x in pageLeft..pageRight && y in pageTop..pageBottom
    if (!withinPage && !clampToPage) return null

    return PagePoint(
        pageIndex = page,
        offset = Offset(
            x = ((x - pageLeft) / pageSize.width).coerceIn(0f, 1f),
            y = ((y - pageTop) / pageSize.height).coerceIn(0f, 1f),
        ),
    )
}

private fun drawPdfHighlights(
    canvas: android.graphics.Canvas,
    pageWidth: Float,
    pageHeight: Float,
    annotations: List<PdfAnnotationEntity>,
) {
    if (pageWidth <= 0f || pageHeight <= 0f || annotations.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    annotations.forEach { annotation ->
        val left = annotation.left * pageWidth
        val top = annotation.top * pageHeight
        val right = annotation.right * pageWidth
        val bottom = annotation.bottom * pageHeight
        paint.style = Paint.Style.FILL
        paint.color = annotation.color.toPdfHighlightArgb(alpha = 86)
        canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, paint)
        if (!annotation.noteText.isNullOrBlank()) {
            val markerSize = 22f
            val markerLeft = (right - markerSize - 4f).coerceAtLeast(left + 4f)
            val markerTop = (top + 4f).coerceAtMost((bottom - markerSize - 4f).coerceAtLeast(top + 4f))
            paint.color = android.graphics.Color.argb(222, 255, 255, 255)
            canvas.drawRoundRect(markerLeft, markerTop, markerLeft + markerSize, markerTop + markerSize, 8f, 8f, paint)
            paint.color = annotation.color.toPdfHighlightArgb(alpha = 224)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            canvas.drawRoundRect(markerLeft, markerTop, markerLeft + markerSize, markerTop + markerSize, 8f, 8f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(markerLeft + 7f, markerTop + 7f, 1.7f, paint)
            canvas.drawCircle(markerLeft + 15f, markerTop + 7f, 1.7f, paint)
            canvas.drawCircle(markerLeft + 11f, markerTop + 14f, 1.7f, paint)
        }
    }
}

private fun String.toPdfHighlightArgb(alpha: Int): Int {
    val color = toPdfHighlightColor()
    return android.graphics.Color.argb(
        alpha.coerceIn(0, 255),
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )
}
