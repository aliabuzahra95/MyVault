package com.myvault.app.widget.note

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.myvault.app.MainActivity
import com.myvault.app.R

class QuickNoteWidgetProvider : AppWidgetProvider() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach(com.myvault.app.widget.WidgetAppearanceStore(context)::delete)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, manager, appWidgetId)
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val width = manager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val layout = when (quickNoteSizeBucket(width)) {
                QuickNoteSizeBucket.Compact -> R.layout.widget_quick_note_compact
                QuickNoteSizeBucket.Wide -> R.layout.widget_quick_note_wide
            }
            val views = com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, layout)
            val intent = Intent(context, MainActivity::class.java).apply {
                action = NoteWidgetContract.ACTION_QUICK_CREATE_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(NoteWidgetContract.EXTRA_WIDGET_ID, appWidgetId)
            }
            views.setOnClickPendingIntent(
                R.id.quick_note_widget_root,
                PendingIntent.getActivity(
                    context,
                    440_000 + appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
