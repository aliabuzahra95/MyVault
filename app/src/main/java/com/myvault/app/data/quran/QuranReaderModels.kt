package com.myvault.app.data.quran

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioPickerAyah
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.SurahDownloadState

data class QuranAyah(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabicText: String,
    val translation: String = "",
    val tajweedAnnotations: List<TajweedAnnotation> = emptyList(),
)

data class TajweedAnnotation(
    val start: Int,
    val end: Int,
    val rule: String,
)

data class QuranReaderUiState(
    val selectedSurah: SurahInfo = quranCatalog.first(),
    val ayahs: List<QuranAyah> = emptyList(),
    val restoredAyah: Int = 1,
    val arabicFontPercent: Int = 100,
    val translationFontPercent: Int = 100,
    val translationEnabled: Boolean = true,
    val tajweedEnabled: Boolean = false,
    val expandedTafsirVerseKey: String? = null,
    val tafsirByVerse: Map<String, String> = emptyMap(),
    val loadingTafsirVerseKeys: Set<String> = emptySet(),
    val bookmarkedVerseKeys: Set<String> = emptySet(),
    val recentLocations: List<QuranRecentLocation> = emptyList(),
    val availableReciters: List<AudioReciterUiModel> = emptyList(),
    val reciterPickerAyah: AudioPickerAyah? = null,
    val selectedAudioReciter: AudioReciterUiModel? = null,
    val playingVerseKey: String? = null,
    val audioLoadingVerseKey: String? = null,
    val audioStatusMessage: String? = null,
    val audioStatusIsError: Boolean = false,
    val audioPlaybackSpeed: Float = 1f,
    val miniPlayer: AudioMiniPlayerUiState? = null,
    val audioDownloadStates: Map<String, SurahDownloadState> = emptyMap(),
    val loading: Boolean = true,
)

data class QuranRecentLocation(
    val surahNumber: Int,
    val ayahNumber: Int,
    val lastReadAt: Long,
)

val QuranReaderUiState.arabicTextSize: TextUnit
    get() = (29f * arabicFontPercent.coerceIn(70, 140) / 100f).sp

val QuranReaderUiState.translationTextSize: TextUnit
    get() = (14f * translationFontPercent.coerceIn(80, 130) / 100f).sp
