package com.myvault.app.ui.screens

import androidx.compose.ui.graphics.Color
import com.myvault.app.ui.components.EditorBlock
import com.myvault.app.ui.components.EditorBlockType
import com.myvault.app.ui.components.SearchResultData
import com.myvault.app.ui.components.VaultNoteCardData

data class FolderNoteSample(
    val id: String,
    val title: String,
    val meta: String,
    val pinned: Boolean = false,
    val favourite: Boolean = false,
    val preview: String = "",
)

data class FolderCardSample(val id: String, val title: String, val count: String)

data class AttachmentSample(
    val name: String,
    val note: String,
    val size: String,
    val date: String,
    val kind: String,
    val id: String = "",
    val noteId: String = "",
    val mimeType: String = "application/octet-stream",
    val localPath: String = "",
)

object Pass4Samples {
    val folderCards = listOf(
        FolderCardSample("aqeedah", "Aqeedah", "9 notes"),
        FolderCardSample("fiqh", "Fiqh", "7 notes"),
        FolderCardSample("tafsir", "Tafsir Notes", "8 notes"),
    )

    val folderNotes = listOf(
        FolderNoteSample("wasit", "Al-Wasitiyyah Notes", "Edited 5h ago · 2 attachments", pinned = true),
        FolderNoteSample("usool", "Usool al-Thalatha", "Edited 3d ago", favourite = true),
        FolderNoteSample("names", "Names and Attributes", "Edited 1w ago · 1 attachment"),
    )

    val editorTags = listOf("aqeedah", "class", "review")

    val noteBlocks = listOf(
        EditorBlock(EditorBlockType.Heading, "Names and Attributes"),
        EditorBlock(
            EditorBlockType.Paragraph,
            "These notes collect the main points from the current study plan, with emphasis on clarity, evidence, and review.",
        ),
        EditorBlock(EditorBlockType.Quote, "Affirm what Allah affirmed for Himself without distortion or denial."),
        EditorBlock(EditorBlockType.Checklist, "Review chapter summary before next lesson", checked = true),
        EditorBlock(EditorBlockType.Divider),
        EditorBlock(EditorBlockType.Attachment, "aqeedah-summary.pdf", "2.4 MB · PDF"),
        EditorBlock(EditorBlockType.Image, "Whiteboard diagram", "Lesson diagram", tint = Color(0xFF6FB78A)),
    )

    val relatedNotes = listOf(
        VaultNoteCardData("Usool al-Thalatha", "Aqeedah"),
        VaultNoteCardData("Names and Attributes", "Aqeedah"),
    )

    val searchResults = listOf(
        SearchResultData(
            title = "Al-Wasitiyyah Notes",
            snippet = "Names and Attributes appear throughout the current study plan.",
            folder = "Islamic Studies / Aqeedah",
        ),
        SearchResultData(
            title = "Current Study Plan",
            snippet = "Review Attributes before the next lesson and update checklist items.",
            folder = "Islamic Studies",
        ),
        SearchResultData(
            title = "Usool al-Thalatha",
            snippet = "Short summary and revision schedule for the week.",
            folder = "Islamic Studies / Aqeedah",
        ),
    )

    val attachments = listOf(
        AttachmentSample("aqeedah-summary.pdf", "Al-Wasitiyyah Notes", "2.4 MB", "Today", "PDF"),
        AttachmentSample("blood-review.jpg", "Blood Test Review", "740 KB", "Yesterday", "Image"),
        AttachmentSample("bread-orders.xlsx", "Weekly Bread Orders", "86 KB", "Apr 24", "Doc"),
        AttachmentSample("lesson-audio.m4a", "Class Notes", "12 MB", "Apr 21", "Audio"),
    )
}
