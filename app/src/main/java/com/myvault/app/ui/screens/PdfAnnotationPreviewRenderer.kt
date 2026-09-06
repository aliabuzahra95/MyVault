package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import com.myvault.app.data.local.entity.resolvedGeometrySegments
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PdfPreviewRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PdfPreviewPlan(
    val pageIndex: Int,
    val crop: PdfPreviewRect,
    val scale: Float,
    val outputWidth: Int,
    val outputHeight: Int,
    val partial: Boolean,
    val segments: List<PdfPreviewRect>,
)

internal data class PdfAnnotationRasterPreview(
    val bitmap: Bitmap,
    val partial: Boolean,
)

internal fun buildPdfPreviewPlan(
    pageWidth: Int,
    pageHeight: Int,
    segments: List<PdfAnnotationSegmentEntity>,
    targetWidthPx: Int = 960,
    maxHeightPx: Int = 420,
): PdfPreviewPlan? {
    if (pageWidth <= 0 || pageHeight <= 0 || segments.isEmpty()) return null
    val pageIndex = segments.first().pageIndex
    val pageSegments = segments
        .filter { it.pageIndex == pageIndex }
        .mapNotNull { segment ->
            PdfPreviewRect(
                left = segment.left.coerceIn(0f, pageWidth.toFloat()),
                top = segment.top.coerceIn(0f, pageHeight.toFloat()),
                right = segment.right.coerceIn(0f, pageWidth.toFloat()),
                bottom = segment.bottom.coerceIn(0f, pageHeight.toFloat()),
            ).takeIf { it.right > it.left && it.bottom > it.top }
        }
    if (pageSegments.isEmpty()) return null

    val padding = 10f
    val left = (pageSegments.minOf { it.left } - padding).coerceAtLeast(0f)
    val top = (pageSegments.minOf { it.top } - padding).coerceAtLeast(0f)
    val right = (pageSegments.maxOf { it.right } + padding).coerceAtMost(pageWidth.toFloat())
    val bottom = (pageSegments.maxOf { it.bottom } + padding).coerceAtMost(pageHeight.toFloat())
    if (right <= left || bottom <= top) return null

    val cropWidth = right - left
    val cropHeight = bottom - top
    val scale = (targetWidthPx / cropWidth).coerceIn(1.25f, 3.5f)
    val outputWidth = (cropWidth * scale).roundToInt().coerceIn(240, targetWidthPx)
    val fullOutputHeight = (cropHeight * scale).roundToInt().coerceAtLeast(96)
    return PdfPreviewPlan(
        pageIndex = pageIndex,
        crop = PdfPreviewRect(left, top, right, bottom),
        scale = scale,
        outputWidth = outputWidth,
        outputHeight = fullOutputHeight.coerceAtMost(maxHeightPx),
        partial = fullOutputHeight > maxHeightPx,
        segments = pageSegments,
    )
}

internal object PdfAnnotationPreviewRenderer {
    private const val CacheKilobytes = 16 * 1024
    private val cache = object : LruCache<String, PdfAnnotationRasterPreview>(CacheKilobytes) {
        override fun sizeOf(key: String, value: PdfAnnotationRasterPreview): Int =
            (value.bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun load(
        file: File,
        annotation: PdfAnnotationEntity,
        allSegments: List<PdfAnnotationSegmentEntity>,
    ): PdfAnnotationRasterPreview? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val segments = annotation.resolvedGeometrySegments(allSegments)
        val key = cacheKey(file, annotation, segments)
        synchronized(cache) { cache.get(key) }?.let { return@withContext it }
        val rendered = render(file, annotation, segments) ?: return@withContext null
        synchronized(cache) { cache.put(key, rendered) }
        rendered
    }

    fun invalidate(annotationId: String) {
        synchronized(cache) {
            cache.snapshot().keys
                .filter { it.contains("|$annotationId|") }
                .forEach(cache::remove)
        }
    }

    internal fun cacheKey(
        file: File,
        annotation: PdfAnnotationEntity,
        segments: List<PdfAnnotationSegmentEntity>,
    ): String = buildString {
        append(file.absolutePath)
        append('|').append(file.length())
        append('|').append(file.lastModified())
        append('|').append(annotation.id).append('|')
        append(annotation.updatedAt).append('|').append(annotation.color)
        segments.forEach {
            append('|').append(it.pageIndex)
            append(':').append(it.left)
            append(':').append(it.top)
            append(':').append(it.right)
            append(':').append(it.bottom)
        }
    }

    private fun render(
        file: File,
        annotation: PdfAnnotationEntity,
        segments: List<PdfAnnotationSegmentEntity>,
    ): PdfAnnotationRasterPreview? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageIndex = segments.firstOrNull()?.pageIndex ?: annotation.pageIndex
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val plan = buildPdfPreviewPlan(page.width, page.height, segments) ?: return null
                    val bitmap = Bitmap.createBitmap(plan.outputWidth, plan.outputHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    val matrix = Matrix().apply {
                        setValues(
                            floatArrayOf(
                                plan.scale, 0f, -plan.crop.left * plan.scale,
                                0f, plan.scale, -plan.crop.top * plan.scale,
                                0f, 0f, 1f,
                            ),
                        )
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    drawHighlightOverlay(bitmap, plan, annotation.color)
                    PdfAnnotationRasterPreview(bitmap = bitmap, partial = plan.partial)
                }
            }
        }
    }.getOrNull()

    private fun drawHighlightOverlay(bitmap: Bitmap, plan: PdfPreviewPlan, color: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = when (color) {
                "blue" -> 0x669ED8FF
                "green" -> 0x66AEE8C3
                "red" -> 0x66FF8F94
                "pink" -> 0x66FFB4C8
                "orange" -> 0x66FFC58A
                else -> 0x66FFE27A
            }
        }
        val canvas = Canvas(bitmap)
        plan.segments.forEach { segment ->
            val rect = RectF(
                (segment.left - plan.crop.left) * plan.scale,
                (segment.top - plan.crop.top) * plan.scale,
                (segment.right - plan.crop.left) * plan.scale,
                (segment.bottom - plan.crop.top) * plan.scale,
            )
            if (rect.bottom > 0f && rect.top < bitmap.height) {
                canvas.drawRoundRect(rect, 7f, 7f, paint)
            }
        }
    }
}
