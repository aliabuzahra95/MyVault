package com.myvault.app.data.narration

import java.io.File

object NarrationConfig {
    const val MODEL = "gpt-4o-mini-tts"
    const val DEFAULT_VOICE = "cedar"
    const val RESPONSE_FORMAT = "mp3"
    const val MAX_CHARS_PER_CHUNK = 1_400
    const val MAX_TOTAL_CHARS = 25_000
    val VoiceOptions = listOf("cedar", "marin")
    val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f)
}

object AzureNarrationConfig {
    const val DEFAULT_REGION = "australiaeast"
    const val DEFAULT_VOICE = "en-AU-NatashaNeural"
    const val DEFAULT_ARABIC_VOICE = "ar-SA-HamedNeural"
    val EnglishVoiceOptions = listOf(
        "en-AU-NatashaNeural",
        "en-AU-WilliamNeural",
        "en-US-JennyNeural",
        "en-US-GuyNeural",
    )
    val ArabicVoiceOptions = listOf(
        "ar-SA-HamedNeural",
        "ar-SA-ZariyahNeural",
        "ar-EG-SalmaNeural",
        "ar-EG-ShakirNeural",
    )
    val VoiceOptions = EnglishVoiceOptions + ArabicVoiceOptions
}

enum class NarrationProvider(val storedValue: String, val label: String) {
    Device("device", "Device TTS"),
    Azure("azure", "Azure Speech TTS"),
    OpenAi("openai", "OpenAI TTS");

    companion object {
        fun fromStoredValue(value: String): NarrationProvider =
            entries.firstOrNull { it.storedValue == value } ?: Device
    }
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
    val activeSentence: String = "",
) {
    val isActive: Boolean
        get() = status != NarrationPlaybackStatus.Idle
}

data class NarrationCue(
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val textStart: Int,
    val textEnd: Int,
    val text: String,
    val displayText: String = text,
)

data class NarrationSession(
    val cacheKey: String,
    val noteId: String,
    val noteTitle: String,
    val model: String,
    val voice: String,
    val speed: Float,
    val contentHash: String,
    val files: List<File>,
    val cues: List<NarrationCue> = emptyList(),
)
