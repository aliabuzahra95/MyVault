package com.myvault.app.ui.model

import com.myvault.app.ui.components.VaultTreeItem
import java.text.Collator
import java.util.Locale

enum class StudySortMode(val label: String) {
    Manual("Manual"), Alphabetical("Alphabetical"), Modified("Recently modified"),
    Created("Recently created"), Opened("Recently opened");
}

data class StudyOrganisationState(
    val sortMode: StudySortMode = StudySortMode.Manual,
    val openedAt: Map<String, Long> = emptyMap(),
)

fun List<VaultTreeItem>.studySorted(mode: StudySortMode, openedAt: Map<String, Long> = emptyMap()): List<VaultTreeItem> {
    val alphabetical = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }
    val comparator = when (mode) {
        StudySortMode.Manual -> compareBy<VaultTreeItem> { it.orderIndex }.thenBy { it.id }
        StudySortMode.Alphabetical -> Comparator<VaultTreeItem> { a, b -> alphabetical.compare(a.name, b.name) }.thenBy { it.id }
        StudySortMode.Modified -> compareByDescending<VaultTreeItem> { it.updatedAt }.thenByDescending { it.createdAt }.thenBy { it.id }
        StudySortMode.Created -> compareByDescending<VaultTreeItem> { it.createdAt }.thenBy { it.id }
        StudySortMode.Opened -> compareByDescending<VaultTreeItem> { openedAt[it.id] ?: 0L }.thenBy { it.orderIndex }.thenBy { it.id }
    }
    fun sort(items: List<VaultTreeItem>): List<VaultTreeItem> = items.sortedWith(comparator).map { it.copy(children = sort(it.children)) }
    return sort(this)
}

fun List<VaultTreeItem>.studySiblings(id: String): List<VaultTreeItem>? {
    if (any { it.id == id }) return this
    for (item in this) item.children.studySiblings(id)?.let { return it }
    return null
}

/** Move only within one parent; children stay attached to their original item. */
fun List<VaultTreeItem>.moveStudySibling(from: String, to: String): List<VaultTreeItem> {
    val a = indexOfFirst { it.id == from }
    val b = indexOfFirst { it.id == to }
    if (a >= 0 && b >= 0) return toMutableList().apply { add(b, removeAt(a)) }
    return map { it.copy(children = it.children.moveStudySibling(from, to)) }
}
