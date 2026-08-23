package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFabAndQuranChromeContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `workspace FABs are circular and sit just above the fixed navigation`() {
        val component = source("ui/components/FloatingActionMenu.kt")
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(component.contains("shape = CircleShape"))
        assertTrue(component.contains("val fixedBottomBarFabPadding: Dp = 12.dp"))
        assertEquals(
            5,
            navigation.countOccurrences(
                "fabBottomPadding = FloatingActionStackDefaults.fixedBottomBarFabPadding",
            ),
        )
    }

    @Test
    fun `all expandable workspace FAB hosts apply the same scrim`() {
        val home = source("ui/screens/HomeScreen.kt")
        val library = source("ui/screens/LibraryScreen.kt")
        val folder = source("ui/screens/FolderViewScreen.kt")

        assertTrue(home.contains(".background(colors.scrim)"))
        assertTrue(library.contains(".background(colors.scrim)"))
        assertTrue(folder.contains(".background(colors.scrim)"))
    }

    @Test
    fun `Quran reader starts with its reader content and retains correct scroll offsets`() {
        val reader = source("ui/quran/QuranReaderSurface.kt")
        val chrome = source("ui/quran/QuranReaderChrome.kt")

        assertFalse(reader.contains("QuranContinueReadingCard("))
        assertFalse(reader.contains("quran_continue_"))
        assertFalse(chrome.contains("internal fun QuranContinueReadingCard"))
        assertTrue(reader.contains("val readerHeaderItemCount = 1 + if (latestSurahTestAttempt != null) 1 else 0"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
