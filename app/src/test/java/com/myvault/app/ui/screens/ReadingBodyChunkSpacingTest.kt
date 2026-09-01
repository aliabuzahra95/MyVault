package com.myvault.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingBodyChunkSpacingTest {
    @Test
    fun longNoteChunkingPreservesEveryParagraphBreakExactly() {
        val paragraph = "A deliberately long paragraph with English and العربية content. "
        val text = buildString {
            repeat(90) { index ->
                append("Section ${index + 1}\n")
                append(paragraph.repeat(3))
                append("\n\n")
            }
            append("Final paragraph.")
        }

        val chunks = text.toReadingBodyChunks(emptyList(), emptyList())

        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString(separator = "") { it.text })
        assertEquals(
            text.windowed(2).count { it == "\n\n" },
            chunks.joinToString(separator = "") { it.text }.windowed(2).count { it == "\n\n" },
        )
    }
}
