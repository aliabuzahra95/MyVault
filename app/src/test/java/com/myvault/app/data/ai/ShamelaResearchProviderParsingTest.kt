package com.myvault.app.data.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShamelaResearchProviderParsingTest {
    @Test
    fun parsesBoundedSourceContextPageWithoutInventingMetadata() {
        val page = JSONObject()
            .put("page_id", 42)
            .put("printed_page", "١٢")
            .put("body", "<span>نص المؤلف</span>")
            .put("foot", "حاشية المحقق")

        val parsed = parseContextPage(page, isCurrent = true)

        assertEquals(42, parsed.pageId)
        assertEquals("١٢", parsed.printedPage)
        assertEquals("نص المؤلف", parsed.body)
        assertEquals("حاشية المحقق", parsed.footnote)
        assertTrue(parsed.comment.isEmpty())
        assertTrue(parsed.isCurrent)
    }

    @Test
    fun exactPhraseResultUsesNormalVerifiedSourceIdentity() {
        val value = JSONObject().put("results", org.json.JSONArray().put(
            JSONObject()
                .put("book_id", 99)
                .put("page_id", 7)
                .put("book_name", "كتاب الإيمان")
                .put("author_name", "ابن تيمية")
                .put("snippet_body", "الإيمان قول وعمل"),
        ))

        val source = value.getJSONArray("results").getJSONObject(0)
            .let { parseShamelaSearchResult(it, 1L) }
            .single()

        assertEquals("shamela:99:7:authorbody", source.sourceId)
        assertEquals("الإيمان قول وعمل", source.arabicPassage)
    }

    @Test
    fun readsStructuredContentDirectly() {
        val structured = JSONObject().put("results", org.json.JSONArray())
        val result = JSONObject().put("structuredContent", structured)

        assertSame(structured, result.structuredContent())
    }

    @Test
    fun readsStructuredJsonTextFallback() {
        val result = JSONObject().put(
            "content",
            org.json.JSONArray().put(
                JSONObject()
                    .put("type", "text")
                    .put("text", "```json\n{\"total_hits\":3}\n```"),
            ),
        )

        assertEquals(3, result.structuredContent().getInt("total_hits"))
    }

    @Test(expected = ResearchProviderException::class)
    fun rejectsUnstructuredResult() {
        JSONObject().put("content", org.json.JSONArray()).structuredContent()
    }

    @Test
    fun preservesBodyAndFootnoteAsDifferentSources() {
        val result = JSONObject()
            .put("book_id", 1366)
            .put("page_id", 43)
            .put("book_name", "كتاب الاختبار")
            .put("author_name", "المؤلف")
            .put("printed_page", "52")
            .put("matched_in", org.json.JSONArray().put("body").put("foot"))
            .put("snippet_body", "قال المؤلف <mark>الاستواء</mark> معلوم")
            .put("snippet_foot", "قال المحقق في الحاشية")

        val sources = parseShamelaSearchResult(result, retrievedAt = 7L)

        assertEquals(2, sources.size)
        assertEquals(ResearchProvenance.AuthorBody, sources[0].provenanceType)
        assertEquals(ResearchProvenance.Footnote, sources[1].provenanceType)
        assertTrue(sources[0].arabicPassage.contains("الاستواء"))
        assertTrue(sources[0].arabicPassage.contains("<mark>").not())
        assertEquals("52", sources[0].printedPage)
    }
}
