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
    fun displayAddsRenderOnlyIsolationForEmbeddedRtlAndLtrRuns() {
        val englishFirst = "Al-Kulliyat: The universals / universal — الكليات • concepts."
        val arabicFirst = "استدل العلماء بهذه الآية = \"The scholars used this verse as evidence.\""

        val englishDisplay = buildVaultDisplayText(englishFirst, emptyList(), colors = LightVaultColors)
        val arabicDisplay = buildVaultDisplayText(arabicFirst, emptyList(), colors = LightVaultColors)

        assertEquals(
            "Al-Kulliyat: The universals / universal — \u2067الكليات\u2069 • concepts.",
            englishDisplay.text.text,
        )
        assertEquals(
            "استدل العلماء بهذه الآية = \"\u2066The scholars used this verse as evidence\u2069.\"",
            arabicDisplay.text.text,
        )
        assertTrue(englishDisplay.text.paragraphStyles.isEmpty())
        assertTrue(arabicDisplay.text.paragraphStyles.isEmpty())
    }

    @Test
    fun mixedPunctuationCasesKeepNeutralCharactersOutsideIsolatedRuns() {
        val text = """
            Al-Kulliyat: The universals / universal — الكليات • concepts.
            Innama wujuduhu: Their existence is — إنما وجودها • only / is merely.
            Fi al-adh'han: In the minds / in intellects — في الأذهان.
            La fi al-a'yan: Not in concrete realities — لا في الأعيان • not in external existences.
            The universals / universal: (Al-Kulliyat) الكليات — concepts.
        """.trimIndent()

        val display = buildVaultDisplayAnnotatedString(text, emptyList(), colors = LightVaultColors)

        assertEquals(text, display.text.withoutBidiIsolates())
        assertEquals(text.count { it == '\n' }, display.text.count { it == '\n' })
        assertTrue(display.text.contains("\u2067الكليات\u2069 •"))
        assertTrue(display.text.contains("\u2067إنما وجودها\u2069 •"))
        assertTrue(display.text.contains("\u2067في الأذهان\u2069."))
        assertTrue(display.text.contains("\u2067لا في الأعيان\u2069 •"))
        assertTrue(display.paragraphStyles.isEmpty())
    }

    @Test
    fun bidiOffsetMappingKeepsEditorOffsetsOnOriginalText() {
        val text = "English الكليات text"
        val display = buildVaultDisplayText(text, emptyList(), colors = LightVaultColors)
        val arabicStart = text.indexOf("الكليات")
        val arabicEnd = arabicStart + "الكليات".length

        assertEquals(arabicStart + 1, display.offsetMapping.originalToTransformed(arabicStart))
        assertEquals(arabicEnd + 2, display.offsetMapping.originalToTransformed(arabicEnd))
        for (offset in 0..text.length) {
            val transformed = display.offsetMapping.originalToTransformed(offset)
            assertEquals(offset, display.offsetMapping.transformedToOriginal(transformed))
        }
    }

    @Test
    fun displayIsolationPreservesRichTextOnTheSameVisibleCharacters() {
        val text = "Heading الكليات: mixed title"
        val marks = listOf(
            VaultStyleMark(0, text.length, VaultInlineStyle.Heading2),
            VaultStyleMark(8, 15, VaultInlineStyle.ColorRed),
        )

        val display = buildVaultDisplayText(text, marks, colors = LightVaultColors)

        assertEquals(text, display.text.text.withoutBidiIsolates())
        val headingText = display.text.spanStyles
            .filter { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold }
            .sortedBy { it.start }
            .joinToString(separator = "") { display.text.text.substring(it.start, it.end) }
            .withoutBidiIsolates()
        assertEquals(text, headingText)
        assertTrue(display.text.spanStyles.any { range ->
            range.item.color == androidx.compose.ui.graphics.Color(0xFFE5484D) &&
                display.text.text.substring(range.start, range.end) == "الكليات"
        })
        assertTrue(display.text.paragraphStyles.isEmpty())
    }

    @Test
    fun renderIsolationDoesNotDuplicateParagraphBreaksOrStoredText() {
        val text = """
            First paragraph with العربية.

            الفقرة الثانية with English.
            يا سائلي عن مذهبي وعقيدتي
            رزق الهدى من للهداية يسأل
        """.trimIndent()
        val storage = buildVaultAnnotatedString(text, emptyList(), colors = LightVaultColors)
        val display = buildVaultDisplayText(text, emptyList(), colors = LightVaultColors)

        assertEquals(text, storage.text)
        assertEquals(text, display.text.text.withoutBidiIsolates())
        assertEquals(text.count { it == '\n' }, display.text.text.count { it == '\n' })
        assertTrue(storage.paragraphStyles.isEmpty())
        assertTrue(display.text.paragraphStyles.isEmpty())
    }

    private fun assertNoInjectedParagraphStyles(text: String) =
        buildVaultAnnotatedString(text, emptyList(), colors = LightVaultColors).also { annotated ->
            assertEquals(text, annotated.text)
            assertTrue(annotated.paragraphStyles.isEmpty())
        }

    private fun String.withoutBidiIsolates(): String =
        replace("\u2066", "").replace("\u2067", "").replace("\u2069", "")
}
