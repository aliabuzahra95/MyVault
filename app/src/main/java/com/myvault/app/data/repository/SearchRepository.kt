package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.ui.components.SearchResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val searchDao: SearchDao,
    private val tagDao: TagDao,
) {
    fun searchNotes(query: String): Flow<List<SearchResultData>> {
        if (query.isBlank()) return flowOf(emptyList())
        val normalizedQuery = query.trim()
        val ftsQuery = normalizedQuery.toFtsQuery()
        val noteResults = if (ftsQuery != null) {
            searchDao.searchNotes(ftsQuery, limit = 40)
        } else {
            searchDao.searchActiveNotes(normalizedQuery.toLikePattern(), limit = 40)
        }
        return noteResults
            .map { notes ->
                notes.map {
                    SearchResultData(
                        title = it.title,
                        snippet = it.bodyPlainText.compactSearchSnippet(normalizedQuery),
                        folder = it.folderName ?: "Unfiled",
                        id = it.id,
                    )
                }
            }
    }

    fun searchFolders(query: String): Flow<List<FolderEntity>> {
        if (query.isBlank()) return flowOf(emptyList())
        return folderDao.searchActive(query.trim().toLikePattern(), limit = 40)
    }

    fun searchTags(query: String): Flow<List<String>> {
        if (query.isBlank()) return flowOf(emptyList())
        return tagDao.observeAll().map { tags ->
            tags
                .asSequence()
                .map { it.name }
                .filter { it.contains(query.trim(), ignoreCase = true) }
                .take(40)
                .toList()
        }
    }
}

private fun String.toLikePattern(): String =
    "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

private fun String.toFtsQuery(): String? {
    val terms = Regex("[\\p{L}\\p{N}]+")
        .findAll(this)
        .map { it.value }
        .filter { it.isNotBlank() }
        .take(8)
        .toList()
    if (terms.isEmpty()) return null
    return terms.joinToString(separator = " ") { "$it*" }
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
