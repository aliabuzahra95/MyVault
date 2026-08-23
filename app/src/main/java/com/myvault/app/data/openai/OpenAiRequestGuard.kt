package com.myvault.app.data.openai

import android.util.Log
import com.myvault.app.BuildConfig

object OpenAiRequestGuard {
    private const val LogTag = "MyVaultOpenAI"
    private const val ListenModeEndpoint = "https://api.openai.com/v1/audio/speech"
    private const val ListenModeModel = "gpt-4o-mini-tts"

    private val forbiddenEndpointParts = listOf(
        "realtime",
        "transcriptions",
        "translations",
    )
    private val forbiddenModelParts = listOf(
        "realtime",
        "whisper",
    )

    fun logCacheDecision(
        featureName: String,
        endpointUrl: String,
        model: String,
        noteId: String? = null,
        characterCount: Int? = null,
        cacheStatus: String,
    ) {
        validate(featureName = featureName, endpointUrl = endpointUrl, model = model)
        if (BuildConfig.DEBUG) {
            Log.i(
                LogTag,
                buildLogMessage(
                    event = "cache",
                    endpointUrl = endpointUrl,
                    model = model,
                    featureName = featureName,
                    noteId = noteId,
                    characterCount = characterCount,
                    cacheStatus = cacheStatus,
                ),
            )
        }
    }

    fun validateAndLogRequest(
        featureName: String,
        endpointUrl: String,
        model: String,
        noteId: String? = null,
        characterCount: Int? = null,
        cacheStatus: String,
    ) {
        validate(featureName = featureName, endpointUrl = endpointUrl, model = model)
        if (BuildConfig.DEBUG) {
            Log.w(
                LogTag,
                buildLogMessage(
                    event = "request",
                    endpointUrl = endpointUrl,
                    model = model,
                    featureName = featureName,
                    noteId = noteId,
                    characterCount = characterCount,
                    cacheStatus = cacheStatus,
                ),
            )
        }
    }

    private fun validate(featureName: String, endpointUrl: String, model: String) {
        val endpointLower = endpointUrl.lowercase()
        val modelLower = model.lowercase()
        val blockedEndpoint = forbiddenEndpointParts.firstOrNull { endpointLower.contains(it) }
        if (blockedEndpoint != null) {
            error("Blocked forbidden OpenAI endpoint for $featureName: $blockedEndpoint")
        }
        val blockedModel = forbiddenModelParts.firstOrNull { modelLower.contains(it) }
        if (blockedModel != null) {
            error("Blocked forbidden OpenAI model for $featureName: $blockedModel")
        }
        if (featureName == OpenAiFeature.ListenMode) {
            if (endpointUrl != ListenModeEndpoint) {
                error("Listen Mode may only use /v1/audio/speech.")
            }
            if (model != ListenModeModel) {
                error("Listen Mode may only use gpt-4o-mini-tts.")
            }
        }
    }

    private fun buildLogMessage(
        event: String,
        endpointUrl: String,
        model: String,
        featureName: String,
        noteId: String?,
        characterCount: Int?,
        cacheStatus: String,
    ): String = buildString {
        append("OpenAI ").append(event)
        append(" endpoint=").append(endpointUrl)
        append(" model=").append(model)
        append(" feature=").append(featureName)
        append(" timestamp=").append(System.currentTimeMillis())
        append(" cache=").append(cacheStatus)
        noteId?.let { append(" noteId=").append(it) }
        characterCount?.let { append(" chars=").append(it) }
        append(" caller=").append(callerFrame())
    }

    private fun callerFrame(): String = Throwable().stackTrace
        .firstOrNull { frame ->
            val name = frame.className
            name.startsWith("com.myvault.app") &&
                !name.contains("OpenAiRequestGuard")
        }
        ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
        ?: "unknown"
}

object OpenAiFeature {
    const val ListenMode = "ListenMode"
}
