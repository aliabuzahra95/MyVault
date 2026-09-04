package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.myvault.app.R
import com.myvault.app.data.quran.CanonicalArabicAyah
import com.myvault.app.data.quran.QuranCanonicalSource
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog

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
    private val surahNumber = intent.getIntExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, 1)
    private val mode = intent.getStringExtra(QuranWidgetContract.EXTRA_MODE)
        ?.let { name -> QuranWidgetMode.entries.firstOrNull { it.name == name } }
        ?: QuranWidgetMode.Reader
    private val bucket = intent.getStringExtra(QuranWidgetContract.EXTRA_SIZE_BUCKET)
        ?.let { name -> QuranWidgetSizeBucket.entries.firstOrNull { it.name == name } }
        ?: QuranWidgetSizeBucket.Medium
    private var ayahs: List<CanonicalArabicAyah> = emptyList()
    private var surahs: List<SurahInfo> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        if (mode == QuranWidgetMode.Reader) {
            ayahs = runCatching { QuranCanonicalSource.surah(context, surahNumber) }.getOrDefault(emptyList())
            surahs = emptyList()
        } else {
            surahs = quranCatalog
            ayahs = emptyList()
        }
    }

    override fun onDestroy() {
        ayahs = emptyList()
        surahs = emptyList()
    }

    override fun getCount(): Int = if (mode == QuranWidgetMode.Reader) ayahs.size else surahs.size

    override fun getViewAt(position: Int): RemoteViews? = when (mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.toReaderView()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.toPickerView()
    }

    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quran_loading_row)

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = when (mode) {
        QuranWidgetMode.Reader -> ayahs.getOrNull(position)?.let { it.surahNumber * 1_000L + it.ayahNumber } ?: position.toLong()
        QuranWidgetMode.Picker -> surahs.getOrNull(position)?.num?.toLong() ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun CanonicalArabicAyah.toReaderView(): RemoteViews {
        val layout = when (bucket) {
            QuranWidgetSizeBucket.Compact -> R.layout.widget_quran_ayah_compact
            QuranWidgetSizeBucket.Medium -> R.layout.widget_quran_ayah_medium
            QuranWidgetSizeBucket.Large -> R.layout.widget_quran_ayah_large
            QuranWidgetSizeBucket.ExtraLarge -> R.layout.widget_quran_ayah_extra_large
        }
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.quran_widget_ayah_text, arabicText)
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
                    putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, surahNumber)
                    putExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, ayahNumber)
                    data = android.net.Uri.parse("myvault://quran/$surahNumber/$ayahNumber?widget=$appWidgetId")
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
}
