package com.myvault.app.ui.screens

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.formatting.NoteFormattingUiState
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.viewmodel.NoteUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorInteractionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val body get() = compose.onNodeWithTag("NoteEditorBody")
    private val scroll get() = compose.onNodeWithTag("NoteEditorScroll")
    private fun scrollPosition() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun selection() = body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
    private fun select(range: TextRange) = body.performSemanticsAction(SemanticsActions.SetSelection) {
        it(range.start, range.end, true)
    }

    @Test fun syntheticLongNoteKeyboardSelectionToolbarAndUndo() = exercise(20_000)
    @Test fun fiftyThousandWordNoteUsesTheSameEditor() = exercise(50_000)
    @Test fun fiveThousandWordNoteUsesTheSameEditor() = exercise(5_000)

    @Test fun readModeUsesContinuousSelectionAndDocumentPositionToolbar() {
        val text = (0 until 20_000).joinToString(" ") { if (it % 10 == 0) "\nSection$it" else if (it % 2 == 0) "العربية" else "word" }
        val note = NoteEntity("reader-disposable", null, title = "Disposable reader", bodyPlainText = text,
            isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
        compose.activity.setContent { VaultTheme { ReadingScreen(NoteUiState(note = note,
            richText = VaultRichTextDocument(text, emptyList())), onBackClick = {}, onEditClick = {}) } }
        val reader = compose.onNodeWithTag("NoteReaderText")
        val list = compose.onNodeWithTag("NoteReaderScroll")
        compose.onNodeWithTag("NoteReaderToolbar").assertIsDisplayed()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 20_000f) }
        compose.onNodeWithTag("NoteReaderToolbar").assertDoesNotExist()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -200f) }
        compose.onNodeWithTag("NoteReaderToolbar").assertDoesNotExist()
        val viewport = list.fetchSemanticsNode().boundsInRoot
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "input tap ${viewport.center.x.toInt()} ${viewport.center.y.toInt()}").close()
        SystemClock.sleep(200)
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        reader.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val display = androidx.compose.ui.text.AnnotatedString(text).withVaultBidiIsolation()
        val offset = display.offsetMapping.transformedToOriginal(layouts.last().getOffsetForPosition(
            Offset(200f, viewport.center.y - reader.fetchSemanticsNode().positionInRoot.y)))
        reader.performSemanticsAction(SemanticsActions.SetSelection) { it(offset, offset + 20, true) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val handle = compose.onNodeWithTag("EditorSelectionEnd", useUnmergedTree = true)
        handle.performTouchInput { down(center); moveBy(Offset(0f, 2000f)) }
        compose.mainClock.advanceTimeBy(3000)
        val selection = reader.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertTrue(selection.end > offset + 1000)
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(text, reader.fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        compose.onNodeWithTag("NoteReaderToolbar").assertDoesNotExist()
    }

    private fun exercise(words: Int) {
        val text = (0 until words).joinToString(" ") {
            if (it % 12 == 0) "\nSection$it" else if (it % 2 == 0) "نص" else "word"
        }
        val marks = listOf(VaultStyleMark(0, 10, VaultInlineStyle.Heading), VaultStyleMark(100, text.length - 10, VaultInlineStyle.Bold))
        val note = NoteEntity("editor-disposable", null, title = "Disposable editor", bodyPlainText = text,
            isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
        var saved: VaultRichTextDocument? = null
        var generation by mutableIntStateOf(0)
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        val initialDocument = VaultRichTextDocument(text, marks)
        val openedAt = SystemClock.uptimeMillis()
        compose.activity.setContent {
            VaultTheme {
                keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                key(generation) {
                EditorScreen(NoteUiState(note = note, richText = saved ?: initialDocument), NoteFormattingUiState(),
                    onBackClick = {}, onMenuClick = {}, onTitleChange = {},
                    onContentChange = { t, m, l -> saved = VaultRichTextDocument(t, m, l) },
                    onRunFormattingTool = { _, _, _, _, _ -> }, onClearFormattingResult = {}, onAttachDocument = {})
                }
            }
        }
        compose.onNodeWithTag("NoteEditorToolbar").assertIsDisplayed()
        println("words=$words editorOpenMs=${SystemClock.uptimeMillis() - openedAt}")
        val maxScroll = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, maxScroll * .75f) }
        compose.waitForIdle()
        compose.onNodeWithTag("NoteEditorToolbar").assertIsNotDisplayed()
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -150f) }
        compose.onNodeWithTag("NoteEditorToolbar").assertIsNotDisplayed()
        // Real tap opens the IME; get the actual cursor rectangle, including display-only bidi markers.
        val tapViewport = scroll.fetchSemanticsNode().boundsInRoot
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "input tap ${tapViewport.center.x.toInt()} ${(tapViewport.bottom - 100f).toInt()}").close()
        compose.waitForIdle()
        SystemClock.sleep(700)
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val display = text.let { androidx.compose.ui.text.AnnotatedString(it).withVaultBidiIsolation() }
        val cursor = layout.last().getCursorRect(display.offsetMapping.originalToTransformed(selection().end))
        val field = body.fetchSemanticsNode()
        val viewport = scroll.fetchSemanticsNode().boundsInRoot
        val caretTop = field.positionInRoot.y + cursor.top
        val caretBottom = field.positionInRoot.y + cursor.bottom
        assertTrue("Caret must be above editor bottom/IME ($caretBottom, $viewport)", caretBottom <= viewport.bottom)
        assertTrue("Caret must be below editor top ($caretTop, $viewport)", caretTop >= viewport.top)
        // Ordinary manual scrolling must not be pulled back to the old caret when the finger lifts.
        val beforeManual = scrollPosition()
        scroll.performTouchInput { swipeDown() }
        compose.waitForIdle()
        val afterManual = scrollPosition()
        assertTrue(afterManual < beforeManual)
        SystemClock.sleep(150)
        compose.waitForIdle()
        assertEquals(afterManual, scrollPosition(), 2f)
        compose.onNodeWithTag("NoteEditorToolbar").assertIsNotDisplayed()
        val movedCaret = (selection().end - 50).coerceAtLeast(0)
        select(TextRange(movedCaret))
        compose.waitForIdle()
        assertEquals(movedCaret, selection().end)
        // Dismiss IME, select around the visible cursor, then HOLD the end handle below the viewport.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        SystemClock.sleep(400)
        assertEquals(movedCaret, selection().end)
        compose.runOnIdle { keyboard?.show() }
        SystemClock.sleep(400)
        compose.waitForIdle()
        assertEquals("Keyboard reopening must preserve exact caret", movedCaret, selection().end)
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val reopenedCursor = layout.last().getCursorRect(display.offsetMapping.originalToTransformed(movedCaret))
        val reopenedField = body.fetchSemanticsNode()
        val reopenedViewport = scroll.fetchSemanticsNode().boundsInRoot
        assertTrue(reopenedField.positionInRoot.y + reopenedCursor.top >= reopenedViewport.top)
        assertTrue(reopenedField.positionInRoot.y + reopenedCursor.bottom <= reopenedViewport.bottom)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        SystemClock.sleep(400)
        val offset = selection().end.coerceAtMost(text.length - 30)
        select(TextRange(offset, offset + 20))
        compose.waitForIdle()
        val before = selection()
        val handle = compose.onNodeWithTag("EditorSelectionEnd", useUnmergedTree = true)
        compose.mainClock.autoAdvance = false
        handle.performTouchInput { down(center); moveBy(Offset(0f, 2000f)) }
        compose.mainClock.advanceTimeBy(10_000)
        val extended = selection()
        assertTrue("Holding at edge must extend the REAL selected range over multiple screens", extended.end > before.end + 4000)
        println("words=$words heldSelectionWords=${editorWordCount(text.substring(extended.min, extended.max))}")
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val stopped = scrollPosition()
        SystemClock.sleep(350)
        assertEquals("Release stops auto-scroll", stopped, scrollPosition(), 2f)
        compose.onNodeWithTag("NoteEditorToolbar").assertIsNotDisplayed()
        // Upward extension uses the other handle and leaves the far endpoint fixed.
        val top = compose.onNodeWithTag("EditorSelectionStart", useUnmergedTree = true)
        // Bring a fresh short selection into the currently visible part of the document.
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val visibleViewport = scroll.fetchSemanticsNode().boundsInRoot
        val current = display.offsetMapping.transformedToOriginal(layout.last().getOffsetForPosition(
            Offset(visibleViewport.center.x - body.fetchSemanticsNode().positionInRoot.x,
                visibleViewport.center.y - body.fetchSemanticsNode().positionInRoot.y)))
        select(TextRange(current - 20, current))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        top.performTouchInput { down(center); moveBy(Offset(0f, -2000f)) }
        compose.mainClock.advanceTimeBy(1500)
        assertTrue(selection().start < current - 100)
        top.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val deletion = TextRange(text.length / 4, text.length * 3 / 4)
        select(deletion)
        val deleteStarted = SystemClock.uptimeMillis()
        body.performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        println("words=$words uiDeletionMs=${SystemClock.uptimeMillis() - deleteStarted}")
        assertEquals(text.removeRange(deletion.start, deletion.end), body.fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        compose.mainClock.advanceTimeBy(450)
        compose.waitForIdle()
        assertEquals(text.removeRange(deletion.start, deletion.end), saved?.text)
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.waitForIdle()
        assertEquals(text, body.fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        compose.mainClock.advanceTimeBy(450)
        compose.waitForIdle()
        assertEquals(text, saved?.text)
        assertEquals(marks.toSet(), saved?.styleMarks?.toSet())
        compose.runOnIdle {
            saved = parseVaultRichTextDocument(saved!!.toStorageJson())
            generation++
        }
        compose.waitForIdle()
        assertEquals(text, body.fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
        compose.onNodeWithTag("NoteEditorToolbar").assertIsDisplayed()
    }
}
