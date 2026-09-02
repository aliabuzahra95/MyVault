package com.myvault.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranReflectionParsingTest {
    @Test
    fun explicitReflectionSectionReturnsOnlyUserText() {
        val note = """
            Reflection on Al-Faatiha 1:1

            Source: Al-Faatiha 1:1

            بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ

            In the name of Allah, the Most Compassionate, Most Merciful.

            Reflection:

            My own first paragraph.

            My own second paragraph.
        """.trimIndent()

        assertEquals(
            "My own first paragraph.\n\nMy own second paragraph.",
            note.reflectionBody(arabic = "different text", translation = "different translation"),
        )
    }

    @Test
    fun legacyReflectionIgnoresWhitespaceDifferencesInVerseAndTranslation() {
        val arabic = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
        val translation = "In the name of Allah, the Most Compassionate, Most Merciful."
        val note = """
            Reflection on Al-Faatiha 1:1

            Source: Al-Faatiha 1:1

            بِسْمِ   اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ

            In the name of Allah,
            the Most Compassionate, Most Merciful.

            This is the reflection I wrote.
        """.trimIndent()

        assertEquals(
            "This is the reflection I wrote.",
            note.reflectionBody(arabic = arabic, translation = translation),
        )
    }
}
