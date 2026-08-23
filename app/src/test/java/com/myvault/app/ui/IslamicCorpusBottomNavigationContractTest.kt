package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFixedBottomNavigationContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `Islamic Corpus destinations keep their required order`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(
            navigation.contains(
                "listOf(VaultRootMode.Courses, VaultRootMode.Study, VaultRootMode.Library, " +
                    "VaultRootMode.Quran, VaultRootMode.Memorise)",
            ),
        )
    }

    @Test
    fun `one shared fixed bar serves Islamic Corpus and Personal pagers`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertEquals(1, navigation.countOccurrences("VaultFixedBottomNavigation("))
        assertFalse(navigation.contains("FloatingBottomNav("))
        assertTrue(navigation.contains("listOf(VaultRootMode.Personal, VaultRootMode.Library)"))
        assertTrue(navigation.contains("pagerContent("))
        assertTrue(navigation.contains(".weight(1f)"))
        assertTrue(navigation.contains("onItemSelected = handleModeSelected"))
    }

    @Test
    fun `fixed bar owns width boundary indicator and navigation inset`() {
        val component = source("ui/components/VaultFixedBottomNavigation.kt")

        assertTrue(component.contains("modifier.fillMaxWidth()"))
        assertTrue(component.contains("HorizontalDivider("))
        assertTrue(component.contains(".navigationBarsPadding()"))
        assertTrue(component.contains("colors.accentSoft"))
        assertTrue(component.contains("colors.accent"))
        assertTrue(component.contains("shadowElevation = 0.dp"))
        assertFalse(component.contains("VaultSpacing.lg"))
    }

    @Test
    fun `pager navigation and state behavior remain in place`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(navigation.contains("HorizontalPager("))
        assertTrue(navigation.contains("rememberPagerState("))
        assertTrue(navigation.contains("snapshotFlow { pagerState.targetPage"))
        assertTrue(navigation.contains("snapshotFlow { pagerState.settledPage"))
        assertTrue(navigation.contains("pagerState.animateScrollToPage("))
        assertTrue(navigation.contains("rememberSaveable(modes)"))
    }

    @Test
    fun `workspace changes recreate pager state before page sets are exchanged`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(navigation.contains("key(preferences.workspace)"))
        assertTrue(navigation.contains("rememberPagerState(initialPage = requestedPage) { modes.size }"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
