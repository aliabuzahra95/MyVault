package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerCoursesRefinementContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `drawer folder row toggles while content rows open`() {
        val shell = source("ui/components/VaultMobileWebShell.kt")
        val node = shell.substringAfter("private fun DrawerExplorerNode(").substringBefore("private fun DrawerProfileHeader(")

        assertTrue(node.contains("onClick = { if (isFolder) onToggle(nodeKey) else onOpen(node) }"))
        assertTrue(node.contains("onClick = { onToggle(nodeKey) }"))
        assertTrue(node.contains("Surface(onClick = { onAdd(node) }"))
        assertTrue(node.contains("tween(160)"))
        assertFalse(node.contains("if (expandable) onToggle(nodeKey) else onOpen(node)"))
    }

    @Test
    fun `Study and Library corpus folder rows use one toggle target with independent add`() {
        val corpus = source("ui/components/CorpusBrowser.kt")
        val home = source("ui/screens/HomeScreen.kt")
        val library = source("ui/screens/LibraryScreen.kt")

        assertTrue(corpus.contains(".combinedClickable(onClick = onToggle, onLongClick = onLongPress)"))
        assertTrue(corpus.contains(".clickable(role = Role.Button, onClick = onAdd)"))
        assertTrue(home.contains("onToggle = { onToggleFolder(item) }"))
        assertTrue(library.contains("onToggle = { onFolderExpandedChange(folder.id, !expanded) }"))
    }

    @Test
    fun `normal Library folder callbacks reveal inline while legacy route remains`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertTrue(navigation.contains("fun revealLibraryFolder("))
        assertTrue(navigation.contains("folderId: String,"))
        assertTrue(navigation.contains("onFolderClick = { folderId -> revealLibraryFolder(folderId) }"))
        assertTrue(navigation.contains("route = VaultDestination.LibraryFolder.route"))
        assertTrue(navigation.contains("route = VaultDestination.FolderView.route"))
    }

    @Test
    fun `drawer footer is Settings Backup Theme and reuses production backup`() {
        val shell = source("ui/components/VaultMobileWebShell.kt")
        val footer = shell.substringAfter("private fun DrawerUtilityRow(")
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val shellViewModel = source("ui/viewmodel/ShellPreferencesViewModel.kt")

        assertTrue(footer.contains("text = \"Settings\""))
        assertTrue(footer.contains("Icons.Rounded.Backup"))
        assertTrue(footer.contains("Icons.Outlined.LightMode"))
        assertFalse(footer.contains("Icons.Outlined.PersonOutline"))
        assertTrue(navigation.contains("shellViewModel.pushGoogleDriveSync"))
        assertTrue(shellViewModel.contains("googleDriveRestoreController.get().startPush"))
        assertTrue(shellViewModel.contains("driveRestoreState = googleDriveRestoreController.get().state"))
    }

    @Test
    fun `Course cards are compact and sticky notes precede full Course notes`() {
        val courses = source("ui/screens/CoursesScreen.kt")
        val folder = source("ui/screens/FolderViewScreen.kt")
        val stickySection = folder.indexOf("CourseCountSectionLabel(label = \"STICKY NOTES\"")
        val courseNotesSection = folder.indexOf("CourseCountSectionLabel(label = \"COURSE NOTES\"")
        val stickyCard = folder.substringAfter("private fun CourseStickyNoteCard(").substringBefore("private fun CourseCountSectionLabel(")

        assertTrue(courses.contains(".heightIn(min = 56.dp)"))
        assertTrue(stickySection >= 0 && stickySection < courseNotesSection)
        assertTrue(stickyCard.contains(".fillMaxWidth()"))
        assertTrue(stickyCard.contains("color = colors.warningSoft"))
        assertFalse(stickyCard.contains("maxLines"))
        assertFalse(stickyCard.contains("TextOverflow.Ellipsis"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
