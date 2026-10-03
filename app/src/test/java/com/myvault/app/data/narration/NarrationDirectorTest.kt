package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NarrationDirectorTest {

    private lateinit var director: NarrationDirector

    @Before
    fun setUp() {
        director = NarrationDirector(BilingualTextSegmenter())
    }

    @Test
    fun testTitleExtraction() {
        val plan = director.createPlan(
            sourceId = "note-1",
            title = "Introduction to Usul al-Fiqh",
            rawContent = "This is the first introductory sentence.",
        )

        assertEquals("note-1", plan.sourceId)
        assertEquals("Introduction to Usul al-Fiqh", plan.title)
        assertTrue(plan.units.isNotEmpty())

        val titleUnit = plan.units.first()
        assertEquals(NarrationUnitType.Title, titleUnit.type)
        assertEquals("Introduction to Usul al-Fiqh", titleUnit.spokenText)
        assertEquals(700L, titleUnit.pauseAfterMs)
        assertEquals(1.25f, titleUnit.emphasisLevel, 0.01f)
    }

    @Test
    fun testHeadingsAndSubheadingsPacing() {
        val content = """
            # Primary Principles of Jurisprudence
            Here is a paragraph describing the primary principles.
            ### Historical Context
            This subheading introduces the classical era.
        """.trimIndent()

        val plan = director.createPlan("note-2", "Principles", content)
        val units = plan.units.drop(1) // Drop title

        val headingUnit = units.first { it.type == NarrationUnitType.Heading }
        assertEquals("Primary Principles of Jurisprudence", headingUnit.spokenText)
        assertEquals(600L, headingUnit.pauseBeforeMs)
        assertEquals(700L, headingUnit.pauseAfterMs)
        assertEquals(1.2f, headingUnit.emphasisLevel, 0.01f)

        val subheadingUnit = units.first { it.type == NarrationUnitType.Subheading }
        assertEquals("Historical Context", subheadingUnit.spokenText)
        assertEquals(400L, subheadingUnit.pauseBeforeMs)
        assertEquals(500L, subheadingUnit.pauseAfterMs)
        assertEquals(1.1f, subheadingUnit.emphasisLevel, 0.01f)
    }

    @Test
    fun testListItemsAndBlockQuotes() {
        val content = """
            The scholar enumerated three conditions:
            - Knowledge of the core text
            - Comprehensive memory
            - Sound deduction
            > Knowledge without action is like a tree without fruit.
        """.trimIndent()

        val plan = director.createPlan("note-3", "Conditions", content)
        val listUnits = plan.units.filter { it.type == NarrationUnitType.ListItem }
        assertEquals(3, listUnits.size)
        assertEquals("Knowledge of the core text", listUnits[0].spokenText)
        assertEquals(250L, listUnits[0].pauseBeforeMs)
        assertEquals(350L, listUnits[0].pauseAfterMs)

        val quoteUnit = plan.units.first { it.type == NarrationUnitType.BlockQuote }
        assertTrue(quoteUnit.spokenText.contains("Knowledge without action"))
        assertEquals(400L, quoteUnit.pauseBeforeMs)
        assertEquals(400L, quoteUnit.pauseAfterMs)
    }

    @Test
    fun testArabicQuotesAndBreathingRoom() {
        val content = """
            The author then cites the classical statement:
            من حفظ الأصول حاز الفنون
            Whoever memorizes the fundamentals masters the branches of science.
        """.trimIndent()

        val plan = director.createPlan("note-4", "Quotes", content)
        val arabicUnit = plan.units.first { it.language == "ar" }
        assertTrue(arabicUnit.spokenText.contains("من حفظ الأصول"))
        assertEquals(500L, arabicUnit.pauseBeforeMs)
        assertEquals(500L, arabicUnit.pauseAfterMs)
    }

    @Test
    fun testTransliteratedIslamicTerminologyRemainsEnglish() {
        val content = """
            In this chapter on tawhid and aqidah, Imam al-Ghazali and Shaykh al-Islam Ibn Taymiyyah
            delineate the methodology of fiqh and hadith sciences with utmost rigor.
        """.trimIndent()

        val plan = director.createPlan("note-5", "Terminology", content)
        val paragraphUnit = plan.units.first { it.type == NarrationUnitType.Paragraph }
        assertEquals("en", paragraphUnit.language)
        assertTrue(paragraphUnit.spokenText.contains("tawhid and aqidah"))
        assertTrue(paragraphUnit.spokenText.contains("Ibn Taymiyyah"))
    }

    @Test
    fun testZeroParaphrasingFaithfulness() {
        val raw = """
            ## Chapter 1
            A faithful narration must preserve every substantive word verbatim.
            1. No omission
            2. No unauthorized summarization
            3. Pure text fidelity
        """.trimIndent()

        val plan = director.createPlan("note-6", "Fidelity", raw)
        val fullText = plan.fullSpokenText

        assertTrue(fullText.contains("Fidelity"))
        assertTrue(fullText.contains("Chapter 1"))
        assertTrue(fullText.contains("A faithful narration must preserve every substantive word verbatim."))
        assertTrue(fullText.contains("No omission"))
        assertTrue(fullText.contains("No unauthorized summarization"))
        assertTrue(fullText.contains("Pure text fidelity"))
    }

    @Test
    fun testCanonicalChunksAndWordEstimatesDoNotDependOnPlanUnitIds() {
        val text = (1..100).joinToString(" ") { "word" }
        val first = director.planToChunks(director.createPlan("word-count", "", text))
        val rebuilt = director.planToChunks(director.createPlan("word-count", "", text))
        assertEquals(40_000L, first.sumOf { it.estimatedDurationMs })
        assertEquals(first, rebuilt)
    }

    @Test
    fun testChunkingAlongUnitBoundaries() {
        val content = buildString {
            append("## Overview\n")
            append("Short introduction.\n\n")
            append("### Section 1\n")
            append("A very concise point.\n\n")
            append("### Section 2\n")
            append("Another concise point.\n")
        }

        val plan = director.createPlan("note-7", "Structure", content)
        val chunks = director.planToChunks(plan, maxCharsPerChunk = 80)

        assertTrue(chunks.size >= 2)
        // Verify no chunk starts with partial sentence or broken words
        chunks.forEach { chunk ->
            assertFalse(chunk.text.startsWith("."))
            assertFalse(chunk.text.startsWith(" "))
        }
    }
}
