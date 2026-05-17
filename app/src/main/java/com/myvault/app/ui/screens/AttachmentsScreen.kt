package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.ui.components.AttachmentKind
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.AttachmentTypeChip
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.util.openAttachment

@Composable
fun AttachmentsScreen(
    attachments: List<AttachmentSample>,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAttachmentClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    var selectedKind by remember { mutableStateOf(AttachmentKind.All) }
    val visibleAttachments = remember(attachments, selectedKind) {
        if (selectedKind == AttachmentKind.All) {
            attachments
        } else {
            attachments.filter { it.kind == selectedKind.storageKind() }
        }
    }
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.bg) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = VaultSpacing.huge),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            item {
                ScreenTopBar(onBackClick = onBackClick) {
                    IconBtn(Icons.Rounded.Search, "Search", onClick = onSearchClick)
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = VaultSpacing.screen)) {
                    Text("Attachments", style = MaterialTheme.typography.displayMedium, color = colors.text)
                    Text("${visibleAttachments.size} files across your workspace", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = VaultSpacing.screen), horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    items(AttachmentKind.entries, key = { it.name }) { kind ->
                        AttachmentTypeChip(
                            kind = kind,
                            selected = kind == selectedKind,
                            onClick = { selectedKind = kind },
                        )
                    }
                }
            }
            if (visibleAttachments.isEmpty()) {
                item {
                    Text(
                        text = "No attachments in this category",
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            } else {
                items(visibleAttachments, key = { item -> item.id.ifBlank { item.name } }) { attachment ->
                    AttachmentCard(
                        attachment = attachment,
                        onClick = {
                            if (attachment.id.isNotBlank()) {
                                onAttachmentClick(attachment.id)
                            } else {
                                attachment.toEntityOrNull()?.let { openAttachment(context, it) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentCard(attachment: AttachmentSample, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    val iconData = attachmentIcon(attachment.kind, colors)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kind, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Link, null, modifier = Modifier.size(11.dp), tint = colors.textMuted)
                    Text(attachment.note, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(attachment.size, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text(attachment.date, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun attachmentIcon(kind: String, colors: com.myvault.app.ui.theme.VaultColors): Pair<ImageVector, Color> = when (kind) {
    "PDF" -> Icons.Rounded.PictureAsPdf to colors.warning
    "Audio" -> Icons.Rounded.Audiotrack to colors.accent
    "Image" -> Icons.Rounded.Image to colors.success
    "Doc" -> Icons.AutoMirrored.Rounded.Article to colors.textSecondary
    else -> Icons.Rounded.AttachFile to colors.textSecondary
}

private fun AttachmentSample.toEntityOrNull(): AttachmentEntity? =
    if (id.isBlank() || noteId.isBlank() || localPath.isBlank()) {
        null
    } else {
        AttachmentEntity(
            id = id,
            noteId = noteId,
            fileName = name,
            mimeType = mimeType,
            sizeBytes = 0L,
            localPath = localPath,
            remoteUrl = null,
            createdAt = 0L,
        )
    }

private fun AttachmentKind.storageKind(): String = when (this) {
    AttachmentKind.All -> "All"
    AttachmentKind.Image -> "Image"
    AttachmentKind.Pdf -> "PDF"
    AttachmentKind.Audio -> "Audio"
    AttachmentKind.Doc -> "Doc"
}
