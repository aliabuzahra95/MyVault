package com.myvault.app.data.ai

import org.json.JSONObject
import org.junit.Test

class ShamelaMcpToolPolicyTest {
    @Test
    fun allowsKnownReadOnlyToolWithExpectedArguments() {
        ShamelaMcpToolPolicy.validate(
            "shamela_search_pages",
            JSONObject().put("query", "الإيمان").put("limit", 6),
        )
    }

    @Test
    fun allowsAuthorScopedProximitySearch() {
        ShamelaMcpToolPolicy.validate(
            "shamela_search_phrase",
            JSONObject()
                .put("query", "مس الذكر الوضوء")
                .put("mode", "near")
                .put("distance", 12)
                .put("scope", JSONObject().put("author_ids", org.json.JSONArray().put(42))),
        )
    }

    @Test
    fun allowsReadOnlyQuoteVerificationAndDisagreementDiscovery() {
        ShamelaMcpToolPolicy.validate(
            "shamela_verify_quote",
            JSONObject().put("quote", "الوضوء مستحب").put("book_id", 1).put("page_id", 2),
        )
        ShamelaMcpToolPolicy.validate(
            "shamela_scan_consensus",
            JSONObject().put("question", "مس الذكر").put("witnesses", 2),
        )
    }

    @Test(expected = ShamelaMcpException::class)
    fun rejectsUnknownTool() {
        ShamelaMcpToolPolicy.validate("shamela_delete_book", JSONObject())
    }

    @Test(expected = ShamelaMcpException::class)
    fun rejectsMissingRequiredArgument() {
        ShamelaMcpToolPolicy.validate("shamela_get_page", JSONObject().put("book_id", 1))
    }

    @Test(expected = ShamelaMcpException::class)
    fun rejectsUnexpectedArgument() {
        ShamelaMcpToolPolicy.validate(
            "shamela_search_pages",
            JSONObject().put("query", "الإيمان").put("admin", true),
        )
    }
}
