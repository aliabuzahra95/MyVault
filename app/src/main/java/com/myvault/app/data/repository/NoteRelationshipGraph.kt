package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.NoteEntity

internal object NoteRelationshipGraph {
    fun descendantIds(noteId: String, notes: List<NoteEntity>): List<String> {
        val childrenByParent = notes.groupBy { it.parentNoteId }
        val visited = mutableSetOf(noteId)

        fun collect(parentId: String): List<String> =
            childrenByParent[parentId].orEmpty().flatMap { child ->
                if (!visited.add(child.id)) {
                    emptyList()
                } else {
                    listOf(child.id) + collect(child.id)
                }
            }

        return collect(noteId)
    }

    fun sanitizedForPersistence(notes: List<NoteEntity>): List<NoteEntity> {
        val notesById = notes.associateBy { it.id }
        return notes.map { note ->
            if (note.parentNoteId == null || hasValidParentChain(note, notesById)) {
                note
            } else {
                note.copy(parentNoteId = null)
            }
        }
    }

    fun sanitizedForAvailableFolders(
        notes: List<NoteEntity>,
        availableFolderIds: Set<String>,
    ): List<NoteEntity> {
        val recovered = notes.map { note ->
            if (note.folderId != null && note.folderId !in availableFolderIds) {
                note.copy(folderId = null)
            } else {
                note
            }
        }
        return sanitizedForPersistence(recovered)
    }

    fun withMissingFolders(
        notes: List<NoteEntity>,
        availableFolderIds: Set<String>,
    ): List<NoteEntity> = notes.filter { note ->
        note.folderId != null && note.folderId !in availableFolderIds
    }

    fun isValid(notes: List<NoteEntity>): Boolean =
        sanitizedForPersistence(notes) == notes

    private fun hasValidParentChain(note: NoteEntity, notesById: Map<String, NoteEntity>): Boolean {
        val visited = mutableSetOf(note.id)
        var current = note
        while (current.parentNoteId != null) {
            val parent = notesById[current.parentNoteId] ?: return false
            if (!visited.add(parent.id) || parent.folderId != current.folderId) return false
            current = parent
        }
        return true
    }
}
