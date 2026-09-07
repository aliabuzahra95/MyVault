package com.myvault.app.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrozenPdfAnnotationsUiContractTest {
    private val reader = File("src/main/java/com/myvault/app/ui/screens/FrozenPdfReaderScreen.kt").readText()
    private val renderer = File("src/main/java/com/myvault/app/ui/screens/PdfAnnotationPreviewRenderer.kt").readText()

    @Test
    fun toolbarUsesReadableDocumentWideDestinations() {
        assertTrue(reader.contains("FrozenPdfPillCount(Icons.Rounded.BorderColor, \"Highlights\""))
        assertTrue(reader.contains("FrozenPdfPillCount(Icons.Rounded.ChatBubbleOutline, \"Notes\""))
        assertTrue(reader.contains("localActivityFilter = PdfActivityFilter.Highlights"))
        assertTrue(reader.contains("localActivityFilter = PdfActivityFilter.Notes"))
        assertFalse(reader.contains("FrozenPdfPillCount(Icons.Rounded.BorderColor, highlightCount, \"H\")"))
    }

    @Test
    fun sheetRetainsFiltersPageScopeAndTruthfulEmptyState() {
        assertTrue(reader.contains("AllPages(\"All pages\")"))
        assertTrue(reader.contains("ThisPage(\"This page\")"))
        assertTrue(reader.contains("No annotations on page"))
        assertTrue(reader.contains("This PDF still has"))
        assertTrue(reader.contains("Show all pages"))
    }

    @Test
    fun previewsAreRasterDerivedLazyAndContainNoOcrPipeline() {
        assertTrue(reader.contains("LazyColumn"))
        assertTrue(renderer.contains("android.graphics.pdf.PdfRenderer"))
        assertTrue(renderer.contains("LruCache"))
        assertTrue(renderer.contains("resolvedGeometrySegments"))
        assertFalse(renderer.contains("ML Kit", ignoreCase = true))
        assertFalse(renderer.contains("Tesseract", ignoreCase = true))
        assertFalse(renderer.contains("recognizeText"))
    }

    @Test
    fun exactNavigationIncludesTemporaryEmphasis() {
        assertTrue(reader.contains("scrollToAnnotation(annotation, annotationSegments)"))
        assertTrue(reader.contains("emphasizedAnnotationId = annotation.id"))
        assertTrue(reader.contains("delay(550)"))
    }

    @Test
    fun annotationListOwnsScrollingWithoutElasticOrSheetMotionAtItsBoundary() {
        assertTrue(reader.contains("overscrollEffect = null"))
        assertTrue(reader.contains("sheetGesturesEnabled = false"))
        assertTrue(reader.contains("sheetState.partialExpand()"))
        assertTrue(reader.contains("sheetState.expand()"))
        assertTrue(reader.contains("sheetState.currentValue == sheetState.targetValue"))
        assertTrue(reader.contains("Box(Modifier.fillMaxWidth().height(170.dp)"))
    }

    @Test
    fun highlightFilteringDoesNotHideAHighlightThatAlsoHasANote() {
        assertTrue(reader.contains("PdfActivityFilter.Highlights -> annotation.isPdfHighlightActivity()"))
        assertTrue(reader.contains("PdfActivityFilter.Notes -> annotation.isPdfNoteActivity()"))
    }
}
