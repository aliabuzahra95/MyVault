package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class AzureNarrationSsmlTest {
    private val segmenter = BilingualTextSegmenter()
    private val director = NarrationDirector(segmenter)

    private fun ssml(units: List<NarrationUnit>) = AzureNarrationSsml.build(
        units, segmenter, "en-AU-NatashaNeural", "ar-SA-HamedNeural")

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(InputSource(StringReader(xml)))

    @Test fun representativeLectureUsesDirectorStructureAndModestRate() {
        val plan = director.createPlan("disposable", "Lecture title", """
            # First heading

            An English paragraph. Its punctuation remains natural.

            الحمد لله رب العالمين.

            English resumes after the quotation.

            ### Details
            - First point
            - Second point
        """.trimIndent())
        val xml = ssml(plan.units)
        val document = parse(xml)
        assertEquals("speak", document.documentElement.localName)
        assertEquals(listOf(NarrationUnitType.Title, NarrationUnitType.Heading,
            NarrationUnitType.Paragraph, NarrationUnitType.ArabicQuote,
            NarrationUnitType.Paragraph, NarrationUnitType.Subheading,
            NarrationUnitType.ListItem, NarrationUnitType.ListItem), plan.units.map { it.type })
        val prosody = document.getElementsByTagName("prosody")
        for (index in 0 until prosody.length) {
            assertEquals("-8%", (prosody.item(index) as Element).getAttribute("rate"))
        }
        for (pause in listOf(700, 500, 350)) assertTrue(xml.contains("time=\"${pause}ms\""))
        assertTrue(document.documentElement.textContent.contains("الحمد لله رب العالمين."))
        assertFalse(document.documentElement.textContent.contains("#"))
    }

    @Test fun adjacentPausesAreCombinedRatherThanStacked() {
        val units = director.createPlan("disposable", "", "# Heading\n\nParagraph one.\n\nParagraph two.").units
        val document = parse(ssml(units))
        val breaks = document.getElementsByTagName("break")
        val durations = (0 until breaks.length).map { (breaks.item(it) as Element).getAttribute("time") }
        assertEquals(listOf("100ms", "700ms", "400ms", "400ms"), durations)
        assertFalse(durations.any { it.removeSuffix("ms").toLong() > 1_000 })
    }

    @Test fun englishArabicEnglishExplicitlyResetsVoiceAndLanguage() {
        val plan = director.createPlan("disposable", "", "English introduction. الحمد لله. English resumes.")
        val document = parse(ssml(plan.units))
        val voices = document.getElementsByTagName("voice")
        assertEquals(listOf("en-AU-NatashaNeural", "ar-SA-HamedNeural", "en-AU-NatashaNeural"),
            (0 until voices.length).map { (voices.item(it) as Element).getAttribute("name") })
        val languages = document.getElementsByTagName("lang")
        assertEquals(listOf("en-AU", "ar-SA", "en-AU"),
            (0 until languages.length).map { (languages.item(it) as Element).getAttribute("xml:lang") })
        assertEquals(2, Regex("time=\"500ms\"").findAll(ssml(plan.units)).count())
    }

    @Test fun chunksRetainStructureWithoutRepeatingContinuationPauses() {
        val units = listOf(NarrationUnit("u", NarrationUnitType.Heading,
            "Heading text", "Heading text", pauseBeforeMs = 600, pauseAfterMs = 700))
        val first = AzureNarrationSsml.chunkUnits(units, 0, 7).single()
        val continuation = AzureNarrationSsml.chunkUnits(units, 8, 12).single()
        assertEquals("Heading", first.spokenText)
        assertEquals(600L, first.pauseBeforeMs)
        assertEquals(0L, first.pauseAfterMs)
        assertEquals("text", continuation.spokenText)
        assertEquals(0L, continuation.pauseBeforeMs)
        assertEquals(700L, continuation.pauseAfterMs)
        assertEquals(NarrationUnitType.Heading, continuation.type)
    }

    @Test fun paragraphListAndQuotationPacingUsesExistingMetadata() {
        val plan = director.createPlan("disposable", "", "Paragraph.\n\n> Quotation.\n\n- Item\n\n### Subheading")
        val units = plan.units.associateBy { it.type }
        assertEquals(400L, units[NarrationUnitType.Paragraph]?.pauseAfterMs)
        assertEquals(350L, units[NarrationUnitType.ListItem]?.pauseAfterMs)
        assertEquals(400L, units[NarrationUnitType.BlockQuote]?.pauseBeforeMs)
        assertEquals(400L, units[NarrationUnitType.BlockQuote]?.pauseAfterMs)
        assertEquals(500L, units[NarrationUnitType.Subheading]?.pauseAfterMs)
        parse(ssml(plan.units))
    }

    @Test fun ssmlEscapesTextAndVoiceAttributes() {
        val unit = NarrationUnit("u", NarrationUnitType.Paragraph,
            "A & B < C", "A & B < C")
        val xml = AzureNarrationSsml.build(listOf(unit), segmenter, "voice\"&", "ar-SA-HamedNeural")
        val document = parse(xml)
        assertEquals("A & B < C", document.documentElement.textContent)
        assertEquals("voice\"&", (document.getElementsByTagName("voice").item(0) as Element).getAttribute("name"))
    }

    @Test fun ssmlIdentityIsDeterministicAndChangesWithStructuralPacing() {
        val first = director.createPlan("same", "Title", "# Heading\n\nParagraph.")
        val second = director.createPlan("same", "Title", "# Heading\n\nParagraph.")
        assertEquals(ssml(first.units), ssml(second.units))
        val sameWordsDifferentPacing = first.units.map { it.copy(pauseAfterMs = 200) }
        assertNotEquals(ssml(first.units), ssml(sameWordsDifferentPacing))
        assertTrue(AzureNarrationSsml.CacheVersion.contains("rate-minus8"))
    }
}
