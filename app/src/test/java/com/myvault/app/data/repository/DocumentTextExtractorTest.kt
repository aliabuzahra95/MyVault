package com.myvault.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentTextExtractorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun plainTextIsRecognizedByMimeTypeOrExtension() {
        assertTrue(DocumentTextExtractor.isSupported("notes", "text/plain"))
        assertTrue(DocumentTextExtractor.isSupported("notes.TXT", "application/octet-stream"))
        assertFalse(DocumentTextExtractor.isSupported("data.bin", "application/octet-stream"))
    }

    @Test fun existingDocumentTypesRemainSupported() {
        listOf("book.pdf", "book.docx", "book.html", "book.htm").forEach {
            assertTrue(DocumentTextExtractor.isSupported(it, "application/octet-stream"))
        }
    }

    @Test fun utf8PreservesArabicWhitespaceAndLiteralMarkup() = runBlocking {
        val text = "  English\r\nالعربية\n\n\n\t<heading> &amp;  \n"
        val file = temporaryFolder.newFile("notes.txt").apply { writeText(text) }
        assertEquals(text, DocumentTextExtractor.extract(file.name, "text/plain", file.path))
    }

    @Test fun txtExtensionWorksWithUnknownMimeType() = runBlocking {
        val file = temporaryFolder.newFile("notes.TXT").apply { writeText("A note") }
        assertEquals("A note", DocumentTextExtractor.extract(file.name, "application/octet-stream", file.path))
    }

    @Test fun mimeTypeWorksWithoutExtension() = runBlocking {
        val file = temporaryFolder.newFile("notes").apply { writeText("A note") }
        assertEquals("A note", DocumentTextExtractor.extract(file.name, "text/plain", file.path))
    }

    @Test fun byteOrderMarksAreDecodedWithoutDisplayingThem() = runBlocking {
        val text = "English\nالعربية"
        listOf(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) to Charsets.UTF_8,
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to Charsets.UTF_16LE,
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to Charsets.UTF_16BE,
        ).forEachIndexed { index, (bom, charset) ->
            val file = temporaryFolder.newFile("encoded-$index.txt").apply {
                writeBytes(bom + text.toByteArray(charset))
            }
            assertEquals(text, DocumentTextExtractor.extract(file.name, "text/plain", file.path))
        }
    }

    @Test fun shortFileIsNotLostDuringEncodingDetection() = runBlocking {
        val file = temporaryFolder.newFile("short.txt").apply { writeText("A") }
        assertEquals("A", DocumentTextExtractor.extract(file.name, "text/plain", file.path))
    }

    @Test fun emptyTextReportsReadableError() = runBlocking {
        val file = temporaryFolder.newFile("empty.txt")
        val failure = runCatching { DocumentTextExtractor.extract(file.name, "text/plain", file.path) }.exceptionOrNull()
        assertEquals("This document does not contain readable text.", failure?.message)
    }

    @Test fun missingFileReportsReadableError() = runBlocking {
        val failure = runCatching {
            DocumentTextExtractor.extract("missing.txt", "text/plain", temporaryFolder.root.resolve("missing.txt").path)
        }.exceptionOrNull()
        assertEquals("The document file is missing.", failure?.message)
    }
}
