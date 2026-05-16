package com.myvault.app.ui.navigation

import android.net.Uri

sealed class VaultDestination(val route: String, val label: String) {
    data object Home : VaultDestination("home", "Home")
    data object FolderView : VaultDestination("folder/{folderId}", "Folder View") {
        fun route(folderId: String) = "folder/$folderId"
    }
    data object LibraryFolder : VaultDestination("library-folder/{libraryFolderId}", "Library Folder") {
        fun route(folderId: String) = "library-folder/$folderId"
    }
    data object Editor : VaultDestination("editor/{noteId}", "Editor") {
        fun route(noteId: String) = "editor/$noteId"
    }
    data object Reading : VaultDestination("reading/{noteId}", "Reading") {
        fun route(noteId: String) = "reading/$noteId"
    }
    data object AskAi : VaultDestination("ask-ai/{noteId}?selectedText={selectedText}", "Ask AI") {
        fun route(noteId: String, selectedText: String? = null): String {
            val encoded = Uri.encode(selectedText.orEmpty())
            return "ask-ai/$noteId?selectedText=$encoded"
        }
    }
    data object AttachmentViewer : VaultDestination("attachment/{attachmentId}?page={page}", "Attachment Viewer") {
        fun route(attachmentId: String, pageIndex: Int? = null) =
            "attachment/$attachmentId?page=${pageIndex ?: -1}"
    }
    data object Search : VaultDestination("search", "Search")
    data object Attachments : VaultDestination("attachments", "Attachments")
    data object Settings : VaultDestination("settings", "Settings")
}
