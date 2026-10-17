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
    fun `Dashboard records opens and renders only Quran and Study continue slots`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val dashboard = source("ui/screens/DashboardOverviewScreen.kt")
        assertTrue(navigation.contains("recordNoteOpened"))
        assertTrue(navigation.contains("recordLibraryOpened"))
        assertTrue(dashboard.contains("ContinueOverviewCard(\"QUR'AN\""))
        assertTrue(dashboard.contains("ContinueOverviewCard(\"STUDY\""))
        assertTrue(dashboard.contains("OverviewSection(\"Continue reading · Library\""))
        assertTrue(dashboard.indexOf("item(key = \"pinned\")") < dashboard.indexOf("item(key = \"continue\")"))
        assertTrue(dashboard.contains("rows = GridCells.Fixed(2)"))
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
