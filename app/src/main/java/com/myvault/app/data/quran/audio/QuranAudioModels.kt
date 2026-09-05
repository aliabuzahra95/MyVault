package com.myvault.app.data.quran.audio

import java.io.File

data class AudioReciterUiModel(
    val id: Int,
    val name: String,
)

data class AudioMiniPlayerUiState(
    val verseKey: String,
    val ayahNumber: Int,
    val reciterName: String,
    val isPlaying: Boolean,
    val playbackSpeed: Float,
    val progressMs: Long,
    val durationMs: Long,
    val listeningMode: QuranListeningMode = QuranListeningMode.ThisAyah,
    val synchronized: Boolean = false,
)

sealed interface SurahDownloadState {
    data object NotDownloaded : SurahDownloadState
    data object Preparing : SurahDownloadState
    data class Queued(val position: Int) : SurahDownloadState
    data class Downloading(val progressPercent: Int) : SurahDownloadState
    data object Downloaded : SurahDownloadState
    data class Failed(val message: String) : SurahDownloadState
}

data class AudioPickerAyah(
    val verseKey: String,
    val ayahNumber: Int,
)

data class ChapterAudioMetadata(
    val reciter: AudioReciterUiModel,
    val surahNumber: Int,
    val mode: PlaybackMode,
    val audioUrl: String?,
    val timestamps: Map<String, Long>,
    val verseAudioUrls: Map<String, String>,
    val localSurahFile: File,
)

enum class PlaybackMode {
    FullSurah,
    VerseByVerse,
}
