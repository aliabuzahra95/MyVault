package com.myvault.app.ui.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.ui.screens.AttachmentViewerScreen
import com.myvault.app.ui.screens.AttachmentsScreen
import com.myvault.app.ui.screens.AskAiScreen
import com.myvault.app.ui.screens.EditorScreen
import com.myvault.app.ui.screens.FolderViewScreen
import com.myvault.app.ui.screens.HomeScreen
import com.myvault.app.ui.screens.LibraryFolderScreen
import com.myvault.app.ui.screens.LibraryScreen
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
import com.myvault.app.ui.viewmodel.NoteViewModel
import com.myvault.app.ui.viewmodel.SearchViewModel
import com.myvault.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun VaultNavHost(
    pendingOpenNoteId: String? = null,
    onPendingOpenNoteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

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
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            )
        },
    ) {
        composable(VaultDestination.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val studyState by homeViewModel.uiState.collectAsState()
            val personalState by homeViewModel.personalUiState.collectAsState()
            val preferences by settingsViewModel.userPreferences.collectAsState()
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
                studyContent = {
                    HomeScreen(
                        uiState = studyState,
                        onSearchClick = {},
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = {},
                        onNoteClick = openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId) { noteId ->
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
                            homeViewModel.importDocument(uri) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onOpenAttachmentsClick = {
                            navController.navigate(VaultDestination.Attachments.route)
                        },
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.uploadCloudBackup {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        },
                        quickBackupRecommended = preferences.quickBackupRecommended(),
                        dashboardFontSizeSp = preferences.dashboardFontSize.toDashboardFontSizeSp(),
                        currentFolderMode = FOLDER_MODE_STUDY,
                    )
                },
                libraryContent = {
                    val libraryViewModel: LibraryViewModel = hiltViewModel()
                    val libraryState by libraryViewModel.uiState.collectAsState()
                    LibraryScreen(
                        uiState = libraryState,
                        onFolderClick = { folderId ->
                            navController.navigate(VaultDestination.LibraryFolder.route(folderId))
                        },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onCreateFolder = { parentId, name ->
                            libraryViewModel.createFolder(parentId = parentId, name = name)
                        },
                        onRenameFolder = libraryViewModel::renameFolder,
                        onMoveFolder = libraryViewModel::moveFolder,
                        onDeleteFolder = libraryViewModel::deleteFolder,
                        onFolderExpandedChange = libraryViewModel::setFolderExpanded,
                        onViewModeChange = libraryViewModel::setViewMode,
                        onImportFile = { uri ->
                            libraryViewModel.importFile(uri) { attachmentId ->
                                navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                            }
                        },
                    )
                },
                personalContent = {
                    HomeScreen(
                        uiState = personalState,
                        onSearchClick = {},
                        onSearchQueryChange = homeViewModel::setSearchQuery,
                        onSettingsClick = { navController.navigate(VaultDestination.Settings.route) },
                        onFolderClick = {},
                        onNoteClick = openNote,
                        onNewNoteClick = { folderId ->
                            homeViewModel.createNote(folderId = folderId) { noteId ->
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
                            homeViewModel.importDocument(uri) { noteId ->
                                navController.navigate(VaultDestination.Editor.route(noteId))
                            }
                        },
                        onAttachmentClick = { attachmentId ->
                            navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                        },
                        onOpenAttachmentsClick = {
                            navController.navigate(VaultDestination.Attachments.route)
                        },
                        onThemeClick = {
                            settingsViewModel.setTheme(
                                if (preferences.theme == VaultThemeMode.Dark) VaultThemeMode.Light else VaultThemeMode.Dark,
                            )
                        },
                        onQuickBackupClick = {
                            settingsViewModel.uploadCloudBackup {
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
            val uiState by viewModel.uiState.collectAsState()
            LibraryFolderScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onFolderClick = { folderId ->
                    navController.navigate(VaultDestination.LibraryFolder.route(folderId))
                },
                onAttachmentClick = { attachmentId ->
                    navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                },
                onCreateFolder = { parentId, name ->
                    viewModel.createFolder(parentId = parentId, name = name)
                },
                onRenameFolder = viewModel::renameFolder,
                onMoveFolder = viewModel::moveFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onFolderExpandedChange = viewModel::setFolderExpanded,
                onViewModeChange = viewModel::setViewMode,
                onImportFile = { uri ->
                    viewModel.importFile(uri) { attachmentId ->
                        navController.navigate(VaultDestination.AttachmentViewer.route(attachmentId))
                    }
                },
            )
        }
        composable(
            route = VaultDestination.FolderView.route,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
        ) {
            val viewModel: FolderViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val preferences by settingsViewModel.userPreferences.collectAsState()
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
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            val viewModel: NoteViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val aiState by viewModel.aiState.collectAsState()
            val selectedTextAiState by viewModel.selectedTextAiState.collectAsState()
            val preferences by settingsViewModel.userPreferences.collectAsState()
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
            )
        }
        composable(
            route = VaultDestination.Reading.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            val viewModel: NoteViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val aiState by viewModel.aiState.collectAsState()
            val preferences by settingsViewModel.userPreferences.collectAsState()
            ReadingScreen(
                uiState = uiState,
                aiState = aiState,
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
            val uiState by viewModel.uiState.collectAsState()
            val aiState by viewModel.aiState.collectAsState()
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
            val uiState by viewModel.uiState.collectAsState()
            val preferences by settingsViewModel.userPreferences.collectAsState()
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
            arguments = listOf(navArgument("attachmentId") { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            val viewModel: AttachmentViewerViewModel = hiltViewModel()
            val attachment by viewModel.attachment.collectAsState()
            val pdfProgress by viewModel.pdfProgress.collectAsState()
            AttachmentViewerScreen(
                attachment = attachment,
                pdfProgress = pdfProgress,
                onBackClick = { navController.popBackStack() },
                onPdfProgressChanged = viewModel::updatePdfProgress,
                onDeleteAttachment = {
                    viewModel.deleteAttachment {
                        Toast.makeText(context, "Attachment deleted", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(VaultDestination.Attachments.route) {
            val viewModel: AttachmentsViewModel = hiltViewModel()
            val attachments by viewModel.attachments.collectAsState()
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
            val preferences by viewModel.userPreferences.collectAsState()
            val storageLabel by viewModel.storageLabel.collectAsState()
            val recentlyDeleted by viewModel.recentlyDeleted.collectAsState()
            val cloudBackup by viewModel.cloudBackupState.collectAsState()
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
                cloudBackup = cloudBackup,
                onRestoreDeletedNote = viewModel::restoreNote,
                onPermanentlyDeleteNote = { noteId -> viewModel.permanentlyDeleteNote(noteId) { backupMessage = it } },
                onRestoreDeletedFolder = viewModel::restoreFolder,
                onPermanentlyDeleteFolder = { folderId -> viewModel.permanentlyDeleteFolder(folderId) { backupMessage = it } },
                onPermanentlyDeleteAllDeleted = { viewModel.permanentlyDeleteAllRecentlyDeleted { backupMessage = it } },
                onCloudSignUp = { email, password -> viewModel.signUpToCloud(email, password) { backupMessage = it } },
                onCloudSignIn = { email, password -> viewModel.signInToCloud(email, password) { backupMessage = it } },
                onCloudSignOut = { viewModel.signOutOfCloud { backupMessage = it } },
                onCloudBackup = { viewModel.uploadCloudBackup { backupMessage = it } },
                onCloudRestore = { viewModel.restoreCloudBackup { backupMessage = it } },
                onVerifyBackup = { viewModel.verifyBackupIntegrity { backupMessage = it } },
                backupMessage = backupMessage,
                onDismissBackupMessage = { backupMessage = null },
            )
        }
    }
}

private enum class VaultRootMode(val label: String) {
    Study("Study"),
    Library("Library"),
    Personal("Personal"),
}

@Composable
private fun StudyLibraryPersonalShell(
    studyContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    personalContent: @Composable () -> Unit,
) {
    val modes = VaultRootMode.entries
    val pagerState = rememberPagerState(initialPage = VaultRootMode.Study.ordinal) { modes.size }
    val scope = rememberCoroutineScope()
    val colors = VaultThemeTokens.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> modes[page].name },
        ) { page ->
            when (modes[page]) {
                VaultRootMode.Study -> studyContent()
                VaultRootMode.Library -> libraryContent()
                VaultRootMode.Personal -> personalContent()
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = VaultSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEachIndexed { index, mode ->
                val selected = pagerState.currentPage == index
                RootModePill(
                    label = mode.label,
                    selected = selected,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RootModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = if (selected) colors.accentSoft else colors.elevated.copy(alpha = 0.74f),
        contentColor = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.78f),
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.7f)),
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 5.dp else 1.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        )
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
    val mostRecent = maxOf(lastLocalBackupAt, lastCloudBackupAt)
    if (mostRecent <= 0L) return true
    return System.currentTimeMillis() - mostRecent > 7L * 24L * 60L * 60L * 1000L
}
