package com.myvault.app.ui.screens

import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationPreviewRendererTest {
    @Test
    fun multiRectanglePlanUsesOrderedSegmentsWithoutChangingGeometry() {
        val segments = listOf(
            segment(0, 120f, 210f, 410f, 250f),
            segment(1, 90f, 260f, 460f, 305f),
        )

        val plan = requireNotNull(buildPdfPreviewPlan(600, 900, segments))

        assertEquals(0, plan.pageIndex)
        assertEquals(2, plan.segments.size)
        assertTrue(plan.crop.left <= 90f)
        assertTrue(plan.crop.right >= 460f)
        assertFalse(plan.partial)
        assertEquals(120f, segments.first().left)
    }

    @Test
    fun tallRegionIsBoundedAndMarkedAsPartial() {
        val plan = requireNotNull(
            buildPdfPreviewPlan(
                pageWidth = 600,
                pageHeight = 900,
                segments = listOf(segment(0, 60f, 80f, 540f, 820f)),
                maxHeightPx = 240,
            ),
        )

        assertEquals(240, plan.outputHeight)
        assertTrue(plan.partial)
    }

    @Test
    fun cacheIdentityChangesForColourGeometryAndFileVersion() {
        val file = File("preview-fixture.pdf")
        val annotation = annotation()
        val original = listOf(segment(0, 10f, 20f, 100f, 40f))
        val changed = listOf(segment(0, 10f, 20f, 140f, 40f))

        val baseKey = PdfAnnotationPreviewRenderer.cacheKey(file, annotation, original)
        assertNotEquals(baseKey, PdfAnnotationPreviewRenderer.cacheKey(file, annotation.copy(color = "blue"), original))
        assertNotEquals(baseKey, PdfAnnotationPreviewRenderer.cacheKey(file, annotation, changed))
        assertNotEquals(baseKey, PdfAnnotationPreviewRenderer.cacheKey(file, annotation.copy(updatedAt = 3), original))
    }

    private fun segment(order: Int, left: Float, top: Float, right: Float, bottom: Float) =
        PdfAnnotationSegmentEntity("annotation-1", order, 0, left, top, right, bottom)

    private fun annotation() = PdfAnnotationEntity(
        id = "annotation-1",
        attachmentId = "pdf-1",
        libraryFolderId = null,
        pageIndex = 0,
        left = 10f,
        top = 20f,
        right = 100f,
        bottom = 40f,
        color = "yellow",
        noteText = null,
        createdAt = 1,
        updatedAt = 2,
    )
}
