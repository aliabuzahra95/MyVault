package com.myvault.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParagraphStripContractTest {
    private val editorSource = File("src/main/java/com/myvault/app/ui/screens/EditorScreen.kt")

    @Test
    fun paragraphStylesUseInlineOverlayInsteadOfModalSheet() {
        val source = editorSource.readText()

        assertTrue(source.contains("listOf(EditorTool.Paragraph, EditorTool.Heading, EditorTool.Heading2, EditorTool.Heading3, EditorTool.Heading4)"))
        assertTrue(source.contains("Modifier.offset { IntOffset(0, -formattingToolbarHeightPx) }"))
        assertTrue(source.contains("applyVaultParagraphStyleFromToolbar"))
        assertFalse(source.contains("title = \"Text style\""))
    }
}
