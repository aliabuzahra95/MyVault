package com.myvault.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.myvault.app.data.formatting.NoteFormattingUiState
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.narration.NarrationUiState
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.viewmodel.NoteUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NoteNarrationUiRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val paragraphs = (0 until 120).map {
        "Paragraph $it contains disposable narration and selection test text."
    }
    private val body = paragraphs.joinToString("\n\n")
    private val note = NoteEntity("disposable-ui-note", null, title = "UI Test", bodyPlainText = body,
        isPinned = false, isFavourite = false, createdAt = 1L, updatedAt = 1L)
    private val ui = NoteUiState(note = note, richText = VaultRichTextDocument(body, emptyList()))

    @Test fun readModeSelectAllCopiesEntireLongNote() {
        compose.setContent { VaultTheme { ReadingScreen(ui, onBackClick = {}, onEditClick = {}) } }
        val text = compose.onNodeWithTag("NoteReaderText")
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val point = layout.getBoundingBox(layout.layoutInput.text.text.indexOf("Paragraph") + 3).center
        text.performTouchInput { longClick(point, durationMillis = 1_000L) }
        compose.waitForIdle()
        assertFalse(text.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
        text.performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.A); keyUp(Key.CtrlLeft) }
        text.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange,
            androidx.compose.ui.text.TextRange(0, body.length)))
        text.performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.C); keyUp(Key.CtrlLeft) }
        compose.runOnIdle {
            val clipboard = ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
                .replace(Regex("[\\u200E\\u200F\\u2066-\\u2069]"), "")
            assertEquals(body, copied)
        }
    }


    @Test fun editorToolbarRevealsFromDeepScrollAndHidesAgain() {
        compose.setContent { VaultTheme {
            EditorScreen(ui, NoteFormattingUiState(), onBackClick = {}, onMenuClick = {},
                onTitleChange = {}, onContentChange = { _, _, _ -> },
                onRunFormattingTool = { _, _, _, _, _ -> }, onClearFormattingResult = {}, onAttachDocument = {})
        } }
        val scroll = compose.onNodeWithTag("NoteEditorScroll")
        repeat(4) { scroll.performTouchInput { swipeUp() } }
        compose.onNodeWithTag("NoteEditorToolbar").assertDoesNotExist()
        scroll.performTouchInput { swipeDown() }
        compose.onNodeWithTag("NoteEditorToolbar").assertIsDisplayed()
        scroll.performTouchInput { swipeUp() }
        compose.onNodeWithTag("NoteEditorToolbar").assertDoesNotExist()
        compose.onNodeWithText("Follow Text").assertDoesNotExist()
        compose.onNodeWithText("Following text").assertDoesNotExist()
    }

    @Test fun editorSelectAllStillSelectsEntireNote() {
        compose.setContent { VaultTheme {
            EditorScreen(ui, NoteFormattingUiState(), onBackClick = {}, onMenuClick = {},
                onTitleChange = {}, onContentChange = { _, _, _ -> },
                onRunFormattingTool = { _, _, _, _, _ -> }, onClearFormattingResult = {}, onAttachDocument = {})
        } }
        val text = compose.onNode(hasSetTextAction() and hasText(body))
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val point = layout.getBoundingBox(layout.layoutInput.text.text.indexOf("Paragraph") + 3).center
        text.performTouchInput { longClick(point, durationMillis = 1_000L) }
        text.performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.A); keyUp(Key.CtrlLeft) }
        text.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange,
            androidx.compose.ui.text.TextRange(0, body.length)))
    }

    @Test fun permanentFollowReturnsAfterDragAndDoesNotScrollWhilePaused() {
        val narration = mutableStateOf(NarrationUiState(status = NarrationPlaybackStatus.Playing,
            noteId = note.id, activeSentence = paragraphs[90], activeSentenceSourceOffset = body.indexOf(paragraphs[90])))
        compose.setContent { VaultTheme { ReadingScreen(ui, narration.value, onBackClick = {}, onEditClick = {}) } }
        val scroll = compose.onNodeWithTag("NoteReaderScroll")
        fun position(): Float = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.waitUntil(5_000) { position() > 1f }
        compose.waitForIdle()
        val followed = position()
        scroll.performTouchInput { down(center); moveBy(Offset(0f, 200f)) }
        compose.waitForIdle()
        val dragged = position()
        assertNotEquals(followed, dragged)
        scroll.performTouchInput { up() }
        fun centered(): Boolean {
            val layouts = mutableListOf<TextLayoutResult>()
            val text = compose.onNodeWithTag("NoteReaderText")
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val start = layout.layoutInput.text.text.indexOf(paragraphs[90])
            val end = start + paragraphs[90].length - 1
            val midpoint = text.fetchSemanticsNode().positionInRoot.y +
                (layout.getBoundingBox(start).top + layout.getBoundingBox(end).bottom) / 2f
            return kotlin.math.abs(midpoint - scroll.fetchSemanticsNode().boundsInRoot.center.y) < 8f
        }
        compose.waitUntil(5_000) { centered() }
        compose.onNodeWithTag("NoteReaderToolbar").assertIsDisplayed()
        compose.runOnIdle { narration.value = narration.value.copy(status = NarrationPlaybackStatus.Paused) }
        scroll.performTouchInput { swipeDown() }
        compose.waitForIdle()
        val paused = position()
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(paused, position(), 0.0001f)
        compose.onNodeWithText("Follow Text").assertDoesNotExist()
    }
}
