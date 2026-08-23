package com.myvault.app.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormattingOutputEngineTest {
    @Test
    fun retainedActionsContainOnlyTheFourFormattingWorkflows() {
        assertEquals(
            setOf(
                NoteFormattingAction.StructureOnly,
                NoteFormattingAction.IntelligentStructure,
                NoteFormattingAction.CleanFormat,
                NoteFormattingAction.FormatNote,
            ),
            NoteFormattingOutputEngine.retainedActions,
        )
    }

    @Test
    fun structureOnlyRejectsShortenedOutputAndRestoresEverySourceSegment() {
        val original = """
            Types of Water

            Water is pure and purifying when it remains upon its original creation.

            الماء طهور لا ينجسه شيء

            This final sentence must remain exactly as written.
        """.trimIndent()

        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.StructureOnly,
            generated = "<h1>Types of Water</h1><p>Water is pure.</p>",
            originalBody = original,
        )

        assertTrue(output.contains("Water is pure and purifying when it remains upon its original creation."))
        assertTrue(output.contains("الماء طهور لا ينجسه شيء"))
        assertTrue(output.contains("This final sentence must remain exactly as written."))
    }

    @Test
    fun structureOnlyPreservesSafeCompleteHtml() {
        val original = "Purification is required.\n\nالماء طهور"
        val generated = "<h2>Purification</h2><p>Purification is required.</p><blockquote>الماء طهور</blockquote>"

        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.StructureOnly,
            generated = generated,
            originalBody = original,
        )

        assertTrue(output.contains("<h2>Purification</h2>"))
        assertTrue(output.contains("Purification is required."))
        assertTrue(output.contains("الماء طهور"))
    }

    @Test
    fun intelligentStructureReturnsEditorHtmlWithoutMarkdownFence() {
        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.IntelligentStructure,
            generated = "```html\n<h2>Principles</h2><p>First principle.</p>\n```",
            originalBody = "First principle.",
        )

        assertTrue(output.contains("<h2>Principles</h2>"))
        assertTrue(output.contains("First principle."))
        assertFalse(output.contains("```"))
    }

    @Test
    fun intelligentStructureRejectsSummariesAndRestoresTheCompleteOriginal() {
        val original = """
            The first explanation must remain exactly as written.

            This supporting example is intentionally detailed and must not be summarised.

            الدليل يبقى كما هو دون حذف
        """.trimIndent()

        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.IntelligentStructure,
            generated = "<h2>Summary</h2><p>The explanation has supporting evidence.</p>",
            originalBody = original,
        )

        assertTrue(output.contains("The first explanation must remain exactly as written."))
        assertTrue(output.contains("This supporting example is intentionally detailed and must not be summarised."))
        assertTrue(output.contains("الدليل يبقى كما هو دون حذف"))
    }

    @Test
    fun intelligentStructureRejectsOneMissingRepeatedWordOccurrence() {
        val original = "Every word matters, and word repetition matters too."
        val generated = "<h2>Preservation</h2><p>Every word matters, and repetition matters too.</p>"

        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.IntelligentStructure,
            generated = generated,
            originalBody = original,
        )

        assertTrue(output.contains("Every word matters, and word repetition matters too."))
        assertFalse(output.contains("<h2>Preservation</h2>"))
    }

    @Test
    fun intelligentStructureKeepsSafeAdditiveHeadings() {
        val original = "Purification is required before prayer."
        val generated = "<h2>Foundational Rule</h2><p>Purification is required before prayer.</p>"

        val output = NoteFormattingOutputEngine.prepareOutput(
            action = NoteFormattingAction.IntelligentStructure,
            generated = generated,
            originalBody = original,
        )

        assertTrue(output.contains("<h2>Foundational Rule</h2>"))
        assertTrue(output.contains(original))
    }

    @Test
    fun longNotesAreSplitIntoBoundedFormattingChunksWithoutLosingEndpoints() {
        val first = "Opening principle الماء طهور."
        val middle = List(900) { index -> "Paragraph $index explains a retained point in the lesson." }
            .joinToString("\n\n")
        val last = "Final principle must remain present."
        val chunks = NoteFormattingOutputEngine.chunkSource("$first\n\n$middle\n\n$last")

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 12_000 })
        assertTrue(chunks.first().contains(first))
        assertTrue(chunks.last().contains(last))
    }

    @Test
    fun everyFormattingProviderReceivesTheEditorHtmlPromptContract() {
        NoteFormattingProvider.entries.forEach { provider ->
            NoteFormattingAction.entries.forEach { action ->
                val prompt = NoteFormattingPromptBuilder.build(
                    NoteFormattingRequest(
                        action = action,
                        provider = provider,
                        model = NoteFormattingModel.Fast,
                        title = "Purification",
                        body = "The original note text.",
                    ),
                )

                assertTrue(prompt.systemInstruction.contains("HTML", ignoreCase = true))
                assertTrue(prompt.prompt.contains("Output type: EditorOutputHtml"))
                assertTrue(prompt.prompt.contains("The original note text."))
                assertFalse(prompt.prompt.contains("Study Tutor"))
                if (action == NoteFormattingAction.IntelligentStructure) {
                    assertTrue(prompt.systemInstruction.contains("Every original word"))
                    assertTrue(prompt.prompt.contains("Absolute lossless rule"))
                }
            }
        }
    }
}
