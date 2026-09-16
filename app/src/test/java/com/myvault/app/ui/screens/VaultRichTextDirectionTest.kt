package com.myvault.app.ui.screens

import com.myvault.app.ui.theme.LightVaultColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRichTextDirectionTest {
    @Test
    fun englishParagraphDoesNotGainExtraParagraphStyles() {
        assertNoInjectedParagraphStyles("The Necessary Existent is not caused by anything outside Himself.")
    }

    @Test
    fun arabicParagraphDoesNotGainExtraParagraphStyles() {
        assertNoInjectedParagraphStyles("واجب الوجود وصفات الكمال")
    }

    @Test
    fun arabicPoetryKeepsOriginalLineBreaksWithoutExtraParagraphStyles() {
        assertNoInjectedParagraphStyles(
            """
            يا سائلي عن مذهبي وعقيدتي
            رزق الهدى من للهداية يسأل
            """.trimIndent(),
        )
    }

    @Test
    fun mixedArabicFirstParagraphKeepsTheOriginalText() {
        val text = "استدل العلماء بهذه الآية = \"The scholars used this verse as evidence.\""

        val annotated = assertNoInjectedParagraphStyles(text)

        assertEquals(text, annotated.text)
    }

    @Test
    fun mixedEnglishFirstParagraphKeepsTheOriginalText() {
        val text = "Example: استدل العلماء بهذه الآية"

        val annotated = assertNoInjectedParagraphStyles(text)

        assertEquals(text, annotated.text)
    }

    @Test
    fun formattedMixedParagraphKeepsDirectionAndFormattingMetadata() {
        val text = "Arabic واجب الوجود and English"
        val marks = listOf(
            VaultStyleMark(0, 6, VaultInlineStyle.Bold),
            VaultStyleMark(7, 19, VaultInlineStyle.ColorBlue),
            VaultStyleMark(0, text.length, VaultInlineStyle.Heading2),
        )

        val annotated = buildVaultAnnotatedString(text, marks, colors = LightVaultColors)

        assertEquals(text, annotated.text)
        assertTrue(annotated.paragraphStyles.isEmpty())
        assertTrue(annotated.spanStyles.any { it.item.fontWeight != null })
        assertTrue(annotated.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color(0xFF2F80ED) })
    }

    private fun assertNoInjectedParagraphStyles(text: String) =
        buildVaultAnnotatedString(text, emptyList(), colors = LightVaultColors).also { annotated ->
            assertEquals(text, annotated.text)
            assertTrue(annotated.paragraphStyles.isEmpty())
        }
}
