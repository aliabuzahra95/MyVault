package com.myvault.app.ai.home

import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeInlineAiClient @Inject constructor() {
    fun streamText(
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
    ): Flow<String> = flow {
        val config = resolveConfig(
            provider = provider,
            modelMode = modelMode,
            systemInstruction = systemInstruction,
            prompt = prompt,
        )
        if (!config.configured) throw HomeInlineAiException(HomeInlineAiError.AuthFailure)

        val connection = (URL(config.endpointUrl).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            if (provider == HomeAiProvider.OPENAI) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }

            connection.outputStream.use { stream ->
                stream.write(config.requestBody.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode !in 200..299) {
                throw HomeInlineAiException(connection.toHomeAiError(config, phase = "before_stream"))
            }

            readProviderStream(connection, config) { delta ->
                emit(delta)
            }
        } catch (error: HomeInlineAiException) {
            throw error
        } catch (error: Throwable) {
            throw HomeInlineAiException(error.toHomeAiError())
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun providerStatuses(): List<HomeAiProviderStatus> =
        HomeAiProvider.entries.map { provider ->
            val key = provider.apiKey()
            HomeAiProviderStatus(
                provider = provider,
                configured = key.isNotBlank(),
                implemented = true,
                maskedKeyLabel = provider.maskedKeyDisplay(),
            )
        }

    fun maskedKeyLabel(provider: HomeAiProvider): String = provider.maskedKeyDisplay()

    fun resolveModelId(provider: HomeAiProvider, modelMode: HomeAiModelMode): String =
        when (provider) {
            HomeAiProvider.OPENAI -> when (modelMode) {
                HomeAiModelMode.FAST -> BuildConfig.HOME_AI_OPENAI_FAST_MODEL
                HomeAiModelMode.SMART -> BuildConfig.HOME_AI_OPENAI_SMART_MODEL
            }
            HomeAiProvider.GEMINI -> when (modelMode) {
                HomeAiModelMode.FAST -> BuildConfig.HOME_AI_GEMINI_FAST_MODEL
                HomeAiModelMode.SMART -> BuildConfig.HOME_AI_GEMINI_SMART_MODEL
            }
        }.trim()

    private suspend fun readProviderStream(
        connection: HttpURLConnection,
        config: ProviderRequestConfig,
        emitDelta: suspend (String) -> Unit,
    ) {
        val dataBuffer = StringBuilder()
        connection.inputStream.bufferedReader().use { reader ->
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                when {
                    line.isBlank() -> {
                        parseProviderDelta(dataBuffer.toString().trim(), config)?.let { emitDelta(it) }
                        dataBuffer.clear()
                    }
                    line.startsWith("data:") -> {
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(line.removePrefix("data:").trim())
                    }
                    config.provider == HomeAiProvider.GEMINI -> {
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(line.trim())
                    }
                }
            }
        }
        parseProviderDelta(dataBuffer.toString().trim(), config)?.let { emitDelta(it) }
    }

    private fun parseProviderDelta(data: String, config: ProviderRequestConfig): String? {
        if (data.isBlank() || data == "[DONE]") return null
        return when (config.provider) {
            HomeAiProvider.OPENAI -> parseOpenAiDelta(data)
            HomeAiProvider.GEMINI -> parseGeminiDelta(data)
        }
    }

    private fun parseOpenAiDelta(data: String): String? {
        val event = runCatching { JSONObject(data) }.getOrNull() ?: return null
        event.optJSONObject("error")?.let { throw HomeInlineAiException(it.toHomeAiError(HomeAiProvider.OPENAI, null)) }
        return when (event.optString("type")) {
            "response.output_text.delta" -> event.optString("delta").takeIf { it.isNotBlank() }
            "response.failed" -> throw HomeInlineAiException(HomeInlineAiError.Unknown("OpenAI request failed."))
            else -> event.optString("delta").takeIf { it.isNotBlank() }
        }
    }

    private fun parseGeminiDelta(data: String): String? {
        runCatching { JSONObject(data) }.getOrNull()?.let { event ->
            event.optJSONObject("error")?.let { throw HomeInlineAiException(it.toHomeAiError(HomeAiProvider.GEMINI, null)) }
            return event.extractGeminiText()
        }
        val events = runCatching { JSONArray(data) }.getOrNull() ?: return null
        return buildString {
            for (index in 0 until events.length()) {
                val text = events.optJSONObject(index)?.extractGeminiText().orEmpty()
                if (text.isNotBlank()) append(text)
            }
        }.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.extractGeminiText(): String? {
        val candidates = optJSONArray("candidates") ?: return null
        return buildString {
            for (candidateIndex in 0 until candidates.length()) {
                val parts = candidates
                    .optJSONObject(candidateIndex)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: continue
                for (partIndex in 0 until parts.length()) {
                    val text = parts.optJSONObject(partIndex)?.optString("text").orEmpty()
                    if (text.isNotBlank()) append(text)
                }
            }
        }.takeIf { it.isNotBlank() }
    }

    private fun resolveConfig(
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
        systemInstruction: String = "",
        prompt: String = "",
    ): ProviderRequestConfig {
        val apiKey = provider.apiKey()
        val modelId = resolveModelId(provider, modelMode)
        val endpointUrl = when (provider) {
            HomeAiProvider.OPENAI -> OpenAiResponsesEndpoint
            HomeAiProvider.GEMINI -> {
                val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
                "$GeminiBaseEndpoint/$modelId:streamGenerateContent?alt=sse&key=$encodedKey"
            }
        }
        val requestBody = when (provider) {
            HomeAiProvider.OPENAI -> openAiRequestBody(modelId, modelMode, systemInstruction, prompt)
            HomeAiProvider.GEMINI -> geminiRequestBody(modelMode, systemInstruction, prompt)
        }
        return ProviderRequestConfig(
            provider = provider,
            modelMode = modelMode,
            modelId = modelId,
            endpointType = when (provider) {
                HomeAiProvider.OPENAI -> "OpenAI responses streaming"
                HomeAiProvider.GEMINI -> "Gemini streamGenerateContent"
            },
            endpointUrl = endpointUrl,
            apiKey = apiKey,
            requestBody = requestBody,
            configured = apiKey.isNotBlank() && modelId.isNotBlank(),
        )
    }

    private fun openAiRequestBody(
        modelId: String,
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
    ): String =
        JSONObject()
            .put("model", modelId)
            .put("instructions", systemInstruction)
            .put("input", prompt)
            .put("max_output_tokens", if (modelMode == HomeAiModelMode.FAST) 1_600 else 3_200)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_obfuscation", false))
            .toString()

    private fun geminiRequestBody(
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
    ): String {
        val generationConfig = JSONObject()
            .put("temperature", if (modelMode == HomeAiModelMode.FAST) 0.35 else 0.28)
            .put("maxOutputTokens", if (modelMode == HomeAiModelMode.FAST) 1_600 else 3_200)

        if (modelMode == HomeAiModelMode.FAST) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "$systemInstruction\n\n$prompt")),
                    ),
                ),
            )
            .put("generationConfig", generationConfig)
            .toString()
    }

    private fun HttpURLConnection.toHomeAiError(
        config: ProviderRequestConfig,
        phase: String,
    ): HomeInlineAiError {
        val body = errorStream?.bufferedReader()?.use { it.readText().take(SafeErrorBodyLimit) }.orEmpty()
        val providerMessage = body.safeProviderMessage()
        val detail = buildSafeDiagnostic(
            provider = config.provider,
            modelId = config.modelId,
            endpointType = config.endpointType,
            statusCode = responseCode,
            providerMessage = providerMessage,
            phase = phase,
        )
        return when (responseCode) {
            400 -> if (providerMessage.containsTokenOrContextError()) {
                HomeInlineAiError.MaxTokenBoundsExceeded
            } else {
                HomeInlineAiError.RequestFormatRejected.withDebug(detail)
            }
            401, 403 -> HomeInlineAiError.AuthFailure.withDebug(detail)
            404 -> HomeInlineAiError.ModelNotFound.withDebug(detail)
            408 -> HomeInlineAiError.NetworkDead.withDebug(detail)
            429, 500, 502, 503, 504 -> HomeInlineAiError.ModelOverloaded.withDebug(detail)
            else -> HomeInlineAiError.Unknown(detail)
        }
    }

    private fun JSONObject.toHomeAiError(provider: HomeAiProvider, statusCode: Int?): HomeInlineAiError {
        val message = optString("message").ifBlank {
            optJSONObject("error")?.optString("message").orEmpty()
        }
        val detail = buildSafeDiagnostic(
            provider = provider,
            modelId = null,
            endpointType = if (provider == HomeAiProvider.GEMINI) "Gemini streamGenerateContent" else "OpenAI responses streaming",
            statusCode = statusCode,
            providerMessage = message,
            phase = "during_stream",
        )
        return when {
            message.containsTokenOrContextError() -> HomeInlineAiError.MaxTokenBoundsExceeded
            message.contains("auth", ignoreCase = true) ||
                message.contains("api key", ignoreCase = true) ||
                message.contains("permission", ignoreCase = true) -> HomeInlineAiError.AuthFailure.withDebug(detail)
            message.contains("rate", ignoreCase = true) ||
                message.contains("quota", ignoreCase = true) ||
                message.contains("overloaded", ignoreCase = true) -> HomeInlineAiError.ModelOverloaded.withDebug(detail)
            else -> HomeInlineAiError.Unknown(detail)
        }
    }

    private fun Throwable.toHomeAiError(): HomeInlineAiError =
        when (this) {
            is UnknownHostException,
            is SocketTimeoutException,
            is SocketException,
            -> HomeInlineAiError.NetworkDead
            else -> HomeInlineAiError.Unknown(message.orEmpty().sanitizeSecrets())
        }

    private fun HomeInlineAiError.withDebug(detail: String): HomeInlineAiError =
        if (BuildConfig.DEBUG) HomeInlineAiError.Unknown("${userMessage} $detail") else this

    private fun String.safeProviderMessage(): String =
        runCatching {
            val root = JSONObject(this)
            root.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { root.optString("message") }
        }.getOrDefault(this)
            .sanitizeSecrets()
            .take(360)

    private fun String.containsTokenOrContextError(): Boolean =
        contains("token", ignoreCase = true) ||
            contains("context", ignoreCase = true) ||
            contains("too large", ignoreCase = true)

    private fun buildSafeDiagnostic(
        provider: HomeAiProvider,
        modelId: String?,
        endpointType: String,
        statusCode: Int?,
        providerMessage: String,
        phase: String,
    ): String {
        val status = statusCode?.let { " ($it)" }.orEmpty()
        val model = modelId?.takeIf { it.isNotBlank() }?.let { " model=$it" }.orEmpty()
        val message = providerMessage.takeIf { it.isNotBlank() }?.let { " message=${it.sanitizeSecrets()}" }.orEmpty()
        return "${provider.label} request failed$status. endpoint=$endpointType$model phase=$phase$message"
    }

    private fun HomeAiProvider.apiKey(): String =
        when (this) {
            HomeAiProvider.OPENAI -> DirectOpenAiApiKeyOverride.ifBlank { BuildConfig.OPENAI_API_KEY }
            HomeAiProvider.GEMINI -> DirectGeminiApiKeyOverride.ifBlank { BuildConfig.GEMINI_API_KEY }
        }.trim()

    private fun HomeAiProvider.maskedKeyDisplay(): String {
        val key = apiKey()
        if (key.isBlank()) return "${label} key: not configured"
        val prefix = if (this == HomeAiProvider.OPENAI) "sk-" else key.take(4)
        return "${label} key: $prefix••••••••••••${key.takeLast(4)}"
    }

    private fun String.sanitizeSecrets(): String =
        replace(Regex("AIza[0-9A-Za-z_\\-]+"), "AIza••••")
            .replace(Regex("AQ\\.[A-Za-z0-9_\\-]+"), "AQ.••••")
            .replace(Regex("sk-[A-Za-z0-9_\\-]+"), "sk-••••")
            .replace(Regex("key=[^&\\s,}]+", RegexOption.IGNORE_CASE), "key=••••")
            .replace(Regex("Bearer\\s+[^\\s,}]+", RegexOption.IGNORE_CASE), "Bearer ••••")

    private data class ProviderRequestConfig(
        val provider: HomeAiProvider,
        val modelMode: HomeAiModelMode,
        val modelId: String,
        val endpointType: String,
        val endpointUrl: String,
        val apiKey: String,
        val requestBody: String,
        val configured: Boolean,
    )

    private companion object {
        const val OpenAiResponsesEndpoint = "https://api.openai.com/v1/responses"
        const val GeminiBaseEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
        const val SafeErrorBodyLimit = 2_000

        // Private single-device overrides. Prefer local.properties; leave blank unless needed.
        const val DirectOpenAiApiKeyOverride = ""
        const val DirectGeminiApiKeyOverride = ""
    }
}

class HomeInlineAiException(val aiError: HomeInlineAiError) : RuntimeException(aiError.userMessage)
