package com.myvault.app.widget.quran

import android.content.Context
import android.content.res.Configuration
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.JsonReader
import com.myvault.app.data.quran.QuranCanonicalSource
import com.myvault.app.data.quran.TajweedAnnotation
import com.myvault.app.data.quran.adjustedQuranTajweedRange
import com.myvault.app.data.quran.quranTajweedColorArgb
import java.util.concurrent.ConcurrentHashMap

internal data class QuranWidgetAyah(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabicText: String,
    val translation: String,
    val tajweedAnnotations: List<TajweedAnnotation>,
)

/** Lightweight process cache over the same bundled assets used by the in-app reader. */
internal object QuranWidgetDisplaySource {
    private val translationsBySurah = ConcurrentHashMap<Int, Map<String, String>>()
    private val tajweedBySurah = ConcurrentHashMap<Int, Map<String, List<TajweedAnnotation>>>()

    fun surah(
        context: Context,
        surahNumber: Int,
        includeTranslation: Boolean,
        includeTajweed: Boolean,
    ): List<QuranWidgetAyah> {
        val translationSource = if (includeTranslation) translationSource(context, surahNumber) else emptyMap()
        val tajweedSource = if (includeTajweed) tajweedSource(context, surahNumber) else emptyMap()
        return QuranCanonicalSource.surah(context, surahNumber).map { ayah ->
            QuranWidgetAyah(
                verseKey = ayah.verseKey,
                surahNumber = ayah.surahNumber,
                ayahNumber = ayah.ayahNumber,
                arabicText = ayah.arabicText,
                translation = translationSource[ayah.verseKey].orEmpty(),
                tajweedAnnotations = tajweedSource[ayah.verseKey].orEmpty(),
            )
        }
    }

    private fun translationSource(context: Context, surahNumber: Int): Map<String, String> =
        translationsBySurah[surahNumber] ?: synchronized(translationsBySurah) {
            translationsBySurah[surahNumber] ?: readTranslationSurah(context, surahNumber).also {
                translationsBySurah[surahNumber] = it
            }
        }

    private fun tajweedSource(context: Context, surahNumber: Int): Map<String, List<TajweedAnnotation>> =
        tajweedBySurah[surahNumber] ?: synchronized(tajweedBySurah) {
            tajweedBySurah[surahNumber] ?: readTajweedSurah(context, surahNumber).also {
                tajweedBySurah[surahNumber] = it
            }
        }

    private fun readTranslationSurah(context: Context, surahNumber: Int): Map<String, String> = buildMap {
        val prefix = "$surahNumber:"
        context.applicationContext.assets.open("Sahih_international.json").bufferedReader().use { source ->
            JsonReader(source).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val verseKey = reader.nextName()
                    if (!verseKey.startsWith(prefix)) {
                        reader.skipValue()
                        continue
                    }
                    var translation = ""
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "t" -> translation = reader.nextString().trim()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (translation.isNotBlank()) put(verseKey, translation)
                }
                reader.endObject()
            }
        }
    }

    private fun readTajweedSurah(
        context: Context,
        targetSurah: Int,
    ): Map<String, List<TajweedAnnotation>> = buildMap {
        context.applicationContext.assets.open("Tajweed.json").bufferedReader().use { source ->
            JsonReader(source).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var surah = 0
                    var ayah = 0
                    var annotations = emptyList<TajweedAnnotation>()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "surah" -> surah = reader.nextInt()
                            "ayah" -> ayah = reader.nextInt()
                            "annotations" -> annotations = reader.readTajweedAnnotations()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (surah == targetSurah && ayah > 0) put("$surah:$ayah", annotations)
                }
                reader.endArray()
            }
        }
    }

    private fun JsonReader.readTajweedAnnotations(): List<TajweedAnnotation> = buildList {
        beginArray()
        while (hasNext()) {
            var start = 0
            var end = 0
            var rule = ""
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "start" -> start = nextInt()
                    "end" -> end = nextInt()
                    "rule" -> rule = nextString()
                    else -> skipValue()
                }
            }
            endObject()
            if (rule.isNotBlank()) add(TajweedAnnotation(start = start, end = end, rule = rule))
        }
        endArray()
    }
}

internal fun quranWidgetArabicText(
    text: String,
    annotations: List<TajweedAnnotation>,
    tajweedEnabled: Boolean,
    isDark: Boolean,
): CharSequence {
    if (!tajweedEnabled || annotations.isEmpty()) return text
    return SpannableString(text).apply {
        annotations.forEach { annotation ->
            val color = quranTajweedColorArgb(annotation.rule, isDark) ?: return@forEach
            val (start, end) = adjustedQuranTajweedRange(text, annotation.start, annotation.end)
            if (start < end) {
                setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

internal fun Context.isWidgetDarkTheme(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
