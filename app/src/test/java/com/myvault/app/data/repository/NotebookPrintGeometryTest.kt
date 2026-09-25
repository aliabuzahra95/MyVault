package com.myvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookPrintGeometryTest {
    @Test fun a4AndBothIndependentA5SlotsUseExactMillimetreGeometry() {
        val config = NotebookExportConfig()
        assertEquals(297f, NotebookExportConfig.A4_WIDTH_MM, 0f)
        assertEquals(210f, NotebookExportConfig.A4_HEIGHT_MM, 0f)
        assertEquals(148.5f, NotebookExportConfig.SLOT_WIDTH_MM, 0f)
        assertEquals(133.5f, config.contentWidthMm, 0f)
        assertEquals(195f, config.contentHeightMm, 0f)
        assertEquals(0f, config.slot(0).leftMm, 0f)
        assertEquals(5f, config.slot(0).contentLeftMm, 0f)
        assertEquals(138.5f, config.slot(0).contentRightMm, 0f)
        assertEquals(148.5f, config.slot(1).leftMm, 0f)
        assertEquals(153.5f, config.slot(1).contentLeftMm, 0f)
        assertEquals(287f, config.slot(1).contentRightMm, 0f)
        assertEquals(72f, mmToPdfPoints(25.4f), 0.001f)
    }

    @Test fun continuousPagesPairInReadingOrderAndOddLastPageStaysLeft() {
        assertEquals(listOf(0 to 1, 2 to 3, 4 to 5), notebookSheetPairs(6))
        val odd = notebookSheetPairs(5)
        assertEquals(listOf(0 to 1, 2 to 3, 4 to null), odd)
        assertNull(odd.last().second)
    }

    @Test fun foldInsetsRemainAdjustableWithoutMirroringTheRightSlot() {
        val config = NotebookExportConfig(topInsetMm = 8f, rightInsetMm = 11f, leftMarginMm = 6f)
        assertEquals(131.5f, config.contentWidthMm, 0f)
        assertEquals(197f, config.contentHeightMm, 0f)
        assertEquals(137.5f, config.slot(0).contentRightMm, 0f)
        assertEquals(286f, config.slot(1).contentRightMm, 0f)
    }

    @Test fun densityPresetsReduceTypeAndSpacingWithoutChangingPhysicalGeometry() {
        val comfortable = NotebookExportConfig(density = NotebookPrintDensity.Comfortable)
        val compact = NotebookExportConfig(density = NotebookPrintDensity.Compact)
        val dense = NotebookExportConfig(density = NotebookPrintDensity.Dense)
        assertEquals(13f, comfortable.typography().bodySizePt, 0f)
        assertEquals(11.5f, compact.typography().bodySizePt, 0f)
        assertEquals(10f, dense.typography().bodySizePt, 0f)
        assertTrue(comfortable.typography().paragraphSpacingPt > compact.typography().paragraphSpacingPt)
        assertTrue(compact.typography().paragraphSpacingPt > dense.typography().paragraphSpacingPt)
        assertTrue(comfortable.typography().lineExtraPt > compact.typography().lineExtraPt)
        assertTrue(compact.typography().lineExtraPt > dense.typography().lineExtraPt)
        assertEquals(comfortable.slot(0), dense.slot(0))
        assertEquals(comfortable.slot(1), dense.slot(1))
    }

    @Test fun manualBodySizeKeepsPresetSpacingAndProportionalHeadingHierarchy() {
        val auto = NotebookExportConfig(density = NotebookPrintDensity.Compact).typography()
        val manual = NotebookExportConfig(density = NotebookPrintDensity.Compact, bodyFontSizePt = 10).typography()
        assertEquals(10f, manual.bodySizePt, 0f)
        assertEquals(auto.paragraphSpacingPt, manual.paragraphSpacingPt, 0f)
        assertEquals(auto.headingSpacingPt, manual.headingSpacingPt, 0f)
        assertTrue(manual.headingSizeFor(1, "Heading") > manual.headingSizeFor(2, "Heading"))
        assertTrue(manual.headingSizeFor(2, "Heading") > manual.headingSizeFor(3, "Heading"))
        assertTrue(manual.headingSizeFor(3, "Heading") > manual.headingSizeFor(4, "Heading"))
        assertTrue(manual.headingSizeFor(4, "Heading") > manual.bodySizePt)
        assertThrows(IllegalArgumentException::class.java) { NotebookExportConfig(bodyFontSizePt = 8) }
    }

    @Test fun denseArabicAndMixedParagraphsKeepAReadableMinimum() {
        val type = NotebookExportConfig(density = NotebookPrintDensity.Dense, bodyFontSizePt = 9).typography()
        assertEquals(9f, type.bodySizeFor("English"), 0f)
        assertEquals(11f, type.bodySizeFor("العربية بِالتَّشْكِيل"), 0f)
        assertEquals(11f, type.bodySizeFor("English العربية"), 0f)
    }

    @Test fun savedNotebookSettingsKeepOldTypographyWhileNewSettingsStartCompact() {
        assertEquals(NotebookPrintDensity.Comfortable, resolveNotebookDensity(null, hasExistingSettings = true))
        assertEquals(NotebookPrintDensity.Compact, resolveNotebookDensity(null, hasExistingSettings = false))
        assertEquals(NotebookPrintDensity.Dense, resolveNotebookDensity("Dense", hasExistingSettings = true))
    }
}
