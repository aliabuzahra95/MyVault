package com.myvault.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetUtilityTaskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun widgetUtilitiesCannotJoinTheMainAppTask() {
        listOf("widget.quran.QuranWidgetSearchActivity", "widget.WidgetAppearanceActivity",
            "widget.note.NoteWidgetConfigActivity").forEach { name ->
            val info = context.packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.$name"), 0,
            )
            assertTrue(name, info.taskAffinity.isNullOrEmpty())
            assertTrue(name, info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        }
    }

    @Test fun mainActivityRetainsItsNormalTaskAffinity() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context.packageName, "${context.packageName}.MainActivity"), 0,
        )
        assertEquals(context.packageName, info.taskAffinity)
    }
}
