package com.myvault.app.ui.quran

import com.myvault.app.data.quran.QuranWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranWordTapContractTest {

    @Test
    fun `character offsets resolve only inside indexed Arabic words`() {
        val words = listOf(
            word(position = 1, text = "بسم", start = 0, end = 3),
            word(position = 2, text = "الله", start = 4, end = 8),
            word(position = 3, text = "الرحمن", start = 9, end = 15),
        )

        assertEquals("1:1:1", words.wordAtCharacterOffset(0)?.wordId)
        assertEquals("1:1:2", words.wordAtCharacterOffset(7)?.wordId)
        assertEquals("1:1:3", words.wordAtCharacterOffset(14)?.wordId)

        assertNull(words.wordAtCharacterOffset(3))
        assertNull(words.wordAtCharacterOffset(8))
        assertNull(words.wordAtCharacterOffset(15))
        assertNull(words.wordAtCharacterOffset(-1))
    }

    private fun word(position: Int, text: String, start: Int, end: Int): QuranWord =
        QuranWord(
            wordId = "1:1:$position",
            surahNumber = 1,
            ayahNumber = 1,
            wordPosition = position,
            arabicText = text,
            normalizedArabicText = text,
            charStart = start,
            charEnd = end,
        )
}
