package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage9DirectRefinementContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `note attachments remain compact and visible in reading and editing`() {
        val reading = source("ui/screens/ReadingScreen.kt")
        val editor = source("ui/screens/EditorScreen.kt")

        assertTrue(reading.contains("items(uiState.attachments"))
        assertTrue(reading.contains("AttachmentSheetRow("))
        assertTrue(editor.contains("EditorAttachmentPreviewSection("))
        assertTrue(editor.contains("AttachmentSheetRow("))
        assertFalse(editor.contains("if (!bodyFocused &&"))
        assertFalse(editor.contains("heightIn(min = 180.dp, max = 320.dp)"))
    }

    @Test
    fun `Tafsir uses a dismissible sheet with an independently scrolling body`() {
        val reader = source("ui/quran/FrozenQuranReader.kt")
        val surface = source("ui/quran/QuranReaderSurface.kt")

        assertTrue(reader.contains("internal fun QuranTafsirSheet("))
        assertTrue(reader.contains("ModalBottomSheet("))
        assertTrue(reader.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(reader.contains("Icons.Rounded.Close, \"Close Tafsir\""))
        assertTrue(surface.contains("expandedTafsirVerseKey != null"))
        assertTrue(surface.contains("QuranTafsirSheet("))
    }

    @Test
    fun `reciter changes cancel stale preparation and retain one selected source`() {
        val viewModel = source("ui/viewmodel/QuranReaderViewModel.kt")

        val controller = source("data/quran/audio/QuranPlaybackController.kt")
        assertTrue(controller.contains("preparation?.cancel()"))
        assertTrue(controller.contains("++generation"))
        assertTrue(controller.contains("request != generation"))
        assertTrue(viewModel.contains("selectedAudioReciter = reciter"))
        assertTrue(viewModel.contains("playAudio(activeVerseKey ?: pickerAyah.verseKey, reciter, playbackController.state.value.mode)"))
    }

    @Test
    fun `editor exposes compact style tokens without removing style choices`() {
        val toolbar = source("ui/components/EditorToolbar.kt")

        assertTrue(toolbar.contains("Paragraph(\"P\""))
        assertTrue(toolbar.contains("Heading(\"H1\""))
        assertTrue(toolbar.contains("Heading2(\"H2\""))
        assertTrue(toolbar.contains("Heading3(\"H3\""))
        assertTrue(toolbar.contains("Heading4(\"H4\""))
        assertTrue(toolbar.contains("isStyleToken"))
        assertFalse(toolbar.contains("Paragraph(\"Paragraph\""))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
