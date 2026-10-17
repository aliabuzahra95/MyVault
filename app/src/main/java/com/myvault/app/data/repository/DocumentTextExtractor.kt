package com.myvault.app.data.repository

import android.text.Html
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object DocumentTextExtractor {
    fun isSupported(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return mimeType == "text/html" ||
            mimeType == "text/plain" ||
            mimeType == "application/xhtml+xml" ||
            mimeType == "application/pdf" ||
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            extension == "html" ||
            extension == "htm" ||
            extension == "pdf" ||
            extension == "docx" ||
            extension == "txt"
    }

    suspend fun extract(
        fileName: String,
        mimeType: String,
        localPath: String,
        maxChars: Int = Int.MAX_VALUE,
    ): String =
        withContext(Dispatchers.IO) {
            val file = File(localPath)
            require(file.exists() && file.isFile) { "The document file is missing." }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (mimeType == "text/plain" || extension == "txt") {
                return@withContext extractPlainText(file).ifBlank {
                    error("This document does not contain readable text.")
                }
            }
            val text = when {
                mimeType == "text/html" || mimeType == "application/xhtml+xml" ||
                    extension == "html" || extension == "htm" -> extractHtml(file)
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    extension == "docx" -> extractDocx(file)
                mimeType == "application/pdf" || extension == "pdf" -> extractPdf(file, maxChars)
                else -> error("This document type is not supported.")
            }
            normalize(text).ifBlank { error("This document does not contain readable text.") }
        }

    private fun extractPlainText(file: File): String =
        file.inputStream().buffered().use { input ->
            input.mark(3)
            val first = input.read()
            val second = input.read()
            val third = input.read()
            input.reset()
            val (charset, bomSize) = when {
                first == 0xEF && second == 0xBB && third == 0xBF -> Charsets.UTF_8 to 3
                first == 0xFF && second == 0xFE -> Charsets.UTF_16LE to 2
                first == 0xFE && second == 0xFF -> Charsets.UTF_16BE to 2
                else -> Charsets.UTF_8 to 0
            }
            repeat(bomSize) { input.read() }
            input.reader(charset).readText()
        }

    private fun extractHtml(file: File): String {
        val source = file.readText()
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun extractDocx(file: File): String =
        ZipFile(file).use { archive ->
            val document = archive.getEntry("word/document.xml")
                ?: error("This DOCX file does not contain a readable document body.")
            archive.getInputStream(document).use { input ->
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .replace(Regex("(?i)<w:tab\\b[^>]*/>"), "\t")
                    .replace(Regex("(?i)<w:(?:br|cr)\\b[^>]*/>"), "\n")
                    .replace(Regex("(?i)</w:tc>"), "\t")
                    .replace(Regex("(?i)</w:p>"), "\n\n")
                    .replace(Regex("(?is)<[^>]+>"), "")
                    .decodeXmlEntities()
            }
        }

    private fun extractPdf(file: File, maxChars: Int): String =
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            if (document.isEncrypted) {
                error("This PDF is encrypted, so its text cannot be extracted.")
            }

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                setShouldSeparateByBeads(false)
            }
            val buffer = StringBuilder()
            for (page in 1..document.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val pageText = normalize(stripper.getText(document))
                if (pageText.isBlank()) continue
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append("Page ").append(page).append(":\n")
                val remaining = maxChars - buffer.length
                if (remaining <= 0) break
                if (pageText.length <= remaining) {
                    buffer.append(pageText)
                } else {
                    buffer.append(pageText.take(remaining))
                    break
                }
            }
            buffer.toString()
        }

    private fun String.decodeXmlEntities(): String =
        replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun normalize(text: String): String =
        text
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
