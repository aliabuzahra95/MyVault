package com.myvault.app.ui.navigation

sealed class VaultDestination(val route: String, val label: String) {
    // Keep the established route string so restored navigation state remains compatible.
    data object Knowledge : VaultDestination("home", "Knowledge")
    data object Dashboard : VaultDestination("dashboard", "Dashboard")
    data object FolderView : VaultDestination("folder/{folderId}", "Folder View") {
        fun route(folderId: String) = "folder/$folderId"
    }
    data object LibraryFolder : VaultDestination("library-folder/{libraryFolderId}?libraryMode={libraryMode}", "Library Folder") {
        fun route(folderId: String, libraryMode: String = "library") = "library-folder/$folderId?libraryMode=$libraryMode"
    }
    data object Editor : VaultDestination("editor/{noteId}?quickFocus={quickFocus}", "Editor") {
        fun route(noteId: String, quickFocus: Boolean = false) = "editor/$noteId?quickFocus=$quickFocus"
    }
    data object Reading : VaultDestination("reading/{noteId}", "Reading") {
        fun route(noteId: String) = "reading/$noteId"
    }
    data object AttachmentViewer : VaultDestination("attachment/{attachmentId}?page={page}", "Attachment Viewer") {
        fun route(attachmentId: String, pageIndex: Int? = null) =
            "attachment/$attachmentId?page=${pageIndex ?: -1}"
    }
    data object Search : VaultDestination("search", "Search")
    data object Favourites : VaultDestination("favourites", "Favourites")
    data object QuranReflections : VaultDestination("quran-reflections", "Qur'an Reflections")
    data object Attachments : VaultDestination("attachments/{mode}", "Attachments") {
        fun route(mode: String) = "attachments/$mode"
    }
    data object Settings : VaultDestination("settings", "Settings")
    data object PdfActivityFeed : VaultDestination("pdf-activity-feed?libraryMode={libraryMode}", "Activity Feed") {
        fun route(libraryMode: String = "library") = "pdf-activity-feed?libraryMode=$libraryMode"
    }
}
