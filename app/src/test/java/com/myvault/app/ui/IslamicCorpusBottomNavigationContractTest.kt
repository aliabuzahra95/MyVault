package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMobileWebNavigationContractTest {

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
    fun `one shared mobile web drawer serves Islamic Corpus and Personal pagers`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertEquals(1, navigation.countOccurrences("VaultMobileWebShell("))
        assertTrue(navigation.contains(") { onOpenNavigation ->\n        NavHost("))
        assertFalse(navigation.contains("VaultFixedBottomNavigation("))
        assertFalse(navigation.contains("FloatingBottomNav("))
        assertFalse(navigation.contains("useNavigationShell"))
        assertTrue(navigation.contains("listOf(VaultRootMode.Personal, VaultRootMode.Library)"))
        assertTrue(navigation.contains("HorizontalPager("))
        assertTrue(navigation.contains("onItemSelected = { index -> selectRootMode(rootModes[index]) }"))
    }

    @Test
    fun `frozen shell owns responsive explorer workspace chooser and compact utility`() {
        val component = source("ui/components/VaultMobileWebShell.kt")
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(component.contains("ModalNavigationDrawer("))
        assertTrue(component.contains("ModalDrawerSheet("))
        assertTrue(component.contains("(maxWidth - 46.dp).coerceAtMost(366.dp)"))
        assertTrue(component.contains(".height(56.dp)"))
        assertTrue(component.contains("Icons.Rounded.Menu"))
        assertTrue(component.contains("DrawerUtilityRow("))
        assertFalse(component.contains("HorizontalDivider("))
        assertTrue(component.contains(".navigationBarsPadding()"))
        assertTrue(component.contains("VaultMobileWebExplorerSection"))
        assertTrue(component.contains("DrawerExplorerNode("))
        assertTrue(component.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(component.contains("onExplorerAddSelected"))
        assertTrue(component.contains("onExplorerMoreSelected"))
        assertTrue(component.contains("selectedExplorerNodeId"))
        assertTrue(component.contains("findExplorerPath(selectedId)"))
        assertTrue(component.contains("onWorkspaceSelected: (String) -> Unit"))
        assertTrue(component.contains(".clickable(onClick = onWorkspaceSelected)"))
        assertTrue(component.contains("title = \"Switch workspace\""))
        assertTrue(component.contains("label = \"Islamic Corpus\""))
        assertTrue(component.contains("label = \"Personal\""))
        assertTrue(navigation.contains("onWorkspaceSelected = ::switchWorkspace"))
        assertTrue(component.contains("onThemeSelected"))
        assertFalse(component.contains("Icons.Rounded.Backup"))
        assertFalse(component.contains("Icons.Rounded.MoreVert"))
        assertTrue(component.contains(".combinedClickable("))
        assertFalse(navigation.contains("slideIntoContainer("))
        assertFalse(navigation.contains("slideOutOfContainer("))
        assertFalse(component.contains("VaultFixedBottomNavigation("))
    }

    @Test
    fun `one native action host manages every explorer content family`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val actions = source("ui/components/VaultExplorerActionHost.kt")
        val modal = source("ui/components/VaultModal.kt")

        assertEquals(1, navigation.countOccurrences("VaultExplorerActionHost("))
        assertTrue(navigation.contains("VaultRootMode.Study, VaultRootMode.Personal"))
        assertTrue(navigation.contains("VaultRootMode.Library"))
        assertTrue(navigation.contains("VaultRootMode.Courses"))
        assertTrue(actions.contains("New note"))
        assertTrue(actions.contains("Upload document"))
        assertTrue(actions.contains("Rename"))
        assertTrue(actions.contains("Move"))
        assertTrue(actions.contains("Unpin"))
        assertTrue(actions.contains("Delete"))
        assertTrue(modal.contains("Modifier.heightIn(max = 430.dp)"))
    }

    @Test
    fun `explorer move choices exclude the selected folder and descendants`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(navigation.contains("source.descendantIds()"))
        assertTrue(navigation.contains("id in excludedIds"))
        assertTrue(navigation.contains("roots.flatMap { it.flattenExplorerFolderTargets"))
        assertTrue(navigation.contains("VaultExplorerMoveTarget(null, rootLabel)"))
    }

    @Test
    fun `root pager only changes from navigation selections`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(navigation.contains("HorizontalPager("))
        assertTrue(navigation.contains("rememberPagerState("))
        assertTrue(navigation.contains("modes.indexOfFirst { it.name == selectedRootModeName }"))
        assertTrue(navigation.contains("LaunchedEffect(modes, requestedPage)"))
        assertTrue(navigation.contains("userScrollEnabled = false"))
        assertTrue(navigation.contains("pagerState.scrollToPage("))
        assertFalse(navigation.contains("pagerState.animateScrollToPage("))
        assertFalse(navigation.contains("snapshotFlow { pagerState.targetPage"))
    }

    @Test
    fun `drawer keeps interactive swipe gestures enabled`() {
        val component = source("ui/components/VaultMobileWebShell.kt")

        assertTrue(component.contains("gesturesEnabled = true"))
        assertTrue(component.contains("Modifier.systemGestureExclusion"))
        assertTrue(component.contains("drawerGestureEdgeWidth"))
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
