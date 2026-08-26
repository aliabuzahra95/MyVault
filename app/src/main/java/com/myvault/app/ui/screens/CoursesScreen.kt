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
import com.myvault.app.ui.components.CompactWorkspaceHeader
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
    BackHandler(enabled = openedCourse != null) { openedCourseId = null }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            openedCourse == null -> CoursesMobileWebOverview(
                courses = uiState.courses,
                noteCountsByCourse = uiState.noteCountsByCourse,
                conceptCountsByCourse = uiState.conceptCountsByCourse,
                onCreate = { creatingCourse = true },
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
            fabBottomPadding = fabBottomPadding,
            topContent = {
                CourseHeader(
                    active = uiState.activeCourse,
                    continueTitle = uiState.continueNoteTitle,
                    onBack = { openedCourseId = null },
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
        }
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
    conceptCountsByCourse: Map<String, Int>,
    onCreate: () -> Unit,
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
            CompactWorkspaceHeader(
                title = "Courses",
                metadata = if (courses.size == 1) "1 course" else "${courses.size} courses",
            ) {
                CompactPrimaryAction(
                    icon = Icons.Rounded.Add,
                    description = "Create course",
                    onClick = onCreate,
                )
            }
            Spacer(Modifier.height(VaultSpacing.md))
        }

        if (courses.isEmpty()) {
            item(key = "courses_empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    color = colors.surface,
                    shape = VaultShapes.sm,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(VaultSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = colors.accent,
                        )
                        Text(
                            text = "No courses yet",
                            modifier = Modifier.padding(top = 16.dp),
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = "Create a course for its lesson folders, notes, sticky notes, and concepts.",
                            modifier = Modifier.padding(top = 7.dp),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Surface(
                            onClick = onCreate,
                            modifier = Modifier.padding(top = 20.dp),
                            color = colors.accent,
                            contentColor = Color.White,
                            shape = VaultShapes.sm,
                        ) {
                            Text(
                                text = "Create course",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                fontWeight = FontWeight.W700,
                            )
                        }
                    }
                }
            }
        } else {
            items(courses, key = { "course_${it.id}" }) { course ->
                CourseMobileWebCard(
                    course = course,
                    noteCount = noteCountsByCourse[course.id] ?: 0,
                    conceptCount = conceptCountsByCourse[course.id] ?: 0,
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
    conceptCount: Int,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.sm,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .padding(VaultSpacing.md),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        color = colors.accent.copy(alpha = 0.10f),
                        shape = VaultShapes.sm,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, null, modifier = Modifier.size(23.dp), tint = colors.accent)
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp, top = 1.dp)) {
                        Text(
                            text = course.title,
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W800,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Course workspace",
                            modifier = Modifier.padding(top = 4.dp),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onManage) {
                        Icon(Icons.Rounded.MoreVert, "Manage ${course.title}", tint = colors.textSecondary)
                    }
                }
                Row(
                    modifier = Modifier.padding(top = VaultSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CourseCountTile(
                        value = noteCount,
                        label = if (noteCount == 1) "Note" else "Notes",
                        icon = Icons.Rounded.Description,
                        modifier = Modifier.weight(1f),
                    )
                    CourseCountTile(
                        value = conceptCount,
                        label = if (conceptCount == 1) "Concept" else "Concepts",
                        icon = Icons.Rounded.Lightbulb,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseCountTile(
    value: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(modifier = modifier, color = colors.bg, shape = VaultShapes.sm) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(17.dp), tint = colors.textMuted)
            Column {
                Text(value.toString(), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.W800)
                Text(label, color = colors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CourseHeader(
    active: CourseEntity,
    continueTitle: String?,
    onBack: () -> Unit,
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
    var addOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<CourseDeferredAction?>(null) }

    LaunchedEffect(addOpen, actionsOpen, pendingAction) {
        val pending = pendingAction ?: return@LaunchedEffect
        if (addOpen || actionsOpen) return@LaunchedEffect
        delay(180)
        when (pending) {
            CourseDeferredAction.NewFolder -> onNewFolder()
            CourseDeferredAction.NewNote -> onNewNote()
            CourseDeferredAction.NewSticky -> onNewSticky()
            CourseDeferredAction.NewConcept -> onNewConcept()
            CourseDeferredAction.RenameCourse -> onRename()
            CourseDeferredAction.NewCourse -> onCreate()
            CourseDeferredAction.DeleteCourse -> onDelete()
        }
        pendingAction = null
    }
    Column(
        modifier = Modifier.padding(
            start = VaultSpacing.lg,
            top = VaultSpacing.md,
            end = VaultSpacing.lg,
            bottom = VaultSpacing.lg,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.ArrowBack, "Back to courses", tint = colors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = active.title,
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Course workspace",
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            CompactPrimaryAction(
                icon = Icons.Rounded.Add,
                description = "Add to course",
                onClick = { addOpen = true },
                buttonSize = 42.dp,
            )
            IconButton(onClick = { actionsOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, "Course actions", tint = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(VaultSpacing.md))
        ContinueLessonCard(
            continueTitle = continueTitle,
            onContinue = onContinue,
        )
    }

    if (addOpen) {
        PremiumActionDialog(
            title = "Add to ${active.title}",
            onDismiss = { addOpen = false },
            actions = listOf(
                PremiumAction("New folder", Icons.Rounded.CreateNewFolder) {
                    pendingAction = CourseDeferredAction.NewFolder
                    addOpen = false
                },
                PremiumAction("New note", Icons.Rounded.Description) {
                    pendingAction = CourseDeferredAction.NewNote
                    addOpen = false
                },
                PremiumAction("New sticky note", Icons.Rounded.StickyNote2) {
                    pendingAction = CourseDeferredAction.NewSticky
                    addOpen = false
                },
                PremiumAction("New concept card", Icons.Rounded.Lightbulb) {
                    pendingAction = CourseDeferredAction.NewConcept
                    addOpen = false
                },
            ),
        )
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
                PremiumAction("New course", Icons.Rounded.Add) {
                    pendingAction = CourseDeferredAction.NewCourse
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
        modifier = modifier.height(124.dp),
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
