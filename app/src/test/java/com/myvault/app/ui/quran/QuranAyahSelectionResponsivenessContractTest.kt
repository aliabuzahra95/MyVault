package com.myvault.app.ui.quran

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranAyahSelectionResponsivenessContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/quran/FrozenQuranReader.kt")) }

    @Test
    fun `ayah selection does not wait for double tap recognition`() {
        val reader = source("FrozenQuranReader.kt")

        assertTrue(reader.contains("onClick = onSelect"))
        assertFalse(reader.contains("onDoubleClick ="))
        assertTrue(reader.contains("onLongClick = onSaveReadingPosition"))
    }

    @Test
    fun `context toolbar uses bounded motion and switches without an outgoing duplicate`() {
        val reader = source("FrozenQuranReader.kt")
        val surface = source("QuranReaderSurface.kt")

        assertTrue(reader.contains("with(LocalDensity.current) { 8.dp.roundToPx() }"))
        assertTrue(reader.contains("slideInVertically(tween(145"))
        assertTrue(reader.contains("slideOutVertically(tween(115"))
        assertTrue(reader.contains("if (anotherAyahSelected)"))
        assertTrue(reader.contains("ExitTransition.None"))
        assertTrue(surface.contains("anotherAyahSelected = selectedVerseKey != null && selectedVerseKey != ayah.verseKey"))
    }

    private fun source(fileName: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/ui/quran/$fileName")))
}
