package com.myvault.app.widget.quran

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.VaultPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses only the disposable emulator widget already pinned by the launcher fixture. */
@RunWith(AndroidJUnit4::class)
class QuranWidgetInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun waitFor(message: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        repeat((timeoutMs / 100).toInt()) { if (condition()) return; Thread.sleep(100) }
        assertTrue(message, condition())
    }
    private fun capture(context: Context, name: String) {
        val directory = File(context.getExternalFilesDir(null), "quran-widget-audio-evidence").apply { mkdirs() }
        automation.takeScreenshot()?.let { bitmap -> File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun showWidgetPage() {
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(1_000)
        repeat(3) {
            if (nodes(automation.rootInActiveWindow).any {
                    it.viewIdResourceName == "com.myvault.app:id/quran_widget_collection" && it.isVisibleToUser
                }) return
            shell("input swipe 1000 1000 80 1000 700")
            Thread.sleep(1_000)
        }
    }

    private fun visibleAyahReferences(): List<String> = nodes(automation.rootInActiveWindow)
        .filter {
            it.isVisibleToUser &&
                it.viewIdResourceName == "com.myvault.app:id/quran_widget_ayah_reference"
        }
        .mapNotNull { it.text?.toString() }

    @Test fun actualWidgetTextOpensExactAyahDespiteSavedSurah16() = runBlocking {
        assumeTrue("Never change production phone preferences", Build.MODEL.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val args = InstrumentationRegistry.getArguments()
        val surah = args.getString("surah")?.toIntOrNull() ?: 4
        val ayah = args.getString("ayah")?.toIntOrNull() ?: 5
        val manager = AppWidgetManager.getInstance(context)
        val id = File(context.getExternalFilesDir(null), "quran-widget-test-id.txt").readText().trim().toInt()
        assertTrue(manager.getAppWidgetIds(ComponentName(context, QuranWidgetProvider::class.java)).contains(id))
        val preferences = VaultPreferences(context)
        preferences.setQuranReadingPosition(16, 1)
        preferences.setQuranAudioReciterId(7)
        val store = QuranWidgetStateStore(context)
        store.selectSurah(id, surah)
        store.setAnchor(id, surah, ayah)
        store.setTranslationEnabled(id, false)
        repeat(5) { store.adjustArabicFontLevel(id, -1) }
        QuranWidgetProvider.updateWidget(context, manager, id, scrollToAyah = ayah)
        showWidgetPage()
        waitFor("Widget collection visible") { nodes(automation.rootInActiveWindow).any { it.viewIdResourceName == "com.myvault.app:id/quran_widget_collection" && it.isVisibleToUser } }
        Thread.sleep(1500)
        QuranWidgetProvider.updateWidget(context, manager, id)
        waitFor("Target row $surah:$ayah visible") {
            nodes(automation.rootInActiveWindow).any { it.contentDescription?.toString()?.contains("$surah:$ayah") == true && it.isVisibleToUser }
        }
        capture(context, "widget-before-$surah-$ayah")
        val row = nodes(automation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString() == "Open ayah $surah:$ayah in MyVault" }
            ?: nodes(automation.rootInActiveWindow).first { it.viewIdResourceName == "com.myvault.app:id/quran_widget_row" && it.contentDescription?.contains("$surah:$ayah") == true }
        val arabic = nodes(row).first { it.viewIdResourceName == "com.myvault.app:id/quran_widget_ayah_text" }
        val bounds = Rect().also { arabic.getBoundsInScreen(it) }
        // Real launcher touch dispatch, not an adb deep link or direct receiver invocation.
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        waitFor("MyVault opened from widget") { automation.rootInActiveWindow?.packageName?.toString() == "com.myvault.app" }
        Thread.sleep(1500)
        capture(context, "reader-opened-$surah-$ayah")
        val target = com.myvault.app.data.quran.quranCatalog.first { it.num == surah }
        waitFor("Exact reader location $surah:$ayah, not saved 16:1", 120_000) {
            val visible = nodes(automation.rootInActiveWindow).filter { it.isVisibleToUser }
            visible.any { it.text?.toString()?.contains(target.name) == true } &&
                visible.any { it.text?.toString() == "Ayah $ayah of ${target.ayat}" }
        }
        Thread.sleep(1000)
        capture(context, "reader-after-$surah-$ayah")
        assertFalse("Text tap does not start playback", quranWidgetPlayback(context).state.value.active)
    }

    @Test fun widgetPlayUsesItsOwnFaresPreferenceWithoutOpeningReader() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = File(context.getExternalFilesDir(null), "quran-widget-test-id.txt").readText().trim().toInt()
        val controller = quranWidgetPlayback(context)
        try {
            QuranWidgetStateStore(context).apply {
                selectSurah(id, 1)
                setReciter(id, 1_000_081, "Fares Abbad")
                setMode(id, QuranWidgetMode.Reader)
            }
            QuranWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), id)
            showWidgetPage()
            waitFor("Widget play visible") { nodes(automation.rootInActiveWindow).any { it.viewIdResourceName == "com.myvault.app:id/quran_widget_play_surah" && it.isVisibleToUser } }
            val play = nodes(automation.rootInActiveWindow).first { it.viewIdResourceName == "com.myvault.app:id/quran_widget_play_surah" && it.isVisibleToUser }
            val bounds = Rect().also(play::getBoundsInScreen)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            waitFor("Widget recitation started", 90_000) { controller.state.value.isPlaying }
            assertEquals(1_000_081, controller.state.value.reciter?.id)
            assertEquals(id, controller.state.value.sourceWidgetId)
            assertTrue(controller.state.value.synchronized)
            assertNotEquals("com.myvault.app", automation.rootInActiveWindow?.packageName?.toString())
            capture(context, "widget-fares-playing")
        } finally {
            instrumentation.runOnMainSync { controller.stop() }
        }
    }

    @Test fun routineWidgetUpdatePreservesLauncherManagedScrollPosition() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppWidgetManager.getInstance(context)
        val id = File(context.getExternalFilesDir(null), "quran-widget-test-id.txt").readText().trim().toInt()
        QuranWidgetStateStore(context).apply {
            selectSurah(id, 2)
            setAnchor(id, 2, 1)
            setTranslationEnabled(id, false)
        }
        QuranWidgetProvider.updateWidget(context, manager, id, scrollToAyah = 1)
        showWidgetPage()
        waitFor("Widget ayahs visible") { visibleAyahReferences().isNotEmpty() }
        repeat(3) {
            shell("input swipe 800 930 800 350 600")
            Thread.sleep(500)
        }
        val before = visibleAyahReferences()
        assertTrue("Manual scroll moved beyond persisted ayah 2:1: $before", before.none { it == "2:1" })

        QuranWidgetProvider.updateWidget(context, manager, id)
        Thread.sleep(2_500)

        assertEquals("Routine refresh must not issue a persisted-anchor scroll", before, visibleAyahReferences())
    }
}
