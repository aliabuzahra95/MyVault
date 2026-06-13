package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import com.myvault.app.data.narration.AzureNarrationProgress
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.DocumentTextExtractor
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.util.openAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private class HighlightablePdfContainer(context: Context) : FrameLayout(context) {
    val pdfView: PDFView = PDFView(context, null)
    var highlighterModeEnabled: Boolean = false
    var pdfSurfaceColor: Int = android.graphics.Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                setBackgroundColor(value)
                pdfView.setBackgroundColor(value)
            }
        }
    var onHighlightDragStart: (x: Float, y: Float) -> Unit = { _, _ -> }
    var onHighlightDragMove: (x: Float, y: Float) -> Unit = { _, _ -> }
    var onHighlightDragEnd: () -> Unit = {}

    init {
        addView(
            pdfView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!highlighterModeEnabled) {
            return super.dispatchTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onHighlightDragStart(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onHighlightDragMove(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onHighlightDragMove(event.x, event.y)
                onHighlightDragEnd()
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onHighlightDragEnd()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }
}

@Composable
fun AttachmentViewerScreen(
    attachment: AttachmentEntity?,
    pdfProgress: PdfReadingProgressEntity? = null,
    pdfAnnotations: List<PdfAnnotationEntity> = emptyList(),
    documentText: String = "",
    documentTextLoading: Boolean = false,
    documentTextError: String? = null,
    activeNarrationSentence: String = "",
    azureNarrationProgress: AzureNarrationProgress? = null,
    initialPageIndex: Int? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPdfProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
    onPdfFirstLoaded: () -> Unit = {},
    onAddPdfHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String, onSaved: (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _, callback -> callback(false) },
    onUpdatePdfHighlightColor: (annotationId: String, color: String) -> Unit = { _, _ -> },
    onUpdatePdfAnnotationNote: (annotationId: String, noteText: String) -> Unit = { _, _ -> },
    onDeletePdfAnnotation: (annotationId: String) -> Unit = {},
    onDeleteAttachment: () -> Unit = {},
    onExportAttachment: (Uri) -> Unit = {},
    onAzureListenClick: () -> Unit = {},
    onAzureResumeClick: () -> Unit = {},
    onAzureListenFromHere: (Int) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let(onExportAttachment)
    }

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
                    IconBtn(Icons.Rounded.FileDownload, "Save to device") {
                        exportLauncher.launch(attachment.fileName.ifBlank { "myvault-file" })
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
                    isPdf && initialPageIndex != null -> PdfAttachmentViewer(
                        attachment = attachment,
                        progress = pdfProgress,
                        annotations = pdfAnnotations,
                        initialPageIndex = initialPageIndex,
                        onProgressChanged = onPdfProgressChanged,
                        onFirstLoaded = onPdfFirstLoaded,
                        onAddHighlight = onAddPdfHighlight,
                        onUpdateHighlightColor = onUpdatePdfHighlightColor,
                        onUpdateAnnotationNote = onUpdatePdfAnnotationNote,
                        onDeleteAnnotation = onDeletePdfAnnotation,
                    )
                    isPdf -> AttachmentCanvas {}
                    attachment.mimeType.startsWith("image/") -> ImageAttachmentViewer(attachment)
                    DocumentTextExtractor.isSupported(attachment.fileName, attachment.mimeType) -> DocumentAttachmentViewer(
                        text = documentText,
                        isLoading = documentTextLoading,
                        error = documentTextError,
                        activeSentence = activeNarrationSentence,
                        azureNarrationProgress = azureNarrationProgress,
                        onAzureListenClick = onAzureListenClick,
                        onAzureResumeClick = onAzureResumeClick,
                        onAzureListenFromHere = onAzureListenFromHere,
                    )
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
private fun DocumentAttachmentViewer(
    text: String,
    isLoading: Boolean,
    error: String?,
    activeSentence: String,
    azureNarrationProgress: AzureNarrationProgress?,
    onAzureListenClick: () -> Unit,
    onAzureResumeClick: () -> Unit,
    onAzureListenFromHere: (Int) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var followAudio by remember { mutableStateOf(true) }
    var followAudioPausedUntil by remember { mutableLongStateOf(0L) }
    var selectableText by remember(text) { mutableStateOf(TextFieldValue(text)) }
    val activeStart = activeSentence.takeIf { it.isNotBlank() }?.let(text::indexOf) ?: -1
    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    followAudioPausedUntil = System.currentTimeMillis() + DocumentFollowAudioPauseMs
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(activeSentence, followAudio, followAudioPausedUntil, textLayout) {
        val layout = textLayout ?: return@LaunchedEffect
        if (!followAudio || activeStart < 0 || System.currentTimeMillis() < followAudioPausedUntil) return@LaunchedEffect
        val startBox = layout.getBoundingBox(activeStart)
        val endBox = layout.getBoundingBox((activeStart + activeSentence.length - 1).coerceIn(activeStart, text.lastIndex))
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = minOf(startBox.left, endBox.left),
                top = (minOf(startBox.top, endBox.top) - DocumentFollowAudioPaddingPx).coerceAtLeast(0f),
                right = maxOf(startBox.right, endBox.right),
                bottom = maxOf(startBox.bottom, endBox.bottom) + DocumentFollowAudioPaddingPx,
            ),
        )
    }

    AttachmentCanvas {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.padding(top = 48.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            error != null -> AttachmentViewerEmpty(error)
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .nestedScroll(userScrollConnection),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
            ) {
                Button(
                    onClick = onAzureListenClick,
                    enabled = text.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Listen with Azure",
                        modifier = Modifier.padding(start = VaultSpacing.xs),
                    )
                }
                azureNarrationProgress?.takeIf { it.positionMs >= 5_000L }?.let { progress ->
                    TextButton(
                        onClick = onAzureResumeClick,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Continue from ${progress.positionMs.toDocumentPlaybackTime()}")
                    }
                }
                if (!selectableText.selection.collapsed) {
                    TextButton(
                        onClick = {
                            onAzureListenFromHere(minOf(selectableText.selection.start, selectableText.selection.end))
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Listen from here", modifier = Modifier.padding(start = VaultSpacing.xs))
                    }
                }
                if (activeSentence.isNotBlank()) {
                    TextButton(
                        onClick = { followAudio = !followAudio },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (followAudio) "Follow audio: On" else "Follow audio: Off")
                    }
                }
                BasicTextField(
                    value = selectableText,
                    onValueChange = { value -> selectableText = value.copy(text = text) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    visualTransformation = remember(text, activeSentence, colors) {
                        VisualTransformation { value ->
                            val displayedText = if (activeStart < 0) {
                                AnnotatedString(value.text)
                            } else {
                                AnnotatedString.Builder(value.text).apply {
                                    addStyle(
                                        SpanStyle(background = colors.accent.copy(alpha = 0.38f), color = colors.text),
                                        activeStart,
                                        (activeStart + activeSentence.length).coerceAtMost(value.text.length),
                                    )
                                }.toAnnotatedString()
                            }
                            TransformedText(displayedText, OffsetMapping.Identity)
                        }
                    },
                    onTextLayout = { textLayout = it },
                )
            }
        }
    }
}

private const val DocumentFollowAudioPauseMs = 5_000L
private const val DocumentFollowAudioPaddingPx = 48f

private fun Long.toDocumentPlaybackTime(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
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
    onFirstLoaded: () -> Unit,
    onAddHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String, onSaved: (Boolean) -> Unit) -> Unit,
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
    var pdfReady by remember(attachment.localPath) { mutableStateOf(false) }
    var dragStart by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var dragEnd by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var highlightSaveMessage by remember(attachment.id) { mutableStateOf<String?>(null) }
    val annotationsByPage = remember(annotations) {
        annotations.groupBy { it.pageIndex }
    }
    val currentAnnotationsByPageState = rememberUpdatedState(annotationsByPage)
    val surfaceColorArgb = VaultThemeTokens.colors.inset.toArgb()
    val context = LocalContext.current
    val previewBitmap by produceState<Bitmap?>(null, attachment.id, attachment.localPath) {
        value = withContext(Dispatchers.IO) {
            loadOrCreatePdfFirstPagePreview(
                context = context,
                attachmentId = attachment.id,
                localPath = attachment.localPath,
            )
        }
    }
    val previewAlpha by animateFloatAsState(
        targetValue = if (pdfReady) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pdfFirstPagePreviewAlpha",
    )

    fun finishHighlightDrag() {
        val view = pdfView
        val startOffset = dragStart
        val endOffset = dragEnd
        if (view != null && startOffset != null && endOffset != null) {
            val start = view.toNormalizedPagePoint(startOffset.x, startOffset.y, clampToPage = false)
            val end = view.toNormalizedPagePoint(endOffset.x, endOffset.y, clampToPage = false)
            val pageIndex = start?.pageIndex ?: end?.pageIndex ?: view.findFocusPage(
                view.currentXOffset,
                view.currentYOffset,
            )
            if (pageIndex >= 0) {
                val normalizedStart = start?.takeIf { it.pageIndex == pageIndex }
                    ?: view.toNormalizedPagePoint(
                        x = startOffset.x,
                        y = startOffset.y,
                        preferredPage = pageIndex,
                        clampToPage = true,
                    )
                val normalizedEnd = end?.takeIf { it.pageIndex == pageIndex }
                    ?: view.toNormalizedPagePoint(
                        x = endOffset.x,
                        y = endOffset.y,
                        preferredPage = pageIndex,
                        clampToPage = true,
                    )

                if (normalizedStart == null || normalizedEnd == null) {
                    highlightSaveMessage = "Highlight not saved"
                    dragStart = null
                    dragEnd = null
                    return
                }

                val startPoint = normalizedStart.offset
                val endPoint = normalizedEnd.offset
                val rect = expandPdfHighlightRect(
                    left = minOf(startPoint.x, endPoint.x),
                    top = minOf(startPoint.y, endPoint.y),
                    right = maxOf(startPoint.x, endPoint.x),
                    bottom = maxOf(startPoint.y, endPoint.y),
                )
                if (rect != null) {
                    onAddHighlight(
                        attachment.libraryFolderId,
                        pageIndex,
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        highlightColor,
                    ) { saved ->
                        highlightSaveMessage = if (saved) "Highlight saved" else "Highlight not saved"
                        if (saved) view.invalidate()
                    }
                } else {
                    highlightSaveMessage = "Highlight not saved"
                }
            } else {
                highlightSaveMessage = "Highlight not saved"
            }
        } else {
            highlightSaveMessage = "Highlight not saved"
        }
        dragStart = null
        dragEnd = null
    }

    LaunchedEffect(highlightSaveMessage) {
        if (highlightSaveMessage != null) {
            delay(1400)
            highlightSaveMessage = null
        }
    }

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
                            HighlightablePdfContainer(context).apply {
                                val pdf = this.pdfView
                                pdfView = pdf
                                pdfSurfaceColor = surfaceColorArgb
                                setBackgroundColor(surfaceColorArgb)
                                onHighlightDragStart = { x, y ->
                                    val offset = Offset(x, y)
                                    dragStart = offset
                                    dragEnd = offset
                                }
                                onHighlightDragMove = { x, y ->
                                    dragEnd = Offset(x, y)
                                }
                                onHighlightDragEnd = {
                                    finishHighlightDrag()
                                }
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                pdf.setMinZoom(1f)
                                pdf.setMidZoom(2.25f)
                                pdf.setMaxZoom(5f)
                                pdf.fromFile(file)
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
                                            pdf.jumpTo(targetPage, false)
                                            onProgressChanged(targetPage, totalPages)
                                            pdfReady = true
                                            onFirstLoaded()
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
                                            val pageAnnotations = currentAnnotationsByPageState.value[currentPage].orEmpty()
                                            if (pageAnnotations.isEmpty()) return
                                            drawPdfHighlights(
                                                canvas = canvas,
                                                pdfView = pdf,
                                                pageIndex = currentPage,
                                                pageWidth = pageWidth,
                                                pageHeight = pageHeight,
                                                annotations = pageAnnotations,
                                            )
                                        }
                                    })
                                    .onTap(object : OnTapListener {
                                        override fun onTap(e: MotionEvent?): Boolean {
                                            if (e == null) return false
                                            if (highlighterMode) return true
                                            val annotationsOnPage = currentAnnotationsByPageState.value
                                            if (annotationsOnPage.isEmpty()) return false
                                            val point = pdf.toNormalizedPagePoint(e.x, e.y, clampToPage = false) ?: return false
                                            val pageAnnotations = annotationsOnPage[point.pageIndex].orEmpty()
                                            if (pageAnnotations.isEmpty()) return false
                                            val pageSize = pdf.pdfFile?.getScaledPageSize(point.pageIndex, pdf.zoom) ?: return false
                                            val annotation = pageAnnotations.findPdfAnnotationAt(
                                                point = point.offset,
                                                pageWidth = pageSize.width,
                                                pageHeight = pageSize.height,
                                            )
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
                                            pdfReady = false
                                            error = t?.message ?: "Unable to load PDF"
                                        }
                                    })
                                    .onPageError(object : OnPageErrorListener {
                                        override fun onPageError(page: Int, t: Throwable?) {
                                            pdfReady = false
                                            error = "Unable to load page ${page + 1}: ${t?.message ?: "unknown error"}"
                                        }
                                    })
                                    .load()
                            }
                        },
                        update = { view ->
                            if (pdfView !== view.pdfView) {
                                pdfView = view.pdfView
                            }
                            view.highlighterModeEnabled = highlighterMode
                            view.pdfSurfaceColor = surfaceColorArgb
                            view.onHighlightDragStart = { x, y ->
                                val offset = Offset(x, y)
                                dragStart = offset
                                dragEnd = offset
                            }
                            view.onHighlightDragMove = { x, y ->
                                dragEnd = Offset(x, y)
                            }
                            view.onHighlightDragEnd = {
                                finishHighlightDrag()
                            }
                        },
                        onRelease = { view ->
                            if (pdfView === view.pdfView) pdfView = null
                            view.pdfView.recycle()
                        },
                    )
                }

                if (highlighterMode) {
                    PdfHighlightDraftLayer(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f),
                        draftStart = dragStart,
                        draftEnd = dragEnd,
                        draftColor = highlightColor,
                    )
                }

                val cachedPreview = previewBitmap
                if (cachedPreview != null && previewAlpha > 0.01f) {
                    Image(
                        bitmap = cachedPreview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VaultThemeTokens.colors.inset)
                            .graphicsLayer { alpha = previewAlpha }
                            .zIndex(1f),
                        contentScale = ContentScale.Fit,
                    )
                }

                if (!pdfReady) {
                    if (cachedPreview == null) {
                        AttachmentViewerEmpty("Loading PDF...")
                    }
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
                    },
                    onHighlightColorChange = { highlightColor = it },
                )

                highlightSaveMessage?.let { message ->
                    PdfHighlightSaveMessage(message = message)
                }

                selectedAnnotation?.let { annotation ->
                    PdfAnnotationActionsCard(
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
            }
        }
    }

    LaunchedEffect(annotations) {
        pdfView?.invalidate()
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
            shape = VaultShapes.xxl,
            tonalElevation = 0.dp,
        )
    }

    noteDialogAnnotation?.let { annotation ->
        AlertDialog(
            onDismissRequest = { noteDialogAnnotation = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = VaultThemeTokens.colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, VaultThemeTokens.colors.accentBorder),
                    ) {
                        Icon(
                            Icons.Rounded.StickyNote2,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            tint = VaultThemeTokens.colors.accent,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Annotation note")
                        Text(
                            "Page ${annotation.pageIndex + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = VaultThemeTokens.colors.textMuted,
                        )
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    minLines = 4,
                    shape = VaultShapes.lg,
                    label = { Text("Quick note") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                PremiumPdfDialogButton(
                    label = "Save",
                    filled = true,
                    onClick = {
                        onUpdateAnnotationNote(annotation.id, noteDraft)
                        noteDialogAnnotation = null
                    },
                )
            },
            dismissButton = {
                PremiumPdfDialogButton(label = "Cancel", onClick = { noteDialogAnnotation = null })
            },
            containerColor = VaultThemeTokens.colors.elevated,
            shape = VaultShapes.xxl,
            tonalElevation = 0.dp,
        )
    }
}

@Composable
private fun PdfHighlightDraftLayer(
    modifier: Modifier,
    draftStart: Offset?,
    draftEnd: Offset?,
    draftColor: String,
) {
    Canvas(modifier = modifier) {
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
private fun BoxScope.PdfHighlightSaveMessage(message: String) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(4f)
            .padding(bottom = 82.dp),
        color = VaultThemeTokens.colors.elevated.copy(alpha = 0.96f),
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, VaultThemeTokens.colors.border),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = if (message == "Highlight saved") VaultThemeTokens.colors.accent else VaultThemeTokens.colors.textSecondary,
        )
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
        color = colors.elevated.copy(alpha = 0.98f),
        shape = VaultShapes.xxl,
        border = BorderStroke(1.dp, if (highlighterMode) colors.accent.copy(alpha = 0.55f) else colors.border),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                    modifier = Modifier.size(32.dp),
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
private fun BoxScope.PdfAnnotationActionsCard(
    annotation: PdfAnnotationEntity,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onAddNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(5f)
            .padding(start = VaultSpacing.screen, end = VaultSpacing.screen, bottom = 92.dp)
            .widthIn(max = 430.dp)
            .fillMaxWidth(),
        color = colors.elevated.copy(alpha = 0.98f),
        shape = VaultShapes.xxl,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = colors.accentSoft,
                    shape = VaultShapes.md,
                    border = BorderStroke(1.dp, colors.accentBorder),
                ) {
                    Icon(
                        Icons.Rounded.Draw,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                        tint = colors.accent,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (annotation.noteText.isNullOrBlank()) "Highlight" else "Annotation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "Page ${annotation.pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }

            if (!annotation.noteText.isNullOrBlank()) {
                Surface(
                    color = colors.surface,
                    shape = VaultShapes.lg,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Text(
                        text = annotation.noteText.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PdfHighlightColors.forEach { color ->
                    val selected = annotation.color == color
                    Surface(
                        onClick = { onColorSelected(color) },
                        modifier = Modifier.size(34.dp),
                        shape = VaultShapes.pill,
                        color = color.toPdfHighlightColor().copy(alpha = if (selected) 0.94f else 0.56f),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) colors.text else colors.border,
                        ),
                    ) {}
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumPdfActionButton(
                    label = "Delete",
                    icon = Icons.Rounded.Delete,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
                PremiumPdfActionButton(
                    label = "Close",
                    icon = Icons.Rounded.Close,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                PremiumPdfActionButton(
                    label = if (annotation.noteText.isNullOrBlank()) "Add note" else "Edit note",
                    icon = Icons.Rounded.StickyNote2,
                    filled = true,
                    modifier = Modifier.weight(1.25f),
                    onClick = onAddNote,
                )
            }
        }
    }
}

@Composable
private fun PremiumPdfActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val accentColor = when {
        destructive -> colors.warning
        filled -> colors.accent
        else -> colors.textSecondary
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = VaultShapes.pill,
        color = if (filled) colors.accent else colors.surface,
        border = BorderStroke(1.dp, if (filled) colors.accentBorder else colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (filled) Color.White else accentColor,
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
                color = if (filled) Color.White else accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PremiumPdfDialogButton(
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = VaultShapes.pill,
        color = if (filled) colors.accent else colors.surface,
        border = BorderStroke(1.dp, if (filled) colors.accentBorder else colors.border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
            color = if (filled) Color.White else colors.textSecondary,
        )
    }
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

private fun loadOrCreatePdfFirstPagePreview(
    context: Context,
    attachmentId: String,
    localPath: String,
): Bitmap? = runCatching {
    val source = File(localPath)
    if (!source.exists() || !source.isFile) return@runCatching null

    val previewDir = File(context.cacheDir, "pdf_first_page_previews").apply { mkdirs() }
    val previewFile = File(
        previewDir,
        "${attachmentId}_${source.length()}_${source.lastModified()}.png",
    )
    if (previewFile.exists() && previewFile.length() > 0L) {
        BitmapFactory.decodeFile(previewFile.absolutePath)?.let { return@runCatching it }
    }

    previewDir.listFiles()
        ?.filter { it.name.startsWith("${attachmentId}_") && it.name != previewFile.name }
        ?.forEach { it.delete() }

    val bitmap = renderPdfFirstPagePreview(source) ?: return@runCatching null
    previewFile.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
    }
    bitmap
}.getOrNull()

private fun renderPdfFirstPagePreview(file: File): Bitmap? {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use {
        PdfRenderer(it).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val width = 720
                val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
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

private data class PdfHighlightRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private data class PdfAnnotationMarkerBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tailPointsLeft: Boolean,
    val outsideHighlight: Boolean,
)

private fun expandPdfHighlightRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): PdfHighlightRect? {
    val width = (right - left).coerceAtLeast(0f)
    val height = (bottom - top).coerceAtLeast(0f)
    if (width < 0.003f && height < 0.003f) return null

    val expandedWidth = width.coerceAtLeast(0.006f)
    val expandedHeight = height.coerceAtLeast(0.012f)
    val centerX = ((left + right) / 2f).coerceIn(0f, 1f)
    val centerY = ((top + bottom) / 2f).coerceIn(0f, 1f)
    val expandedLeft = (centerX - expandedWidth / 2f).coerceIn(0f, 1f)
    val expandedTop = (centerY - expandedHeight / 2f).coerceIn(0f, 1f)
    val expandedRight = (expandedLeft + expandedWidth).coerceIn(0f, 1f)
    val expandedBottom = (expandedTop + expandedHeight).coerceIn(0f, 1f)
    return PdfHighlightRect(
        left = (expandedRight - expandedWidth).coerceAtLeast(0f).takeIf { expandedRight == 1f } ?: expandedLeft,
        top = (expandedBottom - expandedHeight).coerceAtLeast(0f).takeIf { expandedBottom == 1f } ?: expandedTop,
        right = expandedRight,
        bottom = expandedBottom,
    )
}

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

private fun List<PdfAnnotationEntity>.findPdfAnnotationAt(
    point: Offset,
    pageWidth: Float,
    pageHeight: Float,
): PdfAnnotationEntity? =
    asReversed().firstOrNull { annotation ->
        val inHighlight = point.x in annotation.left..annotation.right && point.y in annotation.top..annotation.bottom
        val inMarker = annotation.noteText?.isNotBlank() == true &&
            annotation.markerBounds(pageWidth, pageHeight)?.let { marker ->
                point.x in marker.left..marker.right && point.y in marker.top..marker.bottom
            } == true
        inHighlight || inMarker
    }

private fun PdfAnnotationEntity.markerBounds(
    pageWidth: Float,
    pageHeight: Float,
): PdfAnnotationMarkerBounds? {
    if (pageWidth <= 0f || pageHeight <= 0f || noteText.isNullOrBlank()) return null
    val leftPx = left * pageWidth
    val topPx = top * pageHeight
    val rightPx = right * pageWidth
    val bottomPx = bottom * pageHeight
    val markerWidth = PDF_ANNOTATION_MARKER_WIDTH
    val markerHeight = PDF_ANNOTATION_MARKER_HEIGHT
    val gap = 9f
    val verticalCenter = ((topPx + bottomPx) / 2f) - markerHeight / 2f
    val markerTopPx = verticalCenter.coerceIn(6f, (pageHeight - markerHeight - 6f).coerceAtLeast(6f))
    val rightSideLeft = rightPx + gap
    val leftSideLeft = leftPx - markerWidth - gap
    val canPlaceRight = rightSideLeft + markerWidth <= pageWidth - 6f
    val canPlaceLeft = leftSideLeft >= 6f

    val markerLeftPx = when {
        canPlaceRight -> rightSideLeft
        canPlaceLeft -> leftSideLeft
        else -> (rightPx - markerWidth - 6f).coerceIn(6f, (pageWidth - markerWidth - 6f).coerceAtLeast(6f))
    }
    return PdfAnnotationMarkerBounds(
        left = (markerLeftPx / pageWidth).coerceIn(0f, 1f),
        top = (markerTopPx / pageHeight).coerceIn(0f, 1f),
        right = ((markerLeftPx + markerWidth) / pageWidth).coerceIn(0f, 1f),
        bottom = ((markerTopPx + markerHeight) / pageHeight).coerceIn(0f, 1f),
        tailPointsLeft = canPlaceRight || !canPlaceLeft,
        outsideHighlight = canPlaceRight || canPlaceLeft,
    )
}

private fun drawPdfHighlights(
    canvas: android.graphics.Canvas,
    pdfView: PDFView,
    pageIndex: Int,
    pageWidth: Float,
    pageHeight: Float,
    annotations: List<PdfAnnotationEntity>,
) {
    if (pageWidth <= 0f || pageHeight <= 0f || annotations.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pageInset = pdfView.pdfFile?.getSecondaryPageOffset(pageIndex, pdfView.zoom) ?: 0f
    canvas.save()
    if (pdfView.isSwipeVertical) {
        canvas.translate(pdfView.currentXOffset + pageInset, pdfView.currentYOffset)
    } else {
        canvas.translate(pdfView.currentXOffset, pdfView.currentYOffset + pageInset)
    }
    annotations.forEach { annotation ->
        val left = annotation.left * pageWidth
        val top = annotation.top * pageHeight
        val right = annotation.right * pageWidth
        val bottom = annotation.bottom * pageHeight
        paint.style = Paint.Style.FILL
        paint.color = annotation.color.toPdfHighlightArgb(alpha = 86)
        canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, paint)
        drawPdfAnnotationMarker(canvas, paint, annotation, pageWidth, pageHeight)
    }
    canvas.restore()
}

private fun drawPdfAnnotationMarker(
    canvas: android.graphics.Canvas,
    paint: Paint,
    annotation: PdfAnnotationEntity,
    pageWidth: Float,
    pageHeight: Float,
) {
    val marker = annotation.markerBounds(pageWidth, pageHeight) ?: return
    val markerLeft = marker.left * pageWidth
    val markerTop = marker.top * pageHeight
    val markerRight = marker.right * pageWidth
    val markerBottom = marker.bottom * pageHeight
    val radius = 16f

    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.argb(238, 0, 0, 0)
    canvas.drawRoundRect(markerLeft, markerTop, markerRight, markerBottom, radius, radius, paint)

    if (marker.outsideHighlight) {
        val tail = Path()
        val tailCenterY = markerTop + PDF_ANNOTATION_MARKER_HEIGHT * 0.64f
        if (marker.tailPointsLeft) {
            tail.moveTo(markerLeft + 2f, tailCenterY - 8f)
            tail.lineTo(markerLeft - 13f, tailCenterY + 1f)
            tail.lineTo(markerLeft + 2f, tailCenterY + 11f)
        } else {
            tail.moveTo(markerRight - 2f, tailCenterY - 8f)
            tail.lineTo(markerRight + 13f, tailCenterY + 1f)
            tail.lineTo(markerRight - 2f, tailCenterY + 11f)
        }
        tail.close()
        canvas.drawPath(tail, paint)
    }

    paint.color = android.graphics.Color.argb(238, 255, 255, 255)
    canvas.drawRoundRect(markerLeft + 14f, markerTop + 13f, markerRight - 14f, markerTop + 18f, 2.5f, 2.5f, paint)
    canvas.drawRoundRect(markerLeft + 14f, markerTop + 27f, markerRight - 14f, markerTop + 32f, 2.5f, 2.5f, paint)
    canvas.drawRoundRect(markerLeft + 14f, markerTop + 41f, markerLeft + 49f, markerTop + 46f, 2.5f, 2.5f, paint)
}

private const val PDF_ANNOTATION_MARKER_WIDTH = 74f
private const val PDF_ANNOTATION_MARKER_HEIGHT = 58f

private fun String.toPdfHighlightArgb(alpha: Int): Int {
    val color = toPdfHighlightColor()
    return android.graphics.Color.argb(
        alpha.coerceIn(0, 255),
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )
}
