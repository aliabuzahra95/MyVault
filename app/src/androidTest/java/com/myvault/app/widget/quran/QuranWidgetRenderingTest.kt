package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.R
import com.myvault.app.widget.WidgetAppearanceStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranWidgetRenderingTest {
    @Test fun realRemoteViewsKeepArabicRightAlignedAcrossAppearanceAndSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = 90941
        val store = QuranWidgetStateStore(context)
        try {
            store.initialize(id, 4, 5)
            store.setTranslationEnabled(id, true)
            store.setTajweedEnabled(id, true)
            for (dark in listOf(false, true)) for (bucket in QuranWidgetSizeBucket.entries) {
                WidgetAppearanceStore(context).setDark(id, dark)
                val factory = QuranWidgetFactory(context, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(QuranWidgetContract.EXTRA_SIZE_BUCKET, bucket.name))
                factory.onDataSetChanged()
                assertEquals(4_005L, factory.getItemId(4))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val row = factory.getViewAt(4)!!.apply(context, FrameLayout(context))
                    row.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    val arabic = row.findViewById<TextView>(R.id.quran_widget_ayah_text)
                    assertEquals(View.TEXT_DIRECTION_RTL, arabic.textDirection)
                    assertEquals(Gravity.RIGHT, arabic.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                    assertEquals(-1, arabic.layout.getParagraphDirection(0))
                    assertFalse(arabic.includeFontPadding)
                    val lineHeightRatio = arabic.lineHeight / arabic.textSize
                    assertTrue(
                        "Arabic line height must leave diacritic room; ratio=$lineHeightRatio",
                        lineHeightRatio in 1.75f..2.15f,
                    )
                    assertEquals(View.TEXT_DIRECTION_LTR, row.findViewById<TextView>(R.id.quran_widget_translation).textDirection)
                    assertEquals("4:5", row.findViewById<TextView>(R.id.quran_widget_ayah_reference).text.toString())
                }
            }
        } finally { store.delete(id); WidgetAppearanceStore(context).delete(id) }
    }
}
