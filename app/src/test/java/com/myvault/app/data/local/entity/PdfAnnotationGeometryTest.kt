package com.myvault.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationGeometryTest {
    private val parent = PdfAnnotationEntity(
        id = "annotation-1",
        attachmentId = "attachment-1",
        libraryFolderId = null,
        pageIndex = 3,
        left = 10f,
        top = 20f,
        right = 80f,
        bottom = 32f,
        color = "yellow",
        noteText = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun missingSegmentsFallsBackToLegacyParentRectangle() {
        val resolved = parent.resolvedGeometrySegments(emptyList())

        assertEquals(1, resolved.size)
        assertEquals(parent.pageIndex, resolved.single().pageIndex)
        assertEquals(parent.left, resolved.single().left)
    }

    @Test
    fun validSegmentsRemainOrderedAcrossPages() {
        val segments = listOf(
            segment(order = 2, page = 4, top = 8f),
            segment(order = 0, page = 3, top = 20f),
            segment(order = 1, page = 3, top = 40f),
        )

        val resolved = parent.resolvedGeometrySegments(segments)

        assertEquals(listOf(0, 1, 2), resolved.map { it.orderIndex })
        assertEquals(listOf(3, 3, 4), resolved.map { it.pageIndex })
    }

    @Test
    fun invalidExtensionFallsBackWithoutDiscardingParent() {
        val invalid = segment(order = 0, page = 3, top = 20f).copy(right = 10f)

        val resolved = parent.resolvedGeometrySegments(listOf(invalid))

        assertEquals(1, resolved.size)
        assertEquals(parent.left, resolved.single().left)
        assertTrue(resolved.single().isValidPdfAnnotationSegment())
    }

    private fun segment(order: Int, page: Int, top: Float) = PdfAnnotationSegmentEntity(
        annotationId = parent.id,
        orderIndex = order,
        pageIndex = page,
        left = 10f,
        top = top,
        right = 80f,
        bottom = top + 12f,
    )
}
