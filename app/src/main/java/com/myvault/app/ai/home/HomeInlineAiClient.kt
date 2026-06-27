package com.myvault.app.ai.home

import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeInlineAiClient @Inject constructor() {
    fun streamText(
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
        webSearchEnabled: Boolean = false,
        geminiFiles: List<GeminiFileReference> = emptyList(),
    ): Flow<String> = flow {
        val config = resolveConfig(
            provider = provider,
            modelMode = modelMode,
            systemInstruction = systemInstruction,
            prompt = prompt,
            webSearchEnabled = webSearchEnabled,
            geminiFiles = geminiFiles,
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
            if (provider.requiresBearerAuth()) {
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
            HomeAiProvider.KIMI -> when (modelMode) {
                HomeAiModelMode.FAST -> BuildConfig.HOME_AI_KIMI_FAST_MODEL
                HomeAiModelMode.SMART -> BuildConfig.HOME_AI_KIMI_SMART_MODEL
            }
        }.trim()

    fun isLikelyStaleGeminiFileError(error: HomeInlineAiError): Boolean {
        val detail = error.userMessage
        val mentionsFileReference = detail.contains("file", ignoreCase = true) ||
            detail.contains("uri", ignoreCase = true)
        val mentionsExpiredOrInvalidReference = detail.contains("expired", ignoreCase = true) ||
            detail.contains("not found", ignoreCase = true) ||
            detail.contains("does not exist", ignoreCase = true) ||
            detail.contains("invalid", ignoreCase = true)
        return mentionsFileReference && mentionsExpiredOrInvalidReference
    }

    suspend fun uploadGeminiFile(file: File, displayName: String, mimeType: String): GeminiUploadedFile =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val apiKey = HomeAiProvider.GEMINI.apiKey()
            if (apiKey.isBlank()) throw HomeInlineAiException(HomeInlineAiError.AuthFailure)
            if (!file.exists() || !file.isFile) {
                throw HomeInlineAiException(HomeInlineAiError.Unknown("PDF file could not be found on this device."))
            }
            if (file.length() > GeminiPdfMaxBytes) {
                throw HomeInlineAiException(HomeInlineAiError.MaxTokenBoundsExceeded)
            }

            val uploadUrl = startGeminiUpload(
                apiKey = apiKey,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = file.length(),
            )
            finalizeGeminiUpload(
                uploadUrl = uploadUrl,
                file = file,
                mimeType = mimeType,
            )
        }

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
            HomeAiProvider.KIMI -> parseOpenAiChatDelta(data, HomeAiProvider.KIMI)
        }
    }

    private fun parseOpenAiDelta(data: String): String? {
        val event = runCatching { JSONObject(data) }.getOrNull() ?: return null
        event.optJSONObject("error")?.let { throw HomeInlineAiException(it.toHomeAiError(HomeAiProvider.OPENAI, null)) }
        return when (event.optString("type")) {
            "response.output_text.delta" -> event.optString("delta").takeIf { it.isNotEmpty() }
            "response.failed" -> throw HomeInlineAiException(HomeInlineAiError.Unknown("OpenAI request failed."))
            else -> event.optString("delta").takeIf { it.isNotEmpty() }
        }
    }

    private fun parseOpenAiChatDelta(data: String, provider: HomeAiProvider): String? {
        val event = runCatching { JSONObject(data) }.getOrNull() ?: return null
        event.optJSONObject("error")?.let { throw HomeInlineAiException(it.toHomeAiError(provider, null)) }
        val choices = event.optJSONArray("choices") ?: return null
        return buildString {
            for (index in 0 until choices.length()) {
                val delta = choices.optJSONObject(index)?.optJSONObject("delta")
                val text = delta?.optString("content").orEmpty()
                if (text.isNotEmpty()) append(text)
            }
        }.takeIf { it.isNotEmpty() }
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
                if (text.isNotEmpty()) append(text)
            }
        }.takeIf { it.isNotEmpty() }
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
                    if (text.isNotEmpty()) append(text)
                }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun resolveConfig(
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
        systemInstruction: String = "",
        prompt: String = "",
        webSearchEnabled: Boolean = false,
        geminiFiles: List<GeminiFileReference> = emptyList(),
    ): ProviderRequestConfig {
        val apiKey = provider.apiKey()
        val modelId = resolveModelId(provider, modelMode)
        val endpointUrl = when (provider) {
            HomeAiProvider.OPENAI -> OpenAiResponsesEndpoint
            HomeAiProvider.KIMI -> KimiChatCompletionsEndpoint
            HomeAiProvider.GEMINI -> {
                val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
                "$GeminiBaseEndpoint/$modelId:streamGenerateContent?alt=sse&key=$encodedKey"
            }
        }
        val requestBody = when (provider) {
            HomeAiProvider.OPENAI -> openAiRequestBody(modelId, modelMode, systemInstruction, prompt, webSearchEnabled)
            HomeAiProvider.KIMI -> openAiCompatibleChatRequestBody(
                provider = provider,
                modelId = modelId,
                modelMode = modelMode,
                systemInstruction = systemInstruction,
                prompt = prompt,
                webSearchEnabled = webSearchEnabled,
            )
            HomeAiProvider.GEMINI -> geminiRequestBody(modelMode, systemInstruction, prompt, webSearchEnabled, geminiFiles)
        }
        return ProviderRequestConfig(
            provider = provider,
            modelMode = modelMode,
            modelId = modelId,
            endpointType = when (provider) {
                HomeAiProvider.OPENAI -> "OpenAI responses streaming"
                HomeAiProvider.GEMINI -> "Gemini streamGenerateContent"
                HomeAiProvider.KIMI -> "Kimi chat completions streaming"
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
        webSearchEnabled: Boolean,
    ): String =
        JSONObject()
            .put("model", modelId)
            .put("instructions", systemInstruction.withWebSearchInstruction(webSearchEnabled))
            .put("input", prompt)
            .put("max_output_tokens", if (modelMode == HomeAiModelMode.FAST) 1_600 else 3_200)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_obfuscation", false))
            .apply {
                if (webSearchEnabled) put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
            }
            .toString()

    private fun openAiCompatibleChatRequestBody(
        provider: HomeAiProvider,
        modelId: String,
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
        webSearchEnabled: Boolean,
    ): String =
        JSONObject()
            .put("model", modelId)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemInstruction.withWebSearchInstruction(provider, webSearchEnabled)))
                    .put(JSONObject().put("role", "user").put("content", prompt)),
            )
            .put("temperature", provider.temperatureForOpenAiCompatibleChat(modelId, modelMode))
            .put("max_tokens", if (modelMode == HomeAiModelMode.FAST) 1_600 else 3_200)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("stream", true)
            .toString()

    private fun geminiRequestBody(
        modelMode: HomeAiModelMode,
        systemInstruction: String,
        prompt: String,
        webSearchEnabled: Boolean,
        files: List<GeminiFileReference> = emptyList(),
    ): String {
        val generationConfig = JSONObject()
            .put("temperature", if (modelMode == HomeAiModelMode.FAST) 0.35 else 0.28)
            .put("maxOutputTokens", if (modelMode == HomeAiModelMode.FAST) 1_600 else 3_200)

        if (modelMode == HomeAiModelMode.FAST) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }

        val parts = JSONArray()
        files.forEach { file ->
            parts.put(
                JSONObject().put(
                    "fileData",
                    JSONObject()
                        .put("mimeType", file.mimeType)
                        .put("fileUri", file.fileUri),
                ),
            )
        }
        parts.put(JSONObject().put("text", "${systemInstruction.withWebSearchInstruction(webSearchEnabled)}\n\n$prompt"))

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", parts),
                ),
            )
            .put("generationConfig", generationConfig)
            .apply {
                if (webSearchEnabled) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            .toString()
    }

    private fun startGeminiUpload(
        apiKey: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
    ): String {
        val connection = (URL(GeminiUploadEndpoint).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            connection.setRequestProperty("X-Goog-Upload-Command", "start")
            connection.setRequestProperty("X-Goog-Upload-Header-Content-Length", sizeBytes.toString())
            connection.setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType)
            val body = JSONObject()
                .put("file", JSONObject().put("display_name", displayName.take(512)))
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                throw HomeInlineAiException(connection.toGeminiFileError(phase = "upload_start"))
            }
            return connection.getHeaderField("X-Goog-Upload-URL")
                ?.takeIf { it.isNotBlank() }
                ?: throw HomeInlineAiException(HomeInlineAiError.Unknown("Gemini did not return a PDF upload URL."))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun finalizeGeminiUpload(uploadUrl: String, file: File, mimeType: String): GeminiUploadedFile {
        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", file.length().toString())
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("X-Goog-Upload-Offset", "0")
            connection.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
            file.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            if (connection.responseCode !in 200..299) {
                throw HomeInlineAiException(connection.toGeminiFileError(phase = "upload_finalize"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body).optJSONObject("file") ?: JSONObject(body)
            val uploaded = GeminiUploadedFile(
                name = json.optString("name"),
                uri = json.optString("uri"),
                mimeType = json.optString("mimeType").ifBlank { mimeType },
                displayName = json.optString("displayName").ifBlank { file.name },
                expirationTimeMs = json.optString("expirationTime").toEpochMsOrNull()
                    ?: (System.currentTimeMillis() + GeminiFileTtlFallbackMs),
                state = json.optString("state"),
            )
            return waitForGeminiFile(uploaded)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun waitForGeminiFile(uploaded: GeminiUploadedFile): GeminiUploadedFile {
        if (!uploaded.state.equals("PROCESSING", ignoreCase = true)) return uploaded
        repeat(10) {
            delay(900)
            val current = getGeminiFile(uploaded.name)
            when {
                current.state.equals("ACTIVE", ignoreCase = true) -> return current
                current.state.equals("FAILED", ignoreCase = true) -> {
                    throw HomeInlineAiException(HomeInlineAiError.Unknown("Gemini could not prepare this PDF. Try another PDF."))
                }
            }
        }
        return uploaded
    }

    private fun getGeminiFile(name: String): GeminiUploadedFile {
        val apiKey = HomeAiProvider.GEMINI.apiKey()
        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/$name").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("x-goog-api-key", apiKey)
            if (connection.responseCode !in 200..299) {
                throw HomeInlineAiException(connection.toGeminiFileError(phase = "file_status"))
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return GeminiUploadedFile(
                name = json.optString("name"),
                uri = json.optString("uri"),
                mimeType = json.optString("mimeType"),
                displayName = json.optString("displayName"),
                expirationTimeMs = json.optString("expirationTime").toEpochMsOrNull()
                    ?: (System.currentTimeMillis() + GeminiFileTtlFallbackMs),
                state = json.optString("state"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.toGeminiFileError(phase: String): HomeInlineAiError {
        val body = errorStream?.bufferedReader()?.use { it.readText().take(SafeErrorBodyLimit) }.orEmpty()
        val providerMessage = body.safeProviderMessage()
        val detail = buildSafeDiagnostic(
            provider = HomeAiProvider.GEMINI,
            modelId = null,
            endpointType = "Gemini Files API",
            statusCode = responseCode,
            providerMessage = providerMessage,
            phase = phase,
        )
        return when (responseCode) {
            400 -> HomeInlineAiError.RequestFormatRejected.withDebug(detail)
            401, 403 -> HomeInlineAiError.AuthFailure.withDebug(detail)
            408 -> HomeInlineAiError.NetworkDead.withDebug(detail)
            429, 500, 502, 503, 504 -> HomeInlineAiError.ModelOverloaded.withDebug(detail)
            else -> HomeInlineAiError.Unknown(detail)
        }
    }

    private fun String.toEpochMsOrNull(): Long? =
        try {
            Instant.parse(this).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
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
            endpointType = when (provider) {
                HomeAiProvider.GEMINI -> "Gemini streamGenerateContent"
                HomeAiProvider.KIMI -> "Kimi chat completions streaming"
                HomeAiProvider.OPENAI -> "OpenAI responses streaming"
            },
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
            HomeAiProvider.KIMI -> DirectKimiApiKeyOverride.ifBlank { BuildConfig.KIMI_API_KEY }
        }.trim()

    private fun HomeAiProvider.maskedKeyDisplay(): String {
        val key = apiKey()
        if (key.isBlank()) return "${label} key: not configured"
        val prefix = if (this.requiresBearerAuth() && key.startsWith("sk-")) "sk-" else key.take(4)
        return "${label} key: $prefix••••••••••••${key.takeLast(4)}"
    }

    private fun String.withWebSearchInstruction(provider: HomeAiProvider, enabled: Boolean): String =
        when {
            !enabled -> this
            provider == HomeAiProvider.KIMI -> this + "\nWeb search was requested, but Kimi direct mode in MyVault does not have a native web-search tool. Use only the supplied context and say when current web verification would be needed."
            else -> this + "\nWeb search is enabled for this reply. Use current web results when helpful, and include source links for web-backed claims."
        }

    private fun String.withWebSearchInstruction(enabled: Boolean): String =
        if (!enabled) {
            this
        } else {
            this + "\nWeb search is enabled for this reply. Use current web results when helpful, and include source links for web-backed claims."
        }

    private fun HomeAiProvider.requiresBearerAuth(): Boolean =
        this == HomeAiProvider.OPENAI || this == HomeAiProvider.KIMI

    private fun HomeAiProvider.temperatureForOpenAiCompatibleChat(
        modelId: String,
        modelMode: HomeAiModelMode,
    ): Double =
        if (this == HomeAiProvider.KIMI && modelId.equals("kimi-k2.6", ignoreCase = true)) {
            0.6
        } else if (modelMode == HomeAiModelMode.FAST) {
            0.35
        } else {
            0.28
        }

    private fun String.sanitizeSecrets(): String =
        replace(Regex("AIza[0-9A-Za-z_\\-]+"), "AIza••••")
            .replace(Regex("AQ\\.[A-Za-z0-9_\\-]+"), "AQ.••••")
            .replace(Regex("sk-[A-Za-z0-9_\\-]+"), "sk-••••")
            .replace(Regex("sk-[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "sk-••••")
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
        const val KimiChatCompletionsEndpoint = "https://api.moonshot.ai/v1/chat/completions"
        const val GeminiBaseEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
        const val GeminiUploadEndpoint = "https://generativelanguage.googleapis.com/upload/v1beta/files"
        const val SafeErrorBodyLimit = 2_000
        const val GeminiPdfMaxBytes = 50L * 1024L * 1024L
        const val GeminiFileTtlFallbackMs = 47L * 60L * 60L * 1000L

        // Private single-device overrides. Prefer local.properties; leave blank unless needed.
        const val DirectOpenAiApiKeyOverride = ""
        const val DirectGeminiApiKeyOverride = ""
        const val DirectKimiApiKeyOverride = ""
    }
}

class HomeInlineAiException(val aiError: HomeInlineAiError) : RuntimeException(aiError.userMessage)

data class GeminiFileReference(
    val fileUri: String,
    val mimeType: String,
)

data class GeminiUploadedFile(
    val name: String,
    val uri: String,
    val mimeType: String,
    val displayName: String,
    val expirationTimeMs: Long,
    val state: String = "",
)
