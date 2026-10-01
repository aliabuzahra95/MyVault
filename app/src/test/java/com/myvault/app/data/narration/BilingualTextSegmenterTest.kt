package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BilingualTextSegmenterTest {

    private lateinit var segmenter: BilingualTextSegmenter

    @Before
    fun setUp() {
        segmenter = BilingualTextSegmenter()
    }

    @Test
    fun testEmptyAndBlankText() {
        assertTrue(segmenter.segmentText("").isEmpty())
        assertTrue(segmenter.segmentText("   \n\t  ").isEmpty())
    }

    @Test
    fun testEnglishOnly() {
        val text = "This is a purely English theological treatise on classical metaphysics."
        val segments = segmenter.segmentText(text)
        assertEquals(1, segments.size)
        assertEquals(NarrationLanguage.English, segments[0].language)
        assertEquals("en-AU", segments[0].locale)
        assertEquals("en-AU-NatashaNeural", segments[0].voice)
        assertTrue(segments[0].text.contains("theological treatise"))
    }

    @Test
    fun testArabicOnly() {
        val text = "ثم ذكر شيخ الإسلام ابن تيمية رحمه الله هذا الأصل العظيم."
        val segments = segmenter.segmentText(text)
        assertEquals(1, segments.size)
        assertEquals(NarrationLanguage.Arabic, segments[0].language)
        assertEquals("ar-SA", segments[0].locale)
        assertEquals("ar-SA-HamedNeural", segments[0].voice)
        assertTrue(segments[0].text.contains("شيخ الإسلام"))
    }

    @Test
    fun testEnglishToArabicToEnglish_ExplicitReset() {
        val text = """
            Shaykh al-Islam discusses this fundamental principle.
            ثم ذكر شيخ الإسلام أن هذا القول باطل.
            He then explains why this position is incorrect and presents three refutations.
        """.trimIndent()

        val segments = segmenter.segmentText(text)
        assertEquals(3, segments.size)

        // Segment 1: English
        assertEquals(NarrationLanguage.English, segments[0].language)
        assertEquals("en-AU", segments[0].locale)
        assertEquals("en-AU-NatashaNeural", segments[0].voice)
        assertTrue(segments[0].text.contains("fundamental principle"))

        // Segment 2: Arabic
        assertEquals(NarrationLanguage.Arabic, segments[1].language)
        assertEquals("ar-SA", segments[1].locale)
        assertEquals("ar-SA-HamedNeural", segments[1].voice)
        assertTrue(segments[1].text.contains("شيخ الإسلام"))

        // Segment 3: English RESET
        assertEquals(NarrationLanguage.English, segments[2].language)
        assertEquals("en-AU", segments[2].locale)
        assertEquals("en-AU-NatashaNeural", segments[2].voice)
        assertTrue(segments[2].text.contains("three refutations"))
    }

    @Test
    fun testArabicToEnglishToArabic_ExplicitReset() {
        val text = """
            قال الإمام الشافعي رحمه الله في الرسالة:
            Language is the vessel of thought and legal interpretation.
            وهذا يوضح دقة الفهم الأصولي عند الأئمة.
        """.trimIndent()

        val segments = segmenter.segmentText(text)
        assertEquals(3, segments.size)

        // Segment 1: Arabic
        assertEquals(NarrationLanguage.Arabic, segments[0].language)
        assertEquals("ar-SA", segments[0].locale)
        assertEquals("ar-SA-HamedNeural", segments[0].voice)

        // Segment 2: English
        assertEquals(NarrationLanguage.English, segments[1].language)
        assertEquals("en-AU", segments[1].locale)
        assertEquals("en-AU-NatashaNeural", segments[1].voice)

        // Segment 3: Arabic RESET
        assertEquals(NarrationLanguage.Arabic, segments[2].language)
        assertEquals("ar-SA", segments[2].locale)
        assertEquals("ar-SA-HamedNeural", segments[2].voice)
    }

    @Test
    fun testTransliteratedIslamicTerminologyRemainsEnglish() {
        val text = "The hadith was recorded in Sahih al-Bukhari regarding tawhid, aqidah, and fiqh."
        val segments = segmenter.segmentText(text)
        assertEquals(1, segments.size)
        assertEquals(NarrationLanguage.English, segments[0].language)
        assertEquals("en-AU", segments[0].locale)
    }

    @Test
    fun testEmbeddedArabicQuotationWithinEnglishSentence() {
        val text = "The teacher recited the ayah: إِنَّ مَعَ الْعُسْرِ يُسْرًا and asked for its grammatical breakdown."
        val segments = segmenter.segmentText(text)
        assertEquals(3, segments.size)

        assertEquals(NarrationLanguage.English, segments[0].language)
        assertEquals("en-AU", segments[0].locale)
        assertTrue(segments[0].text.contains("recited the ayah"))

        assertEquals(NarrationLanguage.Arabic, segments[1].language)
        assertEquals("ar-SA", segments[1].locale)
        assertTrue(segments[1].text.contains("إِنَّ مَعَ الْعُسْرِ يُسْرًا"))

        // Resets back to English explicitly
        assertEquals(NarrationLanguage.English, segments[2].language)
        assertEquals("en-AU", segments[2].locale)
        assertEquals("en-AU-NatashaNeural", segments[2].voice)
        assertTrue(segments[2].text.contains("grammatical breakdown"))
    }

    @Test
    fun testMultilingualSsmlStructureAndXmlEscaping() {
        val text = """He said <special> & "important": الحمد لله followed by normal text."""
        val ssml = segmenter.buildMultilingualSsml(
            text = text,
            englishVoice = "en-AU-NatashaNeural",
            englishLocale = "en-AU",
            arabicVoice = "ar-SA-HamedNeural",
            arabicLocale = "ar-SA",
        )

        // Valid root speak with xml:lang
        assertTrue(ssml.startsWith("""<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="en-AU">"""))
        assertTrue(ssml.endsWith("</speak>"))

        // Escaping verification
        assertTrue(ssml.contains("&lt;special&gt;"))
        assertTrue(ssml.contains("&amp;"))
        assertTrue(ssml.contains("&quot;important&quot;"))

        // Explicit voice and lang tags on both English and Arabic
        assertTrue(ssml.contains("""<voice name="en-AU-NatashaNeural"><lang xml:lang="en-AU">"""))
        assertTrue(ssml.contains("""<voice name="ar-SA-HamedNeural"><lang xml:lang="ar-SA">الحمد لله</lang></voice>"""))
        // After Arabic, English tag reappears explicitly
        val afterArabic = ssml.substringAfter("الحمد لله</lang></voice>")
        assertTrue(afterArabic.contains("""<voice name="en-AU-NatashaNeural"><lang xml:lang="en-AU">"""))
        assertTrue(afterArabic.contains("followed by normal text"))
    }
}
