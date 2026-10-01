package com.myvault.app.data.narration

import java.io.File

object NarrationConfig {
    const val MODEL = "gpt-4o-mini-tts"
    const val DEFAULT_VOICE = "cedar"
    const val RESPONSE_FORMAT = "mp3"
    const val MAX_CHARS_PER_CHUNK = 1_400
    val VoiceOptions = listOf("cedar", "marin")
    val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2.0f)
}

object GeminiNarrationConfig {
    const val MODEL_FLASH_LITE = "gemini-3.8-flash-lite-tts"
    const val MODEL_FLASH = "gemini-3.8-flash-tts"
    const val DEFAULT_VOICE = "Puck"
    val FallbackModels = emptyList<String>()
    val VoiceOptions = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
    const val MAX_CHARS_PER_CHUNK = 1_800
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

enum class NarrationProvider(
    val storedValue: String,
    val label: String,
    val tierBadge: String,
    val defaultModel: String,
) {
    GeminiFlashLite("gemini_flash_lite", "Gemini Lite", "Very low cost • Intelligent", GeminiNarrationConfig.MODEL_FLASH_LITE),
    GeminiFlash("gemini_flash", "Gemini", "High quality • Low cost", GeminiNarrationConfig.MODEL_FLASH),
    Device("device", "Device", "Free • Offline", "device-tts"),
    OpenAi("openai", "OpenAI", "Cloud processing", NarrationConfig.MODEL),
    Azure("azure", "Azure", "Cloud neural", AzureNarrationConfig.DEFAULT_VOICE);

    val isCloud: Boolean
        get() = this != Device

    val voiceOptions: List<String>
        get() = when (this) {
            GeminiFlashLite, GeminiFlash -> GeminiNarrationConfig.VoiceOptions
            OpenAi -> NarrationConfig.VoiceOptions
            Azure -> AzureNarrationConfig.VoiceOptions
            Device -> emptyList()
        }

    fun resolveVoice(voice: String?): String = voice?.takeIf { it in voiceOptions } ?: when (this) {
        GeminiFlashLite, GeminiFlash -> GeminiNarrationConfig.DEFAULT_VOICE
        OpenAi -> NarrationConfig.DEFAULT_VOICE
        Azure -> AzureNarrationConfig.DEFAULT_VOICE
        Device -> "device"
    }

    companion object {
        fun fromStoredValue(value: String): NarrationProvider =
            entries.firstOrNull { it.storedValue.equals(value, ignoreCase = true) } ?: GeminiFlashLite

        fun fromModel(model: String): NarrationProvider = when {
            model.startsWith("azure-speech-") -> Azure
            model.startsWith("device-tts") -> Device
            else -> entries.firstOrNull { it.defaultModel == model } ?: GeminiFlashLite
        }
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

enum class NarrationUnitType {
    Title,
    Heading,
    Subheading,
    Paragraph,
    ListItem,
    BlockQuote,
    ArabicQuote,
}

data class NarrationUnit(
    val id: String,
    val type: NarrationUnitType,
    val rawText: String,
    val spokenText: String,
    val language: String = "en",
    val pauseBeforeMs: Long = 0L,
    val pauseAfterMs: Long = 0L,
    val emphasisLevel: Float = 1f,
)

data class NarrationPlan(
    val sourceId: String,
    val title: String,
    val units: List<NarrationUnit>,
    val totalEstimatedDurationMs: Long = 0L,
) {
    val fullSpokenText: String
        get() = units.joinToString(" ") { it.spokenText.trim() }.trim()
}

data class NarrationUiState(
    val status: NarrationPlaybackStatus = NarrationPlaybackStatus.Idle,
    val noteId: String? = null,
    val noteTitle: String = "",
    val label: String = "",
    val error: String? = null,
    val speed: Float = 1f,
    val provider: NarrationProvider = NarrationProvider.GeminiFlashLite,
    val voice: String = GeminiNarrationConfig.DEFAULT_VOICE,
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
    val isPlaying: Boolean
        get() = status == NarrationPlaybackStatus.Playing

    fun preparing(sourceId: String, title: String, provider: NarrationProvider, voice: String) =
        NarrationUiState(
            status = NarrationPlaybackStatus.Preparing,
            noteId = sourceId,
            noteTitle = title,
            label = "Preparing narration...",
            speed = speed,
            provider = provider,
            voice = provider.resolveVoice(voice),
        )
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
