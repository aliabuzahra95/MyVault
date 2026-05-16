package com.myvault.app.ui.components

import androidx.compose.ui.graphics.Color

object ComponentSamples {
    val pinnedNote = VaultNoteCardData(
        title = "Current Study Plan",
        meta = "Islamic Studies",
    )

    val recentNote = VaultNoteCardData(
        title = "Al-Wasitiyyah",
        meta = "Edited 5h ago",
    )

    val tree = VaultTreeItem(
        id = "islamic",
        name = "Islamic Studies",
        type = VaultTreeItemType.Folder,
        count = 24,
        favourite = true,
        children = listOf(
            VaultTreeItem(
                id = "aqeedah",
                name = "Aqeedah",
                type = VaultTreeItemType.Folder,
                count = 9,
                children = listOf(
                    VaultTreeItem(
                        id = "wasit",
                        name = "Al-Wasitiyyah Notes",
                        type = VaultTreeItemType.Note,
                        edited = "5h",
                        attachmentCount = 2,
                    ),
                ),
            ),
        ),
    )

    val blocks = listOf(
        EditorBlock(EditorBlockType.Heading, "Key principles"),
        EditorBlock(
            EditorBlockType.Paragraph,
            "A calm vault keeps the writing surface focused and lets structure do the quiet work.",
        ),
        EditorBlock(EditorBlockType.Quote, "Knowledge is preserved by writing it down."),
        EditorBlock(EditorBlockType.Checklist, "Review notes after class", checked = true),
        EditorBlock(EditorBlockType.Divider),
        EditorBlock(
            EditorBlockType.Attachment,
            primary = "aqeedah-summary.pdf",
            secondary = "2.4 MB · PDF",
        ),
        EditorBlock(
            EditorBlockType.Image,
            primary = "Diagram preview",
            secondary = "Class whiteboard",
            tint = Color(0xFF6FB78A),
        ),
    )

    val blockMenuItems = listOf(
        BlockMenuItem(EditorBlockType.Paragraph, "Paragraph", "Write plain note text"),
        BlockMenuItem(EditorBlockType.Heading, "Heading", "Create a section title"),
        BlockMenuItem(EditorBlockType.BulletList, "Bullet list", "Capture grouped points"),
        BlockMenuItem(EditorBlockType.NumberedList, "Numbered list", "Create ordered steps"),
        BlockMenuItem(EditorBlockType.Checklist, "Checklist", "Track tasks inside a note"),
        BlockMenuItem(EditorBlockType.Quote, "Quote", "Highlight a reference or reminder"),
        BlockMenuItem(EditorBlockType.Divider, "Divider", "Separate sections"),
        BlockMenuItem(EditorBlockType.Link, "Link", "Attach a web reference"),
        BlockMenuItem(EditorBlockType.Attachment, "Attachment", "Add a document or file"),
        BlockMenuItem(EditorBlockType.Image, "Image", "Insert an image block"),
    )

    val searchResult = SearchResultData(
        title = "Al-Wasitiyyah Notes",
        snippet = "Names and Attributes appear throughout the current study plan.",
        folder = "Islamic Studies / Aqeedah",
    )
}
