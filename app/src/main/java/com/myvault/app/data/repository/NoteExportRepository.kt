package com.myvault.app.data.repository

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.myvault.app.data.local.dao.NoteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteExportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
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
        val note = noteDao.getAllIncludingDeleted().firstOrNull { it.id == noteId } ?: error("Note not found")
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 48f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 22f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f
            }
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var y = margin

            fun finishPage() {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                y = margin
            }

            page.canvas.drawText(note.title, margin, y, titlePaint)
            y += 34f
            wrapLines(note.bodyPlainText, bodyPaint, pageWidth - margin * 2).forEach { line ->
                if (y > pageHeight - margin) finishPage()
                page.canvas.drawText(line, margin, y, bodyPaint)
                y += 20f
            }

            document.finishPage(page)
            context.contentResolver.openOutputStream(destination)?.use { output ->
                document.writeTo(output)
            } ?: error("Unable to write export")
        } finally {
            document.close()
        }
    }
}

private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> =
    text.lines().flatMap { paragraph ->
        if (paragraph.isBlank()) return@flatMap listOf("")
        val words = paragraph.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        lines
    }
