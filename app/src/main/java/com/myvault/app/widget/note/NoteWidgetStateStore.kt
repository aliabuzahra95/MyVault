package com.myvault.app.widget.note

import android.content.Context

class NoteWidgetStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun state(appWidgetId: Int): NoteWidgetState = NoteWidgetState(
        noteId = preferences.getString(key(appWidgetId, "note_id"), null),
        textSizeLevel = preferences.getInt(
            key(appWidgetId, "text_size"),
            DEFAULT_NOTE_TEXT_SIZE_LEVEL,
        ).coerceIn(MIN_NOTE_TEXT_SIZE_LEVEL, MAX_NOTE_TEXT_SIZE_LEVEL),
        showTitle = preferences.getBoolean(key(appWidgetId, "show_title"), true),
        showContext = preferences.getBoolean(key(appWidgetId, "show_context"), true),
    )

    fun setNote(appWidgetId: Int, noteId: String) {
        preferences.edit().putString(key(appWidgetId, "note_id"), noteId).apply()
    }

    fun setTextSizeLevel(appWidgetId: Int, level: Int) {
        preferences.edit()
            .putInt(key(appWidgetId, "text_size"), level.coerceIn(MIN_NOTE_TEXT_SIZE_LEVEL, MAX_NOTE_TEXT_SIZE_LEVEL))
            .apply()
    }

    fun setShowTitle(appWidgetId: Int, show: Boolean) {
        preferences.edit().putBoolean(key(appWidgetId, "show_title"), show).apply()
    }

    fun setShowContext(appWidgetId: Int, show: Boolean) {
        preferences.edit().putBoolean(key(appWidgetId, "show_context"), show).apply()
    }

    fun delete(appWidgetId: Int) {
        preferences.edit()
            .remove(key(appWidgetId, "note_id"))
            .remove(key(appWidgetId, "text_size"))
            .remove(key(appWidgetId, "show_title"))
            .remove(key(appWidgetId, "show_context"))
            .apply()
    }

    private fun key(appWidgetId: Int, suffix: String) = "widget_${appWidgetId}_$suffix"

    private companion object {
        const val PREFERENCES_NAME = "note_widget_state"
    }
}
