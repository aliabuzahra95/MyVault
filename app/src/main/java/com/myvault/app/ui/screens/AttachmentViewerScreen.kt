package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.util.openAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AttachmentViewerScreen(
    attachment: AttachmentEntity?,
    pdfProgress: PdfReadingProgressEntity? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPdfProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
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
                        onProgressChanged = onPdfProgressChanged,
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
    onProgressChanged: (pageIndex: Int, pageCount: Int) -> Unit,
) {
    var pageCount by remember(attachment.localPath) { mutableIntStateOf(progress?.pageCount ?: 0) }
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = progress?.pageIndex?.coerceAtLeast(0) ?: 0)
    var scale by remember(attachment.localPath) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.localPath) { mutableStateOf(Offset.Zero) }
    var restoredProgressPage by remember(attachment.id) { mutableStateOf(false) }
    val visiblePage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceAtLeast(0) }
    }
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4.5f)
        scale = nextScale
        offset = if (nextScale <= 1.01f) {
            Offset.Zero
        } else {
            (offset + panChange).let { nextOffset ->
                Offset(
                    x = nextOffset.x.coerceIn(-1800f, 1800f),
                    y = nextOffset.y.coerceIn(-2400f, 2400f),
                )
            }
        }
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
            .collect { page -> onProgressChanged(page.coerceIn(0, pageCount - 1), pageCount) }
    }

    LaunchedEffect(pageCount, progress?.pageIndex, attachment.id) {
        if (pageCount > 0 && !restoredProgressPage) {
            val targetPage = progress?.pageIndex?.coerceIn(0, pageCount - 1)
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
                        .background(VaultThemeTokens.colors.inset)
                        .transformable(transformableState),
                    userScrollEnabled = scale <= 1.05f,
                    contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(count = pageCount, key = { it }) { pageIndex ->
                        PdfPageSurface(
                            path = attachment.localPath,
                            pageIndex = pageIndex,
                            scale = scale,
                            offset = offset,
                        )
                    }
                }
                PdfReadingProgressOverlay(
                    pageIndex = visiblePage.coerceIn(0, pageCount - 1),
                    pageCount = pageCount,
                )
            }
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
private fun PdfPageSurface(
    path: String,
    pageIndex: Int,
    scale: Float,
    offset: Offset,
) {
    var bitmap by remember(path, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(path, pageIndex) { mutableStateOf<String?>(null) }

    LaunchedEffect(path, pageIndex) {
        val result = renderPdfPage(path, pageIndex)
        result.onSuccess { rendered ->
            bitmap = rendered.bitmap
            error = null
        }.onFailure {
            bitmap = null
            error = it.message ?: "Unable to load page"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = if (scale > 1.05f) offset.x else 0f
                translationY = if (scale > 1.05f) offset.y else 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(5.dp),
            shadowElevation = 2.dp,
        ) {
            val loadedBitmap = bitmap
            when {
                loadedBitmap != null -> Image(
                    bitmap = loadedBitmap,
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
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
                    RenderedPdfPage(bitmap.asImageBitmap())
                }
            }
        }
    }
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
