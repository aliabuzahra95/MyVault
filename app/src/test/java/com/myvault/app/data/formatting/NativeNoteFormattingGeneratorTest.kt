package com.myvault.app.data.formatting

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNoteFormattingGeneratorTest {

    @Test
    fun everyRetainedActionAndProviderUsesTheNativeFormattingGateway() = runBlocking {
        val calls = mutableListOf<GatewayCall>()
        val generator = generator { request, prompt, body, question ->
            calls += GatewayCall(request, prompt, body, question)
            "<p>${request.body}</p>"
        }

        NoteFormattingAction.entries.forEach { action ->
            NoteFormattingProvider.entries.forEach { provider ->
                generator.generate(request(action = action, provider = provider)) {}
            }
        }

        assertEquals(NoteFormattingAction.entries.size * NoteFormattingProvider.entries.size, calls.size)
        assertTrue(calls.all { it.prompt.systemInstruction.contains("HTML", ignoreCase = true) })
        assertTrue(calls.all { it.prompt.prompt.contains("Output type: EditorOutputHtml") })
        assertTrue(calls.any { it.request.provider == NoteFormattingProvider.Kimi })
        assertTrue(calls.any { it.request.provider == NoteFormattingProvider.ChatGPT })
        assertTrue(calls.any { it.request.provider == NoteFormattingProvider.Gemini })
    }

    @Test
    fun structureOnlyStillRestoresContentRejectedByTheProvider() = runBlocking {
        val original = "Purification is required.\n\nالماء طهور\n\nFinal retained sentence."
        val generator = generator { _, _, _, _ -> "<p>Purification is required.</p>" }

        val result = generator.generate(
            request(action = NoteFormattingAction.StructureOnly, body = original),
        ) {}

        assertTrue(result.contains("Purification is required."))
        assertTrue(result.contains("الماء طهور"))
        assertTrue(result.contains("Final retained sentence."))
    }

    @Test
    fun longIntelligentStructureUsesOnePlanThenBoundedChunks() = runBlocking {
        val body = List(1_200) { index -> "Paragraph $index keeps a distinct study point." }.joinToString("\n\n")
        val calls = mutableListOf<GatewayCall>()
        val progress = mutableListOf<String>()
        val generator = generator { request, prompt, requestBody, question ->
            calls += GatewayCall(request, prompt, requestBody, question)
            if (question == "Create internal structural plan.") {
                "One coherent plan"
            } else {
                "<h2>Structured Part</h2><p>${requestBody.replace("\n\n", "</p><p>")}</p>"
            }
        }

        val result = generator.generate(
            request(action = NoteFormattingAction.IntelligentStructure, body = body),
            progress::add,
        )

        assertTrue(calls.size > 2)
        assertEquals("Create internal structural plan.", calls.first().question)
        assertTrue(calls.drop(1).all { it.body.length <= 25_000 })
        assertEquals("Creating structure plan...", progress.first())
        assertTrue(progress.last().startsWith("Processing part"))
        assertTrue(result.contains("<h2>"))
        assertTrue(result.contains("Paragraph 1199 keeps a distinct study point."))
        assertFalse(result.contains("```"))
    }

    @Test
    fun intelligentStructureStillRestoresContentRejectedByTheProvider() = runBlocking {
        val original = "Original detailed sentence.\n\nSecond sentence must remain."
        val generator = generator { _, _, _, _ -> "<p>Short summary.</p>" }

        val result = generator.generate(
            request(action = NoteFormattingAction.IntelligentStructure, body = original),
        ) {}

        assertTrue(result.contains("Original detailed sentence."))
        assertTrue(result.contains("Second sentence must remain."))
        assertFalse(result.contains("Short summary."))
    }

    @Test
    fun KimiFormattingPayloadKeepsTheProvenRequestShape() {
        val prompt = NoteFormattingPrompt(
            systemInstruction = "Return HTML only.",
            prompt = "Format the note.",
            temperature = 0.22f,
            maxOutputTokens = 4_500,
        )

        val json = JSONObject(buildKimiFormattingRequestBody(NoteFormattingModel.Fast, prompt))

        assertTrue(json.getString("model").isNotBlank())
        assertEquals(false, json.getBoolean("stream"))
        assertEquals("disabled", json.getJSONObject("thinking").getString("type"))
        assertEquals(4_500, json.getInt("max_tokens"))
        assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("user", json.getJSONArray("messages").getJSONObject(1).getString("role"))
        if (json.getString("model").equals("kimi-k2.6", ignoreCase = true)) {
            assertEquals(0.6, json.getDouble("temperature"), 0.0)
        }
    }

    @Test
    fun ChatGptFormattingPayloadContainsOnlyFormattingActionsAndModels() {
        val prompt = NoteFormattingPrompt("HTML", "Prompt", 0.05f, 16_000)
        val structure = JSONObject(
            buildChatGptFormattingRequestBody(
                request = request(action = NoteFormattingAction.StructureOnly, model = NoteFormattingModel.Fast),
                prompt = prompt,
                requestBody = "Original text",
                requestQuestion = "",
            ),
        )
        val intelligent = JSONObject(
            buildChatGptFormattingRequestBody(
                request = request(action = NoteFormattingAction.IntelligentStructure, model = NoteFormattingModel.Smart),
                prompt = prompt,
                requestBody = "Original text",
                requestQuestion = "",
            ),
        )

        assertEquals("format_note", structure.getString("action"))
        assertEquals("fast", structure.getString("model"))
        assertEquals("organise", intelligent.getString("action"))
        assertEquals("smart", intelligent.getString("model"))
        assertFalse(structure.toString().contains("conversation", ignoreCase = true))
        assertFalse(structure.toString().contains("history", ignoreCase = true))
        assertFalse(structure.toString().contains("study_tutor", ignoreCase = true))
    }

    private fun generator(
        response: suspend (
            NoteFormattingRequest,
            NoteFormattingPrompt,
            String,
            String,
        ) -> String,
    ): NativeNoteFormattingGenerator = NativeNoteFormattingGenerator(
        gateway = NoteFormattingProviderGateway { request, prompt, body, question ->
            response(request, prompt, body, question)
        },
        trace = NoteFormattingTrace { _, _, _ -> },
    )

    private fun request(
        action: NoteFormattingAction = NoteFormattingAction.IntelligentStructure,
        provider: NoteFormattingProvider = NoteFormattingProvider.Gemini,
        model: NoteFormattingModel = NoteFormattingModel.Fast,
        title: String = "Purification",
        body: String = "Original wording.",
    ) = NoteFormattingRequest(action, provider, model, title, body)

    private data class GatewayCall(
        val request: NoteFormattingRequest,
        val prompt: NoteFormattingPrompt,
        val body: String,
        val question: String,
    )
}
