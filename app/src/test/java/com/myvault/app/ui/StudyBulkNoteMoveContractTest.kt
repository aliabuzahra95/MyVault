package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyBulkNoteMoveContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/screens/HomeScreen.kt")) }

    @Test
    fun `study tree note popup offers multi select and bulk move reuses note move callback`() {
        val home = source("ui/screens/HomeScreen.kt")

        assertTrue(home.contains("fun beginNoteSelection(note: VaultTreeItem)"))
        assertTrue(home.contains("moveOnlySelectionMode = true"))
        assertTrue(home.contains("onClick = { if (selectionMode) onToggleNoteSelection(item) else onOpenNote(item.id) }"))
        assertTrue(home.contains("onLongPress = { onMore(item) }"))
        assertTrue(home.contains("PremiumAction(\"Select multiple\", Icons.Rounded.CheckCircle) { beginNoteSelection(note) }"))
        assertTrue(home.contains("title = \"Move ${'$'}{selectedNotes.size} note${'$'}{if (selectedNotes.size == 1) \"\" else \"s\"}\""))
        assertTrue(home.contains("selectedNotes.forEach { onMoveNoteClick(it.id, targetId) }"))
        assertTrue(home.contains("BackHandler(enabled = manageSelectionMode)"))
        assertTrue(home.contains("if (selectedItemIds.isEmpty()) clearSelectionMode()"))
    }

    @Test
    fun `folder view supports note only selection and preserves course long press actions`() {
        val folder = source("ui/screens/FolderViewScreen.kt")

        assertTrue(folder.contains("val noteSelectionEnabled = !coursePresentation"))
        assertTrue(folder.contains("BackHandler(enabled = noteSelectionMode)"))
        assertTrue(folder.contains("PremiumAction(\"Select multiple\", Icons.Rounded.CheckCircle)"))
        assertTrue(folder.contains("note?.let { beginNoteSelection(it) }"))
        assertTrue(folder.contains("selectedNote = item"))
        assertTrue(folder.contains("noteActionsOpen = true"))
        assertTrue(folder.contains("selectionMode = noteSelectionMode"))
        assertTrue(folder.contains("selectable = { it.type == VaultTreeItemType.Note }"))
        assertTrue(folder.contains("selectedNotes.forEach { onMoveNoteClick(it.id, folderId) }"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
