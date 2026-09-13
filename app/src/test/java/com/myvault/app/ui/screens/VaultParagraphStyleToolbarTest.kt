package com.myvault.app.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.myvault.app.ui.components.EditorTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultParagraphStyleToolbarTest {
    @Test
    fun plainParagraphIsDetectedAtCursor() {
        val value = TextFieldValue("First paragraph\nSecond paragraph", selection = TextRange(20))

        assertEquals(EditorTool.Paragraph, activeVaultParagraphToolForSelection(value, emptyList(), emptySet()))
    }

    @Test
    fun headingIsDetectedAcrossSelectedParagraph() {
        val text = "Heading text\nBody text"
        val marks = listOf(VaultStyleMark(0, 12, VaultInlineStyle.Heading2))
        val value = TextFieldValue(text, selection = TextRange(1, 10))

        assertEquals(EditorTool.Heading2, activeVaultParagraphToolForSelection(value, marks, emptySet()))
    }

    @Test
    fun mixedParagraphSelectionHasNoActiveStyle() {
        val text = "Heading text\nBody text"
        val marks = listOf(VaultStyleMark(0, 12, VaultInlineStyle.Heading))
        val value = TextFieldValue(text, selection = TextRange(0, text.length))

        assertNull(activeVaultParagraphToolForSelection(value, marks, emptySet()))
    }

    @Test
    fun applyingHeadingStylesWholeParagraphAndPreservesInlineMarks() {
        val text = "First paragraph\nSecond paragraph"
        val cursor = text.indexOf("paragraph", startIndex = 10)
        val value = TextFieldValue(text, selection = TextRange(cursor))
        val bold = VaultStyleMark(text.indexOf("Second"), text.indexOf("Second") + 6, VaultInlineStyle.Bold)

        val update = applyVaultParagraphStyleFromToolbar(value, listOf(bold), emptySet(), EditorTool.Heading3)

        assertEquals(value.selection, TextRange(cursor))
        assertTrue(update.marks.contains(bold))
        assertTrue(update.marks.any {
            it.style == VaultInlineStyle.Heading3 &&
                it.start == text.indexOf("Second") && it.end == text.length
        })
    }

    @Test
    fun paragraphStyleClearsHeadingWithoutRemovingOtherFormatting() {
        val text = "Styled heading"
        val marks = listOf(
            VaultStyleMark(0, text.length, VaultInlineStyle.Heading4),
            VaultStyleMark(0, 6, VaultInlineStyle.Italic),
        )
        val value = TextFieldValue(text, selection = TextRange(4))

        val update = applyVaultParagraphStyleFromToolbar(value, marks, emptySet(), EditorTool.Paragraph)

        assertTrue(update.marks.none { it.style in setOf(VaultInlineStyle.Heading, VaultInlineStyle.Heading2, VaultInlineStyle.Heading3, VaultInlineStyle.Heading4) })
        assertTrue(update.marks.any { it.style == VaultInlineStyle.Italic && it.start == 0 && it.end == 6 })
    }

    @Test
    fun mixedArabicEnglishParagraphKeepsTextAndSelectionAuthoritative() {
        val text = "عنوان عربي with English\nفقرة ثانية"
        val selection = TextRange(3, 18)
        val value = TextFieldValue(text, selection = selection)

        val update = applyVaultParagraphStyleFromToolbar(value, emptyList(), emptySet(), EditorTool.Heading)

        assertEquals(text, value.text)
        assertEquals(selection, value.selection)
        assertTrue(update.marks.any { it.style == VaultInlineStyle.Heading && it.start == 0 && it.end == text.indexOf('\n') })
    }

    @Test
    fun emptyParagraphUsesPendingHeadingStyle() {
        val value = TextFieldValue("Body\n", selection = TextRange(5))

        val update = applyVaultParagraphStyleFromToolbar(value, emptyList(), emptySet(), EditorTool.Heading2)

        assertTrue(update.marks.isEmpty())
        assertEquals(setOf(VaultInlineStyle.Heading2), update.pendingStyles)
        assertEquals(EditorTool.Heading2, activeVaultParagraphToolForSelection(value, update.marks, update.pendingStyles))
    }
}
