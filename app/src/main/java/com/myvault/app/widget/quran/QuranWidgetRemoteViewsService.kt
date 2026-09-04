package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.myvault.app.R
import com.myvault.app.data.quran.SurahInfo

class QuranWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = QuranWidgetFactory(applicationContext, intent)
}

private class QuranWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
    )
    private val bucket = intent.getStringExtra(QuranWidgetContract.EXTRA_SIZE_BUCKET)
        ?.let { name -> QuranWidgetSizeBucket.entries.firstOrNull { it.name == name } }
        ?: QuranWidgetSizeBucket.Medium
    private var state = QuranWidgetStateStore(context).read(appWidgetId)
    private var ayahs: List<QuranWidgetAyah> = emptyList()
    private var surahs: List<SurahInfo> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        state = QuranWidgetStateStore(context).read(appWidgetId)
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
            }
            QuranWidgetMode.Picker -> {
                surahs = filteredWidgetSurahs(state.searchQuery)
                ayahs = emptyList()
            }
            QuranWidgetMode.Settings -> {
                ayahs = emptyList()
                surahs = emptyList()
            }
        }
    }

    override fun onDestroy() {
        ayahs = emptyList()
        surahs = emptyList()
    }

    override fun getCount(): Int = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.size
        QuranWidgetMode.Picker -> surahs.size
        QuranWidgetMode.Settings -> SETTINGS_ROW_COUNT
    }

    override fun getViewAt(position: Int): RemoteViews? = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.toReaderView()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.toPickerView()
        QuranWidgetMode.Settings -> settingsView(position)
    }

    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quran_loading_row)

    override fun getViewTypeCount(): Int = 5

    override fun getItemId(position: Int): Long = when (state.mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.let { it.surahNumber * 1_000L + it.ayahNumber } ?: position.toLong()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.num?.toLong() ?: position.toLong()
        QuranWidgetMode.Settings -> 10_000L + position
    }

    override fun hasStableIds(): Boolean = true

    private fun QuranWidgetAyah.toReaderView(): RemoteViews {
        val layout = when (bucket) {
            QuranWidgetSizeBucket.Compact -> R.layout.widget_quran_ayah_compact
            QuranWidgetSizeBucket.Medium -> R.layout.widget_quran_ayah_medium
            QuranWidgetSizeBucket.Large -> R.layout.widget_quran_ayah_large
            QuranWidgetSizeBucket.ExtraLarge -> R.layout.widget_quran_ayah_extra_large
        }
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(
                R.id.quran_widget_ayah_text,
                quranWidgetArabicText(arabicText, tajweedAnnotations, state.tajweedEnabled, context.isWidgetDarkTheme()),
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
            setOnClickFillInIntent(
                R.id.quran_widget_row,
                Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(QuranWidgetContract.EXTRA_WIDGET_ID, appWidgetId)
                    putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, this@toReaderView.surahNumber)
                    putExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, ayahNumber)
                    data = android.net.Uri.parse(
                        "myvault://quran/${this@toReaderView.surahNumber}/$ayahNumber?widget=$appWidgetId",
                    )
                },
            )
        }
    }

    private fun SurahInfo.toPickerView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_quran_surah_row).apply {
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
                Intent().putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, num),
            )
        }

    private fun settingsView(position: Int): RemoteViews? = when (position) {
        0 -> toggleSettingView(
            label = context.getString(R.string.quran_widget_translation),
            enabled = state.translationEnabled,
            command = QuranWidgetContract.COMMAND_TOGGLE_TRANSLATION,
        )
        1 -> RemoteViews(context.packageName, R.layout.widget_quran_setting_size_row).apply {
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
        3 -> RemoteViews(context.packageName, R.layout.widget_quran_setting_done_row).apply {
            setOnClickFillInIntent(
                R.id.quran_widget_setting_done,
                settingIntent(QuranWidgetContract.COMMAND_DONE),
            )
        }
        else -> null
    }

    private fun toggleSettingView(label: String, enabled: Boolean, command: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_quran_setting_toggle_row).apply {
            setTextViewText(R.id.quran_widget_setting_label, label)
            setTextViewText(
                R.id.quran_widget_setting_value,
                context.getString(if (enabled) R.string.quran_widget_on else R.string.quran_widget_off),
            )
            setTextColor(
                R.id.quran_widget_setting_value,
                context.getColor(if (enabled) R.color.quran_widget_accent else R.color.quran_widget_secondary),
            )
            setOnClickFillInIntent(R.id.quran_widget_row, settingIntent(command))
        }

    private fun settingIntent(command: String): Intent =
        Intent().putExtra(QuranWidgetContract.EXTRA_SETTING_COMMAND, command)

    private companion object {
        const val SETTINGS_ROW_COUNT = 4
    }
}
