package com.myvault.app.widget.quran

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
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.quranCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuranWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val defaults = runCatching { VaultPreferences(context).userPreferences.first() }.getOrNull()
                val store = QuranWidgetStateStore(context)
                appWidgetIds.forEach { appWidgetId ->
                    store.initialize(
                        appWidgetId = appWidgetId,
                        defaultSurah = defaults?.quranLastReadSurah ?: 1,
                        defaultAyah = defaults?.quranLastReadAyah ?: 1,
                    )
                    updateWidget(context, manager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = QuranWidgetStateStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in widgetActions) return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val store = QuranWidgetStateStore(context)
        when (intent.action) {
            QuranWidgetContract.ACTION_PREVIOUS_SURAH -> store.moveSurah(appWidgetId, -1)
            QuranWidgetContract.ACTION_NEXT_SURAH -> store.moveSurah(appWidgetId, 1)
            QuranWidgetContract.ACTION_SHOW_PICKER -> store.setMode(appWidgetId, QuranWidgetMode.Picker)
            QuranWidgetContract.ACTION_SHOW_READER -> store.setMode(appWidgetId, QuranWidgetMode.Reader)
            QuranWidgetContract.ACTION_SELECT_SURAH -> {
                store.selectSurah(
                    appWidgetId,
                    intent.getIntExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, 1),
                )
            }
        }
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    companion object {
        private val widgetActions = setOf(
            QuranWidgetContract.ACTION_PREVIOUS_SURAH,
            QuranWidgetContract.ACTION_NEXT_SURAH,
            QuranWidgetContract.ACTION_SHOW_PICKER,
            QuranWidgetContract.ACTION_SHOW_READER,
            QuranWidgetContract.ACTION_SELECT_SURAH,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuranWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val state = QuranWidgetStateStore(context).read(appWidgetId)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val bucket = quranWidgetSizeBucket(
                widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320),
                heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300),
            )
            val views = RemoteViews(context.packageName, bucket.layoutResource())
            bindHeader(context, views, appWidgetId, state, bucket)
            bindCollection(context, views, appWidgetId, state, bucket)
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.quran_widget_collection)
        }

        private fun bindHeader(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            state: QuranWidgetState,
            bucket: QuranWidgetSizeBucket,
        ) {
            val surah = quranCatalog.first { it.num == state.surahNumber }
            val isReader = state.mode == QuranWidgetMode.Reader
            views.setTextViewText(
                R.id.quran_widget_title,
                if (isReader) surah.name else context.getString(R.string.quran_widget_choose_surah),
            )
            views.setTextViewText(R.id.quran_widget_arabic_title, if (isReader) surah.arabic else "")
            views.setTextViewText(
                R.id.quran_widget_metadata,
                if (isReader) {
                    context.getString(
                        R.string.quran_widget_surah_metadata,
                        surah.num,
                        surah.ayat,
                        state.anchorAyah,
                    )
                } else {
                    context.getString(R.string.quran_widget_surah_count, quranCatalog.size)
                },
            )

            if (isReader) {
                views.setImageViewResource(R.id.quran_widget_previous, R.drawable.ic_widget_chevron_left)
                views.setViewVisibility(R.id.quran_widget_next, View.VISIBLE)
                views.setViewVisibility(
                    R.id.quran_widget_open,
                    if (bucket == QuranWidgetSizeBucket.Compact) View.GONE else View.VISIBLE,
                )
                views.setViewVisibility(
                    R.id.quran_widget_arabic_title,
                    if (bucket == QuranWidgetSizeBucket.Compact) View.GONE else View.VISIBLE,
                )
                views.setOnClickPendingIntent(
                    R.id.quran_widget_previous,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_PREVIOUS_SURAH, 1),
                )
                views.setOnClickPendingIntent(
                    R.id.quran_widget_next,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_NEXT_SURAH, 2),
                )
                views.setOnClickPendingIntent(
                    R.id.quran_widget_title_group,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_SHOW_PICKER, 3),
                )
                views.setOnClickPendingIntent(
                    R.id.quran_widget_open,
                    openAppIntent(context, appWidgetId, state.surahNumber, state.anchorAyah, 4),
                )
                views.setContentDescription(
                    R.id.quran_widget_previous,
                    context.getString(R.string.quran_widget_previous_surah),
                )
            } else {
                views.setImageViewResource(R.id.quran_widget_previous, R.drawable.ic_widget_back)
                views.setViewVisibility(R.id.quran_widget_next, View.GONE)
                views.setViewVisibility(R.id.quran_widget_open, View.GONE)
                views.setViewVisibility(R.id.quran_widget_arabic_title, View.GONE)
                views.setOnClickPendingIntent(
                    R.id.quran_widget_previous,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_SHOW_READER, 5),
                )
                views.setOnClickPendingIntent(
                    R.id.quran_widget_title_group,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_SHOW_READER, 6),
                )
                views.setContentDescription(
                    R.id.quran_widget_previous,
                    context.getString(R.string.quran_widget_back_to_reader),
                )
            }
            views.setContentDescription(
                R.id.quran_widget_next,
                context.getString(R.string.quran_widget_next_surah),
            )
            views.setContentDescription(
                R.id.quran_widget_open,
                context.getString(R.string.quran_widget_open_in_myvault),
            )
            views.setContentDescription(
                R.id.quran_widget_title_group,
                if (isReader) context.getString(R.string.quran_widget_change_surah)
                else context.getString(R.string.quran_widget_back_to_reader),
            )
        }

        private fun bindCollection(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            state: QuranWidgetState,
            bucket: QuranWidgetSizeBucket,
        ) {
            val serviceIntent = Intent(context, QuranWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, state.surahNumber)
                putExtra(QuranWidgetContract.EXTRA_MODE, state.mode.name)
                putExtra(QuranWidgetContract.EXTRA_SIZE_BUCKET, bucket.name)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.quran_widget_collection, serviceIntent)
            views.setEmptyView(R.id.quran_widget_collection, R.id.quran_widget_empty)
            val template = if (state.mode == QuranWidgetMode.Reader) {
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 7,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 8,
                    Intent(context, QuranWidgetProvider::class.java).apply {
                        action = QuranWidgetContract.ACTION_SELECT_SURAH
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            }
            views.setPendingIntentTemplate(R.id.quran_widget_collection, template)
            if (state.mode == QuranWidgetMode.Reader && state.anchorAyah > 1) {
                views.setScrollPosition(R.id.quran_widget_collection, state.anchorAyah - 1)
            } else {
                views.setScrollPosition(R.id.quran_widget_collection, 0)
            }
            views.setOnClickPendingIntent(
                R.id.quran_widget_empty,
                openAppIntent(context, appWidgetId, state.surahNumber, state.anchorAyah, 9),
            )
        }

        private fun broadcastIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            suffix: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + suffix,
            Intent(context, QuranWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        internal fun openAppIntent(
            context: Context,
            appWidgetId: Int,
            surahNumber: Int,
            ayahNumber: Int,
            suffix: Int,
        ): PendingIntent {
            val location = validatedWidgetLocation(surahNumber, ayahNumber)
            return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + suffix,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(QuranWidgetContract.EXTRA_WIDGET_ID, appWidgetId)
                    putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, location.surahNumber)
                    putExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, location.ayahNumber)
                    data = Uri.parse("myvault://quran/${location.surahNumber}/${location.ayahNumber}?widget=$appWidgetId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun QuranWidgetSizeBucket.layoutResource(): Int = when (this) {
            QuranWidgetSizeBucket.Compact -> R.layout.widget_quran_compact
            QuranWidgetSizeBucket.Medium -> R.layout.widget_quran_medium
            QuranWidgetSizeBucket.Large -> R.layout.widget_quran_large
            QuranWidgetSizeBucket.ExtraLarge -> R.layout.widget_quran_extra_large
        }
    }
}
