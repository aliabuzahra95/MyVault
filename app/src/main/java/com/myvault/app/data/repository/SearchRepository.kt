package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.ui.components.SearchResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
) {
    fun searchNotes(query: String): Flow<List<SearchResultData>> {
        if (query.isBlank()) return flowOf(emptyList())
        val normalizedQuery = query.trim()
        return combine(noteDao.observeAll(), folderDao.observeAll()) { notes, folders ->
            val folderNames = folders.associate { it.id to it.name }
            notes
                .filter {
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                        it.bodyPlainText.contains(normalizedQuery, ignoreCase = true)
                }
                .take(40)
                .map {
                    SearchResultData(
                        title = it.title,
                        snippet = it.bodyPlainText.compactSearchSnippet(normalizedQuery),
                        folder = folderNames[it.folderId] ?: "Unfiled",
                        id = it.id,
                    )
                }
        }
    }

    fun searchFolders(query: String) = folderDao.observeAll().map { folders ->
        if (query.isBlank()) return@map emptyList()
        folders.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun searchTags(query: String): Flow<List<String>> = flowOf(emptyList())
}

private fun String.compactSearchSnippet(query: String, radius: Int = 88): String {
    val clean = replace(Regex("\\s+"), " ").trim()
    if (clean.length <= radius * 2) return clean
    val matchIndex = clean.indexOf(query, ignoreCase = true)
    if (matchIndex < 0) return clean.take(radius * 2).trimEnd() + "..."
    val start = max(0, matchIndex - radius)
    val end = min(clean.length, matchIndex + query.length + radius)
    return buildString {
        if (start > 0) append("...")
        append(clean.substring(start, end).trim())
        if (end < clean.length) append("...")
    }
}
