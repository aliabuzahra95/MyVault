package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.Breadcrumb
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.data.repository.NoteAiAction
import com.myvault.app.data.repository.NoteAiModel
import com.myvault.app.data.repository.NoteAiProvider
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.SourceReferenceCard
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.NoteAiUiState
import com.myvault.app.ui.viewmodel.NoteUiState
import com.myvault.app.ui.viewmodel.NoteTableUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReadingScreen(
    uiState: NoteUiState,
    aiState: NoteAiUiState = NoteAiUiState(),
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAttachmentClick: (String) -> Unit = {},
    onPinnedChange: (Boolean) -> Unit = {},
    onFavouriteChange: (Boolean) -> Unit = {},
    onRunAiTool: (action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel, title: String, body: String, question: String) -> Unit = { _, _, _, _, _, _ -> },
    onClearAiConversation: () -> Unit = {},
    onAiProviderSelected: (NoteAiProvider) -> Unit = {},
    onAiModelSelected: (NoteAiModel) -> Unit = {},
    onAiQuestionChange: (String) -> Unit = {},
    onAskAiClick: () -> Unit = {},
    onDeleteNote: () -> Unit = {},
    onExportText: (Uri) -> Unit = {},
    onExportPdf: (Uri) -> Unit = {},
    onNoteLinkClick: (String) -> Unit = {},
    onSourceReferenceClick: (String, Int) -> Unit = { _, _ -> },
    onRemoveSourceReference: (String) -> Unit = {},
    onAddKnowledgeTag: (String) -> Unit = {},
    onRemoveKnowledgeTag: (String) -> Unit = {},
    bodyFontSizeSp: Float = 15f,
) {
    val colors = VaultThemeTokens.colors
    val note = uiState.note
    val attachmentCount = uiState.attachments.size
    val isPinned = note?.isPinned == true
    val isFavourite = note?.isFavourite == true
    var moreMenuOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var tagDialogOpen by remember { mutableStateOf(false) }
    var removeTagDialogOpen by remember { mutableStateOf(false) }
    var sourceReferenceToRemove by remember { mutableStateOf<SourceReferenceCard?>(null) }
    var tagDraft by remember { mutableStateOf("") }
    val exportTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(onExportText)
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(onExportPdf)
    }
    val breadcrumbItems = remember(uiState.folderPath, note?.title) {
        buildList {
            add("My Vault")
            addAll(uiState.folderPath)
            add(note?.title?.takeIf { it.isNotBlank() } ?: "Untitled note")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onEditClick,
                modifier = Modifier.size(52.dp),
                shape = VaultShapes.md,
                containerColor = colors.accent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.Edit, "Edit", modifier = Modifier.size(20.dp))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            item {
                ScreenTopBar(onBackClick = onBackClick) {
                    IconBtn(
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = "Ask AI",
                        onClick = onAskAiClick,
                    )
                    IconBtn(
                        icon = Icons.Rounded.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        active = isPinned,
                        onClick = { onPinnedChange(!isPinned) },
                    )
                    IconBtn(
                        icon = Icons.Rounded.Star,
                        contentDescription = if (isFavourite) "Unfavourite" else "Favourite",
                        active = isFavourite,
                        onClick = { onFavouriteChange(!isFavourite) },
                    )
                    IconBtn(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        onClick = { moreMenuOpen = true },
                    )
                }
            }
            item {
                Breadcrumb(breadcrumbItems, modifier = Modifier.padding(horizontal = VaultSpacing.screen))
            }
            item {
                Column(modifier = Modifier.padding(horizontal = VaultSpacing.screen), verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                    Text(note?.title ?: "Untitled note", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(13.dp), tint = colors.textMuted)
                        Text(
                            text = listOfNotNull(
                                note?.updatedAt?.toRelativeTime()?.let { "Edited $it" },
                                attachmentCount.takeIf { it > 0 }?.let { "$it attachment${if (it == 1) "" else "s"}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                    if (uiState.knowledgeTags.isNotEmpty()) {
                        KnowledgeTagRow(
                            tags = uiState.knowledgeTags,
                        )
                    }
                }
            }
            item {
                RichNoteBody(
                    html = uiState.richHtml,
                    fallbackText = note?.bodyPlainText.orEmpty(),
                    richText = uiState.richText,
                    onNoteLinkClick = onNoteLinkClick,
                    onDoubleTapEdit = onEditClick,
                    bodyFontSizeSp = bodyFontSizeSp,
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                )
            }
            if (uiState.tables.isNotEmpty()) {
                item { SectionLabel(label = "Tables") }
                items(uiState.tables, key = { it.id }) { table ->
                    ReadOnlyNoteTable(table = table)
                }
            }
            if (uiState.attachments.isNotEmpty()) {
                item { SectionLabel(label = "Attachments") }
                items(uiState.attachments, key = { it.id }) { attachment ->
                    AttachmentReadingPreview(attachment = attachment, onClick = { onAttachmentClick(attachment.id) })
                }
            }
            if (uiState.sourceReferences.isNotEmpty()) {
                item { SectionLabel(label = "Sources used") }
                items(uiState.sourceReferences.take(3), key = { it.id }) { source ->
                    SourceReferenceCardRow(
                        source = source,
                        onClick = { onSourceReferenceClick(source.attachmentId, source.pageIndex) },
                        onLongPress = { sourceReferenceToRemove = source },
                    )
                }
                if (uiState.sourceReferences.size > 3) {
                    item {
                        Text(
                            text = "+${uiState.sourceReferences.size - 3} more sources",
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            if (uiState.backlinks.isNotEmpty()) {
                item { SectionLabel(label = "Linked from:") }
                items(uiState.backlinks, key = { it.id }) { backlink ->
                    BacklinkCard(title = backlink.title, preview = backlink.preview, onClick = { onNoteLinkClick(backlink.id) })
                }
            }
        }
    }

    if (moreMenuOpen) {
        AlertDialog(
            onDismissRequest = { moreMenuOpen = false },
            confirmButton = {},
            title = {
                Text(
                    text = note?.title?.takeIf { it.isNotBlank() } ?: "Note actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    ReadingActionRow("Edit note", Icons.Rounded.Edit) {
                        moreMenuOpen = false
                        onEditClick()
                    }
                    ReadingActionRow(if (isPinned) "Unpin note" else "Pin note", Icons.Rounded.PushPin) {
                        onPinnedChange(!isPinned)
                        moreMenuOpen = false
                    }
                    ReadingActionRow(if (isFavourite) "Remove favourite" else "Add favourite", Icons.Rounded.Star) {
                        onFavouriteChange(!isFavourite)
                        moreMenuOpen = false
                    }
                    ReadingActionRow("Export as TXT", Icons.Rounded.FileDownload) {
                        moreMenuOpen = false
                        exportTextLauncher.launch("${note?.title?.toSafeFileName() ?: "note"}.txt")
                    }
                    ReadingActionRow("Export as PDF", Icons.Rounded.FileDownload) {
                        moreMenuOpen = false
                        exportPdfLauncher.launch("${note?.title?.toSafeFileName() ?: "note"}.pdf")
                    }
                    ReadingActionRow("Add/Edit Tags", Icons.Rounded.LocalOffer) {
                        moreMenuOpen = false
                        tagDraft = ""
                        tagDialogOpen = true
                    }
                    if (uiState.knowledgeTags.isNotEmpty()) {
                        ReadingActionRow("Remove Tag", Icons.Rounded.LocalOffer) {
                            moreMenuOpen = false
                            removeTagDialogOpen = true
                        }
                    }
                    ReadingActionRow("Delete note", Icons.Rounded.Delete, destructive = true) {
                        moreMenuOpen = false
                        deleteDialogOpen = true
                    }
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Move note to Recently Deleted?") },
            text = { Text("You can restore this note from Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteDialogOpen = false
                        onDeleteNote()
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }

    if (tagDialogOpen) {
        AlertDialog(
            onDismissRequest = { tagDialogOpen = false },
            title = { Text("Add tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                    if (uiState.knowledgeTags.isNotEmpty()) {
                        KnowledgeTagRow(tags = uiState.knowledgeTags)
                    }
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        singleLine = true,
                        label = { Text("Tag") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddKnowledgeTag(tagDraft)
                        tagDraft = ""
                        tagDialogOpen = false
                    },
                    enabled = tagDraft.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagDialogOpen = false }) {
                    Text("Close")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (removeTagDialogOpen) {
        AlertDialog(
            onDismissRequest = { removeTagDialogOpen = false },
            title = { Text("Remove tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    uiState.knowledgeTags.forEach { tag ->
                        ReadingActionRow(tag.name, Icons.Rounded.LocalOffer, destructive = true) {
                            onRemoveKnowledgeTag(tag.id)
                            removeTagDialogOpen = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { removeTagDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    sourceReferenceToRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceReferenceToRemove = null },
            title = { Text("Remove source reference?") },
            text = {
                Text(
                    "This only unlinks the reference from this note. It will not delete the note, PDF, or annotation.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveSourceReference(source.id)
                        sourceReferenceToRemove = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceReferenceToRemove = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }
}

private fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }

@Composable
private fun KnowledgeTagRow(
    tags: List<KnowledgeTagChip>,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.take(4).forEach { tag ->
            Surface(
                color = colors.inset,
                shape = VaultShapes.pill,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = tag.name,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (tags.size > 4) {
            Text(
                text = "+${tags.size - 4}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceReferenceCardRow(
    source: SourceReferenceCard,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = colors.accentSoft,
                shape = VaultShapes.sm,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.accent)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                    color = if (source.unavailable) colors.textMuted else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        source.unavailable -> "Source unavailable"
                        source.annotationDeleted -> "Page ${source.pageIndex + 1} · annotation deleted"
                        else -> "Page ${source.pageIndex + 1}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ArrowOutward, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.textMuted)
        }
    }
}

@Composable
private fun ReadingActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = colors.accentSoft,
                shape = VaultShapes.sm,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (destructive) colors.warning else colors.accent,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = if (destructive) colors.warning else colors.text,
            )
        }
    }
}

@Composable
private fun RichNoteBody(
    html: String,
    fallbackText: String,
    richText: VaultRichTextDocument,
    onNoteLinkClick: (String) -> Unit,
    onDoubleTapEdit: () -> Unit,
    bodyFontSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val bodyText = richText.text.ifBlank { fallbackText.ifBlank { html.stripHtml() } }

    if (bodyText.isBlank()) {
        Text(
            text = "No body text yet.",
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = bodyFontSizeSp.sp),
            color = colors.textMuted,
        )
    } else {
        val annotated = buildVaultAnnotatedString(bodyText, richText.styleMarks, richText.noteLinks, colors)
        SelectionContainer(
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTapEdit() })
                },
        ) {
            ClickableText(
                text = annotated,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(color = colors.text, fontSize = bodyFontSizeSp.sp),
                onClick = { offset ->
                    annotated.getStringAnnotations("noteLink", offset, offset).firstOrNull()?.let {
                        onNoteLinkClick(it.item)
                    }
                },
            )
        }
    }
}

@Composable
private fun BacklinkCard(title: String, preview: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text)
            if (preview.isNotBlank()) {
                Text(preview, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>|</h[1-6]>|</li>|</tr>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

@Composable
private fun ReadOnlyNoteTable(table: NoteTableUiState) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(table.rowCount) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(table.columnCount) { column ->
                        val value = table.cells.getOrNull(row)?.getOrNull(column).orEmpty()
                        Surface(
                            modifier = Modifier.width(116.dp),
                            color = colors.elevated,
                            shape = VaultShapes.sm,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Text(
                                text = value.ifBlank { " " },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentReadingPreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    when {
        attachment.mimeType.startsWith("image/") -> ReadingImageAttachmentPreview(attachment = attachment, onClick = onClick)
        attachment.mimeType == "application/pdf" -> ReadingPdfAttachmentPreview(attachment = attachment, onClick = onClick)
        else -> AttachmentReadingCard(attachment = attachment, onClick = onClick)
    }
}

@Composable
private fun ReadingImageAttachmentPreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, attachment.localPath) {
        value = withContext(Dispatchers.IO) { decodeReadingPreviewBitmap(attachment.localPath, maxSize = 1400) }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            val loadedBitmap = bitmap
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 48.dp)
                }
            }
            AttachmentReadingCaption(attachment)
        }
    }
}

@Composable
private fun ReadingPdfAttachmentPreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 96.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${attachment.sizeLabel()} · PDF preview", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = colors.accent)
            }
            Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.size(18.dp), tint = colors.textSecondary)
        }
    }
}

@Composable
private fun AttachmentReadingCaption(attachment: AttachmentEntity) {
    val colors = VaultThemeTokens.colors
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${attachment.sizeLabel()} · Tap to open", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

@Composable
private fun AttachmentReadingCard(attachment: AttachmentEntity, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kindLabel(), size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text)
                Text("${attachment.sizeLabel()} · ${attachment.kindLabel()}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.size(18.dp), tint = colors.textSecondary)
        }
    }
}

private fun decodeReadingPreviewBitmap(localPath: String, maxSize: Int): Bitmap? =
    runCatching {
        val file = File(localPath)
        if (!file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }.getOrNull()
