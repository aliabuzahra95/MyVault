package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.TextFields
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.viewer.fragment.PdfViewerFragment
import androidx.pdf.view.Highlight
import androidx.pdf.view.PdfView as AndroidxPdfView
import com.myvault.app.BuildConfig
import com.myvault.app.R
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.isCurrentPdfAnnotation
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
import java.io.FileOutputStream

@OptIn(ExperimentalPdfApi::class)
class VaultPdfViewerFragment : PdfViewerFragment() {
    override fun onResume() {
        super.onResume()
        hideMyVaultUnsupportedToolbox()
    }

    override fun onPdfViewCreated(pdfView: AndroidxPdfView) {
        super.onPdfViewCreated(pdfView)
        hideMyVaultUnsupportedToolbox()
        PdfViewerCallbackRegistry.onPdfViewReady?.invoke(pdfView)
    }

    private fun hideMyVaultUnsupportedToolbox(scheduleFollowUps: Boolean = true) {
        runCatching { isToolboxVisible = false }
        runCatching {
            toolboxView.visibility = View.GONE
            toolboxView.isEnabled = false
            toolboxView.alpha = 0f
        }
        view?.hideAndroidxPdfToolboxDescendants()
        if (scheduleFollowUps) {
            view?.postDelayed({ hideMyVaultUnsupportedToolbox(scheduleFollowUps = false) }, 80L)
            view?.postDelayed({ hideMyVaultUnsupportedToolbox(scheduleFollowUps = false) }, 240L)
        }
    }

    override fun onLoadDocumentError(throwable: Throwable) {
        super.onLoadDocumentError(throwable)
        PdfViewerCallbackRegistry.onPdfLoadError?.invoke(throwable)
    }
}

private object PdfViewerCallbackRegistry {
    var onPdfViewReady: ((AndroidxPdfView) -> Unit)? = null
    var onPdfLoadError: ((Throwable) -> Unit)? = null
}


private const val NativePdfAnnotationOverlayTag = "myvault_pdf_annotation_overlay"

private class NativePdfAnnotationOverlayView(context: Context) : View(context) {
    var pdfView: AndroidxPdfView? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }
    var pageCount: Int = 0
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }
    var annotations: List<PdfAnnotationEntity> = emptyList()
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }
    var highlightColor: String = "yellow"
    var drawMode: Boolean = false
        set(value) {
            field = value
            resetGestureState()
            postInvalidateOnAnimation()
        }
    var textMode: Boolean = false
        set(value) {
            field = value
            resetGestureState()
            postInvalidateOnAnimation()
        }
    var selectMode: Boolean = false
        set(value) {
            field = value
            resetGestureState()
            postInvalidateOnAnimation()
        }

    var onCreateTextBox: (PdfRect) -> Unit = {}
    var onUpdateTextBoxBounds: (PdfAnnotationEntity, PdfRect) -> Unit = { _, _ -> }
    var onSelectAnnotation: (PdfAnnotationEntity) -> Unit = {}
    var onAddHighlight: (PdfRect, String) -> Unit = { _, _ -> }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tempOverlayLocation = IntArray(2)
    private val tempPdfLocation = IntArray(2)
    private var dragStart: android.graphics.PointF? = null
    private var dragEnd: android.graphics.PointF? = null
    private var textDragState: NativeTextDragState? = null

    init {
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawMode && !textMode && !selectMode) return false
        val x = event.x
        val y = event.y
        when {
            drawMode -> return handleDrawTouch(event, x, y)
            textMode -> return handleTextTouch(event, x, y)
            selectMode -> return handleSelectTouch(event, x, y)
        }
        return false
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (!drawMode) {
            annotations
                .asSequence()
                .filter { it.annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT }
                .forEach { annotation ->
                    val bounds = annotation.toNativeOverlayBounds() ?: return@forEach
                    fillPaint.style = Paint.Style.FILL
                    fillPaint.color = annotation.color.toPdfHighlightPreviewArgb()
                    canvas.drawRoundRect(bounds, 8f, 8f, fillPaint)
                }
        }
        val start = dragStart
        val end = dragEnd
        if (drawMode && start != null && end != null) {
            val preview = RectF(
                minOf(start.x, end.x),
                minOf(start.y, end.y),
                maxOf(start.x, end.x),
                maxOf(start.y, end.y),
            )
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = highlightColor.toPdfHighlightPreviewArgb()
            canvas.drawRoundRect(preview, 8f, 8f, fillPaint)
        }
    }

    private fun drawTextBox(canvas: android.graphics.Canvas, annotation: PdfAnnotationEntity, bounds: RectF) {
        val measured = estimatePdfTextBoxViewSize(annotation.noteText.orEmpty(), annotation.textSize)
        if (bounds.width() < measured.x) bounds.right = bounds.left + measured.x
        if (bounds.height() < measured.y) bounds.bottom = bounds.top + measured.y

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = annotation.backgroundColor.toPdfTextBoxBackgroundArgb()
        canvas.drawRoundRect(bounds, 12f, 12f, fillPaint)

        borderPaint.color = annotation.color.toPdfTextBoxBorderArgb()
        canvas.drawRoundRect(bounds, 12f, 12f, borderPaint)

        val text = annotation.noteText.orEmpty()
        if (text.isBlank()) return
        val padding = 10f
        val textWidth = (bounds.width() - padding * 2f).toInt().coerceAtLeast(1)
        textPaint.color = annotation.color.toPdfTextArgb()
        textPaint.textSize = annotation.textSize.coerceIn(10f, 36f) * resources.displayMetrics.scaledDensity
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.05f)
            .build()
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left + padding, bounds.top + padding)
        layout.draw(canvas)
        canvas.restore()

        if (textMode) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = annotation.color.toPdfTextArgb()
            canvas.drawCircle(bounds.right - 9f, bounds.bottom - 9f, 7f, fillPaint)
        }
    }

    private fun handleDrawTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragStart = android.graphics.PointF(x, y)
                dragEnd = android.graphics.PointF(x, y)
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragEnd = android.graphics.PointF(x, y)
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragEnd = android.graphics.PointF(x, y)
                val start = dragStart
                val end = dragEnd
                parent?.requestDisallowInterceptTouchEvent(false)
                if (start != null && end != null) {
                    val rect = overlayRectToPdfRect(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)))
                    if (rect != null) onAddHighlight(rect, highlightColor)
                }
                resetGestureState()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                resetGestureState()
                return true
            }
        }
        return true
    }

    private fun handleTextTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val target = annotationAt(x, y)?.takeIf { it.annotationType == PdfAnnotationEntity.TYPE_TEXT_BOX }
                if (target != null) {
                    val bounds = target.toNativeOverlayBounds() ?: return true
                    val resize = x >= bounds.right - PdfTextBoxResizeHandlePx && y >= bounds.bottom - PdfTextBoxResizeHandlePx
                    textDragState = NativeTextDragState(target, bounds, x, y, resize)
                } else {
                    textDragState = NativeTextDragState(null, RectF(x, y, x, y), x, y, false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val state = textDragState ?: return true
                val dx = x - state.lastX
                val dy = y - state.lastY
                if (state.annotation != null) {
                    if (state.resize) {
                        state.bounds.right = (state.bounds.right + dx).coerceAtLeast(state.bounds.left + PdfTextBoxMinWidthPx)
                        state.bounds.bottom = (state.bounds.bottom + dy).coerceAtLeast(state.bounds.top + PdfTextBoxMinHeightPx)
                    } else {
                        state.bounds.offset(dx, dy)
                    }
                    state.lastX = x
                    state.lastY = y
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val state = textDragState
                if (state?.annotation != null) {
                    overlayRectToPdfRect(state.bounds, state.annotation.pageIndex)?.let { rect ->
                        onUpdateTextBoxBounds(state.annotation, rect)
                    }
                } else {
                    overlayPointToPdfPoint(x, y)?.let { point ->
                        val size = estimatePdfTextBoxViewSize("", 18f)
                        overlayRectToPdfRect(RectF(x, y, x + size.x, y + size.y), point.pageNum)?.let { rect ->
                            onCreateTextBox(rect)
                        }
                    }
                }
                textDragState = null
                performClick()
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                textDragState = null
                postInvalidateOnAnimation()
                return true
            }
        }
        return true
    }

    private fun handleSelectTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            annotationAt(x, y)?.let(onSelectAnnotation)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun resetGestureState() {
        dragStart = null
        dragEnd = null
        textDragState = null
    }

    private fun annotationAt(x: Float, y: Float): PdfAnnotationEntity? =
        annotations.asReversed().firstOrNull { annotation ->
            if (annotation.annotationType != PdfAnnotationEntity.TYPE_HIGHLIGHT) return@firstOrNull false
            val bounds = annotation.toNativeOverlayBounds() ?: return@firstOrNull false
            x in bounds.left..bounds.right && y in bounds.top..bounds.bottom
        }

    private fun PdfAnnotationEntity.toNativeOverlayBounds(): RectF? {
        if (pageIndex !in 0 until pageCount) return null
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
        if (right <= left || bottom <= top) return null
        val view = pdfView ?: return null
        val topLeft = runCatching { view.pdfToViewPoint(PdfPoint(pageIndex, left, top)) }.getOrNull() ?: return null
        val bottomRight = runCatching { view.pdfToViewPoint(PdfPoint(pageIndex, right, bottom)) }.getOrNull() ?: return null
        val leftTop = pdfViewPointToOverlay(topLeft.x, topLeft.y)
        val rightBottom = pdfViewPointToOverlay(bottomRight.x, bottomRight.y)
        return RectF(
            minOf(leftTop.x, rightBottom.x),
            minOf(leftTop.y, rightBottom.y),
            maxOf(leftTop.x, rightBottom.x),
            maxOf(leftTop.y, rightBottom.y),
        ).takeIf { it.width() >= 4f && it.height() >= 4f }
    }

    private fun overlayRectToPdfRect(rect: RectF, fallbackPage: Int? = null): PdfRect? {
        val topLeft = overlayPointToPdfPoint(rect.left, rect.top, fallbackPage) ?: return null
        val bottomRight = overlayPointToPdfPoint(rect.right, rect.bottom, topLeft.pageNum) ?: return null
        if (topLeft.pageNum != bottomRight.pageNum) return null
        return pdfRectFromDrag(topLeft, bottomRight)
    }

    private fun overlayPointToPdfPoint(x: Float, y: Float, preferredPage: Int? = null): PdfPoint? {
        val viewPoint = overlayPointToPdfViewPoint(x, y)
        val direct = runCatching { pdfView?.viewToPdfPoint(viewPoint.x, viewPoint.y) }.getOrNull()
        if (direct != null && (preferredPage == null || direct.pageNum == preferredPage)) return direct
        return direct
    }

    private fun overlayPointToPdfViewPoint(x: Float, y: Float): android.graphics.PointF {
        val view = pdfView ?: return android.graphics.PointF(x, y)
        getLocationInWindow(tempOverlayLocation)
        view.getLocationInWindow(tempPdfLocation)
        return android.graphics.PointF(
            x + tempOverlayLocation[0] - tempPdfLocation[0],
            y + tempOverlayLocation[1] - tempPdfLocation[1],
        )
    }

    private fun pdfViewPointToOverlay(x: Float, y: Float): android.graphics.PointF {
        val view = pdfView ?: return android.graphics.PointF(x, y)
        getLocationInWindow(tempOverlayLocation)
        view.getLocationInWindow(tempPdfLocation)
        return android.graphics.PointF(
            x + tempPdfLocation[0] - tempOverlayLocation[0],
            y + tempPdfLocation[1] - tempOverlayLocation[1],
        )
    }

    private data class NativeTextDragState(
        val annotation: PdfAnnotationEntity?,
        val bounds: RectF,
        var lastX: Float,
        var lastY: Float,
        val resize: Boolean,
    )
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
    onAddPdfPageNote: (libraryFolderId: String?, pageIndex: Int, noteText: String, onSaved: (Boolean) -> Unit) -> Unit = { _, _, _, callback -> callback(false) },
    onAddPdfTextBox: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, text: String, color: String, textSize: Float, backgroundColor: String, onSaved: (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _, _, _, _, callback -> callback(false) },
    onUpdatePdfTextBox: (annotationId: String, text: String, color: String, textSize: Float, backgroundColor: String) -> Unit = { _, _, _, _, _ -> },
    onUpdatePdfTextBoxBounds: (annotationId: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
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
                    isPdf -> PdfAttachmentViewer(
                        attachment = attachment,
                        progress = pdfProgress,
                        annotations = pdfAnnotations,
                        initialPageIndex = initialPageIndex,
                        onProgressChanged = onPdfProgressChanged,
                        onFirstLoaded = onPdfFirstLoaded,
                        onAddHighlight = onAddPdfHighlight,
                        onUpdateHighlightColor = onUpdatePdfHighlightColor,
                        onUpdateAnnotationNote = onUpdatePdfAnnotationNote,
                        onAddPageNote = onAddPdfPageNote,
                        onAddTextBox = onAddPdfTextBox,
                        onUpdateTextBox = onUpdatePdfTextBox,
                        onUpdateTextBoxBounds = onUpdatePdfTextBoxBounds,
                        onDeleteAnnotation = onDeletePdfAnnotation,
                    )
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
    initialPageIndex: Int?,
    onProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit,
    onFirstLoaded: () -> Unit,
    onAddHighlight: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, color: String, onSaved: (Boolean) -> Unit) -> Unit,
    onUpdateHighlightColor: (annotationId: String, color: String) -> Unit,
    onUpdateAnnotationNote: (annotationId: String, noteText: String) -> Unit,
    onAddPageNote: (libraryFolderId: String?, pageIndex: Int, noteText: String, onSaved: (Boolean) -> Unit) -> Unit,
    onAddTextBox: (libraryFolderId: String?, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, text: String, color: String, textSize: Float, backgroundColor: String, onSaved: (Boolean) -> Unit) -> Unit,
    onUpdateTextBox: (annotationId: String, text: String, color: String, textSize: Float, backgroundColor: String) -> Unit,
    onUpdateTextBoxBounds: (annotationId: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onDeleteAnnotation: (annotationId: String) -> Unit,
) {
    var pageCount by remember(attachment.localPath) { mutableIntStateOf(progress?.pageCount ?: 0) }
    var pageIndex by remember(attachment.id) {
        mutableIntStateOf(initialPageIndex?.takeIf { it >= 0 } ?: progress?.pageIndex?.coerceAtLeast(0) ?: 0)
    }
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }
    var highlightColor by remember(attachment.id) { mutableStateOf("yellow") }
    var selectedAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var annotationDeleteRequest by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var noteDialogAnnotation by remember { mutableStateOf<PdfAnnotationEntity?>(null) }
    var pageNoteDialogOpen by remember(attachment.id) { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var pdfView by remember(attachment.id) { mutableStateOf<AndroidxPdfView?>(null) }
    var pdfReady by remember(attachment.localPath) { mutableStateOf(false) }
    var savedPageApplied by remember(attachment.id) { mutableStateOf(initialPageIndex == null || initialPageIndex <= 0) }
    var userMovedPdf by remember(attachment.id) { mutableStateOf(false) }
    var showAnnotationsPanel by remember(attachment.id) { mutableStateOf(false) }
    var annotationPickMode by remember(attachment.id) { mutableStateOf(false) }
    var highlightSaveMessage by remember(attachment.id) { mutableStateOf<String?>(null) }
    var drawHighlightMode by remember(attachment.id) { mutableStateOf(false) }
    var dragHighlightStart by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var dragHighlightEnd by remember(attachment.id) { mutableStateOf<Offset?>(null) }
    var textBoxPlacementMode by remember(attachment.id) { mutableStateOf(false) }
    var textBoxDialog by remember(attachment.id) { mutableStateOf<PdfTextBoxDraft?>(null) }
    var textBoxDraft by remember(attachment.id) { mutableStateOf("") }
    var textBoxColor by remember(attachment.id) { mutableStateOf("black") }
    var textBoxBackgroundColor by remember(attachment.id) { mutableStateOf(PdfAnnotationEntity.BACKGROUND_NONE) }
    var textBoxSize by remember(attachment.id) { mutableFloatStateOf(18f) }
    var selectedTextBox by remember(attachment.id) { mutableStateOf<PdfAnnotationEntity?>(null) }
    var pdfViewportTick by remember(attachment.id) { mutableLongStateOf(0L) }
    val visiblePdfAnnotations = remember(annotations) {
        annotations.filter { it.isCurrentPdfAnnotation() }
    }
    var pdfPageLocations by remember(attachment.id) { mutableStateOf<Map<Int, RectF>>(emptyMap()) }
    var pdfPageSizes by remember(attachment.id) { mutableStateOf<Map<Int, PdfPageSize>>(emptyMap()) }
    var pdfGestureActive by remember(attachment.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val insetColorArgb = VaultThemeTokens.colors.inset.toArgb()
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    var previewBitmap by remember(attachment.id, attachment.localPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(attachment.id, attachment.localPath) {
        previewBitmap = withContext(Dispatchers.IO) {
            loadCachedPdfFirstPagePreview(
                context = context,
                attachmentId = attachment.id,
                localPath = attachment.localPath,
            )
        }
    }

    LaunchedEffect(pdfReady, previewBitmap, attachment.id, attachment.localPath) {
        if (pdfReady && previewBitmap == null) {
            withContext(Dispatchers.IO) {
                generateAndCachePdfFirstPagePreview(
                    context = context,
                    attachmentId = attachment.id,
                    localPath = attachment.localPath,
                )
            }
        }
    }

    LaunchedEffect(initialPageIndex, pdfReady, pageCount, pdfView) {
        val view = pdfView ?: return@LaunchedEffect
        val resolvedPage = initialPageIndex ?: return@LaunchedEffect
        if (!pdfReady || savedPageApplied || userMovedPdf || pageCount <= 0) return@LaunchedEffect
        val targetPage = resolvedPage.coerceIn(0, pageCount - 1)
        savedPageApplied = true
        if (targetPage != pageIndex) {
            view.scrollToPage(targetPage)
            pageIndex = targetPage
            onProgressChanged(targetPage, pageCount)
        }
    }

    LaunchedEffect(pdfReady, pageCount, pdfView) {
        val document = pdfView?.pdfDocument ?: return@LaunchedEffect
        if (!pdfReady || pageCount <= 0) return@LaunchedEffect
        val sizes = mutableMapOf<Int, PdfPageSize>()
        for (page in 0 until pageCount) {
            val info = runCatching { document.getPageInfo(page) }.getOrNull() ?: continue
            sizes[page] = PdfPageSize(
                width = info.width.toFloat().coerceAtLeast(1f),
                height = info.height.toFloat().coerceAtLeast(1f),
            )
        }
        pdfPageSizes = sizes
        pdfViewportTick += 1
    }

    LaunchedEffect(pdfReady, pdfView, pdfGestureActive) {
        if (!pdfReady || pdfView == null || !pdfGestureActive) return@LaunchedEffect
        while (pdfGestureActive) {
            withFrameNanos { }
            pdfViewportTick += 1
        }
    }

    LaunchedEffect(highlightSaveMessage) {
        if (highlightSaveMessage != null) {
            delay(1400)
            highlightSaveMessage = null
        }
    }

    fun saveDragHighlight(start: Offset?, end: Offset?) {
        val view = pdfView
        if (view == null || !pdfReady || pageCount <= 0 || start == null || end == null) {
            highlightSaveMessage = "Highlight not saved"
            return
        }

        val startPoint = view.toPdfPointOrNull(start)
        val endPoint = view.toPdfPointOrNull(end)
        if (startPoint == null || endPoint == null || startPoint.pageNum != endPoint.pageNum) {
            highlightSaveMessage = "Highlight one page at a time"
            return
        }

        val rect = pdfRectFromDrag(startPoint, endPoint)
        if (rect == null) {
            highlightSaveMessage = "Highlight too small"
            return
        }

        onAddHighlight(
            attachment.libraryFolderId,
            rect.pageNum,
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            highlightColor,
        ) { saved ->
            highlightSaveMessage = if (saved) "Highlight saved" else "Highlight not saved"
            if (saved) drawHighlightMode = false
        }
    }

    fun openTextBoxAt(offset: Offset) {
        val view = pdfView
        if (view == null || !pdfReady || pageCount <= 0) {
            highlightSaveMessage = "Text box not placed"
            return
        }
        val startPoint = view.toPdfPointOrNull(offset)
        val endPoint = view.toPdfPointOrNull(offset + Offset(PdfTextBoxDefaultWidthPx, PdfTextBoxDefaultHeightPx))
        if (startPoint == null || endPoint == null || startPoint.pageNum != endPoint.pageNum) {
            highlightSaveMessage = "Tap inside one PDF page"
            return
        }
        val rect = pdfRectFromDrag(startPoint, endPoint)
        if (rect == null) {
            highlightSaveMessage = "Text box not placed"
            return
        }
        textBoxDraft = ""
        textBoxColor = "black"
        textBoxBackgroundColor = PdfAnnotationEntity.BACKGROUND_NONE
        textBoxSize = 18f
        textBoxDialog = PdfTextBoxDraft.New(rect)
        textBoxPlacementMode = false
        annotationPickMode = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            error != null -> AttachmentViewerEmpty(error ?: "Unable to load PDF")
            !file.exists() || !file.isFile -> AttachmentViewerEmpty("PDF file is missing")
            !isAndroidxPdfViewerSupported() -> AndroidxPdfFallback(
                title = "PDF viewer is not supported on this device.",
                message = "AndroidX PDF requires Android S/API 31 with SDK extension 13 or newer.",
            )
            else -> {
                AndroidxPdfViewer(
                    file = file,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultThemeTokens.colors.inset),
                    annotations = visiblePdfAnnotations,
                    viewportTick = pdfViewportTick,
                    pageCount = pageCount,
                    highlightColor = highlightColor,
                    drawHighlightMode = drawHighlightMode,
                    textBoxMode = textBoxPlacementMode,
                    annotationPickMode = annotationPickMode,
                    onCreateTextBox = { rect ->
                        textBoxDraft = ""
                        textBoxColor = "black"
                        textBoxBackgroundColor = PdfAnnotationEntity.BACKGROUND_NONE
                        textBoxSize = 18f
                        textBoxDialog = PdfTextBoxDraft.New(rect)
                        textBoxPlacementMode = false
                        annotationPickMode = false
                    },
                    onUpdateTextBoxBounds = { annotation, rect ->
                        onUpdateTextBoxBounds(annotation.id, rect.left, rect.top, rect.right, rect.bottom)
                    },
                    onSelectAnnotation = { annotation ->
                        annotationPickMode = false
                        if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) {
                            noteDraft = annotation.noteText.orEmpty()
                            noteDialogAnnotation = annotation
                        } else {
                            selectedAnnotation = annotation
                        }
                    },
                    onAddHighlightRect = { rect, color ->
                        onAddHighlight(
                            attachment.libraryFolderId,
                            rect.pageNum,
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.bottom,
                            color,
                        ) { saved ->
                            highlightSaveMessage = if (saved) "Highlight saved" else "Highlight not saved"
                            if (saved) drawHighlightMode = false
                        }
                    },
                    onPdfViewReady = { view ->
                        pdfView = view
                        view.setBackgroundColor(insetColorArgb)
                        view.setOnLongClickListener {
                            view.clearCurrentSelection()
                            true
                        }
                        view.addOnSelectionChangedListener(
                            object : AndroidxPdfView.OnSelectionChangedListener {
                                override fun onSelectionChanged(selection: androidx.pdf.selection.Selection?) {
                                    if (selection != null) view.post { view.clearCurrentSelection() }
                                }
                            },
                        )
                        view.addOnGestureStateChangedListener(
                            object : AndroidxPdfView.OnGestureStateChangedListener {
                                override fun onGestureStateChanged(newState: Int) {
                                    pdfGestureActive = newState != AndroidxPdfView.GESTURE_STATE_IDLE
                                    view.hideAndroidxPdfToolboxDescendants()
                                    pdfViewportTick += 1
                                }
                            },
                        )
                        view.setOnScrollChangeListener { _, _, _, _, _ ->
                            view.hideAndroidxPdfToolboxDescendants()
                            if (pdfReady) pdfViewportTick += 1
                        }
                        view.addOnFirstContentLoadListener(
                            object : AndroidxPdfView.OnFirstContentLoadListener {
                                override fun onFirstContentLoad() {
                                    pdfReady = true
                                    val count = view.pdfDocument?.pageCount ?: pageCount
                                    pageCount = count
                                    if (count > 0) {
                                        val safePage = view.firstVisiblePage.coerceIn(0, count - 1)
                                        pageIndex = safePage
                                        onProgressChanged(safePage, count)
                                        pdfViewportTick += 1
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
                                    pageLocations: android.util.SparseArray<android.graphics.RectF>,
                                    zoom: Float,
                                ) {
                                    val count = view.pdfDocument?.pageCount ?: pageCount
                                    view.hideAndroidxPdfToolboxDescendants()
                                    if (count <= 0) return
                                    val safePage = firstVisiblePage.coerceIn(0, count - 1)
                                    if (pdfReady && safePage != pageIndex) {
                                        userMovedPdf = true
                                        pageIndex = safePage
                                        pageCount = count
                                        onProgressChanged(safePage, count)
                                    }
                                    if (pdfReady) {
                                        val nextLocations = mutableMapOf<Int, RectF>()
                                        for (index in 0 until pageLocations.size()) {
                                            val page = pageLocations.keyAt(index)
                                            val bounds = pageLocations.valueAt(index)
                                            if (bounds != null) nextLocations[page] = RectF(bounds)
                                        }
                                        pdfPageLocations = nextLocations
                                        pdfViewportTick += 1
                                    }
                                }
                            },
                        )
                    },
                    onError = { throwable -> error = throwable.message ?: throwable::class.java.simpleName },
                )

                val cachedPreview = previewBitmap
                val previewAlpha by animateFloatAsState(
                    targetValue = if (pdfReady) 0f else 1f,
                    animationSpec = tween(durationMillis = 120),
                    label = "pdfFirstPagePreviewAlpha",
                )
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

                if (pdfReady) {
                    PdfDocumentTitleOverlay(attachment = attachment)
                    PdfReadingProgressOverlay(
                        pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                        pageCount = pageCount,
                    )
                    PdfReaderControls(
                        modifier = Modifier.zIndex(3f),
                        highlightColor = highlightColor,
                        annotationCount = visiblePdfAnnotations.size,
                        drawHighlightMode = drawHighlightMode,
                        textBoxMode = textBoxPlacementMode,
                        annotationPickMode = annotationPickMode,
                        onSaveSelectedHighlight = {
                            drawHighlightMode = !drawHighlightMode
                            if (drawHighlightMode) {
                                textBoxPlacementMode = false
                                annotationPickMode = false
                            }
                            highlightSaveMessage = if (drawHighlightMode) "Drag over the page to highlight" else null
                        },
                        onTextBoxModeChange = {
                            textBoxPlacementMode = false
                            drawHighlightMode = false
                            annotationPickMode = false
                            noteDraft = ""
                            pageNoteDialogOpen = true
                        },
                        onHighlightColorChange = { highlightColor = it },
                        onShowAnnotations = { showAnnotationsPanel = true },
                        onAnnotationPickModeChange = {
                            annotationPickMode = !annotationPickMode
                            if (annotationPickMode) {
                                drawHighlightMode = false
                                textBoxPlacementMode = false
                            }
                            highlightSaveMessage = if (annotationPickMode) "Tap a highlight" else null
                        },
                    )
                }

                highlightSaveMessage?.let { message ->
                    PdfHighlightSaveMessage(message = message)
                }

                if (showAnnotationsPanel) {
                    PdfAnnotationsPanel(
                        annotations = visiblePdfAnnotations,
                        currentPageIndex = pageIndex,
                        onDismiss = { showAnnotationsPanel = false },
                        onAddPageNote = {
                            noteDraft = ""
                            pageNoteDialogOpen = true
                            showAnnotationsPanel = false
                        },
                        onAnnotationSelected = {
                            pdfView?.scrollToPage(it.pageIndex.coerceAtLeast(0))
                            pageIndex = it.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                            pdfViewportTick += 1
                            if (it.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) {
                                noteDraft = it.noteText.orEmpty()
                                noteDialogAnnotation = it
                            } else {
                                selectedAnnotation = it
                            }
                            showAnnotationsPanel = false
                        },
                    )
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

    annotationDeleteRequest?.let { annotation ->
        AlertDialog(
            onDismissRequest = { annotationDeleteRequest = null },
            title = { Text(if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) "Delete page note?" else "Delete highlight?") },
            text = {
                Text(
                    if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) {
                        "This removes the page note. The PDF file will not be changed."
                    } else {
                        "This removes the highlight and any quick note attached to it. The PDF file will not be changed."
                    },
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

    if (pageNoteDialogOpen) {
        PdfNoteDialog(
            title = "Page note",
            subtitle = "Page ${pageIndex + 1}",
            text = noteDraft,
            confirmLabel = "Save",
            onTextChange = { noteDraft = it },
            onDismiss = { pageNoteDialogOpen = false },
            onDelete = null,
            onSave = {
                onAddPageNote(attachment.libraryFolderId, pageIndex, noteDraft) { saved ->
                    highlightSaveMessage = if (saved) "Page note saved" else "Write a note first"
                    if (saved) pageNoteDialogOpen = false
                }
            },
        )
    }

    noteDialogAnnotation?.let { annotation ->
        PdfNoteDialog(
            title = if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) "Page note" else "Annotation note",
            subtitle = "Page ${annotation.pageIndex + 1}",
            text = noteDraft,
            confirmLabel = "Save",
            onTextChange = { noteDraft = it },
            onDismiss = { noteDialogAnnotation = null },
            onDelete = {
                onDeleteAnnotation(annotation.id)
                noteDialogAnnotation = null
            },
            onSave = {
                onUpdateAnnotationNote(annotation.id, noteDraft)
                noteDialogAnnotation = null
                highlightSaveMessage = "Note saved"
            },
        )
    }


    textBoxDialog?.let { draft ->
        PdfTextBoxDialog(
            title = if (draft is PdfTextBoxDraft.Edit) "Edit text box" else "New text box",
            text = textBoxDraft,
            color = textBoxColor,
            backgroundColor = textBoxBackgroundColor,
            textSize = textBoxSize,
            onTextChange = { textBoxDraft = it },
            onColorChange = { textBoxColor = it },
            onBackgroundColorChange = { textBoxBackgroundColor = it },
            onTextSizeChange = { textBoxSize = it },
            onDismiss = {
                textBoxDialog = null
                selectedTextBox = null
                textBoxPlacementMode = false
            },
            onDelete = (draft as? PdfTextBoxDraft.Edit)?.let { edit ->
                {
                    onDeleteAnnotation(edit.annotation.id)
                    textBoxDialog = null
                    selectedTextBox = null
                    textBoxPlacementMode = false
                }
            },
            onSave = {
                when (draft) {
                    is PdfTextBoxDraft.New -> {
                        val rect = draft.rect.expandedForText(
                            pdfView = pdfView,
                            pageLocations = pdfPageLocations,
                            pageSizes = pdfPageSizes,
                            text = textBoxDraft,
                            textSize = textBoxSize,
                        )
                        onAddTextBox(
                            attachment.libraryFolderId,
                            rect.pageNum,
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.bottom,
                            textBoxDraft,
                            textBoxColor,
                            textBoxSize,
                            textBoxBackgroundColor,
                        ) { saved ->
                            highlightSaveMessage = if (saved) "Text box saved" else "Text box not saved"
                            if (saved) {
                                textBoxDialog = null
                                textBoxPlacementMode = false
                                highlightSaveMessage = "Text box saved"
                            }
                        }
                    }
                    is PdfTextBoxDraft.Edit -> {
                        onUpdateTextBox(draft.annotation.id, textBoxDraft, textBoxColor, textBoxSize, textBoxBackgroundColor)
                        draft.annotation.expandedRectForText(
                            pdfView = pdfView,
                            pageLocations = pdfPageLocations,
                            pageSizes = pdfPageSizes,
                            text = textBoxDraft,
                            textSize = textBoxSize,
                        )?.let { rect ->
                            onUpdateTextBoxBounds(draft.annotation.id, rect.left, rect.top, rect.right, rect.bottom)
                        }
                        textBoxDialog = null
                        selectedTextBox = null
                        textBoxPlacementMode = false
                        highlightSaveMessage = "Text box saved"
                    }
                }
            },
        )
    }
}

@Composable
private fun AndroidxPdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    annotations: List<PdfAnnotationEntity>,
    viewportTick: Long,
    pageCount: Int,
    highlightColor: String,
    drawHighlightMode: Boolean,
    textBoxMode: Boolean,
    annotationPickMode: Boolean,
    onCreateTextBox: (PdfRect) -> Unit,
    onUpdateTextBoxBounds: (PdfAnnotationEntity, PdfRect) -> Unit,
    onSelectAnnotation: (PdfAnnotationEntity) -> Unit,
    onAddHighlightRect: (PdfRect, String) -> Unit,
    onPdfViewReady: (AndroidxPdfView) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val containerId = R.id.pdf_viewer_fragment_container
    val insetColorArgb = VaultThemeTokens.colors.inset.toArgb()
    val latestOnPdfViewReady = rememberUpdatedState(onPdfViewReady)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnCreateTextBox = rememberUpdatedState(onCreateTextBox)
    val latestOnUpdateTextBoxBounds = rememberUpdatedState(onUpdateTextBoxBounds)
    val latestOnSelectAnnotation = rememberUpdatedState(onSelectAnnotation)
    val latestOnAddHighlightRect = rememberUpdatedState(onAddHighlightRect)
    val fragmentTag = remember(file.absolutePath) {
        "myvault_pdf_viewer_${file.absolutePath.hashCode()}"
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                setBackgroundColor(insetColorArgb)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                val fragmentContainer = FragmentContainerView(viewContext).apply {
                    id = containerId
                    setBackgroundColor(insetColorArgb)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
                val annotationOverlay = NativePdfAnnotationOverlayView(viewContext).apply {
                    tag = NativePdfAnnotationOverlayTag
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
                addView(fragmentContainer)
                addView(annotationOverlay)

                val activity = viewContext.findFragmentActivity()
                if (activity == null) {
                    latestOnError.value(IllegalStateException("MyVault could not open the PDF viewer here."))
                    return@apply
                }

                val uri = runCatching {
                    FileProvider.getUriForFile(
                        viewContext,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        file,
                    )
                }.getOrElse { throwable ->
                    latestOnError.value(throwable)
                    return@apply
                }

                fragmentContainer.attachPdfFragmentWhenReady(
                    activity = activity,
                    fragmentTag = fragmentTag,
                    uri = uri,
                    onPdfViewReady = { view ->
                        annotationOverlay.pdfView = view
                        latestOnPdfViewReady.value(view)
                    },
                    onError = latestOnError.value,
                )
            }
        },
        update = { root ->
            val currentViewportTick = viewportTick
            val overlay = root.findViewWithTag<NativePdfAnnotationOverlayView>(NativePdfAnnotationOverlayTag)
            overlay?.annotations = annotations
            overlay?.pageCount = pageCount
            overlay?.highlightColor = highlightColor
            overlay?.drawMode = drawHighlightMode
            overlay?.textMode = false
            overlay?.selectMode = annotationPickMode
            if (currentViewportTick >= 0L) overlay?.postInvalidateOnAnimation()
            root.hideAndroidxPdfToolboxDescendants()
            overlay?.pdfView?.setHighlights(emptyList())
            overlay?.onCreateTextBox = { latestOnCreateTextBox.value(it) }
            overlay?.onUpdateTextBoxBounds = { annotation, rect -> latestOnUpdateTextBoxBounds.value(annotation, rect) }
            overlay?.onSelectAnnotation = { latestOnSelectAnnotation.value(it) }
            overlay?.onAddHighlight = { rect, color -> latestOnAddHighlightRect.value(rect, color) }
        },
        onRelease = {
            val activity = context.findFragmentActivity()
            val fragment = activity
                ?.supportFragmentManager
                ?.findFragmentByTag(fragmentTag)
            if (activity != null && fragment != null) {
                runCatching {
                    activity.supportFragmentManager
                        .beginTransaction()
                        .remove(fragment)
                        .commitNowAllowingStateLoss()
                }
            }
            PdfViewerCallbackRegistry.onPdfViewReady = null
            PdfViewerCallbackRegistry.onPdfLoadError = null
        },
    )
}

private fun FragmentContainerView.attachPdfFragmentWhenReady(
    activity: FragmentActivity,
    fragmentTag: String,
    uri: Uri,
    onPdfViewReady: (AndroidxPdfView) -> Unit,
    onError: (Throwable) -> Unit,
) {
    fun attach() {
        try {
            if (!isAttachedToWindow || id == View.NO_ID) return
            val manager = activity.supportFragmentManager
            PdfViewerCallbackRegistry.onPdfViewReady = onPdfViewReady
            PdfViewerCallbackRegistry.onPdfLoadError = onError
            val existing = manager.findFragmentByTag(fragmentTag) as? VaultPdfViewerFragment
            if (existing != null) {
                existing.documentUri = uri
                return
            }
            val fragment = VaultPdfViewerFragment()
            manager.fragments
                .filterIsInstance<VaultPdfViewerFragment>()
                .filter { it.id == id || it.tag?.startsWith("myvault_pdf_viewer_") == true }
                .forEach { staleFragment ->
                    runCatching {
                        manager
                            .beginTransaction()
                            .remove(staleFragment)
                            .commitNowAllowingStateLoss()
                    }
                }
            manager
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(id, fragment, fragmentTag)
                .runOnCommit {
                    fragment.documentUri = uri
                }
                .commitAllowingStateLoss()
        } catch (throwable: Throwable) {
            onError(throwable)
        }
    }

    if (isAttachedToWindow) {
        post { attach() }
    } else {
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    removeOnAttachStateChangeListener(this)
                    post { attach() }
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            },
        )
    }
}

@Composable
private fun AndroidxPdfFallback(
    title: String,
    message: String,
) {
    AttachmentCanvas {
        Column(
            modifier = Modifier.padding(VaultSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                color = VaultThemeTokens.colors.text,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VaultThemeTokens.colors.textMuted,
            )
        }
    }
}

private fun isAndroidxPdfViewerSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is Activity -> null
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }

private fun View.hideAndroidxPdfToolboxDescendants() {
    val className = javaClass.name
    if (className.contains("ToolBox", ignoreCase = true)) {
        visibility = View.GONE
        isEnabled = false
        alpha = 0f
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index)?.hideAndroidxPdfToolboxDescendants()
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
private fun BoxScope.PdfDragHighlightOverlay(
    dragStart: Offset?,
    dragEnd: Offset?,
    highlightColor: String,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f)
            .pointerInput(highlightColor) {
                detectDragGestures(
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onDrag = { change, _ ->
                        change.consume()
                        onDragMove(change.position)
                    },
                )
            },
    ) {
        val start = dragStart
        val end = dragEnd
        if (start != null && end != null) {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            drawRoundRect(
                color = highlightColor.toPdfHighlightColor().copy(alpha = 0.34f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    width = (right - left).coerceAtLeast(1f),
                    height = (bottom - top).coerceAtLeast(1f),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
    }
}

@Composable
private fun BoxScope.PdfAnnotationOverlay(
    pdfView: AndroidxPdfView?,
    pageCount: Int,
    viewportTick: Long,
    pageLocations: Map<Int, RectF>,
    pageSizes: Map<Int, PdfPageSize>,
    annotations: List<PdfAnnotationEntity>,
    placementMode: Boolean,
    annotationPickMode: Boolean,
    onPlaceTextBox: (Offset) -> Unit,
    onTextBoxBoundsChanged: (PdfAnnotationEntity, PdfRect) -> Unit,
    onAnnotationSelected: (PdfAnnotationEntity) -> Unit,
    onTextBoxSelected: (PdfAnnotationEntity) -> Unit,
) {
    val density = LocalDensity.current
    var dragState by remember { mutableStateOf<PdfTextBoxDragState?>(null) }
    val overlayBounds = remember(pdfView, pageCount, viewportTick, pageLocations, pageSizes, annotations) {
        if (pdfView == null || pageCount <= 0) {
            emptyList()
        } else {
            annotations.mapNotNull { annotation ->
                annotation.toPdfOverlayBounds(
                    pdfView = pdfView,
                    pageCount = pageCount,
                    pageLocations = pageLocations,
                    pageSizes = pageSizes,
                )
            }
        }
    }
    val displayBounds = overlayBounds.map { item ->
        val activeDrag = dragState
        if (activeDrag?.annotationId == item.annotation.id) {
            item.copy(bounds = activeDrag.bounds)
        } else {
            item
        }
    }
    val interactiveModifier = when {
        placementMode -> Modifier
            .pointerInput(placementMode, displayBounds) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val target = displayBounds
                            .asReversed()
                            .firstOrNull {
                                it.annotation.annotationType == PdfAnnotationEntity.TYPE_TEXT_BOX &&
                                    offset.x in it.bounds.left..it.bounds.right &&
                                    offset.y in it.bounds.top..it.bounds.bottom
                            }
                            ?: return@detectDragGestures
                        val resize = offset.x >= target.bounds.right - PdfTextBoxResizeHandlePx &&
                            offset.y >= target.bounds.bottom - PdfTextBoxResizeHandlePx
                        dragState = PdfTextBoxDragState(
                            annotationId = target.annotation.id,
                            startBounds = target.bounds,
                            bounds = target.bounds,
                            mode = if (resize) PdfTextBoxDragMode.Resize else PdfTextBoxDragMode.Move,
                        )
                    },
                    onDrag = { change, dragAmount ->
                        val current = dragState ?: return@detectDragGestures
                        change.consume()
                        val nextBounds = when (current.mode) {
                            PdfTextBoxDragMode.Move -> current.bounds.translate(dragAmount.x, dragAmount.y)
                            PdfTextBoxDragMode.Resize -> current.bounds.resizeBy(dragAmount.x, dragAmount.y)
                        }
                        dragState = current.copy(bounds = nextBounds)
                    },
                    onDragEnd = {
                        val current = dragState
                        val view = pdfView
                        val annotation = current?.let { state ->
                            overlayBounds.firstOrNull { it.annotation.id == state.annotationId }?.annotation
                        }
                        if (current != null && view != null && annotation != null) {
                            current.bounds.toPdfRect(
                                pdfView = view,
                                fallbackPageIndex = annotation.pageIndex,
                                pageLocations = pageLocations,
                                pageSizes = pageSizes,
                            )?.let { rect ->
                                onTextBoxBoundsChanged(annotation, rect)
                            }
                        }
                        dragState = null
                    },
                    onDragCancel = { dragState = null },
                )
            }
            .pointerInput(placementMode, displayBounds) {
                detectTapGestures { offset ->
                    displayBounds
                        .asReversed()
                        .firstOrNull {
                            it.annotation.annotationType == PdfAnnotationEntity.TYPE_TEXT_BOX &&
                                offset.x in it.bounds.left..it.bounds.right &&
                                offset.y in it.bounds.top..it.bounds.bottom
                        }
                        ?.let { onTextBoxSelected(it.annotation) }
                        ?: onPlaceTextBox(offset)
                }
            }
        annotationPickMode -> Modifier.pointerInput(annotationPickMode, displayBounds) {
            detectTapGestures { offset ->
                displayBounds
                    .asReversed()
                    .firstOrNull {
                        offset.x in it.bounds.left..it.bounds.right &&
                            offset.y in it.bounds.top..it.bounds.bottom
                    }
                    ?.let { onAnnotationSelected(it.annotation) }
            }
        }
        else -> Modifier
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (placementMode || annotationPickMode) 2.5f else 1.5f)
            .then(interactiveModifier),
    ) {}

    displayBounds
        .filter { it.annotation.annotationType == PdfAnnotationEntity.TYPE_TEXT_BOX }
        .forEach { item ->
            val isActive = placementMode && dragState?.annotationId == item.annotation.id
            val measuredSize = estimatePdfTextBoxViewSize(
                text = item.annotation.noteText.orEmpty(),
                textSize = item.annotation.textSize,
            )
            val minTextBoxWidth = maxOf(PdfTextBoxMinWidthPx, measuredSize.x)
            val minTextBoxHeight = maxOf(PdfTextBoxMinHeightPx, measuredSize.y)
            val width = with(density) { item.bounds.width.coerceAtLeast(minTextBoxWidth).toDp() }
            val height = with(density) { item.bounds.height.coerceAtLeast(minTextBoxHeight).toDp() }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = item.bounds.left
                        translationY = item.bounds.top
                    }
                    .width(width)
                    .height(height)
                    .zIndex(if (placementMode) 2.7f else 1.7f)
                    .background(item.annotation.backgroundColor.toPdfTextBoxBackgroundColor(), VaultShapes.sm)
                    .border(
                        width = if (isActive) 2.dp else 1.dp,
                        color = item.annotation.color.toPdfTextBoxBorderColor(if (placementMode) 0.78f else 0.42f),
                        shape = VaultShapes.sm,
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = item.annotation.noteText.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.W800,
                        fontSize = item.annotation.textSize.sp,
                        lineHeight = (item.annotation.textSize * 1.38f).sp,
                    ),
                    color = item.annotation.color.toPdfTextColor(),
                    maxLines = 14,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                )
                if (placementMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp),
                        shape = VaultShapes.pill,
                        color = item.annotation.color.toPdfTextColor().copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.70f)),
                    ) {}
                }
            }
        }
}

@Composable
private fun BoxScope.PdfReaderControls(
    modifier: Modifier = Modifier,
    highlightColor: String,
    annotationCount: Int,
    drawHighlightMode: Boolean,
    textBoxMode: Boolean,
    annotationPickMode: Boolean,
    onSaveSelectedHighlight: () -> Unit,
    onTextBoxModeChange: () -> Unit,
    onHighlightColorChange: (String) -> Unit,
    onShowAnnotations: () -> Unit,
    onAnnotationPickModeChange: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
        color = colors.elevated.copy(alpha = 0.98f),
        shape = VaultShapes.xxl,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = onSaveSelectedHighlight,
                shape = VaultShapes.pill,
                color = if (drawHighlightMode) colors.accent.copy(alpha = 0.22f) else colors.accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = if (drawHighlightMode) 0.58f else 0.38f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accent)
                    Text(
                        text = when {
                            drawHighlightMode -> "Done"
                            else -> "Draw"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.accent,
                    )
                }
            }
            Surface(
                onClick = onTextBoxModeChange,
                shape = VaultShapes.pill,
                color = if (textBoxMode) colors.accent.copy(alpha = 0.22f) else colors.surface,
                border = BorderStroke(1.dp, if (textBoxMode) colors.accent.copy(alpha = 0.58f) else colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accent)
                    Text(
                        text = "+ Note",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = if (textBoxMode) colors.accent else colors.textSecondary,
                    )
                }
            }
            PdfHighlightColors.forEach { color ->
                Surface(
                    onClick = { onHighlightColorChange(color) },
                    modifier = Modifier.size(32.dp),
                    shape = VaultShapes.pill,
                    color = color.toPdfHighlightColor().copy(alpha = if (color == highlightColor) 0.92f else 0.46f),
                    border = BorderStroke(
                        width = if (color == highlightColor) 2.dp else 1.dp,
                        color = if (color == highlightColor) colors.text else colors.border,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            if (annotationCount > 0) {
                Surface(
                    onClick = onShowAnnotations,
                    shape = VaultShapes.pill,
                    color = colors.surface,
                    border = BorderStroke(
                        1.dp,
                        colors.border,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.StickyNote2,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colors.textSecondary,
                        )
                        Text(
                            text = annotationCount.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PdfAnnotationsPanel(
    annotations: List<PdfAnnotationEntity>,
    currentPageIndex: Int,
    onDismiss: () -> Unit,
    onAddPageNote: () -> Unit,
    onAnnotationSelected: (PdfAnnotationEntity) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "PDF notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "Page ${currentPageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
                PremiumPdfDialogButton(label = "+ Note", filled = true, onClick = onAddPageNote)
                PremiumPdfDialogButton(label = "Close", onClick = onDismiss)
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                annotations
                    .sortedWith(compareBy<PdfAnnotationEntity> { it.pageIndex }.thenByDescending { it.updatedAt })
                    .forEach { annotation ->
                        Surface(
                            onClick = { onAnnotationSelected(annotation) },
                            color = colors.surface,
                            shape = VaultShapes.lg,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(18.dp),
                                    shape = VaultShapes.pill,
                                    color = if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) colors.accent.copy(alpha = 0.72f) else annotation.color.toPdfHighlightColor().copy(alpha = 0.84f),
                                    border = BorderStroke(1.dp, colors.border),
                                ) {}
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Page ${annotation.pageIndex + 1}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                                        color = colors.text,
                                    )
                                    Text(
                                        text = annotation.noteText?.takeIf { it.isNotBlank() }
                                            ?: if (annotation.annotationType == PdfAnnotationEntity.TYPE_PAGE_NOTE) "Page note" else "Highlight",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
            }
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
private fun PdfNoteDialog(
    title: String,
    subtitle: String,
    text: String,
    confirmLabel: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    Text(title)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = VaultThemeTokens.colors.textMuted,
                    )
                }
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                minLines = 5,
                maxLines = 8,
                shape = VaultShapes.lg,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PremiumPdfDialogButton(label = confirmLabel, filled = true, onClick = onSave)
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDelete?.let { PremiumPdfDialogButton(label = "Delete", onClick = it) }
                PremiumPdfDialogButton(label = "Cancel", onClick = onDismiss)
            }
        },
        containerColor = VaultThemeTokens.colors.elevated,
        shape = VaultShapes.xxl,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun PdfTextBoxDialog(
    title: String,
    text: String,
    color: String,
    backgroundColor: String,
    textSize: Float,
    onTextChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        Icons.Rounded.TextFields,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                        tint = VaultThemeTokens.colors.accent,
                    )
                }
                Text(title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    minLines = 4,
                    maxLines = 8,
                    shape = VaultShapes.lg,
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = "Text colour",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = VaultThemeTokens.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PdfTextBoxColors.forEach { option ->
                            Surface(
                                onClick = { onColorChange(option) },
                                modifier = Modifier.size(34.dp),
                                shape = VaultShapes.pill,
                                color = option.toPdfTextColor(),
                                border = BorderStroke(
                                    width = if (option == color) 2.dp else 1.dp,
                                    color = if (option == color) VaultThemeTokens.colors.text else VaultThemeTokens.colors.border,
                                ),
                            ) {}
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = "Background",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = VaultThemeTokens.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PdfTextBoxBackgroundColors.forEach { option ->
                            Surface(
                                onClick = { onBackgroundColorChange(option) },
                                modifier = Modifier.size(if (option == PdfAnnotationEntity.BACKGROUND_NONE) 42.dp else 34.dp),
                                shape = VaultShapes.pill,
                                color = if (option == PdfAnnotationEntity.BACKGROUND_NONE) VaultThemeTokens.colors.surface else option.toPdfTextBoxBackgroundColor(),
                                border = BorderStroke(
                                    width = if (option == backgroundColor) 2.dp else 1.dp,
                                    color = if (option == backgroundColor) VaultThemeTokens.colors.text else VaultThemeTokens.colors.border,
                                ),
                            ) {
                                if (option == PdfAnnotationEntity.BACKGROUND_NONE) {
                                    Text(
                                        text = "None",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                                        color = VaultThemeTokens.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14f to "S", 18f to "M", 24f to "L").forEach { (size, label) ->
                        Surface(
                            onClick = { onTextSizeChange(size) },
                            shape = VaultShapes.pill,
                            color = if (textSize == size) VaultThemeTokens.colors.accentSoft else VaultThemeTokens.colors.surface,
                            border = BorderStroke(
                                1.dp,
                                if (textSize == size) VaultThemeTokens.colors.accentBorder else VaultThemeTokens.colors.border,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
                                color = if (textSize == size) VaultThemeTokens.colors.accent else VaultThemeTokens.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PremiumPdfDialogButton(label = "Save", filled = true, onClick = onSave)
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDelete?.let {
                    PremiumPdfDialogButton(label = "Delete", onClick = it)
                }
                PremiumPdfDialogButton(label = "Cancel", onClick = onDismiss)
            }
        },
        containerColor = VaultThemeTokens.colors.elevated,
        shape = VaultShapes.xxl,
        tonalElevation = 0.dp,
    )
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

private fun loadCachedPdfFirstPagePreview(
    context: Context,
    attachmentId: String,
    localPath: String,
): Bitmap? = runCatching {
    val source = File(localPath)
    if (!source.exists() || !source.isFile) return@runCatching null

    val previewFile = pdfFirstPagePreviewFile(context, attachmentId, source)
    if (previewFile.exists() && previewFile.length() > 0L) {
        BitmapFactory.decodeFile(previewFile.absolutePath)?.let { return@runCatching it }
    }
    null
}.getOrNull()

private fun generateAndCachePdfFirstPagePreview(
    context: Context,
    attachmentId: String,
    localPath: String,
) {
    runCatching {
        val source = File(localPath)
        if (!source.exists() || !source.isFile) return@runCatching
        val previewFile = pdfFirstPagePreviewFile(context, attachmentId, source)
        if (previewFile.exists() && previewFile.length() > 0L) return@runCatching

        val bitmap = renderPdfFirstPagePreview(source) ?: return@runCatching
        FileOutputStream(previewFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 88, output)
        }
        bitmap.recycle()
    }
}

private fun pdfFirstPagePreviewFile(
    context: Context,
    attachmentId: String,
    source: File,
): File {
    val previewDir = File(context.cacheDir, "pdf_first_page_previews").apply { mkdirs() }
    return File(previewDir, "${attachmentId}_${source.length()}_${source.lastModified()}.png")
}

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
private val PdfTextBoxColors = listOf("black", "red", "blue", "green", "yellow")
private val PdfTextBoxBackgroundColors = listOf(PdfAnnotationEntity.BACKGROUND_NONE, "white", "yellow", "blue", "green", "red")

private fun String.toPdfHighlightColor(): Color =
    when (lowercase()) {
        "black" -> Color(0xFF111111)
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFFF55)
    }

private fun String.toPdfHighlightFillColor(): Color =
    when (lowercase()) {
        "yellow" -> toPdfHighlightColor().copy(alpha = 0.38f)
        "blue" -> toPdfHighlightColor().copy(alpha = 0.30f)
        "green" -> toPdfHighlightColor().copy(alpha = 0.30f)
        "red" -> toPdfHighlightColor().copy(alpha = 0.30f)
        else -> toPdfHighlightColor().copy(alpha = 0.30f)
    }

private fun String.toPdfHighlightFillArgb(): Int = toPdfHighlightFillColor().toArgb()

private fun String.toPdfHighlightPreviewArgb(): Int = toPdfHighlightColor().copy(alpha = 0.22f).toArgb()

private fun String.toAndroidxHighlightArgb(): Int =
    when (lowercase()) {
        "yellow" -> Color(0x36FFFF55).toArgb()
        "blue" -> Color(0x4D5EA2FF).toArgb()
        "green" -> Color(0x4D34C759).toArgb()
        "red" -> Color(0x4DFF5A5F).toArgb()
        else -> Color(0x36FFFF55).toArgb()
    }

private fun String.toPdfTextColor(): Color =
    when (lowercase()) {
        "yellow" -> Color(0xFFC99100)
        "blue" -> Color(0xFF256EC9)
        "green" -> Color(0xFF1F7A3D)
        "red" -> Color(0xFFD7383D)
        else -> Color(0xFF111111)
    }

private fun String.toPdfTextBoxBorderColor(alpha: Float = 0.45f): Color =
    toPdfTextColor().copy(alpha = alpha)

private fun String.toPdfTextBoxBackgroundColor(): Color =
    when (lowercase()) {
        "white" -> Color.White.copy(alpha = 0.78f)
        "yellow" -> Color(0xFFFFE89A).copy(alpha = 0.56f)
        "blue" -> Color(0xFFD9EBFF).copy(alpha = 0.58f)
        "green" -> Color(0xFFDDF7E6).copy(alpha = 0.58f)
        "red" -> Color(0xFFFFD9DC).copy(alpha = 0.58f)
        else -> Color.Transparent
    }

private fun String.toPdfTextArgb(): Int = toPdfTextColor().toArgb()

private fun String.toPdfTextBoxBorderArgb(): Int = toPdfTextBoxBorderColor(0.52f).toArgb()

private fun String.toPdfTextBoxBackgroundArgb(): Int = toPdfTextBoxBackgroundColor().toArgb()

private sealed interface PdfTextBoxDraft {
    data class New(val rect: PdfRect) : PdfTextBoxDraft
    data class Edit(val annotation: PdfAnnotationEntity) : PdfTextBoxDraft
}

private data class PdfOverlayBounds(
    val annotation: PdfAnnotationEntity,
    val bounds: Rect,
)

private data class PdfPageSize(
    val width: Float,
    val height: Float,
)

private enum class PdfTextBoxDragMode {
    Move,
    Resize,
}

private data class PdfTextBoxDragState(
    val annotationId: String,
    val startBounds: Rect,
    val bounds: Rect,
    val mode: PdfTextBoxDragMode,
)

private const val PdfTextBoxResizeHandlePx = 42f
private const val PdfTextBoxDefaultWidthPx = 360f
private const val PdfTextBoxDefaultHeightPx = 160f
private const val PdfTextBoxMinWidthPx = 180f
private const val PdfTextBoxMinHeightPx = 86f
private const val PdfTextBoxMaxWidthPx = 560f
private const val PdfTextBoxMaxHeightPx = 460f

private fun estimatePdfTextBoxViewSize(text: String, textSize: Float): Offset {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    val safeTextSize = textSize.coerceIn(10f, 36f)
    if (clean.isBlank()) {
        return Offset(PdfTextBoxDefaultWidthPx, PdfTextBoxDefaultHeightPx)
    }
    val preferredCharsPerLine = when {
        safeTextSize >= 24f -> 16
        safeTextSize >= 18f -> 22
        else -> 28
    }
    val longestWord = clean.split(' ').maxOfOrNull { it.length } ?: 0
    val effectiveChars = maxOf(clean.length, longestWord * 3)
    val lineCount = kotlin.math.ceil(effectiveChars / preferredCharsPerLine.toFloat()).toInt().coerceIn(1, 14)
    val widthChars = maxOf(
        preferredCharsPerLine,
        longestWord,
        minOf(clean.length, 46),
    ).coerceIn(18, 54)
    val width = (widthChars * safeTextSize * 0.78f + 62f).coerceIn(PdfTextBoxMinWidthPx, PdfTextBoxMaxWidthPx)
    val height = (lineCount * safeTextSize * 1.62f + 54f).coerceIn(PdfTextBoxMinHeightPx, PdfTextBoxMaxHeightPx)
    return Offset(width, height)
}

private fun PdfRect.expandedForText(
    pdfView: AndroidxPdfView?,
    pageLocations: Map<Int, RectF>,
    pageSizes: Map<Int, PdfPageSize>,
    text: String,
    textSize: Float,
): PdfRect {
    val measuredSize = estimatePdfTextBoxViewSize(text, textSize)
    val pageBounds = pageLocations[pageNum]
    val pageSize = pageSizes[pageNum]
    if (pageBounds != null && pageSize != null && pageBounds.width() > 0f && pageBounds.height() > 0f) {
        val requiredPdfWidth = measuredSize.x / pageBounds.width() * pageSize.width
        val requiredPdfHeight = measuredSize.y / pageBounds.height() * pageSize.height
        return PdfRect(
            pageNum,
            left,
            top,
            maxOf(right, left + requiredPdfWidth),
            maxOf(bottom, top + requiredPdfHeight),
        )
    }

    val view = pdfView ?: return this
    val topLeft = runCatching { view.pdfToViewPoint(PdfPoint(pageNum, left, top)) }.getOrNull() ?: return this
    val bottomRight = view.toPdfPointOrNull(Offset(topLeft.x + measuredSize.x, topLeft.y + measuredSize.y)) ?: return this
    if (bottomRight.pageNum != pageNum) return this
    return PdfRect(
        pageNum,
        left,
        top,
        maxOf(right, bottomRight.x),
        maxOf(bottom, bottomRight.y),
    )
}

private fun PdfAnnotationEntity.expandedRectForText(
    pdfView: AndroidxPdfView?,
    pageLocations: Map<Int, RectF>,
    pageSizes: Map<Int, PdfPageSize>,
    text: String,
    textSize: Float,
): PdfRect? =
    PdfRect(pageIndex, left, top, right, bottom)
        .expandedForText(pdfView, pageLocations, pageSizes, text, textSize)

private fun PdfAnnotationEntity.toPdfOverlayBounds(
    pdfView: AndroidxPdfView,
    pageCount: Int,
    pageLocations: Map<Int, RectF>,
    pageSizes: Map<Int, PdfPageSize>,
): PdfOverlayBounds? {
    if (annotationType != PdfAnnotationEntity.TYPE_TEXT_BOX && annotationType != PdfAnnotationEntity.TYPE_HIGHLIGHT) {
        return null
    }
    if (pageIndex !in 0 until pageCount) return null
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
    if (right <= left || bottom <= top) return null

    val topLeft = runCatching { pdfView.pdfToViewPoint(PdfPoint(pageIndex, left, top)) }.getOrNull()
    val bottomRight = runCatching { pdfView.pdfToViewPoint(PdfPoint(pageIndex, right, bottom)) }.getOrNull()
    if (topLeft != null && bottomRight != null) {
        val bounds = Rect(
            left = minOf(topLeft.x, bottomRight.x),
            top = minOf(topLeft.y, bottomRight.y),
            right = maxOf(topLeft.x, bottomRight.x),
            bottom = maxOf(topLeft.y, bottomRight.y),
        )
        if (bounds.width >= 8f && bounds.height >= 8f) return PdfOverlayBounds(this, bounds)
    }

    val pageBounds = pageLocations[pageIndex]
    val pageSize = pageSizes[pageIndex]
    if (pageBounds != null && pageSize != null && pageBounds.width() > 0f && pageBounds.height() > 0f) {
        val normalized = right <= 1.2f && bottom <= 1.2f
        val scaleX = if (normalized) pageBounds.width() else pageBounds.width() / pageSize.width
        val scaleY = if (normalized) pageBounds.height() else pageBounds.height() / pageSize.height
        val bounds = Rect(
            left = pageBounds.left + left * scaleX,
            top = pageBounds.top + top * scaleY,
            right = pageBounds.left + right * scaleX,
            bottom = pageBounds.top + bottom * scaleY,
        )
        if (bounds.width >= 8f && bounds.height >= 8f) return PdfOverlayBounds(this, bounds)
    }
    return null
}

private fun Rect.translate(dx: Float, dy: Float): Rect =
    Rect(left + dx, top + dy, right + dx, bottom + dy)

private fun Rect.resizeBy(dx: Float, dy: Float): Rect =
    Rect(
        left = left,
        top = top,
        right = (right + dx).coerceAtLeast(left + PdfTextBoxMinWidthPx),
        bottom = (bottom + dy).coerceAtLeast(top + PdfTextBoxMinHeightPx),
    )

private fun Rect.toPdfRect(
    pdfView: AndroidxPdfView,
    fallbackPageIndex: Int,
    pageLocations: Map<Int, RectF>,
    pageSizes: Map<Int, PdfPageSize>,
): PdfRect? {
    val pageBounds = pageLocations[fallbackPageIndex]
    val pageSize = pageSizes[fallbackPageIndex]
    if (pageBounds != null && pageSize != null && pageBounds.width() > 0f && pageBounds.height() > 0f) {
        val pdfLeft = ((left - pageBounds.left) / pageBounds.width() * pageSize.width).coerceIn(0f, pageSize.width)
        val pdfTop = ((top - pageBounds.top) / pageBounds.height() * pageSize.height).coerceIn(0f, pageSize.height)
        val pdfRight = ((right - pageBounds.left) / pageBounds.width() * pageSize.width).coerceIn(0f, pageSize.width)
        val pdfBottom = ((bottom - pageBounds.top) / pageBounds.height() * pageSize.height).coerceIn(0f, pageSize.height)
        return PdfRect(
            fallbackPageIndex.coerceAtLeast(0),
            minOf(pdfLeft, pdfRight),
            minOf(pdfTop, pdfBottom),
            maxOf(pdfLeft, pdfRight),
            maxOf(pdfTop, pdfBottom),
        )
    }

    val topLeft = pdfView.toPdfPointOrNull(Offset(left, top)) ?: return null
    val bottomRight = pdfView.toPdfPointOrNull(Offset(right, bottom)) ?: return null
    if (topLeft.pageNum != bottomRight.pageNum) return null
    return pdfRectFromDrag(topLeft, bottomRight) ?: PdfRect(
        fallbackPageIndex.coerceAtLeast(0),
        minOf(topLeft.x, bottomRight.x),
        minOf(topLeft.y, bottomRight.y),
        maxOf(topLeft.x, bottomRight.x),
        maxOf(topLeft.y, bottomRight.y),
    )
}

private fun AndroidxPdfView.toPdfPointOrNull(offset: Offset): PdfPoint? =
    runCatching { viewToPdfPoint(offset.x, offset.y) }.getOrNull()

private fun pdfRectFromDrag(
    start: PdfPoint,
    end: PdfPoint,
): PdfRect? {
    if (start.pageNum != end.pageNum) return null
    val left = minOf(start.x, end.x)
    val right = maxOf(start.x, end.x)
    val top = minOf(start.y, end.y)
    val bottom = maxOf(start.y, end.y)
    if (right - left < 0.5f || bottom - top < 0.5f) return null
    return PdfRect(start.pageNum.coerceAtLeast(0), left, top, right, bottom)
}
