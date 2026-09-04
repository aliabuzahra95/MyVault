package com.myvault.app.widget.quran

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranWidgetStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: QuranWidgetStateStore

    @Before
    fun setUp() {
        context.getSharedPreferences("quran_widget_state", Context.MODE_PRIVATE).edit().clear().commit()
        store = QuranWidgetStateStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("quran_widget_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun displaySettingsPersistAndRemainIsolatedPerWidget() {
        val firstWidget = 901
        val secondWidget = 902
        store.initialize(firstWidget, 91, 1)
        store.initialize(secondWidget, 2, 1)

        store.setTranslationEnabled(firstWidget, true)
        store.adjustArabicFontLevel(firstWidget, 1)
        store.setTajweedEnabled(firstWidget, true)
        store.setSearchQuery(firstWidget, "Shams")

        val restoredStore = QuranWidgetStateStore(context)
        val first = restoredStore.read(firstWidget)
        val second = restoredStore.read(secondWidget)

        assertEquals(91, first.surahNumber)
        assertTrue(first.translationEnabled)
        assertEquals(DEFAULT_ARABIC_FONT_LEVEL + 1, first.arabicFontLevel)
        assertTrue(first.tajweedEnabled)
        assertEquals("Shams", first.searchQuery)

        assertEquals(2, second.surahNumber)
        assertFalse(second.translationEnabled)
        assertEquals(DEFAULT_ARABIC_FONT_LEVEL, second.arabicFontLevel)
        assertFalse(second.tajweedEnabled)
        assertEquals("", second.searchQuery)
    }

    @Test
    fun selectingPickerResultReturnsToReaderAndClearsTemporarySearch() {
        val widgetId = 903
        store.initialize(widgetId, 1, 1)
        store.setMode(widgetId, QuranWidgetMode.Picker)
        store.setSearchQuery(widgetId, "91")

        store.selectSurah(widgetId, 91)

        val state = QuranWidgetStateStore(context).read(widgetId)
        assertEquals(91, state.surahNumber)
        assertEquals(QuranWidgetMode.Reader, state.mode)
        assertEquals("", state.searchQuery)
    }
}
