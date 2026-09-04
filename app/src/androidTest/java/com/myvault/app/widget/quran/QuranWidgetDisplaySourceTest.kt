package com.myvault.app.widget.quran

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.data.quran.quranTajweedColorArgb
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranWidgetDisplaySourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun alBaqaraUsesBundledTranslationAndTajweedWithinWidgetStartupBudget() {
        lateinit var ayahs: List<QuranWidgetAyah>
        val elapsedMs = measureTimeMillis {
            ayahs = QuranWidgetDisplaySource.surah(
                context = context,
                surahNumber = 2,
                includeTranslation = true,
                includeTajweed = true,
            )
        }

        assertEquals(286, ayahs.size)
        assertFalse(ayahs.first().translation.isBlank())
        val tajweedAyah = ayahs.first { ayah ->
            ayah.tajweedAnnotations.any { quranTajweedColorArgb(it.rule, isDark = false) != null }
        }
        val rendered = quranWidgetArabicText(
            text = tajweedAyah.arabicText,
            annotations = tajweedAyah.tajweedAnnotations,
            tajweedEnabled = true,
            isDark = false,
        )
        assertTrue(rendered is Spanned)
        assertTrue((rendered as Spanned).getSpans(0, rendered.length, ForegroundColorSpan::class.java).isNotEmpty())
        assertTrue("Cold widget source load took ${elapsedMs}ms", elapsedMs < 10_000)
    }
}
