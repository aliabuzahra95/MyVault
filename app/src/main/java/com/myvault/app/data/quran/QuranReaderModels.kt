package com.myvault.app.data.quran

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.audio.AudioMiniPlayerUiState
import com.myvault.app.data.quran.audio.AudioPickerAyah
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.SurahDownloadState
import com.myvault.app.data.quran.memorization.MemorizationConcealAmount
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationRepeatMode
import com.myvault.app.data.quran.memorization.AyahMemorizationStatusSnapshot
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationSavedAttempt

data class QuranAyah(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabicText: String,
    val translation: String = "",
    val translationFootnotes: List<QuranTranslationFootnote> = emptyList(),
    val tajweedAnnotations: List<TajweedAnnotation> = emptyList(),
    val words: List<QuranWord> = emptyList(),
)

data class QuranTranslationFootnote(
    val id: String,
    val label: String,
    val text: String,
    val markerStart: Int,
    val markerEndExclusive: Int,
)

enum class QuranTranslationSource(
    val storedValue: String,
    val displayName: String,
    val description: String,
) {
    SahihInternational(
        storedValue = "sahih_international",
        displayName = "Sahih International",
        description = "Clear English translation available fully offline.",
    ),
    Maududi(
        storedValue = "maududi",
        displayName = "Tafheem-ul-Quran",
        description = "Sayyid Abul Ala Maududi, available offline with explanatory footnotes when available.",
    ),
    ;

    companion object {
        fun fromStoredValue(value: String?): QuranTranslationSource =
            entries.firstOrNull { it.storedValue == value } ?: SahihInternational
    }
}

data class QuranWord(
    val wordId: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val wordPosition: Int,
    val arabicText: String,
    val normalizedArabicText: String,
    val metadata: QuranWordMetadata? = null,
    val charStart: Int = -1,
    val charEnd: Int = -1,
)

data class QuranWordMetadata(
    val wordId: String,
    val arabicText: String,
    val normalizedArabicText: String,
    val imlaeiText: String? = null,
    val imlaeiSimpleText: String? = null,
    val translation: String? = null,
    val transliteration: String? = null,
    val root: String? = null,
    val lemma: String? = null,
    val definition: String? = null,
    val source: String? = null,
)

data class QuranWordMetadataVerificationResult(
    val totalDisplayedWords: Int,
    val metadataRows: Int,
    val attachedRows: Int,
    val missingWordIds: List<String>,
    val mismatchedRows: List<QuranWordMetadataMismatch>,
    val duplicateMetadataWordIds: List<String>,
)

data class QuranWordMetadataMismatch(
    val wordId: String,
    val displayedArabic: String,
    val metadataArabic: String,
)

data class TajweedAnnotation(
    val start: Int,
    val end: Int,
    val rule: String,
)

data class TafsirSourceUiModel(
    val id: Int,
    val name: String,
)

data class QuranReaderUiState(
    val selectedSurah: SurahInfo = quranCatalog.first(),
    val ayahs: List<QuranAyah> = emptyList(),
    val restoredAyah: Int = 1,
    val arabicFontPercent: Int = 100,
    val translationFontPercent: Int = 100,
    val translationEnabled: Boolean = true,
    val translationSource: QuranTranslationSource = QuranTranslationSource.SahihInternational,
    val translationSourceLoading: Boolean = false,
    val translationSourceMessage: String? = null,
    val tajweedEnabled: Boolean = false,
    val expandedTafsirVerseKey: String? = null,
    val availableTafsirSources: List<TafsirSourceUiModel> = emptyList(),
    val selectedTafsirSourceId: Int = MUKHTASAR_TAFSIR_ID,
    val tafsirByVerse: Map<String, String> = emptyMap(),
    val loadingTafsirVerseKeys: Set<String> = emptySet(),
    val bookmarkedVerseKeys: Set<String> = emptySet(),
    val reflectionsByVerse: Map<String, List<QuranReflectionItem>> = emptyMap(),
    val pendingScrollVerseKey: String? = null,
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
    val memorizationRecords: List<MemorizationRecord> = emptyList(),
    val memorizationAttemptStatuses: Map<String, AyahMemorizationStatusSnapshot> = emptyMap(),
    val surahMemorizationAttempts: List<QuranSurahMemorizationSavedAttempt> = emptyList(),
    val memorizationConcealedVerseKey: String? = null,
    val memorizationConcealAmount: MemorizationConcealAmount? = null,
    val memorizationRepeatVerseKey: String? = null,
    val memorizationRepeatMode: MemorizationRepeatMode? = null,
    val loading: Boolean = true,
)

const val MUKHTASAR_TAFSIR_ID = -1

fun tafsirCacheKey(verseKey: String, sourceId: Int): String = "$verseKey|$sourceId"

data class QuranRecentLocation(
    val surahNumber: Int,
    val ayahNumber: Int,
    val lastReadAt: Long,
)

val QuranReaderUiState.arabicTextSize: TextUnit
    get() = (29f * arabicFontPercent.coerceIn(70, 140) / 100f).sp

val QuranReaderUiState.translationTextSize: TextUnit
    get() = (14f * translationFontPercent.coerceIn(80, 130) / 100f).sp
