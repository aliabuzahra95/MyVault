package com.myvault.app.ui.screens

import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType

object HomeSampleData {
    val pinnedNotes = listOf(
        VaultNoteCardData("Current Study Plan", "Islamic Studies"),
        VaultNoteCardData("Important Health Notes", "Health"),
        VaultNoteCardData("Weekly Bread Orders", "Business"),
    )

    val recentNotes = listOf(
        VaultNoteCardData("TRT Notes", "Edited 2h ago"),
        VaultNoteCardData("Al-Wasitiyyah", "Edited 5h ago"),
        VaultNoteCardData("Bread Customers", "Edited 1d ago"),
        VaultNoteCardData("Blood Test Review", "Edited 2d ago"),
        VaultNoteCardData("Peptide Stack", "Edited 3d ago"),
    )

    val workspace = listOf(
        VaultTreeItem(
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
                        VaultTreeItem(
                            id = "usool",
                            name = "Usool al-Thalatha",
                            type = VaultTreeItemType.Note,
                            edited = "3d",
                        ),
                        VaultTreeItem(
                            id = "names",
                            name = "Names and Attributes",
                            type = VaultTreeItemType.Note,
                            edited = "1w",
                            attachmentCount = 1,
                        ),
                    ),
                ),
                VaultTreeItem("fiqh", "Fiqh", VaultTreeItemType.Folder, count = 7),
                VaultTreeItem("tafsir", "Tafsir Notes", VaultTreeItemType.Folder, count = 8),
            ),
        ),
        VaultTreeItem(
            id = "health",
            name = "Health",
            type = VaultTreeItemType.Folder,
            count = 14,
            children = listOf(
                VaultTreeItem("blood", "Blood Tests", VaultTreeItemType.Folder, count = 4),
                VaultTreeItem("trt", "TRT Notes", VaultTreeItemType.Folder, count = 6),
                VaultTreeItem("peptides", "Peptides", VaultTreeItemType.Folder, count = 3),
                VaultTreeItem("back", "Back Pain Journal", VaultTreeItemType.Note, edited = "1d"),
            ),
        ),
        VaultTreeItem(
            id = "business",
            name = "Business",
            type = VaultTreeItemType.Folder,
            count = 18,
            favourite = true,
            children = listOf(
                VaultTreeItem("bread", "Bread Deliveries", VaultTreeItemType.Folder, count = 5),
                VaultTreeItem("cust", "Customers", VaultTreeItemType.Folder, count = 4),
                VaultTreeItem("inv", "Invoices", VaultTreeItemType.Folder, count = 6),
                VaultTreeItem("exp", "Expenses", VaultTreeItemType.Folder, count = 3),
            ),
        ),
        VaultTreeItem(
            id = "personal",
            name = "Personal",
            type = VaultTreeItemType.Folder,
            count = 11,
            children = listOf(
                VaultTreeItem("family", "Family", VaultTreeItemType.Folder, count = 3),
                VaultTreeItem("ideas", "Ideas", VaultTreeItemType.Folder, count = 5),
                VaultTreeItem("plans", "Plans", VaultTreeItemType.Folder, count = 3),
            ),
        ),
    )
}
