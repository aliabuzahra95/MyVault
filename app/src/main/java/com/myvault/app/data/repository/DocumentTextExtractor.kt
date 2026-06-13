package com.myvault.app.data.repository

import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object DocumentTextExtractor {
    fun isSupported(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return mimeType == "text/html" ||
            mimeType == "application/xhtml+xml" ||
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            extension == "html" ||
            extension == "htm" ||
            extension == "docx"
    }

    suspend fun extract(fileName: String, mimeType: String, localPath: String): String =
        withContext(Dispatchers.IO) {
            val file = File(localPath)
            require(file.exists() && file.isFile) { "The document file is missing." }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            val text = when {
                mimeType == "text/html" || mimeType == "application/xhtml+xml" ||
                    extension == "html" || extension == "htm" -> extractHtml(file)
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    extension == "docx" -> extractDocx(file)
                else -> error("This document type is not supported.")
            }
            normalize(text).ifBlank { error("This document does not contain readable text.") }
        }

    private fun extractHtml(file: File): String {
        val source = file.readText()
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(source).toString()
        }
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
