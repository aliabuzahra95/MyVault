package com.myvault.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VaultRichTextStorageRoundTripTest {
    @Test
    fun storageRoundTripPreservesBlankParagraphsMixedTextAndMarksExactly() {
        val text = "Lesson heading\n\nFirst paragraph.\n\nفقرة عربية\nEnglish ending."
        val document = VaultRichTextDocument(
            text = text,
            styleMarks = listOf(
                VaultStyleMark(0, "Lesson heading".length, VaultInlineStyle.Heading),
                VaultStyleMark(text.indexOf("First"), text.indexOf("First") + "First".length, VaultInlineStyle.Bold),
                VaultStyleMark(text.indexOf("فقرة"), text.indexOf("فقرة") + "فقرة عربية".length, VaultInlineStyle.Italic),
            ),
            noteLinks = listOf(
                VaultNoteLink(text.indexOf("English"), text.indexOf("English") + "English".length, "linked-note"),
            ),
        )

        val restored = parseVaultRichTextDocument(document.toStorageJson())

        assertNotNull(restored)
        assertEquals(document.text, restored!!.text)
        assertEquals(document.styleMarks.toSet(), restored.styleMarks.toSet())
        assertEquals(document.noteLinks, restored.noteLinks)
        assertEquals(2, restored.text.windowed(2).count { it == "\n\n" })
    }
}
