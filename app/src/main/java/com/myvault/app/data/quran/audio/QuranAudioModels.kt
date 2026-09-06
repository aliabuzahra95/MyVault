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
    val endTimestamps: Map<String, Long> = emptyMap(),
    val recordingId: String? = null,
)

internal fun ChapterAudioMetadata.toDownloadedTimingMap(
    ayahCount: Int,
    durationMs: Long,
): QuranTimingMap? {
    if (mode != PlaybackMode.FullSurah || ayahCount <= 0 || durationMs <= 0) return null
    return runCatching {
        val starts = (1..ayahCount).map { ayah ->
            timestamps.getValue("$surahNumber:$ayah")
        }
        val ayahs = starts.mapIndexed { index, start ->
            val verseKey = "$surahNumber:${index + 1}"
            val end = endTimestamps[verseKey]
                ?: starts.getOrNull(index + 1)
                ?: durationMs
            require(start >= 0 && end > start)
            QuranAyahTiming(verseKey, start, end)
        }
        require(ayahs.zipWithNext().all { (current, next) -> next.startMs >= current.endMs })
        QuranTimingMap(
            recordingId = recordingId ?: "offline-${reciter.id}-$surahNumber-${localSurahFile.length()}",
            surah = surahNumber,
            audioUrl = audioUrl.orEmpty(),
            ayahs = ayahs,
        ).also { it.validateDuration(durationMs) }
    }.getOrNull()
}

enum class PlaybackMode {
    FullSurah,
    VerseByVerse,
}
