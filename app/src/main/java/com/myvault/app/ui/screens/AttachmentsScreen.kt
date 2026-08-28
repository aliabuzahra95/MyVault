package com.myvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun AttachmentsScreen(
    attachments: List<AttachmentSample>,
    onMenuClick: () -> Unit,
    onAttachmentClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Workspace Attachments", "Across Study and Library", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
        ) {
            item {
                Text(
                    "Files and images attached across your workspace.",
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                )
            }
            if (attachments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.65f).fillParentMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.AttachFile, null, tint = colors.textMuted)
                        Text("No attachments", modifier = Modifier.padding(top = 7.dp), fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
                        Text("Files attached to notes will appear here.", fontSize = 10.5.sp, color = colors.textMuted)
                    }
                }
            } else {
                items(attachments, key = { it.id.ifBlank { it.name } }) { attachment ->
                    DashboardRow(
                        title = attachment.name,
                        meta = attachment.note.ifBlank { attachment.kind },
                        icon = when (attachment.kind) {
                            "PDF" -> Icons.Rounded.PictureAsPdf
                            "Image" -> Icons.Rounded.Image
                            else -> Icons.Rounded.AttachFile
                        },
                        onClick = { if (attachment.id.isNotBlank()) onAttachmentClick(attachment.id) },
                        titleFontSize = 13.sp,
                        metaFontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}
