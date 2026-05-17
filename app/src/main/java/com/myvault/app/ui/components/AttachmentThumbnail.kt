package com.myvault.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AttachmentThumbnail(
    mimeType: String,
    localPath: String,
    kind: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    val colors = VaultThemeTokens.colors
    val bitmap by produceState<Bitmap?>(null, mimeType, localPath) {
        value = withContext(Dispatchers.IO) {
            loadAttachmentThumbnail(mimeType, localPath)
        }
    }

    Surface(
        modifier = modifier.size(size),
        color = colors.inset,
        shape = VaultShapes.sm,
        border = BorderStroke(1.dp, colors.border),
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            ComposeImage(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        } else {
            val icon = attachmentFallbackIcon(kind, mimeType, colors)
            Box(contentAlignment = Alignment.Center) {
                Icon(icon.first, null, modifier = Modifier.size(size * 0.48f), tint = icon.second)
            }
        }
    }
}

private fun loadAttachmentThumbnail(mimeType: String, localPath: String): Bitmap? =
    runCatching {
        val file = File(localPath)
        if (!file.exists()) return@runCatching null
        val key = "$mimeType:$localPath:${file.lastModified()}"
        thumbnailCache.get(key)?.let { return@runCatching it }
        when {
            mimeType.startsWith("image/") -> decodeScaledBitmap(file, maxSize = 240)
            mimeType == "application/pdf" -> renderPdfFirstPage(file)
            else -> null
        }?.also { thumbnailCache.put(key, it) }
    }.getOrNull()

private val thumbnailCache = object : LruCache<String, Bitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private fun renderPdfFirstPage(file: File): Bitmap? {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use {
        PdfRenderer(it).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val width = 180
                val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
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

private fun attachmentFallbackIcon(
    kind: String,
    mimeType: String,
    colors: com.myvault.app.ui.theme.VaultColors,
): Pair<ImageVector, Color> = when {
    mimeType == "application/pdf" || kind == "PDF" -> Icons.Rounded.PictureAsPdf to colors.warning
    mimeType.startsWith("image/") || kind == "Image" -> Icons.Rounded.Image to colors.success
    mimeType.startsWith("audio/") || kind == "Audio" -> Icons.Rounded.Audiotrack to colors.accent
    kind == "Doc" -> Icons.AutoMirrored.Rounded.Article to colors.textSecondary
    else -> Icons.Rounded.AttachFile to colors.textSecondary
}
