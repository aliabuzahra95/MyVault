package com.myvault.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShamelaSourceNoteTest {
    @Test
    fun sourceNotePreservesPassageAndVerifiedMetadata() {
        val note = source(citation = "الكتاب 2/41").toShamelaSourceNote()

        assertTrue(note.body.startsWith("قال المؤلف النص الموثق"))
        assertTrue(note.body.contains("Source: كتاب الاختبار"))
        assertTrue(note.body.contains("Author: المؤلف"))
        assertTrue(note.body.contains("Location: Part 2 · Page 41"))
        assertTrue(note.body.contains("Provenance: Author text"))
        assertTrue(note.body.contains("Citation: الكتاب 2/41"))
        assertEquals("الكتاب 2/41", note.citation)
    }

    @Test
    fun sourceNoteBuildsTruthfulCitationWhenServerCitationIsMissing() {
        val note = source(citation = null).toShamelaSourceNote()

        assertEquals("كتاب الاختبار · المؤلف · Part 2 · Page 41", note.citation)
        assertTrue(note.body.contains("Citation: ${note.citation}"))
    }

    private fun source(citation: String?) = ResearchSource(
        sourceId = "shamela:1:2:authorbody",
        bookId = 1,
        pageId = 2,
        bookTitle = "كتاب الاختبار",
        authorId = 3,
        authorName = "المؤلف",
        arabicPassage = "قال المؤلف النص الموثق",
        provenanceType = ResearchProvenance.AuthorBody,
        part = "2",
        printedPage = "41",
        citationText = citation,
        retrievedAtEpochMillis = 1L,
    )
}
