package com.myvault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.*
import com.myvault.app.ui.model.moveStudySibling
import com.myvault.app.ui.model.studySiblings
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal data class StudyVisibleRow(val item: VaultTreeItem, val depth: Int)

internal fun visibleStudyRows(items: List<VaultTreeItem>, expanded: (String) -> Boolean, depth: Int = 0): List<StudyVisibleRow> =
    items.flatMap { item ->
        listOf(StudyVisibleRow(item, depth)) +
            if (item.type == VaultTreeItemType.Folder && expanded(item.id)) visibleStudyRows(item.children, expanded, depth + 1) else emptyList()
    }

/** One virtualized scroll owner; dragging can rearrange only an item's direct siblings. */
@Composable
internal fun StudyOrganizeList(
    workspace: List<VaultTreeItem>,
    listState: LazyListState,
    expanded: (String) -> Boolean,
    onToggle: (VaultTreeItem) -> Unit,
    onPersist: suspend (List<String>) -> Boolean,
    onDone: () -> Unit,
    showFullTitle: Boolean,
    header: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<List<VaultTreeItem>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    val currentWorkspace by rememberUpdatedState(workspace)
    val persist by rememberUpdatedState(onPersist)
    val tree = draft ?: workspace
    val rows = visibleStudyRows(tree, expanded)
    val dragSiblings = draggedId?.let { tree.studySiblings(it)?.map { sibling -> sibling.id }?.toSet() }

    fun save(id: String) {
        val ids = draft?.studySiblings(id)?.map { it.id }
        draggedId = null
        if (ids == null || ids == currentWorkspace.studySiblings(id)?.map { it.id }) {
            draft = null
            return
        }
        saving = true
        scope.launch {
            try { failure = !persist(ids) } finally { draft = null; saving = false }
        }
    }

    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        if (!saving) draft = (draft ?: currentWorkspace).moveStudySibling(from.key.toString(), to.key.toString())
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "corpus_study_header") {
            header()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Manual", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                TextButton(onClick = onDone, enabled = !saving && draggedId == null) { Text("Done") }
            }
            if (failure) Text("Order could not be saved. Please try again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        item(key = "corpus_study_pinned") { pinned() }
        items(rows, key = { it.item.id }) { row ->
            val item = row.item
            ReorderableItem(reorder, key = item.id, enabled = dragSiblings == null || item.id in dragSiblings) { isDragging ->
                val siblings = tree.studySiblings(item.id).orEmpty()
                val index = siblings.indexOfFirst { it.id == item.id }
                val actions = listOfNotNull(
                    if (index > 0) CustomAccessibilityAction("Move earlier") {
                        if (saving) false else { draft = tree.moveStudySibling(item.id, siblings[index - 1].id); save(item.id); true }
                    } else null,
                    if (index in 0 until siblings.lastIndex) CustomAccessibilityAction("Move later") {
                        if (saving) false else { draft = tree.moveStudySibling(item.id, siblings[index + 1].id); save(item.id); true }
                    } else null,
                )
                Surface(color = if (isDragging) colors.elevated else colors.bg, shadowElevation = if (isDragging) 3.dp else 0.dp) {
                    Row(Modifier.fillMaxWidth().semantics { customActions = actions }, verticalAlignment = Alignment.CenterVertically) {
                        if (item.type == VaultTreeItemType.Folder) {
                            CorpusFolderRow(item.name, item.count, expanded(item.id),
                                onToggle = { if (draggedId == null && !saving) onToggle(item) }, onLongPress = {},
                                depth = row.depth, colorKey = item.colorKey, modifier = Modifier.weight(1f))
                        } else {
                            CorpusLeafRow(item.name, Icons.Outlined.Description, onClick = {}, onLongPress = {},
                                modifier = Modifier.weight(1f), depth = row.depth, pinned = item.pinned,
                                attachmentCount = item.attachmentCount, showFullTitle = showFullTitle)
                        }
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp).longPressDraggableHandle(enabled = !saving,
                                onDragStarted = { draft = currentWorkspace; draggedId = item.id; failure = false },
                                onDragStopped = { save(item.id) },
                            ),
                        ) { Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder ${item.name}", tint = colors.textMuted) }
                    }
                }
            }
        }
    }
}
