package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.quran.*
import com.myvault.app.ui.quran.QuranReaderSurface
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable presentation fixtures only; never changes the user's reflection records. */
@RunWith(AndroidJUnit4::class)
class ReflectionsPortDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        fun walk(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
        return walk(automation.rootInActiveWindow)
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(100) { nodes().firstOrNull(predicate)?.let { return it }; SystemClock.sleep(60) }
        error("Reflections control not found. " + nodes().mapNotNull { it.text ?: it.contentDescription })
    }
    private fun clickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val target = clickableParent(node) ?: nodes().firstNotNullOfOrNull {
            if (it.text == node.text && it.isVisibleToUser) clickableParent(it) else null
        }
        assertTrue("No clickable target for ${node.text ?: node.contentDescription}", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(600)
    }
    private fun text(value: String) = awaitNode { it.text?.toString() == value }
    private fun search(value: String) {
        assertTrue(awaitNode { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        SystemClock.sleep(450)
    }
    private fun screenshot(name: String) {
        SystemClock.sleep(500)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "reflections-premium").apply { mkdirs() }
        val width = instrumentation.targetContext.resources.configuration.screenWidthDp
        File(directory, "$width-$name.png").outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun item(id: String, surah: Int, ayah: Int, body: String, time: Long) = QuranReflectionItem(
        id, "Reflection on $surah:$ayah", quranCatalog.first { it.num == surah }.name, surah, ayah, "$surah:$ayah",
        "", "", body, body, time,
    )
    private val fixtures = listOf(
        item("anfal", 8, 53, "Allah does not change the favour bestowed upon a people until they change what is within themselves. A reminder to examine my own gratitude and conduct before looking only at what has changed around me.", 60),
        item("fatiha", 1, 1, "Beginning in the name of Allah is more than an opening phrase. I want it to shape the intention behind what follows, especially the small tasks I usually do without thinking.", 50),
        item("hajj12", 22, 12, "Calling upon what can neither harm nor benefit: where do I place my trust when I feel uncertain? Return to this passage alongside the surrounding ayat.", 40),
        item("hajj11", 22, 11, "When good touches him, he is content; when tested, he turns away. This makes me think about the difference between faith rooted in conviction and faith made conditional on comfort. I should revisit this when a difficult day makes gratitude feel distant. What practices help me remain steady before the next test arrives?", 30),
        item("arabic", 2, 183, "الصيام يعلّم القلب مراقبة الله في السرّ قبل العلن. أريد أن أتذكّر أن المقصود ليس الجوع وحده، بل التقوى ومحاسبة النفس في الكلام والعمل.", 20),
        item("mixed", 2, 185, "هُدًى لِلنَّاسِ — guidance should reach my daily choices. Set aside time after reading to write one practical change, rather than measuring the day only by how many pages I finished.", 10),
        item("guidance", 1, 6, "Asking for guidance every day means I never outgrow the need to be guided. Bring this request into decisions about learning, family and the way I speak to others.", 5),
    )

    @Test fun productionDrawerPlacesReflectionsBetweenQuranAndMemorise() {
        ActivityScenario.launch(MainActivity::class.java).use {
            tap(awaitNode { it.contentDescription?.toString() == "Open navigation" })
            val quran = Rect().also { text("Qur'an").getBoundsInScreen(it) }
            val reflections = Rect().also { text("Reflections").getBoundsInScreen(it) }
            val memorise = Rect().also { text("Memorise").getBoundsInScreen(it) }
            assertTrue(quran.centerY() < reflections.centerY())
            assertTrue(reflections.centerY() < memorise.centerY())
            screenshot("09-drawer")
            tap(text("Reflections")); text("Your Qur’an reflections")
            tap(awaitNode { it.contentDescription?.toString() == "Open navigation" })
            tap(text("Reflections")); text("Your Qur’an reflections")
        }
    }

    @Test fun compactListThemesSearchFilterSortAndLivePresentationUpdates() {
        var mode by mutableStateOf(VaultThemeMode.Light)
        var data by mutableStateOf(fixtures)
        var opened: QuranReflectionItem? = null
        var quranOpened = false
        var showList by mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VaultTheme(mode) {
                val holder = rememberSaveableStateHolder()
                if (showList) holder.SaveableStateProvider("reflections") {
                    ReflectionsScreen(data, {}, { quranOpened = true }, { opened = it; showList = false }, Modifier.statusBarsPadding())
                } else TextButton(onClick = { showList = true }) { Text("Return to reflections") }
            } } }
            text("Reflections"); text("7 reflections · 4 Surahs")
            tap(awaitNode { it.contentDescription?.toString() == "Sort: Newest first" }); tap(text("Qur’an order"))
            val firstBody = nodes().mapNotNull { it.text?.toString() }.first { it in fixtures.map { f -> f.reflectionBody } }
            assertEquals(fixtures[1].reflectionBody, firstBody)
            screenshot("01-light")
            scenario.onActivity { mode = VaultThemeMode.Dark }; screenshot("02-dark")
            scenario.onActivity { mode = VaultThemeMode.Oled }; screenshot("03-oled")
            scenario.onActivity { mode = VaultThemeMode.Light }
            tap(awaitNode { it.contentDescription?.toString() == "Surah: All Surahs" })
            text("Filter by Surah"); screenshot("04-surah-sheet")
            search("البقرة"); tap(text("Al-Baqara"))
            text("2 reflections in Al-Baqara"); screenshot("05-al-baqarah")
            assertFalse(nodes().any { it.text?.toString() == fixtures[0].reflectionBody })
            tap(awaitNode { it.contentDescription?.toString() == "Sort: Qur’an order" }); tap(text("Newest first"))
            val bodies = nodes().mapNotNull { it.text?.toString() }.filter { it in fixtures.map { f -> f.reflectionBody } }
            assertEquals(fixtures[4].reflectionBody, bodies.first())
            tap(awaitNode { it.contentDescription?.toString() == "Sort: Newest first" }); tap(text("Oldest first"))
            search("2:185"); text("1 reflection in Al-Baqara"); screenshot("06-search")
            tap(text(fixtures[5].reflectionBody))
            assertEquals("mixed", opened?.noteId); assertEquals("2:185", opened?.verseKey)
            tap(text("Return to reflections"))
            text("1 reflection in Al-Baqara")
            awaitNode { it.isEditable && it.text?.toString() == "2:185" }
            awaitNode { it.contentDescription?.toString() == "Sort: Oldest first" }
            tap(awaitNode { it.contentDescription?.toString() == "Clear search" })
            text("2 reflections in Al-Baqara")
            tap(awaitNode { it.contentDescription?.toString() == "Surah: Al-Baqara" })
            search("114"); tap(text("An-Naas")); text("No reflections in An-Naas")
            tap(awaitNode { it.contentDescription?.toString() == "Surah: An-Naas" })
            tap(text("All Surahs")); text("7 reflections · 4 Surahs")
            search("Anfal"); text(fixtures[0].reflectionBody)
            search("8:53"); text(fixtures[0].reflectionBody)
            search("change the favour"); text(fixtures[0].reflectionBody)
            search("nothing-matches"); text("No reflections found"); screenshot("07-empty-results")
            tap(text("Clear search / filter")); text("7 reflections · 4 Surahs")
            tap(awaitNode { it.contentDescription?.toString() == "Surah: All Surahs" })
            search("Hajj"); tap(text("Al-Hajj"))
            scenario.onActivity { data = fixtures.map { if (it.noteId == "hajj11") it.copy(reflectionBody = "Updated reflection") else it } }
            text("Updated reflection")
            scenario.onActivity { data = emptyList() }; text("No reflections yet"); screenshot("08-no-reflections")
            tap(text("Open Qur’an")); assertTrue(quranOpened)
        }
    }

    @Test fun groupedScrollPositionSurvivesOpeningAndReturningToList() {
        var showList by mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VaultTheme(VaultThemeMode.Light) {
                val holder = rememberSaveableStateHolder()
                if (showList) holder.SaveableStateProvider("reflections") {
                    ReflectionsScreen(fixtures, {}, {}, { showList = false }, Modifier.statusBarsPadding())
                } else TextButton(onClick = { showList = true }) { Text("Return to reflections") }
            } } }
            tap(awaitNode { it.contentDescription?.toString() == "Sort: Newest first" }); tap(text("Qur’an order"))
            repeat(2) {
                awaitNode { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(600)
            }
            val target = awaitNode { it.contentDescription?.toString() == "Open reflection Al-Hajj 22:11" }
            val before = Rect().also { target.getBoundsInScreen(it) }
            tap(target); tap(text("Return to reflections"))
            val after = Rect().also { awaitNode { it.contentDescription?.toString() == "Open reflection Al-Hajj 22:11" }.getBoundsInScreen(it) }
            assertEquals(before.top, after.top)
        }
    }

    @Test fun explicitReflectionOpensExistingSheetWithExactIdAndCanonicalAyah() {
        val target = fixtures[3]
        val ayahs = runBlocking { QuranTextRepository(instrumentation.targetContext).getSurahAyahs(22) }
        var request by mutableStateOf<String?>(target.noteId)
        var state by mutableStateOf(QuranReaderUiState(selectedSurah = quranCatalog.first { it.num == 22 },
            ayahs = ayahs, loading = false, restoredAyah = 1, pendingScrollVerseKey = "22:11",
            reflectionsByVerse = mapOf("22:11" to listOf(target.copy(noteId = "other", reflectionBody = "Wrong duplicate"), target))))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VaultTheme(VaultThemeMode.Dark) {
                QuranReaderSurface(uiState = state, onOpenNavigation = {}, onOpenSelector = {}, onSetArabicFontPercent = {},
                    onSetTranslationFontPercent = {}, onSetTranslationEnabled = {}, onSetTranslationSource = {}, onSetTajweedEnabled = {},
                    onLastReadAyahChanged = { _, _ -> }, onToggleTafsir = {}, onSelectTafsirSource = {}, onToggleBookmark = {},
                    onCreateReflectionNote = { _, _, _ -> }, onUpdateReflection = { _, _, _, _ -> }, onDeleteReflection = {},
                    onOpenBookmark = {}, onOpenReflectionsHub = {}, onOpenReciterPicker = {}, onOpenReciterPreferencePicker = {},
                    onDismissReciterPicker = {}, onSelectAudioReciter = {}, onPlayAudioForAyah = {}, onToggleAudioPlayback = {},
                    onStopAudio = {}, onSeekAudioTo = {}, onSetAudioSpeed = {}, onSetAudioListeningMode = {}, onSkipAudioBy = {},
                    onPlayAdjacentAudio = {}, onChooseOtherReciter = {}, onRefreshAudioDownloads = {}, onDownloadSurahAudio = { _, _ -> },
                    onMemoriseFromHere = {}, onPendingScrollHandled = { state = state.copy(pendingScrollVerseKey = null) },
                    requestedReflectionNoteId = request, requestedReflectionVerseKey = "22:11", onRequestedReflectionHandled = { request = null })
            } } }
            text("Edit reflection")
            awaitNode { it.isEditable && it.text?.toString() == target.reflectionBody }
            assertFalse(nodes().any { it.isEditable && it.text?.toString() == "Wrong duplicate" })
            screenshot("08-exact-ayah-reflection")
            assertNull(request)
        }
    }
}
