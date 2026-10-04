package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.NoteActionSheet
import com.myvault.app.ui.components.NoteModalActionRow
import com.myvault.app.ui.components.NoteSheetAction
import com.myvault.app.ui.components.NoteSheetSection
import com.myvault.app.ui.components.NoteWorkspaceHeader
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultModal
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.pdfClipSourceOrNull
import com.myvault.app.data.narration.AzureNarrationProgress
import com.myvault.app.data.narration.GeminiNarrationConfig
import com.myvault.app.data.narration.NarrationConfig
import com.myvault.app.data.narration.NarrationProvider
import com.myvault.app.data.narration.NarrationUiState
import com.myvault.app.data.repository.kindLabel
import com.myvault.app.data.repository.sizeLabel
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.SourceReferenceCard
import com.myvault.app.data.repository.NotebookExportConfig
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.NoteUiState
import com.myvault.app.ui.viewmodel.NoteTableUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

@Composable
fun ReadingScreen(
    uiState: NoteUiState,
    narrationState: NarrationUiState = NarrationUiState(),
    azureNarrationProgress: AzureNarrationProgress? = null,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onEditAtAnchor: ((NoteViewportAnchor?) -> Unit)? = null,
    onFormatClick: () -> Unit = onEditClick,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onAttachmentClick: (String) -> Unit = {},
    onAttachDocument: (Uri) -> Unit = {},
    onConfigureAzure: () -> Unit = {},
    onPinnedChange: (Boolean) -> Unit = {},
    onFavouriteChange: (Boolean) -> Unit = {},
    onListenClick: (title: String, body: String, voice: String) -> Unit = { _, _, _ -> },
    onGeminiListenClick: (title: String, body: String, model: String) -> Unit = { _, _, _ -> },
    onAzureListenClick: (title: String, body: String) -> Unit = { _, _ -> },
    onAzureResumeClick: (title: String, body: String) -> Unit = { _, _ -> },
    onDeviceListenClick: (title: String, body: String) -> Unit = { _, _ -> },
    defaultNarrationProvider: String = NarrationProvider.Device.storedValue,
    azureNarrationVoice: String = "",
    onDeleteNote: () -> Unit = {},
    onExportText: (Uri) -> Unit = {},
    onExportPdf: (Uri) -> Unit = {},
    onNotebookExport: (NotebookPrintAction, NotebookExportConfig, Uri?) -> Unit = { _, _, _ -> },
    onNoteLinkClick: (String) -> Unit = {},
    onSourceReferenceClick: (String, Int) -> Unit = { _, _ -> },
    onRemoveSourceReference: (String) -> Unit = {},
    onAddKnowledgeTag: (String) -> Unit = {},
    onRemoveKnowledgeTag: (String) -> Unit = {},
    onRestoreVersion: (String) -> Unit = {},
    bodyFontSizeSp: Float = 15f,
    narrationMiniPlayerVisible: Boolean = false,
    narrationMiniPlayerHeight: Dp = 0.dp,
) {
    val colors = VaultThemeTokens.colors
    val note = uiState.note
    val attachmentCount = uiState.attachments.size
    val isPinned = note?.isPinned == true
    val isFavourite = note?.isFavourite == true
    var moreMenuOpen by remember { mutableStateOf(false) }
    var listenModeOpen by remember { mutableStateOf(false) }
    var noteInfoOpen by remember { mutableStateOf(false) }
    var knowledgeOpen by remember { mutableStateOf(false) }
    var attachmentsOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var notebookPrintOpen by remember { mutableStateOf(false) }
    var notebookConfig by remember { mutableStateOf(NotebookExportConfig()) }
    var notebookDocumentAction by remember { mutableStateOf(NotebookPrintAction.Save) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var versionHistoryOpen by remember { mutableStateOf(false) }
    var versionToRestore by remember { mutableStateOf<String?>(null) }
    var tagDialogOpen by remember { mutableStateOf(false) }
    var removeTagDialogOpen by remember { mutableStateOf(false) }
    var sourceReferenceToRemove by remember { mutableStateOf<SourceReferenceCard?>(null) }
    var tagDraft by remember { mutableStateOf("") }
    var selectedNarrationVoice by remember { mutableStateOf(NarrationConfig.DEFAULT_VOICE) }
    val viewportGesture = rememberNarrationViewportGesture(note?.id)
    var headerVisible by remember(note?.id) { mutableStateOf(true) }
    val readingListState = rememberLazyListState()
    val exportTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(onExportText)
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(onExportPdf)
    }
    val notebookPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { onNotebookExport(notebookDocumentAction, notebookConfig, it) }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachDocument)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachDocument)
    }
    val noteBreadcrumb = remember(uiState.folderPath) {
        uiState.folderPath.joinToString(" / ").ifBlank { "Study" }
    }
    
    val wordCount = remember(uiState.richText.text, note?.bodyPlainText) {
        val text = uiState.richText.text.ifBlank { note?.bodyPlainText.orEmpty() }
        text.split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val charCount = remember(uiState.richText.text, note?.bodyPlainText) {
        val text = uiState.richText.text.ifBlank { note?.bodyPlainText.orEmpty() }
        text.length
    }
    val noteBodyText = uiState.richText.text.ifBlank { note?.bodyPlainText.orEmpty().ifBlank { uiState.richHtml.stripHtml() } }
    val noteBodyChunks = remember(noteBodyText, uiState.richText.styleMarks, uiState.richText.noteLinks) {
        noteBodyText.toReadingBodyChunks(uiState.richText.styleMarks, uiState.richText.noteLinks)
    }
    val readingLayouts = remember(note?.id, noteBodyText) { mutableStateMapOf<Int, ReadingBodyLayout>() }
    val narrationRange = remember(noteBodyText, narrationState.activeSentence, narrationState.activeSentenceSourceOffset,
        narrationState.activeSentenceContext, narrationState.activeSentenceContextOffset, narrationState.noteId) {
        if (narrationState.noteId == note?.id) narrationTextRange(noteBodyText,
            narrationState.activeSentence, narrationState.activeSentenceSourceOffset - note?.title.orEmpty().length,
            narrationState.activeSentenceContext, narrationState.activeSentenceContextOffset) else null
    }
    val playerInsetPx = with(LocalDensity.current) {
        (if (narrationMiniPlayerVisible) narrationMiniPlayerHeight else 0.dp).toPx()
    }
    LaunchedEffect(narrationRange, narrationState.status, viewportGesture.touching, viewportGesture.releasedAtMs, playerInsetPx) {
        val range = narrationRange ?: return@LaunchedEffect
        if (!narrationShouldFollow(narrationState.status, viewportGesture.touching)) return@LaunchedEffect
        kotlinx.coroutines.delay((viewportGesture.releasedAtMs + 300L - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
        val chunk = noteBodyChunks.firstOrNull { range.first in it.start until it.end } ?: return@LaunchedEffect
        val key = "body-${chunk.start}-${chunk.end}"
        if (readingListState.layoutInfo.visibleItemsInfo.none { it.key == key }) {
            val chunkIndex = noteBodyChunks.indexOf(chunk)
            // The toolbar is outside the scroll content; only the title precedes the body.
            readingListState.scrollToItem(chunkIndex + 1)
        }
        val bodyLayout = snapshotFlow { readingLayouts[chunk.start] }.filterNotNull().first()
        val item = readingListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@LaunchedEffect
        val start = bodyLayout.offsetMapping.originalToTransformed(range.first - chunk.start)
        val end = bodyLayout.offsetMapping.originalToTransformed(minOf(range.last, chunk.end - 1) - chunk.start)
        val midpoint = (bodyLayout.layout.getBoundingBox(start).top + bodyLayout.layout.getBoundingBox(end).bottom) / 2f
        val info = readingListState.layoutInfo
        val usable = (info.viewportEndOffset - info.viewportStartOffset - playerInsetPx).coerceAtLeast(1f)
        readingListState.animateScrollBy(item.offset + midpoint - info.viewportStartOffset - usable / 2f)
    }
    val editAtReadingPosition = {
        val visible = readingListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key.toString().startsWith("body-") }
        val chunk = noteBodyChunks.firstOrNull { "body-${it.start}-${it.end}" == visible?.key }
        val layout = chunk?.let { readingLayouts[it.start] }
        val anchor = if (note != null && chunk != null && layout != null && visible != null) {
            val y = (readingListState.layoutInfo.viewportStartOffset - visible.offset).coerceAtLeast(0).toFloat()
            val line = layout.layout.getLineForVerticalPosition(y)
            val top = layout.layout.getLineTop(line)
            val height = (layout.layout.getLineBottom(line) - top).coerceAtLeast(1f)
            NoteViewportAnchor(note.id, noteBodyText.length, noteBodyText.hashCode(),
                chunk.start + layout.offsetMapping.transformedToOriginal(layout.layout.getLineStart(line)),
                ((y - top) / height).coerceIn(0f, 1f))
        } else null
        onEditAtAnchor?.invoke(anchor) ?: onEditClick()
    }
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    val editActionBottomOffset = if (narrationMiniPlayerVisible) {
        narrationMiniPlayerHeight + NarrationEditActionClearance
    } else {
        0.dp
    }
    val noteTitle = note?.title.orEmpty()
    val startDefaultNarration = {
        val provider = NarrationProvider.fromStoredValue(defaultNarrationProvider)
        when (provider) {
            NarrationProvider.GeminiFlashLite -> onGeminiListenClick(noteTitle, noteBodyText, GeminiNarrationConfig.MODEL_FLASH_LITE)
            NarrationProvider.GeminiFlash -> onGeminiListenClick(noteTitle, noteBodyText, GeminiNarrationConfig.MODEL_FLASH)
            NarrationProvider.Device -> onDeviceListenClick(noteTitle, noteBodyText)
            NarrationProvider.OpenAi -> onListenClick(noteTitle, noteBodyText, selectedNarrationVoice)
            NarrationProvider.Azure -> onAzureListenClick(noteTitle, noteBodyText)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().narrationViewportGesture(viewportGesture) { delta ->
            headerVisible = narrationToolbarVisible(headerVisible, delta, true)
        },
        containerColor = colors.bg,
        topBar = {
            androidx.compose.animation.AnimatedVisibility(visible = headerVisible,
                enter = androidx.compose.animation.expandVertically(tween(160)),
                exit = androidx.compose.animation.shrinkVertically(tween(160))) {
                NoteWorkspaceHeader(breadcrumb = noteBreadcrumb, onMenuClick = onMenuClick,
                    modifier = Modifier.testTag("NoteReaderToolbar"),
                    onListenClick = startDefaultNarration, onMoreClick = { moreMenuOpen = true })
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = editAtReadingPosition,
                modifier = Modifier
                    .padding(bottom = editActionBottomOffset)
                    .size(52.dp),
                shape = VaultShapes.md,
                containerColor = colors.accent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.Edit, "Edit", modifier = Modifier.size(20.dp))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = readingListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("NoteReaderScroll")
                ,
            contentPadding = PaddingValues(bottom = 112.dp + if (narrationMiniPlayerVisible) narrationMiniPlayerHeight else 0.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            item {
                Text(
                    text = note?.title ?: "Untitled note",
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                )
            }
            if (noteBodyChunks.isEmpty()) {
                item {
                    RichNoteBody(
                        html = "",
                        fallbackText = "",
                        richText = VaultRichTextDocument("", emptyList(), emptyList()),
                        onNoteLinkClick = onNoteLinkClick,
                        onDoubleTapEdit = editAtReadingPosition,
                        bodyFontSizeSp = bodyFontSizeSp,
                        activeRange = null,
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    )
                }
            } else {
                items(noteBodyChunks, key = { "body-${it.start}-${it.end}" }) { chunk ->
                    androidx.compose.runtime.DisposableEffect(readingLayouts, chunk.start) {
                        onDispose { readingLayouts.remove(chunk.start) }
                    }
                    val localRange = narrationRange?.takeIf { it.first < chunk.end && it.last >= chunk.start }
                        ?.let { maxOf(it.first - chunk.start, 0)..minOf(it.last - chunk.start, chunk.text.lastIndex) }
                RichNoteBody(
                        html = "",
                        fallbackText = chunk.text,
                        richText = chunk.document,
                    onNoteLinkClick = onNoteLinkClick,
                    onDoubleTapEdit = editAtReadingPosition,
                    onLayout = { layout, offsetMapping ->
                        readingLayouts[chunk.start] = ReadingBodyLayout(layout, offsetMapping)
                    },
                    bodyFontSizeSp = bodyFontSizeSp,
                        activeRange = localRange,
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                )
                }
            }
            if (uiState.tables.isNotEmpty()) {
                item { SectionLabel(label = "Tables") }
                items(uiState.tables, key = { it.id }) { table ->
                    ReadOnlyNoteTable(table = table)
                }
            }
            if (uiState.attachmentsLoading || uiState.attachments.isNotEmpty()) {
                item { SectionLabel(label = "Attachments") }
                if (uiState.attachmentsLoading && uiState.attachments.isEmpty()) {
                    item { AttachmentHydrationPlaceholder(count = uiState.attachmentCount) }
                }
                items(uiState.attachments, key = { it.id }) { attachment ->
                    val clipSource = attachment.pdfClipSourceOrNull()
                    NoteInlineAttachment(
                        attachment = attachment,
                        onClick = {
                            if (clipSource != null) {
                                onSourceReferenceClick(clipSource.attachmentId, clipSource.pageIndex)
                            } else {
                                onAttachmentClick(attachment.id)
                            }
                        },
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    )
                }
            }
        }
    }

    if (moreMenuOpen) {
        NoteActionSheet(
            title = "Note actions",
            onDismiss = { moreMenuOpen = false },
            sections = listOf(
                NoteSheetSection(
                    label = "Note",
                    actions = listOf(
                        NoteSheetAction("Listen", Icons.Rounded.PlayArrow, subtitle = "Read this note aloud", onClick = {
                            moreMenuOpen = false
                            listenModeOpen = true
                        }),
                        NoteSheetAction(if (isPinned) "Unpin note" else "Pin note", Icons.Rounded.PushPin, selected = isPinned, onClick = {
                            onPinnedChange(!isPinned)
                            moreMenuOpen = false
                        }),
                        NoteSheetAction(if (isFavourite) "Remove favourite" else "Favourite", Icons.Rounded.Star, selected = isFavourite, onClick = {
                            onFavouriteChange(!isFavourite)
                            moreMenuOpen = false
                        }),
                        NoteSheetAction("Note info", Icons.Rounded.Info, subtitle = "Updated, words and characters", onClick = {
                            moreMenuOpen = false
                            noteInfoOpen = true
                        }),
                    ),
                ),
                NoteSheetSection(
                    label = "History",
                    actions = listOf(
                        NoteSheetAction("Version history", Icons.Rounded.History, onClick = {
                            moreMenuOpen = false
                            versionHistoryOpen = true
                        }),
                    ),
                ),
                NoteSheetSection(
                    label = "Tools",
                    actions = listOf(
                        NoteSheetAction("Export", Icons.Rounded.FileDownload, subtitle = "TXT or PDF", onClick = {
                            moreMenuOpen = false
                            exportOpen = true
                        }),
                        NoteSheetAction("Structure & Format", Icons.Rounded.AutoAwesome, onClick = {
                            moreMenuOpen = false
                            onFormatClick()
                        }),
                    ),
                ),
                NoteSheetSection(
                    label = "Delete",
                    actions = listOf(
                        NoteSheetAction("Delete note", Icons.Rounded.Delete, subtitle = "Move this note to Recently Deleted", destructive = true, onClick = {
                            moreMenuOpen = false
                            deleteDialogOpen = true
                        }),
                    ),
                ),
            ),
        )
    }

    if (listenModeOpen) {
        val noteTitle = note?.title.orEmpty()
        val noteBody = uiState.richText.text.ifBlank { note?.bodyPlainText.orEmpty() }
        val narrationActions = buildList {
            azureNarrationProgress?.takeIf { it.positionMs >= 5_000L }?.let { progress ->
                add(
                    NoteSheetAction(
                        label = "Continue listening",
                        icon = Icons.Rounded.PlayArrow,
                        subtitle = "Resume from ${progress.positionMs.toPlaybackTime()}",
                        onClick = {
                            listenModeOpen = false
                            onAzureResumeClick(noteTitle, noteBody)
                        },
                    ),
                )
            }
            val choices = listOf(
                NarrationProvider.GeminiFlashLite to {
                    listenModeOpen = false
                    onGeminiListenClick(noteTitle, noteBody, GeminiNarrationConfig.MODEL_FLASH_LITE)
                },
                NarrationProvider.GeminiFlash to {
                    listenModeOpen = false
                    onGeminiListenClick(noteTitle, noteBody, GeminiNarrationConfig.MODEL_FLASH)
                },
                NarrationProvider.Device to {
                    listenModeOpen = false
                    onDeviceListenClick(noteTitle, noteBody)
                },
                NarrationProvider.OpenAi to {
                    listenModeOpen = false
                    onListenClick(noteTitle, noteBody, selectedNarrationVoice)
                },
                NarrationProvider.Azure to {
                    listenModeOpen = false
                    onAzureListenClick(noteTitle, noteBody)
                },
            ).sortedByDescending { it.first.storedValue == defaultNarrationProvider }
            choices.forEach { (provider, action) ->
                add(
                    NoteSheetAction(
                        label = provider.label,
                        icon = if (provider == NarrationProvider.Device) Icons.Rounded.PlayArrow else Icons.Rounded.AutoAwesome,
                        selected = provider.storedValue == defaultNarrationProvider,
                        subtitle = when (provider) {
                            NarrationProvider.GeminiFlashLite -> "Fast, intelligent, ultra-low cost lecture voice. (Recommended)"
                            NarrationProvider.GeminiFlash -> "High-fidelity, lecture-style speech."
                            NarrationProvider.Device -> "Fast built-in phone voice. Free and offline."
                            NarrationProvider.OpenAi -> "Natural OpenAI voice. Uses cached audio when available."
                            NarrationProvider.Azure -> "Azure ${azureNarrationVoice.ifBlank { "neural voice" }}. Uses cached audio."
                        },
                        onClick = action,
                    ),
                )
            }
        }
        NoteActionSheet(
            title = "Listen",
            onDismiss = { listenModeOpen = false },
            sections = listOf(
                NoteSheetSection("Listen with", narrationActions),
                NoteSheetSection(
                    "Audio",
                    listOf(
                        NoteSheetAction(
                            label = "Configure Azure Speech",
                            icon = Icons.Rounded.Settings,
                            subtitle = "Voice, region and connection",
                            onClick = {
                                listenModeOpen = false
                                onConfigureAzure()
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    if (noteInfoOpen) {
        VaultModal(title = "Note info", onDismiss = { noteInfoOpen = false }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            ) {
                NoteInfoStat(
                    label = "Updated",
                    value = note?.updatedAt?.toRelativeTime() ?: "Unknown",
                    modifier = Modifier.weight(1f),
                )
                NoteInfoStat(
                    label = "Words",
                    value = numberFormat.format(wordCount),
                    modifier = Modifier.weight(1f),
                )
                NoteInfoStat(
                    label = "Characters",
                    value = numberFormat.format(charCount),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (knowledgeOpen) {
        VaultModal(title = "Knowledge & references", onDismiss = { knowledgeOpen = false }) {
            Text("TAGS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800), color = colors.textMuted)
            if (uiState.knowledgeTags.isEmpty()) {
                Text("No tags yet", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                KnowledgeTagRow(tags = uiState.knowledgeTags)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                TextButton(onClick = {
                    knowledgeOpen = false
                    tagDraft = ""
                    tagDialogOpen = true
                }) { Text("Add tag") }
                if (uiState.knowledgeTags.isNotEmpty()) {
                    TextButton(onClick = {
                        knowledgeOpen = false
                        removeTagDialogOpen = true
                    }) { Text("Remove tag") }
                }
            }
            Text("BACKLINKS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800), color = colors.textMuted)
            if (uiState.backlinks.isEmpty()) {
                Text("No notes link here", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.backlinks.take(5).forEach { backlink ->
                    BacklinkCard(
                        title = backlink.title,
                        preview = backlink.preview,
                        onClick = {
                            knowledgeOpen = false
                            onNoteLinkClick(backlink.id)
                        },
                        compact = true,
                    )
                }
            }
            Text("PDF SOURCES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800), color = colors.textMuted)
            if (uiState.sourceReferences.isEmpty()) {
                Text("No PDF sources linked", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.sourceReferences.take(5).forEach { source ->
                    SourceReferenceCardRow(
                        source = source,
                        onClick = {
                            knowledgeOpen = false
                            onSourceReferenceClick(source.attachmentId, source.pageIndex)
                        },
                        onLongPress = { sourceReferenceToRemove = source },
                        compact = true,
                    )
                }
            }
        }
    }

    if (attachmentsOpen) {
        VaultModal(title = "Attachments", onDismiss = { attachmentsOpen = false }) {
            if (uiState.attachmentsLoading && uiState.attachments.isEmpty()) {
                Text("Loading attachments...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else if (uiState.attachments.isEmpty()) {
                Text("No files or images attached", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.attachments.forEach { attachment ->
                    AttachmentSheetRow(
                        attachment = attachment,
                        onClick = {
                            attachmentsOpen = false
                            onAttachmentClick(attachment.id)
                        },
                    )
                }
            }
            Text("ADD", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800), color = colors.textMuted)
            NoteModalActionRow(
                NoteSheetAction("Attach file", Icons.Rounded.AttachFile, onClick = {
                    attachmentsOpen = false
                    attachmentPicker.launch(arrayOf("*/*"))
                }),
            )
            NoteModalActionRow(
                NoteSheetAction("Attach image", Icons.Rounded.Image, onClick = {
                    attachmentsOpen = false
                    imagePicker.launch(arrayOf("image/*"))
                }),
            )
        }
    }

    if (exportOpen) {
        NoteActionSheet(
            title = "Export",
            onDismiss = { exportOpen = false },
            sections = listOf(
                NoteSheetSection(
                    "Export as",
                    listOf(
                        NoteSheetAction("Text file", Icons.Rounded.Notes, subtitle = "Plain TXT document", onClick = {
                            exportOpen = false
                            exportTextLauncher.launch("${note?.title?.toSafeFileName() ?: "note"}.txt")
                        }),
                        NoteSheetAction("PDF", Icons.Rounded.FileDownload, subtitle = "Portable document", onClick = {
                            exportOpen = false
                            exportPdfLauncher.launch("${note?.title?.toSafeFileName() ?: "note"}.pdf")
                        }),
                        NoteSheetAction("A5 Notebook Print", Icons.Rounded.FileDownload, subtitle = "Two A5 pages on A4 landscape", onClick = {
                            exportOpen = false
                            notebookPrintOpen = true
                        }),
                    ),
                ),
            ),
        )
    }

    if (notebookPrintOpen) NotebookPrintSheet(onDismiss = { notebookPrintOpen = false }) { action, config ->
        notebookConfig = config
        if (action == NotebookPrintAction.Save || action == NotebookPrintAction.Calibration) {
            notebookDocumentAction = action
            notebookPdfLauncher.launch("${note?.title?.toSafeFileName() ?: "note"}-${if (action == NotebookPrintAction.Calibration) "calibration" else "notebook"}.pdf")
        } else onNotebookExport(action, config, null)
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

    if (versionHistoryOpen) {
        VaultModal(title = "Version history", onDismiss = { versionHistoryOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.accentSoft,
                shape = VaultShapes.md,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                Row(
                    modifier = Modifier.padding(VaultSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                    Column {
                        Text("Current", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800), color = colors.accent)
                        Text("${numberFormat.format(wordCount)} words · ${numberFormat.format(charCount)} characters", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
            }
            if (uiState.versions.isEmpty()) {
                Text("No saved versions yet", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                uiState.versions.take(12).forEach { version ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(VaultSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(version.createdAt.toRelativeTime(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = colors.text)
                                Text("${numberFormat.format(version.wordCount)} words · ${numberFormat.format(version.characterCount)} characters", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            }
                            TextButton(onClick = { versionToRestore = version.id }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }

    versionToRestore?.let { versionId ->
        AlertDialog(
            onDismissRequest = { versionToRestore = null },
            title = { Text("Restore this version?") },
            text = { Text("Your current note will be saved as a version first, then this older version will be restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreVersion(versionId)
                        versionToRestore = null
                        versionHistoryOpen = false
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { versionToRestore = null }) {
                    Text("Cancel")
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

private val NarrationEditActionClearance = 10.dp

private fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }

@Composable
internal fun KnowledgeTagRow(
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
internal fun SourceReferenceCardRow(
    source: SourceReferenceCard,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    compact: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else VaultSpacing.screen)
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
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    onLayout: (TextLayoutResult, androidx.compose.ui.text.input.OffsetMapping) -> Unit = { _, _ -> },
    bodyFontSizeSp: Float,
    activeRange: IntRange?,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val bodyText = richText.text.ifBlank { fallbackText.ifBlank { html.stripHtml() } }
    var textLayout by remember(bodyText) { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember(bodyText) { mutableStateOf(TextFieldValue(bodyText)) }
    val currentLinkClick by rememberUpdatedState(onNoteLinkClick)
    val currentDoubleTap by rememberUpdatedState(onDoubleTapEdit)
    // Keep the full selectable text/layout cached; cue changes only redraw its highlight.
    val display = remember(bodyText, richText.styleMarks, richText.noteLinks, colors) {
        buildVaultAnnotatedString(bodyText, richText.styleMarks, richText.noteLinks, colors).withVaultBidiIsolation()
    }

    if (bodyText.isBlank()) {
        Text(
            text = "No body text yet.",
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = bodyFontSizeSp.sp),
            color = colors.textMuted,
        )
    } else {
            BasicTextField(
                value = selection,
                onValueChange = { selection = it.copy(text = bodyText) },
                readOnly = true,
                visualTransformation = remember(display) {
                    VisualTransformation { TransformedText(display.text, display.offsetMapping) }
                },
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("NoteReaderText")
                    .pointerInput(bodyText) {
                        var lastTap = 0L
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var moved = false
                            var upTime = down.uptimeMillis
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    upTime = change.uptimeMillis
                                }
                            } while (event.changes.any { it.pressed })
                            if (!moved && upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                if (lastTap > 0 && upTime - lastTap <= viewConfiguration.doubleTapTimeoutMillis) {
                                    currentDoubleTap()
                                    lastTap = 0L
                                } else {
                                    val offset = textLayout?.getOffsetForPosition(down.position)
                                    offset?.let { display.text.getStringAnnotations("noteLink", it, it).firstOrNull() }
                                        ?.let { currentLinkClick(it.item) }
                                    lastTap = upTime
                                }
                            } else lastTap = 0L
                        }
                    }
                    .drawBehind {
                        val layout = textLayout
                        val range = activeRange
                        if (layout != null && range != null && range.last < bodyText.length) {
                            drawPath(layout.getPathForRange(display.offsetMapping.originalToTransformed(range.first),
                                display.offsetMapping.originalToTransformed(range.last + 1)),
                                color = colors.accent.copy(alpha = 0.38f))
                        }
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.text,
                    fontSize = bodyFontSizeSp.sp,
                    textAlign = TextAlign.Start,
                    textDirection = vaultDefaultTextDirection(),
                ),
                onTextLayout = {
                    textLayout = it
                    onLayout(it, display.offsetMapping)
                },
            )
    }
}

private data class ReadingBodyLayout(
    val layout: TextLayoutResult,
    val offsetMapping: androidx.compose.ui.text.input.OffsetMapping,
)


internal data class ReadingBodyChunk(
    val start: Int,
    val end: Int,
    val text: String,
    val document: VaultRichTextDocument,
)

internal fun String.toReadingBodyChunks(
    marks: List<VaultStyleMark>,
    noteLinks: List<VaultNoteLink>,
): List<ReadingBodyChunk> {
    if (isBlank()) return emptyList()
    // One selection owner must know the complete note, including off-screen text.
        return listOf(
            ReadingBodyChunk(
                start = 0,
                end = length,
                text = this,
                document = VaultRichTextDocument(
                    text = this,
                    styleMarks = marks,
                    noteLinks = noteLinks,
                ),
            ),
        )
}

private fun Long.toPlaybackTime(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
internal fun BacklinkCard(
    title: String,
    preview: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 0.dp else VaultSpacing.screen,
                vertical = VaultSpacing.xs,
            ),
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

@Composable
internal fun NoteInfoStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AttachmentSheetRow(
    attachment: AttachmentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${attachment.kindLabel()} · ${attachment.sizeLabel()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
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
private fun AttachmentHydrationPlaceholder(count: Int) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.xs),
        color = colors.surface.copy(alpha = 0.72f),
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.inset, VaultShapes.md),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(20.dp), tint = colors.textMuted)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (count > 1) "Loading attachments..." else "Loading attachment...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
                Text(
                    text = "Preparing file preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
}
