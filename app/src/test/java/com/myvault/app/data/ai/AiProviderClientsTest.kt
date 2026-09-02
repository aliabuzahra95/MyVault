package com.myvault.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderClientsTest {
    private val request = AiGenerationRequest(
        systemInstruction = "Use only supplied evidence.",
        prompt = "Explain the passage.",
        maxOutputTokens = 512,
        temperature = 0.25,
    )

    @Test
    fun openAiRequestDoesNotPersistProviderContent() {
        val body = buildOpenAiRequest(request, "gpt-5-mini")

        assertEquals("gpt-5-mini", body.getString("model"))
        assertFalse(body.getBoolean("store"))
        assertEquals("Explain the passage.", body.getString("input"))
    }

    @Test
    fun extractsOpenAiOutputTextItems() {
        val body = JSONObject().put(
            "output",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONArray().put(JSONObject().put("type", "output_text").put("text", "Verified answer")),
                ),
            ),
        )

        assertEquals("Verified answer", extractOpenAiText(body))
    }

    @Test
    fun geminiRequestIncludesSeparateSystemInstruction() {
        val body = buildGeminiRequest(request)

        assertEquals(
            "Use only supplied evidence.",
            body.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text"),
        )
        assertEquals(512, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
    }

    @Test
    fun extractsGeminiTextParts() {
        val body = JSONObject().put(
            "candidates",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", "First"))
                            .put(JSONObject().put("text", "Second")),
                    ),
                ),
            ),
        )

        assertEquals("First\nSecond", extractGeminiText(body))
    }

    @Test
    fun kimiRequestUsesMessagesAndDisablesThinking() {
        val body = buildKimiRequest(request, "kimi-k2.6")

        assertEquals("kimi-k2.6", body.getString("model"))
        assertEquals(0.6, body.getDouble("temperature"), 0.0)
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertTrue(body.getJSONArray("messages").length() == 2)
    }

    @Test
    fun extractsKimiMessageText() {
        val body = JSONObject().put(
            "choices",
            JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", "Kimi answer")),
            ),
        )

        assertEquals("Kimi answer", extractKimiText(body))
    }
}
