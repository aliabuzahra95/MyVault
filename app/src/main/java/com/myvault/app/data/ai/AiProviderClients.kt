package com.myvault.app.data.ai

import com.myvault.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AiProviderGateway @Inject constructor(
    openAi: OpenAiResearchClient,
    gemini: GeminiResearchClient,
    kimi: KimiResearchClient,
) {
    private val clients = listOf(openAi, gemini, kimi).associateBy(AiProviderClient::provider)

    fun client(provider: AiResearchProvider): AiProviderClient =
        clients[provider] ?: error("No AI provider is configured for ${provider.label}.")

    suspend fun generate(
        provider: AiResearchProvider,
        request: AiGenerationRequest,
        onDelta: suspend (String) -> Unit = {},
    ): AiGenerationResponse = client(provider).generate(request, onDelta)
}

@Singleton
class OpenAiResearchClient @Inject constructor(
    private val credentials: AiProviderCredentialStore,
) : AiProviderClient {
    override val provider = AiResearchProvider.ChatGpt
    override val defaultModel = OpenAiDefaultModel

    override suspend fun generate(
        request: AiGenerationRequest,
        onDelta: suspend (String) -> Unit,
    ): AiGenerationResponse {
        val model = request.safeModel(defaultModel)
        val response = postAiJson(
            provider = provider,
            endpoint = OpenAiResponsesEndpoint,
            credential = credentials.credential(provider),
            headers = mapOf("Authorization" to "Bearer %s"),
            body = buildOpenAiRequest(request, model),
        )
        val text = extractOpenAiText(response).ifBlank {
            throw AiProviderException(provider, AiProviderErrorKind.EmptyResponse, "ChatGPT returned no answer.")
        }
        onDelta(text)
        return AiGenerationResponse(provider, model, text)
    }
}

@Singleton
class GeminiResearchClient @Inject constructor(
    private val credentials: AiProviderCredentialStore,
) : AiProviderClient {
    override val provider = AiResearchProvider.Gemini
    override val defaultModel = GeminiDefaultModel

    override suspend fun generate(
        request: AiGenerationRequest,
        onDelta: suspend (String) -> Unit,
    ): AiGenerationResponse {
        val model = request.safeModel(defaultModel)
        val response = postAiJson(
            provider = provider,
            endpoint = "$GeminiModelsEndpoint/$model:generateContent",
            credential = credentials.credential(provider),
            headers = mapOf("x-goog-api-key" to "%s"),
            body = buildGeminiRequest(request),
        )
        val text = extractGeminiText(response).ifBlank {
            throw AiProviderException(provider, AiProviderErrorKind.EmptyResponse, "Gemini returned no answer.")
        }
        onDelta(text)
        return AiGenerationResponse(provider, model, text)
    }
}

@Singleton
class KimiResearchClient @Inject constructor(
    private val credentials: AiProviderCredentialStore,
) : AiProviderClient {
    override val provider = AiResearchProvider.Kimi
    override val defaultModel = BuildConfig.NOTE_FORMATTING_KIMI_FAST_MODEL.trim().ifBlank { KimiDefaultModel }

    override suspend fun generate(
        request: AiGenerationRequest,
        onDelta: suspend (String) -> Unit,
    ): AiGenerationResponse {
        val model = request.safeModel(defaultModel)
        val response = postAiJson(
            provider = provider,
            endpoint = KimiChatCompletionsEndpoint,
            credential = credentials.credential(provider),
            headers = mapOf("Authorization" to "Bearer %s"),
            body = buildKimiRequest(request, model),
        )
        val text = extractKimiText(response).ifBlank {
            throw AiProviderException(provider, AiProviderErrorKind.EmptyResponse, "Kimi returned no answer.")
        }
        onDelta(text)
        return AiGenerationResponse(provider, model, text)
    }
}

internal fun buildOpenAiRequest(request: AiGenerationRequest, model: String): JSONObject = JSONObject()
    .put("model", model)
    .put("instructions", request.systemInstruction.safeSystemInstruction())
    .put("input", request.prompt.safePrompt())
    .put("max_output_tokens", request.maxOutputTokens.safeMaxOutputTokens())
    .put("reasoning", JSONObject().put("effort", "minimal"))
    .put("store", false)

internal fun buildGeminiRequest(request: AiGenerationRequest): JSONObject = JSONObject()
    .put(
        "system_instruction",
        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemInstruction.safeSystemInstruction()))),
    )
    .put(
        "contents",
        JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", request.prompt.safePrompt()))),
        ),
    )
    .put(
        "generationConfig",
        JSONObject()
            .put("temperature", request.temperature.coerceIn(0.0, 1.0))
            .put("maxOutputTokens", request.maxOutputTokens.safeMaxOutputTokens()),
    )

internal fun buildKimiRequest(request: AiGenerationRequest, model: String): JSONObject = JSONObject()
    .put("model", model)
    .put(
        "messages",
        JSONArray()
            .put(JSONObject().put("role", "system").put("content", request.systemInstruction.safeSystemInstruction()))
            .put(JSONObject().put("role", "user").put("content", request.prompt.safePrompt())),
    )
    .put("temperature", if (model.equals("kimi-k2.6", ignoreCase = true)) 0.6 else request.temperature.coerceIn(0.0, 1.0))
    .put("max_tokens", request.maxOutputTokens.safeMaxOutputTokens())
    .put("thinking", JSONObject().put("type", "disabled"))
    .put("stream", false)

internal fun extractOpenAiText(json: JSONObject): String {
    json.optString("output_text").trim().takeIf(String::isNotBlank)?.let { return it }
    val output = json.optJSONArray("output") ?: return ""
    return buildList {
        for (outputIndex in 0 until output.length()) {
            val content = output.optJSONObject(outputIndex)?.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val item = content.optJSONObject(contentIndex) ?: continue
                if (item.optString("type") == "output_text") item.optString("text").trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.joinToString("\n")
}

internal fun extractGeminiText(json: JSONObject): String {
    val candidates = json.optJSONArray("candidates") ?: return ""
    return buildList {
        for (candidateIndex in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(candidateIndex)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (partIndex in 0 until parts.length()) {
                parts.optJSONObject(partIndex)?.optString("text")?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.joinToString("\n")
}

internal fun extractKimiText(json: JSONObject): String =
    json.optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
        .orEmpty()
        .trim()

private suspend fun postAiJson(
    provider: AiResearchProvider,
    endpoint: String,
    credential: String,
    headers: Map<String, String>,
    body: JSONObject,
): JSONObject = withContext(Dispatchers.IO) {
    if (credential.isBlank()) {
        throw AiProviderException(
            provider,
            AiProviderErrorKind.MissingCredential,
            "${provider.label} is not configured on this device.",
        )
    }
    currentCoroutineContext().ensureActive()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = ConnectTimeoutMillis
        readTimeout = ReadTimeoutMillis
        doOutput = true
        useCaches = false
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        headers.forEach { (name, template) -> setRequestProperty(name, template.format(credential)) }
    }
    val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
        if (cause is CancellationException) connection.disconnect()
    }
    try {
        connection.outputStream.use { it.write(body.toString().encodeToByteArray()) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.use { it.readUtf8Bounded(MaxAiResponseBytes) }.orEmpty()
        if (status !in 200..299) throw provider.httpError(status, responseBody)
        runCatching { JSONObject(responseBody) }.getOrElse { cause ->
            throw AiProviderException(
                provider,
                AiProviderErrorKind.MalformedResponse,
                "${provider.label} returned an unreadable response.",
                cause = cause,
            )
        }
    } catch (error: AiProviderException) {
        throw error
    } catch (error: SocketTimeoutException) {
        throw AiProviderException(provider, AiProviderErrorKind.Timeout, "${provider.label} timed out. Try again.", cause = error)
    } catch (error: UnknownHostException) {
        throw AiProviderException(provider, AiProviderErrorKind.Offline, "No network connection is available.", cause = error)
    } finally {
        cancellationHandle.dispose()
        connection.disconnect()
    }
}

private fun AiResearchProvider.httpError(status: Int, responseBody: String): AiProviderException {
    val kind = when (status) {
        401, 403 -> AiProviderErrorKind.Unauthorized
        429 -> AiProviderErrorKind.RateLimited
        in 500..599 -> AiProviderErrorKind.ProviderUnavailable
        else -> AiProviderErrorKind.ProviderUnavailable
    }
    val fallback = when (kind) {
        AiProviderErrorKind.Unauthorized -> "$label rejected its device credential."
        AiProviderErrorKind.RateLimited -> "$label is temporarily rate limited. Try again shortly."
        else -> "$label request failed (HTTP $status)."
    }
    val safeDetail = runCatching {
        val error = JSONObject(responseBody).optJSONObject("error")
        error?.optString("message")?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()?.take(MaxProviderErrorCharacters)
    return AiProviderException(this, kind, safeDetail ?: fallback, status)
}

private fun AiGenerationRequest.safeModel(defaultModel: String): String =
    model?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) } ?: defaultModel

private fun String.safeSystemInstruction(): String = trim().take(MaxSystemInstructionCharacters)

private fun String.safePrompt(): String {
    val clean = trim()
    require(clean.isNotEmpty()) { "AI prompt cannot be empty." }
    return clean.take(MaxPromptCharacters)
}

private fun Int.safeMaxOutputTokens(): Int = coerceIn(64, MaxOutputTokens)

private fun InputStream.readUtf8Bounded(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IllegalStateException("AI provider response exceeded the safe size limit.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray().decodeToString()
}

private const val OpenAiResponsesEndpoint = "https://api.openai.com/v1/responses"
private const val GeminiModelsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
private const val KimiChatCompletionsEndpoint = "https://api.moonshot.ai/v1/chat/completions"
private const val OpenAiDefaultModel = "gpt-5-mini"
private const val GeminiDefaultModel = "gemini-2.5-flash"
private const val KimiDefaultModel = "kimi-k2.6"
private const val ConnectTimeoutMillis = 20_000
private const val ReadTimeoutMillis = 90_000
private const val MaxAiResponseBytes = 4 * 1024 * 1024
private const val MaxPromptCharacters = 48_000
private const val MaxSystemInstructionCharacters = 8_000
private const val MaxOutputTokens = 8_192
private const val MaxProviderErrorCharacters = 300
