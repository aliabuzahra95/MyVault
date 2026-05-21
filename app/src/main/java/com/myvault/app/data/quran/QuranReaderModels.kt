package com.myvault.app.data.quran

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class QuranAyah(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabicText: String,
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
    val tajweedEnabled: Boolean = false,
    val loading: Boolean = true,
)

val QuranReaderUiState.arabicTextSize: TextUnit
    get() = (29f * arabicFontPercent.coerceIn(70, 140) / 100f).sp
