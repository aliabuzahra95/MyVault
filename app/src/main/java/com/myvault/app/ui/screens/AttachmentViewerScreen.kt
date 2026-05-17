package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
                    IconBtn(Icons.AutoMirrored.Rounded.OpenInNew, "Open externally") {
                        openAttachment(context, attachment)
                    }
                    IconBtn(Icons.Rounded.Delete, "Delete attachment") {
                        deleteConfirmOpen = true
                    }
                }
            }

            if (attachment == null) {
                AttachmentViewerEmpty("Attachment not found")
            } else {
                AttachmentViewerHeader(attachment)
                when {
                    attachment.mimeType == "application/pdf" -> PdfAttachmentViewer(
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
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }
    var zoom by remember(attachment.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPageIndex.takeIf { it >= 0 } ?: progress?.pageIndex?.coerceAtLeast(0) ?: 0,
    )
    var restoredProgressPage by remember(attachment.id) { mutableStateOf(false) }
    var highlighterMode by remember(attachment.id) { mutableStateOf(false) }
    var highlightColor by remember(attachment.id) { mutableStateOf("yellow") }
    var selectedAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var noteDialogAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val visiblePage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceAtLeast(0) }
    }
    val annotationsByPage = remember(annotations) {
        annotations.groupBy { it.pageIndex }
    }

    LaunchedEffect(attachment.localPath) {
        val result = readPdfPageCount(attachment.localPath)
        result.onSuccess { count ->
            pageCount = count
            error = null
        }.onFailure {
            error = it.message ?: "Unable to load PDF"
        }
    }

    LaunchedEffect(pageCount, attachment.id) {
        if (pageCount <= 0) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { page ->
                delay(500)
                onProgressChanged(page.coerceIn(0, pageCount - 1), pageCount)
            }
    }

    LaunchedEffect(pageCount, progress?.pageIndex, attachment.id) {
        if (pageCount > 0 && !restoredProgressPage) {
            val targetPage = initialPageIndex.takeIf { it >= 0 }?.coerceIn(0, pageCount - 1)
                ?: progress?.pageIndex?.coerceIn(0, pageCount - 1)
            if (targetPage != null && targetPage != listState.firstVisibleItemIndex) {
                listState.scrollToItem(targetPage)
            }
            restoredProgressPage = true
            onProgressChanged((targetPage ?: visiblePage).coerceIn(0, pageCount - 1), pageCount)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            error != null -> AttachmentViewerEmpty(error ?: "Unable to load PDF")
            pageCount <= 0 -> AttachmentViewerEmpty("Loading PDF...")
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultThemeTokens.colors.inset),
                    contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(count = pageCount, key = { it }) { pageIndex ->
                        PdfPageSurface(
                            path = attachment.localPath,
                            pageIndex = pageIndex,
                            zoom = zoom,
                            panOffset = panOffset,
                            highlighterMode = highlighterMode,
                            highlightColor = highlightColor,
                            annotations = annotationsByPage[pageIndex].orEmpty(),
                            onPanChange = { delta ->
                                panOffset = if (zoom <= 1.01f) {
                                    Offset.Zero
                                } else {
                                    val maxX = 420f * (zoom - 1f)
                                    val maxY = 620f * (zoom - 1f)
                                    Offset(
                                        x = (panOffset.x + delta.x).coerceIn(-maxX, maxX),
                                        y = (panOffset.y + delta.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            },
                            onAddHighlight = { left, top, right, bottom ->
                                onAddHighlight(
                                    attachment.libraryFolderId,
                                    pageIndex,
                                    left,
                                    top,
                                    right,
                                    bottom,
                                    highlightColor,
                                )
                            },
                            onAnnotationTap = { selectedAnnotation = it },
                        )
                    }
                }
                PdfReadingProgressOverlay(
                    pageIndex = visiblePage.coerceIn(0, pageCount - 1),
                    pageCount = pageCount,
                )
                PdfZoomControls(
                    zoom = zoom,
                    highlighterMode = highlighterMode,
                    highlightColor = highlightColor,
                    onHighlighterModeChange = { highlighterMode = it },
                    onHighlightColorChange = { highlightColor = it },
                    onZoomIn = { zoom = (zoom + 0.25f).coerceAtMost(2.5f) },
                    onZoomOut = {
                        zoom = (zoom - 0.25f).coerceAtLeast(1f)
                        if (zoom <= 1.01f) panOffset = Offset.Zero
                    },
                    onReset = {
                        zoom = 1f
                        panOffset = Offset.Zero
                    },
                )
            }
        }
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
                onDeleteAnnotation(annotation.id)
                selectedAnnotation = null
            },
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
private fun BoxScope.PdfReadingProgressOverlay(
    pageIndex: Int,
    pageCount: Int,
) {
    val colors = VaultThemeTokens.colors
    val percent = ((pageIndex + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
    AnimatedVisibility(
        visible = pageCount > 0,
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
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
}

@Composable
private fun BoxScope.PdfZoomControls(
    zoom: Float,
    highlighterMode: Boolean,
    highlightColor: String,
    onHighlighterModeChange: (Boolean) -> Unit,
    onHighlightColorChange: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
        color = colors.elevated.copy(alpha = 0.9f),
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = { onHighlighterModeChange(!highlighterMode) }) {
                    Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (highlighterMode) "Done" else "Highlight")
                }
                IconBtn(Icons.Rounded.Remove, "Zoom out", onClick = onZoomOut)
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.textSecondary,
                )
                IconBtn(Icons.Rounded.Add, "Zoom in", onClick = onZoomIn)
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
            }
            AnimatedVisibility(visible = highlighterMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfHighlightColors.forEach { color ->
                        Surface(
                            onClick = { onHighlightColorChange(color) },
                            modifier = Modifier.size(24.dp),
                            shape = VaultShapes.pill,
                            color = color.toPdfHighlightColor().copy(alpha = if (color == highlightColor) 0.9f else 0.42f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (color == highlightColor) colors.textSecondary else colors.border,
                            ),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageSurface(
    path: String,
    pageIndex: Int,
    zoom: Float,
    panOffset: Offset,
    highlighterMode: Boolean,
    highlightColor: String,
    annotations: List<PdfAnnotationEntity>,
    onPanChange: (Offset) -> Unit,
    onAddHighlight: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onAnnotationTap: (PdfAnnotationEntity) -> Unit,
) {
    var renderedPage by remember(path, pageIndex) { mutableStateOf<RenderedPdfPage?>(null) }
    var error by remember(path, pageIndex) { mutableStateOf<String?>(null) }
    var dragStart by remember(path, pageIndex) { mutableStateOf<Offset?>(null) }
    var dragEnd by remember(path, pageIndex) { mutableStateOf<Offset?>(null) }

    LaunchedEffect(path, pageIndex) {
        val result = renderPdfPage(path, pageIndex)
        result.onSuccess { rendered ->
            renderedPage = rendered
            error = null
        }.onFailure {
            renderedPage = null
            error = it.message ?: "Unable to load page"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp)),
            color = Color.White,
            shape = RoundedCornerShape(5.dp),
            shadowElevation = 2.dp,
        ) {
            val loadedPage = renderedPage
            when {
                loadedPage != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(loadedPage.aspectRatio)
                        .clip(RoundedCornerShape(5.dp))
                        .then(
                            if (highlighterMode) {
                                Modifier.pointerInput(path, pageIndex, zoom, panOffset, highlightColor) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            dragStart = offset.toPageSpace(size.width.toFloat(), size.height.toFloat(), zoom, panOffset)
                                            dragEnd = dragStart
                                        },
                                        onDragEnd = {
                                            val start = dragStart
                                            val end = dragEnd
                                            if (start != null && end != null) {
                                                onAddHighlight(start.x, start.y, end.x, end.y)
                                            }
                                            dragStart = null
                                            dragEnd = null
                                        },
                                        onDragCancel = {
                                            dragStart = null
                                            dragEnd = null
                                        },
                                    ) { change, _ ->
                                        change.consume()
                                        dragEnd = change.position.toPageSpace(size.width.toFloat(), size.height.toFloat(), zoom, panOffset)
                                    }
                                }
                            } else if (zoom > 1.01f) {
                                Modifier.pointerInput(path, pageIndex, zoom) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onPanChange(dragAmount)
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (!highlighterMode) {
                                Modifier.pointerInput(path, pageIndex, annotations, zoom, panOffset) {
                                    detectTapGestures { offset ->
                                        val point = offset.toPageSpace(size.width.toFloat(), size.height.toFloat(), zoom, panOffset)
                                        annotations
                                            .lastOrNull { point.x in it.left..it.right && point.y in it.top..it.bottom }
                                            ?.let(onAnnotationTap)
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = if (zoom > 1.01f) panOffset.x else 0f
                                translationY = if (zoom > 1.01f) panOffset.y else 0f
                            },
                    ) {
                        Image(
                            bitmap = loadedPage.bitmap,
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        PdfHighlightOverlay(
                            annotations = annotations,
                            draftStart = dragStart,
                            draftEnd = dragEnd,
                            draftColor = highlightColor,
                        )
                    }
                }
                error != null -> AttachmentViewerEmpty(error ?: "Unable to load page")
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading page ${pageIndex + 1}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultThemeTokens.colors.textMuted,
                    )
                }
            }
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
private fun PdfHighlightOverlay(
    annotations: List<PdfAnnotationEntity>,
    draftStart: Offset?,
    draftEnd: Offset?,
    draftColor: String,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            val color = annotation.color.toPdfHighlightColor()
            val left = annotation.left * size.width
            val top = annotation.top * size.height
            val width = (annotation.right - annotation.left) * size.width
            val height = (annotation.bottom - annotation.top) * size.height
            drawRoundRect(
                color = color.copy(alpha = 0.34f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
            if (!annotation.noteText.isNullOrBlank()) {
                drawCircle(
                    color = color.copy(alpha = 0.88f),
                    radius = 5.dp.toPx(),
                    center = Offset(left + width - 8.dp.toPx(), top + 8.dp.toPx()),
                )
            }
        }
        if (draftStart != null && draftEnd != null) {
            val left = minOf(draftStart.x, draftEnd.x) * size.width
            val top = minOf(draftStart.y, draftEnd.y) * size.height
            val width = kotlin.math.abs(draftEnd.x - draftStart.x) * size.width
            val height = kotlin.math.abs(draftEnd.y - draftStart.y) * size.height
            drawRoundRect(
                color = draftColor.toPdfHighlightColor().copy(alpha = 0.28f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
            drawRoundRect(
                color = draftColor.toPdfHighlightColor().copy(alpha = 0.72f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
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
        title = { Text("Highlight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Page ${annotation.pageIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultThemeTokens.colors.textMuted,
                )
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

private data class RenderedPdfPage(
    val bitmap: ImageBitmap,
    val aspectRatio: Float,
)

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

private suspend fun renderPdfPage(path: String, pageIndex: Int): Result<RenderedPdfPage> = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "PDF file is missing" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val safePage = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                renderer.openPage(safePage).use { page ->
                    val width = 1400
                    val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    RenderedPdfPage(bitmap.asImageBitmap(), width.toFloat() / height.toFloat())
                }
            }
        }
    }
}

private val PdfHighlightColors = listOf("yellow", "blue", "green", "red")

private fun String.toPdfHighlightColor(): Color =
    when (lowercase()) {
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFD84D)
    }

private fun Offset.toPageSpace(width: Float, height: Float, zoom: Float, panOffset: Offset): Offset {
    if (width <= 0f || height <= 0f) return Offset.Zero
    val center = Offset(width / 2f, height / 2f)
    val untransformed = if (zoom > 1.01f) {
        ((this - center - panOffset) / zoom) + center
    } else {
        this
    }
    return Offset(
        x = (untransformed.x / width).coerceIn(0f, 1f),
        y = (untransformed.y / height).coerceIn(0f, 1f),
    )
}

private suspend fun readPdfPageCount(path: String): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "PDF file is missing" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    }
}
