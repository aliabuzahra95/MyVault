package com.myvault.app.widget.quran

import com.myvault.app.data.quran.quranCatalog
import java.text.Normalizer

enum class QuranWidgetMode { Reader, Picker, Settings, ReciterPicker }

enum class QuranWidgetSizeBucket { Compact, Medium, Large, ExtraLarge }

data class QuranWidgetState(
    val surahNumber: Int,
    val mode: QuranWidgetMode,
    val anchorAyah: Int,
    val translationEnabled: Boolean = false,
    val arabicFontLevel: Int = DEFAULT_ARABIC_FONT_LEVEL,
    val tajweedEnabled: Boolean = false,
    val searchQuery: String = "",
    val reciterId: Int = DEFAULT_QURAN_WIDGET_RECITER_ID,
    val reciterName: String = DEFAULT_QURAN_WIDGET_RECITER_NAME,
)

data class QuranWidgetLocation(
    val surahNumber: Int,
    val ayahNumber: Int,
) {
    val verseKey: String get() = "$surahNumber:$ayahNumber"
}

internal fun quranWidgetSizeBucket(widthDp: Int, heightDp: Int): QuranWidgetSizeBucket = when {
    widthDp < 250 || heightDp < 180 -> QuranWidgetSizeBucket.Compact
    widthDp < 330 || heightDp < 300 -> QuranWidgetSizeBucket.Medium
    widthDp < 450 || heightDp < 480 -> QuranWidgetSizeBucket.Large
    else -> QuranWidgetSizeBucket.ExtraLarge
}

internal fun validatedWidgetLocation(surahNumber: Int, ayahNumber: Int): QuranWidgetLocation {
    val matchedSurah = quranCatalog.firstOrNull { it.num == surahNumber }
    val surah = matchedSurah ?: quranCatalog.first()
    return QuranWidgetLocation(
        surahNumber = surah.num,
        ayahNumber = if (matchedSurah == null) 1 else ayahNumber.coerceIn(1, surah.ayat),
    )
}

internal fun adjacentSurah(currentSurah: Int, direction: Int): Int =
    (currentSurah + direction).coerceIn(quranCatalog.first().num, quranCatalog.last().num)

internal const val MIN_ARABIC_FONT_LEVEL = 1
internal const val DEFAULT_ARABIC_FONT_LEVEL = 3
internal const val MAX_ARABIC_FONT_LEVEL = 5
internal const val DEFAULT_QURAN_WIDGET_RECITER_ID = 7
internal const val DEFAULT_QURAN_WIDGET_RECITER_NAME = "Mishary al-Afasy"

internal fun adjustedArabicFontLevel(current: Int, direction: Int): Int =
    (current + direction).coerceIn(MIN_ARABIC_FONT_LEVEL, MAX_ARABIC_FONT_LEVEL)

internal fun quranWidgetArabicTextSize(bucket: QuranWidgetSizeBucket, level: Int): Float {
    val baseSize = when (bucket) {
        QuranWidgetSizeBucket.Compact -> 24f
        QuranWidgetSizeBucket.Medium -> 27f
        QuranWidgetSizeBucket.Large -> 29f
        QuranWidgetSizeBucket.ExtraLarge -> 31f
    }
    return baseSize + (level.coerceIn(MIN_ARABIC_FONT_LEVEL, MAX_ARABIC_FONT_LEVEL) - DEFAULT_ARABIC_FONT_LEVEL) * 2f
}

internal fun filteredWidgetSurahs(query: String) = quranCatalog.filter { surah ->
    val trimmed = query.trim()
    if (trimmed.isBlank()) return@filter true
    trimmed.toIntOrNull()?.let { number -> return@filter surah.num == number }
    val normalized = trimmed.normalizedWidgetSurahSearchText()
    surah.name.normalizedWidgetSurahSearchText().contains(normalized) ||
        surah.arabic.normalizedWidgetSurahSearchText().contains(normalized)
}

private fun String.normalizedWidgetSurahSearchText(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('ٱ', 'ا')
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

object QuranWidgetContract {
    const val ACTION_PREVIOUS_SURAH = "com.myvault.app.widget.quran.PREVIOUS_SURAH"
    const val ACTION_NEXT_SURAH = "com.myvault.app.widget.quran.NEXT_SURAH"
    const val ACTION_SHOW_PICKER = "com.myvault.app.widget.quran.SHOW_PICKER"
    const val ACTION_SHOW_READER = "com.myvault.app.widget.quran.SHOW_READER"
    const val ACTION_SHOW_SETTINGS = "com.myvault.app.widget.quran.SHOW_SETTINGS"
    const val ACTION_SELECT_SURAH = "com.myvault.app.widget.quran.SELECT_SURAH"
    const val ACTION_SHOW_RECITERS = "com.myvault.app.widget.quran.SHOW_RECITERS"
    const val ACTION_SELECT_RECITER = "com.myvault.app.widget.quran.SELECT_RECITER"
    const val ACTION_SETTING_COMMAND = "com.myvault.app.widget.quran.SETTING_COMMAND"

    const val COMMAND_TOGGLE_TRANSLATION = "toggle_translation"
    const val COMMAND_DECREASE_ARABIC = "decrease_arabic"
    const val COMMAND_INCREASE_ARABIC = "increase_arabic"
    const val COMMAND_TOGGLE_TAJWEED = "toggle_tajweed"
    const val COMMAND_SHOW_RECITERS = "show_reciters"
    const val COMMAND_DONE = "done"

    const val EXTRA_SURAH_NUMBER = "quran_widget_surah"
    const val EXTRA_AYAH_NUMBER = "quran_widget_ayah"
    const val EXTRA_WIDGET_ID = "quran_widget_id"
    const val EXTRA_MODE = "quran_widget_mode"
    const val EXTRA_SIZE_BUCKET = "quran_widget_size_bucket"
    const val EXTRA_TRANSLATION_ENABLED = "quran_widget_translation_enabled"
    const val EXTRA_ARABIC_FONT_LEVEL = "quran_widget_arabic_font_level"
    const val EXTRA_TAJWEED_ENABLED = "quran_widget_tajweed_enabled"
    const val EXTRA_SEARCH_QUERY = "quran_widget_search_query"
    const val EXTRA_SETTING_COMMAND = "quran_widget_setting_command"
    const val EXTRA_RECITER_ID = "quran_widget_reciter_id"
    const val EXTRA_RECITER_NAME = "quran_widget_reciter_name"
}
