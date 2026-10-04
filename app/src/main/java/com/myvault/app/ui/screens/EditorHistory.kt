package com.myvault.app.ui.screens

import androidx.compose.ui.text.input.TextFieldValue

internal data class EditorHistorySnapshot(
    val title: TextFieldValue,
    val body: TextFieldValue,
    val styleMarks: List<VaultStyleMark>,
    val noteLinks: List<VaultNoteLink>,
    val pendingInlineStyles: Set<VaultInlineStyle>,
)

internal fun EditorHistorySnapshot.hasSameEditorContentAs(other: EditorHistorySnapshot): Boolean =
    title.text == other.title.text && body.text == other.body.text &&
        styleMarks == other.styleMarks && noteLinks == other.noteLinks &&
        pendingInlineStyles == other.pendingInlineStyles

internal data class EditorHistory(
    val past: List<EditorHistorySnapshot> = emptyList(),
    val future: List<EditorHistorySnapshot> = emptyList(),
) {
    fun record(snapshot: EditorHistorySnapshot): EditorHistory =
        if (past.lastOrNull()?.hasSameEditorContentAs(snapshot) == true) this
        else EditorHistory((past + snapshot).takeLast(Limit))

    fun canUndo(current: EditorHistorySnapshot): Boolean = record(current).past.size > 1

    fun canRedo(current: EditorHistorySnapshot): Boolean =
        future.isNotEmpty() && past.lastOrNull()?.hasSameEditorContentAs(current) == true

    fun undo(current: EditorHistorySnapshot): EditorHistoryChange? {
        val recorded = record(current)
        if (recorded.past.size < 2) return null
        return EditorHistoryChange(
            EditorHistory(recorded.past.dropLast(1), (listOf(current) + recorded.future).take(Limit)),
            recorded.past[recorded.past.lastIndex - 1],
        )
    }

    fun redo(current: EditorHistorySnapshot): EditorHistoryChange? {
        if (!canRedo(current)) return null
        val next = future.first()
        return EditorHistoryChange(EditorHistory((past + next).takeLast(Limit), future.drop(1)), next)
    }

    private companion object {
        const val Limit = 48
    }
}

internal data class EditorHistoryChange(val history: EditorHistory, val snapshot: EditorHistorySnapshot)
