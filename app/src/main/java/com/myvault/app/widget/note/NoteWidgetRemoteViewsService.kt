package com.myvault.app.widget.note

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.myvault.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class NoteWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = NoteWidgetBodyFactory(applicationContext, intent)
}

private class NoteWidgetBodyFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
    )
    private val bucket = intent.getStringExtra(NoteWidgetContract.EXTRA_SIZE_BUCKET)
        ?.let { runCatching { NoteWidgetSizeBucket.valueOf(it) }.getOrNull() }
        ?: NoteWidgetSizeBucket.Medium
    private var note: NoteWidgetItem? = null
    private var chunks: List<String> = emptyList()
    private var textSizeLevel = DEFAULT_NOTE_TEXT_SIZE_LEVEL

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val state = NoteWidgetStateStore(context).state(appWidgetId)
        textSizeLevel = state.textSizeLevel
        note = runBlocking(Dispatchers.IO) { context.noteWidgetDataSource().item(state.noteId) }
        chunks = note?.body.orEmpty().toNoteWidgetChunks()
        if (note != null && chunks.isEmpty()) {
            chunks = listOf(context.getString(R.string.note_widget_empty_note))
        }
    }

    override fun onDestroy() {
        note = null
        chunks = emptyList()
    }

    override fun getCount(): Int = chunks.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = note ?: return null
        val text = chunks.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_note_body_row).apply {
            setTextViewText(R.id.note_widget_body_text, text)
            setTextViewTextSize(
                R.id.note_widget_body_text,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                noteWidgetBodyTextSize(bucket, textSizeLevel),
            )
            setOnClickFillInIntent(
                R.id.note_widget_body_text,
                Intent().apply {
                    action = NoteWidgetContract.ACTION_OPEN_NOTE
                    putExtra(NoteWidgetContract.EXTRA_NOTE_ID, item.id)
                    putExtra(NoteWidgetContract.EXTRA_COURSE_ID, item.courseId)
                    putExtra(NoteWidgetContract.EXTRA_WIDGET_ID, appWidgetId)
                },
            )
        }
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_note_loading_row)

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun String.toNoteWidgetChunks(maxCharacters: Int = 900): List<String> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) return emptyList()
    val paragraphs = normalized.split(Regex("\n{2,}"))
    val chunks = mutableListOf<String>()
    paragraphs.forEach { paragraph ->
        val clean = paragraph.trimEnd()
        if (clean.length <= maxCharacters) {
            chunks += clean
        } else {
            var cursor = 0
            while (cursor < clean.length) {
                val desiredEnd = (cursor + maxCharacters).coerceAtMost(clean.length)
                val breakAt = if (desiredEnd < clean.length) {
                    clean.lastIndexOfAny(charArrayOf('\n', ' ', '.', '،'), startIndex = desiredEnd)
                        .takeIf { it > cursor + maxCharacters / 2 }
                        ?: desiredEnd
                } else {
                    desiredEnd
                }
                chunks += clean.substring(cursor, breakAt).trim()
                cursor = breakAt
                while (cursor < clean.length && clean[cursor].isWhitespace()) cursor++
            }
        }
    }
    return chunks.filter(String::isNotBlank)
}
