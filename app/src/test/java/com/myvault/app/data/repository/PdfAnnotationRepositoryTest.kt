package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.PdfAnnotationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationRepositoryTest {
    @Test
    fun sanitizedPdfAnnotationColor_allowsKnownColours() {
        assertEquals("yellow", "yellow".sanitizedPdfAnnotationColor(defaultColor = "black"))
        assertEquals("blue", "BLUE".sanitizedPdfAnnotationColor(defaultColor = "black"))
        assertEquals("green", "green".sanitizedPdfAnnotationColor(defaultColor = "black"))
        assertEquals("red", "red".sanitizedPdfAnnotationColor(defaultColor = "black"))
        assertEquals("black", "black".sanitizedPdfAnnotationColor(defaultColor = "yellow"))
    }

    @Test
    fun sanitizedPdfAnnotationColor_fallsBackForUnknownColours() {
        assertEquals("yellow", "purple".sanitizedPdfAnnotationColor(defaultColor = "yellow"))
        assertEquals("black", "".sanitizedPdfAnnotationColor(defaultColor = "black"))
    }

    @Test
    fun sanitizedPdfTextBoxBackground_allowsKnownBackgrounds() {
        assertEquals(PdfAnnotationEntity.BACKGROUND_NONE, "none".sanitizedPdfTextBoxBackground())
        assertEquals("white", "WHITE".sanitizedPdfTextBoxBackground())
        assertEquals("yellow", "yellow".sanitizedPdfTextBoxBackground())
        assertEquals("blue", "blue".sanitizedPdfTextBoxBackground())
        assertEquals("green", "green".sanitizedPdfTextBoxBackground())
        assertEquals("red", "red".sanitizedPdfTextBoxBackground())
    }

    @Test
    fun sanitizedPdfTextBoxBackground_fallsBackForUnknownBackgrounds() {
        assertEquals(PdfAnnotationEntity.BACKGROUND_NONE, "purple".sanitizedPdfTextBoxBackground())
    }

    @Test
    fun isValidPdfAnnotationRect_rejectsImpossibleRectangles() {
        assertTrue(isValidPdfAnnotationRect(1f, 2f, 10f, 20f))
        assertFalse(isValidPdfAnnotationRect(10f, 2f, 1f, 20f))
        assertFalse(isValidPdfAnnotationRect(1f, 20f, 10f, 2f))
        assertFalse(isValidPdfAnnotationRect(Float.NaN, 2f, 10f, 20f))
        assertFalse(isValidPdfAnnotationRect(1f, Float.POSITIVE_INFINITY, 10f, 20f))
    }
}
