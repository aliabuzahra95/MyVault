package com.myvault.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReadingPositionTest {
    @Test fun `preceding sliver cannot replace centred restored page`() {
        val pages = listOf(VisibleReadingPage(18, -700f, 180f), VisibleReadingPage(19, 200f, 1100f),
            VisibleReadingPage(20, 1120f, 2020f))
        repeat(5) { assertEquals(19, selectPdfReadingPage(pages, 1300f, 18)) }
    }

    @Test fun `scroll advances when next page becomes the primary content`() {
        assertEquals(20, selectPdfReadingPage(listOf(VisibleReadingPage(19, -750f, 150f),
            VisibleReadingPage(20, 170f, 1070f)), 1000f, 19))
    }

    @Test fun `zoomed page covering viewport stays selected`() {
        assertEquals(254, selectPdfReadingPage(listOf(VisibleReadingPage(254, -500f, 2500f)), 1000f, 254))
    }

    @Test fun `equally visible pages prefer viewport centre`() {
        assertEquals(1, selectPdfReadingPage(listOf(VisibleReadingPage(0, 0f, 200f),
            VisibleReadingPage(1, 220f, 420f), VisibleReadingPage(2, 440f, 640f)), 640f, 0))
    }

    @Test fun `missing or invalid layout uses safe caller fallback`() {
        assertEquals(0, selectPdfReadingPage(emptyList(), 0f, 0))
        assertEquals(823, selectPdfReadingPage(listOf(VisibleReadingPage(3, Float.NaN, 500f)), 1000f, 823))
    }
}
