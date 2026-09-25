package com.myvault.app.data.repository

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.ui.screens.VaultInlineStyle
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.VaultStyleMark
import com.myvault.app.ui.screens.parseVaultRichTextDocument
import com.myvault.app.ui.screens.withVaultBidiIsolation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteExportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val attachmentDao: AttachmentDao,
) {
    suspend fun exportText(noteId: String, destination: Uri) = withContext(Dispatchers.IO) {
        val note = noteDao.getAllIncludingDeleted().firstOrNull { it.id == noteId } ?: error("Note not found")
        context.contentResolver.openOutputStream(destination)?.bufferedWriter()?.use { writer ->
            writer.appendLine(note.title)
            writer.appendLine()
            writer.append(note.bodyPlainText)
        } ?: error("Unable to write export")
    }

    suspend fun exportPdf(noteId: String, destination: Uri) = withContext(Dispatchers.IO) {
        val (title, body) = loadNote(noteId)
        val temporary = File.createTempFile("note-export-", ".pdf", context.cacheDir)
        try {
            renderPdf(title, body, temporary)
            context.contentResolver.openOutputStream(destination)?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: error("Unable to write export")
        } finally {
            temporary.delete()
        }
    }

    suspend fun exportNotebookPdf(
        noteId: String,
        destination: Uri,
        config: NotebookExportConfig,
        calibration: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val temporary = createNotebookPdf(noteId, config, calibration)
        try {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: error("Unable to write notebook PDF")
        } finally {
            temporary.delete()
        }
    }

    suspend fun createNotebookPdf(noteId: String, config: NotebookExportConfig, calibration: Boolean = false): File =
        withContext(Dispatchers.IO) {
            val (title, body) = loadNote(noteId)
            val directory = File(context.cacheDir, "notebook_print").apply { mkdirs() }
            val output = File.createTempFile("notebook-print-", ".pdf", directory)
            try {
                val images = attachmentDao.getForNotes(listOf(noteId))
                    .filter { it.deletedAt == null && it.mimeType.startsWith("image/") }
                    .sortedBy { it.createdAt }
                renderNotebookPdf(title, body, images, config, calibration, output)
                output
            } catch (error: Throwable) {
                output.delete()
                throw error
            }
        }

    private suspend fun loadNote(noteId: String): Pair<String, VaultRichTextDocument> {
        val note = noteDao.getAllIncludingDeleted().firstOrNull { it.id == noteId } ?: error("Note not found")
        val richBlock = blockDao.getForNote(noteId).firstOrNull { it.type == "rich_text" }
        val body = richBlock?.content?.let(::parseVaultRichTextDocument)
            ?: if (richBlock == null) VaultRichTextDocument(note.bodyPlainText, emptyList())
            else error("Stored rich text could not be read safely")
        return note.title to body
    }
}

private val headingStyles = setOf(
    VaultInlineStyle.Heading,
    VaultInlineStyle.Heading2,
    VaultInlineStyle.Heading3,
    VaultInlineStyle.Heading4,
)

private fun renderPdf(title: String, body: VaultRichTextDocument, output: File) {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 48f
    val contentWidth = (pageWidth - 2 * margin).toInt()
    val document = PdfDocument()
    try {
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var y = margin

        fun nextPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }

        fun drawParagraph(paragraph: PdfTextParagraph, afterSpacing: Float) {
            if (paragraph.text.isEmpty()) {
                if (y + 13f > pageHeight - margin) nextPage()
                y += 13f
                return
            }
            val layout = paragraph.toLayout(contentWidth)
            var firstLine = 0
            while (firstLine < layout.lineCount) {
                val firstTop = layout.getLineTop(firstLine)
                var lastLine = firstLine
                while (lastLine < layout.lineCount &&
                    y + layout.getLineBottom(lastLine) - firstTop <= pageHeight - margin
                ) lastLine++
                if (lastLine == firstLine) {
                    nextPage()
                    continue
                }
                val segmentHeight = layout.getLineBottom(lastLine - 1) - firstTop
                val canvas = page.canvas
                canvas.save()
                canvas.clipRect(margin, y, margin + contentWidth, y + segmentHeight)
                canvas.translate(margin, y - firstTop)
                layout.draw(canvas)
                canvas.restore()
                y += segmentHeight
                firstLine = lastLine
                if (firstLine < layout.lineCount) nextPage()
            }
            y += afterSpacing
        }

        val titleSource = VaultRichTextDocument(
            title,
            if (title.isEmpty()) emptyList() else listOf(VaultStyleMark(0, title.length, VaultInlineStyle.Heading)),
        )
        RichTextPdfDocument.paragraphs(titleSource).forEach { drawParagraph(it, 18f) }
        RichTextPdfDocument.paragraphs(body).forEach { paragraph ->
            val heading = paragraph.runs.any { run -> run.styles.any { it in headingStyles } }
            drawParagraph(paragraph, if (heading) 8f else 5f)
        }
        document.finishPage(page)
        output.outputStream().use(document::writeTo)
    } finally {
        document.close()
    }
}

internal fun PdfTextParagraph.toLayout(width: Int): StaticLayout {
    val display = androidx.compose.ui.text.AnnotatedString(text).withVaultBidiIsolation()
    val styled = SpannableStringBuilder(display.text.text)
    var offset = 0
    runs.forEach { run ->
        val end = offset + run.text.length
        val displayStart = display.offsetMapping.originalToTransformed(offset)
        val displayEnd = display.offsetMapping.originalToTransformed(end)
        run.styles.forEach { style ->
            when (style) {
                VaultInlineStyle.Bold -> styled.setSpan(StyleSpan(Typeface.BOLD), displayStart, displayEnd, 0)
                VaultInlineStyle.Italic -> styled.setSpan(StyleSpan(Typeface.ITALIC), displayStart, displayEnd, 0)
                VaultInlineStyle.Underline -> styled.setSpan(UnderlineSpan(), displayStart, displayEnd, 0)
                VaultInlineStyle.Heading -> styled.setSpan(AbsoluteSizeSpan(22), displayStart, displayEnd, 0)
                VaultInlineStyle.Heading2 -> styled.setSpan(AbsoluteSizeSpan(20), displayStart, displayEnd, 0)
                VaultInlineStyle.Heading3 -> styled.setSpan(AbsoluteSizeSpan(18), displayStart, displayEnd, 0)
                VaultInlineStyle.Heading4 -> styled.setSpan(AbsoluteSizeSpan(16), displayStart, displayEnd, 0)
                VaultInlineStyle.Quote -> styled.setSpan(StyleSpan(Typeface.ITALIC), displayStart, displayEnd, 0)
                else -> Unit
            }
            if (style in headingStyles) styled.setSpan(StyleSpan(Typeface.BOLD), displayStart, displayEnd, 0)
            style.pdfColor()?.let { styled.setSpan(ForegroundColorSpan(it), displayStart, displayEnd, 0) }
        }
        offset = end
    }
    if (listPrefixLength > 0) styled.setSpan(LeadingMarginSpan.Standard(0, 20), 0, styled.length, 0)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        color = android.graphics.Color.BLACK
        typeface = Typeface.DEFAULT
    }
    return StaticLayout.Builder.obtain(styled, 0, styled.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(if (rightToLeft) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(true)
        .setLineSpacing(3f, 1f)
        .build()
}

private fun VaultInlineStyle.pdfColor(): Int? = when (this) {
    VaultInlineStyle.ColorRed -> 0xFFE5484D.toInt()
    VaultInlineStyle.ColorOrange -> 0xFFF97316.toInt()
    VaultInlineStyle.ColorGreen -> 0xFF2F9E66.toInt()
    VaultInlineStyle.ColorBlue -> 0xFF2F80ED.toInt()
    VaultInlineStyle.ColorPurple -> 0xFF8B5CF6.toInt()
    VaultInlineStyle.ColorPink -> 0xFFDB2777.toInt()
    VaultInlineStyle.ColorSlate -> 0xFF64748B.toInt()
    VaultInlineStyle.Quote -> 0xFF64748B.toInt()
    else -> null
}
