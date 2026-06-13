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

@Singleton
class NarrationProgressStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("azure_narration_progress", Context.MODE_PRIVATE)
    private val _progress = MutableStateFlow(loadAll())
    val progress: StateFlow<Map<String, AzureNarrationProgress>> = _progress.asStateFlow()

    fun get(sourceId: String): AzureNarrationProgress? = _progress.value[sourceId]

    fun save(progress: AzureNarrationProgress) {
        val updated = _progress.value.toMutableMap().apply { put(progress.sourceId, progress) }
        preferences.edit().putString(progress.sourceId, progress.toJson().toString()).apply()
        _progress.value = updated
    }

    fun clear(sourceId: String) {
        if (sourceId !in _progress.value) return
        preferences.edit().remove(sourceId).apply()
        _progress.value = _progress.value - sourceId
    }

    private fun loadAll(): Map<String, AzureNarrationProgress> =
        preferences.all.mapNotNull { (sourceId, value) ->
            val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
            json.toProgress(sourceId)?.let { sourceId to it }
        }.toMap()
}

private fun AzureNarrationProgress.toJson(): JSONObject = JSONObject()
    .put("cacheKey", cacheKey)
    .put("positionMs", positionMs)
    .put("durationMs", durationMs)
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
