package com.myvault.app.ui.screens

import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.formatting.NoteFormattingUiState
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.viewmodel.NoteTableUiState
import com.myvault.app.ui.viewmodel.NoteUiState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic reader/editor tests, plus explicitly opted-in writes to named disposable fixtures. */
@RunWith(AndroidJUnit4::class)
class NoteViewportDeviceTest {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun nodes(node: AccessibilityNodeInfo? = automation.rootInActiveWindow): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(100) {
            nodes().firstOrNull(predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Expected note control was not available")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.bufferedReader().readText()
        }

    @Test fun mixedRichBodyPreservesAnchorAndUserCaret() = exercise(false)
    @Test fun bodyAboveTableUsesTheSameCaretOwner() = exercise(true)

    // Explicit opt-in only: these two pre-existing disposable fixtures are the entire write allowlist.
    @Test fun liveStudyFixture() = exerciseLiveFixture("RC-20260909-Quick-note")
    @Test fun liveCourseFixture() = exerciseLiveFixture("RC-20260909-Course-note")

    private fun tap(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(350)
    }

    private fun exerciseLiveFixture(title: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCursorFixtures") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            tap(awaitNode { it.contentDescription == "Open navigation" })
            tap(awaitNode { it.text?.toString() == "Search" })
            awaitNode { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title)
            })
            tap(awaitNode { !it.isEditable && it.text?.toString() == title })
            tap(awaitNode { it.contentDescription == "Edit" })
            awaitNode { it.isEditable && it.text?.toString() == title }
            val field = awaitNode { it.isEditable && it.text?.toString() != title }
            val old = field.text.toString()
            val expanded = if (old.contains("CURSOR-LIVE-LONG")) old else old + "\n\nCURSOR-LIVE-LONG\n\n" +
                (1..100).joinToString("\n\n") { "Paragraph $it: Disposable revision text for testing keyboard and reading position.\nفقرة عربية للاختبار فقط" }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expanded)
            }))
            SystemClock.sleep(800)
            shell("input keyevent 4")
            awaitNode { it.contentDescription == "Edit" }
            repeat(12) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
            }
            shell("logcat -c")
            tap(awaitNode { it.contentDescription == "Edit" })
            val reopened = awaitNode { it.isEditable && it.text?.contains("CURSOR-LIVE-LONG") == true }
            assertEquals("Real repository autosave/reopen must preserve every character", expanded, reopened.text.toString())
            SystemClock.sleep(600)
            val before = shell("logcat -d -s NoteViewport:D '*:S'")
            val restored = Regex("scroll=(\\d+)").findAll(before).last().groupValues[1].toInt()
            assertTrue("Long live note must retain a nonzero reading anchor", restored > 0)
            val bounds = Rect().also { reopened.getBoundsInScreen(it) }
            shell("logcat -c")
            shell("input tap ${bounds.centerX()} ${bounds.bottom - 120}")
            SystemClock.sleep(1500)
            val focused = Regex("scroll=(\\d+) cursor=(\\d+) focused=true ime=(\\d+)")
                .findAll(shell("logcat -d -s NoteViewport:D '*:S'"))
                .map { m -> m.groupValues.drop(1).map(String::toInt) }.toList()
            assertTrue(focused.isNotEmpty())
            assertTrue(focused.any { it[2] > 0 })
            assertEquals(1, focused.map { it[1] }.distinct().size)
            assertTrue(focused.map { it[0] }.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(focused.maxOf { it[0] } - focused.minOf { it[0] } < 1300)
            shell("screencap -p /data/local/tmp/cursor-$title.png")
            repeat(2) {
                if (nodes().none { it.contentDescription == "Edit" }) {
                    shell("input keyevent 4")
                    SystemClock.sleep(250)
                }
            }
            awaitNode { it.contentDescription == "Edit" }
        }
    }

    private fun exercise(withTable: Boolean) {
        val body = "Viewport fixture\n\n" + (1..120).joinToString("\n\n") {
            "Section $it\nEnglish revision paragraph with a link https://example.com.\n" +
                "فقرة عربية للاختبار مع نص محفوظ\n• First bullet\n1. Numbered item"
        }
        val marks = listOf(VaultStyleMark(0, 16, VaultInlineStyle.Heading),
            VaultStyleMark(18, 27, VaultInlineStyle.Bold))
        val note = NoteEntity("viewport-test", null, title = "Viewport fixture", bodyPlainText = body,
            isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
        val state = NoteUiState(note = note, richText = VaultRichTextDocument(body, marks),
            tables = if (withTable) listOf(NoteTableUiState("fixture-table", 1, 2, listOf(listOf("A", "B")))) else emptyList())
        var editing by mutableStateOf(false)
        var anchor: NoteViewportAnchor? = null
        var saved: String? = null
        var savedMarks: List<VaultStyleMark>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    VaultTheme(mode = VaultThemeMode.Dark) {
                        if (editing) {
                            EditorScreen(state, NoteFormattingUiState(), onBackClick = { editing = false },
                                onMenuClick = {}, onTitleChange = {}, onContentChange = { text, styles, _ ->
                                    saved = text
                                    savedMarks = styles
                                }, onRunFormattingTool = { _, _, _, _, _ -> }, onClearFormattingResult = {},
                                onAttachDocument = {}, readingAnchor = anchor)
                        } else {
                            ReadingScreen(state, onBackClick = {}, onEditClick = { editing = true },
                                onEditAtAnchor = { anchor = it; editing = true })
                        }
                    }
                }
            }
            awaitNode { it.contentDescription == "Edit" }
            repeat(12) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(100)
            }
            shell("logcat -c")
            val edit = awaitNode { it.contentDescription == "Edit" }
            val editBounds = Rect().also { edit.getBoundsInScreen(it) }
            shell("input tap ${editBounds.centerX()} ${editBounds.centerY()}")
            val field = awaitNode { it.isEditable && it.text?.startsWith("Viewport fixture\n") == true }
            SystemClock.sleep(650)
            assertNotNull(anchor)
            assertTrue(anchor!!.offset > 0)
            assertTrue(anchor!!.matches(note.id, body))
            assertFalse(field.isFocused)
            assertNull("Selection/mode entry must not save a changed body", saved)
            val before = shell("logcat -d -s NoteViewport:D '*:S'")
            val anchorScroll = Regex("scroll=(\\d+)").findAll(before).last().groupValues[1].toInt()
            assertTrue("Reader anchor must be applied before focus", anchorScroll > 0)
            val bounds = Rect().also { field.getBoundsInScreen(it) }
            shell("logcat -c")
            shell("input tap ${bounds.centerX()} ${bounds.bottom - 120}")
            SystemClock.sleep(1500)
            val trace = shell("logcat -d -s NoteViewport:D '*:S'")
            val focused = Regex("scroll=(\\d+) cursor=(\\d+) focused=true ime=(\\d+)")
                .findAll(trace).map { match -> match.groupValues.drop(1).map(String::toInt) }.toList()
            assertTrue("A real pointer tap must focus the body", focused.isNotEmpty())
            assertTrue(focused.any { it[2] > 0 })
            assertEquals("Native focus must not replace the tapped selection", 1, focused.map { it[1] }.distinct().size)
            assertTrue(focused.first()[1] < body.length)
            val scrolls = focused.map { it[0] }
            assertTrue("No overshoot then return", scrolls.zipWithNext().all { (a, b) -> b >= a })
            assertTrue("Only keyboard avoidance, not a document jump", scrolls.max() - scrolls.min() < bounds.height())
            assertNull("Focus and cursor changes alone must not autosave", saved)
            val updated = body + "\nSaved immediately after focus."
            assertTrue(awaitNode { it.isEditable && it.text?.startsWith("Viewport fixture\n") == true }
                .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
                }))
            scenario.onActivity { editing = false }
            awaitNode { it.contentDescription == "Edit" }
            assertEquals(updated, saved)
            assertEquals(marks.toSet(), savedMarks?.toSet())
            shell("input keyevent 4")
        }
    }
}
