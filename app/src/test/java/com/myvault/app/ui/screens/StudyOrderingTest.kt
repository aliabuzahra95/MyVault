package com.myvault.app.ui.screens

import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.model.*
import org.junit.Assert.*
import org.junit.Test

class StudyOrderingTest {
    private fun item(id: String, order: Int, name: String = id, created: Long = 10, updated: Long = 20, children: List<VaultTreeItem> = emptyList()) =
        VaultTreeItem(id, name, if (children.isEmpty()) VaultTreeItemType.Note else VaultTreeItemType.Folder,
            orderIndex = order, createdAt = created, updatedAt = updated, children = children)
    private fun List<VaultTreeItem>.ids() = map { it.id }

    @Test fun automaticModesKeepManualOrderAndUseRealSeparateTimestamps() {
        val original = listOf(item("z", 0, created = 100, updated = 200), item("a", 1, created = 300, updated = 50), item("b", 2, created = 20, updated = 400))
        assertEquals(listOf("z", "a", "b"), original.studySorted(StudySortMode.Manual).ids())
        assertEquals(listOf("a", "b", "z"), original.studySorted(StudySortMode.Alphabetical).ids())
        assertEquals(listOf("b", "z", "a"), original.studySorted(StudySortMode.Modified).ids())
        assertEquals(listOf("a", "z", "b"), original.studySorted(StudySortMode.Created).ids())
        assertEquals(listOf("a", "z", "b"), original.studySorted(StudySortMode.Opened, mapOf("a" to 999L)).ids())
        assertEquals(original, original.studySorted(StudySortMode.Modified).studySorted(StudySortMode.Manual))
    }

    @Test fun nestedMixedMoveNeverReparentsOrChangesTimestamps() {
        val root = listOf(item("folder", 0, children = listOf(item("d", 1), item("c", 0))), item("note", 1))
        val moved = root.moveStudySibling("d", "c")
        assertEquals(root.ids(), moved.ids())
        assertEquals(listOf("c", "d"), moved.first().children.ids())
        assertEquals(root, root.moveStudySibling("d", "note"))
        assertEquals(listOf("note", "folder"), root.moveStudySibling("note", "folder").ids())
        assertEquals(root.first().updatedAt, moved.first().updatedAt)
        assertEquals(root.first().children.toSet(), moved.first().children.toSet())
    }

    @Test fun allLevelsSortAndArabicEnglishTiesAreDeterministic() {
        val children = listOf(item("z", 0, "ALPHA"), item("a", 0, "alpha"), item("arabic", 0, "الشمس"))
        for (mode in StudySortMode.entries) {
            val sorted = children.studySorted(mode)
            assertEquals(sorted, children.reversed().studySorted(mode))
            assertEquals(sorted, listOf(item("parent", 0, children = children)).studySorted(mode).first().children)
        }
        assertEquals(listOf("a", "z"), children.studySorted(StudySortMode.Alphabetical).filter { it.name.equals("alpha", true) }.ids())
    }

    @Test fun pinnedPreviewFlagDoesNotRewriteManualIndices() {
        val items = listOf(item("a", 2).copy(pinned = true), item("b", 1))
        assertEquals(listOf("b", "a"), items.studySorted(StudySortMode.Manual).ids())
        assertTrue(items.last { it.id == "a" }.pinned)
    }
}
