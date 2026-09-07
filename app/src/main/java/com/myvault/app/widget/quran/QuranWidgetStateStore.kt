package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.Context

class QuranWidgetStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "quran_widget_state",
        Context.MODE_PRIVATE,
    )

    fun read(appWidgetId: Int, defaultSurah: Int = 1): QuranWidgetState {
        val validDefault = validatedWidgetLocation(defaultSurah, 1).surahNumber
        val surah = preferences.getInt(surahKey(appWidgetId), validDefault)
        val ayah = preferences.getInt(anchorKey(appWidgetId), 1)
        val mode = preferences.getString(modeKey(appWidgetId), null)
            ?.let { stored -> QuranWidgetMode.entries.firstOrNull { it.name == stored } }
            ?: QuranWidgetMode.Reader
        val location = validatedWidgetLocation(surah, ayah)
        return QuranWidgetState(
            surahNumber = location.surahNumber,
            mode = mode,
            anchorAyah = location.ayahNumber,
            translationEnabled = preferences.getBoolean(translationKey(appWidgetId), false),
            arabicFontLevel = preferences.getInt(fontLevelKey(appWidgetId), DEFAULT_ARABIC_FONT_LEVEL)
                .coerceIn(MIN_ARABIC_FONT_LEVEL, MAX_ARABIC_FONT_LEVEL),
            tajweedEnabled = preferences.getBoolean(tajweedKey(appWidgetId), false),
            searchQuery = preferences.getString(searchKey(appWidgetId), "").orEmpty(),
            reciterId = preferences.getInt(reciterIdKey(appWidgetId), DEFAULT_QURAN_WIDGET_RECITER_ID),
            reciterName = preferences.getString(reciterNameKey(appWidgetId), DEFAULT_QURAN_WIDGET_RECITER_NAME)
                .orEmpty().ifBlank { DEFAULT_QURAN_WIDGET_RECITER_NAME },
        )
    }

    fun exists(appWidgetId: Int): Boolean = preferences.contains(surahKey(appWidgetId))

    fun initialize(
        appWidgetId: Int,
        defaultSurah: Int,
        defaultAyah: Int,
        defaultReciterId: Int = DEFAULT_QURAN_WIDGET_RECITER_ID,
        defaultReciterName: String = DEFAULT_QURAN_WIDGET_RECITER_NAME,
    ): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || exists(appWidgetId)) return false
        val location = validatedWidgetLocation(defaultSurah, defaultAyah)
        write(
            appWidgetId,
            QuranWidgetState(
                surahNumber = location.surahNumber,
                mode = QuranWidgetMode.Reader,
                anchorAyah = location.ayahNumber,
                reciterId = defaultReciterId,
                reciterName = defaultReciterName,
            ),
        )
        return true
    }

    fun selectSurah(appWidgetId: Int, surahNumber: Int) {
        val selected = validatedWidgetLocation(surahNumber, 1)
        write(
            appWidgetId,
            read(appWidgetId).copy(
                surahNumber = selected.surahNumber,
                mode = QuranWidgetMode.Reader,
                anchorAyah = 1,
                searchQuery = "",
            ),
        )
    }

    fun moveSurah(appWidgetId: Int, direction: Int) {
        val current = read(appWidgetId)
        selectSurah(appWidgetId, adjacentSurah(current.surahNumber, direction))
    }

    fun setMode(appWidgetId: Int, mode: QuranWidgetMode) {
        val current = read(appWidgetId)
        write(appWidgetId, current.copy(mode = mode))
    }

    fun setTranslationEnabled(appWidgetId: Int, enabled: Boolean) {
        write(appWidgetId, read(appWidgetId).copy(translationEnabled = enabled))
    }

    fun adjustArabicFontLevel(appWidgetId: Int, direction: Int) {
        val current = read(appWidgetId)
        write(
            appWidgetId,
            current.copy(arabicFontLevel = adjustedArabicFontLevel(current.arabicFontLevel, direction)),
        )
    }

    fun setTajweedEnabled(appWidgetId: Int, enabled: Boolean) {
        write(appWidgetId, read(appWidgetId).copy(tajweedEnabled = enabled))
    }

    fun setSearchQuery(appWidgetId: Int, query: String) {
        write(appWidgetId, read(appWidgetId).copy(searchQuery = query.trim().take(64)))
    }

    fun setReciter(appWidgetId: Int, reciterId: Int, reciterName: String) {
        write(
            appWidgetId,
            read(appWidgetId).copy(
                mode = QuranWidgetMode.Settings,
                reciterId = reciterId,
                reciterName = reciterName,
            ),
        )
    }

    fun setAnchor(appWidgetId: Int, surahNumber: Int, ayahNumber: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val location = validatedWidgetLocation(surahNumber, ayahNumber)
        val current = read(appWidgetId, location.surahNumber)
        write(
            appWidgetId,
            current.copy(surahNumber = location.surahNumber, anchorAyah = location.ayahNumber),
        )
    }

    fun delete(appWidgetId: Int) {
        preferences.edit()
            .remove(surahKey(appWidgetId))
            .remove(modeKey(appWidgetId))
            .remove(anchorKey(appWidgetId))
            .remove(translationKey(appWidgetId))
            .remove(fontLevelKey(appWidgetId))
            .remove(tajweedKey(appWidgetId))
            .remove(searchKey(appWidgetId))
            .remove(reciterIdKey(appWidgetId))
            .remove(reciterNameKey(appWidgetId))
            .commit()
    }

    private fun write(appWidgetId: Int, state: QuranWidgetState) {
        preferences.edit()
            .putInt(surahKey(appWidgetId), state.surahNumber)
            .putString(modeKey(appWidgetId), state.mode.name)
            .putInt(anchorKey(appWidgetId), state.anchorAyah)
            .putBoolean(translationKey(appWidgetId), state.translationEnabled)
            .putInt(fontLevelKey(appWidgetId), state.arabicFontLevel)
            .putBoolean(tajweedKey(appWidgetId), state.tajweedEnabled)
            .putString(searchKey(appWidgetId), state.searchQuery)
            .putInt(reciterIdKey(appWidgetId), state.reciterId)
            .putString(reciterNameKey(appWidgetId), state.reciterName)
            .commit()
    }

    private fun surahKey(id: Int) = "surah_$id"
    private fun modeKey(id: Int) = "mode_$id"
    private fun anchorKey(id: Int) = "anchor_$id"
    private fun translationKey(id: Int) = "translation_$id"
    private fun fontLevelKey(id: Int) = "font_level_$id"
    private fun tajweedKey(id: Int) = "tajweed_$id"
    private fun searchKey(id: Int) = "search_$id"
    private fun reciterIdKey(id: Int) = "reciter_id_$id"
    private fun reciterNameKey(id: Int) = "reciter_name_$id"
}
