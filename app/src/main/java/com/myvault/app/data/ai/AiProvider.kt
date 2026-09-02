package com.myvault.app.data.ai

interface AiProviderClient {
    val provider: AiResearchProvider
    val defaultModel: String

    suspend fun generate(
        request: AiGenerationRequest,
        onDelta: suspend (String) -> Unit = {},
    ): AiGenerationResponse
}

data class AiGenerationRequest(
    val systemInstruction: String,
    val prompt: String,
    val maxOutputTokens: Int = 2_048,
    val temperature: Double = 0.2,
    val model: String? = null,
    val stream: Boolean = false,
)

data class AiGenerationResponse(
    val provider: AiResearchProvider,
    val model: String,
    val text: String,
)

enum class AiProviderErrorKind {
    MissingCredential,
    Unauthorized,
    RateLimited,
    Timeout,
    Offline,
    ProviderUnavailable,
    MalformedResponse,
    EmptyResponse,
}

class AiProviderException(
    val provider: AiResearchProvider,
    val kind: AiProviderErrorKind,
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
