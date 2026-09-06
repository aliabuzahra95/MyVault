package com.myvault.app.data.repository

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationPreservationContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/data/local/dao/PdfAnnotationDao.kt")) }

    @Test
    fun `historic text boxes are compatibility-valid instead of blanket deleted`() {
        val dao = source("data/local/dao/PdfAnnotationDao.kt")

        assertTrue(dao.contains("annotationType NOT IN ('highlight', 'page_note', 'text_box')"))
        assertTrue(dao.contains("annotationType = 'text_box'"))
        assertTrue(dao.contains("OR right <= left"))
        assertTrue(dao.contains("OR noteText IS NULL"))
        assertFalse(dao.contains("annotationType != 'highlight'"))
    }

    @Test
    fun `viewer cleanup deletes only ids proven genuinely invalid`() {
        val dao = source("data/local/dao/PdfAnnotationDao.kt")
        val repository = source("data/repository/PdfAnnotationRepository.kt")
        val viewer = source("ui/viewmodel/AttachmentViewerViewModel.kt")

        assertTrue(dao.contains("AND NOT EXISTS"))
        assertTrue(dao.contains("segment.annotationId = pdf_annotations.id"))
        assertTrue(repository.contains("val ids = annotationDao.getGenuinelyInvalidIds()"))
        assertTrue(repository.contains("if (ids.isEmpty()) return"))
        assertTrue(repository.contains("annotationDao.deleteByIds(ids)"))
        assertTrue(repository.contains("sourceBacklinkDao.deleteForAnnotations(ids)"))
        assertTrue(repository.contains("knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, ids)"))
        assertFalse(repository.contains("deleteIncompatibleAnnotations"))
        assertTrue(viewer.contains("pdfAnnotationRepository.cleanupGenuinelyInvalidAnnotations()"))
    }

    @Test
    fun `historic text box creation stays unavailable in the frozen reader`() {
        val reader = source("ui/screens/FrozenPdfReaderScreen.kt")
        val viewer = source("ui/screens/AttachmentViewerScreen.kt")

        assertFalse(reader.contains("Add text box"))
        assertTrue(reader.contains("Read-only historic text box"))
        assertTrue(viewer.contains("onCreateTextBox = {}"))
    }

    @Test
    fun `draw highlight is one shot and has no persistent exit control`() {
        val reader = source("ui/screens/FrozenPdfReaderScreen.kt")
        val viewer = source("ui/screens/AttachmentViewerScreen.kt")

        assertTrue(reader.contains("onDrawHighlightFinished = {"))
        assertTrue(reader.contains("drawHighlightMode = false"))
        assertFalse(reader.contains("FrozenDrawHighlightBar"))
        assertFalse(reader.contains("Text(\"Exit\""))
        assertTrue(viewer.contains("onDrawHighlightFinished()"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
