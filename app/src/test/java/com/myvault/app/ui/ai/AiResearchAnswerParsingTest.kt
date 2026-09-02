package com.myvault.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResearchAnswerParsingTest {
    @Test
    fun turnsParagraphCitationsIntoSeparateSourceReferences() {
        val blocks = parseResearchAnswerBlocks(
            """
                ## Direct answer

                Ibn Taymiyyah held that the touch does not invalidate wudu. [S1][S3]

                **Evidence:** The source reports the relevant narration. [S2]
            """.trimIndent(),
            sourceCount = 3,
        )

        assertEquals(3, blocks.size)
        assertTrue(blocks.first().isHeading)
        assertEquals(listOf(1, 3), blocks[1].sourceNumbers)
        assertFalse(blocks[1].text.contains("[S1]"))
        assertEquals("**Evidence:** The source reports the relevant narration.", blocks[2].text)
    }

    @Test
    fun ignoresCitationNumbersThatHaveNoRetrievedSource() {
        val blocks = parseResearchAnswerBlocks("Supported claim. [S2][S9]", sourceCount = 2)

        assertEquals(listOf(2), blocks.single().sourceNumbers)
    }

    @Test
    fun stylesSimpleBoldAndItalicWithoutShowingMarkdownMarkers() {
        val styled = simpleResearchMarkdown("**Ruling:** renew wudu as *mustahabb*.")

        assertEquals("Ruling: renew wudu as mustahabb.", styled.text)
        assertTrue(styled.spanStyles.isNotEmpty())
    }
}
