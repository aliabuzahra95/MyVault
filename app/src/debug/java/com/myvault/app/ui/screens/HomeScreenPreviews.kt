package com.myvault.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.model.AttachmentSample
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.viewmodel.HomeUiState

private object HomePreviewData {
    val pinnedNotes = listOf(
        VaultNoteCardData("Current Study Plan", "Islamic Studies"),
        VaultNoteCardData("Important Health Notes", "Health"),
        VaultNoteCardData("Weekly Bread Orders", "Business"),
    )

    val attachments = listOf(
        AttachmentSample("aqeedah-summary.pdf", "Al-Wasitiyyah Notes", "2.4 MB", "Today", "PDF"),
        AttachmentSample("blood-review.jpg", "Blood Test Review", "740 KB", "Yesterday", "Image"),
        AttachmentSample("bread-orders.xlsx", "Weekly Bread Orders", "86 KB", "Apr 24", "Doc"),
        AttachmentSample("lesson-audio.m4a", "Class Notes", "12 MB", "Apr 21", "Audio"),
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

@Preview(name = "HomeScreen Light")
@Composable
private fun HomeScreenLightPreview() {
    HomeScreenPreview(VaultThemeMode.Light)
}

@Preview(name = "HomeScreen Dark")
@Composable
private fun HomeScreenDarkPreview() {
    HomeScreenPreview(VaultThemeMode.Dark)
}

@Composable
private fun HomeScreenPreview(mode: VaultThemeMode) {
    VaultTheme(mode = mode) {
        HomeScreen(
            uiState = HomeUiState(
                pinnedNotes = HomePreviewData.pinnedNotes,
                attachments = HomePreviewData.attachments,
                workspace = HomePreviewData.workspace,
            ),
            onSearchClick = {},
            onSettingsClick = {},
            onFolderClick = {},
            onNoteClick = {},
        )
    }
}
