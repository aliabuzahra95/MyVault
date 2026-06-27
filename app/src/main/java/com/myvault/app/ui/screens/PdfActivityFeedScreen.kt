package com.myvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.ui.components.SearchBar
import com.myvault.app.ui.components.VaultActionModal
import com.myvault.app.ui.components.VaultConfirmModal
import com.myvault.app.ui.components.VaultFormModal
import com.myvault.app.ui.components.VaultModalAction
import com.myvault.app.ui.components.VaultTextField
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryAnnotationItem
import com.myvault.app.ui.viewmodel.PdfActivityFeedUiState
import com.myvault.app.ui.viewmodel.PdfActivityGroup

@Composable
fun PdfActivityFeedScreen(
    uiState: PdfActivityFeedUiState,
    onBackClick: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onActivityClick: (String, Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onUpdateActivityDetails: (String, String, String) -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateStudyNoteFromSelected: (onCreated: (String) -> Unit) -> Unit,
    onAskAiOnSelected: (onNav: (String, String) -> Unit) -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToAskAi: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val title = if (uiState.libraryMode == "library") "Islamic Corpus Feed" else "Personal Activity Feed"
    val inSelectionMode = uiState.selectedActivityIds.isNotEmpty()

    var showActionMenuId by remember { mutableStateOf<LibraryAnnotationItem?>(null) }
    var showEditDialogId by remember { mutableStateOf<LibraryAnnotationItem?>(null) }
    var deleteSelectedConfirmOpen by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            ScreenTopBar(
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800),
                        color = colors.text
                    )
                    Text(
                        text = "PDF activity grouped by document",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    active = uiState.searchQuery.isNotBlank(),
                    placeholder = "Search highlights and notes...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.screen, vertical = 4.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.pdfActivities.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = colors.textMuted,
                            )
                            Text(
                                text = "No matching activity found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = VaultSpacing.screen,
                            end = VaultSpacing.screen,
                            bottom = if (inSelectionMode) 92.dp else VaultSpacing.screen,
                            top = 6.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.pdfActivities, key = { it.attachmentId }) { group ->
                            val isExpanded = group.attachmentId in uiState.expandedPdfIds
                            PdfGroupCard(
                                group = group,
                                isExpanded = isExpanded,
                                onToggle = { onToggleExpanded(group.attachmentId) },
                                selectedActivityIds = uiState.selectedActivityIds,
                                onActivityClick = { activity ->
                                    if (uiState.selectedActivityIds.isNotEmpty()) {
                                        onToggleSelection(activity.id)
                                    } else {
                                        onActivityClick(activity.attachmentId, activity.pageIndex)
                                    }
                                },
                                onActivityLongPress = { activity ->
                                    if (uiState.selectedActivityIds.isEmpty()) {
                                        showActionMenuId = activity
                                    } else {
                                        onToggleSelection(activity.id)
                                    }
                                },
                                onShowActionMenu = { activity ->
                                    showActionMenuId = activity
                                },
                                onToggleSelection = onToggleSelection
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.selectedActivityIds.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(VaultSpacing.lg),
            ) {
                Surface(
                    color = colors.elevated.copy(alpha = 0.96f),
                    contentColor = colors.textSecondary,
                    shape = VaultShapes.pill,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, "Clear Selection", tint = colors.textSecondary)
                        }
                        Text(
                            text = "${uiState.selectedActivityIds.size} selected",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = {
                            onCreateStudyNoteFromSelected { noteId ->
                                onNavigateToEditor(noteId)
                            }
                        }) {
                            Icon(Icons.Rounded.Description, "Create Study Note", tint = colors.accent)
                        }

                        IconButton(onClick = {
                            onAskAiOnSelected { noteId, text ->
                                onNavigateToAskAi(noteId, text)
                            }
                        }) {
                            Icon(Icons.Rounded.AutoAwesome, "Ask AI", tint = colors.accent)
                        }

                        IconButton(onClick = { deleteSelectedConfirmOpen = true }) {
                            Icon(Icons.Rounded.Delete, "Delete", tint = colors.warning)
                        }
                    }
                }
            }
        }
    }

    if (deleteSelectedConfirmOpen) {
        VaultConfirmModal(
            title = "Delete selected activities?",
            message = "This will permanently delete the ${uiState.selectedActivityIds.size} selected highlights and notes from the database. This action cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onDismiss = { deleteSelectedConfirmOpen = false },
            onConfirm = {
                deleteSelectedConfirmOpen = false
                onDeleteSelected()
            }
        )
    }

    showActionMenuId?.let { activity ->
        VaultActionModal(
            title = activity.displayTitle ?: activity.notePreview.take(30).ifBlank { "PDF Activity" },
            onDismiss = { showActionMenuId = null },
            actions = listOf(
                VaultModalAction("Open in PDF reader", Icons.Rounded.MenuBook) {
                    onActivityClick(activity.attachmentId, activity.pageIndex)
                    showActionMenuId = null
                },
                VaultModalAction("Edit title / description", Icons.Rounded.Edit) {
                    editTitle = activity.displayTitle.orEmpty()
                    editDesc = activity.notePreview
                    showEditDialogId = activity
                    showActionMenuId = null
                },
                VaultModalAction("Enter Selection Mode", Icons.Rounded.PlaylistAddCheck) {
                    onToggleSelection(activity.id)
                    showActionMenuId = null
                }
            )
        )
    }

    showEditDialogId?.let { activity ->
        VaultFormModal(
            title = "Edit activity details",
            confirmLabel = "Save",
            icon = Icons.Rounded.Edit,
            onDismiss = { showEditDialogId = null },
            onConfirm = {
                onUpdateActivityDetails(activity.id, editTitle, editDesc)
                showEditDialogId = null
            }
        ) {
            VaultTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "Custom title (optional)",
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            VaultTextField(
                value = editDesc,
                onValueChange = { editDesc = it },
                label = "Description / Note text (optional)",
                minLines = 4,
                maxLines = 8
            )
        }
    }
}

@Composable
private fun PdfGroupCard(
    group: PdfActivityGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onActivityClick: (LibraryAnnotationItem) -> Unit,
    selectedActivityIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    onActivityLongPress: (LibraryAnnotationItem) -> Unit = {},
    onShowActionMenu: (LibraryAnnotationItem) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val timeLabel = if (group.lastActivityAt > 0L) "Updated ${group.lastActivityAt.toRelativeTime()}" else ""

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        label = "pdf-group-chevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${group.totalCount} activity item${if (group.totalCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        if (timeLabel.isNotBlank()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.textMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            ) {
                Surface(
                    color = colors.inset,
                    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                    shape = VaultShapes.sm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    Column {
                        group.activities.forEachIndexed { index, activity ->
                            val isSelected = activity.id in selectedActivityIds
                            ActivityItemRow(
                                activity = activity,
                                isSelected = isSelected,
                                onClick = {
                                    onActivityClick(activity)
                                },
                                onLongPress = {
                                    onActivityLongPress(activity)
                                }
                            )
                            if (index < group.activities.lastIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .padding(start = 12.dp, end = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ActivityItemRow(
    activity: LibraryAnnotationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val (defaultTitle, iconTint) = when (activity.annotationType) {
        PdfAnnotationEntity.TYPE_PAGE_NOTE -> {
            "PDF Note" to Color(0xFFFFD84D)
        }
        else -> {
            "Highlight" to activity.color.toAnnotationColor()
        }
    }

    val titleText = if (!activity.displayTitle.isNullOrBlank()) activity.displayTitle else defaultTitle
    val descriptionText = activity.notePreview

    val rowBg = if (isSelected) colors.accent.copy(alpha = 0.08f) else Color.Transparent

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = rowBg,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.StickyNote2,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Page ${activity.pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (descriptionText.isNotBlank()) {
                    Text(
                        text = descriptionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun String.toAnnotationColor(): Color =
    when (lowercase()) {
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFD84D)
    }
