package com.myvault.app.data.narration

import java.io.File

object NarrationConfig {
    const val MODEL = "gpt-4o-mini-tts"
    const val DEFAULT_VOICE = "coral"
    const val RESPONSE_FORMAT = "mp3"
    const val MAX_CHARS_PER_CHUNK = 1_400
    const val MAX_TOTAL_CHARS = 25_000
    val VoiceOptions = listOf("coral", "onyx", "alloy", "nova")
    val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f)
}

enum class NarrationPlaybackStatus {
    Idle,
    Preparing,
    Generating,
    Playing,
    Paused,
    Stopped,
    Error,
}

data class NarrationUiState(
    val status: NarrationPlaybackStatus = NarrationPlaybackStatus.Idle,
    val noteId: String? = null,
    val noteTitle: String = "",
    val label: String = "",
    val error: String? = null,
    val speed: Float = 1f,
    val voice: String = NarrationConfig.DEFAULT_VOICE,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val totalPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
) {
    val isActive: Boolean
        get() = status != NarrationPlaybackStatus.Idle
}

data class NarrationSession(
    val cacheKey: String,
    val noteId: String,
    val noteTitle: String,
    val model: String,
    val voice: String,
    val speed: Float,
    val contentHash: String,
    val files: List<File>,
)
