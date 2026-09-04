package com.myvault.app.widget.note

import android.content.Context
import android.os.SystemClock

class QuickNoteLaunchGuard(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun accept(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean {
        val previous = preferences.getLong(KEY_LAST_ACCEPTED, Long.MIN_VALUE)
        if (!isQuickNoteTapAccepted(previous, nowElapsed)) return false
        preferences.edit().putLong(KEY_LAST_ACCEPTED, nowElapsed).apply()
        return true
    }

    private companion object {
        const val PREFERENCES_NAME = "quick_note_widget_guard"
        const val KEY_LAST_ACCEPTED = "last_accepted_elapsed"
    }
}

internal fun isQuickNoteTapAccepted(
    previousElapsed: Long,
    nowElapsed: Long,
    debounceMillis: Long = 1_500L,
): Boolean = previousElapsed == Long.MIN_VALUE || nowElapsed < previousElapsed || nowElapsed - previousElapsed >= debounceMillis
