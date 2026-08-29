package com.myvault.app.data.repository

import android.content.Context
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.quran.QuranReflectionSummary
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.data.local.entity.normalizeFolderColorKey
import com.myvault.app.ui.screens.AttachmentSample
import com.myvault.app.ui.viewmodel.HomeUiState
import com.myvault.app.ui.viewmodel.HomeQuranContinue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class HomeSnapshotRepository @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("home_startup_snapshot", Context.MODE_PRIVATE)
    private val lastSavedJsonByKey = mutableMapOf<String, String>()

    fun load(mode: String): HomeUiState? =
        runCatching {
            val raw = preferences.getString(keyFor(mode), null) ?: return null
            JSONObject(raw).toHomeUiState()
        }.getOrNull()

    fun save(mode: String, state: HomeUiState) {
        if (state.workspace.isEmpty() && state.pinnedNotes.isEmpty() && state.attachments.isEmpty()) return
        val snapshot = state.copy(
            searchQuery = "",
            searchNotes = emptyList(),
            searchFolders = emptyList(),
            searchAttachments = emptyList(),
            searchTags = emptyList(),
        )
        val key = keyFor(mode)
        val encoded = snapshot.toJson().toString()
        val changed = synchronized(lastSavedJsonByKey) {
            if (lastSavedJsonByKey[key] == encoded) {
                false
            } else {
                lastSavedJsonByKey[key] = encoded
                true
            }
        }
        if (changed) {
            preferences.edit().putString(key, encoded).apply()
        }
    }

    private fun keyFor(mode: String): String =
        when (mode) {
            FOLDER_MODE_PERSONAL -> "personal"
            FOLDER_MODE_STUDY -> "study"
            else -> mode
        }
}

private fun HomeUiState.toJson(): JSONObject = JSONObject()
    .put("pinnedNotes", pinnedNotes.take(24).toJsonArray { it.toJson() })
    .put("attachments", attachments.take(24).toJsonArray { it.toJson() })
    .put("workspace", workspace.toJsonArray { it.toJson() })
    .put("expandedFolderIds", expandedFolderIds.toJsonArray { it })
    .put("notePreviewLines", notePreviewLines)
    .put("showFullNoteTitles", showFullNoteTitles)
    .put("quranReflectionCount", quranReflectionSummary.count)
    .put("quranReflectionLatestReference", quranReflectionSummary.latestReference)
    .put("quranContinueSurahName", quranContinue?.surahName)
    .put("quranContinueSurahNumber", quranContinue?.surahNumber)
    .put("quranContinueAyahNumber", quranContinue?.ayahNumber)

private fun JSONObject.toHomeUiState(): HomeUiState = HomeUiState(
    pinnedNotes = optJSONArray("pinnedNotes").orEmptyJsonArray().mapObjects { it.toVaultNoteCardData() },
    attachments = optJSONArray("attachments").orEmptyJsonArray().mapObjects { it.toAttachmentSample() },
    workspace = optJSONArray("workspace").orEmptyJsonArray().mapObjects { it.toVaultTreeItem() },
    expandedFolderIds = optJSONArray("expandedFolderIds").orEmptyJsonArray().mapStrings().toSet(),
    notePreviewLines = optInt("notePreviewLines", 0),
    showFullNoteTitles = optBoolean("showFullNoteTitles", false),
    quranReflectionSummary = QuranReflectionSummary(
        count = optInt("quranReflectionCount", 0),
        latestReference = optString("quranReflectionLatestReference", ""),
    ),
    quranContinue = if (
        optInt("quranContinueSurahNumber", 0) > 0 &&
        optInt("quranContinueAyahNumber", 0) > 0
    ) {
        HomeQuranContinue(
            surahName = optString("quranContinueSurahName").ifBlank { "Surah ${optInt("quranContinueSurahNumber")}" },
            surahNumber = optInt("quranContinueSurahNumber"),
            ayahNumber = optInt("quranContinueAyahNumber"),
        )
    } else {
        null
    },
)

private fun VaultNoteCardData.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("meta", meta)
    .put("tableCount", tableCount)
    .put("preview", preview)

private fun JSONObject.toVaultNoteCardData(): VaultNoteCardData = VaultNoteCardData(
    id = optString("id"),
    title = optString("title"),
    meta = optString("meta"),
    tableCount = optInt("tableCount", 0),
    preview = optString("preview"),
)

private fun AttachmentSample.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("note", note)
    .put("size", size)
    .put("date", date)
    .put("kind", kind)
    .put("noteId", noteId)
    .put("mimeType", mimeType)
    .put("localPath", localPath)

private fun JSONObject.toAttachmentSample(): AttachmentSample = AttachmentSample(
    id = optString("id"),
    name = optString("name"),
    note = optString("note"),
    size = optString("size"),
    date = optString("date"),
    kind = optString("kind"),
    noteId = optString("noteId"),
    mimeType = optString("mimeType", "application/octet-stream"),
    localPath = optString("localPath"),
)

private fun VaultTreeItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("description", description)
    .put("orderIndex", orderIndex)
    .put("type", type.name)
    .put("count", count)
    .put("edited", edited)
    .put("updatedAt", updatedAt)
    .put("attachmentCount", attachmentCount)
    .put("tableCount", tableCount)
    .put("pinned", pinned)
    .put("folderPinned", folderPinned)
    .put("favourite", favourite)
    .put("colorKey", colorKey)
    .put("preview", preview)
    .put("children", children.toJsonArray { it.toJson() })

private fun JSONObject.toVaultTreeItem(): VaultTreeItem = VaultTreeItem(
    id = optString("id"),
    name = optString("name"),
    description = optString("description").ifBlank { null },
    orderIndex = optInt("orderIndex", 0),
    type = runCatching { VaultTreeItemType.valueOf(optString("type")) }.getOrDefault(VaultTreeItemType.Note),
    count = optInt("count", 0),
    edited = optString("edited").ifBlank { null },
    updatedAt = optLong("updatedAt", 0L),
    attachmentCount = optInt("attachmentCount", 0),
    tableCount = optInt("tableCount", 0),
    pinned = optBoolean("pinned", false),
    folderPinned = optBoolean("folderPinned", false),
    favourite = optBoolean("favourite", false),
    colorKey = normalizeFolderColorKey(optString("colorKey").ifBlank { null }),
    preview = optString("preview"),
    children = optJSONArray("children").orEmptyJsonArray().mapObjects { it.toVaultTreeItem() },
)

private fun <T> Iterable<T>.toJsonArray(transform: (T) -> Any): JSONArray =
    JSONArray().also { array -> forEach { array.put(transform(it)) } }

private fun JSONArray.mapStrings(): List<String> =
    List(length()) { index -> optString(index) }.filter { it.isNotBlank() }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> optJSONObject(index) }.filterNotNull().map(transform)

private fun JSONArray?.orEmptyJsonArray(): JSONArray = this ?: JSONArray()
