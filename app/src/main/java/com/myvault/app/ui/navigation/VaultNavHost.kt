package com.myvault.app.ui.navigation

import android.widget.Toast
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.repository.DashboardActivityKind
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.preferences.WORKSPACE_ISLAMIC_CORPUS
import com.myvault.app.data.preferences.WORKSPACE_PERSONAL
import com.myvault.app.data.sync.DriveRestoreStage
import com.myvault.app.data.sync.DriveSyncOperation
import com.myvault.app.ui.components.NarrationMiniPlayer
import com.myvault.app.ui.components.VaultExplorerActionHost
import com.myvault.app.ui.components.VaultExplorerMoveTarget
import com.myvault.app.ui.components.VaultMobileWebNavigationItem
import com.myvault.app.ui.components.VaultMobileWebApplicationDestination
import com.myvault.app.ui.components.VaultMobileWebExplorerNode
import com.myvault.app.ui.components.VaultMobileWebExplorerNodeType
import com.myvault.app.ui.components.VaultMobileWebExplorerSection
import com.myvault.app.ui.components.VaultMobileWebShell
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.screens.AttachmentViewerScreen
import com.myvault.app.ui.screens.AttachmentsScreen
import com.myvault.app.ui.screens.CoursesScreen
import com.myvault.app.ui.screens.EditorScreen
import com.myvault.app.ui.screens.FolderViewScreen
import com.myvault.app.ui.screens.HomeScreen
import com.myvault.app.ui.screens.LibraryFolderScreen
import com.myvault.app.ui.screens.LibraryScreen
import com.myvault.app.ui.screens.FrozenMemoriseScreen
import com.myvault.app.ui.screens.FrozenDashboardScreen
import com.myvault.app.ui.screens.FrozenFavouritesScreen
import com.myvault.app.ui.screens.PdfActivityFeedScreen
import com.myvault.app.ui.screens.QuranShellScreen
import com.myvault.app.ui.screens.QuranReflectionsHubScreen
import com.myvault.app.ui.screens.ReadingScreen
import com.myvault.app.ui.screens.SearchScreen
import com.myvault.app.ui.screens.SettingsScreen
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.viewmodel.AttachmentsViewModel
import com.myvault.app.ui.viewmodel.AttachmentViewerViewModel
import com.myvault.app.ui.viewmodel.CoursesViewModel
import com.myvault.app.ui.viewmodel.CoursesUiState
import com.myvault.app.ui.viewmodel.DashboardActivityViewModel
import com.myvault.app.ui.viewmodel.FolderViewModel
import com.myvault.app.ui.viewmodel.HomeViewModel
import com.myvault.app.ui.viewmodel.HomeUiState
import com.myvault.app.ui.viewmodel.LibraryViewModel
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.MemoriseViewModel
import com.myvault.app.ui.viewmodel.NarrationViewModel
import com.myvault.app.ui.viewmodel.NoteViewModel
import com.myvault.app.ui.viewmodel.PdfActivityFeedViewModel
import com.myvault.app.ui.viewmodel.QuranReaderViewModel
import com.myvault.app.ui.viewmodel.QuranReflectionsViewModel
import com.myvault.app.ui.viewmodel.SearchViewModel
import com.myvault.app.ui.viewmodel.SettingsViewModel
import com.myvault.app.ui.viewmodel.ShellPreferencesViewModel
import kotlinx.coroutines.launch

@Composable
fun VaultNavHost(
    pendingOpenNoteId: String? = null,
    pendingOpenNoteCourseId: String? = null,
    pendingOpenNoteQuickFocus: Boolean = false,
    onPendingOpenNoteConsumed: () -> Unit = {},
    pendingOpenQuranVerseKey: String? = null,
    onPendingOpenQuranConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentAttachmentId = currentBackStackEntry?.arguments?.getString("attachmentId")
    var attachmentViewerOwnsHeader by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute, currentAttachmentId) {
        if (currentRoute == VaultDestination.AttachmentViewer.route) {
            // PDFs own the compact reader header. Start in that mode so the PDF
            // renderer never mounts beneath the shell's legacy menu band.
            attachmentViewerOwnsHeader = true
        }
    }
    var selectedIslamicRootMode by rememberSaveable { mutableStateOf(VaultRootMode.Study.name) }
    var selectedPersonalRootMode by rememberSaveable { mutableStateOf(VaultRootMode.Personal.name) }
    var pendingQuranVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMemoriseVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMemoriseAutoRecord by rememberSaveable { mutableStateOf(false) }
    var pendingCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSettingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    val narrationViewModel: NarrationViewModel = hiltViewModel()
    val narrationState by narrationViewModel.narrationState.collectAsStateWithLifecycle()
    val narrationMiniPlayerVisibility = remember { MutableTransitionState(false) }
    var narrationMiniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val narrationMiniPlayerHeight = with(density) { narrationMiniPlayerHeightPx.toDp() }
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(narrationState.isActive) {
        narrationMiniPlayerVisibility.targetState = narrationState.isActive
    }
    val narrationMiniPlayerOccupiesSpace =
        narrationMiniPlayerVisibility.currentState || narrationMiniPlayerVisibility.targetState
    val narrationOccupiedBottom = if (narrationMiniPlayerOccupiesSpace) {
        systemBottomInset + 12.dp + narrationMiniPlayerHeight
    } else {
        0.dp
    }
    val floatingActionBottomPadding = if (narrationMiniPlayerOccupiesSpace) {
        narrationOccupiedBottom + 10.dp
    } else {
        18.dp
    }
    val shellViewModel: ShellPreferencesViewModel = hiltViewModel()
    val preferences by shellViewModel.userPreferences.collectAsStateWithLifecycle()
    val driveRestoreState by shellViewModel.driveRestoreState.collectAsStateWithLifecycle()
    var previousBackupActive by remember { mutableStateOf(false) }
    val homeViewModel: HomeViewModel = hiltViewModel()
    val studyState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val personalState by homeViewModel.personalUiState.collectAsStateWithLifecycle()
    val coursesViewModel: CoursesViewModel = hiltViewModel()
    val coursesState by coursesViewModel.uiState.collectAsStateWithLifecycle()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val dashboardActivityViewModel: DashboardActivityViewModel = hiltViewModel()
    val dashboardActivityState by dashboardActivityViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val routeMotionDisabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val routeMotionDistancePx = with(density) { 18.dp.roundToPx() } *
        if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val routeMotionSpec = tween<IntOffset>(
        durationMillis = 210,
        easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f),
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    var explorerActionTarget by remember { mutableStateOf<ExplorerActionTarget?>(null) }
    var corpusSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(driveRestoreState.active, driveRestoreState.progress.stage, driveRestoreState.operation) {
        val backupActive = driveRestoreState.active && driveRestoreState.operation == DriveSyncOperation.Backup
        if (
            previousBackupActive &&
            !driveRestoreState.active &&
            driveRestoreState.operation == DriveSyncOperation.Backup
        ) {
            val message = if (driveRestoreState.progress.stage == DriveRestoreStage.Complete) {
                "Backup complete"
            } else {
                driveRestoreState.message ?: driveRestoreState.progress.message.ifBlank { "Backup failed" }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        previousBackupActive = backupActive
    }

    LaunchedEffect(preferences.workspace, libraryViewModel) {
        libraryViewModel.setLibraryMode(
            if (preferences.workspace == WORKSPACE_PERSONAL) FOLDER_MODE_PERSONAL_LIBRARY else "library",
        )
    }

    val rootModes = remember(preferences.workspace) { preferences.workspace.rootModes() }
    val selectedRootModeName = if (preferences.workspace == WORKSPACE_PERSONAL) {
        selectedPersonalRootMode
    } else {
        selectedIslamicRootMode
    }
    val selectedRootIndex = rootModes.indexOfFirst { it.name == selectedRootModeName }.coerceAtLeast(0)
    val shellNavigationItems = remember(rootModes) {
        rootModes.map { mode -> VaultMobileWebNavigationItem(mode.label, mode.icon) }
    }
    val shellExplorerSections = remember(
        rootModes,
        studyState.workspace,
        personalState.workspace,
        libraryState.allFolders,
        libraryState.files,
        coursesState.courses,
        coursesState.treesByCourse,
    ) {
        buildExplorerSections(
            modes = rootModes,
            studyState = studyState,
            personalState = personalState,
            libraryState = libraryState,
            coursesState = coursesState,
        )
    }
    val selectedExplorerNodeId = currentBackStackEntry?.arguments?.getString("noteId")
        ?: currentBackStackEntry?.arguments?.getString("attachmentId")
        ?: currentBackStackEntry?.arguments?.getString("folderId")
        ?: currentBackStackEntry?.arguments?.getString("libraryFolderId")
        ?: coursesState.activeCourse?.id
            ?.takeIf { currentRoute == VaultDestination.Knowledge.route && selectedRootModeName == VaultRootMode.Courses.name }
            ?.let { COURSE_EXPLORER_PREFIX + it }

    fun openNote(noteId: String) {
        navController.navigate(
            if (preferences.defaultNoteView == "editing") {
                VaultDestination.Editor.route(noteId)
            } else {
                VaultDestination.Reading.route(noteId)
            },
        )
    }

    fun selectRootMode(mode: VaultRootMode) {
        if (preferences.workspace == WORKSPACE_PERSONAL) {
            selectedPersonalRootMode = mode.name
        } else if (mode != VaultRootMode.Personal) {
            selectedIslamicRootMode = mode.name
        }
        if (currentRoute != VaultDestination.Knowledge.route) {
            navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        }
    }

    fun revealCorpusFolder(mode: VaultRootMode, folderId: String) {
        val tree = when (mode) {
            VaultRootMode.Study -> studyState.workspace
            VaultRootMode.Personal -> personalState.workspace
            else -> emptyList()
        }
        val folderPath = tree.findTreeItemPath(folderId)
            .orEmpty()
            .filter { it.type == VaultTreeItemType.Folder }
            .map { it.id }
        homeViewModel.revealFolderPath(
            if (mode == VaultRootMode.Personal) FOLDER_MODE_PERSONAL else FOLDER_MODE_STUDY,
            folderPath,
        )
        if (mode == VaultRootMode.Personal) {
            selectedPersonalRootMode = VaultRootMode.Personal.name
            shellViewModel.setWorkspace(WORKSPACE_PERSONAL)
        } else {
            selectedIslamicRootMode = VaultRootMode.Study.name
            shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
        }
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
    }

    fun revealCourseLocation(courseId: String, folderId: String? = null) {
        val folderPath = folderId
            ?.let { coursesState.treesByCourse[courseId].orEmpty().findTreeItemPath(it) }
            .orEmpty()
            .filter { it.type == VaultTreeItemType.Folder }
            .map { it.id }
        pendingCourseId = courseId
        coursesViewModel.revealFolderPath(courseId, folderPath)
        shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
        selectedIslamicRootMode = VaultRootMode.Courses.name
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
    }

    fun revealLibraryFolder(
        folderId: String,
        libraryMode: String = if (preferences.workspace == WORKSPACE_PERSONAL) FOLDER_MODE_PERSONAL_LIBRARY else "library",
    ) {
        val personalLibrary = libraryMode == FOLDER_MODE_PERSONAL_LIBRARY
        shellViewModel.setWorkspace(if (personalLibrary) WORKSPACE_PERSONAL else WORKSPACE_ISLAMIC_CORPUS)
        libraryViewModel.setLibraryMode(libraryMode)
        libraryViewModel.revealFolder(folderId)
        if (personalLibrary) {
            selectedPersonalRootMode = VaultRootMode.Library.name
        } else {
            selectedIslamicRootMode = VaultRootMode.Library.name
        }
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
    }

    fun openExplorerNode(mode: VaultRootMode, node: VaultMobileWebExplorerNode) {
        when (mode) {
            VaultRootMode.Study, VaultRootMode.Personal -> when (node.type) {
                VaultMobileWebExplorerNodeType.Folder -> revealCorpusFolder(mode, node.id)
                VaultMobileWebExplorerNodeType.Note -> {
                    selectRootMode(mode)
                    openNote(node.id)
                }
                VaultMobileWebExplorerNodeType.Document -> Unit
            }
            VaultRootMode.Library -> {
                selectRootMode(mode)
                when (node.type) {
                    VaultMobileWebExplorerNodeType.Folder -> revealLibraryFolder(
                        node.id,
                        if (preferences.workspace == WORKSPACE_PERSONAL) FOLDER_MODE_PERSONAL_LIBRARY else "library",
                    )
                    VaultMobileWebExplorerNodeType.Document -> navController.navigate(VaultDestination.AttachmentViewer.route(node.id))
                    VaultMobileWebExplorerNodeType.Note -> openNote(node.id)
                }
            }
            VaultRootMode.Courses -> {
                val coursePath = shellExplorerSections
                    .firstOrNull { section -> rootModes.getOrNull(section.navigationIndex) == VaultRootMode.Courses }
                    ?.nodes
                    ?.findExplorerNodePath(node.id)
                    .orEmpty()
                val courseId = coursePath.firstOrNull()
                    ?.id
                    ?.takeIf { it.startsWith(COURSE_EXPLORER_PREFIX) }
                    ?.removePrefix(COURSE_EXPLORER_PREFIX)
                when {
                    courseId == null -> Unit
                    node.id.startsWith(COURSE_EXPLORER_PREFIX) -> revealCourseLocation(courseId)
                    node.type == VaultMobileWebExplorerNodeType.Folder -> revealCourseLocation(courseId, node.id)
                    node.type == VaultMobileWebExplorerNodeType.Note -> {
                        revealCourseLocation(courseId)
                        openNote(node.id)
                    }
                }
            }
            else -> selectRootMode(mode)
        }
    }

    DisposableEffect(lifecycleOwner, narrationViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) narrationViewModel.saveProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun switchWorkspace(label: String) {
        val workspace = label.workspaceValue()
        if (workspace == WORKSPACE_PERSONAL) {
            selectedPersonalRootMode = VaultRootMode.Personal.name
        }
        shellViewModel.setWorkspace(workspace)
    }

    LaunchedEffect(pendingOpenNoteId, pendingOpenNoteCourseId, pendingOpenNoteQuickFocus) {
        val noteId = pendingOpenNoteId ?: return@LaunchedEffect
        pendingOpenNoteCourseId?.let { courseId ->
            revealCourseLocation(courseId)
        }
        navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = pendingOpenNoteQuickFocus))
        onPendingOpenNoteConsumed()
    }

    LaunchedEffect(pendingOpenQuranVerseKey) {
        val verseKey = pendingOpenQuranVerseKey ?: return@LaunchedEffect
        pendingQuranVerseKey = verseKey
        shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
        selectedIslamicRootMode = VaultRootMode.Quran.name
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        onPendingOpenQuranConsumed()
    }

    LaunchedEffect(currentRoute) {
        narrationViewModel.saveProgress()
    }

    LaunchedEffect(pendingQuranVerseKey, currentRoute, preferences.workspace) {
        if (
            pendingQuranVerseKey != null &&
            currentRoute == VaultDestination.Knowledge.route &&
            preferences.workspace == WORKSPACE_ISLAMIC_CORPUS
        ) {
            selectedIslamicRootMode = VaultRootMode.Quran.name
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VaultMobileWebShell(
            workspaceLabel = preferences.workspace.workspaceLabel(),
            workspaceKey = preferences.workspace,
            accountEmail = preferences.googleDriveAccountEmail,
            onWorkspaceSelected = ::switchWorkspace,
            items = shellNavigationItems,
            selectedIndex = selectedRootIndex,
            selectedExplorerNodeId = selectedExplorerNodeId,
            onItemSelected = { index -> selectRootMode(rootModes[index]) },
            onDashboardSelected = {
                navController.navigateToVaultRoot(VaultDestination.Dashboard.route)
            },
            onSearchSelected = { navController.navigateToVaultRoot(VaultDestination.Search.route) },
            onAttachmentsSelected = {
                navController.navigate(
                    VaultDestination.Attachments.route(
                        if (preferences.workspace == WORKSPACE_PERSONAL) FOLDER_MODE_PERSONAL else FOLDER_MODE_STUDY,
                    ),
                )
            },
            onFavouritesSelected = { navController.navigate(VaultDestination.Favourites.route) },
            onSettingsSelected = { navController.navigateToVaultRoot(VaultDestination.Settings.route) },
            onBackupSelected = {
                shellViewModel.pushGoogleDriveSync {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            },
            onThemeSelected = {
                shellViewModel.setTheme(
                    preferences.theme.quickToggle(),
                )
            },
            backupRunning = driveRestoreState.active,
            selectedApplicationDestination = when (currentRoute) {
                VaultDestination.Dashboard.route -> VaultMobileWebApplicationDestination.Dashboard
                VaultDestination.Search.route -> VaultMobileWebApplicationDestination.Search
                VaultDestination.Settings.route -> VaultMobileWebApplicationDestination.Settings
                else -> null
            },
            attachmentsSelected = currentRoute == VaultDestination.Attachments.route,
            favouritesSelected = currentRoute == VaultDestination.Favourites.route,
            explorerSections = shellExplorerSections,
            persistedExpandedExplorerKeys = preferences.explorerExpandedKeys,
            onPersistExpandedExplorerKeys = shellViewModel::setExplorerExpandedKeys,
            onExplorerNodeSelected = { sectionIndex, node ->
                openExplorerNode(rootModes[sectionIndex], node)
            },
            onExplorerAddSelected = { sectionIndex, node ->
                explorerActionTarget = ExplorerActionTarget(rootModes[sectionIndex], node, creationOnly = true)
            },
            onExplorerMoreSelected = { sectionIndex, node ->
                explorerActionTarget = ExplorerActionTarget(rootModes[sectionIndex], node)
            },
            contentStartsInMenuBar = currentRoute in setOf(
                VaultDestination.Dashboard.route,
                VaultDestination.Search.route,
                VaultDestination.Attachments.route,
                VaultDestination.Favourites.route,
                VaultDestination.Settings.route,
                VaultDestination.Editor.route,
                VaultDestination.Reading.route,
            ) || (currentRoute == VaultDestination.AttachmentViewer.route && attachmentViewerOwnsHeader) ||
                (currentRoute == VaultDestination.Knowledge.route &&
                    rootModes.getOrNull(selectedRootIndex) in setOf(
                        VaultRootMode.Courses,
                        VaultRootMode.Study,
                        VaultRootMode.Library,
                        VaultRootMode.Quran,
                        VaultRootMode.Memorise,
                    )),
            drawerGesturesEnabled = currentRoute != VaultDestination.AttachmentViewer.route,
            menuVisible = currentRoute !in setOf(
                VaultDestination.Dashboard.route,
                VaultDestination.Search.route,
                VaultDestination.Attachments.route,
                VaultDestination.Favourites.route,
                VaultDestination.Settings.route,
                VaultDestination.Editor.route,
                VaultDestination.Reading.route,
            ) && !(currentRoute == VaultDestination.AttachmentViewer.route && attachmentViewerOwnsHeader) &&
                !corpusSearchActive &&
                !(currentRoute == VaultDestination.Knowledge.route && rootModes.getOrNull(selectedRootIndex) in setOf(VaultRootMode.Quran, VaultRootMode.Memorise)),
        ) { onOpenNavigation ->
        NavHost(
            navController = navController,
            startDestination = VaultDestination.Dashboard.route,
            enterTransition = {
                if (routeMotionDisabled) EnterTransition.None else {
                    slideInHorizontally(routeMotionSpec) { routeMotionDistancePx } +
                        fadeIn(tween(210, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)))
                }
            },
            exitTransition = {
                if (routeMotionDisabled) ExitTransition.None else {
                    slideOutHorizontally(routeMotionSpec) { -routeMotionDistancePx } +
                        fadeOut(tween(170, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)))
                }
            },
            popEnterTransition = {
                if (routeMotionDisabled) EnterTransition.None else {
                    slideInHorizontally(routeMotionSpec) { -routeMotionDistancePx } +
                        fadeIn(tween(210, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)))
                }
            },
            popExitTransition = {
                if (routeMotionDisabled) ExitTransition.None else {
                    slideOutHorizontally(routeMotionSpec) { routeMotionDistancePx } +
                        fadeOut(tween(170, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)))
                }
            },
        ) {
        composable(VaultDestination.Knowledge.route) {
            key(preferences.workspace) {
                StudyLibraryPersonalShell(
                    workspace = preferences.workspace,
                    selectedRootModeName = if (preferences.workspace == WORKSPACE_PERSONAL) {
                        selectedPersonalRootMode
                    } else {
                        selectedIslamicRootMode
                    },
                    onRootModeChanged = { mode ->
                        if (preferences.workspace == WORKSPACE_PERSONAL) {
                            selectedPersonalRootMode = mode.name
                        } else if (mode != VaultRootMode.Personal) {
                            selectedIslamicRootMode = mode.name
                        }
                    },
                    rootBackHandlerEnabled = currentRoute == VaultDestination.Knowledge.route,
                    coursesContent = {
                    CoursesScreen(
                        uiState = coursesState,
                        backHandlerEnabled = currentRoute == VaultDestination.Knowledge.route,
                        requestedCourseId = pendingCourseId,
                        onRequestedCourseHandled = { pendingCourseId = null },
                        onSelectCourse = coursesViewModel::selectCourse,
                        onCreateCourse = coursesViewModel::createCourse,
                        onRenameCourse = coursesViewModel::renameCourse,
                        onDeleteCourse = coursesViewModel::deleteCourse,
                        onOpenNote = { noteId ->
                            coursesViewModel.openNote(noteId)
                            openNote(noteId)
                        },
                        onOpenFolder = { folderId -> coursesViewModel.setFolderExpanded(folderId, true) },
                        onNewNote = {
                            coursesViewModel.createNote { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        },
                        onNewNoteInFolder = { folderId ->
                            coursesViewModel.createNoteInFolder(folderId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        },
                        onNewFolder = coursesViewModel::createSubfolder,
                        onNewSubfolderInFolder = coursesViewModel::createSubfolderInFolder,
                        onUpdateChildFolder = coursesViewModel::updateChildFolder,
                        onMoveChildFolder = coursesViewModel::moveChildFolder,
                        onDeleteChildFolder = coursesViewModel::deleteChildFolder,
                        onFolderExpandedChange = coursesViewModel::setFolderExpanded,
                        onMoveItemInOrder = coursesViewModel::moveItemInOrder,
                        onRenameNote = coursesViewModel::renameNote,
                        onMoveNote = coursesViewModel::moveNote,
                        onMoveNoteToMode = coursesViewModel::moveNoteToMode,
                        onDeleteNote = coursesViewModel::deleteNote,
                        onSetNotePinned = coursesViewModel::setNotePinned,
                        onSetNoteFolderPinned = coursesViewModel::setNoteFolderPinned,
                        onSetNoteFavourite = coursesViewModel::setNoteFavourite,
                        onCreateSubNote = { parentId ->
                            coursesViewModel.createSubNote(parentId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        },
                        onCreateSticky = coursesViewModel::createSticky,
                        onUpdateSticky = coursesViewModel::updateSticky,
                        onDeleteSticky = coursesViewModel::deleteSticky,
                        onCreateConcept = coursesViewModel::createConcept,
                        onSaveConcept = coursesViewModel::saveConcept,
                        onDeleteConcept = coursesViewModel::deleteConcept,
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        fabBottomPadding = floatingActionBottomPadding,
                    )
                },
                studyContent = {
                    HomeScreen(
                        uiState = studyState,
                        onCorpusSearchActiveChange = { corpusSearchActive = it },
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = ::switchWorkspace,
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = { folderId ->
                            revealCorpusFolder(VaultRootMode.Study, folderId)
                        },
                        onNoteClick = ::openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId, mode = FOLDER_MODE_STUDY) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onNewFolderClick = { parentId, name, description ->
                            homeViewModel.createFolder(parentId = parentId, name = name, mode = FOLDER_MODE_STUDY, description = description) { }
                        },
                        onRenameFolderClick = { folderId, name, description ->
                            homeViewModel.updateFolderDetails(folderId, name, description)
                        },
                        onUpdateFolderColorClick = homeViewModel::updateFolderColor,
                        onMoveFolderClick = { folderId, parentId ->
                            homeViewModel.moveFolder(folderId, parentId)
                        },
                        onMoveFolderInOrderClick = { folderId, direction ->
                            homeViewModel.moveFolderInOrder(folderId, direction)
                        },
                        onMoveFolderToModeClick = { folderId, mode ->
                            homeViewModel.moveFolderToMode(folderId, mode)
                        },
                        onFolderExpandedChange = { folderId, expanded ->
                            homeViewModel.setFolderExpanded(folderId, expanded)
                        },
                        onPinnedExpandedChange = { expanded ->
                            homeViewModel.setPinnedExpanded(FOLDER_MODE_STUDY, expanded)
                        },
                        onDeleteFolderClick = { folderId ->
                            homeViewModel.deleteFolder(folderId)
                        },
                        onRenameNoteClick = { noteId, title ->
                            homeViewModel.renameNote(noteId, title)
                        },
                        onMoveNoteClick = { noteId, folderId ->
                            homeViewModel.moveNote(noteId, folderId)
                        },
                        onMoveNoteToModeClick = { noteId, mode ->
                            homeViewModel.moveNoteToMode(noteId, mode)
                        },
                        onDeleteNoteClick = { noteId ->
                            homeViewModel.deleteNote(noteId)
                        },
                        onSetNotePinnedClick = { noteId, pinned ->
                            homeViewModel.setNotePinned(noteId, pinned)
                        },
                        onSetNoteFolderPinnedClick = homeViewModel::setNoteFolderPinned,
                        onSetNoteFavouriteClick = { noteId, favourite ->
                            homeViewModel.setNoteFavourite(noteId, favourite)
                        },
                        onCreateSubNoteClick = { parentNoteId ->
                            homeViewModel.createSubNote(parentNoteId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        },
                        onNewStickyNoteClick = homeViewModel::createStickyNote,
                        onImportFileClick = { uri ->
                            homeViewModel.importDocument(uri, mode = FOLDER_MODE_STUDY) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onOpenAttachmentsClick = {
                            navController.navigate(VaultDestination.Attachments.route(FOLDER_MODE_STUDY))
                        },
                        onQuranReflectionsClick = {
                            navController.navigate(VaultDestination.QuranReflections.route)
                        },
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        currentFolderMode = FOLDER_MODE_STUDY,
                        fabBottomPadding = floatingActionBottomPadding,
                    )
                },
                quranContent = {
                    val quranViewModel: QuranReaderViewModel = hiltViewModel()
                    val quranState by quranViewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingQuranVerseKey) {
                        val verseKey = pendingQuranVerseKey ?: return@LaunchedEffect
                        quranViewModel.openBookmarkedAyah(verseKey)
                    }
                    QuranShellScreen(
                        onOpenNavigation = onOpenNavigation,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = ::switchWorkspace,
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        uiState = quranState,
                        onSelectSurah = quranViewModel::selectSurah,
                        onSetArabicFontPercent = quranViewModel::setArabicFontPercentFromSlider,
                        onSetTranslationFontPercent = quranViewModel::setTranslationFontPercent,
                        onSetTranslationEnabled = quranViewModel::setTranslationEnabled,
                        onSetTranslationSource = quranViewModel::setTranslationSource,
                        onSetTajweedEnabled = quranViewModel::setTajweedEnabled,
                        onLastReadAyahChanged = quranViewModel::updateLastReadPosition,
                        onToggleTafsir = quranViewModel::toggleTafsir,
                        onSelectTafsirSource = quranViewModel::selectTafsirSource,
                        onToggleBookmark = quranViewModel::toggleBookmark,
                        onCreateReflectionNote = { ayah, title, body ->
                            quranViewModel.createReflectionNoteForAyah(ayah, title, body) {
                                Toast.makeText(context, "Reflection saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdateReflection = quranViewModel::updateReflectionForAyah,
                        onDeleteReflection = quranViewModel::deleteReflection,
                        onOpenBookmark = quranViewModel::openBookmarkedAyah,
                        onOpenReflectionsHub = {
                            navController.navigate(VaultDestination.QuranReflections.route)
                        },
                        onOpenReciterPicker = quranViewModel::openReciterPicker,
                        onOpenReciterPreferencePicker = quranViewModel::openReciterPreferencePicker,
                        onDismissReciterPicker = quranViewModel::dismissReciterPicker,
                        onSelectAudioReciter = quranViewModel::playWithReciter,
                        onPlayAudioForAyah = quranViewModel::playAudioForAyah,
                        onToggleAudioPlayback = quranViewModel::toggleAudioPlayback,
                        onStopAudio = quranViewModel::stopAudio,
                        onSeekAudioTo = quranViewModel::seekAudioTo,
                        onSetAudioSpeed = quranViewModel::setAudioPlaybackSpeed,
                        onSkipAudioBy = quranViewModel::skipAudioBy,
                        onPlayAdjacentAudio = quranViewModel::playAdjacentAudio,
                        onChooseOtherReciter = quranViewModel::chooseOtherReciterForCurrentAudio,
                        onRefreshAudioDownloads = quranViewModel::refreshAudioDownloadStates,
                        onDownloadSurahAudio = quranViewModel::downloadSurahAudio,
                        loadAyahSearchIndex = quranViewModel::getAyahSearchIndex,
                        onMemoriseFromHere = { ayah ->
                            quranViewModel.startMemorizingAyah(ayah)
                            pendingMemoriseVerseKey = ayah.verseKey
                            pendingMemoriseAutoRecord = true
                            selectedIslamicRootMode = VaultRootMode.Memorise.name
                        },
                        onPendingScrollHandled = {
                            quranViewModel.consumePendingScrollVerse()
                            pendingQuranVerseKey = null
                        },
                        showNavigationHeader = false,
                    )
                },
                memoriseContent = {
                    val memoriseViewModel: MemoriseViewModel = hiltViewModel()
                    val memoriseState by memoriseViewModel.uiState.collectAsStateWithLifecycle()
                    val memoriseSessionState by memoriseViewModel.sessionState.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingMemoriseVerseKey) {
                        val verseKey = pendingMemoriseVerseKey ?: return@LaunchedEffect
                        memoriseViewModel.openSession(verseKey, autoRecord = pendingMemoriseAutoRecord)
                        pendingMemoriseVerseKey = null
                        pendingMemoriseAutoRecord = false
                    }
                    FrozenMemoriseScreen(
                        uiState = memoriseState,
                        sessionState = memoriseSessionState,
                        onOpenNavigation = onOpenNavigation,
                        onOpenSession = memoriseViewModel::openSession,
                        onOpenSurah = memoriseViewModel::openSurah,
                        onOpenWholeSurah = memoriseViewModel::openWholeSurah,
                        onCloseSession = memoriseViewModel::closeSession,
                        onNextAyah = memoriseViewModel::openNextAyah,
                        onConsumeAutoRecord = memoriseViewModel::consumeAutoRecordRequest,
                        onSetStatus = memoriseViewModel::setStatus,
                        onRecordAttempt = memoriseViewModel::recordAttempt,
                        onRecordSurahAttempt = memoriseViewModel::recordSurahAttempt,
                        onSurahPositionChanged = memoriseViewModel::updateSurahPosition,
                    )
                },
                libraryContent = {
                    LibraryScreen(
                        uiState = libraryState,
                        onCorpusSearchActiveChange = { corpusSearchActive = it },
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = ::switchWorkspace,
                        onFolderClick = { folderId -> revealLibraryFolder(folderId) },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onAnnotationClick = { attachmentId, pageIndex ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                        },
                        onReferenceNoteClick = { noteId ->
                            navController.navigate(VaultDestination.Reading.route(noteId))
                        },
                        onRenameAnnotation = libraryViewModel::renameAnnotation,
                        onMoveAnnotation = libraryViewModel::moveAnnotation,
                        onDeleteAnnotationNote = libraryViewModel::deleteAnnotationNote,
                        onDeleteAnnotation = libraryViewModel::deleteAnnotation,
                        onLinkAnnotationToStudyNote = libraryViewModel::linkAnnotationToStudyNote,
                        onCreateStudyNoteFromAnnotation = { annotationId ->
                            libraryViewModel.createStudyNoteFromAnnotation(annotationId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onPrepareStudyNoteLinks = libraryViewModel::prepareStudyNoteLinks,
                        onCreateFolder = { parentId, name ->
                            libraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = libraryViewModel::renameFolder,
                        onUpdateFolderColor = libraryViewModel::updateFolderColor,
                        onMoveFolder = libraryViewModel::moveFolder,
                        onMoveFolderInOrder = libraryViewModel::moveFolderInOrder,
                        onDeleteFolder = libraryViewModel::deleteFolder,
                        onFolderExpandedChange = libraryViewModel::setFolderExpanded,
                        onViewModeChange = libraryViewModel::setViewMode,
                        onImportFiles = { uris ->
                            libraryViewModel.importFiles(uris)
                        },
                        onImportFilesToFolder = libraryViewModel::importFilesToFolder,
                        onReplaceDuplicatePdf = libraryViewModel::replaceDuplicatePdf,
                        onSkipDuplicatePdf = libraryViewModel::skipDuplicatePdf,
                        onDismissImportMessage = libraryViewModel::clearImportMessage,
                        onRenameFile = libraryViewModel::renameFile,
                        onMoveFile = libraryViewModel::moveFile,
                        onSetFilePinned = libraryViewModel::setFilePinned,
                        onDeleteFile = libraryViewModel::deleteFile,
                        onExportFile = { fileId, uri ->
                            libraryViewModel.exportFile(fileId, uri) {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddAttachmentTag = libraryViewModel::addAttachmentTag,
                        onRemoveAttachmentTag = libraryViewModel::removeAttachmentTag,
                        onAddAnnotationTag = libraryViewModel::addAnnotationTag,
                        onRemoveAnnotationTag = libraryViewModel::removeAnnotationTag,
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onViewAllAnnotationsClick = {
                            navController.navigate(VaultDestination.PdfActivityFeed.route("library"))
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        showFullFileTitles = preferences.showFullFileTitles,
                        fabBottomPadding = floatingActionBottomPadding,
                    )
                },
                personalContent = {
                    HomeScreen(
                        uiState = personalState,
                        onCorpusSearchActiveChange = { corpusSearchActive = it },
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = ::switchWorkspace,
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = { folderId -> revealCorpusFolder(VaultRootMode.Personal, folderId) },
                        onNoteClick = ::openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId, mode = FOLDER_MODE_PERSONAL) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onNewFolderClick = { parentId, name, description ->
                            homeViewModel.createFolder(parentId = parentId, name = name, mode = FOLDER_MODE_PERSONAL, description = description) { }
                        },
                        onRenameFolderClick = { folderId, name, description ->
                            homeViewModel.updateFolderDetails(folderId, name, description)
                        },
                        onUpdateFolderColorClick = homeViewModel::updateFolderColor,
                        onMoveFolderClick = { folderId, parentId ->
                            homeViewModel.moveFolder(folderId, parentId)
                        },
                        onMoveFolderInOrderClick = { folderId, direction ->
                            homeViewModel.moveFolderInOrder(folderId, direction)
                        },
                        onMoveFolderToModeClick = { folderId, mode ->
                            homeViewModel.moveFolderToMode(folderId, mode)
                        },
                        onFolderExpandedChange = { folderId, expanded ->
                            homeViewModel.setFolderExpanded(folderId, expanded)
                        },
                        onPinnedExpandedChange = { expanded ->
                            homeViewModel.setPinnedExpanded(FOLDER_MODE_PERSONAL, expanded)
                        },
                        onDeleteFolderClick = { folderId ->
                            homeViewModel.deleteFolder(folderId)
                        },
                        onRenameNoteClick = { noteId, title ->
                            homeViewModel.renameNote(noteId, title)
                        },
                        onMoveNoteClick = { noteId, folderId ->
                            homeViewModel.moveNote(noteId, folderId)
                        },
                        onMoveNoteToModeClick = { noteId, mode ->
                            homeViewModel.moveNoteToMode(noteId, mode)
                        },
                        onDeleteNoteClick = { noteId ->
                            homeViewModel.deleteNote(noteId)
                        },
                        onSetNotePinnedClick = { noteId, pinned ->
                            homeViewModel.setNotePinned(noteId, pinned)
                        },
                        onSetNoteFolderPinnedClick = homeViewModel::setNoteFolderPinned,
                        onSetNoteFavouriteClick = { noteId, favourite ->
                            homeViewModel.setNoteFavourite(noteId, favourite)
                        },
                        onCreateSubNoteClick = { parentNoteId ->
                            homeViewModel.createSubNote(parentNoteId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        },
                        onNewStickyNoteClick = homeViewModel::createStickyNote,
                        onImportFileClick = { uri ->
                            homeViewModel.importDocument(uri, mode = FOLDER_MODE_PERSONAL) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onOpenAttachmentsClick = {
                            navController.navigate(VaultDestination.Attachments.route(FOLDER_MODE_PERSONAL))
                        },
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        currentFolderMode = FOLDER_MODE_PERSONAL,
                        fabBottomPadding = floatingActionBottomPadding,
                    )
                },
                personalLibraryContent = {
                    LibraryScreen(
                        uiState = libraryState,
                        onCorpusSearchActiveChange = { corpusSearchActive = it },
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = ::switchWorkspace,
                        onFolderClick = { folderId -> revealLibraryFolder(folderId) },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onAnnotationClick = { attachmentId, pageIndex ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                        },
                        onReferenceNoteClick = { noteId ->
                            navController.navigate(VaultDestination.Reading.route(noteId))
                        },
                        onRenameAnnotation = libraryViewModel::renameAnnotation,
                        onMoveAnnotation = libraryViewModel::moveAnnotation,
                        onDeleteAnnotationNote = libraryViewModel::deleteAnnotationNote,
                        onDeleteAnnotation = libraryViewModel::deleteAnnotation,
                        onLinkAnnotationToStudyNote = libraryViewModel::linkAnnotationToStudyNote,
                        onCreateStudyNoteFromAnnotation = { annotationId ->
                            libraryViewModel.createStudyNoteFromAnnotation(annotationId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onPrepareStudyNoteLinks = libraryViewModel::prepareStudyNoteLinks,
                        onCreateFolder = { parentId, name ->
                            libraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = libraryViewModel::renameFolder,
                        onUpdateFolderColor = libraryViewModel::updateFolderColor,
                        onMoveFolder = libraryViewModel::moveFolder,
                        onMoveFolderInOrder = libraryViewModel::moveFolderInOrder,
                        onDeleteFolder = libraryViewModel::deleteFolder,
                        onFolderExpandedChange = libraryViewModel::setFolderExpanded,
                        onViewModeChange = libraryViewModel::setViewMode,
                        onImportFiles = { uris ->
                            libraryViewModel.importFiles(uris)
                        },
                        onImportFilesToFolder = libraryViewModel::importFilesToFolder,
                        onReplaceDuplicatePdf = libraryViewModel::replaceDuplicatePdf,
                        onSkipDuplicatePdf = libraryViewModel::skipDuplicatePdf,
                        onDismissImportMessage = libraryViewModel::clearImportMessage,
                        onRenameFile = libraryViewModel::renameFile,
                        onMoveFile = libraryViewModel::moveFile,
                        onSetFilePinned = libraryViewModel::setFilePinned,
                        onDeleteFile = libraryViewModel::deleteFile,
                        onExportFile = { fileId, uri ->
                            libraryViewModel.exportFile(fileId, uri) {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddAttachmentTag = libraryViewModel::addAttachmentTag,
                        onRemoveAttachmentTag = libraryViewModel::removeAttachmentTag,
                        onAddAnnotationTag = libraryViewModel::addAnnotationTag,
                        onRemoveAnnotationTag = libraryViewModel::removeAnnotationTag,
                        onThemeClick = {
                            shellViewModel.setTheme(
                                preferences.theme.quickToggle(),
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onViewAllAnnotationsClick = {
                            navController.navigate(VaultDestination.PdfActivityFeed.route(FOLDER_MODE_PERSONAL_LIBRARY))
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        showFullFileTitles = preferences.showFullFileTitles,
                        fabBottomPadding = floatingActionBottomPadding,
                    )
                    },
                )
            }
        }
        composable(VaultDestination.Dashboard.route) {
            FrozenDashboardScreen(
                continueFile = libraryState.continueReading,
                activityState = dashboardActivityState,
                pinnedNotes = if (preferences.workspace == WORKSPACE_PERSONAL) personalState.pinnedNotes else studyState.pinnedNotes,
                pinnedFiles = libraryState.pinnedFiles,
                quranContinue = if (preferences.workspace == WORKSPACE_PERSONAL) null else studyState.quranContinue,
                reflections = if (preferences.workspace == WORKSPACE_PERSONAL) emptyList() else studyState.quranReflectionItems,
                onMenuClick = onOpenNavigation,
                onOpenNote = ::openNote,
                onOpenFile = { navController.navigate(VaultDestination.AttachmentViewer.route(it)) },
                onOpenQuran = { verseKey ->
                    pendingQuranVerseKey = verseKey
                    shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
                    selectedIslamicRootMode = VaultRootMode.Quran.name
                    navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
                },
                onOpenActivity = { activity ->
                    when (activity.kind) {
                        DashboardActivityKind.Note -> openNote(activity.destinationId)
                        DashboardActivityKind.Library -> navController.navigate(
                            VaultDestination.AttachmentViewer.route(activity.destinationId, activity.pageIndex ?: -1),
                        )
                        DashboardActivityKind.Course -> {
                            activity.courseId?.let { revealCourseLocation(it, activity.folderId) }
                            openNote(activity.destinationId)
                        }
                    }
                },
                onOpenReflection = { reflection ->
                    pendingQuranVerseKey = reflection.verseKey
                    shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
                    selectedIslamicRootMode = VaultRootMode.Quran.name
                    navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
                },
                onViewAllReflections = {
                    navController.navigate(VaultDestination.QuranReflections.route)
                },
            )
        }
        composable(
            route = VaultDestination.LibraryFolder.route,
            arguments = listOf(
                navArgument("libraryFolderId") { type = NavType.StringType },
                navArgument("libraryMode") {
                    type = NavType.StringType
                    defaultValue = "library"
                },
            ),
        ) {
            val viewModel: LibraryViewModel = hiltViewModel()
            val homeViewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val libraryMode = it.arguments?.getString("libraryMode") ?: "library"
            LibraryFolderScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onFolderClick = { folderId ->
                    navController.navigate(VaultDestination.LibraryFolder.route(folderId, libraryMode))
                },
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onAnnotationClick = { attachmentId, pageIndex ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                },
                onReferenceNoteClick = { noteId ->
                    navController.navigate(VaultDestination.Reading.route(noteId))
                },
                onRenameAnnotation = viewModel::renameAnnotation,
                onMoveAnnotation = viewModel::moveAnnotation,
                onDeleteAnnotationNote = viewModel::deleteAnnotationNote,
                onDeleteAnnotation = viewModel::deleteAnnotation,
                onLinkAnnotationToStudyNote = viewModel::linkAnnotationToStudyNote,
                onCreateStudyNoteFromAnnotation = { annotationId ->
                    viewModel.createStudyNoteFromAnnotation(annotationId) { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId))
                    }
                },
                onPrepareStudyNoteLinks = viewModel::prepareStudyNoteLinks,
                onCreateFolder = { parentId, name ->
                    viewModel.createFolder(parentId = parentId, name = name)
                },
                onRenameFolder = viewModel::renameFolder,
                onUpdateFolderColor = viewModel::updateFolderColor,
                onMoveFolder = viewModel::moveFolder,
                onMoveFolderInOrder = viewModel::moveFolderInOrder,
                onDeleteFolder = viewModel::deleteFolder,
                onFolderExpandedChange = viewModel::setFolderExpanded,
                onViewModeChange = viewModel::setViewMode,
                onImportFiles = { uris ->
                    viewModel.importFiles(uris)
                },
                onImportFilesToFolder = viewModel::importFilesToFolder,
                onReplaceDuplicatePdf = viewModel::replaceDuplicatePdf,
                onSkipDuplicatePdf = viewModel::skipDuplicatePdf,
                onDismissImportMessage = viewModel::clearImportMessage,
                onRenameFile = viewModel::renameFile,
                onMoveFile = viewModel::moveFile,
                onSetFilePinned = viewModel::setFilePinned,
                onDeleteFile = viewModel::deleteFile,
                onExportFile = { fileId, uri ->
                    viewModel.exportFile(fileId, uri) {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddAttachmentTag = viewModel::addAttachmentTag,
                onRemoveAttachmentTag = viewModel::removeAttachmentTag,
                onAddAnnotationTag = viewModel::addAnnotationTag,
                onRemoveAnnotationTag = viewModel::removeAnnotationTag,
                onViewAllAnnotationsClick = {
                    navController.navigate(VaultDestination.PdfActivityFeed.route(libraryMode))
                },
                showFullFileTitles = preferences.showFullFileTitles,
            )
        }
        composable(
            route = VaultDestination.FolderView.route,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
        ) {
            val viewModel: FolderViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            FolderViewScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onSearchClick = { navController.navigate(VaultDestination.Search.route) },
                onNoteClick = { noteId ->
                    viewModel.recordCourseNoteOpened(noteId)
                    navController.navigate(
                        if (preferences.defaultNoteView == "editing") {
                            VaultDestination.Editor.route(noteId)
                        } else {
                            VaultDestination.Reading.route(noteId)
                        },
                    )
                },
                onFolderClick = { folderId -> navController.navigate(VaultDestination.FolderView.route(folderId)) },
                onNewNoteClick = {
                    viewModel.createNote { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId))
                    }
                },
                onNewSubfolderClick = { name, description ->
                    viewModel.createSubfolder(name, description) { folderId ->
                        navController.navigate(VaultDestination.FolderView.route(folderId))
                    }
                },
                onCreateNoteInFolderClick = { folderId ->
                    viewModel.createNoteInFolder(folderId) { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                    }
                },
                onCreateSubfolderInFolderClick = viewModel::createSubfolderInFolder,
                onUpdateChildFolderClick = viewModel::updateChildFolder,
                onMoveChildFolderClick = viewModel::moveChildFolder,
                onDeleteChildFolderClick = viewModel::deleteChildFolder,
                onUpdateFolderClick = viewModel::updateFolderDetails,
                onMoveCurrentFolderClick = viewModel::moveCurrentFolder,
                onDeleteCurrentFolderClick = {
                    viewModel.deleteCurrentFolder { navController.popBackStack() }
                },
                onFolderExpandedChange = viewModel::setFolderExpanded,
                onMoveItemInOrderClick = viewModel::moveItemInOrder,
                onRenameNoteClick = viewModel::renameNote,
                onMoveNoteClick = viewModel::moveNote,
                onMoveNoteToModeClick = viewModel::moveNoteToMode,
                onDeleteNoteClick = viewModel::deleteNote,
                onSetNotePinnedClick = { noteId, pinned ->
                    viewModel.setNotePinned(noteId, pinned)
                },
                onSetNoteFolderPinnedClick = viewModel::setNoteFolderPinned,
                onSetNoteFavouriteClick = viewModel::setNoteFavourite,
                onCreateSubNoteClick = { parentNoteId ->
                    viewModel.createSubNote(parentNoteId) { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                    }
                },
                onNewStickyNoteClick = viewModel::createStickyNote,
                onUpdateStickyNoteClick = viewModel::updateStickyNote,
                onDeleteStickyNoteClick = viewModel::deleteStickyNote,
                notePreviewLines = preferences.notePreview.toPreviewLines(),
                showFullNoteTitles = preferences.showFullNoteTitles,
                dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
            )
        }
        composable(
            route = VaultDestination.Editor.route,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("quickFocus") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val viewModel: NoteViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val formattingState by viewModel.formattingState.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.note?.id) {
                uiState.note?.id?.let(dashboardActivityViewModel::recordNoteOpened)
            }
            EditorScreen(
                uiState = uiState,
                formattingState = formattingState,
                onBackClick = { navController.popBackStack() },
                onMenuClick = onOpenNavigation,
                onTitleChange = viewModel::updateTitle,
                onContentChange = viewModel::saveRichText,
                onRunFormattingTool = viewModel::runFormattingTool,
                onClearFormattingResult = viewModel::clearFormattingResult,
                onFormattingProviderSelected = viewModel::setFormattingProvider,
                onFormattingModelSelected = viewModel::setFormattingModel,
                onAzureListenFromHere = viewModel::startAzureNarrationFromSelection,
                onAttachDocument = viewModel::attachDocument,
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onPinnedChange = viewModel::setPinned,
                onFavouriteChange = viewModel::setFavourite,
                onDeleteNote = {
                    viewModel.deleteNote {
                        navController.leaveDeletedNote()
                    }
                },
                onExportText = { uri -> viewModel.exportText(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onExportPdf = { uri -> viewModel.exportPdf(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onCreateTable = viewModel::createTable,
                onUpdateTableCell = viewModel::updateTableCell,
                onDeleteTable = viewModel::deleteTable,
                onNoteLinkClick = { noteId ->
                    navController.navigate(VaultDestination.Reading.route(noteId))
                },
                onSourceReferenceClick = { attachmentId, pageIndex ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                },
                onRemoveSourceReference = viewModel::removeSourceReference,
                onAddKnowledgeTag = viewModel::addKnowledgeTag,
                onRemoveKnowledgeTag = viewModel::removeKnowledgeTag,
                onRestoreVersion = viewModel::restoreVersion,
                bodyFontSizeSp = preferences.noteFontSize.toNoteBodyFontSizeSp(),
                autoFocusBody = backStackEntry.arguments?.getBoolean("quickFocus") == true,
            )
        }
        composable(
            route = VaultDestination.Reading.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            val viewModel: NoteViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val narrationState by viewModel.narrationState.collectAsStateWithLifecycle()
            val azureNarrationProgress by viewModel.azureNarrationProgress.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.note?.id) {
                uiState.note?.id?.let(dashboardActivityViewModel::recordNoteOpened)
            }
            ReadingScreen(
                uiState = uiState,
                narrationState = narrationState,
                azureNarrationProgress = azureNarrationProgress,
                onBackClick = { navController.popBackStack() },
                onEditClick = {
                    uiState.note?.id?.let { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId))
                    }
                },
                onMenuClick = onOpenNavigation,
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onAttachDocument = viewModel::attachDocument,
                onConfigureAzure = {
                    pendingSettingsSection = "azure_speech"
                    navController.navigate(VaultDestination.Settings.route)
                },
                onPinnedChange = viewModel::setPinned,
                onFavouriteChange = viewModel::setFavourite,
                onListenClick = viewModel::startNarration,
                onAzureListenClick = viewModel::startAzureNarration,
                onAzureResumeClick = viewModel::resumeAzureNarration,
                onDeviceListenClick = viewModel::startDeviceNarration,
                defaultNarrationProvider = preferences.narrationProvider,
                onDeleteNote = {
                    viewModel.deleteNote {
                        navController.leaveDeletedNote()
                    }
                },
                onExportText = { uri -> viewModel.exportText(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onExportPdf = { uri -> viewModel.exportPdf(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onNoteLinkClick = { noteId ->
                    navController.navigate(
                        if (preferences.defaultNoteView == "editing") {
                            VaultDestination.Editor.route(noteId)
                        } else {
                            VaultDestination.Reading.route(noteId)
                        },
                    )
                },
                onSourceReferenceClick = { attachmentId, pageIndex ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                },
                onRemoveSourceReference = viewModel::removeSourceReference,
                onAddKnowledgeTag = viewModel::addKnowledgeTag,
                onRemoveKnowledgeTag = viewModel::removeKnowledgeTag,
                onRestoreVersion = viewModel::restoreVersion,
                bodyFontSizeSp = preferences.noteFontSize.toNoteBodyFontSizeSp(),
                narrationMiniPlayerVisible = narrationMiniPlayerOccupiesSpace,
                narrationMiniPlayerHeight = narrationOccupiedBottom,
            )
        }
        composable(VaultDestination.Search.route) {
            val viewModel: SearchViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                uiState = uiState,
                files = libraryState.files,
                courses = coursesState.courses,
                onMenuClick = onOpenNavigation,
                onQueryChange = viewModel::setQuery,
                onNoteClick = { noteId ->
                    navController.navigate(
                        if (preferences.defaultNoteView == "editing") {
                            VaultDestination.Editor.route(noteId)
                        } else {
                            VaultDestination.Reading.route(noteId)
                        },
                    )
                },
                onFolderClick = { folder ->
                    if (folder.mode.contains("library", ignoreCase = true)) {
                        revealLibraryFolder(folder.id, folder.mode)
                    } else if (folder.mode.startsWith("course:")) {
                        revealCourseLocation(folder.mode.removePrefix("course:"), folder.id)
                    } else {
                        val mode = if (folder.mode == FOLDER_MODE_PERSONAL) VaultRootMode.Personal else VaultRootMode.Study
                        shellViewModel.setWorkspace(
                            if (mode == VaultRootMode.Personal) WORKSPACE_PERSONAL else WORKSPACE_ISLAMIC_CORPUS,
                        )
                        revealCorpusFolder(mode, folder.id)
                    }
                },
                onFileClick = { navController.navigate(VaultDestination.AttachmentViewer.route(it)) },
                onCourseClick = { courseId ->
                    coursesViewModel.selectCourse(courseId)
                    selectedIslamicRootMode = VaultRootMode.Courses.name
                    navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
                },
                onQuranClick = { verseKey ->
                    pendingQuranVerseKey = verseKey
                    shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
                    selectedIslamicRootMode = VaultRootMode.Quran.name
                    navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
                },
            )
        }
        composable(VaultDestination.Favourites.route) {
            val favourites = remember(preferences.workspace, studyState.workspace, personalState.workspace) {
                val tree = if (preferences.workspace == WORKSPACE_PERSONAL) personalState.workspace else studyState.workspace
                tree.flatMap { it.flattenFavouriteNotes() }
            }
            FrozenFavouritesScreen(
                favourites = favourites,
                onMenuClick = onOpenNavigation,
                onOpenNote = ::openNote,
            )
        }
        composable(VaultDestination.QuranReflections.route) {
            val viewModel: QuranReflectionsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            QuranReflectionsHubScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onReflectionClick = { reflection ->
                    pendingQuranVerseKey = reflection.verseKey
                    shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
                    selectedIslamicRootMode = VaultRootMode.Quran.name
                    navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
                },
            )
        }
        composable(
            route = VaultDestination.AttachmentViewer.route,
            arguments = listOf(
                navArgument("attachmentId") { type = NavType.StringType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) {
            val viewModel: AttachmentViewerViewModel = hiltViewModel()
            val attachment by viewModel.attachment.collectAsStateWithLifecycle()
            val resolvedInitialPageIndex by viewModel.resolvedInitialPageIndex.collectAsStateWithLifecycle()
            val pdfProgress by viewModel.pdfProgress.collectAsStateWithLifecycle()
            val pdfAnnotations by viewModel.pdfAnnotations.collectAsStateWithLifecycle()
            val pdfAnnotationSegments by viewModel.pdfAnnotationSegments.collectAsStateWithLifecycle()
            val studyNotes by viewModel.studyNotes.collectAsStateWithLifecycle()
            val pdfReferences by viewModel.pdfReferences.collectAsStateWithLifecycle()
            val pdfAnnotationTags by viewModel.annotationTags.collectAsStateWithLifecycle()
            val documentText by viewModel.documentText.collectAsStateWithLifecycle()
            val azureNarrationProgress by viewModel.azureNarrationProgress.collectAsStateWithLifecycle()
            LaunchedEffect(attachment?.id) {
                attachment?.id?.let(dashboardActivityViewModel::recordLibraryOpened)
            }
            AttachmentViewerScreen(
                attachment = attachment,
                pdfProgress = pdfProgress,
                pdfAnnotations = pdfAnnotations,
                pdfAnnotationSegments = pdfAnnotationSegments,
                studyNotes = studyNotes,
                pdfReferences = pdfReferences,
                pdfAnnotationTags = pdfAnnotationTags,
                documentText = documentText.text,
                documentTextLoading = documentText.isLoading,
                documentTextError = documentText.error,
                activeNarrationSentence = narrationState.activeSentence.takeIf {
                    it.isNotBlank() && narrationState.noteId == attachment?.id?.let { id -> "attachment:$id" }
                }.orEmpty(),
                azureNarrationProgress = azureNarrationProgress,
                initialPageIndex = resolvedInitialPageIndex,
                onBackClick = { navController.popBackStack() },
                onMenuClick = onOpenNavigation,
                onOwnHeaderChanged = { attachmentViewerOwnsHeader = it },
                onPdfProgressChanged = { pageIndex, pageCount ->
                    viewModel.updatePdfProgress(pageIndex, pageCount)
                    attachment?.id?.let { dashboardActivityViewModel.updateLibraryProgress(it, pageIndex, pageCount) }
                },
                onPdfFirstLoaded = viewModel::loadPdfSecondaryData,
                onAddPdfHighlight = viewModel::addPdfHighlight,
                onAddPdfSelectedTextAnnotation = viewModel::addPdfSelectedTextAnnotation,
                onUpdatePdfHighlightColor = viewModel::updatePdfHighlightColor,
                onUpdatePdfAnnotationNote = viewModel::updatePdfAnnotationNote,
                onAddPdfPageNote = viewModel::addPdfPageNote,
                onAddPdfTextBox = viewModel::addPdfTextBox,
                onUpdatePdfTextBox = viewModel::updatePdfTextBox,
                onUpdatePdfTextBoxBounds = viewModel::updatePdfTextBoxBounds,
                onDeletePdfAnnotation = viewModel::deletePdfAnnotation,
                onAddPdfAnnotationTag = viewModel::addAnnotationTag,
                onRemovePdfAnnotationTag = viewModel::removeAnnotationTag,
                onLinkPdfAnnotationToStudyNote = viewModel::linkAnnotationToStudyNote,
                onCreateStudyNoteFromPdfAnnotation = viewModel::createStudyNoteFromAnnotation,
                onOpenStudyNote = { noteId ->
                    navController.navigate(VaultDestination.Reading.route(noteId))
                },
                onStartDevicePdfNarration = viewModel::startDeviceNarration,
                onStartOpenAiPdfNarration = viewModel::startOpenAiNarration,
                onStartAzurePdfNarration = viewModel::startAzureNarration,
                onDeleteAttachment = {
                    viewModel.deleteAttachment {
                        Toast.makeText(context, "Attachment deleted", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                onExportAttachment = { uri ->
                    viewModel.exportAttachment(uri) {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                },
                onAzureListenClick = viewModel::startAzureNarration,
                onAzureResumeClick = viewModel::resumeAzureNarration,
                onAzureListenFromHere = viewModel::startAzureNarrationFromSelection,
                narrationMiniPlayerVisible = narrationMiniPlayerOccupiesSpace,
                narrationMiniPlayerHeight = narrationOccupiedBottom,
            )
        }
        composable(
            route = VaultDestination.Attachments.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) {
            val viewModel: AttachmentsViewModel = hiltViewModel()
            val attachments by viewModel.attachments.collectAsStateWithLifecycle()
            AttachmentsScreen(
                attachments = attachments,
                onMenuClick = onOpenNavigation,
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
            )
        }
        composable(VaultDestination.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
            val azureSpeechSettings by viewModel.azureSpeechSettings.collectAsStateWithLifecycle()
            val storageLabel by viewModel.storageLabel.collectAsStateWithLifecycle()
            val recentlyDeleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
            val recentlyDeletedLoaded by viewModel.recentlyDeletedLoaded.collectAsStateWithLifecycle()
            val driveRestoreState by viewModel.driveRestoreState.collectAsStateWithLifecycle()
            val supabaseSession by viewModel.supabaseSession.collectAsStateWithLifecycle()
            var backupMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            LaunchedEffect(viewModel) {
                viewModel.refreshStorage()
            }
            SettingsScreen(
                preferences = preferences,
                onBackClick = { navController.popBackStack() },
                onMenuClick = onOpenNavigation,
                onThemeSelected = viewModel::setTheme,
                workspaceLabel = preferences.workspace.workspaceLabel(),
                accountEmail = preferences.googleDriveAccountEmail,
                onMaterialYouEnabledChange = viewModel::setMaterialYouEnabled,
                onAccentColorSelected = viewModel::setAccentColor,
                onBackupSelected = { uri -> viewModel.exportBackup(uri) { backupMessage = it } },
                onRestoreSelected = { uri -> viewModel.restoreBackup(uri) { backupMessage = it } },
                onDashboardFontSizeSelected = viewModel::setDashboardFontSize,
                onNoteFontSizeSelected = viewModel::setNoteFontSize,
                onNotePreviewSelected = viewModel::setNotePreview,
                onShowFullNoteTitlesChanged = viewModel::setShowFullNoteTitles,
                onShowFullFileTitlesChanged = viewModel::setShowFullFileTitles,
                onDefaultNoteViewSelected = viewModel::setDefaultNoteView,
                azureSpeechSettings = azureSpeechSettings,
                onNarrationProviderSelected = viewModel::setNarrationProvider,
                onAzureSpeechSettingsSaved = viewModel::setAzureSpeechSettings,
                onSecurityLockChanged = viewModel::setSecurityLockEnabled,
                onSecurityLockTimeoutSelected = viewModel::setSecurityLockTimeout,
                storageLabel = storageLabel,
                recentlyDeleted = recentlyDeleted,
                recentlyDeletedLoaded = recentlyDeletedLoaded,
                onRecentlyDeletedOpened = viewModel::observeRecentlyDeleted,
                onRestoreDeletedNote = viewModel::restoreNote,
                onPermanentlyDeleteNote = { noteId -> viewModel.permanentlyDeleteNote(noteId) { backupMessage = it } },
                onRestoreDeletedFolder = viewModel::restoreFolder,
                onPermanentlyDeleteFolder = { folderId -> viewModel.permanentlyDeleteFolder(folderId) { backupMessage = it } },
                onPermanentlyDeleteAllDeleted = { viewModel.permanentlyDeleteAllRecentlyDeleted { backupMessage = it } },
                onPrepareGoogleDriveSignIn = { onReady ->
                    viewModel.prepareGoogleDriveSignIn(onReady) { backupMessage = it }
                },
                onGoogleDriveSignInResult = { data, onAuthorizationRequired ->
                    viewModel.handleGoogleDriveSignInResult(data, onAuthorizationRequired) { backupMessage = it }
                },
                onGoogleDriveConsentResult = { granted ->
                    viewModel.handleGoogleDriveConsentResult(granted) { backupMessage = it }
                },
                onGoogleDrivePush = { onAuthorizationRequired ->
                    viewModel.pushGoogleDriveSync(onAuthorizationRequired) { backupMessage = it }
                },
                onGoogleDriveForcePush = { onAuthorizationRequired ->
                    viewModel.forcePushGoogleDriveSync(onAuthorizationRequired) { backupMessage = it }
                },
                onGoogleDrivePull = { onAuthorizationRequired ->
                    viewModel.pullGoogleDriveSync(onAuthorizationRequired) { backupMessage = it }
                },
                onBackupSettingsOpened = viewModel::observeDriveRestoreState,
                formattingAccountEmail = supabaseSession.email,
                onFormattingAccountLogin = { email, password -> viewModel.signInFormattingAccount(email, password) { backupMessage = it } },
                onFormattingAccountLogout = { viewModel.signOutFormattingAccount { backupMessage = it } },
                driveRestoreState = driveRestoreState,
                backupMessage = backupMessage,
                onDismissBackupMessage = {
                    backupMessage = null
                    viewModel.dismissDriveRestoreMessage()
                },
                initialSection = pendingSettingsSection,
                onInitialSectionConsumed = { pendingSettingsSection = null },
            )
        }
        composable(
            route = VaultDestination.PdfActivityFeed.route,
            arguments = listOf(
                navArgument("libraryMode") {
                    type = NavType.StringType
                    defaultValue = "library"
                }
            )
        ) {
            val viewModel: PdfActivityFeedViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PdfActivityFeedScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onToggleExpanded = viewModel::toggleExpanded,
                onSearchQueryChange = viewModel::setSearchQuery,
                onActivityClick = { attachmentId, pageIndex ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId, pageIndex))
                },
                onToggleSelection = viewModel::toggleSelection,
                onClearSelection = viewModel::clearSelection,
                onUpdateActivityDetails = viewModel::updateActivityDetails,
                onDeleteSelected = viewModel::deleteSelected,
                onCreateStudyNoteFromSelected = { onCreated ->
                    viewModel.createStudyNoteFromSelected { noteId ->
                        onCreated(noteId)
                    }
                },
                onNavigateToEditor = { noteId ->
                    navController.navigate(VaultDestination.Editor.route(noteId))
                }
            )
        }
        }
        }
        explorerActionTarget?.let { target ->
            val node = target.node
            val section = shellExplorerSections.firstOrNull {
                rootModes.getOrNull(it.navigationIndex) == target.mode
            }
            val courseId = if (target.mode == VaultRootMode.Courses && node != null) {
                section?.nodes?.findExplorerNodePath(node.id)
                    ?.firstOrNull()
                    ?.id
                    ?.removePrefix(COURSE_EXPLORER_PREFIX)
            } else {
                null
            }
            val isCourse = node?.id?.startsWith(COURSE_EXPLORER_PREFIX) == true
            val isFolder = node?.type == VaultMobileWebExplorerNodeType.Folder
            val canCreateInside = node == null || isFolder
            val modeLabel = when (target.mode) {
                VaultRootMode.Personal -> "Personal"
                else -> target.mode.label
            }
            VaultExplorerActionHost(
                title = node?.label ?: "Add to $modeLabel",
                nodeType = node?.type,
                creationOnly = target.creationOnly,
                canCreateNote = when (target.mode) {
                    VaultRootMode.Study, VaultRootMode.Personal -> canCreateInside
                    VaultRootMode.Courses -> node != null && isFolder
                    else -> false
                },
                canCreateFolder = when (target.mode) {
                    VaultRootMode.Study, VaultRootMode.Personal, VaultRootMode.Library, VaultRootMode.Courses -> canCreateInside
                    else -> false
                },
                createFolderActionLabel = if (target.mode == VaultRootMode.Courses && node == null) "New course" else "New folder",
                canImportDocuments = target.mode == VaultRootMode.Library && canCreateInside,
                canRename = node != null,
                canMove = node != null && !isCourse,
                canPin = node?.type == VaultMobileWebExplorerNodeType.Note ||
                    node?.type == VaultMobileWebExplorerNodeType.Document,
                pinned = node?.pinned == true,
                canDelete = node != null,
                moveTargets = section.explorerMoveTargets(target.mode, node),
                onDismiss = { explorerActionTarget = null },
                onOpen = { node?.let { openExplorerNode(target.mode, it) } },
                onCreateNote = {
                    when (target.mode) {
                        VaultRootMode.Study -> homeViewModel.createNote(node?.id, FOLDER_MODE_STUDY) { noteId ->
                            navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                        }
                        VaultRootMode.Personal -> homeViewModel.createNote(node?.id, FOLDER_MODE_PERSONAL) { noteId ->
                            navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                        }
                        VaultRootMode.Courses -> when {
                            isCourse && courseId != null -> coursesViewModel.createNoteForCourse(courseId) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                            node != null && courseId != null -> coursesViewModel.createNoteInFolderForCourse(
                                courseId,
                                node.id,
                            ) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                            }
                        }
                        else -> Unit
                    }
                },
                onCreateFolder = { name ->
                    when (target.mode) {
                        VaultRootMode.Study -> homeViewModel.createFolder(node?.id, name, FOLDER_MODE_STUDY) { }
                        VaultRootMode.Personal -> homeViewModel.createFolder(node?.id, name, FOLDER_MODE_PERSONAL) { }
                        VaultRootMode.Library -> libraryViewModel.createFolder(node?.id, name)
                        VaultRootMode.Courses -> when {
                            node == null -> coursesViewModel.createCourse(name)
                            courseId != null -> coursesViewModel.createSubfolderForCourse(
                                courseId = courseId,
                                parentId = node.id.takeUnless { isCourse },
                                name = name,
                            )
                        }
                        else -> Unit
                    }
                },
                onImportDocuments = { uris ->
                    if (target.mode == VaultRootMode.Library) {
                        libraryViewModel.importFilesToFolder(node?.id, uris)
                    }
                },
                onRename = { name ->
                    when (target.mode) {
                        VaultRootMode.Study, VaultRootMode.Personal -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> homeViewModel.updateFolderDetails(node.id, name, node.description)
                            VaultMobileWebExplorerNodeType.Note -> homeViewModel.renameNote(node.id, name)
                            else -> Unit
                        }
                        VaultRootMode.Library -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> libraryViewModel.renameFolder(node.id, name)
                            VaultMobileWebExplorerNodeType.Document -> libraryViewModel.renameFile(node.id, name)
                            else -> Unit
                        }
                        VaultRootMode.Courses -> when {
                            node == null -> Unit
                            isCourse && courseId != null -> coursesViewModel.renameCourse(courseId, name)
                            node.type == VaultMobileWebExplorerNodeType.Folder -> coursesViewModel.updateChildFolder(
                                node.id,
                                name,
                                node.description,
                            )
                            node.type == VaultMobileWebExplorerNodeType.Note -> coursesViewModel.renameNote(node.id, name)
                        }
                        else -> Unit
                    }
                },
                onMove = { parentId ->
                    when (target.mode) {
                        VaultRootMode.Study, VaultRootMode.Personal -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> homeViewModel.moveFolder(node.id, parentId)
                            VaultMobileWebExplorerNodeType.Note -> homeViewModel.moveNote(node.id, parentId)
                            else -> Unit
                        }
                        VaultRootMode.Library -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> libraryViewModel.moveFolder(node.id, parentId)
                            VaultMobileWebExplorerNodeType.Document -> libraryViewModel.moveFile(node.id, parentId)
                            else -> Unit
                        }
                        VaultRootMode.Courses -> if (node != null && courseId != null) {
                            when (node.type) {
                                VaultMobileWebExplorerNodeType.Folder -> coursesViewModel.moveChildFolderForCourse(courseId, node.id, parentId)
                                VaultMobileWebExplorerNodeType.Note -> coursesViewModel.moveNoteForCourse(courseId, node.id, parentId)
                                else -> Unit
                            }
                        }
                        else -> Unit
                    }
                },
                onTogglePin = {
                    when (target.mode) {
                        VaultRootMode.Study, VaultRootMode.Personal -> node?.let {
                            homeViewModel.setNotePinned(it.id, !it.pinned)
                        }
                        VaultRootMode.Library -> node?.let {
                            libraryViewModel.setFilePinned(it.id, !it.pinned)
                        }
                        VaultRootMode.Courses -> node?.let {
                            coursesViewModel.setNotePinned(it.id, !it.pinned)
                        }
                        else -> Unit
                    }
                },
                onDelete = {
                    when (target.mode) {
                        VaultRootMode.Study, VaultRootMode.Personal -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> homeViewModel.deleteFolder(node.id)
                            VaultMobileWebExplorerNodeType.Note -> homeViewModel.deleteNote(node.id)
                            else -> Unit
                        }
                        VaultRootMode.Library -> when (node?.type) {
                            VaultMobileWebExplorerNodeType.Folder -> libraryViewModel.deleteFolder(node.id)
                            VaultMobileWebExplorerNodeType.Document -> libraryViewModel.deleteFile(node.id)
                            else -> Unit
                        }
                        VaultRootMode.Courses -> when {
                            node == null -> Unit
                            isCourse && courseId != null -> coursesViewModel.deleteCourse(courseId)
                            node.type == VaultMobileWebExplorerNodeType.Folder -> coursesViewModel.deleteChildFolder(node.id)
                            node.type == VaultMobileWebExplorerNodeType.Note -> coursesViewModel.deleteNote(node.id)
                        }
                        else -> Unit
                    }
                },
            )
        }
        AnimatedVisibility(
            visibleState = narrationMiniPlayerVisibility,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            enter = fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 2 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    targetOffsetY = { it / 2 },
                ),
        ) {
            NarrationMiniPlayer(
                state = narrationState,
                onPrimaryAction = {
                    if (narrationState.status != NarrationPlaybackStatus.Preparing &&
                        narrationState.status != NarrationPlaybackStatus.Generating
                    ) {
                        narrationViewModel.togglePlayback()
                    }
                },
                onStop = narrationViewModel::stop,
                onSeek = narrationViewModel::seekTo,
                onProgressTick = narrationViewModel::refreshProgress,
                modifier = Modifier.onSizeChanged { narrationMiniPlayerHeightPx = it.height },
            )
        }
    }
}

private val WorkspaceLabels = listOf("Personal", "Islamic Corpus")

private fun String.workspaceLabel(): String =
    when (this) {
        WORKSPACE_PERSONAL -> "Personal"
        else -> "Islamic Corpus"
    }

private fun String.workspaceValue(): String =
    when (this) {
        "Personal" -> WORKSPACE_PERSONAL
        else -> WORKSPACE_ISLAMIC_CORPUS
    }

private enum class VaultRootMode(val label: String, val icon: ImageVector) {
    Courses("Courses", Icons.Outlined.School),
    Study("Study", Icons.AutoMirrored.Outlined.MenuBook),
    Library("Library", Icons.Outlined.LocalLibrary),
    Quran("Qur'an", Icons.Outlined.AutoStories),
    Memorise("Memorise", Icons.Outlined.CheckCircleOutline),
    Personal("Personal", Icons.Outlined.PersonOutline),
}

private data class ExplorerActionTarget(
    val mode: VaultRootMode,
    val node: VaultMobileWebExplorerNode?,
    val creationOnly: Boolean = false,
)

private fun List<VaultMobileWebExplorerNode>.findExplorerNodePath(
    nodeId: String,
): List<VaultMobileWebExplorerNode>? {
    for (node in this) {
        if (node.id == nodeId) return listOf(node)
        node.children.findExplorerNodePath(nodeId)?.let { path ->
            return listOf(node) + path
        }
    }
    return null
}

private fun List<VaultTreeItem>.findTreeItemPath(itemId: String): List<VaultTreeItem>? {
    for (item in this) {
        if (item.id == itemId) return listOf(item)
        item.children.findTreeItemPath(itemId)?.let { path -> return listOf(item) + path }
    }
    return null
}

private fun VaultMobileWebExplorerSection?.explorerMoveTargets(
    mode: VaultRootMode,
    source: VaultMobileWebExplorerNode?,
): List<VaultExplorerMoveTarget> {
    if (this == null || source == null) return emptyList()
    val path = nodes.findExplorerNodePath(source.id).orEmpty()
    val roots = if (mode == VaultRootMode.Courses) path.firstOrNull()?.children.orEmpty() else nodes
    val excludedIds = if (source.type == VaultMobileWebExplorerNodeType.Folder) {
        source.descendantIds()
    } else {
        emptySet()
    }
    val rootLabel = when (mode) {
        VaultRootMode.Personal -> "Personal root"
        VaultRootMode.Courses -> "Course root"
        else -> "${mode.label} root"
    }
    return listOf(VaultExplorerMoveTarget(null, rootLabel)) +
        roots.flatMap { it.flattenExplorerFolderTargets(excludedIds = excludedIds) }
}

private fun VaultMobileWebExplorerNode.descendantIds(): Set<String> =
    buildSet {
        add(id)
        children.forEach { addAll(it.descendantIds()) }
    }

private fun VaultMobileWebExplorerNode.flattenExplorerFolderTargets(
    parentPath: String = "",
    excludedIds: Set<String>,
): List<VaultExplorerMoveTarget> {
    if (type != VaultMobileWebExplorerNodeType.Folder || id in excludedIds) return emptyList()
    val path = if (parentPath.isBlank()) label else "$parentPath / $label"
    return listOf(VaultExplorerMoveTarget(id, path)) + children.flatMap {
        it.flattenExplorerFolderTargets(path, excludedIds)
    }
}

private fun String.rootModes(): List<VaultRootMode> =
    if (this == WORKSPACE_PERSONAL) {
        listOf(VaultRootMode.Personal, VaultRootMode.Library)
    } else {
        listOf(
            VaultRootMode.Study,
            VaultRootMode.Library,
            VaultRootMode.Courses,
            VaultRootMode.Quran,
            VaultRootMode.Memorise,
        )
    }

private fun VaultTreeItem.flattenFavouriteNotes(): List<VaultTreeItem> =
    buildList {
        if (type == VaultTreeItemType.Note && favourite) add(this@flattenFavouriteNotes)
        children.forEach { addAll(it.flattenFavouriteNotes()) }
    }

private fun buildExplorerSections(
    modes: List<VaultRootMode>,
    studyState: HomeUiState,
    personalState: HomeUiState,
    libraryState: LibraryUiState,
    coursesState: CoursesUiState,
): List<VaultMobileWebExplorerSection> =
    modes.mapIndexedNotNull { index, mode ->
        when (mode) {
            VaultRootMode.Study -> VaultMobileWebExplorerSection(
                navigationIndex = index,
                nodes = studyState.workspace.map(VaultTreeItem::toExplorerNode),
                canAdd = true,
            )
            VaultRootMode.Personal -> VaultMobileWebExplorerSection(
                navigationIndex = index,
                nodes = personalState.workspace.map(VaultTreeItem::toExplorerNode),
                canAdd = true,
            )
            VaultRootMode.Library -> VaultMobileWebExplorerSection(
                navigationIndex = index,
                nodes = libraryState.allFolders.map(LibraryFolderItem::toExplorerNode) +
                    libraryState.files.map { file ->
                        VaultMobileWebExplorerNode(
                            id = file.id,
                            label = file.name,
                            type = VaultMobileWebExplorerNodeType.Document,
                            pinned = file.pinned,
                        )
                    },
                canAdd = true,
            )
            VaultRootMode.Courses -> VaultMobileWebExplorerSection(
                navigationIndex = index,
                nodes = coursesState.courses.map { course ->
                    VaultMobileWebExplorerNode(
                        id = COURSE_EXPLORER_PREFIX + course.id,
                        label = course.title,
                        type = VaultMobileWebExplorerNodeType.Folder,
                        count = coursesState.noteCountsByCourse[course.id],
                        children = coursesState.treesByCourse[course.id].orEmpty().map(VaultTreeItem::toExplorerNode),
                        canAdd = true,
                    )
                },
                canAdd = true,
            )
            VaultRootMode.Quran, VaultRootMode.Memorise -> null
        }
    }

@Composable
private fun StudyLibraryPersonalShell(
    workspace: String,
    selectedRootModeName: String,
    onRootModeChanged: (VaultRootMode) -> Unit,
    rootBackHandlerEnabled: Boolean,
    coursesContent: @Composable () -> Unit,
    studyContent: @Composable () -> Unit,
    quranContent: @Composable () -> Unit,
    memoriseContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    personalContent: @Composable () -> Unit,
    personalLibraryContent: @Composable () -> Unit,
) {
    val modes = remember(workspace) {
        if (workspace == WORKSPACE_PERSONAL) {
            listOf(VaultRootMode.Personal, VaultRootMode.Library)
        } else {
            listOf(VaultRootMode.Courses, VaultRootMode.Study, VaultRootMode.Library, VaultRootMode.Quran, VaultRootMode.Memorise)
        }
    }
    val requestedPage = remember(modes, selectedRootModeName) {
        modes.indexOfFirst { it.name == selectedRootModeName }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = requestedPage) { modes.size }
    val studyPage = remember(modes) { modes.indexOf(VaultRootMode.Study).takeIf { it >= 0 } ?: 0 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(modes, requestedPage) {
        if (pagerState.currentPage != requestedPage) {
            pagerState.scrollToPage(requestedPage)
        }
    }

    BackHandler(enabled = rootBackHandlerEnabled && workspace == WORKSPACE_ISLAMIC_CORPUS && pagerState.currentPage != studyPage) {
        onRootModeChanged(VaultRootMode.Study)
        scope.launch {
            pagerState.scrollToPage(studyPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { page -> modes[page].name },
        beyondViewportPageCount = 1,
        userScrollEnabled = false,
    ) { page ->
        when (modes[page]) {
            VaultRootMode.Courses -> coursesContent()
            VaultRootMode.Study -> studyContent()
            VaultRootMode.Quran -> quranContent()
            VaultRootMode.Memorise -> memoriseContent()
            VaultRootMode.Library -> if (workspace == WORKSPACE_PERSONAL) personalLibraryContent() else libraryContent()
            VaultRootMode.Personal -> personalContent()
        }
    }
}

private fun VaultTreeItem.toExplorerNode(): VaultMobileWebExplorerNode =
    VaultMobileWebExplorerNode(
        id = id,
        label = name,
        type = if (type == VaultTreeItemType.Folder) {
            VaultMobileWebExplorerNodeType.Folder
        } else {
            VaultMobileWebExplorerNodeType.Note
        },
        count = count.takeIf { type == VaultTreeItemType.Folder && it > 0 },
        children = children.map(VaultTreeItem::toExplorerNode),
        canAdd = type == VaultTreeItemType.Folder,
        pinned = pinned,
        description = description,
        colorKey = colorKey,
    )

private fun LibraryFolderItem.toExplorerNode(): VaultMobileWebExplorerNode =
    VaultMobileWebExplorerNode(
        id = id,
        label = name,
        type = VaultMobileWebExplorerNodeType.Folder,
        count = count.takeIf { it > 0 },
        children = children.map(LibraryFolderItem::toExplorerNode) + files.map { file ->
            VaultMobileWebExplorerNode(
                id = file.id,
                label = file.name,
                type = VaultMobileWebExplorerNodeType.Document,
                pinned = file.pinned,
            )
        },
        canAdd = true,
        colorKey = colorKey,
    )

private const val COURSE_EXPLORER_PREFIX = "course:"

private fun String.toNoteBodyFontSizeSp(): Float =
    when (this) {
        "small" -> 13.5f
        "large" -> 17f
        else -> 15f
    }

private fun String.toDashboardFontSizeSp(): Float =
    when (this) {
        "small" -> 13f
        "medium_large" -> 15f
        "large" -> 16f
        else -> 14f
    }

private fun String.toPreviewLines(): Int = when (this) {
    "one" -> 1
    "two" -> 2
    else -> 0
}

private fun com.myvault.app.data.preferences.VaultUserPreferences.quickBackupRecommended(): Boolean {
    val mostRecent = lastGoogleDriveSyncAt
    if (mostRecent <= 0L) return true
    return System.currentTimeMillis() - mostRecent > 7L * 24L * 60L * 60L * 1000L
}
