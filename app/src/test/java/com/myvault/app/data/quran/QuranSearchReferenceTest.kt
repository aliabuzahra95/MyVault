package com.myvault.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranSearchReferenceTest {
    @Test
    fun `numeric and labelled references resolve to exact ayah`() {
        assertEquals(2 to 17, parseQuranSearchReference("2:17"))
        assertEquals(2 to 17, parseQuranSearchReference("2 17"))
        assertEquals(2 to 17, parseQuranSearchReference("Surah 2 verse 17"))
        assertEquals(2 to 17, parseQuranSearchReference("Surah 2 ayah 17"))
    }

    @Test
    fun `English and Arabic Surah names resolve without punctuation dependence`() {
        assertEquals(2 to 17, parseQuranSearchReference("Al-Baqarah 17"))
        assertEquals(2 to 17, parseQuranSearchReference("Al Baqarah 17"))
        assertEquals(2 to 17, parseQuranSearchReference("البقرة 17"))
    }

    @Test
    fun `invalid free text is not misread as a reference`() {
        assertNull(parseQuranSearchReference("mercy and patience"))
    }
}
