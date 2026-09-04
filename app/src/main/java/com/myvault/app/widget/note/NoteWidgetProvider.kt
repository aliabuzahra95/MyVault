package com.myvault.app.widget.note

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.myvault.app.MainActivity
import com.myvault.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { updateWidget(context, manager, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidget(context, manager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = NoteWidgetStateStore(context)
        appWidgetIds.forEach(store::delete)
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val state = NoteWidgetStateStore(context).state(appWidgetId)
            val note = context.noteWidgetDataSource().item(state.noteId)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300)
            val bucket = noteWidgetSizeBucket(width, height)
            val layout = when (bucket) {
                NoteWidgetSizeBucket.Compact -> R.layout.widget_note_viewer_compact
                NoteWidgetSizeBucket.Medium -> R.layout.widget_note_viewer_medium
                NoteWidgetSizeBucket.Large -> R.layout.widget_note_viewer_large
                NoteWidgetSizeBucket.ExtraLarge -> R.layout.widget_note_viewer_extra_large
            }
            val views = RemoteViews(context.packageName, layout)
            val configIntent = NoteWidgetConfigActivity.intent(context, appWidgetId, editing = state.noteId != null)
            val configPendingIntent = PendingIntent.getActivity(
                context,
                410_000 + appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.note_widget_settings, configPendingIntent)

            if (note == null) {
                views.setTextViewText(R.id.note_widget_title, context.getString(R.string.note_widget_unavailable_title))
                views.setTextViewText(R.id.note_widget_context, context.getString(R.string.note_widget_unavailable_context))
                views.setViewVisibility(R.id.note_widget_title, View.VISIBLE)
                views.setViewVisibility(R.id.note_widget_context, View.VISIBLE)
                views.setViewVisibility(R.id.note_widget_body, View.GONE)
                views.setViewVisibility(R.id.note_widget_empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.note_widget_root, configPendingIntent)
                views.setOnClickPendingIntent(R.id.note_widget_empty, configPendingIntent)
            } else {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    action = NoteWidgetContract.ACTION_OPEN_NOTE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(NoteWidgetContract.EXTRA_NOTE_ID, note.id)
                    putExtra(NoteWidgetContract.EXTRA_COURSE_ID, note.courseId)
                    putExtra(NoteWidgetContract.EXTRA_WIDGET_ID, appWidgetId)
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    420_000 + appWidgetId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setTextViewText(R.id.note_widget_title, note.title)
                views.setTextViewText(R.id.note_widget_context, note.context)
                views.setViewVisibility(
                    R.id.note_widget_title,
                    if (state.showTitle) View.VISIBLE else View.GONE,
                )
                views.setViewVisibility(
                    R.id.note_widget_context,
                    if (state.showContext && bucket != NoteWidgetSizeBucket.Compact) View.VISIBLE else View.GONE,
                )
                views.setViewVisibility(R.id.note_widget_body, View.VISIBLE)
                views.setViewVisibility(R.id.note_widget_empty, View.GONE)
                views.setOnClickPendingIntent(R.id.note_widget_root, openPendingIntent)
                views.setOnClickPendingIntent(R.id.note_widget_title, openPendingIntent)
                views.setOnClickPendingIntent(R.id.note_widget_context, openPendingIntent)

                val serviceIntent = Intent(context, NoteWidgetRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(NoteWidgetContract.EXTRA_SIZE_BUCKET, bucket.name)
                    data = Uri.parse(
                        "myvault://note-widget/$appWidgetId/${note.updatedAt}/${state.textSizeLevel}/${bucket.name}",
                    )
                }
                views.setRemoteAdapter(R.id.note_widget_body, serviceIntent)
                val templateIntent = Intent(context, MainActivity::class.java).apply {
                    action = NoteWidgetContract.ACTION_OPEN_NOTE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                views.setPendingIntentTemplate(
                    R.id.note_widget_body,
                    PendingIntent.getActivity(
                        context,
                        430_000 + appWidgetId,
                        templateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(appWidgetId, views)
            if (note != null) manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.note_widget_body)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
            val pendingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            pendingScope.launch { ids.forEach { updateWidget(context, manager, it) } }
        }
    }
}
