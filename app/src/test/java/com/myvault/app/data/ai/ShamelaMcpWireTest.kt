package com.myvault.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ShamelaMcpWireTest {
    @Test
    fun parsesJsonResponse() {
        val response = ShamelaMcpWire.parseResponse(
            body = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25"}}""",
            contentType = "application/json",
        )

        assertEquals("2025-11-25", response.getJSONObject("result").getString("protocolVersion"))
    }

    @Test
    fun parsesSseResponseAndIgnoresProgressNotification() {
        val response = ShamelaMcpWire.parseResponse(
            body = """
                event: message
                data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}

                event: message
                data: {"jsonrpc":"2.0","id":2,
                data: "result":{"tools":[]}}

            """.trimIndent(),
            contentType = "text/event-stream; charset=utf-8",
        )

        assertEquals(0, response.getJSONObject("result").getJSONArray("tools").length())
    }

    @Test
    fun extractsSafeStructuredErrorMessage() {
        assertEquals(
            "Too many requests",
            ShamelaMcpWire.safeErrorMessage("""{"error":{"code":429,"message":"Too many requests"}}"""),
        )
    }

    @Test(expected = ShamelaMcpException::class)
    fun rejectsMalformedJsonRpcResponse() {
        ShamelaMcpWire.parseResponse("not-json", "application/json")
    }

    @Test(expected = ShamelaMcpException::class)
    fun rejectsUnexpectedSseWithoutJsonRpcResult() {
        ShamelaMcpWire.parseResponse(
            "event: message\ndata: {\"event\":\"unrelated\"}\n\n",
            "text/event-stream",
        )
    }
}
