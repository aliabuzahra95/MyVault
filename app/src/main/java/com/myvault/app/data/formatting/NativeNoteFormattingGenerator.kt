package com.myvault.app.data.formatting

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import com.myvault.app.BuildConfig
import com.myvault.app.data.supabase.SupabaseConfig
import com.myvault.app.data.supabase.SupabaseSession
import com.myvault.app.data.supabase.SupabaseSessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

internal data class NoteFormattingPrompt(
    val systemInstruction: String,
    val prompt: String,
    val temperature: Float,
    val maxOutputTokens: Int,
)

internal fun interface NoteFormattingProviderGateway {
    suspend fun generate(
        request: NoteFormattingRequest,
        prompt: NoteFormattingPrompt,
        requestBody: String,
        requestQuestion: String,
    ): String
}

internal fun interface NoteFormattingTrace {
    fun record(request: NoteFormattingRequest, stage: String, content: String)
}

/**
 * Native formatting-only orchestrator. It contains no chat history, messages,
 * tutoring state, selected-text actions, continuation, or streaming UI state.
 */
@Singleton
internal class NativeNoteFormattingGenerator @Inject constructor(
    private val gateway: NoteFormattingProviderGateway,
    private val trace: NoteFormattingTrace,
) : NoteFormattingGenerator {

    override suspend fun generate(
        request: NoteFormattingRequest,
        onProgress: (String) -> Unit,
    ): String {
        val generated = if (
            request.action in StructuredFormattingActions &&
            request.body.length > FormattingChunkSize
        ) {
            generateInChunks(request, onProgress)
        } else {
            generateOnce(request, onProgress = onProgress)
        }
        if (request.action in StructuredFormattingActions) {
            onProgress("Validating wording...")
            withContext(Dispatchers.Default) { FormattingTextContract.requirePreserved(request.body, generated) }
        }
        // Validated structural HTML must not pass through the old heuristic text-repair fallback.
        val preserved = if (request.action in StructuredFormattingActions) {
            generated.trim().removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
        } else NoteFormattingOutputEngine.prepareOutput(
            action = request.action,
            generated = generated,
            originalBody = request.body,
        )
        withContext(Dispatchers.IO) { trace.record(request, "03-cleaned-html-after-sanitizer", preserved) }
        return preserved
    }

    private suspend fun generateOnce(
        request: NoteFormattingRequest,
        question: String = "",
        onProgress: (String) -> Unit = {},
    ): String {
        for (attempt in 0..1) {
            val instruction = if (attempt == 0) question else """
                $question
                The previous output failed validation. Retry from the original source only.
                Keep all wording and repeated occurrences in exactly the same order.
                Use minimal h2, p, blockquote and ul markup; no colours or new labels.
                Escape literal ampersands as &amp; and close every tag.
            """.trimIndent()
            val prompt = NoteFormattingPromptBuilder.build(request, instruction)
            withContext(Dispatchers.IO) { trace.record(request, "01-final-prompt", prompt.toTraceText()) }
            val raw = gateway.generate(request, prompt, request.body, instruction)
            withContext(Dispatchers.IO) { trace.record(request, "02-raw-ai-response", raw) }
            if (request.action !in StructuredFormattingActions) return raw
            try {
                onProgress("Validating wording...")
                withContext(Dispatchers.Default) { FormattingTextContract.requirePreserved(request.body, raw) }
                return raw
            } catch (error: NoteFormattingException) {
                if (attempt == 1) throw error
                onProgress("Retrying unchanged wording...")
            }
        }
        error("Unable to validate formatting. Your note is unchanged.")
    }

    private suspend fun generateInChunks(
        request: NoteFormattingRequest,
        onProgress: (String) -> Unit,
    ): String {
        val chunks = NoteFormattingOutputEngine.chunkSource(request.body)
        onProgress("Creating structure plan...")
        val planPrompt = NoteFormattingPromptBuilder.buildPlan(request)
        val structuralPlan = gateway.generate(
            request = request,
            prompt = planPrompt,
            requestBody = request.body.take(FormattingChunkSize),
            requestQuestion = "Create internal structural plan.",
        )
        val processedChunks = mutableListOf<String>()
        chunks.forEachIndexed { index, chunk ->
            onProgress("Processing part ${index + 1} of ${chunks.size}...")
            val previousContext = processedChunks
                .takeLast(1)
                .joinToString("\n")
                .extractFormattingHeadings()
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

                Preserve every original word in this chunk and every repeated occurrence exactly as written.
                Do not delete, summarise, shorten, paraphrase, rewrite, simplify, merge away, deduplicate, replace, or correct any source wording.
                Do not add headings or connective labels. Promote existing text to headings without duplication.
                Maintain consistent heading hierarchy, formatting style, and colour usage with earlier chunks.
                Avoid repeated Introduction, Overview, Main Topic, or duplicate top-level headings.
                Do not include a generic <h1> for every chunk.
                If this chunk continues a prior section, continue that structure instead of restarting.
                Use extracted phrases/concepts already present in this chunk for headings.
                Use lists for grouped concepts or ordered argument flow, but keep related sentences together instead of inflating whitespace.
                User request: ${request.action.defaultFormattingRequest()}
            """.trimIndent()
            val chunkRequest = request.copy(
                title = "${request.title} - part ${index + 1} of ${chunks.size}",
                body = chunk,
            )
            val processed = runCatching {
                generateOnce(chunkRequest, chunkQuestion, onProgress)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                error(
                    "Formatting failed while processing part ${index + 1} of ${chunks.size}. " +
                        "Your note was not changed. ${error.message.orEmpty()}".trim(),
                )
            }
            processedChunks += processed
        }
        return processedChunks.mergeFormattingChunks()
    }
}

@Singleton
internal class DefaultNoteFormattingTrace @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NoteFormattingTrace {
    override fun record(request: NoteFormattingRequest, stage: String, content: String) {
        if (request.action != NoteFormattingAction.StructureOnly || !BuildConfig.DEBUG) return
        val listSummary = "ul=${content.contains("<ul", ignoreCase = true)} ol=${content.contains("<ol", ignoreCase = true)} li=${content.contains("<li", ignoreCase = true)}"
        Log.d("MyVaultStructureOnly", "$stage chars=${content.length} $listSummary")
        runCatching {
            val dir = File(context.filesDir, "ai_debug/structure_only").apply { mkdirs() }
            File(dir, "$stage.html").writeText(content, Charsets.UTF_8)
        }.onFailure { error ->
            Log.w("MyVaultStructureOnly", "Unable to save $stage trace: ${error.message}")
        }
    }
}

/** The actual Gemini, ChatGPT and Kimi formatting transports. */
@Singleton
internal class DefaultNoteFormattingProviderGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionStore: SupabaseSessionStore,
) : NoteFormattingProviderGateway {

    override suspend fun generate(
        request: NoteFormattingRequest,
        prompt: NoteFormattingPrompt,
        requestBody: String,
        requestQuestion: String,
    ): String = when (request.provider) {
        NoteFormattingProvider.Gemini -> generateWithGemini(request.model, prompt)
        NoteFormattingProvider.ChatGPT -> generateWithChatGpt(request, prompt, requestBody, requestQuestion)
        NoteFormattingProvider.Kimi -> generateWithKimi(request.model, prompt)
    }

    private suspend fun generateWithGemini(
        model: NoteFormattingModel,
        prompt: NoteFormattingPrompt,
    ): String {
        ensureFirebaseReady()
        val config = generationConfig {
            temperature = prompt.temperature
            topP = 0.9f
            maxOutputTokens = prompt.maxOutputTokens
        }
        var lastFailure: Throwable? = null
        val response = model.safeGeminiModelNames().firstNotNullOfOrNull { modelName ->
            val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = modelName, generationConfig = config,
                    systemInstruction = content { text(prompt.systemInstruction) })
            runCatching { generativeModel.generateContent(prompt.prompt) }
                .onFailure { if (it is CancellationException) throw it else lastFailure = it }
                .getOrNull()
        } ?: run {
            val error = lastFailure ?: error("Gemini request failed before a response was returned.")
            if (error is ResponseStoppedException || error.message?.contains("MAX_TOKENS") == true) {
                throw IllegalStateException(
                    "Gemini reached its output limit. No partial result was applied. Try again with another quality or provider.",
                )
            }
            throw IllegalStateException(error.toFormattingFriendlyMessage())
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

    private suspend fun generateWithChatGpt(
        request: NoteFormattingRequest,
        prompt: NoteFormattingPrompt,
        requestBody: String,
        requestQuestion: String,
    ): String = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) error("Supabase is not configured yet.")
        val session = authenticatedSupabaseSession()
        if (!session.isSignedIn) error("Sign in to your Supabase account first, then try ChatGPT again.")

        val connection = URL("${SupabaseConfig.url}/functions/v1/myvault-ai").openConnection() as HttpURLConnection
        runCatching {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            val bodyJson = buildChatGptFormattingRequestBody(
                request = request,
                prompt = prompt,
                requestBody = requestBody,
                requestQuestion = requestQuestion,
            )
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
            json.optString("text").trim().ifBlank { error("ChatGPT did not return any text. Please try again.") }
        }.also {
            connection.disconnect()
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            throw IllegalStateException(error.toFormattingFriendlyMessage())
        }
    }

    private suspend fun generateWithKimi(
        model: NoteFormattingModel,
        prompt: NoteFormattingPrompt,
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
            val bodyJson = buildKimiFormattingRequestBody(model, prompt)
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            val json = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                error(json.kimiFormattingErrorMessage().ifBlank { "Kimi request failed. HTTP ${connection.responseCode}." })
            }
            json.extractKimiFormattingText().ifBlank { error("Kimi did not return any text. Please try again.") }
        }.also {
            connection.disconnect()
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            throw IllegalStateException(error.toFormattingFriendlyMessage())
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
            val bodyJson = JSONObject().put("refresh_token", current.refreshToken).toString()
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "Supabase session refresh failed. Please sign in again." }
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
        }.also { connection.disconnect() }
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

internal fun buildKimiFormattingRequestBody(
    model: NoteFormattingModel,
    prompt: NoteFormattingPrompt,
): String = JSONObject()
    .put("model", model.toKimiModelId())
    .put(
        "messages",
        JSONArray()
            .put(JSONObject().put("role", "system").put("content", prompt.systemInstruction))
            .put(JSONObject().put("role", "user").put("content", prompt.prompt)),
    )
    .put("temperature", if (model.toKimiModelId().equals("kimi-k2.6", ignoreCase = true)) 0.6 else prompt.temperature.toDouble())
    .put("max_tokens", prompt.maxOutputTokens)
    .put("thinking", JSONObject().put("type", "disabled"))
    .put("stream", false)
    .toString()

internal fun buildChatGptFormattingRequestBody(
    request: NoteFormattingRequest,
    prompt: NoteFormattingPrompt,
    requestBody: String,
    requestQuestion: String,
): String = JSONObject()
    .put("action", request.action.toFunctionAction())
    .put("model", request.model.toFunctionModel())
    .put("title", request.title)
    .put("body", requestBody.scopedForFormattingFunctionPayload(request.action))
    .put("question", requestQuestion)
    .put("systemInstruction", prompt.systemInstruction)
    .put("prompt", prompt.prompt)
    .put("temperature", prompt.temperature.toDouble())
    .put("maxOutputTokens", prompt.maxOutputTokens)
    .toString()

private fun NoteFormattingAction.toFunctionAction(): String = when (this) {
    NoteFormattingAction.StructureOnly,
    NoteFormattingAction.FormatNote,
    -> "format_note"
    NoteFormattingAction.IntelligentStructure,
    NoteFormattingAction.CleanFormat,
    -> "organise"
}

private fun NoteFormattingModel.toFunctionModel(): String = when (this) {
    NoteFormattingModel.Fast -> "fast"
    NoteFormattingModel.Smart -> "smart"
}

private fun NoteFormattingModel.toKimiModelId(): String = when (this) {
    NoteFormattingModel.Fast -> BuildConfig.NOTE_FORMATTING_KIMI_FAST_MODEL
    NoteFormattingModel.Smart -> BuildConfig.NOTE_FORMATTING_KIMI_SMART_MODEL
}.trim()

private fun NoteFormattingModel.safeGeminiModelNames(): List<String> = when (this) {
    NoteFormattingModel.Fast -> listOf("gemini-2.5-flash")
    NoteFormattingModel.Smart -> listOf("gemini-2.5-pro", "gemini-2.5-flash")
}

private fun String.scopedForFormattingFunctionPayload(action: NoteFormattingAction): String {
    val maxChars = when (action) {
        NoteFormattingAction.StructureOnly -> Int.MAX_VALUE
        NoteFormattingAction.IntelligentStructure -> Int.MAX_VALUE
        NoteFormattingAction.CleanFormat,
        NoteFormattingAction.FormatNote,
        -> 10_000
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

private fun NoteFormattingAction.defaultFormattingRequest(): String = when (this) {
    NoteFormattingAction.StructureOnly -> "Format this note into polished editor-safe HTML like a professional document formatter. Preserve every original word, sentence, paragraph, quote, Arabic phrase, citation, reference, code line, and repeated wording exactly. Improve headings, spacing, hierarchy, bullet formatting, sectioning, blockquotes, and readability only. Do not delete, summarise, paraphrase, rewrite, simplify, merge away, expand, infer, or add content."
    NoteFormattingAction.IntelligentStructure -> "Intelligently structure this note as a lossless document formatter. Preserve every original word and repeated occurrence exactly as written. Add headings, grouping, lists, and presentation only. Never delete, summarise, shorten, paraphrase, rewrite, simplify, merge away, deduplicate, replace, or correct source wording."
    else -> "Intelligently structure this note."
}

private fun List<String>.mergeFormattingChunks(): String {
    return mapIndexed { index, chunk ->
        var cleaned = chunk.trim()
            .replace(Regex("(?i)^```html\\s*"), "")
            .replace(Regex("(?i)^```\\s*"), "")
            .replace(Regex("```$"), "")
            .trim()
        cleaned = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(cleaned) { match ->
                if (index == 0) match.value else "<h2>${match.groupValues[1]}</h2>"
            }
        cleaned
    }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.extractFormattingHeadings(): List<String> =
    Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(this)
        .map { it.groupValues[1].stripFormattingHtml().trim() }
        .filter { it.isNotBlank() }
        .toList()

private fun String.stripFormattingHtml(): String = replace(Regex("<[^>]+>"), "")

private fun NoteFormattingPrompt.toTraceText(): String = buildString {
    append("SYSTEM:\n")
    append(systemInstruction)
    append("\n\nPROMPT:\n")
    append(prompt)
    append("\n\nTEMPERATURE: $temperature")
    append("\nMAX_OUTPUT_TOKENS: $maxOutputTokens")
}

private fun JSONObject.extractKimiFormattingText(): String {
    val choices = optJSONArray("choices") ?: return ""
    return buildString {
        for (index in 0 until choices.length()) {
            val content = choices.optJSONObject(index)?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isNotEmpty()) append(content)
        }
    }.trim()
}

private fun JSONObject.kimiFormattingErrorMessage(): String =
    optJSONObject("error")?.optString("message").orEmpty().ifBlank { optString("message") }

internal fun Throwable.toFormattingFriendlyMessage(): String {
    val message = message.orEmpty()
    return when {
        message.contains("max RPM", ignoreCase = true) || message.contains("rate limit", ignoreCase = true) ||
            message.contains("HTTP 429", ignoreCase = true) -> "The provider's request limit was reached. Wait a minute, then retry. Your note is unchanged."
        this is UnknownHostException -> "Network connection lost. Please check your internet and try again."
        this is SocketException -> "The connection dropped while waiting for the AI. Please try again."
        this is SocketTimeoutException -> "The AI took too long to respond. Please try again."
        this is ConnectException -> "Unable to connect to the server. Please check your internet."
        message.contains("Unable to resolve host", ignoreCase = true) -> "Network connection lost. Please check your internet."
        message.contains("Software caused connection abort", ignoreCase = true) -> "The connection was interrupted. Please try again."
        else -> message.ifBlank { "Note formatting failed." }
    }
}

private const val FormattingChunkSize = 12_000
private val StructuredFormattingActions = setOf(
    NoteFormattingAction.StructureOnly,
    NoteFormattingAction.IntelligentStructure,
)
