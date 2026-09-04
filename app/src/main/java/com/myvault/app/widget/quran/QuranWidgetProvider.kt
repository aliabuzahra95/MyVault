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
            QuranWidgetContract.ACTION_SHOW_PICKER -> {
                store.setSearchQuery(appWidgetId, "")
                store.setMode(appWidgetId, QuranWidgetMode.Picker)
            }
            QuranWidgetContract.ACTION_SHOW_READER -> store.setMode(appWidgetId, QuranWidgetMode.Reader)
            QuranWidgetContract.ACTION_SHOW_SETTINGS -> store.setMode(appWidgetId, QuranWidgetMode.Settings)
            QuranWidgetContract.ACTION_SELECT_SURAH -> {
                store.selectSurah(
                    appWidgetId,
                    intent.getIntExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, 1),
                )
            }
            QuranWidgetContract.ACTION_SETTING_COMMAND -> {
                when (intent.getStringExtra(QuranWidgetContract.EXTRA_SETTING_COMMAND)) {
                    QuranWidgetContract.COMMAND_TOGGLE_TRANSLATION -> {
                        val current = store.read(appWidgetId)
                        store.setTranslationEnabled(appWidgetId, !current.translationEnabled)
                    }
                    QuranWidgetContract.COMMAND_DECREASE_ARABIC -> store.adjustArabicFontLevel(appWidgetId, -1)
                    QuranWidgetContract.COMMAND_INCREASE_ARABIC -> store.adjustArabicFontLevel(appWidgetId, 1)
                    QuranWidgetContract.COMMAND_TOGGLE_TAJWEED -> {
                        val current = store.read(appWidgetId)
                        store.setTajweedEnabled(appWidgetId, !current.tajweedEnabled)
                    }
                    QuranWidgetContract.COMMAND_DONE -> store.setMode(appWidgetId, QuranWidgetMode.Reader)
                }
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
            QuranWidgetContract.ACTION_SHOW_SETTINGS,
            QuranWidgetContract.ACTION_SELECT_SURAH,
            QuranWidgetContract.ACTION_SETTING_COMMAND,
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
            val isPicker = state.mode == QuranWidgetMode.Picker
            views.setTextViewText(
                R.id.quran_widget_title,
                when (state.mode) {
                    QuranWidgetMode.Reader -> surah.name
                    QuranWidgetMode.Picker -> context.getString(R.string.quran_widget_choose_surah)
                    QuranWidgetMode.Settings -> context.getString(R.string.quran_widget_reader_settings)
                },
            )
            views.setTextViewText(R.id.quran_widget_arabic_title, if (isReader) surah.arabic else "")
            views.setTextViewText(
                R.id.quran_widget_metadata,
                when (state.mode) {
                    QuranWidgetMode.Reader -> context.getString(
                        R.string.quran_widget_surah_metadata,
                        surah.num,
                        surah.ayat,
                        state.anchorAyah,
                    )
                    QuranWidgetMode.Picker -> {
                        if (state.searchQuery.isBlank()) {
                            context.getString(R.string.quran_widget_surah_count, quranCatalog.size)
                        } else {
                            context.getString(
                                R.string.quran_widget_search_results,
                                state.searchQuery,
                                filteredWidgetSurahs(state.searchQuery).size,
                            )
                        }
                    }
                    QuranWidgetMode.Settings -> context.getString(
                        R.string.quran_widget_settings_for_surah,
                        surah.name,
                    )
                },
            )

            if (isReader) {
                views.setImageViewResource(R.id.quran_widget_previous, R.drawable.ic_widget_chevron_left)
                views.setViewVisibility(R.id.quran_widget_next, View.VISIBLE)
                views.setViewVisibility(
                    R.id.quran_widget_open,
                    if (bucket == QuranWidgetSizeBucket.Compact || bucket == QuranWidgetSizeBucket.Medium) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    },
                )
                views.setViewVisibility(R.id.quran_widget_settings, View.VISIBLE)
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
                views.setImageViewResource(R.id.quran_widget_settings, R.drawable.ic_widget_settings)
                views.setOnClickPendingIntent(
                    R.id.quran_widget_settings,
                    broadcastIntent(context, appWidgetId, QuranWidgetContract.ACTION_SHOW_SETTINGS, 10),
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
                if (isPicker) {
                    views.setViewVisibility(R.id.quran_widget_settings, View.VISIBLE)
                    views.setImageViewResource(R.id.quran_widget_settings, R.drawable.ic_widget_search)
                    views.setOnClickPendingIntent(
                        R.id.quran_widget_settings,
                        searchIntent(context, appWidgetId),
                    )
                    views.setContentDescription(
                        R.id.quran_widget_settings,
                        context.getString(R.string.quran_widget_search_surahs),
                    )
                } else {
                    views.setViewVisibility(R.id.quran_widget_settings, View.GONE)
                }
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
            if (isReader) {
                views.setContentDescription(
                    R.id.quran_widget_settings,
                    context.getString(R.string.quran_widget_open_reader_settings),
                )
            }
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
                putExtra(QuranWidgetContract.EXTRA_TRANSLATION_ENABLED, state.translationEnabled)
                putExtra(QuranWidgetContract.EXTRA_ARABIC_FONT_LEVEL, state.arabicFontLevel)
                putExtra(QuranWidgetContract.EXTRA_TAJWEED_ENABLED, state.tajweedEnabled)
                putExtra(QuranWidgetContract.EXTRA_SEARCH_QUERY, state.searchQuery)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.quran_widget_collection, serviceIntent)
            views.setEmptyView(R.id.quran_widget_collection, R.id.quran_widget_empty)
            val template = when (state.mode) {
                QuranWidgetMode.Reader -> PendingIntent.getActivity(
                    context,
                    requestCode(appWidgetId, 7),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                QuranWidgetMode.Picker -> PendingIntent.getBroadcast(
                    context,
                    requestCode(appWidgetId, 8),
                    Intent(context, QuranWidgetProvider::class.java).apply {
                        action = QuranWidgetContract.ACTION_SELECT_SURAH
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                QuranWidgetMode.Settings -> PendingIntent.getBroadcast(
                    context,
                    requestCode(appWidgetId, 11),
                    Intent(context, QuranWidgetProvider::class.java).apply {
                        action = QuranWidgetContract.ACTION_SETTING_COMMAND
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
                if (state.mode == QuranWidgetMode.Picker) {
                    searchIntent(context, appWidgetId)
                } else {
                    openAppIntent(context, appWidgetId, state.surahNumber, state.anchorAyah, 9)
                },
            )
            views.setTextViewText(
                R.id.quran_widget_empty,
                if (state.mode == QuranWidgetMode.Picker && state.searchQuery.isNotBlank()) {
                    context.getString(R.string.quran_widget_no_surah_found)
                } else {
                    context.getString(R.string.quran_widget_unavailable)
                },
            )
        }

        private fun broadcastIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            suffix: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(appWidgetId, suffix),
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
                requestCode(appWidgetId, suffix),
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

        private fun searchIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode(appWidgetId, 12),
            Intent(context, QuranWidgetSearchActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("myvault://quran-widget/$appWidgetId/search")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun requestCode(appWidgetId: Int, suffix: Int): Int = appWidgetId * 100 + suffix
    }
}
