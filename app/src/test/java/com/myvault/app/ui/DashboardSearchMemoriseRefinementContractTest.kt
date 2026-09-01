package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSearchMemoriseRefinementContractTest {
    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `Dashboard records opens and renders four fixed category slots`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val dashboard = source("ui/screens/StageNineDestinationScreens.kt")
        assertTrue(navigation.contains("recordNoteOpened"))
        assertTrue(navigation.contains("recordLibraryOpened"))
        listOf("Qur'an", "Notes", "Library", "Courses").forEach { label ->
            assertTrue(dashboard.contains("type = \"$label\""))
        }
    }

    @Test
    fun `Quran search and whole Surah navigation use exact existing routes`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val memorise = source("ui/screens/FrozenMemoriseScreen.kt")
        assertTrue(navigation.contains("onQuranClick = { verseKey ->"))
        assertTrue(navigation.contains("pendingQuranVerseKey = verseKey"))
        assertTrue(memorise.contains("onOpenSurah(progress.surah.num, null)"))
        assertTrue(memorise.contains("onWholeSurah(progress.surah.num)"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
