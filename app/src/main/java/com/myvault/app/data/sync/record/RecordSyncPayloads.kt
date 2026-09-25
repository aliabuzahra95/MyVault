package com.myvault.app.data.sync.record

import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun notePayload(note: NoteEntity, blocks: List<BlockEntity>): String {
    val rich = blocks.firstOrNull { it.type == "rich_text" }?.content
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject().put("text", note.bodyPlainText).put("styleMarks", JSONArray()).put("noteLinks", JSONArray())
    val rows = JSONArray()
    blocks.sortedWith(compareBy<BlockEntity> { it.orderIndex }.thenBy { it.id }).forEach { block ->
        rows.put(JSONObject().put("id", block.id).put("noteId", block.noteId).put("type", block.type)
            .put("content", block.content).put("orderIndex", block.orderIndex))
    }
    return canonicalJson(JSONObject()
        .put("id", note.id).put("folderId", note.folderId ?: JSONObject.NULL)
        .put("parentNoteId", note.parentNoteId ?: JSONObject.NULL).put("title", note.title)
        .put("bodyPlainText", note.bodyPlainText).put("isPinned", note.isPinned)
        .put("isFolderPinned", note.isFolderPinned).put("isFavourite", note.isFavourite)
        .put("orderIndex", note.orderIndex).put("createdAt", note.createdAt)
        .put("updatedAt", note.updatedAt).put("deletedAt", note.deletedAt ?: JSONObject.NULL)
        .put("richText", rich).put("blocks", rows))
}

internal fun folderPayload(folder: FolderEntity): String = canonicalJson(JSONObject()
    .put("id", folder.id).put("parentId", folder.parentId ?: JSONObject.NULL)
    .put("name", folder.name).put("description", folder.description ?: JSONObject.NULL)
    .put("orderIndex", folder.orderIndex).put("isFavourite", folder.isFavourite)
    .put("mode", folder.mode).put("createdAt", folder.createdAt)
    .put("updatedAt", folder.updatedAt).put("deletedAt", folder.deletedAt ?: JSONObject.NULL)
    .put("colorKey", folder.colorKey ?: JSONObject.NULL))

internal fun noteFromPayload(value: JSONObject): NoteEntity = NoteEntity(
    id = value.getString("id"), folderId = value.nullableString("folderId"),
    parentNoteId = value.nullableString("parentNoteId"), title = value.getString("title"),
    bodyPlainText = value.getString("bodyPlainText"), isPinned = value.getBoolean("isPinned"),
    isFolderPinned = value.getBoolean("isFolderPinned"), isFavourite = value.getBoolean("isFavourite"),
    orderIndex = value.getInt("orderIndex"), createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"), deletedAt = value.nullableLong("deletedAt"),
)

internal fun blocksFromPayload(value: JSONObject): List<BlockEntity> {
    val rows = value.getJSONArray("blocks")
    val blocks = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        require(row.getString("noteId") == value.getString("id"))
        BlockEntity(row.getString("id"), row.getString("noteId"), row.getString("type"), row.getString("content"), row.getInt("orderIndex"))
    }
    if (blocks.isNotEmpty()) return blocks
    val rich = value.getJSONObject("richText")
    if (rich.optString("text").isEmpty()) return emptyList()
    return listOf(BlockEntity("${value.getString("id")}-rich-text", value.getString("id"), "rich_text", rich.toString(), 0))
}

internal fun folderFromPayload(value: JSONObject): FolderEntity {
    require(value.getString("mode") == "study")
    return FolderEntity(
        id = value.getString("id"), parentId = value.nullableString("parentId"),
        name = value.getString("name"), description = value.nullableString("description"),
        orderIndex = value.getInt("orderIndex"), isFavourite = value.getBoolean("isFavourite"),
        mode = "study", createdAt = value.getLong("createdAt"), updatedAt = value.getLong("updatedAt"),
        deletedAt = value.nullableLong("deletedAt"), colorKey = value.nullableString("colorKey"),
    )
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
