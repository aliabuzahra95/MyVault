package com.myvault.app.data.local

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FolderEntity
import java.util.Locale

internal data class LibraryOrderSeed(val id: String, val parentId: String?, val name: String, val orderIndex: Int?)

/** Missing order is appended, never substituted with activity or content timestamps. */
internal fun seedMissingLibraryOrders(files: List<LibraryOrderSeed>, folderMaxima: Map<String?, Int>): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    files.groupBy { it.parentId }.forEach { (parent, siblings) ->
        var next = maxOf(folderMaxima[parent] ?: -1, siblings.mapNotNull { it.orderIndex }.maxOrNull() ?: -1).toLong() + 1
        siblings.filter { it.orderIndex == null }
            .sortedWith(compareBy<LibraryOrderSeed> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
            .forEach {
                require(next <= Int.MAX_VALUE) { "Library order exceeds supported range" }
                result[it.id] = next++.toInt()
            }
    }
    return result
}

internal fun List<AttachmentEntity>.withSeededLibraryOrder(folders: List<FolderEntity>): List<AttachmentEntity> {
    val maxima = folders.filter { it.mode == "library" || it.mode == "personal_library" }
        .groupBy { it.parentId }.mapValues { (_, rows) -> rows.maxOf { it.orderIndex } }
    val seeds = filter { it.noteId.isBlank() || it.libraryFolderId != null }
        .map { LibraryOrderSeed(it.id, it.libraryFolderId, it.fileName, it.orderIndex) }
    val assigned = seedMissingLibraryOrders(seeds, maxima)
    return map { file -> assigned[file.id]?.let { file.copy(orderIndex = it) } ?: file }
}

// Leave ample headroom for deterministic appends, including malformed legacy input.
internal fun optionalLibraryOrder(value: Any?): Int? = (value as? Number)?.toDouble()?.let {
    if (it.isFinite() && it >= 0 && it <= 1_000_000_000 && it == kotlin.math.floor(it)) it.toInt() else null
}
