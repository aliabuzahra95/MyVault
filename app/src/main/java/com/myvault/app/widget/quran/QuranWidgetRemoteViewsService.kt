package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.myvault.app.widget.widgetAppearanceContext
import android.widget.RemoteViewsService
import com.myvault.app.R
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.audio.AudioReciterUiModel
import com.myvault.app.data.quran.audio.QuranTimedRecitations

class QuranWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = QuranWidgetFactory(applicationContext, intent)
}

internal class QuranWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
    )
    private var bucket = currentBucket()
    private var state = QuranWidgetStateStore(context).read(appWidgetId)
    private var ayahs: List<QuranWidgetAyah> = emptyList()
    private var surahs: List<SurahInfo> = emptyList()
    private var reciters: List<AudioReciterUiModel> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        state = QuranWidgetStateStore(context).read(appWidgetId)
        bucket = currentBucket()
        when (state.mode) {
            QuranWidgetMode.Reader -> {
                ayahs = runCatching {
                    QuranWidgetDisplaySource.surah(
                        context = context,
                        surahNumber = state.surahNumber,
                        includeTranslation = state.translationEnabled,
                        includeTajweed = state.tajweedEnabled,
                    )
                }.getOrDefault(emptyList())
                surahs = emptyList()
                reciters = emptyList()
            }
            QuranWidgetMode.Picker -> {
                surahs = filteredWidgetSurahs(state.searchQuery)
                ayahs = emptyList()
                reciters = emptyList()
            }
            QuranWidgetMode.Settings -> {
                ayahs = emptyList()
                surahs = emptyList()
                reciters = emptyList()
            }
            QuranWidgetMode.ReciterPicker -> {
                ayahs = emptyList()
                surahs = emptyList()
                reciters = QuranTimedRecitations.widgetReciters
            }
        }
    }

    override fun onDestroy() {
        ayahs = emptyList()
        surahs = emptyList()
        reciters = emptyList()
    }

    override fun getCount(): Int = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.size
        QuranWidgetMode.Picker -> surahs.size
        QuranWidgetMode.Settings -> SETTINGS_ROW_COUNT
        QuranWidgetMode.ReciterPicker -> reciters.size
    }

    override fun getViewAt(position: Int): RemoteViews? = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.toReaderView()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.toPickerView()
        QuranWidgetMode.Settings -> settingsView(position)
        QuranWidgetMode.ReciterPicker -> reciters.getOrNull(position)?.toReciterView()
    }

    override fun getLoadingView(): RemoteViews = com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_loading_row)

    override fun getViewTypeCount(): Int = 7

    override fun getItemId(position: Int): Long = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.let { it.surahNumber * 1_000L + it.ayahNumber } ?: position.toLong()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.num?.toLong() ?: position.toLong()
        QuranWidgetMode.Settings -> 10_000L + position
        QuranWidgetMode.ReciterPicker -> 20_000L + (reciters.getOrNull(position)?.id ?: position)
    }

    override fun hasStableIds(): Boolean = true

    private fun QuranWidgetAyah.toReaderView(): RemoteViews {
        val layout = when (bucket) {
            QuranWidgetSizeBucket.Compact -> R.layout.widget_quran_ayah_compact
            QuranWidgetSizeBucket.Medium -> R.layout.widget_quran_ayah_medium
            QuranWidgetSizeBucket.Large -> R.layout.widget_quran_ayah_large
            QuranWidgetSizeBucket.ExtraLarge -> R.layout.widget_quran_ayah_extra_large
        }
        return com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, layout).apply {
            setTextViewText(
                R.id.quran_widget_ayah_text,
                quranWidgetArabicText(arabicText, tajweedAnnotations, state.tajweedEnabled, com.myvault.app.widget.WidgetAppearanceStore(context).isDark(appWidgetId)),
            )
            setTextViewTextSize(
                R.id.quran_widget_ayah_text,
                TypedValue.COMPLEX_UNIT_SP,
                quranWidgetArabicTextSize(bucket, state.arabicFontLevel),
            )
            setTextViewText(R.id.quran_widget_translation, translation)
            setViewVisibility(
                R.id.quran_widget_translation,
                if (state.translationEnabled && translation.isNotBlank()) View.VISIBLE else View.GONE,
            )
            setTextViewText(R.id.quran_widget_ayah_reference, verseKey)
            setContentDescription(
                R.id.quran_widget_row,
                context.getString(R.string.quran_widget_open_ayah, verseKey),
            )
            setOnClickFillInIntent(R.id.quran_widget_row,
                QuranWidgetPlayback.rowIntent(appWidgetId, surahNumber, ayahNumber, QuranWidgetPlayback.OPEN))
            // A focusable Play child can suppress the collection's root item click.
            // Bind the reading surfaces explicitly while keeping Play independent.
            for (viewId in intArrayOf(R.id.quran_widget_ayah_text, R.id.quran_widget_translation, R.id.quran_widget_ayah_reference)) {
                setOnClickFillInIntent(viewId,
                    QuranWidgetPlayback.rowIntent(appWidgetId, surahNumber, ayahNumber, QuranWidgetPlayback.OPEN))
            }
            setOnClickFillInIntent(R.id.quran_widget_ayah_play,
                QuranWidgetPlayback.rowIntent(appWidgetId, surahNumber, ayahNumber, QuranWidgetPlayback.PLAY))
            val playback = quranWidgetPlayback(context).state.value
            val active = playback.sourceWidgetId == appWidgetId && playback.verseKey == verseKey && playback.isPlaying
            setQuranAudioIcon(
                context,
                appWidgetId,
                R.id.quran_widget_ayah_play,
                if (active) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            setContentDescription(R.id.quran_widget_ayah_play, if (active) "Pause ayah $verseKey" else "Play ayah $verseKey")

        }
    }

    private fun SurahInfo.toPickerView(): RemoteViews =
        com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_surah_row).apply {
            setTextViewText(R.id.quran_widget_surah_number, num.toString())
            setTextViewText(R.id.quran_widget_surah_name, name)
            setTextViewText(R.id.quran_widget_surah_arabic, arabic)
            setTextViewText(
                R.id.quran_widget_surah_detail,
                context.getString(R.string.quran_widget_surah_row_metadata, type, ayat),
            )
            setViewVisibility(
                R.id.quran_widget_surah_detail,
                if (bucket == QuranWidgetSizeBucket.Compact) View.GONE else View.VISIBLE,
            )
            setContentDescription(
                R.id.quran_widget_row,
                context.getString(R.string.quran_widget_select_surah, num, name),
            )
            setOnClickFillInIntent(
                R.id.quran_widget_row,
                Intent()
                    .putExtra(QuranWidgetPlayback.COMMAND, QuranWidgetPlayback.SELECT_SURAH)
                    .putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, num),
            )
        }

    private fun AudioReciterUiModel.toReciterView(): RemoteViews =
        com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_reciter_row).apply {
            setTextViewText(R.id.quran_widget_reciter_name, name)
            val selected = id == state.reciterId
            setTextViewText(R.id.quran_widget_reciter_selected, if (selected) context.getString(R.string.quran_widget_selected) else "")
            setViewVisibility(R.id.quran_widget_reciter_selected, if (selected) View.VISIBLE else View.GONE)
            val selectionIntent = Intent()
                .putExtra(QuranWidgetPlayback.COMMAND, QuranWidgetPlayback.SELECT_RECITER)
                .putExtra(QuranWidgetContract.EXTRA_RECITER_ID, id)
                .putExtra(QuranWidgetContract.EXTRA_RECITER_NAME, name)
            for (viewId in intArrayOf(
                R.id.quran_widget_row,
                R.id.quran_widget_reciter_name,
                R.id.quran_widget_reciter_selected,
            )) {
                setOnClickFillInIntent(viewId, selectionIntent)
            }
        }

    private fun settingsView(position: Int): RemoteViews? = when (position) {
        0 -> toggleSettingView(
            label = context.getString(R.string.quran_widget_translation),
            enabled = state.translationEnabled,
            command = QuranWidgetContract.COMMAND_TOGGLE_TRANSLATION,
        )
        1 -> com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_setting_size_row).apply {
            setTextViewText(
                R.id.quran_widget_setting_size_value,
                context.getString(R.string.quran_widget_arabic_size_level, state.arabicFontLevel),
            )
            setBoolean(
                R.id.quran_widget_setting_decrease,
                "setEnabled",
                state.arabicFontLevel > MIN_ARABIC_FONT_LEVEL,
            )
            setBoolean(
                R.id.quran_widget_setting_increase,
                "setEnabled",
                state.arabicFontLevel < MAX_ARABIC_FONT_LEVEL,
            )
            setOnClickFillInIntent(
                R.id.quran_widget_setting_decrease,
                settingIntent(QuranWidgetContract.COMMAND_DECREASE_ARABIC),
            )
            setOnClickFillInIntent(
                R.id.quran_widget_setting_increase,
                settingIntent(QuranWidgetContract.COMMAND_INCREASE_ARABIC),
            )
        }
        2 -> toggleSettingView(
            label = context.getString(R.string.quran_widget_tajweed),
            enabled = state.tajweedEnabled,
            command = QuranWidgetContract.COMMAND_TOGGLE_TAJWEED,
        )
        3 -> com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_setting_reciter_row).apply {
            setTextViewText(R.id.quran_widget_setting_reciter_value, state.reciterName)
            val reciterIntent = settingIntent(QuranWidgetContract.COMMAND_SHOW_RECITERS)
            for (viewId in intArrayOf(
                R.id.quran_widget_row,
                R.id.quran_widget_setting_reciter_label,
                R.id.quran_widget_setting_reciter_value,
                R.id.quran_widget_setting_reciter_chevron,
            )) {
                setOnClickFillInIntent(viewId, reciterIntent)
            }
        }
        4 -> toggleSettingView(
            label = "Appearance",
            enabled = com.myvault.app.widget.WidgetAppearanceStore(context).isDark(appWidgetId),
            command = "toggle_appearance",
        ).apply {
            setTextViewText(R.id.quran_widget_setting_value,
                if (com.myvault.app.widget.WidgetAppearanceStore(context).isDark(appWidgetId)) "Dark" else "Light")
        }
        5 -> com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_setting_done_row).apply {
            setOnClickFillInIntent(
                R.id.quran_widget_setting_done,
                settingIntent(QuranWidgetContract.COMMAND_DONE),
            )
        }
        else -> null
    }

    private fun toggleSettingView(label: String, enabled: Boolean, command: String): RemoteViews =
        com.myvault.app.widget.widgetRemoteViews(context, appWidgetId, R.layout.widget_quran_setting_toggle_row).apply {
            setTextViewText(R.id.quran_widget_setting_label, label)
            setTextViewText(
                R.id.quran_widget_setting_value,
                context.getString(if (enabled) R.string.quran_widget_on else R.string.quran_widget_off),
            )
            setTextColor(
                R.id.quran_widget_setting_value,
                context.widgetAppearanceContext(appWidgetId).getColor(if (enabled) R.color.quran_widget_accent else R.color.quran_widget_secondary),
            )
            val toggleIntent = settingIntent(command)
            for (viewId in intArrayOf(
                R.id.quran_widget_row,
                R.id.quran_widget_setting_label,
                R.id.quran_widget_setting_value,
            )) {
                setOnClickFillInIntent(viewId, toggleIntent)
            }
        }

    private fun settingIntent(command: String): Intent =
        Intent()
            .putExtra(QuranWidgetPlayback.COMMAND, QuranWidgetPlayback.SETTING)
            .putExtra(QuranWidgetContract.EXTRA_SETTING_COMMAND, command)

    private fun currentBucket(): QuranWidgetSizeBucket {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        return quranWidgetSizeBucket(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300),
        )
    }

    private companion object {
        const val SETTINGS_ROW_COUNT = 6
    }
}
