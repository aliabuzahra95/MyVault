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
        return QuranWidgetState(location.surahNumber, mode, location.ayahNumber)
    }

    fun exists(appWidgetId: Int): Boolean = preferences.contains(surahKey(appWidgetId))

    fun initialize(appWidgetId: Int, defaultSurah: Int, defaultAyah: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || exists(appWidgetId)) return
        val location = validatedWidgetLocation(defaultSurah, defaultAyah)
        write(appWidgetId, QuranWidgetState(location.surahNumber, QuranWidgetMode.Reader, location.ayahNumber))
    }

    fun selectSurah(appWidgetId: Int, surahNumber: Int) {
        val selected = validatedWidgetLocation(surahNumber, 1)
        write(appWidgetId, QuranWidgetState(selected.surahNumber, QuranWidgetMode.Reader, 1))
    }

    fun moveSurah(appWidgetId: Int, direction: Int) {
        val current = read(appWidgetId)
        selectSurah(appWidgetId, adjacentSurah(current.surahNumber, direction))
    }

    fun setMode(appWidgetId: Int, mode: QuranWidgetMode) {
        val current = read(appWidgetId)
        write(appWidgetId, current.copy(mode = mode))
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
            .apply()
    }

    private fun write(appWidgetId: Int, state: QuranWidgetState) {
        preferences.edit()
            .putInt(surahKey(appWidgetId), state.surahNumber)
            .putString(modeKey(appWidgetId), state.mode.name)
            .putInt(anchorKey(appWidgetId), state.anchorAyah)
            .apply()
    }

    private fun surahKey(id: Int) = "surah_$id"
    private fun modeKey(id: Int) = "mode_$id"
    private fun anchorKey(id: Int) = "anchor_$id"
}
