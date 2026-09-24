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

    @Test
    fun firstStrongCharacterControlsParagraphDirection() {
        assertEquals(
            VaultParagraphDirection.Ltr,
            resolveVaultParagraphDirection("The universals / universal: (Al-Kulliyat) الكليات - concepts."),
        )
        assertEquals(
            VaultParagraphDirection.Rtl,
            resolveVaultParagraphDirection("الكليات (Al-Kulliyat): universal concepts."),
        )
        assertEquals(VaultParagraphDirection.Ltr, resolveVaultParagraphDirection("... () [] / -"))
    }

    @Test
    fun displayAnnotatedStringAddsRenderOnlyParagraphDirections() {
        val text = """
            The universals / universal: (Al-Kulliyat) الكليات - concepts.
            الكليات (Al-Kulliyat): universal concepts.
        """.trimIndent()

        val storage = buildVaultAnnotatedString(text, emptyList(), colors = LightVaultColors)
        val display = buildVaultDisplayAnnotatedString(text, emptyList(), colors = LightVaultColors)

        assertEquals(text, storage.text)
        assertEquals(text, display.text)
        assertTrue(storage.paragraphStyles.isEmpty())
        assertEquals(2, display.paragraphStyles.size)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Ltr, display.paragraphStyles[0].item.textDirection)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Rtl, display.paragraphStyles[1].item.textDirection)
    }

    @Test
    fun mixedPunctuationExamplesKeepTextAndDirectionRangesStable() {
        val text = """
            The universals / universal: (Al-Kulliyat) الكليات - concepts.
            Their existence is only / merely: (Innama wujuduhu) إنما وجودها.
            Not in concrete realities: (La fi al-a'yan) لا في الأعيان - not in external existences.
            قال العلماء: "universal / concrete" (concepts).
        """.trimIndent()

        val display = buildVaultDisplayAnnotatedString(text, emptyList(), colors = LightVaultColors)

        assertEquals(text, display.text)
        assertEquals(4, display.paragraphStyles.size)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Ltr, display.paragraphStyles[0].item.textDirection)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Ltr, display.paragraphStyles[1].item.textDirection)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Ltr, display.paragraphStyles[2].item.textDirection)
        assertEquals(androidx.compose.ui.text.style.TextDirection.Rtl, display.paragraphStyles[3].item.textDirection)
    }

    @Test
    fun displayDirectionDoesNotShiftRichTextMarks() {
        val text = "Heading الكليات: mixed title"
        val marks = listOf(
            VaultStyleMark(0, text.length, VaultInlineStyle.Heading2),
            VaultStyleMark(8, 15, VaultInlineStyle.ColorRed),
        )

        val display = buildVaultDisplayAnnotatedString(text, marks, colors = LightVaultColors)

        assertEquals(text, display.text)
        assertTrue(display.spanStyles.any { it.start == 0 && it.end == text.length })
        assertTrue(display.spanStyles.any { it.start == 8 && it.end == 15 })
        assertEquals(androidx.compose.ui.text.style.TextDirection.Ltr, display.paragraphStyles.single().item.textDirection)
    }

    private fun assertNoInjectedParagraphStyles(text: String) =
        buildVaultAnnotatedString(text, emptyList(), colors = LightVaultColors).also { annotated ->
            assertEquals(text, annotated.text)
            assertTrue(annotated.paragraphStyles.isEmpty())
        }
}
