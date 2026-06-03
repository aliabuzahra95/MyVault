package com.myvault.app.data.repository

import android.content.Context
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.ui.viewmodel.LibraryAnnotationItem
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.LibraryViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LibrarySnapshotRepository @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("library_startup_snapshot", Context.MODE_PRIVATE)

    fun load(mode: String, folderId: String?): LibraryUiState? =
        runCatching {
            val raw = preferences.getString(keyFor(mode, folderId), null) ?: return null
            JSONObject(raw).toLibraryUiState()
        }.getOrNull()

    fun save(mode: String, folderId: String?, state: LibraryUiState) {
        if (
            state.folders.isEmpty() &&
            state.files.isEmpty() &&
            state.pinnedFiles.isEmpty() &&
            state.recentFiles.isEmpty() &&
            state.allFolders.isEmpty()
        ) return
        val snapshot = state.copy(
            references = emptyList(),
            attachmentTags = emptyMap(),
            annotationTags = emptyMap(),
            studyNotes = emptyList(),
            studyNotesLoading = false,
            importing = false,
            importMessage = null,
            duplicatePdfImport = null,
        )
        preferences.edit().putString(keyFor(mode, folderId), snapshot.toJson().toString()).apply()
    }

    private fun keyFor(mode: String, folderId: String?): String {
        val safeMode = when (mode) {
            FOLDER_MODE_PERSONAL_LIBRARY -> FOLDER_MODE_PERSONAL_LIBRARY
            FOLDER_MODE_LIBRARY -> FOLDER_MODE_LIBRARY
            else -> mode
        }
        return "$safeMode:${folderId ?: "root"}"
    }
}

private fun LibraryUiState.toJson(): JSONObject = JSONObject()
    .put("currentFolder", currentFolder?.toJson())
    .put("folders", folders.toJsonArray { it.toJson() })
    .put("files", files.take(80).toJsonArray { it.toSnapshotJson() })
    .put("pinnedFiles", pinnedFiles.take(24).toJsonArray { it.toSnapshotJson() })
    .put("annotations", annotations.take(80).toJsonArray { it.toJson() })
    .put("continueReading", continueReading?.toSnapshotJson())
    .put("recentFiles", recentFiles.take(12).toJsonArray { it.toSnapshotJson() })
    .put("allFolders", allFolders.toJsonArray { it.toJson() })
    .put("expandedFolderIds", expandedFolderIds.toJsonArray { it })
    .put("viewMode", viewMode.storedValue)

private fun JSONObject.toLibraryUiState(): LibraryUiState = LibraryUiState(
    currentFolder = optJSONObject("currentFolder")?.toFolderEntity(),
    folders = optJSONArray("folders").orEmptyJsonArray().mapObjects { it.toLibraryFolderItem() },
    files = optJSONArray("files").orEmptyJsonArray().mapObjects { it.toLibraryFileItem() },
    pinnedFiles = optJSONArray("pinnedFiles").orEmptyJsonArray().mapObjects { it.toLibraryFileItem() },
    annotations = optJSONArray("annotations").orEmptyJsonArray().mapObjects { it.toLibraryAnnotationItem() },
    continueReading = optJSONObject("continueReading")?.toLibraryFileItem(),
    recentFiles = optJSONArray("recentFiles").orEmptyJsonArray().mapObjects { it.toLibraryFileItem() },
    allFolders = optJSONArray("allFolders").orEmptyJsonArray().mapObjects { it.toLibraryFolderItem() },
    expandedFolderIds = optJSONArray("expandedFolderIds").orEmptyJsonArray().mapStrings().toSet(),
    viewMode = LibraryViewMode.fromStoredValue(optString("viewMode", LibraryViewMode.List.storedValue)),
)

private fun FolderEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("parentId", parentId)
    .put("name", name)
    .put("orderIndex", orderIndex)
    .put("isFavourite", isFavourite)
    .put("mode", mode)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)
    .put("deletedAt", deletedAt)

private fun JSONObject.toFolderEntity(): FolderEntity = FolderEntity(
    id = optString("id"),
    parentId = nullableString("parentId"),
    name = optString("name"),
    orderIndex = optInt("orderIndex", 0),
    isFavourite = optBoolean("isFavourite", false),
    mode = optString("mode", FOLDER_MODE_LIBRARY),
    createdAt = optLong("createdAt", 0L),
    updatedAt = optLong("updatedAt", 0L),
    deletedAt = if (has("deletedAt") && !isNull("deletedAt")) optLong("deletedAt") else null,
)

private fun LibraryFolderItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("count", count)
    .put("depth", depth)
    .put("files", files.take(40).toJsonArray { it.toSnapshotJson() })
    .put("annotations", annotations.take(40).toJsonArray { it.toJson() })
    .put("children", children.toJsonArray { it.toJson() })

private fun JSONObject.toLibraryFolderItem(): LibraryFolderItem = LibraryFolderItem(
    id = optString("id"),
    name = optString("name"),
    count = optInt("count", 0),
    depth = optInt("depth", 0),
    files = optJSONArray("files").orEmptyJsonArray().mapObjects { it.toLibraryFileItem() },
    annotations = optJSONArray("annotations").orEmptyJsonArray().mapObjects { it.toLibraryAnnotationItem() },
    children = optJSONArray("children").orEmptyJsonArray().mapObjects { it.toLibraryFolderItem() },
)

private fun LibraryFileItem.toSnapshotJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("kind", kind)
    .put("size", size)
    .put("meta", meta)
    .put("mimeType", mimeType)
    .put("pageIndex", pageIndex)
    .put("pageCount", pageCount)
    .put("progressPercent", progressPercent)
    .put("lastOpenedAt", lastOpenedAt)
    .put("pinned", pinned)
    .put("highlightCount", highlightCount)
    .put("annotationNoteCount", annotationNoteCount)

private fun JSONObject.toLibraryFileItem(): LibraryFileItem = LibraryFileItem(
    id = optString("id"),
    name = optString("name"),
    kind = optString("kind"),
    size = optString("size"),
    meta = optString("meta"),
    mimeType = optString("mimeType", "application/octet-stream"),
    localPath = "",
    pageIndex = if (has("pageIndex") && !isNull("pageIndex")) optInt("pageIndex") else null,
    pageCount = if (has("pageCount") && !isNull("pageCount")) optInt("pageCount") else null,
    progressPercent = if (has("progressPercent") && !isNull("progressPercent")) optDouble("progressPercent").toFloat() else null,
    lastOpenedAt = optLong("lastOpenedAt", 0L),
    pinned = optBoolean("pinned", false),
    highlightCount = optInt("highlightCount", 0),
    annotationNoteCount = optInt("annotationNoteCount", 0),
)

private fun LibraryAnnotationItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("attachmentId", attachmentId)
    .put("fileName", fileName)
    .put("pageIndex", pageIndex)
    .put("color", color)
    .put("displayTitle", displayTitle)
    .put("displayFolderId", displayFolderId)
    .put("notePreview", notePreview)
    .put("updatedAt", updatedAt)

private fun JSONObject.toLibraryAnnotationItem(): LibraryAnnotationItem = LibraryAnnotationItem(
    id = optString("id"),
    attachmentId = optString("attachmentId"),
    fileName = optString("fileName"),
    pageIndex = optInt("pageIndex", 0),
    color = optString("color"),
    displayTitle = nullableString("displayTitle"),
    displayFolderId = nullableString("displayFolderId"),
    notePreview = optString("notePreview"),
    updatedAt = optLong("updatedAt", 0L),
)

private fun <T> Iterable<T>.toJsonArray(transform: (T) -> Any): JSONArray =
    JSONArray().also { array -> forEach { array.put(transform(it)) } }

private fun JSONArray.mapStrings(): List<String> =
    List(length()) { index -> optString(index) }.filter { it.isNotBlank() }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> optJSONObject(index) }.filterNotNull().map(transform)

private fun JSONArray?.orEmptyJsonArray(): JSONArray = this ?: JSONArray()

private fun JSONObject.nullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
