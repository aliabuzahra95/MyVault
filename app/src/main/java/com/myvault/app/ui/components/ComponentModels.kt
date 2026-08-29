package com.myvault.app.ui.components

import androidx.compose.ui.graphics.Color

data class VaultNoteCardData(
    val title: String,
    val meta: String,
    val id: String = title,
    val tableCount: Int = 0,
    val preview: String = "",
)

data class VaultTreeItem(
    val id: String,
    val name: String,
    val type: VaultTreeItemType,
    val description: String? = null,
    val orderIndex: Int = 0,
    val count: Int = 0,
    val edited: String? = null,
    val updatedAt: Long = 0L,
    val attachmentCount: Int = 0,
    val tableCount: Int = 0,
    val pinned: Boolean = false,
    val folderPinned: Boolean = false,
    val favourite: Boolean = false,
    val colorKey: String? = null,
    val preview: String = "",
    val children: List<VaultTreeItem> = emptyList(),
)

enum class VaultTreeItemType {
    Folder,
    Note,
}

enum class AttachmentKind(val label: String) {
    All("All"),
    Image("Images"),
    Pdf("PDFs"),
    Audio("Audio"),
    Doc("Docs"),
}

data class EditorBlock(
    val type: EditorBlockType,
    val primary: String = "",
    val secondary: String = "",
    val checked: Boolean = false,
    val tint: Color? = null,
    val id: String = "",
)

enum class EditorBlockType {
    Paragraph,
    Heading,
    Quote,
    Checklist,
    Divider,
    Attachment,
    Image,
    BulletList,
    NumberedList,
    Link,
}

data class BlockMenuItem(
    val type: EditorBlockType,
    val title: String,
    val description: String,
)

data class SearchResultData(
    val title: String,
    val snippet: String,
    val folder: String,
    val id: String = title,
)
