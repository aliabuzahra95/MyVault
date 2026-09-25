package com.myvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
