package com.myvault.app.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LongNoteStructurePipelineTest {
    @Test
    fun plannerUsesStableIdsSemanticLinesAndBoundedChunks() {
        val source = List(60) { index -> "Heading or paragraph $index ${"detail ".repeat(35)}" }.joinToString("\n")
        val chunks = LongNoteStructurePipeline.plan(source, maxChunkCharacters = 1_500)
        val blocks = chunks.flatMap { it.blocks }

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.characterCount <= 1_500 })
        assertEquals("b0001", blocks.first().id)
        assertEquals(blocks.size, blocks.map { it.id }.distinct().size)
        assertEquals(source.lines().map { it.trim() }, blocks.map { it.text })
    }

    @Test
    fun parserAcceptsHarmlessFenceBomAndWrapperNoise() {
        val chunk = NoteStructureChunk(
            listOf(
                NoteSourceBlock("b0001", "Heading"),
                NoteSourceBlock("b0002", "Body text."),
            ),
        )
        val response = "\uFEFF Here is the result:\n```json\n{\"blocks\":[{\"id\":\"b0001\",\"style\":\"h2\"},{\"id\":\"b0002\",\"style\":\"p\"}]}\n```"

        val operations = LongNoteStructurePipeline.parseOperations(response, chunk)

        assertEquals(listOf(NoteStructureStyle.Heading2, NoteStructureStyle.Paragraph), operations.map { it.style })
    }

    @Test
    fun parserRejectsUnknownDuplicateMissingAndOutOfOrderBlocks() {
        val chunk = NoteStructureChunk(listOf(NoteSourceBlock("b0001", "One"), NoteSourceBlock("b0002", "Two")))
        val invalid = listOf(
            "{\"blocks\":[{\"id\":\"wrong\",\"style\":\"p\"},{\"id\":\"b0002\",\"style\":\"p\"}]}",
            "{\"blocks\":[{\"id\":\"b0001\",\"style\":\"p\"},{\"id\":\"b0001\",\"style\":\"p\"}]}",
            "{\"blocks\":[{\"id\":\"b0001\",\"style\":\"p\"}]}",
            "{\"blocks\":[{\"id\":\"b0002\",\"style\":\"p\"},{\"id\":\"b0001\",\"style\":\"p\"}]}",
        )

        invalid.forEach { response ->
            assertThrows(NoteFormattingException::class.java) {
                LongNoteStructurePipeline.parseOperations(response, chunk)
            }
        }
    }

    @Test
    fun localRendererPreservesEnglishArabicDiacriticsPunctuationAndOrder() {
        val source = """
            Purification & intention
            قَالَ: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ».
            - First exact point
            - Second exact point
            1. Numbered premise
            2. Exact conclusion
        """.trimIndent()
        val blocks = LongNoteStructurePipeline.plan(source).single().blocks
        val styles = listOf(
            NoteStructureStyle.Heading1,
            NoteStructureStyle.Quote,
            NoteStructureStyle.Bullet,
            NoteStructureStyle.Bullet,
            NoteStructureStyle.Numbered,
            NoteStructureStyle.Numbered,
        )
        val html = LongNoteStructurePipeline.render(blocks.zip(styles) { block, style -> NoteStructureOperation(block, style) })

        FormattingTextContract.requirePreserved(source, html)
        assertTrue(html.contains("إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<ol>"))
    }

    @Test
    fun realisticLongNoteSizesRenderLosslessly() {
        listOf(2_000, 5_000, 10_000, 22_000).forEach { targetWords ->
            val paragraphCount = (targetWords + 9) / 10
            val source = List(paragraphCount) { index ->
                "Point $index stays exact. الدَّلِيلُ $index يَبْقَى كَمَا هُوَ تماماً."
            }.joinToString("\n")
            val chunks = LongNoteStructurePipeline.plan(source)
            val operations = chunks.flatMap { chunk ->
                chunk.blocks.map { block -> NoteStructureOperation(block, NoteStructureStyle.Paragraph) }
            }

            FormattingTextContract.requirePreserved(source, LongNoteStructurePipeline.render(operations))
            assertTrue(chunks.all { it.characterCount <= LongNoteStructurePipeline.MaxChunkCharacters })
        }
    }

    @Test
    fun adaptiveSplitPreservesEverySourceCharacter() {
        val text = "Evidence ${"word ".repeat(900)} conclusion."
        val original = NoteStructureChunk(listOf(NoteSourceBlock("b0042", text)))
        val children = LongNoteStructurePipeline.splitForRetry(original)

        assertEquals(2, children.size)
        assertEquals(text, children.flatMap { it.blocks }.joinToString(" ") { it.text })
        assertEquals(listOf("b0042a", "b0042b"), children.flatMap { it.blocks }.map { it.id })
    }
}
