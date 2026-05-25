package com.myvault.app.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.generationConfig
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

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
) {
    Gemini25Flash("Gemini 3.5 Flash", "gemini-3.5-flash"),
    Gemini3Pro("Gemini 3.1 Pro Preview", "gemini-3.1-pro-preview"),
}

enum class NoteAiProvider(
    val displayName: String,
) {
    Gemini("Gemini"),
    ChatGPT("ChatGPT"),
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
        return generated.cleanForAction(action)
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
        if (provider != NoteAiProvider.Gemini || action.isEditorOutputModeForStreaming()) {
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

        ensureFirebaseReady()
        onProgress?.invoke("Thinking...")
        val promptRequest = AiPromptBuilder.build(
            action = action,
            title = title,
            body = body,
            question = question,
            history = history,
            provider = NoteAiProvider.Gemini,
            model = model,
        )
        generateWithGeminiPromptStream(model, promptRequest).collect { chunk ->
            emit(chunk)
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
        }
        return generated.stripChatMarkdown().trim()
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

                Preserve this chunk's original order and meaning.
                Maintain consistent heading hierarchy, formatting style, and colour usage.
                Avoid repeated Introduction, Overview, Main Topic, or duplicate top-level headings.
                Do not include a generic <h1> for every chunk.
                Preserve continuity with the previous chunk.
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
        return generateWithGeminiPrompt(model, promptRequest)
    }

    private suspend fun generatePrompt(
        action: NoteAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        promptRequest: AiPromptRequest,
        title: String,
        body: String,
        question: String,
    ): String =
        when (provider) {
            NoteAiProvider.Gemini -> {
                ensureFirebaseReady()
                generateWithGeminiPrompt(model, promptRequest)
            }
            NoteAiProvider.ChatGPT -> generateWithChatGptPrompt(action, model, promptRequest, title, body, question)
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
        val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = model.modelName,
                generationConfig = config,
            )
        val response = runCatching {
            generativeModel.generateContent(promptRequest.prompt)
        }.getOrElse { error ->
            if (error is ResponseStoppedException) {
                val partial = error.response.text?.trim().orEmpty()
                if (partial.isNotBlank()) {
                    return partial + "\n\n[Gemini stopped because the answer reached its output limit. The useful partial answer above was kept.]"
                }
                val reason = error.response.candidates.firstOrNull()?.finishReason?.name ?: "unknown"
                throw IllegalStateException(
                    if (reason == "MAX_TOKENS") {
                        "Gemini reached its answer length limit before returning text. Try the fast model, shorten the request, or ask for a smaller section."
                    } else {
                        "Gemini stopped before finishing. Reason: $reason."
                    },
                )
            }
            throw error
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
        val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = model.modelName,
                generationConfig = config,
            )
        var emittedAnyText = false
        runCatching {
            generativeModel.generateContentStream(promptRequest.prompt).collect { response ->
                val chunk = response.text.orEmpty()
                if (chunk.isNotBlank()) {
                    emittedAnyText = true
                    emit(chunk)
                }
            }
        }.getOrElse { error ->
            if (error is ResponseStoppedException) {
                val partial = error.response.text?.trim().orEmpty()
                if (partial.isNotBlank()) {
                    emittedAnyText = true
                    emit(partial)
                }
                emit("\n\n[Response paused because Gemini reached its output limit. Tap Continue to keep going.]")
                return@flow
            }
            throw error
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
        return generateWithChatGptPrompt(action, model, promptRequest, title, body, question)
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
            throw IllegalStateException(error.message ?: "ChatGPT request failed.")
        }.also {
            connection.disconnect()
    }
}

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
        NoteAiAction.StructureOnly -> 0
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
            NoteAiModel.Gemini3Pro -> "smart"
        }

    private fun ensureFirebaseReady() {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(context)
            ?: error("Firebase AI is not connected yet. Make sure google-services.json is configured, then try AI Tools again.")
    }

}


private fun NoteAiAction.isEditorOutputModeForStreaming(): Boolean =
    this == NoteAiAction.StructureOnly ||
        this == NoteAiAction.IntelligentStructure ||
        this == NoteAiAction.CleanFormat ||
        this == NoteAiAction.FormatNote

private const val IntelligentStructureChunkSize = 7_000
private val StructuredEditorActions = setOf(NoteAiAction.StructureOnly, NoteAiAction.IntelligentStructure)

private fun NoteAiAction.defaultStructureRequest(): String =
    when (this) {
        NoteAiAction.StructureOnly -> "Structure and format this note without adding, removing, or rewriting any words."
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

private fun String.normaliseHeading(): String =
    lowercase().replace(Regex("\\s+"), " ").trim()

private fun String.cleanForAction(action: NoteAiAction): String {
    if (action == NoteAiAction.StructureOnly) return trim()
    if (action in StructuredEditorActions) return normalizeIntelligentStructureColors().trim()
    if (action == NoteAiAction.CleanFormat || action == NoteAiAction.FormatNote) return trim()
    return stripChatMarkdown().trim()
}


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

private fun String.stripChatMarkdown(): String {
    var output = trim()
        .replace(Regex("(?m)^```[A-Za-z0-9_-]*\\s*$"), "")
        .replace(Regex("\\*\\*([^\\n*]+?)\\*\\*"), "$1")
        .replace(Regex("__([^\\n_]+?)__"), "$1")
        .replace(Regex("(?<!\\*)\\*([^\\n*]+?)\\*(?!\\*)"), "$1")
        .replace(Regex("(?<!_)_([^\\n_]+?)_(?!_)"), "$1")
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
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun String.isMarkdownTableDivider(): Boolean =
    matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"))
