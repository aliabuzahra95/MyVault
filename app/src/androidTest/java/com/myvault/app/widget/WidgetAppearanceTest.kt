package com.myvault.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetAppearanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun choicesPersistIndependentlyAndIgnoreSystemMode() {
        val store = WidgetAppearanceStore(context)
        store.delete(91001)
        store.delete(91002)
        try {
            assertFalse(store.isDark(91001))
            store.setDark(91001, true)
            assertTrue(WidgetAppearanceStore(context).isDark(91001))
            assertFalse(WidgetAppearanceStore(context).isDark(91002))
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val config = Configuration(context.resources.configuration).apply { uiMode = night }
                val host = context.createConfigurationContext(config)
                assertTrue(WidgetAppearanceStore(host).isDark(91001))
                assertFalse(WidgetAppearanceStore(host).isDark(91002))
            }
        } finally { store.delete(91001); store.delete(91002) }
    }

    @Test fun launcherViewsRenderSelectedPaletteInOppositeSystemTheme() {
        val store = WidgetAppearanceStore(context)
        try {
            for (dark in listOf(false, true)) {
                store.setDark(91003, dark)
                val config = Configuration(context.resources.configuration).apply {
                    uiMode = if (dark) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
                }
                val host = context.createConfigurationContext(config)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val note = widgetRemoteViews(context, 91003, R.layout.widget_note_viewer_medium)
                    note.setTextViewText(R.id.note_widget_title, "Revision notes")
                    note.setTextViewText(R.id.note_widget_context, "Study / Aqeedah")
                    val view = note.apply(host, FrameLayout(host))
                    assertEquals(android.graphics.Color.parseColor(if (dark) "#F3F1EC" else "#172033"),
                        view.findViewById<TextView>(R.id.note_widget_title).currentTextColor)
                    capture(view, "note-${if (dark) "dark" else "light"}", 380, 280)
                    val quick = widgetRemoteViews(context, 91003, R.layout.widget_quick_note_wide).apply(host, FrameLayout(host))
                    capture(quick, "quick-${if (dark) "dark" else "light"}", 300, 80)
                    val quran = widgetRemoteViews(context, 91003, R.layout.widget_quran_medium).apply(host, FrameLayout(host))
                    capture(quran, "quran-${if (dark) "dark" else "light"}", 380, 300)
                }
            }
        } finally { store.delete(91003) }
    }

    private fun capture(view: View, name: String, width: Int, height: Int) {
        val density = context.resources.displayMetrics.density
        val w = (width * density).toInt()
        val h = (height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
