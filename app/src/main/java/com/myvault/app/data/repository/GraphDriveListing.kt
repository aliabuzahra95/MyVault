package com.myvault.app.data.repository

internal data class GraphDriveListingPage(val files: List<GraphDriveFile>, val nextPageToken: String? = null,
    val incompleteSearch: Boolean = false)

/** A caller receives all pages or an exception, never a partial namespace inventory. */
internal suspend fun completeGraphDriveListing(fetch: suspend (String?) -> GraphDriveListingPage): List<GraphDriveFile> {
    val files = linkedMapOf<String, GraphDriveFile>()
    val tokens = mutableSetOf<String>()
    var token: String? = null
    do {
        val page = fetch(token)
        check(!page.incompleteSearch) { "Drive returned an incomplete backup listing. Nothing was applied." }
        for (file in page.files) {
            BackupGraphProtocol.id(file.id)
            check(files.put(file.id, file) == null) { "Drive returned a duplicate backup object across listing pages. Retry discovery." }
        }
        token = page.nextPageToken
        if (token != null) check(token.isNotBlank() && tokens.add(token)) { "Drive backup pagination did not advance. Nothing was applied." }
    } while (token != null)
    return files.values.toList()
}
