package com.myvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.CompactPrimaryAction
import com.myvault.app.ui.components.FloatingActionStackDefaults
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
import kotlinx.coroutines.delay

private enum class CourseDeferredAction {
    NewFolder,
    NewNote,
    NewSticky,
    NewConcept,
    RenameCourse,
    NewCourse,
    DeleteCourse,
}

private enum class OverviewCourseActionType {
    Rename,
    Delete,
}

private data class PendingOverviewCourseAction(
    val course: CourseEntity,
    val type: OverviewCourseActionType,
)

@Composable
fun CoursesScreen(
    uiState: CoursesUiState,
    backHandlerEnabled: Boolean = true,
    onSelectCourse: (String) -> Unit,
    onCreateCourse: (String) -> Unit,
    onRenameCourse: (String, String) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: () -> Unit,
    onNewNoteInFolder: (String) -> Unit,
    onNewFolder: (String, String?) -> Unit,
    onNewSubfolderInFolder: (String, String, String?) -> Unit,
    onUpdateChildFolder: (String, String, String?) -> Unit,
    onMoveChildFolder: (String, String?) -> Unit,
    onDeleteChildFolder: (String) -> Unit,
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
    fabBottomPadding: Dp = FloatingActionStackDefaults.fabBottomPadding,
) {
    var courseDialog by remember { mutableStateOf<CourseEntity?>(null) }
    var creatingCourse by remember { mutableStateOf(false) }
    var deleteCourseDialog by remember { mutableStateOf<CourseEntity?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var creatingSticky by remember { mutableStateOf(false) }
    var conceptDialog by remember { mutableStateOf<CourseConceptCardEntity?>(null) }
    var creatingConcept by remember { mutableStateOf(false) }
    var deleteConceptDialog by remember { mutableStateOf<CourseConceptCardEntity?>(null) }
    var openedCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var courseCreateActionsOpen by remember { mutableStateOf(false) }
    var overviewActionsCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var pendingOverviewAction by remember { mutableStateOf<PendingOverviewCourseAction?>(null) }

    LaunchedEffect(overviewActionsCourse, pendingOverviewAction) {
        val pending = pendingOverviewAction ?: return@LaunchedEffect
        if (overviewActionsCourse != null) return@LaunchedEffect
        delay(180)
        when (pending.type) {
            OverviewCourseActionType.Rename -> courseDialog = pending.course
            OverviewCourseActionType.Delete -> deleteCourseDialog = pending.course
        }
        pendingOverviewAction = null
    }

    val openedCourse = openedCourseId?.let { id -> uiState.courses.firstOrNull { it.id == id } }
    BackHandler(enabled = backHandlerEnabled && openedCourse != null) { openedCourseId = null }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            openedCourse == null -> CoursesMobileWebOverview(
                courses = uiState.courses,
                noteCountsByCourse = uiState.noteCountsByCourse,
                onOpen = { course ->
                    openedCourseId = course.id
                    onSelectCourse(course.id)
                },
                onManage = { overviewActionsCourse = it },
            )
            uiState.activeCourse?.id != openedCourse.id || uiState.folderState.folder == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VaultThemeTokens.colors.accent)
                }
            }
            else -> {
            FolderViewScreen(
            uiState = uiState.folderState,
            onBackClick = { openedCourseId = null },
            onSearchClick = {},
            onNoteClick = onOpenNote,
            onFolderClick = onOpenFolder,
            onNewNoteClick = onNewNote,
            onNewSubfolderClick = onNewFolder,
            onCreateNoteInFolderClick = onNewNoteInFolder,
            onCreateSubfolderInFolderClick = onNewSubfolderInFolder,
            onUpdateChildFolderClick = onUpdateChildFolder,
            onMoveChildFolderClick = onMoveChildFolder,
            onDeleteChildFolderClick = onDeleteChildFolder,
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
            showFloatingCreateAction = false,
            coursePresentation = true,
            fabBottomPadding = fabBottomPadding,
            topContent = {
                CourseHeader(
                    active = uiState.activeCourse,
                    noteCount = uiState.noteCountsByCourse[uiState.activeCourse.id] ?: 0,
                    continueTitle = uiState.continueNoteTitle,
                    onRename = { courseDialog = uiState.activeCourse },
                    onDelete = { deleteCourseDialog = uiState.activeCourse },
                    onContinue = { uiState.continueNoteId?.let(onOpenNote) },
                    onThemeClick = onThemeClick,
                    onQuickBackupClick = onQuickBackupClick,
                    onSettingsClick = onSettingsClick,
                    quickBackupRecommended = quickBackupRecommended,
                )
            },
            bottomContent = {
                ConceptSection(
                    concepts = uiState.concepts,
                    onEdit = { conceptDialog = it },
                    onDelete = { concept -> deleteConceptDialog = concept },
                )
            },
        )

            }
        }
        if (openedCourse == null) {
            CourseFloatingCreateButton(
                contentDescription = "Create course",
                onClick = { creatingCourse = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = fabBottomPadding),
            )
        } else if (uiState.activeCourse?.id == openedCourse.id && uiState.folderState.folder != null) {
            CourseFloatingCreateButton(
                contentDescription = "Add to course",
                onClick = { courseCreateActionsOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = fabBottomPadding),
            )
        }
    }

    if (courseCreateActionsOpen && openedCourse != null) {
        PremiumActionDialog(
            title = openedCourse.title,
            onDismiss = { courseCreateActionsOpen = false },
            actions = listOf(
                PremiumAction("New folder", Icons.Rounded.CreateNewFolder, section = "CREATE") {
                    courseCreateActionsOpen = false
                    creatingFolder = true
                },
                PremiumAction("New note", Icons.Rounded.Description, section = "CREATE") {
                    courseCreateActionsOpen = false
                    onNewNote()
                },
                PremiumAction("New sticky note", Icons.Rounded.StickyNote2, section = "CREATE") {
                    courseCreateActionsOpen = false
                    creatingSticky = true
                },
                PremiumAction("New concept card", Icons.Rounded.Lightbulb, section = "CREATE") {
                    courseCreateActionsOpen = false
                    creatingConcept = true
                },
            ),
        )
    }

    overviewActionsCourse?.let { course ->
        PremiumActionDialog(
            title = course.title,
            onDismiss = { overviewActionsCourse = null },
            actions = listOf(
                PremiumAction("Open course", Icons.Rounded.MenuBook) {
                    overviewActionsCourse = null
                    openedCourseId = course.id
                    onSelectCourse(course.id)
                },
                PremiumAction("Rename", Icons.Rounded.Edit) {
                    pendingOverviewAction = PendingOverviewCourseAction(course, OverviewCourseActionType.Rename)
                    overviewActionsCourse = null
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    pendingOverviewAction = PendingOverviewCourseAction(course, OverviewCourseActionType.Delete)
                    overviewActionsCourse = null
                },
            ),
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
private fun CoursesMobileWebOverview(
    courses: List<CourseEntity>,
    noteCountsByCourse: Map<String, Int>,
    onOpen: (CourseEntity) -> Unit,
    onManage: (CourseEntity) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(
            start = VaultSpacing.lg,
            top = VaultSpacing.md,
            end = VaultSpacing.lg,
            bottom = VaultSpacing.huge,
        ),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        item(key = "courses_header") {
            Column(modifier = Modifier.padding(start = 44.dp)) {
                Text(
                    text = "Courses",
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = if (courses.size == 1) "1 course" else "${courses.size} courses",
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (courses.isEmpty()) {
            item(key = "courses_empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.MenuBook, null, modifier = Modifier.size(28.dp), tint = colors.textMuted)
                    Text(
                        text = "No courses yet",
                        modifier = Modifier.padding(top = 12.dp),
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
        } else {
            items(courses, key = { "course_${it.id}" }) { course ->
                CourseMobileWebCard(
                    course = course,
                    noteCount = noteCountsByCourse[course.id] ?: 0,
                    onOpen = { onOpen(course) },
                    onManage = { onManage(course) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseMobileWebCard(
    course: CourseEntity,
    noteCount: Int,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.sm,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.title,
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = courseNoteCountLabel(noteCount),
                        modifier = Modifier.padding(top = 2.dp),
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = onManage, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.MoreVert, "Manage ${course.title}", modifier = Modifier.size(18.dp), tint = colors.textMuted)
                }
            }
        }
    }
}

internal fun courseNoteCountLabel(noteCount: Int): String =
    if (noteCount == 1) "1 note" else "$noteCount notes"

@Composable
private fun CourseHeader(
    active: CourseEntity,
    noteCount: Int,
    continueTitle: String?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onContinue: () -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
) {
    val colors = VaultThemeTokens.colors
    var actionsOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<CourseDeferredAction?>(null) }

    LaunchedEffect(actionsOpen, pendingAction) {
        val pending = pendingAction ?: return@LaunchedEffect
        if (actionsOpen) return@LaunchedEffect
        delay(180)
        when (pending) {
            CourseDeferredAction.RenameCourse -> onRename()
            CourseDeferredAction.DeleteCourse -> onDelete()
            else -> Unit
        }
        pendingAction = null
    }
    Column(
        modifier = Modifier.padding(
            start = 60.dp,
            top = VaultSpacing.md,
            end = VaultSpacing.lg,
            bottom = VaultSpacing.sm,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = active.title,
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = courseNoteCountLabel(noteCount),
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
            IconButton(onClick = { actionsOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, "Course actions", tint = colors.textSecondary)
            }
        }
        continueTitle?.let {
            Spacer(Modifier.height(10.dp))
            ContinueLessonCard(continueTitle = it, onContinue = onContinue)
        }
    }

    if (actionsOpen) {
        PremiumActionDialog(
            title = active.title,
            onDismiss = { actionsOpen = false },
            actions = listOf(
                PremiumAction("Rename course", Icons.Rounded.Edit) {
                    pendingAction = CourseDeferredAction.RenameCourse
                    actionsOpen = false
                },
                PremiumAction("Delete course", Icons.Rounded.Delete, destructive = true) {
                    pendingAction = CourseDeferredAction.DeleteCourse
                    actionsOpen = false
                },
            ),
        )
    }
}

@Composable
private fun ContinueLessonCard(
    continueTitle: String,
    onContinue: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(horizontal = VaultSpacing.md, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = VaultShapes.sm,
                color = colors.accent,
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Description, null, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("CONTINUE", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.W700, letterSpacing = 0.6.sp)
                Text(continueTitle, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.W700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Continue reading", color = colors.textMuted, fontSize = 10.5.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
    }
    val modifier = Modifier
        .fillMaxWidth()

    Surface(
        onClick = onContinue,
        modifier = modifier,
        shape = VaultShapes.sm,
        color = colors.accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.18f)),
        content = content,
    )
}

@Composable
private fun CourseFloatingCreateButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = VaultShapes.pill,
        color = colors.accent,
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription, modifier = Modifier.size(25.dp))
        }
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
    onEdit: (CourseConceptCardEntity) -> Unit,
    onDelete: (CourseConceptCardEntity) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        CourseSupportingSectionLabel(label = "CONCEPT CARDS", count = concepts.size)
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
private fun CourseSupportingSectionLabel(label: String, count: Int) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.7.sp,
        )
        Text(count.toString(), color = colors.textMuted, fontSize = 11.sp)
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
        modifier = modifier.height(64.dp),
        shape = VaultShapes.sm,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                Spacer(Modifier.width(VaultSpacing.xxs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(concept.term, color = colors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        concept.definition.ifBlank { concept.arabicTerm.orEmpty() },
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    if (menuOpen) {
        PremiumActionDialog(
            title = concept.term,
            onDismiss = { menuOpen = false },
            actions = listOf(
                PremiumAction("Edit", Icons.Rounded.Edit) {
                    menuOpen = false
                    onEdit()
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    menuOpen = false
                    onDelete()
                },
            ),
        )
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
