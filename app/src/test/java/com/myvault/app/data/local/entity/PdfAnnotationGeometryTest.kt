package com.myvault.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun restoredGeometryKeepsHighlightVisibleWhenLegacyRectangleIsUnavailable() {
        val geometryOnlyParent = parent.copy(left = 0f, top = 0f, right = 0f, bottom = 0f)
        val restoredSegments = listOf(segment(order = 0, page = 11, top = 120f))

        assertTrue(geometryOnlyParent.isSupportedPdfAnnotation(restoredSegments))
        assertTrue(geometryOnlyParent.occursOnPdfPage(11, restoredSegments))
        assertEquals(11, geometryOnlyParent.primaryPdfPageIndex(restoredSegments))
    }

    @Test
    fun multiRectangleHighlightAppearsOnEveryRepresentedPage() {
        val restoredSegments = listOf(
            segment(order = 0, page = 10, top = 120f),
            segment(order = 1, page = 11, top = 20f),
        )

        assertTrue(parent.isSupportedPdfAnnotation(restoredSegments))
        assertTrue(parent.occursOnPdfPage(10, restoredSegments))
        assertTrue(parent.occursOnPdfPage(11, restoredSegments))
        assertFalse(parent.occursOnPdfPage(12, restoredSegments))
    }

    @Test
    fun unknownAnnotationTypeIsNotAcceptedAsSupportedContent() {
        val unknown = parent.copy(annotationType = "future_unknown")

        assertFalse(unknown.isSupportedPdfAnnotation(emptyList()))
    }

    @Test
    fun highlightWithANoteBelongsToBothSupportedActivityCategories() {
        val annotatedHighlight = parent.copy(noteText = "A note attached to this highlight")

        assertTrue(annotatedHighlight.isPdfHighlightActivity())
        assertTrue(annotatedHighlight.isPdfNoteActivity())
    }

    @Test
    fun webStyleDirectRectangleUsesZeroBasedPageIndexWithoutConversion() {
        val webHighlight = parent.copy(
            pageIndex = 11,
            left = 58.35f,
            top = 340f,
            right = 412.37f,
            bottom = 437.59f,
            selectedText = null,
            annotationType = PdfAnnotationEntity.TYPE_HIGHLIGHT,
        )

        assertTrue(webHighlight.isSupportedPdfAnnotation(emptyList()))
        assertEquals(11, webHighlight.primaryPdfPageIndex(emptyList()))
        assertTrue(webHighlight.occursOnPdfPage(11, emptyList()))
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
