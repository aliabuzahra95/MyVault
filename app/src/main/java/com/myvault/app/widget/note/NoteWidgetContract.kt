package com.myvault.app.widget.note

import android.appwidget.AppWidgetManager

enum class NoteWidgetSizeBucket { Compact, Medium, Large, ExtraLarge }

data class NoteWidgetState(
    val noteId: String? = null,
    val textSizeLevel: Int = DEFAULT_NOTE_TEXT_SIZE_LEVEL,
    val showTitle: Boolean = true,
    val showContext: Boolean = true,
)

internal const val MIN_NOTE_TEXT_SIZE_LEVEL = 1
internal const val DEFAULT_NOTE_TEXT_SIZE_LEVEL = 2
internal const val MAX_NOTE_TEXT_SIZE_LEVEL = 4

internal fun adjustedNoteTextSizeLevel(current: Int, direction: Int): Int =
    (current + direction).coerceIn(MIN_NOTE_TEXT_SIZE_LEVEL, MAX_NOTE_TEXT_SIZE_LEVEL)

internal fun noteWidgetBodyTextSize(bucket: NoteWidgetSizeBucket, level: Int): Float {
    val base = when (bucket) {
        NoteWidgetSizeBucket.Compact -> 13f
        NoteWidgetSizeBucket.Medium -> 14f
        NoteWidgetSizeBucket.Large -> 15f
        NoteWidgetSizeBucket.ExtraLarge -> 16f
    }
    return base + (level.coerceIn(MIN_NOTE_TEXT_SIZE_LEVEL, MAX_NOTE_TEXT_SIZE_LEVEL) - DEFAULT_NOTE_TEXT_SIZE_LEVEL) * 1.5f
}

internal fun noteWidgetSizeBucket(widthDp: Int, heightDp: Int): NoteWidgetSizeBucket = when {
    widthDp < 220 || heightDp < 140 -> NoteWidgetSizeBucket.Compact
    widthDp < 320 || heightDp < 260 -> NoteWidgetSizeBucket.Medium
    widthDp < 440 || heightDp < 440 -> NoteWidgetSizeBucket.Large
    else -> NoteWidgetSizeBucket.ExtraLarge
}

internal fun shouldCreateQuickNote(
    unlocked: Boolean,
    pending: Boolean,
    creationInFlight: Boolean,
): Boolean = unlocked && pending && !creationInFlight

object NoteWidgetContract {
    const val ACTION_OPEN_NOTE = "com.myvault.app.widget.note.OPEN_NOTE"
    const val ACTION_QUICK_CREATE_NOTE = "com.myvault.app.widget.note.QUICK_CREATE_NOTE"

    const val EXTRA_NOTE_ID = "note_widget_note_id"
    const val EXTRA_COURSE_ID = "note_widget_course_id"
    const val EXTRA_WIDGET_ID = "note_widget_id"
    const val EXTRA_SIZE_BUCKET = "note_widget_size_bucket"
    const val EXTRA_NOTE_UPDATED_AT = "note_widget_updated_at"

    const val INVALID_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID
}
