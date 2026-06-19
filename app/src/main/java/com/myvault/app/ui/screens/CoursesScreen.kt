package com.myvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultConfirmModal
import com.myvault.app.ui.components.VaultFormModal
import com.myvault.app.ui.components.VaultTextField
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.CoursesUiState

@Composable
fun CoursesScreen(
    uiState: CoursesUiState,
    onSelectCourse: (String) -> Unit,
    onCreateCourse: (String) -> Unit,
    onRenameCourse: (String, String) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: () -> Unit,
    onNewFolder: (String, String?) -> Unit,
    onFolderExpandedChange: (String, Boolean) -> Unit,
    onMoveItemInOrder: (String, VaultTreeItemType, Int) -> Unit,
    onRenameNote: (String, String) -> Unit,
    onMoveNote: (String, String?) -> Unit,
    onMoveNoteToMode: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onSetNotePinned: (String, Boolean) -> Unit,
    onSetNoteFolderPinned: (String, Boolean) -> Unit,
    onSetNoteFavourite: (String, Boolean) -> Unit,
    onCreateSubNote: (String) -> Unit,
    onCreateSticky: (String) -> Unit,
    onUpdateSticky: (String, String) -> Unit,
    onDeleteSticky: (String) -> Unit,
    onCreateConcept: (String, String?, String, String?) -> Unit,
    onSaveConcept: (String, String, String?, String, String?) -> Unit,
    onDeleteConcept: (String) -> Unit,
    dashboardFontSizeSp: Float,
    onThemeClick: () -> Unit = {},
    onQuickBackupClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    quickBackupRecommended: Boolean = false,
) {
    var courseDialog by remember { mutableStateOf<CourseEntity?>(null) }
    var creatingCourse by remember { mutableStateOf(false) }
    var deleteCourseDialog by remember { mutableStateOf<CourseEntity?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var creatingSticky by remember { mutableStateOf(false) }
    var conceptDialog by remember { mutableStateOf<CourseConceptCardEntity?>(null) }
    var creatingConcept by remember { mutableStateOf(false) }
    var deleteConceptDialog by remember { mutableStateOf<CourseConceptCardEntity?>(null) }

    if (uiState.activeCourse == null) {
        EmptyCoursesScreen(onCreate = { creatingCourse = true })
    } else {
        FolderViewScreen(
            uiState = uiState.folderState,
            onBackClick = {},
            onSearchClick = {},
            onNoteClick = onOpenNote,
            onFolderClick = onOpenFolder,
            onNewNoteClick = onNewNote,
            onNewSubfolderClick = onNewFolder,
            onFolderExpandedChange = onFolderExpandedChange,
            onMoveItemInOrderClick = onMoveItemInOrder,
            onRenameNoteClick = onRenameNote,
            onMoveNoteClick = onMoveNote,
            onMoveNoteToModeClick = onMoveNoteToMode,
            onDeleteNoteClick = onDeleteNote,
            onSetNotePinnedClick = onSetNotePinned,
            onSetNoteFolderPinnedClick = onSetNoteFolderPinned,
            onSetNoteFavouriteClick = onSetNoteFavourite,
            onCreateSubNoteClick = onCreateSubNote,
            onNewStickyNoteClick = onCreateSticky,
            onUpdateStickyNoteClick = onUpdateSticky,
            onDeleteStickyNoteClick = onDeleteSticky,
            notePreviewLines = uiState.notePreviewLines,
            showFullNoteTitles = uiState.showFullNoteTitles,
            dashboardFontSizeSp = dashboardFontSizeSp,
            showNavigationHeader = false,
            topContent = {
                CourseHeader(
                    courses = uiState.courses,
                    active = uiState.activeCourse,
                    continueTitle = uiState.continueNoteTitle,
                    onSelect = onSelectCourse,
                    onCreate = { creatingCourse = true },
                    onRename = { courseDialog = uiState.activeCourse },
                    onDelete = { deleteCourseDialog = uiState.activeCourse },
                    onContinue = { uiState.continueNoteId?.let(onOpenNote) },
                    onNewNote = onNewNote,
                    onNewFolder = { creatingFolder = true },
                    onNewSticky = { creatingSticky = true },
                    onNewConcept = { creatingConcept = true },
                    onThemeClick = onThemeClick,
                    onQuickBackupClick = onQuickBackupClick,
                    onSettingsClick = onSettingsClick,
                    quickBackupRecommended = quickBackupRecommended,
                )
            },
            bottomContent = {
                ConceptSection(
                    concepts = uiState.concepts,
                    onAdd = { creatingConcept = true },
                    onEdit = { conceptDialog = it },
                    onDelete = { concept -> deleteConceptDialog = concept },
                )
            },
        )
    }

    if (creatingCourse || courseDialog != null) {
        TitleDialog(
            title = if (creatingCourse) "New course" else "Rename course",
            initial = courseDialog?.title.orEmpty(),
            onDismiss = { creatingCourse = false; courseDialog = null },
            onSave = {
                courseDialog?.let { course -> onRenameCourse(course.id, it) } ?: onCreateCourse(it)
                creatingCourse = false
                courseDialog = null
            },
        )
    }
    if (creatingFolder) {
        TitleDialog(
            title = "New folder",
            initial = "",
            onDismiss = { creatingFolder = false },
            onSave = {
                onNewFolder(it, null)
                creatingFolder = false
            },
        )
    }
    if (creatingSticky) {
        TextDialog(
            title = "New sticky note",
            initial = "",
            label = "Sticky note",
            icon = Icons.Rounded.StickyNote2,
            onDismiss = { creatingSticky = false },
            onSave = {
                onCreateSticky(it)
                creatingSticky = false
            },
        )
    }
    if (creatingConcept || conceptDialog != null) {
        ConceptDialog(
            concept = conceptDialog,
            onDismiss = { creatingConcept = false; conceptDialog = null },
            onSave = { term, arabic, definition, details ->
                conceptDialog?.let { onSaveConcept(it.id, term, arabic, definition, details) }
                    ?: onCreateConcept(term, arabic, definition, details)
                creatingConcept = false
                conceptDialog = null
            },
        )
    }
    deleteCourseDialog?.let { course ->
        VaultConfirmModal(
            title = "Delete ${course.title}?",
            message = "This will remove this course workspace. Existing Study notes remain separate.",
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteCourseDialog = null },
            onConfirm = {
                onDeleteCourse(course.id)
                deleteCourseDialog = null
            },
        )
    }
    deleteConceptDialog?.let { concept ->
        VaultConfirmModal(
            title = "Delete ${concept.term}?",
            message = "This concept card will be removed from this course.",
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteConceptDialog = null },
            onConfirm = {
                onDeleteConcept(concept.id)
                deleteConceptDialog = null
            },
        )
    }
}

@Composable
private fun CourseHeader(
    courses: List<CourseEntity>,
    active: CourseEntity,
    continueTitle: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onContinue: () -> Unit,
    onNewNote: () -> Unit,
    onNewFolder: () -> Unit,
    onNewSticky: () -> Unit,
    onNewConcept: () -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
) {
    val colors = VaultThemeTokens.colors
    var actionsOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(vertical = VaultSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
    ) {
        VaultTopBar(
            title = active.title,
            titleContent = {
                CourseSwitcher(
                    courses = courses,
                    active = active,
                    onSelect = onSelect,
                    onCreate = onCreate,
                    modifier = Modifier.widthIn(min = 132.dp, max = 190.dp),
                )
            },
        ) {
            IconBtn(
                icon = Icons.Rounded.WbSunny,
                contentDescription = "Toggle theme",
                active = true,
                onClick = onThemeClick,
            )
            IconBtn(
                icon = Icons.Rounded.Backup,
                contentDescription = "Quick cloud backup",
                active = quickBackupRecommended,
                onClick = onQuickBackupClick,
            )
            IconBtn(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick,
            )
            IconBtn(
                icon = Icons.Rounded.MoreVert,
                contentDescription = "Course actions",
                onClick = { actionsOpen = true },
            )
            CourseActionMenu(
                expanded = actionsOpen,
                onDismiss = { actionsOpen = false },
                onRename = onRename,
                onNewFolder = onNewFolder,
                onNewNote = onNewNote,
                onNewSticky = onNewSticky,
                onNewConcept = onNewConcept,
                onManage = onRename,
                onDelete = onDelete,
            )
        }
        ContinueLessonCard(
            continueTitle = continueTitle,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun ContinueLessonCard(
    continueTitle: String?,
    onContinue: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(horizontal = VaultSpacing.md, vertical = VaultSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = colors.accent)
            Spacer(Modifier.padding(VaultSpacing.xxs))
            Column(modifier = Modifier.weight(1f)) {
                Text("Continue lesson", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
                Text(continueTitle ?: "No lesson opened yet", color = colors.text, fontWeight = FontWeight.SemiBold)
            }
            if (continueTitle != null) Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
        }
    }
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = VaultSpacing.screen)

    if (continueTitle != null) {
        Surface(
            onClick = onContinue,
            modifier = modifier,
            shape = VaultShapes.sm,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = VaultShapes.sm,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            content = content,
        )
    }
}

@Composable
private fun CourseSwitcher(
    courses: List<CourseEntity>,
    active: CourseEntity,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var pickerOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { pickerOpen = true },
        modifier = modifier,
        shape = VaultShapes.pill,
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = active.title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Open course menu",
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(
            expanded = pickerOpen,
            onDismissRequest = { pickerOpen = false },
            modifier = Modifier
                .shadow(10.dp, VaultShapes.lg, clip = false)
                .background(colors.elevated)
                .border(BorderStroke(1.dp, colors.borderStrong), VaultShapes.lg),
            shape = VaultShapes.lg,
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            properties = PopupProperties(focusable = true),
        ) {
            courses.forEach { course ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = course.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (course.id == active.id) FontWeight.W800 else FontWeight.W600,
                            ),
                            color = if (course.id == active.id) colors.accent else colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (course.id == active.id) colors.accent else colors.text,
                    ),
                    onClick = {
                        pickerOpen = false
                        onSelect(course.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("New course", color = colors.text, fontWeight = FontWeight.W700) },
                leadingIcon = { Icon(Icons.Rounded.Add, null, tint = colors.accent) },
                colors = MenuDefaults.itemColors(textColor = colors.text),
                onClick = {
                    pickerOpen = false
                    onCreate()
                },
            )
        }
    }
}

@Composable
private fun CourseActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
    onNewSticky: () -> Unit,
    onNewConcept: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .shadow(10.dp, VaultShapes.lg, clip = false)
            .background(colors.elevated)
            .border(BorderStroke(1.dp, colors.borderStrong), VaultShapes.lg),
        shape = VaultShapes.lg,
        containerColor = colors.elevated,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        properties = PopupProperties(focusable = true),
    ) {
        CourseMenuItem("Rename course", Icons.Rounded.Edit, onDismiss, onRename)
        CourseMenuItem("New folder", Icons.Rounded.CreateNewFolder, onDismiss, onNewFolder)
        CourseMenuItem("New note", Icons.Rounded.Description, onDismiss, onNewNote)
        CourseMenuItem("New sticky note", Icons.Rounded.StickyNote2, onDismiss, onNewSticky)
        CourseMenuItem("New concept card", Icons.Rounded.Lightbulb, onDismiss, onNewConcept)
        CourseMenuItem("Manage course", Icons.Rounded.MoreVert, onDismiss, onManage)
        CourseMenuItem("Delete course", Icons.Rounded.Delete, onDismiss, onDelete, destructive = true)
    }
}

@Composable
private fun CourseMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (destructive) colors.warning else colors.text,
                fontWeight = FontWeight.W700,
            )
        },
        leadingIcon = {
            Icon(icon, null, tint = if (destructive) colors.warning else colors.accent)
        },
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) colors.warning else colors.text,
            leadingIconColor = if (destructive) colors.warning else colors.accent,
        ),
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

@Composable
private fun ConceptSection(
    concepts: List<CourseConceptCardEntity>,
    onAdd: () -> Unit,
    onEdit: (CourseConceptCardEntity) -> Unit,
    onDelete: (CourseConceptCardEntity) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        Row(modifier = Modifier.padding(end = VaultSpacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { SectionLabel("Concept Cards") }
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Add concept", tint = colors.accent) }
        }
        if (concepts.isEmpty()) {
            Text("No concept cards yet.", modifier = Modifier.padding(horizontal = VaultSpacing.screen), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            ConceptGrid(
                concepts = concepts,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
        Spacer(Modifier.height(VaultSpacing.sm))
    }
}

@Composable
private fun ConceptGrid(
    concepts: List<CourseConceptCardEntity>,
    onEdit: (CourseConceptCardEntity) -> Unit,
    onDelete: (CourseConceptCardEntity) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        concepts.chunked(2).forEach { rowConcepts ->
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                rowConcepts.forEach { concept ->
                    ConceptCard(
                        concept = concept,
                        modifier = Modifier.weight(1f),
                        onEdit = { onEdit(concept) },
                        onDelete = { onDelete(concept) },
                    )
                }
                if (rowConcepts.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConceptCard(
    concept: CourseConceptCardEntity,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onEdit,
        modifier = modifier,
        shape = VaultShapes.sm,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(VaultSpacing.xxs)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                Spacer(Modifier.width(VaultSpacing.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(concept.term, color = colors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        concept.arabicTerm?.let {
                            Text(it, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { menuOpen = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Concept actions", modifier = Modifier.size(16.dp), tint = colors.textMuted)
                    }
                }
            }
            Text(concept.definition.ifBlank { "No definition yet." }, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier
                    .shadow(10.dp, VaultShapes.lg, clip = false)
                    .background(colors.elevated)
                    .border(BorderStroke(1.dp, colors.borderStrong), VaultShapes.lg),
                shape = VaultShapes.lg,
                containerColor = colors.elevated,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                properties = PopupProperties(focusable = true),
            ) {
                CourseMenuItem("Edit", Icons.Rounded.Edit, { menuOpen = false }, onEdit)
                CourseMenuItem("Delete", Icons.Rounded.Delete, { menuOpen = false }, onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun EmptyCoursesScreen(onCreate: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(modifier = Modifier.padding(VaultSpacing.screen), verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
        Text("Courses", style = MaterialTheme.typography.headlineMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Text("Create a dedicated Study workspace for one subject.", color = colors.textSecondary)
        Button(onClick = onCreate) { Text("Create course") }
    }
}

@Composable
private fun TitleDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    VaultFormModal(
        title = title,
        confirmLabel = "Save",
        enabled = value.isNotBlank(),
        icon = Icons.Rounded.Edit,
        onDismiss = onDismiss,
        onConfirm = { onSave(value) },
    ) {
        VaultTextField(value, { value = it }, label = "Title", singleLine = true)
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    VaultFormModal(
        title = title,
        confirmLabel = "Save",
        enabled = value.isNotBlank(),
        icon = icon,
        onDismiss = onDismiss,
        onConfirm = { onSave(value) },
    ) {
        VaultTextField(value, { value = it }, label = label, minLines = 6, maxLines = 12)
    }
}

@Composable
private fun ConceptDialog(
    concept: CourseConceptCardEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String?) -> Unit,
) {
    var term by rememberSaveable(concept?.id) { mutableStateOf(concept?.term.orEmpty()) }
    var arabic by rememberSaveable(concept?.id) { mutableStateOf(concept?.arabicTerm.orEmpty()) }
    var definition by rememberSaveable(concept?.id) { mutableStateOf(concept?.definition.orEmpty()) }
    var details by rememberSaveable(concept?.id) { mutableStateOf(concept?.details.orEmpty()) }
    VaultFormModal(
        title = if (concept == null) "New concept card" else "Edit concept card",
        confirmLabel = "Save",
        enabled = term.isNotBlank(),
        icon = Icons.Rounded.Lightbulb,
        onDismiss = onDismiss,
        onConfirm = { onSave(term, arabic, definition, details) },
    ) {
        VaultTextField(term, { term = it }, label = "Term", singleLine = true)
        VaultTextField(arabic, { arabic = it }, label = "Arabic term (optional)", singleLine = true)
        VaultTextField(definition, { definition = it }, label = "Definition", minLines = 2, maxLines = 4)
        VaultTextField(details, { details = it }, label = "Details (optional)", minLines = 2, maxLines = 5)
    }
}
