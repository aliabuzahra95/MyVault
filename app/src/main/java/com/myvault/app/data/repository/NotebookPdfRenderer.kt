package com.myvault.app.data.repository

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.ui.screens.VaultInlineStyle
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.VaultStyleMark
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

private sealed interface NotebookDraw {
    data class Text(val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val y: Float) : NotebookDraw
    data class Image(val file: File, val y: Float, val height: Float) : NotebookDraw
}

internal fun renderNotebookPdf(
    title: String,
    body: VaultRichTextDocument,
    images: List<AttachmentEntity>,
    config: NotebookExportConfig,
    calibration: Boolean,
    output: File,
) {
    val paperWidth = mmToPdfPoints(NotebookExportConfig.A4_WIDTH_MM)
    val paperHeight = mmToPdfPoints(NotebookExportConfig.A4_HEIGHT_MM)
    val pageWidth = ceil(paperWidth).toInt()
    val pageHeight = ceil(paperHeight).toInt()
    val contentWidth = mmToPdfPoints(config.contentWidthMm).toInt()
    val typography = config.typography()
    val top = mmToPdfPoints(config.topInsetMm)
    val bottom = paperHeight - mmToPdfPoints(config.bottomMarginMm) - if (config.drawPageNumbers) 12f else 0f
    val pages = mutableListOf(mutableListOf<NotebookDraw>())
    var y = top

    fun nextPage() {
        pages.add(mutableListOf())
        y = top
    }

    fun addParagraph(paragraph: PdfTextParagraph, spacing: Float, heading: Boolean = false) {
        if (paragraph.text.isEmpty()) {
            if (y + typography.blankLineSpacingPt > bottom) nextPage()
            y += typography.blankLineSpacingPt
            return
        }
        val layout = paragraph.toLayout(contentWidth, typography)
        if (heading && y > top && y + layout.height + typography.headingSpacingPt + typography.bodySizePt > bottom) nextPage()
        var first = 0
        while (first < layout.lineCount) {
            val firstTop = layout.getLineTop(first)
            var last = first
            while (last < layout.lineCount && y + layout.getLineBottom(last) - firstTop <= bottom) last++
            if (last == first) {
                check(y > top) { "Text line is too tall for notebook page" }
                nextPage()
                continue
            }
            pages.last().add(NotebookDraw.Text(layout, first, last, y))
            y += layout.getLineBottom(last - 1) - firstTop
            first = last
            if (first < layout.lineCount) nextPage()
        }
        y += spacing
    }

    if (!calibration) {
        val titleStyles = if (title.isBlank()) emptyList() else listOf(VaultStyleMark(0, title.length, VaultInlineStyle.Heading))
        RichTextPdfDocument.paragraphs(VaultRichTextDocument(title, titleStyles)).forEach { addParagraph(it, typography.titleSpacingPt) }
        val printableBody = body.copy(styleMarks = body.styleMarks + body.noteLinks.flatMap { link ->
            listOf(
                VaultStyleMark(link.start, link.end, VaultInlineStyle.Underline),
                VaultStyleMark(link.start, link.end, VaultInlineStyle.ColorBlue),
            )
        })
        RichTextPdfDocument.paragraphs(printableBody).forEach { paragraph ->
            val heading = paragraph.runs.any { run -> run.styles.any { it in setOf(
                VaultInlineStyle.Heading, VaultInlineStyle.Heading2, VaultInlineStyle.Heading3, VaultInlineStyle.Heading4,
            ) } }
            addParagraph(paragraph, if (heading) typography.headingSpacingPt else typography.paragraphSpacingPt, heading)
        }
        images.forEach { attachment ->
            val file = File(attachment.localPath)
            if (!file.isFile) return@forEach
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, dimensions)
            if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return@forEach
            val imageHeight = (contentWidth * dimensions.outHeight.toFloat() / dimensions.outWidth)
                .coerceAtMost(config.contentHeightMm.let(::mmToPdfPoints) - 35f)
            if (y + imageHeight + 25f > bottom) nextPage()
            pages.last().add(NotebookDraw.Image(file, y, imageHeight))
            y += imageHeight + typography.paragraphSpacingPt
            addParagraph(
                PdfTextParagraph(attachment.fileName, listOf(PdfTextRun(attachment.fileName, emptySet())), 0, false),
                typography.headingSpacingPt,
            )
        }
    }

    val raw = File.createTempFile("notebook-raw-", ".pdf", output.parentFile)
    val pdf = PdfDocument()
    try {
        val pairs = if (calibration) listOf(0 to 1) else notebookSheetPairs(pages.size)
        pairs.forEachIndexed { sheetIndex, (leftIndex, rightIndex) ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, sheetIndex + 1).create())
            val canvas = page.canvas
            if (calibration) {
                drawNotebookCalibration(canvas, config, paperWidth, paperHeight)
            } else {
                listOfNotNull(leftIndex, rightIndex).forEachIndexed { slotIndex, logicalIndex ->
                    val slot = config.slot(slotIndex)
                    val x = mmToPdfPoints(slot.contentLeftMm)
                    pages[logicalIndex].forEach { draw ->
                        when (draw) {
                            is NotebookDraw.Text -> {
                                val lineTop = draw.layout.getLineTop(draw.firstLine)
                                val lineBottom = draw.layout.getLineBottom(draw.lastLine - 1)
                                canvas.save()
                                canvas.clipRect(x, draw.y, x + contentWidth, draw.y + lineBottom - lineTop)
                                canvas.translate(x, draw.y - lineTop)
                                draw.layout.draw(canvas)
                                canvas.restore()
                            }
                            is NotebookDraw.Image -> drawNotebookImage(canvas, draw, x, contentWidth)
                        }
                    }
                    if (config.drawPageNumbers) {
                        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.GRAY }
                        val label = TextUtils.ellipsize("$title · ${logicalIndex + 1}/${pages.size}", footer, contentWidth.toFloat(), TextUtils.TruncateAt.END)
                        canvas.drawText(label.toString(), x, paperHeight - mmToPdfPoints(config.bottomMarginMm), footer)
                    }
                }
                if (config.drawCenterCutGuide) drawNotebookCutGuide(canvas, paperWidth, paperHeight)
            }
            pdf.finishPage(page)
        }
        raw.outputStream().use(pdf::writeTo)
    } finally {
        pdf.close()
    }
    try {
        PDDocument.load(raw).use { exact ->
            val box = PDRectangle(paperWidth, paperHeight)
            exact.pages.forEach { page ->
                page.mediaBox = box
                page.cropBox = box
            }
            exact.save(output)
        }
    } finally {
        raw.delete()
    }
}

private fun drawNotebookImage(canvas: Canvas, draw: NotebookDraw.Image, x: Float, width: Int) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(draw.file.absolutePath, bounds)
    val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 1600)
    val bitmap = BitmapFactory.decodeFile(draw.file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
    try {
        canvas.drawBitmap(bitmap, null, RectF(x, draw.y, x + width, draw.y + draw.height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } finally {
        bitmap.recycle()
    }
}

private fun drawNotebookCutGuide(canvas: Canvas, width: Float, height: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBDBDBD.toInt()
        strokeWidth = 0.5f
        pathEffect = DashPathEffect(floatArrayOf(3f, 4f), 0f)
    }
    canvas.drawLine(width / 2f, 0f, width / 2f, height, paint)
}

private fun drawNotebookCalibration(canvas: Canvas, config: NotebookExportConfig, width: Float, height: Float) {
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF777777.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.7f }
    val zone = Paint().apply { color = 0xFFE7E7E7.toInt() }
    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 8f }
    canvas.drawRect(0f, 0f, width, height, line)
    (0..1).forEach { index ->
        val slot = config.slot(index)
        val left = mmToPdfPoints(slot.leftMm)
        val right = left + mmToPdfPoints(NotebookExportConfig.SLOT_WIDTH_MM)
        val contentLeft = mmToPdfPoints(slot.contentLeftMm)
        val contentRight = mmToPdfPoints(slot.contentRightMm)
        val top = mmToPdfPoints(config.topInsetMm)
        val bottom = height - mmToPdfPoints(config.bottomMarginMm)
        canvas.drawRect(left, 0f, right, top, zone)
        canvas.drawRect(contentRight, top, right, height, zone)
        canvas.drawRect(contentLeft, top, contentRight, bottom, line)
        canvas.drawText("A5 ${index + 1}: 148.5 x 210 mm", contentLeft + 4f, top + 14f, label)
        canvas.drawText("Top fold ${config.topInsetMm} mm · Right fold ${config.rightInsetMm} mm", contentLeft + 4f, top + 28f, label)
        canvas.drawText("Left ${config.leftMarginMm} mm · Bottom ${config.bottomMarginMm} mm", contentLeft + 4f, top + 42f, label)
        canvas.drawText("Content ${config.contentWidthMm} x ${config.contentHeightMm} mm", contentLeft + 4f, top + 56f, label)
    }
    drawNotebookCutGuide(canvas, width, height)
}
