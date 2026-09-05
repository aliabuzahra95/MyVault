package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.preferences.VaultPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** An emulator-launcher fixture. It never clears application content. */
@RunWith(AndroidJUnit4::class)
class QuranWidgetLauncherTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun waitFor(message: String, condition: () -> Boolean) {
        repeat(100) { if (condition()) return; Thread.sleep(100) }
        assertTrue(message, condition())
    }
    private fun screenshot(context: Context, name: String) {
        val directory = File(context.getExternalFilesDir(null), "quran-widget-audio-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun addRealLauncherWidgetForExactTargetAndPlaybackReview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, QuranWidgetProvider::class.java)
        val before = manager.getAppWidgetIds(component).toSet()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(manager.isRequestPinAppWidgetSupported)
            scenario.onActivity { assertTrue(manager.requestPinAppWidget(component, null, null)) }
            Thread.sleep(1500)
            screenshot(context, "pin-confirmation")
            val add = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.isClickable && it.text?.toString()?.contains("Add", ignoreCase = true) == true }
            assertNotNull("Launcher Add button", add)
            add!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor("Real widget added") { manager.getAppWidgetIds(component).any { it !in before } }
            val id = manager.getAppWidgetIds(component).first { it !in before }
            val store = QuranWidgetStateStore(context)
            store.initialize(id, 4, 5)
            store.selectSurah(id, 4)
            store.setAnchor(id, 4, 5)
            store.setTranslationEnabled(id, true)
            QuranWidgetProvider.updateWidget(context, manager, id)
            File(context.getExternalFilesDir(null), "quran-widget-test-id.txt").writeText(id.toString())
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            Thread.sleep(2500)
            screenshot(context, "launcher-4-5")
        }
    }
}
