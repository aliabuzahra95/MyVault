package com.myvault.app.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.narration.NarrationPlaybackStatus
import com.myvault.app.data.preferences.WORKSPACE_ISLAMIC_CORPUS
import com.myvault.app.data.preferences.WORKSPACE_PERSONAL
import com.myvault.app.ui.components.NarrationMiniPlayer
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
import com.myvault.app.ui.screens.QuranReflectionsHubScreen
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
import com.myvault.app.ui.viewmodel.NarrationViewModel
import com.myvault.app.ui.viewmodel.NoteViewModel
import com.myvault.app.ui.viewmodel.QuranReaderViewModel
import com.myvault.app.ui.viewmodel.QuranReflectionsViewModel
import com.myvault.app.ui.viewmodel.SearchViewModel
import com.myvault.app.ui.viewmodel.SettingsViewModel
import com.myvault.app.ui.viewmodel.ShellPreferencesViewModel
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
    var selectedPersonalRootMode by rememberSaveable { mutableStateOf(VaultRootMode.Personal.name) }
    var pendingQuranVerseKey by rememberSaveable { mutableStateOf<String?>(null) }
    val narrationViewModel: NarrationViewModel = hiltViewModel()
    val narrationState by narrationViewModel.narrationState.collectAsStateWithLifecycle()
    val shellViewModel: ShellPreferencesViewModel = hiltViewModel()
    val preferences by shellViewModel.userPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(pendingOpenNoteId) {
        val noteId = pendingOpenNoteId ?: return@LaunchedEffect
        navController.navigate(VaultDestination.Editor.route(noteId))
        onPendingOpenNoteConsumed()
    }

    LaunchedEffect(pendingQuranVerseKey, currentRoute, preferences.workspace) {
        if (
            pendingQuranVerseKey != null &&
            currentRoute == VaultDestination.Home.route &&
            preferences.workspace == WORKSPACE_ISLAMIC_CORPUS
        ) {
            selectedIslamicRootMode = VaultRootMode.Quran.name
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                rootBackHandlerEnabled = currentRoute == VaultDestination.Home.route,
                onQuickNoteMode = { mode ->
                    homeViewModel.createNote(folderId = null, mode = mode) { noteId ->
                        navController.navigate(VaultDestination.Editor.route(noteId, quickFocus = true))
                    }
                },
                studyContent = {
                    val studyState by homeViewModel.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        uiState = studyState,
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
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
                        onQuranReflectionsClick = {
                            navController.navigate(VaultDestination.QuranReflections.route)
                        },
                        onThemeClick = {
                            shellViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
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
                    )
                },
                quranContent = {
                    val quranViewModel: QuranReaderViewModel = hiltViewModel()
                    val quranState by quranViewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingQuranVerseKey) {
                        val verseKey = pendingQuranVerseKey ?: return@LaunchedEffect
                        quranViewModel.openBookmarkedAyah(verseKey)
                        pendingQuranVerseKey = null
                    }
                    QuranShellScreen(
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
                        onThemeClick = {
                            shellViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
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
                        onMarkCurrentSurahMemorized = quranViewModel::markCurrentSurahMemorized,
                        onToggleWeakMemorization = quranViewModel::toggleWeakMemorization,
                        onSetMemorizationConcealAmount = quranViewModel::setMemorizationConcealAmount,
                        onSetMemorizationRepeatMode = quranViewModel::setMemorizationRepeatMode,
                        onStopMemorizationRepeat = quranViewModel::stopMemorizationRepeat,
                        onPendingScrollHandled = quranViewModel::consumePendingScrollVerse,
                    )
                },
                memoriseContent = {
                    val memoriseViewModel: MemoriseViewModel = hiltViewModel()
                    val memoriseState by memoriseViewModel.uiState.collectAsStateWithLifecycle()
                    MemoriseShellScreen(
                        uiState = memoriseState,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
                        onThemeClick = {
                            shellViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
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
                    LaunchedEffect(libraryViewModel) {
                        libraryViewModel.setLibraryMode("library")
                    }
                    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        uiState = libraryState,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
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
                        onPrepareStudyNoteLinks = libraryViewModel::prepareStudyNoteLinks,
                        onCreateFolder = { parentId, name ->
                            libraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = libraryViewModel::renameFolder,
                        onMoveFolder = libraryViewModel::moveFolder,
                        onMoveFolderInOrder = libraryViewModel::moveFolderInOrder,
                        onDeleteFolder = libraryViewModel::deleteFolder,
                        onFolderExpandedChange = libraryViewModel::setFolderExpanded,
                        onViewModeChange = libraryViewModel::setViewMode,
                        onImportFiles = { uris ->
                            libraryViewModel.importFiles(uris)
                        },
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
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        showFullFileTitles = preferences.showFullFileTitles,
                    )
                },
                personalContent = {
                    val personalState by homeViewModel.personalUiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        uiState = personalState,
                        onSearchClick = {},
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
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
                            shellViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
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
                    )
                },
                personalLibraryContent = {
                    val personalLibraryViewModel: LibraryViewModel = hiltViewModel()
                    LaunchedEffect(personalLibraryViewModel) {
                        personalLibraryViewModel.setLibraryMode(FOLDER_MODE_PERSONAL_LIBRARY)
                    }
                    val personalLibraryState by personalLibraryViewModel.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        uiState = personalLibraryState,
                        workspaceTitle = preferences.workspace.workspaceLabel(),
                        workspaceOptions = WorkspaceLabels,
                        onWorkspaceSelected = { shellViewModel.setWorkspace(it.workspaceValue()) },
                        onFolderClick = { folderId ->
                            navController.navigate(VaultDestination.LibraryFolder.route(folderId, FOLDER_MODE_PERSONAL_LIBRARY))
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
                        onRenameAnnotation = personalLibraryViewModel::renameAnnotation,
                        onMoveAnnotation = personalLibraryViewModel::moveAnnotation,
                        onDeleteAnnotationNote = personalLibraryViewModel::deleteAnnotationNote,
                        onDeleteAnnotation = personalLibraryViewModel::deleteAnnotation,
                        onLinkAnnotationToStudyNote = personalLibraryViewModel::linkAnnotationToStudyNote,
                        onPrepareStudyNoteLinks = personalLibraryViewModel::prepareStudyNoteLinks,
                        onCreateFolder = { parentId, name ->
                            personalLibraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = personalLibraryViewModel::renameFolder,
                        onMoveFolder = personalLibraryViewModel::moveFolder,
                        onMoveFolderInOrder = personalLibraryViewModel::moveFolderInOrder,
                        onDeleteFolder = personalLibraryViewModel::deleteFolder,
                        onFolderExpandedChange = personalLibraryViewModel::setFolderExpanded,
                        onViewModeChange = personalLibraryViewModel::setViewMode,
                        onImportFiles = { uris ->
                            personalLibraryViewModel.importFiles(uris)
                        },
                        onReplaceDuplicatePdf = personalLibraryViewModel::replaceDuplicatePdf,
                        onSkipDuplicatePdf = personalLibraryViewModel::skipDuplicatePdf,
                        onDismissImportMessage = personalLibraryViewModel::clearImportMessage,
                        onRenameFile = personalLibraryViewModel::renameFile,
                        onMoveFile = personalLibraryViewModel::moveFile,
                        onSetFilePinned = personalLibraryViewModel::setFilePinned,
                        onDeleteFile = personalLibraryViewModel::deleteFile,
                        onExportFile = { fileId, uri ->
                            personalLibraryViewModel.exportFile(fileId, uri) {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddAttachmentTag = personalLibraryViewModel::addAttachmentTag,
                        onRemoveAttachmentTag = personalLibraryViewModel::removeAttachmentTag,
                        onAddAnnotationTag = personalLibraryViewModel::addAnnotationTag,
                        onRemoveAnnotationTag = personalLibraryViewModel::removeAnnotationTag,
                        onThemeClick = {
                            shellViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            shellViewModel.pushGoogleDriveSync {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        showFullFileTitles = preferences.showFullFileTitles,
                    )
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
                onPrepareStudyNoteLinks = viewModel::prepareStudyNoteLinks,
                onCreateFolder = { parentId, name ->
                    viewModel.createFolder(parentId = parentId, name = name)
                },
                onRenameFolder = viewModel::renameFolder,
                onMoveFolder = viewModel::moveFolder,
                onMoveFolderInOrder = viewModel::moveFolderInOrder,
                onDeleteFolder = viewModel::deleteFolder,
                onFolderExpandedChange = viewModel::setFolderExpanded,
                onViewModeChange = viewModel::setViewMode,
                onImportFiles = { uris ->
                    viewModel.importFiles(uris)
                },
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
            val viewModel: NoteViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
            val selectedTextAiState by viewModel.selectedTextAiState.collectAsStateWithLifecycle()
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
            val viewModel: NoteViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
            val narrationState by viewModel.narrationState.collectAsStateWithLifecycle()
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
                onDeviceListenClick = viewModel::startDeviceNarration,
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
                onRestoreVersion = viewModel::restoreVersion,
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
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        composable(VaultDestination.QuranReflections.route) {
            val viewModel: QuranReflectionsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            QuranReflectionsHubScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onReflectionClick = { reflection ->
                    pendingQuranVerseKey = reflection.verseKey
                    shellViewModel.setWorkspace(WORKSPACE_ISLAMIC_CORPUS)
                    navController.popBackStack(VaultDestination.Home.route, false)
                    selectedIslamicRootMode = VaultRootMode.Quran.name
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
            val pdfProgress by viewModel.pdfProgress.collectAsStateWithLifecycle()
            val pdfAnnotations by viewModel.pdfAnnotations.collectAsStateWithLifecycle()
            AttachmentViewerScreen(
                attachment = attachment,
                pdfProgress = pdfProgress,
                pdfAnnotations = pdfAnnotations,
                initialPageIndex = viewModel.initialPageIndex,
                onBackClick = { navController.popBackStack() },
                onPdfProgressChanged = viewModel::updatePdfProgress,
                onPdfFirstLoaded = viewModel::loadPdfSecondaryData,
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
                onExportAttachment = { uri ->
                    viewModel.exportAttachment(uri) {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
                onThemeSelected = viewModel::setTheme,
                onAccentColorSelected = viewModel::setAccentColor,
                onBackupSelected = { uri -> viewModel.exportBackup(uri) { backupMessage = it } },
                onRestoreSelected = { uri -> viewModel.restoreBackup(uri) { backupMessage = it } },
                onDashboardFontSizeSelected = viewModel::setDashboardFontSize,
                onNoteFontSizeSelected = viewModel::setNoteFontSize,
                onNotePreviewSelected = viewModel::setNotePreview,
                onShowFullNoteTitlesChanged = viewModel::setShowFullNoteTitles,
                onShowFullFileTitlesChanged = viewModel::setShowFullFileTitles,
                onDefaultNoteViewSelected = viewModel::setDefaultNoteView,
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
                googleDriveSignInIntent = viewModel.googleDriveSignInIntent(),
                onGoogleDriveSignInResult = { data -> viewModel.handleGoogleDriveSignInResult(data) { backupMessage = it } },
                onGoogleDrivePush = { viewModel.pushGoogleDriveSync { backupMessage = it } },
                onGoogleDriveForcePush = { viewModel.forcePushGoogleDriveSync { backupMessage = it } },
                onGoogleDrivePull = { viewModel.pullGoogleDriveSync { backupMessage = it } },
                onBackupSettingsOpened = viewModel::observeDriveRestoreState,
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
        AnimatedVisibility(
            visible = narrationState.isActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = VaultSpacing.screen,
                    vertical = if (currentRoute == VaultDestination.Home.route) 88.dp else VaultSpacing.md,
                ),
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
                selectedVoice = narrationState.voice,
                onVoiceChange = narrationViewModel::restartWithVoice,
                onPrimaryAction = {
                    if (narrationState.status != NarrationPlaybackStatus.Preparing &&
                        narrationState.status != NarrationPlaybackStatus.Generating
                    ) {
                        narrationViewModel.togglePlayback()
                    }
                },
                onStop = narrationViewModel::stop,
                onSpeedChange = narrationViewModel::setSpeed,
                onSeek = narrationViewModel::seekTo,
                onProgressTick = narrationViewModel::refreshProgress,
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
    personalLibraryContent: @Composable () -> Unit,
) {
    val modes = remember(workspace) {
        if (workspace == WORKSPACE_PERSONAL) {
            listOf(VaultRootMode.Personal, VaultRootMode.Library)
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
    var visualSelectedPage by rememberSaveable(modes) { mutableStateOf(requestedPage) }
    var lastNavTapMode by remember { mutableStateOf<VaultRootMode?>(null) }
    var lastNavTapAt by remember { mutableStateOf(0L) }

    LaunchedEffect(modes, requestedPage) {
        if (pagerState.currentPage != requestedPage) {
            visualSelectedPage = requestedPage
            pagerState.animateScrollToPage(
                page = requestedPage,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }
    }

    LaunchedEffect(pagerState, modes) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, modes.lastIndex) }
            .distinctUntilChanged()
            .collect { page ->
                visualSelectedPage = page
                onRootModeChanged(modes[page])
            }
    }

    BackHandler(enabled = rootBackHandlerEnabled && workspace == WORKSPACE_ISLAMIC_CORPUS && pagerState.currentPage > 0) {
        scope.launch {
            visualSelectedPage = 0
            pagerState.animateScrollToPage(
                page = 0,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
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
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.18f,
            ),
        ) { page ->
            when (modes[page]) {
                VaultRootMode.Study -> studyContent()
                VaultRootMode.Quran -> quranContent()
                VaultRootMode.Memorise -> memoriseContent()
                VaultRootMode.Library -> if (workspace == WORKSPACE_PERSONAL) personalLibraryContent() else libraryContent()
                VaultRootMode.Personal -> personalContent()
            }
        }

        FloatingBottomNav(
            modes = modes,
            selectedIndex = visualSelectedPage.coerceIn(0, modes.lastIndex),
            onModeSelected = { index ->
                val mode = modes[index]
                val now = System.currentTimeMillis()
                val isFastSecondTap = lastNavTapMode == mode && now - lastNavTapAt <= BottomNavDoubleTapWindowMs
                lastNavTapMode = mode
                lastNavTapAt = now
                if (isFastSecondTap) {
                    onQuickNoteMode(
                        when (mode) {
                            VaultRootMode.Study -> FOLDER_MODE_STUDY
                            VaultRootMode.Personal -> FOLDER_MODE_PERSONAL
                            else -> return@FloatingBottomNav
                        },
                    )
                    return@FloatingBottomNav
                }
                visualSelectedPage = index
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    )
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
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
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
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
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

private const val BottomNavDoubleTapWindowMs = 260L

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
