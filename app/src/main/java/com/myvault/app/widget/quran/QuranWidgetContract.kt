package com.myvault.app.widget.quran

import com.myvault.app.data.quran.quranCatalog

enum class QuranWidgetMode { Reader, Picker }

enum class QuranWidgetSizeBucket { Compact, Medium, Large, ExtraLarge }

data class QuranWidgetState(
    val surahNumber: Int,
    val mode: QuranWidgetMode,
    val anchorAyah: Int,
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

object QuranWidgetContract {
    const val ACTION_PREVIOUS_SURAH = "com.myvault.app.widget.quran.PREVIOUS_SURAH"
    const val ACTION_NEXT_SURAH = "com.myvault.app.widget.quran.NEXT_SURAH"
    const val ACTION_SHOW_PICKER = "com.myvault.app.widget.quran.SHOW_PICKER"
    const val ACTION_SHOW_READER = "com.myvault.app.widget.quran.SHOW_READER"
    const val ACTION_SELECT_SURAH = "com.myvault.app.widget.quran.SELECT_SURAH"

    const val EXTRA_SURAH_NUMBER = "quran_widget_surah"
    const val EXTRA_AYAH_NUMBER = "quran_widget_ayah"
    const val EXTRA_WIDGET_ID = "quran_widget_id"
    const val EXTRA_MODE = "quran_widget_mode"
    const val EXTRA_SIZE_BUCKET = "quran_widget_size_bucket"
}
