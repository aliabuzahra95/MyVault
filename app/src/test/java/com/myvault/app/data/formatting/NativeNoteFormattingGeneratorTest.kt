package com.myvault.app.data.formatting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    fun structureOnlyReportsRejectedProviderContentWithoutSilentFallback() {
        val original = "Purification is required.\n\nالماء طهور\n\nFinal retained sentence."
        val generator = generator { _, _, _, _ -> "<p>Purification is required.</p>" }

        assertThrows(NoteFormattingException::class.java) {
            runBlocking { generator.generate(request(action = NoteFormattingAction.StructureOnly, body = original)) {} }
        }
    }

    @Test
    fun longIntelligentStructureUsesImmutableBlockOperationsAndBoundedChunks() = runBlocking {
        val body = List(1_200) { index -> "Paragraph $index keeps a distinct study point." }.joinToString("\n\n")
        val calls = mutableListOf<GatewayCall>()
        val progress = mutableListOf<String>()
        val generator = generator { request, prompt, requestBody, question ->
            calls += GatewayCall(request, prompt, requestBody, question)
            operationsFor(prompt)
        }

        val result = generator.generate(
            request(action = NoteFormattingAction.IntelligentStructure, body = body),
            progress::add,
        )

        assertTrue(calls.size > 2)
        assertTrue(calls.all { it.body.length <= LongNoteStructurePipeline.MaxChunkCharacters + 200 })
        assertTrue(calls.all { it.prompt.systemInstruction.contains("source text is immutable") })
        assertTrue(calls.all { it.question.startsWith("Classify immutable source blocks") })
        assertTrue(progress.first().startsWith("Structuring part 1 of "))
        assertEquals("Validating wording...", progress.last())
        assertTrue(result.contains("<p>"))
        assertTrue(result.contains("Paragraph 1199 keeps a distinct study point."))
        assertFalse(result.contains("```"))
    }

    @Test
    fun sevenToTwelveThousandCharacterNoteUsesBlockOperations() = runBlocking {
        val body = List(180) { index -> "Paragraph $index ${"word ".repeat(6)}remains exact." }.joinToString("\n")
        assertTrue(body.length in (LongNoteFormattingThresholdCharacters + 1)..11_999)
        val calls = mutableListOf<GatewayCall>()
        val generator = generator { request, prompt, requestBody, question ->
            calls += GatewayCall(request, prompt, requestBody, question)
            operationsFor(prompt)
        }

        val result = generator.generate(request(body = body)) {}

        assertTrue(calls.all { it.question.startsWith("Classify immutable source blocks") })
        FormattingTextContract.requirePreserved(body, result)
    }

    @Test
    fun transportFailureRetriesOnlyCurrentLongNotePart() = runBlocking {
        val body = List(500) { index -> "Paragraph $index ${"detail ".repeat(8)}" }.joinToString("\n")
        var calls = 0
        val generator = generator { _, prompt, _, _ ->
            calls++
            if (calls == 1) throw SocketTimeoutException("simulated timeout")
            operationsFor(prompt)
        }

        val result = generator.generate(request(body = body)) {}

        assertTrue(calls >= 2)
        FormattingTextContract.requirePreserved(body, result)
    }

    @Test
    fun cancellationStopsLongNoteWithoutRetrying() {
        val body = List(500) { index -> "Paragraph $index ${"detail ".repeat(8)}" }.joinToString("\n")
        var calls = 0
        val generator = generator { _, _, _, _ -> calls++; throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { generator.generate(request(body = body)) {} }
        }
        assertEquals(1, calls)
    }

    @Test
    fun failedLongNotePartRetriesWithoutRepeatingSuccessfulParts() = runBlocking {
        val body = List(18) { index ->
            val marker = if (index == 8) "FAIL_MARKER" else "point-$index"
            "$marker ${"word ".repeat(420)}"
        }.joinToString("\n")
        val callsByPart = linkedMapOf<String, Int>()
        val generator = generator { _, prompt, _, question ->
            val part = Regex("part (\\d+) of").find(question)?.groupValues?.get(1).orEmpty()
            callsByPart[part] = callsByPart.getOrDefault(part, 0) + 1
            if (prompt.prompt.contains("FAIL_MARKER") && callsByPart[part] == 1) "not json" else operationsFor(prompt)
        }

        generator.generate(request(body = body)) {}

        assertTrue(callsByPart.size > 1)
        assertEquals(2, callsByPart.values.maxOrNull())
        assertTrue(callsByPart.values.count { it == 2 } == 1)
        assertTrue(callsByPart.values.count { it == 1 } >= 1)
    }

    @Test
    fun repeatedlyInvalidLargePartIsRechunkedInsteadOfRestartingTheNote() = runBlocking {
        val longLine = "RECHUNK ${"evidence ".repeat(650)}"
        val body = List(3) { index -> if (index == 0) longLine else "section-$index ${"detail ".repeat(900)}" }
            .joinToString("\n")
        var largePartFailures = 0
        var smallerPartCalls = 0
        val generator = generator { _, prompt, requestBody, _ ->
            if (requestBody.contains("RECHUNK") && requestBody.length > 3_200) {
                largePartFailures++
                "{broken"
            } else {
                if (requestBody.contains("RECHUNK")) smallerPartCalls++
                operationsFor(prompt)
            }
        }

        val output = generator.generate(request(body = body)) {}

        assertEquals(2, largePartFailures)
        assertTrue(smallerPartCalls >= 1)
        FormattingTextContract.requirePreserved(body, output)
    }

    @Test
    fun intelligentStructureReportsRejectedProviderContentWithoutSilentFallback() {
        val original = "Original detailed sentence.\n\nSecond sentence must remain."
        val generator = generator { _, _, _, _ -> "<p>Short summary.</p>" }

        assertThrows(NoteFormattingException::class.java) {
            runBlocking { generator.generate(request(action = NoteFormattingAction.IntelligentStructure, body = original)) {} }
        }
    }

    @Test
    fun validatedNumberedListsDoNotPassThroughHeuristicRepair() = runBlocking {
        val html = "<ol><li>First statement.</li><li>Second statement.</li></ol>"
        val generator = generator { _, _, _, _ -> html }
        assertEquals(html, generator.generate(request(body = "1. First statement.\n2. Second statement.")) {})
    }

    @Test
    fun invalidOutputGetsOnlyOneBoundedRetryFromTheOriginal() = runBlocking {
        var calls = 0
        val generator = generator { _, _, body, _ ->
            calls++
            if (calls == 1) "<p>Wrong summary.</p>" else "<p>$body</p>"
        }
        assertEquals("<p>Original wording.</p>", generator.generate(request()) {})
        assertEquals(2, calls)
        calls = 0
        val alwaysWrong = generator { _, _, _, _ -> calls++; "<p>Wrong.</p>" }
        assertThrows(NoteFormattingException::class.java) { runBlocking { alwaysWrong.generate(request()) {} } }
        assertEquals(2, calls)
    }

    @Test
    fun rateLimitErrorsDoNotExposeProviderAccountIdentifiers() {
        val message = IllegalStateException("Your account org-private request reached organization max RPM: 3").toFormattingFriendlyMessage()
        assertTrue(message.contains("Wait a minute"))
        assertFalse(message.contains("org-private"))
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

    @Test
    fun GeminiBlockOperationSchemaRequiresEveryIdAndAllowedStyle() {
        val chunk = NoteStructureChunk(
            listOf(NoteSourceBlock("b0001", "Heading"), NoteSourceBlock("b0002", "Body")),
        )
        val schema = blockOperationResponseSchema(
            LongNoteStructurePipeline.promptFor(request(), chunk),
        ) ?: error("Expected a structured-response schema")
        val blocks = schema.properties?.get("blocks") ?: error("Expected blocks schema")
        val operation = blocks.items ?: error("Expected operation schema")

        assertEquals(null, blocks.minItems)
        assertEquals(null, blocks.maxItems)
        assertEquals("STRING", operation.properties?.get("id")?.type)
        assertEquals(NoteStructureStyle.entries.map { it.wireName }, operation.properties?.get("style")?.enum)
        assertEquals(null, blockOperationResponseSchema(NoteFormattingPrompt("HTML", "Prompt", 0f, 100)))
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

    private fun operationsFor(prompt: NoteFormattingPrompt): String {
        val ids = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"")
            .findAll(prompt.prompt)
            .map { it.groupValues[1] }
            .toList()
        return JSONObject().put(
            "blocks",
            org.json.JSONArray().apply {
                ids.forEach { id -> put(JSONObject().put("id", id).put("style", "paragraph")) }
            },
        ).toString()
    }

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
