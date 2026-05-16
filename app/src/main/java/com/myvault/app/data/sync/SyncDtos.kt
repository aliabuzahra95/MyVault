package com.myvault.app.data.sync

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.TagEntity
import org.json.JSONArray
import org.json.JSONObject

data class SyncSnapshot(
    val folders: List<FolderEntity>,
    val notes: List<NoteEntity>,
    val blocks: List<BlockEntity>,
    val tags: List<TagEntity>,
    val noteTags: List<NoteTagCrossRef>,
    val tables: List<NoteTableEntity>,
    val attachments: List<AttachmentEntity>,
)

sealed interface SyncResult {
    data object Success : SyncResult
    data class Skipped(val reason: String) : SyncResult
    data class Failure(val message: String) : SyncResult
}

fun SyncSnapshot.toJson(): JSONObject = JSONObject()
    .put("folders", folders.toJsonArray { it.toJson() })
    .put("notes", notes.toJsonArray { it.toJson() })
    .put("blocks", blocks.toJsonArray { it.toJson() })
    .put("tags", tags.toJsonArray { it.toJson() })
    .put("note_tags", noteTags.toJsonArray { it.toJson() })
    .put("note_tables", tables.toJsonArray { it.toJson() })
    .put("attachments", attachments.toJsonArray { it.toJson() })

private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(toJson(it)) } }

private fun FolderEntity.toJson() = JSONObject()
    .put("id", id)
    .put("parent_id", parentId)
    .put("name", name)
    .put("order_index", orderIndex)
    .put("is_favourite", isFavourite)
    .put("mode", mode)
    .put("created_at", createdAt)
    .put("updated_at", updatedAt)
    .put("deleted_at", deletedAt)

private fun NoteEntity.toJson() = JSONObject()
    .put("id", id)
    .put("folder_id", folderId)
    .put("title", title)
    .put("body_plain_text", bodyPlainText)
    .put("is_pinned", isPinned)
    .put("is_favourite", isFavourite)
    .put("created_at", createdAt)
    .put("updated_at", updatedAt)
    .put("deleted_at", deletedAt)

private fun BlockEntity.toJson() = JSONObject()
    .put("id", id)
    .put("note_id", noteId)
    .put("type", type)
    .put("content", content)
    .put("order_index", orderIndex)

private fun TagEntity.toJson() = JSONObject().put("name", name)

private fun NoteTagCrossRef.toJson() = JSONObject()
    .put("note_id", noteId)
    .put("tag_name", tagName)

private fun NoteTableEntity.toJson() = JSONObject()
    .put("id", id)
    .put("note_id", noteId)
    .put("row_count", rowCount)
    .put("column_count", columnCount)
    .put("cells_json", cellsJson)
    .put("order_index", orderIndex)
    .put("created_at", createdAt)
    .put("updated_at", updatedAt)

private fun AttachmentEntity.toJson() = JSONObject()
    .put("id", id)
    .put("note_id", noteId)
    .put("library_folder_id", libraryFolderId)
    .put("file_name", fileName)
    .put("mime_type", mimeType)
    .put("size_bytes", sizeBytes)
    .put("local_path", localPath)
    .put("remote_url", remoteUrl)
    .put("is_pinned", isPinned)
    .put("created_at", createdAt)
    .put("deleted_at", deletedAt)
