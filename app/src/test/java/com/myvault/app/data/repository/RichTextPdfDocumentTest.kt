package com.myvault.app.data.repository

import com.myvault.app.ui.screens.VaultInlineStyle
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.VaultStyleMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextPdfDocumentTest {
    @Test fun overlappingInlineStylesRemainCombinedWithoutChangingText() {
        val text = "Heading\nred bold underlined, then plain"
        val start = text.indexOf("red")
        val end = start + "red bold underlined".length
        val source = VaultRichTextDocument(text, listOf(
            VaultStyleMark(0, 7, VaultInlineStyle.Heading),
            VaultStyleMark(start, end, VaultInlineStyle.Bold),
            VaultStyleMark(start, end, VaultInlineStyle.Underline),
            VaultStyleMark(start, end, VaultInlineStyle.ColorRed),
        ))
        val paragraphs = RichTextPdfDocument.paragraphs(source)
        assertEquals(text, paragraphs.joinToString("\n") { it.text })
        assertEquals(setOf(VaultInlineStyle.Heading), paragraphs[0].runs.single().styles)
        assertEquals(
            setOf(VaultInlineStyle.Bold, VaultInlineStyle.Underline, VaultInlineStyle.ColorRed),
            paragraphs[1].runs.first().styles,
        )
        assertTrue(paragraphs[1].runs.last().styles.isEmpty())
    }

    @Test fun numberedAndBulletedParagraphsKeepTheirRealTextAndIndent() {
        val paragraphs = RichTextPdfDocument.paragraphs(VaultRichTextDocument("\u2022 item\n12. item\nnormal", emptyList()))
        assertEquals(listOf(2, 4, 0), paragraphs.map { it.listPrefixLength })
        assertEquals("\u2022 item\n12. item\nnormal", paragraphs.joinToString("\n") { it.text })
    }

    @Test fun arabicAndMixedTextPreserveDirectionAndDiacritics() {
        val text = "بِسْمِ اللهِ الرَّحْمَٰنِ\nEnglish ثم Arabic\n\nعربي English"
        val paragraphs = RichTextPdfDocument.paragraphs(VaultRichTextDocument(text, emptyList()))
        assertEquals(text, paragraphs.joinToString("\n") { it.text })
        assertTrue(paragraphs[0].rightToLeft)
        assertFalse(paragraphs[1].rightToLeft)
        assertTrue(paragraphs[3].rightToLeft)
    }

    @Test fun largeDocumentKeepsEveryParagraphAndOriginalOrder() {
        val text = (1..1200).joinToString("\n\n") { "Paragraph $it. Exact source wording." }
        val paragraphs = RichTextPdfDocument.paragraphs(VaultRichTextDocument(text, emptyList()))
        assertEquals(text, paragraphs.joinToString("\n") { it.text })
        assertEquals("Paragraph 1200. Exact source wording.", paragraphs.last().text)
    }
}
