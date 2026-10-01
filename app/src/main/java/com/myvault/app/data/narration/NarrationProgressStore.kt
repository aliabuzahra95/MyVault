package com.myvault.app.data.narration

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AzureNarrationProgress(
    val sourceId: String,
    val cacheKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val activeSentence: String,
    val updatedAt: Long,
)

data class NarrationProgress(
    val sourceId: String,
    val cacheKey: String,
    val contentHash: String = "",
    val provider: String = "",
    val model: String = "",
    val voice: String = "",
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float = 1f,
    val activeSentence: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun isStaleFor(currentContentHash: String): Boolean =
        contentHash.isNotBlank() && currentContentHash.isNotBlank() && contentHash != currentContentHash

    fun toAzureProgress(): AzureNarrationProgress =
        AzureNarrationProgress(sourceId, cacheKey, positionMs, durationMs, activeSentence, updatedAt)
}

@Singleton
class NarrationProgressStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("azure_narration_progress", Context.MODE_PRIVATE)
    private val _progress = MutableStateFlow(loadAll())
    val progress: StateFlow<Map<String, AzureNarrationProgress>> = _progress.asStateFlow()

    private val _unifiedProgress = MutableStateFlow(loadAllUnified())
    val unifiedProgress: StateFlow<Map<String, NarrationProgress>> = _unifiedProgress.asStateFlow()

    fun get(sourceId: String): AzureNarrationProgress? = _progress.value[sourceId]

    fun getUnified(sourceId: String): NarrationProgress? = _unifiedProgress.value[sourceId]

    fun save(progress: AzureNarrationProgress) {
        saveUnified(
            NarrationProgress(
                sourceId = progress.sourceId,
                cacheKey = progress.cacheKey,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                activeSentence = progress.activeSentence,
                updatedAt = progress.updatedAt,
            )
        )
    }

    fun saveUnified(progress: NarrationProgress) {
        val updatedUnified = _unifiedProgress.value.toMutableMap().apply { put(progress.sourceId, progress) }
        val updatedAzure = _progress.value.toMutableMap().apply { put(progress.sourceId, progress.toAzureProgress()) }
        preferences.edit().putString(progress.sourceId, progress.toJson().toString()).apply()
        _unifiedProgress.value = updatedUnified
        _progress.value = updatedAzure
    }

    fun clear(sourceId: String) {
        if (sourceId !in _progress.value && sourceId !in _unifiedProgress.value) return
        preferences.edit().remove(sourceId).apply()
        _progress.value = _progress.value - sourceId
        _unifiedProgress.value = _unifiedProgress.value - sourceId
    }

    private fun loadAll(): Map<String, AzureNarrationProgress> =
        preferences.all.mapNotNull { (sourceId, value) ->
            val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
            json.toProgress(sourceId)?.let { sourceId to it }
        }.toMap()

    private fun loadAllUnified(): Map<String, NarrationProgress> =
        preferences.all.mapNotNull { (sourceId, value) ->
            val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
            json.toUnifiedProgress(sourceId)?.let { sourceId to it }
        }.toMap()
}

private fun NarrationProgress.toJson(): JSONObject = JSONObject()
    .put("cacheKey", cacheKey)
    .put("contentHash", contentHash)
    .put("provider", provider)
    .put("model", model)
    .put("voice", voice)
    .put("positionMs", positionMs)
    .put("durationMs", durationMs)
    .put("speed", speed.toDouble())
    .put("activeSentence", activeSentence)
    .put("updatedAt", updatedAt)

private fun JSONObject.toProgress(sourceId: String): AzureNarrationProgress? {
    val cacheKey = optString("cacheKey")
    if (cacheKey.isBlank()) return null
    return AzureNarrationProgress(
        sourceId = sourceId,
        cacheKey = cacheKey,
        positionMs = optLong("positionMs").coerceAtLeast(0L),
        durationMs = optLong("durationMs").coerceAtLeast(0L),
        activeSentence = optString("activeSentence"),
        updatedAt = optLong("updatedAt"),
    )
}

private fun JSONObject.toUnifiedProgress(sourceId: String): NarrationProgress? {
    val cacheKey = optString("cacheKey")
    if (cacheKey.isBlank()) return null
    return NarrationProgress(
        sourceId = sourceId,
        cacheKey = cacheKey,
        contentHash = optString("contentHash"),
        provider = optString("provider"),
        model = optString("model"),
        voice = optString("voice"),
        positionMs = optLong("positionMs").coerceAtLeast(0L),
        durationMs = optLong("durationMs").coerceAtLeast(0L),
        speed = optDouble("speed", 1.0).toFloat(),
        activeSentence = optString("activeSentence"),
        updatedAt = optLong("updatedAt"),
    )
}
