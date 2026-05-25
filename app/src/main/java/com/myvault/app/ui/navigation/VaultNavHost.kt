package com.myvault.app.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.preferences.WORKSPACE_ISLAMIC_CORPUS
import com.myvault.app.data.preferences.WORKSPACE_PERSONAL
import com.myvault.app.ui.screens.AttachmentViewerScreen
import com.myvault.app.ui.screens.AttachmentsScreen
import com.myvault.app.ui.screens.AskAiScreen
import com.myvault.app.ui.screens.EditorScreen
import com.myvault.app.ui.screens.FolderViewScreen
import com.myvault.app.ui.screens.HomeScreen
import com.myvault.app.ui.screens.LibraryFolderScreen
import com.myvault.app.ui.screens.LibraryScreen
import com.myvault.app.ui.screens.MemoriseShellScreen
import com.myvault.app.ui.screens.QuranShellScreen
import com.myvault.app.ui.screens.ReadingScreen
import com.myvault.app.ui.screens.SearchScreen
import com.myvault.app.ui.screens.SettingsScreen
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.AttachmentsViewModel
import com.myvault.app.ui.viewmodel.AttachmentViewerViewModel
import com.myvault.app.ui.viewmodel.FolderViewModel
import com.myvault.app.ui.viewmodel.HomeViewModel
import com.myvault.app.ui.viewmodel.LibraryViewModel
import com.myvault.app.ui.viewmodel.MemoriseViewModel
import com.myvault.app.ui.viewmodel.NoteViewModel
import com.myvault.app.ui.viewmodel.QuranReaderViewModel
import com.myvault.app.ui.viewmodel.SearchViewModel
import com.myvault.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun VaultNavHost(
    pendingOpenNoteId: String? = null,
    onPendingOpenNoteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    var selectedIslamicRootMode by rememberSaveable { mutableStateOf(VaultRootMode.Study.name) }

    LaunchedEffect(pendingOpenNoteId) {
        val noteId = pendingOpenNoteId ?: return@LaunchedEffect
        navController.navigate(VaultDestination.Editor.route(noteId))
        onPendingOpenNoteConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = VaultDestination.Home.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        },
    ) {
        composable(VaultDestination.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val studyState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val personalState by homeViewModel.personalUiState.collectAsStateWithLifecycle()
            val preferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val openNote: (String) -> Unit = { noteId ->
                navController.navigate(
                    if (preferences.defaultNoteView == "editing") {
                        VaultDestination.Editor.route(noteId)
                    } else {
                        VaultDestination.Reading.route(noteId)
                    },
                )
            }
            StudyLibraryPersonalShell(
                workspace = preferences.workspace,
                selectedRootModeName = if (preferences.workspace == WORKSPACE_PERSONAL) {
                    VaultRootMode.Personal.name
                } else {
                    selectedIslamicRootMode
                },
                onRootModeChanged = { mode ->
                    if (mode != VaultRootMode.Personal) {
                        selectedIslamicRootMode = mode.name
                    }
                },
                rootBackHandlerEnabled = currentRoute == VaultDestination.Home.route,
                onQuickNoteMode = { mode ->
                    homeViewModel.createNote(folderId = null, mode = mode) { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                    }
                },
                studyContent = {
                    HomeScreen(
                        uiState = studyState,
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { settingsViewModel.setWorkspace(it.workspaceValue()) },
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = {},
                        onNoteClick = openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId, mode = FOLDER_MODE_STUDY) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onNewFolderClick = { parentId, name ->
                            homeViewModel.createFolder(parentId = parentId, name = name, mode = FOLDER_MODE_STUDY) { }
                        },
                        onRenameFolderClick = { folderId, name ->
                            homeViewModel.renameFolder(folderId, name)
                        },
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
                        onSetNoteFavouriteClick = { noteId, favourite ->
                            homeViewModel.setNoteFavourite(noteId, favourite)
                        },
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
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        currentFolderMode = FOLDER_MODE_STUDY,
                    )
                },
                quranContent = {
                    val quranViewModel: QuranReaderViewModel = hiltViewModel()
                    val quranState by quranViewModel.uiState.collectAsStateWithLifecycle()
                    QuranShellScreen(
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { settingsViewModel.setWorkspace(it.workspaceValue()) },
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.pushGoogleDriveSync {
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
                        onOpenBookmark = quranViewModel::openBookmarkedAyah,
                        onOpenReciterPicker = quranViewModel::openReciterPicker,
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
                        onStartMemorizingAyah = quranViewModel::startMemorizingAyah,
                        onToggleMemorizedAyah = quranViewModel::toggleMemorizedAyah,
                        onToggleWeakMemorization = quranViewModel::toggleWeakMemorization,
                        onSetMemorizationConcealAmount = quranViewModel::setMemorizationConcealAmount,
                        onSetMemorizationRepeatMode = quranViewModel::setMemorizationRepeatMode,
                        onStopMemorizationRepeat = quranViewModel::stopMemorizationRepeat,
                    )
                },
                memoriseContent = {
                    val memoriseViewModel: MemoriseViewModel = hiltViewModel()
                    val memoriseState by memoriseViewModel.uiState.collectAsStateWithLifecycle()
                    MemoriseShellScreen(
                        uiState = memoriseState,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { settingsViewModel.setWorkspace(it.workspaceValue()) },
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        onSelectGroup = memoriseViewModel::selectGroup,
                        onSelectSurah = memoriseViewModel::selectSurah,
                        onSelectAyah = memoriseViewModel::selectAyah,
                        onStartSelectedAyah = memoriseViewModel::startSelectedAyah,
                        onMarkSelectedSurahMemorized = memoriseViewModel::markSelectedSurahMemorized,
                        onMarkReviewed = memoriseViewModel::markReviewed,
                        onToggleMemorized = memoriseViewModel::toggleMemorized,
                        onToggleRevision = memoriseViewModel::toggleRevision,
                        onToggleWeak = memoriseViewModel::toggleWeak,
                    )
                },
                libraryContent = {
                    val libraryViewModel: LibraryViewModel = hiltViewModel()
                    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        uiState = libraryState,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { settingsViewModel.setWorkspace(it.workspaceValue()) },
                        onFolderClick = { folderId ->
                            navController.navigate(VaultDestination.LibraryFolder.route(folderId))
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
                        onRenameAnnotation = libraryViewModel::renameAnnotation,
                        onMoveAnnotation = libraryViewModel::moveAnnotation,
                        onDeleteAnnotationNote = libraryViewModel::deleteAnnotationNote,
                        onDeleteAnnotation = libraryViewModel::deleteAnnotation,
                        onLinkAnnotationToStudyNote = libraryViewModel::linkAnnotationToStudyNote,
                        onCreateFolder = { parentId, name ->
                            libraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = libraryViewModel::renameFolder,
                        onMoveFolder = libraryViewModel::moveFolder,
                        onDeleteFolder = libraryViewModel::deleteFolder,
                        onFolderExpandedChange = libraryViewModel::setFolderExpanded,
                        onViewModeChange = libraryViewModel::setViewMode,
                        onImportFiles = { uris ->
                            libraryViewModel.importFiles(uris)
                        },
                        onDismissImportMessage = libraryViewModel::clearImportMessage,
                        onRenameFile = libraryViewModel::renameFile,
                        onMoveFile = libraryViewModel::moveFile,
                        onSetFilePinned = libraryViewModel::setFilePinned,
                        onDeleteFile = libraryViewModel::deleteFile,
                        onAddAttachmentTag = libraryViewModel::addAttachmentTag,
                        onRemoveAttachmentTag = libraryViewModel::removeAttachmentTag,
                        onAddAnnotationTag = libraryViewModel::addAnnotationTag,
                        onRemoveAnnotationTag = libraryViewModel::removeAnnotationTag,
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                    )
                },
                personalContent = {
                    HomeScreen(
                        uiState = personalState,
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { settingsViewModel.setWorkspace(it.workspaceValue()) },
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = {},
                        onNoteClick = openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId, mode = FOLDER_MODE_PERSONAL) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onNewFolderClick = { parentId, name ->
                            homeViewModel.createFolder(parentId = parentId, name = name, mode = FOLDER_MODE_PERSONAL) { }
                        },
                        onRenameFolderClick = { folderId, name ->
                            homeViewModel.renameFolder(folderId, name)
                        },
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
                        onSetNoteFavouriteClick = { noteId, favourite ->
                            homeViewModel.setNoteFavourite(noteId, favourite)
                        },
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
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        currentFolderMode = FOLDER_MODE_PERSONAL,
                    )
                },
            )
        }
        composable(
            route = VaultDestination.LibraryFolder.route,
            arguments = listOf(navArgument("libraryFolderId") { type = NavType.StringType }),
        ) {
            val viewModel: LibraryViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LibraryFolderScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onFolderClick = { folderId ->
                    navController.navigate(VaultDestination.LibraryFolder.route(folderId))
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
                onCreateFolder = { parentId, name ->
                    viewModel.createFolder(parentId = parentId, name = name)
                },
                onRenameFolder = viewModel::renameFolder,
                onMoveFolder = viewModel::moveFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onFolderExpandedChange = viewModel::setFolderExpanded,
                onViewModeChange = viewModel::setViewMode,
                onImportFiles = { uris ->
                    viewModel.importFiles(uris)
                },
                onDismissImportMessage = viewModel::clearImportMessage,
                onRenameFile = viewModel::renameFile,
                onMoveFile = viewModel::moveFile,
                onSetFilePinned = viewModel::setFilePinned,
                onDeleteFile = viewModel::deleteFile,
                onAddAttachmentTag = viewModel::addAttachmentTag,
                onRemoveAttachmentTag = viewModel::removeAttachmentTag,
                onAddAnnotationTag = viewModel::addAnnotationTag,
                onRemoveAnnotationTag = viewModel::removeAnnotationTag,
            )
        }
        composable(
            route = VaultDestination.FolderView.route,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
        ) {
            val viewModel: FolderViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            FolderViewScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onSearchClick = { navController.navigate(VaultDestination.Search.route) },
                onNoteClick = { noteId ->
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
                onNewSubfolderClick = { name ->
                    viewModel.createSubfolder(name) { folderId ->
                        navController.navigate(VaultDestination.FolderView.route(folderId))
                    }
                },
                notePreviewLines = preferences.notePreview.toPreviewLines(),
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
            val context = LocalContext.current
            val viewModel: NoteViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
            val selectedTextAiState by viewModel.selectedTextAiState.collectAsStateWithLifecycle()
            val preferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            EditorScreen(
                uiState = uiState,
                aiState = aiState,
                selectedTextAiState = selectedTextAiState,
                onBackClick = { navController.popBackStack() },
                onTitleChange = viewModel::updateTitle,
                onContentChange = viewModel::saveRichText,
                onRunAiTool = viewModel::runAiTool,
                onRunSelectedTextAi = viewModel::runSelectedTextAi,
                onClearSelectedTextAi = viewModel::clearSelectedTextAi,
                onSelectedTextAiQuestionChange = viewModel::setSelectedTextAiQuestion,
                onSendSelectedTextResultToChat = viewModel::sendSelectedTextResultToChat,
                onClearAiResult = viewModel::clearAiResult,
                onClearAiConversation = viewModel::clearAiConversation,
                onAiProviderSelected = viewModel::setAiProvider,
                onAiModelSelected = viewModel::setAiModel,
                onAiQuestionChange = viewModel::setAiQuestion,
                onAskAiClick = { selectedText ->
                    uiState.note?.id?.let { noteId ->
                        navController.navigate(VaultDestination.AskAi.route(noteId, selectedText))
                    }
                },
                onAttachDocument = viewModel::attachDocument,
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onPinnedChange = viewModel::setPinned,
                onFavouriteChange = viewModel::setFavourite,
                onDeleteNote = {
                    viewModel.deleteNote {
                        navController.popBackStack(VaultDestination.Home.route, false)
                    }
                },
                onExportText = { uri -> viewModel.exportText(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onExportPdf = { uri -> viewModel.exportPdf(uri) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                onCreateTable = viewModel::createTable,
                onUpdateTableCell = viewModel::updateTableCell,
                onDeleteTable = viewModel::deleteTable,
                bodyFontSizeSp = preferences.noteFontSize.toNoteBodyFontSizeSp(),
                autoFocusBody = backStackEntry.arguments?.getBoolean("quickFocus") == true,
            )
        }
        composable(
            route = VaultDestination.Reading.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            val viewModel: NoteViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
            val narrationState by viewModel.narrationState.collectAsStateWithLifecycle()
            val preferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            ReadingScreen(
                uiState = uiState,
                aiState = aiState,
                narrationState = narrationState,
                onBackClick = { navController.popBackStack() },
                onEditClick = {
                    uiState.note?.id?.let { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId))
                    }
                },
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onPinnedChange = viewModel::setPinned,
                onFavouriteChange = viewModel::setFavourite,
                onRunAiTool = viewModel::runAiTool,
                onClearAiConversation = viewModel::clearAiConversation,
                onAiProviderSelected = viewModel::setAiProvider,
                onAiModelSelected = viewModel::setAiModel,
                onAiQuestionChange = viewModel::setAiQuestion,
                onAskAiClick = {
                    uiState.note?.id?.let { noteId ->
                        navController.navigate(VaultDestination.AskAi.route(noteId))
                    }
                },
                onListenClick = viewModel::startNarration,
                onNarrationPrimaryAction = { _, _, _ -> viewModel.toggleNarrationPlayback() },
                onNarrationStop = viewModel::stopNarration,
                onNarrationSpeedChange = viewModel::setNarrationSpeed,
                onNarrationSeek = viewModel::seekNarration,
                onNarrationProgressTick = viewModel::refreshNarrationProgress,
                onDeleteNote = {
                    viewModel.deleteNote {
                        navController.popBackStack(VaultDestination.Home.route, false)
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
                bodyFontSizeSp = preferences.noteFontSize.toNoteBodyFontSizeSp(),
            )
        }
        composable(
            route = VaultDestination.AskAi.route,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("selectedText") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val viewModel: NoteViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
            val selectedText = backStackEntry.arguments?.getString("selectedText").orEmpty()
            AskAiScreen(
                uiState = uiState,
                aiState = aiState,
                selectedText = selectedText.takeIf { it.isNotBlank() },
                onBackClick = { navController.popBackStack() },
                onRunAiTool = viewModel::runAiTool,
                onClearAiConversation = viewModel::clearAiConversation,
                onAiProviderSelected = viewModel::setAiProvider,
                onAiModelSelected = viewModel::setAiModel,
                onAiQuestionChange = viewModel::setAiQuestion,
                onOpenAiConversation = viewModel::openAiConversation,
                onStartNewAiConversation = viewModel::startNewAiConversation,
            )
        }
        composable(VaultDestination.Search.route) {
            val viewModel: SearchViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            SearchScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
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
            val context = LocalContext.current
            val viewModel: AttachmentViewerViewModel = hiltViewModel()
            val attachment by viewModel.attachment.collectAsStateWithLifecycle()
            val pdfProgress by viewModel.pdfProgress.collectAsStateWithLifecycle()
            val pdfAnnotations by viewModel.pdfAnnotations.collectAsStateWithLifecycle()
            AttachmentViewerScreen(
                attachment = attachment,
                pdfProgress = pdfProgress,
                pdfAnnotations = pdfAnnotations,
                initialPageIndex = viewModel.initialPageIndex,
                onBackClick = { navController.popBackStack() },
                onPdfProgressChanged = viewModel::updatePdfProgress,
                onAddPdfHighlight = viewModel::addPdfHighlight,
                onUpdatePdfHighlightColor = viewModel::updatePdfHighlightColor,
                onUpdatePdfAnnotationNote = viewModel::updatePdfAnnotationNote,
                onDeletePdfAnnotation = viewModel::deletePdfAnnotation,
                onDeleteAttachment = {
                    viewModel.deleteAttachment {
                        Toast.makeText(context, "Attachment deleted", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
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
                onBackClick = { navController.popBackStack() },
                onSearchClick = { navController.navigate(VaultDestination.Search.route) },
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
            )
        }
        composable(VaultDestination.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
            val storageLabel by viewModel.storageLabel.collectAsStateWithLifecycle()
            val recentlyDeleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
            val driveRestoreState by viewModel.driveRestoreState.collectAsStateWithLifecycle()
            val supabaseSession by viewModel.supabaseSession.collectAsStateWithLifecycle()
            var backupMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            SettingsScreen(
                preferences = preferences,
                onBackClick = { navController.popBackStack() },
                onThemeSelected = viewModel::setTheme,
                onAccentColorSelected = viewModel::setAccentColor,
                onBackupSelected = { uri -> viewModel.exportBackup(uri) { backupMessage = it } },
                onRestoreSelected = { uri -> viewModel.restoreBackup(uri) { backupMessage = it } },
                onDashboardFontSizeSelected = viewModel::setDashboardFontSize,
                onNoteFontSizeSelected = viewModel::setNoteFontSize,
                onNotePreviewSelected = viewModel::setNotePreview,
                onDefaultNoteViewSelected = viewModel::setDefaultNoteView,
                onSecurityLockChanged = viewModel::setSecurityLockEnabled,
                onSecurityLockTimeoutSelected = viewModel::setSecurityLockTimeout,
                storageLabel = storageLabel,
                recentlyDeleted = recentlyDeleted,
                onRestoreDeletedNote = viewModel::restoreNote,
                onPermanentlyDeleteNote = { noteId -> viewModel.permanentlyDeleteNote(noteId) { backupMessage = it } },
                onRestoreDeletedFolder = viewModel::restoreFolder,
                onPermanentlyDeleteFolder = { folderId -> viewModel.permanentlyDeleteFolder(folderId) { backupMessage = it } },
                onPermanentlyDeleteAllDeleted = { viewModel.permanentlyDeleteAllRecentlyDeleted { backupMessage = it } },
                googleDriveSignInIntent = viewModel.googleDriveSignInIntent(),
                onGoogleDriveSignInResult = { data -> viewModel.handleGoogleDriveSignInResult(data) { backupMessage = it } },
                onGoogleDrivePush = { viewModel.pushGoogleDriveSync { backupMessage = it } },
                onGoogleDrivePull = { viewModel.pullGoogleDriveSync { backupMessage = it } },
                supabaseAiEmail = supabaseSession.email,
                onSupabaseAiLogin = { email, password -> viewModel.signInSupabaseAi(email, password) { backupMessage = it } },
                onSupabaseAiLogout = { viewModel.signOutSupabaseAi { backupMessage = it } },
                driveRestoreState = driveRestoreState,
                backupMessage = backupMessage,
                onDismissBackupMessage = {
                    backupMessage = null
                    viewModel.dismissDriveRestoreMessage()
                },
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
    Study("Study", Icons.Rounded.MenuBook),
    Library("Library", Icons.Rounded.LocalLibrary),
    Quran("Qur'an", Icons.Rounded.AutoStories),
    Memorise("Memorise", Icons.Rounded.CheckCircle),
    Personal("Personal", Icons.Rounded.Person),
}

@Composable
private fun StudyLibraryPersonalShell(
    workspace: String,
    selectedRootModeName: String,
    onRootModeChanged: (VaultRootMode) -> Unit,
    rootBackHandlerEnabled: Boolean,
    onQuickNoteMode: (String) -> Unit,
    studyContent: @Composable () -> Unit,
    quranContent: @Composable () -> Unit,
    memoriseContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    personalContent: @Composable () -> Unit,
) {
    val modes = remember(workspace) {
        if (workspace == WORKSPACE_PERSONAL) {
            listOf(VaultRootMode.Personal)
        } else {
            listOf(VaultRootMode.Study, VaultRootMode.Library, VaultRootMode.Quran, VaultRootMode.Memorise)
        }
    }
    val requestedPage = remember(modes, selectedRootModeName) {
        modes.indexOfFirst { it.name == selectedRootModeName }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = requestedPage) { modes.size }
    val scope = rememberCoroutineScope()
    val colors = VaultThemeTokens.colors

    LaunchedEffect(modes, requestedPage) {
        if (pagerState.currentPage != requestedPage) {
            pagerState.scrollToPage(requestedPage)
        }
    }

    LaunchedEffect(pagerState, modes) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, modes.lastIndex) }
            .distinctUntilChanged()
            .collect { page -> onRootModeChanged(modes[page]) }
    }

    BackHandler(enabled = rootBackHandlerEnabled && workspace == WORKSPACE_ISLAMIC_CORPUS && pagerState.currentPage > 0) {
        scope.launch {
            onRootModeChanged(VaultRootMode.Study)
            pagerState.animateScrollToPage(
                page = 0,
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> modes[page].name },
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.18f,
            ),
        ) { page ->
            when (modes[page]) {
                VaultRootMode.Study -> studyContent()
                VaultRootMode.Quran -> quranContent()
                VaultRootMode.Memorise -> memoriseContent()
                VaultRootMode.Library -> libraryContent()
                VaultRootMode.Personal -> personalContent()
            }
        }

        FloatingBottomNav(
            modes = modes,
            selectedIndex = pagerState.currentPage.coerceIn(0, modes.lastIndex),
            onModeSelected = { index ->
                onRootModeChanged(modes[index])
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                    )
                }
            },
            onModeDoubleTapped = { mode ->
                when (mode) {
                    VaultRootMode.Study -> onQuickNoteMode(FOLDER_MODE_STUDY)
                    VaultRootMode.Personal -> onQuickNoteMode(FOLDER_MODE_PERSONAL)
                    else -> Unit
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = VaultSpacing.lg),
        )
    }
}


@Composable
private fun FloatingBottomNav(
    modes: List<VaultRootMode>,
    selectedIndex: Int,
    onModeSelected: (Int) -> Unit,
    onModeDoubleTapped: (VaultRootMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier
            .widthIn(
                min = if (modes.size > 3) 304.dp else 236.dp,
                max = if (modes.size > 3) 342.dp else 258.dp,
            ),
        color = colors.elevated.copy(alpha = 0.96f),
        contentColor = colors.textSecondary,
        shape = VaultShapes.pill,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEachIndexed { index, mode ->
                FloatingBottomNavItem(
                    label = mode.label,
                    icon = mode.icon,
                    selected = selectedIndex == index,
                    onClick = { onModeSelected(index) },
                    onDoubleClick = { onModeDoubleTapped(mode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.74f),
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "bottomNavContentColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "bottomNavScale",
    )

    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onDoubleClick = onDoubleClick,
        ),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = contentColor,
        shape = VaultShapes.pill,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .scale(scale)
                .padding(horizontal = if (label.length > 7) 6.dp else 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.W800 else FontWeight.W600,
                ),
                color = contentColor,
            )
        }
    }
}

private fun String.toNoteBodyFontSizeSp(): Float =
    when (this) {
        "small" -> 13.5f
        "large" -> 17f
        else -> 15f
    }

private fun String.toDashboardFontSizeSp(): Float =
    when (this) {
        "small" -> 13f
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
