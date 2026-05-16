package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                    attachment.mimeType == "application/pdf" -> PdfAttachmentViewer(attachment)
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
private fun PdfAttachmentViewer(attachment: AttachmentEntity) {
    var pageIndex by remember(attachment.localPath) { mutableIntStateOf(0) }
    var pageCount by remember(attachment.localPath) { mutableIntStateOf(0) }
    var bitmap by remember(attachment.localPath, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(attachment.localPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(attachment.localPath, pageIndex) {
        val result = renderPdfPage(attachment.localPath, pageIndex)
        result.onSuccess { rendered ->
            bitmap = rendered.bitmap
            pageCount = rendered.pageCount
            error = null
        }.onFailure {
            bitmap = null
            error = it.message ?: "Unable to load PDF"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Previous page", active = pageIndex > 0) {
                if (pageIndex > 0) pageIndex -= 1
            }
            Text(
                text = if (pageCount > 0) "Page ${pageIndex + 1} of $pageCount" else "PDF",
                style = MaterialTheme.typography.labelLarge,
                color = VaultThemeTokens.colors.textSecondary,
            )
            IconBtn(Icons.AutoMirrored.Rounded.ArrowForward, "Next page", active = pageIndex < pageCount - 1) {
                if (pageIndex < pageCount - 1) pageIndex += 1
            }
        }

        ZoomableAttachmentCanvas(resetKey = pageIndex) {
            val loadedBitmap = bitmap
            when {
                loadedBitmap != null -> Image(
                    bitmap = loadedBitmap,
                    contentDescription = attachment.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                error != null -> AttachmentViewerEmpty(error ?: "Unable to load PDF")
                else -> AttachmentViewerEmpty("Loading PDF...")
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
            .verticalScroll(rememberScrollState())
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
    val pageCount: Int,
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
                    RenderedPdfPage(bitmap.asImageBitmap(), renderer.pageCount)
                }
            }
        }
    }
}
