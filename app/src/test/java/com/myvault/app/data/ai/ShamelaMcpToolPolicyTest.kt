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
