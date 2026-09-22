package com.myvault.app.ui.viewmodel

import android.graphics.Bitmap
import com.myvault.app.data.local.entity.buildPdfClipSourceUrl
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.PdfAnnotationRepository
import com.myvault.app.ui.screens.PdfAnnotationPreviewRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PdfClipResult(
    val noteId: String,
    val imageCount: Int,
)

@Singleton
class PdfHighlightClipCoordinator @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val annotationRepository: PdfAnnotationRepository,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
) {
    suspend fun clipToNote(annotationId: String, destinationNoteId: String?): PdfClipResult {
        val annotation = annotationRepository.getAnnotation(annotationId)
            ?: error("Highlight could not be found.")
        check(annotation.annotationType == "highlight") { "Only PDF highlights can be clipped." }
        val source = attachmentRepository.getAttachment(annotation.attachmentId)
            ?: error("Source PDF could not be found.")
        val sourceFile = File(source.localPath)
        check(sourceFile.isFile) { "Source PDF file is unavailable." }
        val segments = annotationRepository.getSegments(annotation.id)
        val previews = PdfAnnotationPreviewRenderer.renderDurableClips(sourceFile, annotation, segments)
        check(previews.isNotEmpty()) { "The highlighted region could not be rendered." }

        val pageNumber = (segments.minOfOrNull { it.pageIndex } ?: annotation.pageIndex) + 1
        val noteId = destinationNoteId ?: noteRepository.createRichTextNote(
            folderId = null,
            title = "${source.fileName.substringBeforeLast('.')} - page $pageNumber",
            text = "Notes:\n",
            styleMarksJson = "[]",
        )
        val baseName = source.fileName.substringBeforeLast('.').ifBlank { "PDF" }
        previews.forEachIndexed { index, preview ->
            val pageIndex = segments
                .map { it.pageIndex }
                .distinct()
                .sorted()
                .getOrElse(index) { annotation.pageIndex }
            val bytes = ByteArrayOutputStream().use { output ->
                check(preview.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "The PDF clip could not be encoded."
                }
                output.toByteArray()
            }
            attachmentRepository.attachGeneratedImage(
                noteId = noteId,
                fileName = "$baseName - Page ${pageIndex + 1}.png",
                bytes = bytes,
                sourceUrl = buildPdfClipSourceUrl(source.id, annotation.id, pageIndex),
            )
            preview.bitmap.recycle()
        }
        knowledgeRepository.createSourceLinkFromAnnotation(noteId, annotation.id)
        return PdfClipResult(noteId = noteId, imageCount = previews.size)
    }
}
