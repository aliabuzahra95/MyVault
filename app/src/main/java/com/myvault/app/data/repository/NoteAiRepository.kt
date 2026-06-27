package com.myvault.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.generationConfig
import com.myvault.app.BuildConfig
import com.myvault.app.data.supabase.SupabaseConfig
import com.myvault.app.data.supabase.SupabaseSession
import com.myvault.app.data.supabase.SupabaseSessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import java.net.UnknownHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.ConnectException

enum class NoteAiAction {
    QuickSummary,
    DeepSummary,
    StudyTutor,
    DeepAnalysis,
    Ask,
    ExplainNote,
    GeneralAsk,
    StructureOnly,
    IntelligentStructure,
    CleanFormat,
    FormatNote,
}

val NoteAiAction.displayName: String
    get() = when (this) {
        NoteAiAction.QuickSummary -> "Quick Summary"
        NoteAiAction.DeepSummary -> "Deep Summary"
        NoteAiAction.StudyTutor -> "Study Tutor"
        NoteAiAction.DeepAnalysis -> "Deep Analysis"
        NoteAiAction.Ask -> "Ask About This Note"
        NoteAiAction.ExplainNote -> "Explain This Note"
        NoteAiAction.GeneralAsk -> "General Ask"
        NoteAiAction.StructureOnly -> "Structure Only"
        NoteAiAction.IntelligentStructure -> "Intelligent Structure"
        NoteAiAction.CleanFormat -> "Format / Organise Note"
        NoteAiAction.FormatNote -> "Format Note"
    }

enum class NoteAiModel(
    val displayName: String,
    val modelName: String,
    val isFastModel: Boolean,
    val isDeepModel: Boolean,
) {
    Gemini25Flash("Gemini 2.5 Flash", "gemini-2.5-flash", isFastModel = true, isDeepModel = false),
    Gemini25Pro("Gemini 2.5 Pro", "gemini-2.5-pro", isFastModel = false, isDeepModel = true),
}

enum class NoteAiProvider(
    val displayName: String,
) {
    Gemini("Gemini"),
    ChatGPT("ChatGPT"),
    Kimi("Kimi"),
}

enum class SelectedTextAiAction {
    Ask,
    Explain,
    Simplify,
    Expand,
    Terminology,
    RelatedConcepts,
    ComparePositions,
    ObjectionResponse,
    StudyQuestions,
}

val SelectedTextAiAction.displayName: String
    get() = when (this) {
        SelectedTextAiAction.Ask -> "Ask"
        SelectedTextAiAction.Explain -> "Explain"
        SelectedTextAiAction.Simplify -> "Simplify"
        SelectedTextAiAction.Expand -> "Expand"
        SelectedTextAiAction.Terminology -> "Terminology"
        SelectedTextAiAction.RelatedConcepts -> "Related Concepts"
        SelectedTextAiAction.ComparePositions -> "Compare Positions"
        SelectedTextAiAction.ObjectionResponse -> "Objection / Response"
        SelectedTextAiAction.StudyQuestions -> "Study Questions"
    }

enum class AiSuggestion {
    Explain,
    Simplify,
    Terminology,
    Compare,
    RelatedConcepts,
    ObjectionResponse,
    StudyQuestions,
}

val AiSuggestion.displayName: String
    get() = when (this) {
        AiSuggestion.Explain -> "Explain"
        AiSuggestion.Simplify -> "Simplify"
        AiSuggestion.Terminology -> "Terminology"
        AiSuggestion.Compare -> "Compare"
        AiSuggestion.RelatedConcepts -> "Related Concepts"
        AiSuggestion.ObjectionResponse -> "Objection / Response"
        AiSuggestion.StudyQuestions -> "Study Questions"
    }

enum class NoteAiChatRole {
    User,
    Assistant,
}

data class NoteAiConversationTurn(
    val role: NoteAiChatRole,
    val content: String,
)

@Singleton
class NoteAiRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionStore: SupabaseSessionStore,
) {
    suspend fun generate(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String = "",
        history: List<NoteAiConversationTurn> = emptyList(),
        onProgress: ((String) -> Unit)? = null,
    ): String {
        if (action != NoteAiAction.GeneralAsk && title.isBlank() && body.isBlank()) {
            error("This note is empty, so there is nothing for AI to read yet.")
        }
        if ((action == NoteAiAction.Ask || action == NoteAiAction.GeneralAsk) && question.isBlank()) {
            error("Type a question first.")
        }
        val generated = if (action in StructuredEditorActions && body.length > IntelligentStructureChunkSize) {
            generateIntelligentStructureInChunks(action, provider, model, title, body, question, onProgress)
        } else {
            generateOnce(action, provider, model, title, body, question, history)
        }
        val cleaned = generated.cleanForAction(action)
        val preserved = if (action == NoteAiAction.StructureOnly) {
            cleaned.ensureStructureOnlyPreservesContent(originalBody = body)
        } else {
            cleaned
        }
        traceStructureOnlyStage(
            action = action,
            stage = "03-cleaned-html-after-sanitizer",
            content = preserved,
            context = context,
        )
        return preserved
    }


    fun generateStreaming(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String = "",
        history: List<NoteAiConversationTurn> = emptyList(),
        onProgress: ((String) -> Unit)? = null,
    ): Flow<String> = flow {
        if (action != NoteAiAction.GeneralAsk && title.isBlank() && body.isBlank()) {
            error("This note is empty, so there is nothing for AI to read yet.")
        }
        if ((action == NoteAiAction.Ask || action == NoteAiAction.GeneralAsk) && question.isBlank()) {
            error("Type a question first.")
        }
        // Editor-output actions must remain one-shot for now because they return full HTML that is
        // applied back into the editor. Normal chat actions stream progressively.
        if (action.isEditorOutputModeForStreaming()) {
            emit(
                generate(
                    action = action,
                    provider = provider,
                    model = model,
                    title = title,
                    body = body,
                    question = question,
                    history = history,
                    onProgress = onProgress,
                ),
            )
            return@flow
        }

        onProgress?.invoke("Thinking...")
        val promptRequest = AiPromptBuilder.build(
            action = action,
            title = title,
            body = body,
            question = question,
            history = history,
            provider = provider,
            model = model,
        )
        when (provider) {
            NoteAiProvider.Gemini -> {
                ensureFirebaseReady()
                generateWithGeminiPromptStream(model, promptRequest).collect { chunk ->
                    emit(chunk)
                }
            }
            NoteAiProvider.ChatGPT -> {
                generateWithChatGptPromptStream(action, model, promptRequest, title, body, question).collect { chunk ->
                    emit(chunk)
                }
            }
            NoteAiProvider.Kimi -> {
                generateWithKimiPromptStream(model, promptRequest).collect { chunk ->
                    emit(chunk)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateForSelectedText(
        action: SelectedTextAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        selectedText: String,
        question: String = "",
        history: List<NoteAiConversationTurn> = emptyList(),
    ): String {
        if (selectedText.isBlank()) {
            error("Select some text first.")
        }
        val promptRequest = AiPromptBuilder.buildSelectedText(
            action = action,
            title = title,
            body = body,
            selectedText = selectedText,
            question = question,
            history = history,
            provider = provider,
            model = model,
        )
        val generated = when (provider) {
            NoteAiProvider.Gemini -> {
                ensureFirebaseReady()
                generateWithGeminiPrompt(model, promptRequest)
            }
            NoteAiProvider.ChatGPT -> generateWithChatGptPrompt(
                action = NoteAiAction.Ask,
                model = model,
                promptRequest = promptRequest,
                title = title,
                body = body,
                question = selectedText,
            )
            NoteAiProvider.Kimi -> generateWithKimiPrompt(model, promptRequest)
        }
        return generated.stripChatMarkdown(isHtmlOutput = false).trim()
    }

    private suspend fun generateOnce(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String = "",
        history: List<NoteAiConversationTurn> = emptyList(),
    ): String =
        when (provider) {
            NoteAiProvider.Gemini -> generateWithGemini(action, model, title, body, question, history)
            NoteAiProvider.ChatGPT -> generateWithChatGpt(action, model, title, body, question, history)
            NoteAiProvider.Kimi -> generateWithKimi(action, model, title, body, question, history)
        }

    private suspend fun generateIntelligentStructureInChunks(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String,
        onProgress: ((String) -> Unit)?,
    ): String {
        val chunks = body.chunkForAi()
        onProgress?.invoke("Creating structure plan...")
        val structuralPlan = generatePrompt(
            action = action,
            provider = provider,
            model = model,
            promptRequest = AiPromptBuilder.buildIntelligentStructurePlan(title = title, body = body, provider = provider, model = model),
            title = title,
            body = body.take(IntelligentStructureChunkSize),
            question = "Create internal structural plan.",
        )
        val processedChunks = mutableListOf<String>()
        return chunks.mapIndexed { index, chunk ->
            onProgress?.invoke("Processing part ${index + 1} of ${chunks.size}...")
            val previousContext = processedChunks
                .takeLast(1)
                .joinToString("\n")
                .extractHtmlHeadings()
                .take(8)
                .joinToString("\n")
            val chunkQuestion = """
                Long-note chunk ${index + 1} of ${chunks.size}.
                This is part of one larger note. The final result must feel like one coherent note after all chunks are merged.

                Overall structural plan:
                <plan>
                $structuralPlan
                </plan>

                Previous chunk heading context:
                <previous_headings>
                ${previousContext.ifBlank { "No previous headings yet." }}
                </previous_headings>

                Preserve this chunk's original order, wording, and meaning.
                Maintain consistent heading hierarchy, formatting style, and colour usage with earlier chunks.
                Avoid repeated Introduction, Overview, Main Topic, or duplicate top-level headings.
                Do not include a generic <h1> for every chunk.
                If this chunk continues a prior section, continue that structure instead of restarting.
                Use extracted phrases/concepts already present in this chunk for headings.
                Use lists for grouped concepts or ordered argument flow, but keep related sentences together instead of inflating whitespace.
                User request: ${question.ifBlank { action.defaultStructureRequest() }}
            """.trimIndent()
            val processed = runCatching {
                generateOnce(
                    action = action,
                    provider = provider,
                    model = model,
                    title = "$title - part ${index + 1} of ${chunks.size}",
                    body = chunk,
                    question = chunkQuestion,
                    history = emptyList(),
                )
            }.getOrElse { error ->
                error("Intelligent Structure failed while processing part ${index + 1} of ${chunks.size}. Your note was not changed. ${error.message.orEmpty()}".trim())
            }
            processedChunks += processed
            processed
        }.mergeStructuredHtmlChunks()
    }

    private suspend fun generateWithGemini(
        action: NoteAiAction,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String,
        history: List<NoteAiConversationTurn> = emptyList(),
    ): String {
        ensureFirebaseReady()

        val promptRequest = AiPromptBuilder.build(
            action = action,
            title = title,
            body = body,
            question = question,
            history = history,
            provider = NoteAiProvider.Gemini,
            model = model,
        )
        traceStructureOnlyPrompt(action, promptRequest, context)
        val raw = generateWithGeminiPrompt(model, promptRequest)
        traceStructureOnlyRaw(action, raw, context)
        return raw
    }

    private suspend fun generatePrompt(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
        title: String,
        body: String,
        question: String,
    ): String {
        traceStructureOnlyPrompt(action, promptRequest, context)
        val raw = when (provider) {
            NoteAiProvider.Gemini -> {
                ensureFirebaseReady()
                generateWithGeminiPrompt(model, promptRequest)
            }
            NoteAiProvider.ChatGPT -> generateWithChatGptPrompt(action, model, promptRequest, title, body, question)
            NoteAiProvider.Kimi -> generateWithKimiPrompt(model, promptRequest)
        }
        traceStructureOnlyRaw(action, raw, context)
        return raw
    }

    private suspend fun generateWithGeminiPrompt(
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
    ): String {
        val config = generationConfig {
            temperature = promptRequest.temperature
            topP = 0.9f
            maxOutputTokens = promptRequest.maxOutputTokens
        }
        var lastFailure: Throwable? = null
        val response = model.safeGeminiModelNames().firstNotNullOfOrNull { modelName ->
            val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = modelName,
                    generationConfig = config,
                )
            runCatching {
                generativeModel.generateContent(promptRequest.prompt)
            }.onFailure { error ->
                lastFailure = error
            }.getOrNull()
        } ?: run {
            val error = lastFailure ?: error("Gemini request failed before a response was returned.")
            if (error is ResponseStoppedException || error.message?.contains("MAX_TOKENS") == true) {
                val partial = (error as? ResponseStoppedException)?.response?.text?.trim().orEmpty()
                if (partial.isNotBlank()) {
                    return partial + "\n\n[Gemini stopped because the answer reached its output limit. The useful partial answer above was kept.]"
                }
                throw java.lang.IllegalStateException("Gemini reached its answer length limit before returning text. Try the fast model, shorten the request, or ask for a smaller section.")
            }
            throw java.lang.IllegalStateException(error.toFriendlyMessage())
        }
        val text = response.text?.trim().orEmpty()
        return text.ifBlank {
            val reason = response.candidates.firstOrNull()?.finishReason?.name
            error(
                if (reason == "MAX_TOKENS") {
                    "Gemini reached its answer length limit. Try a shorter request or split the note into sections."
                } else {
                    "Gemini did not return any text. Please try again."
                },
            )
        }
    }


    private fun generateWithGeminiPromptStream(
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
    ): Flow<String> = flow {
        val config = generationConfig {
            temperature = promptRequest.temperature
            topP = 0.9f
            maxOutputTokens = promptRequest.maxOutputTokens
        }
        var emittedAnyText = false
        var lastFailure: Throwable? = null
        for (modelName in model.safeGeminiModelNames()) {
            val completed = runCatching {
                val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(
                        modelName = modelName,
                        generationConfig = config,
                    )
                generativeModel.generateContentStream(promptRequest.prompt).collect { response ->
                    val chunk = response.text.orEmpty()
                    if (chunk.isNotBlank()) {
                        emittedAnyText = true
                        emit(chunk)
                    }
                }
            }.onFailure { error ->
                lastFailure = error
                if (error is ResponseStoppedException || error.message?.contains("MAX_TOKENS") == true) {
                    val partial = (error as? ResponseStoppedException)?.response?.text?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        emittedAnyText = true
                        emit(partial)
                    }
                    emit("\n\n[Response paused because Gemini reached its output limit. Tap Continue to keep going.]")
                    return@flow
                }
                
                // If it emitted text before failing for network reasons, stop fallback loops and exit gracefully.
                if (emittedAnyText) {
                    throw error
                }
            }.isSuccess
            if (completed) break
        }
        if (!emittedAnyText && lastFailure != null) {
            throw java.lang.IllegalStateException(lastFailure.toFriendlyMessage())
        }
        if (!emittedAnyText) {
            error("Gemini did not return any text. Please try again.")
        }
    }

    private suspend fun generateWithChatGpt(
        action: NoteAiAction,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String,
        history: List<NoteAiConversationTurn>,
    ): String {
        val promptRequest = AiPromptBuilder.build(
            action = action,
            title = title,
            body = body,
            question = question,
            history = history,
            provider = NoteAiProvider.ChatGPT,
            model = model,
        )
        traceStructureOnlyPrompt(action, promptRequest, context)
        val raw = generateWithChatGptPrompt(action, model, promptRequest, title, body, question)
        traceStructureOnlyRaw(action, raw, context)
        return raw
    }

    private suspend fun generateWithChatGptPrompt(
        action: NoteAiAction,
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
        title: String,
        body: String,
        question: String,
    ): String = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            error("Supabase is not configured yet.")
        }
        val session = authenticatedSupabaseSession()
        if (!session.isSignedIn) {
            error("Sign in to your Supabase account first, then try ChatGPT again.")
        }

        val connection = URL("${SupabaseConfig.url}/functions/v1/myvault-ai").openConnection() as HttpURLConnection
        runCatching {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json")

            val bodyJson = JSONObject()
                .put("action", action.toFunctionAction())
                .put("model", model.toFunctionModel())
                .put("title", title)
                .put("body", body.scopedForAiFunctionPayload(action))
                .put("question", question)
                .put("systemInstruction", promptRequest.systemInstruction)
                .put("prompt", promptRequest.prompt)
                .put("temperature", promptRequest.temperature.toDouble())
                .put("maxOutputTokens", promptRequest.maxOutputTokens)
                .toString()

            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            val json = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                error(json.optString("error").ifBlank { "ChatGPT request failed. HTTP ${connection.responseCode}." })
            }
            json.optString("text").trim().ifBlank {
                error("ChatGPT did not return any text. Please try again.")
            }
        }.getOrElse { error ->
            throw java.lang.IllegalStateException(error.toFriendlyMessage())
        }.also {
            connection.disconnect()
        }
    }

    private fun generateWithChatGptPromptStream(
        action: NoteAiAction,
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
        title: String,
        body: String,
        question: String,
    ): Flow<String> = flow {
        if (!SupabaseConfig.isConfigured) {
            error("Supabase is not configured yet.")
        }
        val session = authenticatedSupabaseSession()
        if (!session.isSignedIn) {
            error("Sign in to your Supabase account first, then try ChatGPT again.")
        }

        val connection = URL("${SupabaseConfig.url}/functions/v1/myvault-ai").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")

            val bodyJson = JSONObject()
                .put("action", action.toFunctionAction())
                .put("model", model.toFunctionModel())
                .put("title", title)
                .put("body", body.scopedForAiFunctionPayload(action))
                .put("question", question)
                .put("systemInstruction", promptRequest.systemInstruction)
                .put("prompt", promptRequest.prompt)
                .put("temperature", promptRequest.temperature.toDouble())
                .put("maxOutputTokens", promptRequest.maxOutputTokens)
                .put("stream", true)
                .toString()

            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                val responseText = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                val json = runCatching { JSONObject(responseText.ifBlank { "{}" }) }.getOrNull()
                error(json?.optString("error")?.ifBlank { null } ?: "ChatGPT request failed. HTTP ${connection.responseCode}.")
            }

            var emittedAnyText = false
            val dataBuffer = StringBuilder()
            suspend fun emitEventData(data: String) {
                if (data.isBlank() || data == "[DONE]") return
                val event = runCatching { JSONObject(data) }.getOrNull() ?: return
                if (event.optString("type") == "error") {
                    error(event.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "ChatGPT streaming failed." })
                }
                val delta = event.extractOpenAiTextDelta()
                if (delta.isNotBlank()) {
                    emittedAnyText = true
                    emit(delta)
                }
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isBlank() -> {
                            emitEventData(dataBuffer.toString().trim())
                            dataBuffer.clear()
                        }
                        line.startsWith("data:") -> {
                            if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                            dataBuffer.append(line.removePrefix("data:").trim())
                        }
                    }
                }
            }
            emitEventData(dataBuffer.toString().trim())
            if (!emittedAnyText) {
                error("ChatGPT did not stream any text. Please try again.")
            }
        } catch (error: Throwable) {
            throw java.lang.IllegalStateException(error.toFriendlyMessage())
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun generateWithKimi(
        action: NoteAiAction,
        model: NoteAiModel,
        title: String,
        body: String,
        question: String,
        history: List<NoteAiConversationTurn>,
    ): String {
        val promptRequest = AiPromptBuilder.build(
            action = action,
            title = title,
            body = body,
            question = question,
            history = history,
            provider = NoteAiProvider.Kimi,
            model = model,
        )
        traceStructureOnlyPrompt(action, promptRequest, context)
        val raw = generateWithKimiPrompt(model, promptRequest)
        traceStructureOnlyRaw(action, raw, context)
        return raw
    }

    private suspend fun generateWithKimiPrompt(
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.KIMI_API_KEY.trim()
        if (apiKey.isBlank()) {
            error("Kimi API key is missing. Add MYVAULT_KIMI_API_KEY to local.properties, then rebuild the app.")
        }

        val connection = URL(KimiChatCompletionsEndpoint).openConnection() as HttpURLConnection
        runCatching {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            val bodyJson = kimiChatRequestBody(
                model = model,
                promptRequest = promptRequest,
                stream = false,
            )

            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            val json = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                error(json.kimiErrorMessage().ifBlank { "Kimi request failed. HTTP ${connection.responseCode}." })
            }
            json.extractKimiChatText().ifBlank {
                error("Kimi did not return any text. Please try again.")
            }
        }.getOrElse { error ->
            throw java.lang.IllegalStateException(error.toFriendlyMessage())
        }.also {
            connection.disconnect()
        }
    }

    private fun generateWithKimiPromptStream(
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
    ): Flow<String> = flow {
        val apiKey = BuildConfig.KIMI_API_KEY.trim()
        if (apiKey.isBlank()) {
            error("Kimi API key is missing. Add MYVAULT_KIMI_API_KEY to local.properties, then rebuild the app.")
        }

        val connection = URL(KimiChatCompletionsEndpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")

            val bodyJson = kimiChatRequestBody(
                model = model,
                promptRequest = promptRequest,
                stream = true,
            )
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                val responseText = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                val json = runCatching { JSONObject(responseText.ifBlank { "{}" }) }.getOrNull()
                error(json?.kimiErrorMessage()?.ifBlank { null } ?: "Kimi request failed. HTTP ${connection.responseCode}.")
            }

            var emittedAnyText = false
            val dataBuffer = StringBuilder()
            suspend fun emitEventData(data: String) {
                if (data.isBlank() || data == "[DONE]") return
                val event = runCatching { JSONObject(data) }.getOrNull() ?: return
                event.optJSONObject("error")?.let { error(it.optString("message").ifBlank { "Kimi streaming failed." }) }
                val delta = event.extractKimiChatDelta()
                if (delta.isNotBlank()) {
                    emittedAnyText = true
                    emit(delta)
                }
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isBlank() -> {
                            emitEventData(dataBuffer.toString().trim())
                            dataBuffer.clear()
                        }
                        line.startsWith("data:") -> {
                            if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                            dataBuffer.append(line.removePrefix("data:").trim())
                        }
                    }
                }
            }
            emitEventData(dataBuffer.toString().trim())
            if (!emittedAnyText) {
                error("Kimi did not stream any text. Please try again.")
            }
        } catch (error: Throwable) {
            throw java.lang.IllegalStateException(error.toFriendlyMessage())
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun kimiChatRequestBody(
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
        stream: Boolean,
    ): String =
        JSONObject()
            .put("model", model.toKimiModelId())
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", promptRequest.systemInstruction))
                    .put(JSONObject().put("role", "user").put("content", promptRequest.prompt)),
            )
            .put("temperature", promptRequest.kimiTemperature(model))
            .put("max_tokens", promptRequest.maxOutputTokens)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("stream", stream)
            .toString()

private fun String.scopedForAiFunctionPayload(action: NoteAiAction): String {
    val maxChars = when (action) {
        NoteAiAction.QuickSummary -> 5_000
        NoteAiAction.ExplainNote -> 7_000
        NoteAiAction.DeepSummary,
        NoteAiAction.FormatNote,
        NoteAiAction.CleanFormat,
        -> 10_000
        NoteAiAction.Ask,
        NoteAiAction.GeneralAsk,
        -> 10_000
        NoteAiAction.StudyTutor,
        NoteAiAction.DeepAnalysis,
        NoteAiAction.IntelligentStructure,
        -> 18_000
        NoteAiAction.StructureOnly -> Int.MAX_VALUE
    }
    val clean = trim()
    if (clean.length <= maxChars) return clean
    val headLength = (maxChars * 0.62f).toInt()
    val tailLength = maxChars - headLength
    return buildString {
        append(clean.take(headLength).trimEnd())
        append("\n\n[Middle of note trimmed for AI speed and token budget.]\n\n")
        append(clean.takeLast(tailLength).trimStart())
    }
}

private fun JSONObject.extractOpenAiTextDelta(): String {
    val type = optString("type")
    if (type == "response.output_text.delta") {
        return optString("delta")
    }
    if (has("delta")) {
        return optString("delta")
    }
    return ""
}

private fun JSONObject.extractKimiChatText(): String {
    val choices = optJSONArray("choices") ?: return ""
    return buildString {
        for (index in 0 until choices.length()) {
            val content = choices
                .optJSONObject(index)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isNotBlank()) append(content)
        }
    }.trim()
}

private fun JSONObject.extractKimiChatDelta(): String {
    val choices = optJSONArray("choices") ?: return ""
    return buildString {
        for (index in 0 until choices.length()) {
            val content = choices
                .optJSONObject(index)
                ?.optJSONObject("delta")
                ?.optString("content")
                .orEmpty()
            if (content.isNotBlank()) append(content)
        }
    }
}

private fun JSONObject.kimiErrorMessage(): String =
    optJSONObject("error")?.optString("message").orEmpty()
        .ifBlank { optString("message") }

    private suspend fun authenticatedSupabaseSession(): SupabaseSession {
        val session = sessionStore.session.first()
        val expiresSoon = session.expiresAt > 0 && session.expiresAt - System.currentTimeMillis() < 60_000L
        if (!session.isSignedIn || !expiresSoon || session.refreshToken.isBlank()) return session
        return refreshSupabaseSession(session).getOrElse { session }
    }

    private suspend fun refreshSupabaseSession(current: SupabaseSession): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        val connection = URL("${SupabaseConfig.url}/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection
        runCatching {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val bodyJson = JSONObject()
                .put("refresh_token", current.refreshToken)
                .toString()
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) {
                "Supabase session refresh failed. Please sign in again."
            }
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val user = json.optJSONObject("user")
            val refreshed = current.copy(
                userId = user?.optString("id").orEmpty().ifBlank { current.userId },
                email = user?.optString("email").orEmpty().ifBlank { current.email },
                accessToken = json.optString("access_token").ifBlank { current.accessToken },
                refreshToken = json.optString("refresh_token").ifBlank { current.refreshToken },
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
            )
            sessionStore.save(refreshed)
            refreshed
        }.also {
            connection.disconnect()
        }
    }

    private fun NoteAiAction.toFunctionAction(): String =
        when (this) {
            NoteAiAction.QuickSummary -> "quick_summary"
            NoteAiAction.DeepSummary -> "deep_summary"
            NoteAiAction.StudyTutor -> "study_tutor"
            NoteAiAction.DeepAnalysis -> "deep_analysis"
            NoteAiAction.Ask -> "ask"
            NoteAiAction.ExplainNote -> "explain_note"
            NoteAiAction.GeneralAsk -> "general_ask"
            NoteAiAction.StructureOnly -> "format_note"
            NoteAiAction.IntelligentStructure -> "organise"
            NoteAiAction.CleanFormat -> "organise"
            NoteAiAction.FormatNote -> "format_note"
        }

    private fun NoteAiModel.toFunctionModel(): String =
        when (this) {
            NoteAiModel.Gemini25Flash -> "fast"
            NoteAiModel.Gemini25Pro -> "smart"
        }

    private fun NoteAiModel.toKimiModelId(): String =
        when (this) {
            NoteAiModel.Gemini25Flash -> BuildConfig.HOME_AI_KIMI_FAST_MODEL
            NoteAiModel.Gemini25Pro -> BuildConfig.HOME_AI_KIMI_SMART_MODEL
        }.trim()

    private fun AiPromptRequest.kimiTemperature(model: NoteAiModel): Double =
        if (model.toKimiModelId().equals("kimi-k2.6", ignoreCase = true)) {
            0.6
        } else {
            temperature.toDouble()
        }

    private fun ensureFirebaseReady() {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(context)
            ?: error("Firebase AI is not connected yet. Make sure google-services.json is configured, then try AI Tools again.")
    }

    private companion object {
        const val KimiChatCompletionsEndpoint = "https://api.moonshot.ai/v1/chat/completions"
    }

}


private fun NoteAiAction.isEditorOutputModeForStreaming(): Boolean =
    this == NoteAiAction.StructureOnly ||
        this == NoteAiAction.IntelligentStructure ||
        this == NoteAiAction.CleanFormat ||
        this == NoteAiAction.FormatNote

private fun NoteAiModel.safeGeminiModelNames(): List<String> =
    when (this) {
        NoteAiModel.Gemini25Flash -> listOf("gemini-2.5-flash")
        NoteAiModel.Gemini25Pro -> listOf("gemini-2.5-pro", "gemini-2.5-flash")
    }

private const val IntelligentStructureChunkSize = 25_000
private val StructuredEditorActions = setOf(NoteAiAction.StructureOnly, NoteAiAction.IntelligentStructure)

private fun NoteAiAction.defaultStructureRequest(): String =
    when (this) {
        NoteAiAction.StructureOnly -> "Format this note into polished editor-safe HTML like a professional document formatter. Preserve every original word, sentence, paragraph, quote, Arabic phrase, citation, reference, code line, and repeated wording exactly. Improve headings, spacing, hierarchy, bullet formatting, sectioning, blockquotes, and readability only. Do not delete, summarise, paraphrase, rewrite, simplify, merge away, expand, infer, or add content."
        else -> "Intelligently structure this note."
    }

private fun String.chunkForAi(): List<String> {
    if (length <= IntelligentStructureChunkSize) return listOf(this)
    val chunks = mutableListOf<String>()
    val paragraphs = splitIntoAiBlocks()
    val current = StringBuilder()
    paragraphs.forEach { paragraph ->
        val candidateLength = current.length + paragraph.length + 2
        if (current.isNotEmpty() && candidateLength > IntelligentStructureChunkSize) {
            chunks += current.toString().trim()
            current.clear()
        }
        if (paragraph.length > IntelligentStructureChunkSize) {
            paragraph.splitOversizedBlockSafely(IntelligentStructureChunkSize).forEach { chunks += it.trim() }
        } else {
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
    }
    if (current.isNotEmpty()) chunks += current.toString().trim()
    return chunks.filter { it.isNotBlank() }
}

private fun String.splitIntoAiBlocks(): List<String> {
    val blocks = mutableListOf<String>()
    val current = StringBuilder()
    lineSequence().forEach { line ->
        val trimmed = line.trim()
        val startsNewSection = current.isNotBlank() && trimmed.looksLikeSectionHeading()
        val blankBreak = trimmed.isBlank() && current.isNotBlank()
        if (startsNewSection || blankBreak) {
            blocks += current.toString().trim()
            current.clear()
        }
        if (trimmed.isNotBlank()) {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
    }
    if (current.isNotBlank()) blocks += current.toString().trim()
    return blocks.ifEmpty { listOf(this) }
}

private fun String.looksLikeSectionHeading(): Boolean =
    length in 3..120 &&
        !endsWith(".") &&
        !endsWith(",") &&
        !endsWith("،") &&
        !endsWith(";") &&
        (startsWith("#") || all { it.isLetterOrDigit() || it.isWhitespace() || it in ":-'’()/،" })

private fun String.splitOversizedBlockSafely(maxSize: Int): List<String> {
    val parts = mutableListOf<String>()
    var remaining = trim()
    while (remaining.length > maxSize) {
        val boundary = remaining.safeSplitBoundary(maxSize)
        parts += remaining.substring(0, boundary).trim()
        remaining = remaining.substring(boundary).trimStart()
    }
    if (remaining.isNotBlank()) parts += remaining
    return parts
}

private fun String.safeSplitBoundary(maxSize: Int): Int {
    val search = substring(0, maxSize.coerceAtMost(length))
    val sentenceBoundary = listOf("\n", ". ", "? ", "! ", "؟ ", "۔ ", "؛ ", "; ", "، ")
        .map { search.lastIndexOf(it) }
        .filter { it >= maxSize / 2 }
        .maxOrNull()
    if (sentenceBoundary != null) return sentenceBoundary + 1
    val spaceBoundary = search.lastIndexOf(' ').takeIf { it >= maxSize / 2 }
    return spaceBoundary ?: maxSize.coerceAtMost(length)
}

private fun List<String>.mergeStructuredHtmlChunks(): String {
    val seenTopHeadings = linkedSetOf<String>()
    return mapIndexed { index, chunk ->
        var cleaned = chunk.trim()
            .replace(Regex("(?i)^```html\\s*"), "")
            .replace(Regex("(?i)^```\\s*"), "")
            .replace(Regex("```$"), "")
            .trim()
        cleaned = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(cleaned) { match ->
                val heading = match.groupValues[1].stripHtmlTags().normaliseHeading()
                when {
                    heading.isBlank() -> ""
                    index == 0 && seenTopHeadings.add(heading) -> match.value
                    seenTopHeadings.add(heading) -> "<h2>${match.groupValues[1]}</h2>"
                    else -> ""
                }
            }
        cleaned
    }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.extractHtmlHeadings(): List<String> =
    Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(this)
        .map { it.groupValues[1].stripHtmlTags().trim() }
        .filter { it.isNotBlank() }
        .toList()

private fun String.stripHtmlTags(): String =
    replace(Regex("<[^>]+>"), "")

private fun String.decodeCommonHtmlEntities(): String =
    replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

private fun String.escapeEditorHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun String.normaliseHeading(): String =
    lowercase().replace(Regex("\\s+"), " ").trim()

private fun String.cleanForAction(action: NoteAiAction): String {
    if (action == NoteAiAction.StructureOnly) return cleanEditorHtmlOutput(preferDenseLists = true).trim()
    if (action in StructuredEditorActions) return normalizeIntelligentStructureColors().cleanEditorHtmlOutput().trim()
    if (action == NoteAiAction.CleanFormat || action == NoteAiAction.FormatNote) return trim()
    return stripChatMarkdown(isHtmlOutput = action.isEditorOutputModeForStreaming()).trim()
}

private fun String.ensureStructureOnlyPreservesContent(originalBody: String): String {
    val originalPlain = originalBody.toPlainComparableText()
    val outputPlain = toPlainComparableText()

    if (originalPlain.isBlank()) return this
    if (outputPlain.isBlank()) return originalBody.toStructureOnlyHtml()

    val originalWords = originalPlain.wordCountForPreservation()
    val outputWords = outputPlain.wordCountForPreservation()
    val originalChars = originalPlain.length
    val outputChars = outputPlain.length
    val missingArabicSegments = originalPlain.significantArabicSegments()
        .filterNot { segment -> outputPlain.contains(segment) }
    val missingOriginalSegments = originalBody.structureOnlyPreservationSegments()
        .filterNot { segment -> outputPlain.contains(segment) }

    val wordRatio = if (originalWords == 0) 1.0 else outputWords.toDouble() / originalWords.toDouble()
    val charRatio = if (originalChars == 0) 1.0 else outputChars.toDouble() / originalChars.toDouble()
    val unsafeExpansion = (originalWords >= 80 && wordRatio > 1.35) ||
        (originalChars >= 500 && charRatio > 1.45)

    // StructureOnly is formatting, not summarising. If the model/post-processing omitted a
    // substantial amount of text, never apply the shortened result. Fall back to a conservative
    // local HTML wrapper so the editor never loses content.
    if (wordRatio < 0.96 || charRatio < 0.92 || unsafeExpansion || missingArabicSegments.isNotEmpty() || missingOriginalSegments.isNotEmpty()) {
        if (BuildConfig.DEBUG) {
            Log.w(
                "MyVaultStructureOnly",
                "Rejected unsafe StructureOnly output originalWords=$originalWords outputWords=$outputWords originalChars=$originalChars outputChars=$outputChars wordRatio=$wordRatio charRatio=$charRatio unsafeExpansion=$unsafeExpansion missingArabic=${missingArabicSegments.size} missingSegments=${missingOriginalSegments.size}",
            )
        }
        return originalBody.toStructureOnlyHtml()
            .normalizeEditorHtmlSafety()
            .normalizeListHtmlSafety()
            .trim()
    }

    return this
}

private fun String.toPlainComparableText(): String =
    replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|li|h[1-3]|blockquote)>"), "\n")
        .stripHtmlTags()
        .decodeCommonHtmlEntities()
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.wordCountForPreservation(): Int =
    split(Regex("\\s+")).count { it.isNotBlank() }

private fun String.significantArabicSegments(): List<String> =
    Regex("[\\u0600-\\u06FF][\\u0600-\\u06FF\\s\\u064B-\\u065F\\u0670\\u06D6-\\u06ED،؛؟ـ-]{2,}")
        .findAll(this)
        .map { match -> match.value.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length >= 3 }
        .distinct()
        .take(60)
        .toList()

private fun String.structureOnlyPreservationSegments(): List<String> {
    val source = replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|li|h[1-3]|blockquote)>"), "\n")
        .stripHtmlTags()
        .decodeCommonHtmlEntities()
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    return source.lines()
        .flatMap { line ->
            val segment = line.normalizeStructureOnlySourceSegment()
            when {
                segment.isBlank() -> emptyList()
                segment.length <= 280 -> listOf(segment)
                else -> segment.splitIntoStructureOnlySentenceSegments()
            }
        }
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { segment ->
            segment.length >= 12 &&
                (segment.wordCountForPreservation() >= 3 || segment.contains(Regex("[\\u0600-\\u06FF]")))
        }
        .distinct()
        .take(500)
}

private fun String.normalizeStructureOnlySourceSegment(): String =
    trim()
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^[-•*+]\\s+"), "")
        .replace(Regex("^\\d+[.)]\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.splitIntoStructureOnlySentenceSegments(): List<String> {
    val sentences = Regex("[^.!?؟。]+(?:[.!?؟。]+|$)")
        .findAll(this)
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .flatMap { sentence ->
            if (sentence.length <= 280) listOf(sentence) else sentence.splitIntoStructureOnlyWordWindows()
        }
        .toList()

    return sentences.takeIf { it.isNotEmpty() } ?: splitIntoStructureOnlyWordWindows()
}

private fun String.splitIntoStructureOnlyWordWindows(): List<String> {
    val words = split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    words.forEach { word ->
        if (current.isNotEmpty() && current.length + word.length + 1 > 260) {
            segments += current.toString().trim()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotBlank()) segments += current.toString().trim()
    return segments
}

private fun traceStructureOnlyPrompt(action: NoteAiAction, promptRequest: AiPromptRequest, context: Context) {
    if (action != NoteAiAction.StructureOnly || !BuildConfig.DEBUG) return
    traceStructureOnlyStage(
        action = action,
        stage = "01-final-prompt",
        content = buildString {
            append("SYSTEM:\n")
            append(promptRequest.systemInstruction)
            append("\n\nPROMPT:\n")
            append(promptRequest.prompt)
            append("\n\nTEMPERATURE: ${promptRequest.temperature}")
            append("\nMAX_OUTPUT_TOKENS: ${promptRequest.maxOutputTokens}")
        },
        context = context,
    )
}

private fun traceStructureOnlyRaw(action: NoteAiAction, raw: String, context: Context) {
    if (action != NoteAiAction.StructureOnly || !BuildConfig.DEBUG) return
    traceStructureOnlyStage(
        action = action,
        stage = "02-raw-ai-response",
        content = raw,
        context = context,
    )
}

private fun traceStructureOnlyStage(action: NoteAiAction, stage: String, content: String, context: Context) {
    if (action != NoteAiAction.StructureOnly || !BuildConfig.DEBUG) return
    val listSummary = "ul=${content.contains("<ul", ignoreCase = true)} ol=${content.contains("<ol", ignoreCase = true)} li=${content.contains("<li", ignoreCase = true)}"
    Log.d("MyVaultStructureOnly", "$stage chars=${content.length} $listSummary")
    runCatching {
        val dir = File(context.filesDir, "ai_debug/structure_only").apply { mkdirs() }
        File(dir, "$stage.html").writeText(content, Charsets.UTF_8)
    }.onFailure { error ->
        Log.w("MyVaultStructureOnly", "Unable to save $stage trace: ${error.message}")
    }
}

private fun String.cleanEditorHtmlOutput(preferDenseLists: Boolean = false): String {
    val cleaned = trim()
        .replace(Regex("(?i)^```html\\s*"), "")
        .replace(Regex("(?i)^```\\s*"), "")
        .replace(Regex("```$"), "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s+(.+)$"), "<h2>$1</h2>")
        .replace(Regex("\\*\\*([^\\n*]+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("__([^\\n_]+?)__"), "<strong>$1</strong>")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .normalizeEditorHtmlSafety()
        .normalizeListHtmlSafety()

    val hasEditorHtml = cleaned.contains(
        Regex("<(h1|h2|h3|p|ul|ol|li|blockquote|span|strong|em)\\b", RegexOption.IGNORE_CASE),
    )

    // Important: if the model already returned HTML, do not run semantic paragraph-to-list
    // reconstruction here. That older post-processing could accidentally drop text outside
    // recognised block tags and could reinterpret model semantics incorrectly. StructureOnly
    // must preserve content; the model performs semantic structure, this layer only cleans.
    return if (hasEditorHtml) {
        cleaned
    } else {
        cleaned.toStructureOnlyHtml()
            .normalizeEditorHtmlSafety()
            .normalizeListHtmlSafety()
            .trim()
    }
}

private fun String.normalizeEditorHtmlSafety(): String {
    var output = this
        .replace(Regex("(?i)</h([1-3])\\s*>"), "</h$1>")
        .replace(Regex("(?i)</(p|li|ul|ol|blockquote|strong|em|span)\\s*>"), "</$1>")

    // Repair a common malformed model output where the last heading character lands just
    // outside the closing heading tag, e.g. <h2>Exampl</h2>e<p>...
    output = Regex(
        "<h([1-3])([^>]*)>(.*?)</h\\1>\\s*([\\p{L}\\p{M}\\p{N}])(?=\\s*</?(?:p|h[1-3]|ul|ol|blockquote|br)\\b)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        "<h${match.groupValues[1]}${match.groupValues[2]}>${match.groupValues[3]}${match.groupValues[4]}</h${match.groupValues[1]}>"
    }

    // Headings are structural markers. Flatten their internals so broken nested spans/strong/em
    // cannot produce partial heading ranges in the rich-text importer.
    output = Regex(
        "<h([1-3])[^>]*>(.*?)</h\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        val level = match.groupValues[1]
        val heading = match.groupValues[2].stripHtmlTags().decodeCommonHtmlEntities().escapeEditorHtml().trim()
        if (heading.isBlank()) "" else "<h$level>$heading</h$level>"
    }

    output = output
        .replace(Regex("(?i)<span[^>]*data-color\\s*=\\s*['\"]?(red|blue)['\"]?[^>]*>")) { match ->
            """<span data-color="${match.groupValues[1].lowercase()}">"""
        }
        .replace(Regex("(?i)<span[^>]*dir\\s*=\\s*['\"]?(rtl|ltr)['\"]?[^>]*>")) { match ->
            """<span dir="${match.groupValues[1].lowercase()}">"""
        }
        .replace(Regex("(?i)<span(?!\\s+(?:data-color|dir)=)[^>]*>"), "")
        .replace(Regex("(?i)</span>"), "</span>")
        .replace(Regex("(?i)<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("(?i)</?(div|section|article|font|body|html)[^>]*>"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    return output
}


private fun String.normalizeListHtmlSafety(): String {
    var output = this

    // Keep list items compact and prevent stray line-breaks/paragraphs inside lists from
    // turning into visual gaps or bullet leakage in the editor.
    output = output
        .replace(Regex("(?i)</li>\\s*<br\\s*/?>\\s*<li>"), "</li>\n<li>")
        .replace(Regex("(?i)</li>\\s*<p>\\s*</p>\\s*<li>"), "</li>\n<li>")
        .replace(Regex("(?i)<li>\\s*<p>(.*?)</p>\\s*</li>", setOf(RegexOption.DOT_MATCHES_ALL)), "<li>$1</li>")
        .replace(Regex("(?i)<p>\\s*</p>"), "")
        .replace(Regex("(?i)<br\\s*/?>\\s*(?=</?(ul|ol|li)\\b)"), "")
        .replace(Regex("(?i)(</ul>|</ol>)\\s*(<ul>|<ol>)"), "$1\n$2")

    // Avoid ordered-list misuse leaking from either model output or markdown conversion.
    // StructureOnly's study-note style should default to bullets unless the order is explicit.
    output = Regex(
        "<ol\\b[^>]*>(.*?)</ol>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        val inner = match.groupValues[1]
        val plainItems = Regex("<li\\b[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(inner)
            .map { it.groupValues[1].stripHtmlTags().decodeCommonHtmlEntities().trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (plainItems.isExplicitOrderedTextList()) {
            match.value
        } else {
            "<ul>$inner</ul>"
        }
    }

    return output
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun List<String>.isExplicitOrderedTextList(): Boolean {
    if (isEmpty()) return false
    val labels = map { it.orderedSequenceLabel() }
    if (labels.any { it == null }) return false
    val cleanLabels = labels.filterNotNull().toSet()
    return cleanLabels.any { it in ExplicitOrdinalLabels } ||
        cleanLabels.all { it in SyllogismLabels } ||
        cleanLabels.all { it in PremiseConclusionLabels }
}

private data class EditorHtmlBlock(
    val tag: String,
    val html: String,
    val content: String,
)

private fun String.compactObviousParagraphLists(): String {
    val blockRegex = Regex(
        "<(h[1-3]|p|ul|ol|blockquote)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val matches = blockRegex.findAll(this).toList()
    if (matches.isEmpty()) return this

    val blocks = matches.map { match ->
        EditorHtmlBlock(
            tag = match.groupValues[1].lowercase(),
            html = match.value,
            content = match.value
                .replace(Regex("^<${match.groupValues[1]}\\b[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</${match.groupValues[1]}>$", RegexOption.IGNORE_CASE), "")
                .trim(),
        )
    }

    val output = StringBuilder()
    var index = 0
    while (index < blocks.size) {
        val block = blocks[index]
        if (block.tag != "p") {
            output.append(block.html).append('\n')
            index++
            continue
        }

        val compactRun = mutableListOf<EditorHtmlBlock>()
        var runCursor = index
        while (runCursor < blocks.size && blocks[runCursor].tag == "p" && blocks[runCursor].isCompactListCandidate()) {
            compactRun += blocks[runCursor]
            runCursor++
        }
        val previousBlock = output.lastMeaningfulBlock(blocks, index)
        if (
            compactRun.size >= 2 &&
            (compactRun.isExplicitOrderedSequence() ||
                compactRun.all { it.isTerseStudyPoint() && (compactRun.size >= 3 || previousBlock?.isSectionBoundary() == true) })
        ) {
            output.append(compactRun.toHtmlList(ordered = compactRun.shouldUseOrderedList())).append('\n')
            index = runCursor
            continue
        }

        if (!block.looksLikeListIntroducer()) {
            output.append(block.html).append('\n')
            index++
            continue
        }

        val listItems = mutableListOf<EditorHtmlBlock>()
        var cursor = index + 1
        while (cursor < blocks.size && blocks[cursor].tag == "p" && blocks[cursor].isCompactListCandidate()) {
            listItems += blocks[cursor]
            cursor++
        }

        if (listItems.size >= 2) {
            output.append(block.toIntroParagraphHtml()).append('\n')
            output.append(listItems.toHtmlList(ordered = listItems.shouldUseOrderedList())).append('\n')
            index = cursor
        } else {
            output.append(block.html).append('\n')
            index++
        }
    }

    return output.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun StringBuilder.lastMeaningfulBlock(blocks: List<EditorHtmlBlock>, currentIndex: Int): EditorHtmlBlock? =
    blocks.take(currentIndex).lastOrNull { it.tag != "p" || it.content.stripHtmlTags().decodeCommonHtmlEntities().isNotBlank() }

private fun EditorHtmlBlock.isSectionBoundary(): Boolean =
    tag.startsWith("h") || tag == "blockquote" || looksLikeListIntroducer()

private fun EditorHtmlBlock.toIntroParagraphHtml(): String {
    val plain = content.stripHtmlTags().decodeCommonHtmlEntities().trim()
    if (!plain.endsWith(":")) return html
    if (content.contains(Regex("<strong\\b", RegexOption.IGNORE_CASE))) return html
    return "<p><strong>${content.trim()}</strong></p>"
}

private fun EditorHtmlBlock.looksLikeListIntroducer(): Boolean {
    val text = content.stripHtmlTags().decodeCommonHtmlEntities().trim().lowercase()
    if (text.isBlank()) return false
    if (text.endsWith(":")) return true
    return listOf(
        "includes",
        "include",
        "such as",
        "namely",
        "particularly",
        "especially",
        "assumes",
        "assume",
        "assumption",
        "examples",
        "reasons",
        "consequences",
        "categories",
        "types",
        "forms",
        "stages",
        "steps",
        "premises",
        "consists of",
        "composed of",
        "breaks down into",
        "comprises",
        "made up of",
        "divided into",
        "the following",
        "these are",
        "they are",
        "he argues",
        "this proves",
        "this shows",
    ).any { marker -> text.contains(marker) }
}

private fun EditorHtmlBlock.isCompactListCandidate(): Boolean {
    val text = content.stripHtmlTags().decodeCommonHtmlEntities().trim()
    if (text.isBlank()) return false
    if (text.isMicroHeadingLabel()) return false
    if (text.length > 220) return false
    if (text.count { it == '.' || it == '?' || it == '!' || it == '؟' } > 1) return false
    return true
}

private fun EditorHtmlBlock.isTerseStudyPoint(): Boolean {
    val text = content.stripHtmlTags().decodeCommonHtmlEntities().trim()
    if (text.isBlank()) return false
    if (text.endsWith(":")) return false
    return text.split(Regex("\\s+")).size <= 10
}

private fun List<EditorHtmlBlock>.shouldUseOrderedList(): Boolean =
    isExplicitOrderedSequence()

private fun List<EditorHtmlBlock>.isExplicitOrderedSequence(): Boolean {
    val labels = map { it.content.stripHtmlTags().decodeCommonHtmlEntities().trim().orderedSequenceLabel() }
    if (labels.any { it == null }) return false
    val cleanLabels = labels.filterNotNull().toSet()
    return cleanLabels.any { it in ExplicitOrdinalLabels } ||
        cleanLabels.all { it in SyllogismLabels } ||
        cleanLabels.all { it in PremiseConclusionLabels }
}

private fun String.orderedSequenceLabel(): String? =
    Regex(
        "^(universal|particular|conclusion|premise|major premise|minor premise|step|stage|first|second|third|fourth|fifth|firstly|secondly|thirdly|finally)\\s*[:：-]",
        RegexOption.IGNORE_CASE,
    ).find(this)?.groupValues?.getOrNull(1)?.lowercase()

private fun String.isMicroHeadingLabel(): Boolean {
    val clean = trim().trimEnd(':', '：')
    return clean.matches(
        Regex(
            "^(example|definition|assumption|critique|response|implication|observation|key point|note|summary|benefit|evidence)$",
            RegexOption.IGNORE_CASE,
        ),
    )
}

private fun List<EditorHtmlBlock>.toHtmlList(ordered: Boolean): String {
    val tag = if (ordered) "ol" else "ul"
    return buildString {
        append("<").append(tag).append(">\n")
        this@toHtmlList.forEach { block ->
            append("<li>").append(block.content.trim().normalizeListItemLabel()).append("</li>\n")
        }
        append("</").append(tag).append(">")
    }
}

private fun String.normalizeListItemLabel(): String =
    replace(
        Regex(
            "^((?:universal|particular|conclusion|premise|major premise|minor premise|claim|evidence|response|objection|answer|reason|step|stage|first|second|third|fourth|fifth|firstly|secondly|thirdly|finally|genus|differentia|definition|example|assumption|critique|implication|observation|key point)\\s*[:：-])\\s*(.+)$",
            RegexOption.IGNORE_CASE,
        ),
    ) { match ->
        "<strong>${match.groupValues[1].trim()}</strong> ${match.groupValues[2].trim()}"
    }

private val ExplicitOrdinalLabels = setOf(
    "step",
    "stage",
    "first",
    "second",
    "third",
    "fourth",
    "fifth",
    "firstly",
    "secondly",
    "thirdly",
    "finally",
)

private val SyllogismLabels = setOf("universal", "particular", "conclusion")

private val PremiseConclusionLabels = setOf("premise", "major premise", "minor premise", "conclusion")

private fun String.toStructureOnlyHtml(): String {
    val clean = trim()
    if (clean.isBlank()) return ""

    if (clean.contains(Regex("<(h1|h2|h3|p|ul|ol|li|blockquote|span|strong|em)\\b", RegexOption.IGNORE_CASE))) {
        return clean
    }

    val output = StringBuilder()
    var listType: String? = null
    var paragraph = StringBuilder()

    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun closeParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) {
            output.append("<p>").append(escapeHtml(text)).append("</p>\n")
        }
        paragraph = StringBuilder()
    }

    fun closeList() {
        listType?.let { output.append("</").append(it).append(">\n") }
        listType = null
    }

    fun ensureList(type: String) {
        closeParagraph()
        if (listType != type) {
            closeList()
            output.append("<").append(type).append(">\n")
            listType = type
        }
    }

    fun looksLikeHeading(line: String): Boolean {
        val value = line.trim()
        if (value.length !in 3..90) return false
        if (value.endsWith(".") || value.endsWith(",") || value.endsWith("،") || value.endsWith(":") || value.endsWith("؛")) return false
        if (value.split(Regex("\\s+")).size > 9) return false
        return value.any { it.isLetter() }
    }

    clean.lines().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) {
            closeParagraph()
            closeList()
            return@forEach
        }

        val explicitHeading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
        val bullet = Regex("^[-•*]\\s+(.+)$").matchEntire(line)
        val numbered = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)

        when {
            explicitHeading != null -> {
                closeParagraph()
                closeList()
                val level = explicitHeading.groupValues[1].length.coerceIn(1, 3)
                output.append("<h").append(level).append(">").append(escapeHtml(explicitHeading.groupValues[2].trim())).append("</h").append(level).append(">\n")
            }
            bullet != null -> {
                ensureList("ul")
                output.append("<li>").append(escapeHtml(bullet.groupValues[1].trim())).append("</li>\n")
            }
            numbered != null -> {
                ensureList("ol")
                output.append("<li>").append(escapeHtml(numbered.groupValues[1].trim())).append("</li>\n")
            }
            looksLikeHeading(line) -> {
                closeParagraph()
                closeList()
                output.append("<h2>").append(escapeHtml(line)).append("</h2>\n")
            }
            else -> {
                closeList()
                if (paragraph.isNotBlank()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }

    closeParagraph()
    closeList()

    return output.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.normalizeIntelligentStructureColors(): String {
    var output = this
    val colorSpanRegex = Regex(
        "<span[^>]*data-color\\s*=\\s*['\"]?(green|purple|orange|slate|pink)['\"]?[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    output = colorSpanRegex.replace(output) { match ->
        val inner = match.groupValues[2]
        val plainInner = inner.replace(Regex("<[^>]+>"), "")
        val correctedColor = when {
            plainInner.looksLikeQuranVerse() -> "red"
            plainInner.looksLikeScholarQuote() -> "blue"
            else -> null
        }
        if (correctedColor == null) inner else """<span data-color="$correctedColor">$inner</span>"""
    }
    return output
}

private fun String.looksLikeQuranVerse(): Boolean {
    val value = trim()
    return value.contains(Regex("[\\u0600-\\u06FF]{8,}")) &&
        (value.contains("قال الله") ||
            value.contains("الله تعالى") ||
            value.contains("القرآن") ||
            value.contains("سورة") ||
            value.contains(Regex("\\(\\d{1,3}:\\d{1,3}\\)|\\[\\d{1,3}:\\d{1,3}]")))
}

private fun String.looksLikeScholarQuote(): Boolean {
    val value = lowercase()
    return listOf(
        "ibn taymiyyah",
        "ibn al-qayyim",
        "imam ahmad",
        "ahmad ibn hanbal",
        "al-dhahabi",
        "ibn kathir",
        "al-ashari",
        "al-baqillani",
        "al-juwayni",
        "al-ghazali",
        "al-razi",
        "قال ابن",
        "قال الإمام",
        "ذكر ابن",
    ).any { value.contains(it) }
}

private fun String.stripChatMarkdown(isHtmlOutput: Boolean = false): String {
    var output = trim()
        .replace(Regex("(?m)^```[A-Za-z0-9_-]*\\s*$"), "")
    
    // Only strip bold/italic markdown if this is a plain text chat answer.
    // If it's Editor Output (StructureOnly/IntelligentStructure), we want to preserve markdown 
    // temporarily so it can be parsed into actual <strong> and <em> tags in cleanEditorHtmlOutput.
    if (!isHtmlOutput) {
        output = output
            .replace(Regex("\\*\\*([^\\n*]+?)\\*\\*"), "$1")
            .replace(Regex("__([^\\n_]+?)__"), "$1")
            .replace(Regex("(?<!\\*)\\*([^\\n*]+?)\\*(?!\\*)"), "$1")
            .replace(Regex("(?<!_)_([^\\n_]+?)_(?!_)"), "$1")
    }

    output = output
        .replace(Regex("(?m)^\\s*#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*[*+]\\s+"), "- ")
        .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1 ($2)")

    output = output.lines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            when {
                trimmed.isMarkdownTableDivider() -> null
                trimmed.startsWith("|") && trimmed.endsWith("|") -> trimmed
                    .trim('|')
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" - ")
                else -> line
            }
        }
        .joinToString("\n")

    return output
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.isMarkdownTableDivider(): Boolean =
    matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"))

private fun Throwable.toFriendlyMessage(): String {
    val msg = message ?: ""
    return when {
        this is UnknownHostException -> "Network connection lost. Please check your internet and try again."
        this is SocketException -> "The connection dropped while waiting for the AI. Please try again."
        this is SocketTimeoutException -> "The AI took too long to respond. Please try again."
        this is ConnectException -> "Unable to connect to the server. Please check your internet."
        msg.contains("Unable to resolve host", ignoreCase = true) -> "Network connection lost. Please check your internet."
        msg.contains("Software caused connection abort", ignoreCase = true) -> "The connection was interrupted. Please try again."
        else -> msg.ifBlank { "AI request failed." }
    }
}
