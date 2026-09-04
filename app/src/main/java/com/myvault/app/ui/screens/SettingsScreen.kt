package com.myvault.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.SettingsRow
import com.myvault.app.ui.components.ThemePreviewCard
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.preferences.AzureSpeechSettings
import com.myvault.app.data.narration.AzureNarrationConfig
import com.myvault.app.data.narration.NarrationProvider
import com.myvault.app.data.sync.DriveConflictMessage
import com.myvault.app.data.sync.DriveRestoreState
import com.myvault.app.ui.viewmodel.DeletedItemUiState
import com.myvault.app.ui.viewmodel.RecentlyDeletedUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FrozenSettingsDestination {
    Main,
    Theme,
    Security,
    Storage,
    RecentlyDeleted,
    GoogleDrive,
    BackupRestore,
    FormattingAccount,
    AzureSpeech,
}

@Composable
fun SettingsScreen(
    preferences: VaultUserPreferences,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit = onBackClick,
    onThemeSelected: (VaultThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    workspaceLabel: String = "Islamic Corpus",
    accountEmail: String = "",
    onMaterialYouEnabledChange: (Boolean) -> Unit = {},
    onAccentColorSelected: (String) -> Unit = {},
    onBackupSelected: (Uri) -> Unit = {},
    onRestoreSelected: (Uri) -> Unit = {},
    onDashboardFontSizeSelected: (String) -> Unit = {},
    onNoteFontSizeSelected: (String) -> Unit = {},
    onNotePreviewSelected: (String) -> Unit = {},
    onShowFullNoteTitlesChanged: (Boolean) -> Unit = {},
    onShowFullFileTitlesChanged: (Boolean) -> Unit = {},
    onDefaultNoteViewSelected: (String) -> Unit = {},
    azureSpeechSettings: AzureSpeechSettings = AzureSpeechSettings(),
    onNarrationProviderSelected: (String) -> Unit = {},
    onAzureSpeechSettingsSaved: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onSecurityLockChanged: (Boolean) -> Unit = {},
    onSecurityLockTimeoutSelected: (Long) -> Unit = {},
    storageLabel: String = "Calculating...",
    recentlyDeleted: RecentlyDeletedUiState = RecentlyDeletedUiState(),
    recentlyDeletedLoaded: Boolean = false,
    onRecentlyDeletedOpened: () -> Unit = {},
    onRestoreDeletedNote: (String) -> Unit = {},
    onPermanentlyDeleteNote: (String) -> Unit = {},
    onRestoreDeletedFolder: (String) -> Unit = {},
    onPermanentlyDeleteFolder: (String) -> Unit = {},
    onPermanentlyDeleteAllDeleted: () -> Unit = {},
    onPrepareGoogleDriveSignIn: ((Intent) -> Unit) -> Unit = {},
    onGoogleDriveSignInResult: (Intent?, (Intent) -> Unit) -> Unit = { _, _ -> },
    onGoogleDriveConsentResult: (Boolean) -> Unit = {},
    onGoogleDrivePush: ((Intent) -> Unit) -> Unit = { _ -> },
    onGoogleDriveForcePush: ((Intent) -> Unit) -> Unit = { _ -> },
    onGoogleDrivePull: ((Intent) -> Unit) -> Unit = { _ -> },
    onBackupSettingsOpened: () -> Unit = {},
    formattingAccountEmail: String = "",
    onFormattingAccountLogin: (String, String) -> Unit = { _, _ -> },
    onFormattingAccountLogout: () -> Unit = {},
    driveRestoreState: DriveRestoreState = DriveRestoreState(),
    backupMessage: String? = null,
    onDismissBackupMessage: () -> Unit = {},
    initialSection: String? = null,
    onInitialSectionConsumed: () -> Unit = {},
) {
    var destination by remember { mutableStateOf(FrozenSettingsDestination.Main) }
    var choiceDialog by remember { mutableStateOf<String?>(null) }
    var azureEditorOpen by remember { mutableStateOf(false) }
    var formattingEditorOpen by remember { mutableStateOf(false) }
    var restoreConfirmUri by remember { mutableStateOf<Uri?>(null) }
    var driveRestoreConfirmOpen by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<DeletedTarget?>(null) }
    var deleteAllConfirmOpen by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(onBackupSelected) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreConfirmUri = it }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onGoogleDriveConsentResult(it.resultCode == Activity.RESULT_OK)
    }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onGoogleDriveSignInResult(result.data) { consentLauncher.launch(it) }
    }

    LaunchedEffect(initialSection) {
        when (initialSection) {
            "azure_speech" -> destination = FrozenSettingsDestination.AzureSpeech
            null -> return@LaunchedEffect
        }
        onInitialSectionConsumed()
    }

    BackHandler(destination != FrozenSettingsDestination.Main) { destination = FrozenSettingsDestination.Main }
    LaunchedEffect(destination) {
        if (destination == FrozenSettingsDestination.RecentlyDeleted) onRecentlyDeletedOpened()
        if (destination == FrozenSettingsDestination.BackupRestore || destination == FrozenSettingsDestination.GoogleDrive) onBackupSettingsOpened()
    }

    when (destination) {
        FrozenSettingsDestination.Main -> FrozenSettingsMain(
            modifier = modifier,
            preferences = preferences,
            workspaceLabel = workspaceLabel,
            accountEmail = accountEmail,
            formattingAccountEmail = formattingAccountEmail,
            azureSpeechSettings = azureSpeechSettings,
            storageLabel = storageLabel,
            deletedCount = if (recentlyDeletedLoaded) recentlyDeleted.notes.size + recentlyDeleted.folders.size else null,
            onMenu = onMenuClick,
            onNavigate = { destination = it },
            onMaterialYouChange = onMaterialYouEnabledChange,
            onDashboardFont = { choiceDialog = "dashboard" },
            onNoteFont = { choiceDialog = "note_font" },
            onNotePreview = { choiceDialog = "preview" },
            onFullNotes = { onShowFullNoteTitlesChanged(!preferences.showFullNoteTitles) },
            onFullFiles = { onShowFullFileTitlesChanged(!preferences.showFullFileTitles) },
            onDefaultView = { choiceDialog = "default_view" },
            onNarration = { choiceDialog = "narration" },
        )
        FrozenSettingsDestination.Theme -> FrozenThemeSettings(
            preferences = preferences,
            onBack = { destination = FrozenSettingsDestination.Main },
            onThemeSelected = onThemeSelected,
            onMaterialYouChange = onMaterialYouEnabledChange,
            onAccentSelected = onAccentColorSelected,
        )
        FrozenSettingsDestination.Security -> FrozenSecuritySettings(
            preferences = preferences,
            onBack = { destination = FrozenSettingsDestination.Main },
            onLockChange = onSecurityLockChanged,
            onTimer = { choiceDialog = "lock_timer" },
        )
        FrozenSettingsDestination.Storage -> FrozenStorageSettings(
            storageLabel = storageLabel,
            deletedCount = if (recentlyDeletedLoaded) recentlyDeleted.notes.size + recentlyDeleted.folders.size else null,
            onBack = { destination = FrozenSettingsDestination.Main },
            onRecentlyDeleted = { destination = FrozenSettingsDestination.RecentlyDeleted },
        )
        FrozenSettingsDestination.RecentlyDeleted -> FrozenRecentlyDeletedSettings(
            state = recentlyDeleted,
            loaded = recentlyDeletedLoaded,
            onBack = { destination = FrozenSettingsDestination.Storage },
            onRestoreNote = onRestoreDeletedNote,
            onRestoreFolder = onRestoreDeletedFolder,
            onDeleteNote = { permanentDeleteTarget = DeletedTarget.Note(it.id, it.title) },
            onDeleteFolder = { permanentDeleteTarget = DeletedTarget.Folder(it.id, it.title) },
            onClearAll = { deleteAllConfirmOpen = true },
        )
        FrozenSettingsDestination.GoogleDrive -> FrozenGoogleDriveSettings(
            preferences = preferences,
            state = driveRestoreState,
            onBack = { destination = FrozenSettingsDestination.Main },
            onConnect = { onPrepareGoogleDriveSignIn { signInLauncher.launch(it) } },
            onBackupRestore = { destination = FrozenSettingsDestination.BackupRestore },
        )
        FrozenSettingsDestination.BackupRestore -> FrozenBackupRestoreSettings(
            preferences = preferences,
            state = driveRestoreState,
            onBack = { destination = FrozenSettingsDestination.Main },
            onLocalBackup = { backupLauncher.launch("my-vault-${System.currentTimeMillis()}.vaultbackup") },
            onLocalRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
            onDrivePush = { onGoogleDrivePush { consentLauncher.launch(it) } },
            onDrivePull = { driveRestoreConfirmOpen = true },
        )
        FrozenSettingsDestination.FormattingAccount -> FrozenFormattingAccountSettings(
            email = formattingAccountEmail,
            onBack = { destination = FrozenSettingsDestination.Main },
            onSignIn = { formattingEditorOpen = true },
            onSignOut = onFormattingAccountLogout,
        )
        FrozenSettingsDestination.AzureSpeech -> {
            FrozenAzureSpeechSettings(
                settings = azureSpeechSettings,
                onBack = { destination = FrozenSettingsDestination.Main },
                onEdit = { azureEditorOpen = true },
            )
        }
    }

    when (choiceDialog) {
        "dashboard" -> FrozenChoiceDialog("Dashboard font size", listOf(SettingsChoice("small", "Small"), SettingsChoice("medium", "Medium"), SettingsChoice("medium_large", "Medium-Large"), SettingsChoice("large", "Large")), preferences.dashboardFontSize, { choiceDialog = null }) { onDashboardFontSizeSelected(it); choiceDialog = null }
        "note_font" -> FrozenChoiceDialog("Note editor font size", listOf(SettingsChoice("small", "Small"), SettingsChoice("medium", "Medium"), SettingsChoice("large", "Large")), preferences.noteFontSize, { choiceDialog = null }) { onNoteFontSizeSelected(it); choiceDialog = null }
        "preview" -> FrozenChoiceDialog("Note preview", listOf(SettingsChoice("off", "Off"), SettingsChoice("one", "1 line"), SettingsChoice("two", "2 lines")), preferences.notePreview, { choiceDialog = null }) { onNotePreviewSelected(it); choiceDialog = null }
        "default_view" -> FrozenChoiceDialog("Default note view", listOf(SettingsChoice("reading", "Reading"), SettingsChoice("editing", "Editing")), preferences.defaultNoteView, { choiceDialog = null }) { onDefaultNoteViewSelected(it); choiceDialog = null }
        "narration" -> FrozenChoiceDialog("Default Listen provider", NarrationProvider.entries.map { SettingsChoice(it.storedValue, it.label) }, preferences.narrationProvider, { choiceDialog = null }) { onNarrationProviderSelected(it); choiceDialog = null }
        "lock_timer" -> SettingsLongChoiceDialog("Auto-lock timer", listOf(SettingsLongChoice(30_000L, "30 seconds"), SettingsLongChoice(60_000L, "1 minute"), SettingsLongChoice(300_000L, "5 minutes"), SettingsLongChoice(1_800_000L, "30 minutes"), SettingsLongChoice(3_600_000L, "1 hour")), preferences.securityLockTimeoutMs, { choiceDialog = null }) { onSecurityLockTimeoutSelected(it); choiceDialog = null }
    }
    if (azureEditorOpen) AzureSpeechSettingsDialog(azureSpeechSettings, { azureEditorOpen = false }) { key, region, voice, arabic -> onAzureSpeechSettingsSaved(key, region, voice, arabic); azureEditorOpen = false }
    if (formattingEditorOpen) ChatGptFormattingLoginDialog(formattingAccountEmail, { formattingEditorOpen = false }, { email, password -> onFormattingAccountLogin(email, password); formattingEditorOpen = false }, { onFormattingAccountLogout(); formattingEditorOpen = false })
    restoreConfirmUri?.let { uri ->
        FrozenConfirmDialog("Restore backup?", "This merges the selected trusted backup into your current vault.", "Restore", { restoreConfirmUri = null }, { onRestoreSelected(uri); restoreConfirmUri = null })
    }
    if (driveRestoreConfirmOpen) FrozenConfirmDialog("Restore from Drive?", "Pull the latest MyVault backup from Google Drive onto this device.", "Restore", { driveRestoreConfirmOpen = false }, onConfirm = { driveRestoreConfirmOpen = false; onGoogleDrivePull { consentLauncher.launch(it) } })
    if (deleteAllConfirmOpen) FrozenConfirmDialog("Delete all forever?", "All items in Recently Deleted will be permanently deleted.", "Delete all", { deleteAllConfirmOpen = false }, onConfirm = { onPermanentlyDeleteAllDeleted(); deleteAllConfirmOpen = false })
    permanentDeleteTarget?.let { target -> FrozenConfirmDialog("Delete forever?", "${target.title} will be permanently deleted.", "Delete forever", { permanentDeleteTarget = null }, onConfirm = { when (target) { is DeletedTarget.Note -> onPermanentlyDeleteNote(target.id); is DeletedTarget.Folder -> onPermanentlyDeleteFolder(target.id) }; permanentDeleteTarget = null }) }
    backupMessage?.let { FrozenConfirmDialog("Vault backup", it, "OK", onDismissBackupMessage, onDismissBackupMessage, showCancel = false) }
}

@Composable
private fun FrozenSettingsMain(
    preferences: VaultUserPreferences,
    workspaceLabel: String,
    accountEmail: String,
    formattingAccountEmail: String,
    azureSpeechSettings: AzureSpeechSettings,
    storageLabel: String,
    deletedCount: Int?,
    onMenu: () -> Unit,
    onNavigate: (FrozenSettingsDestination) -> Unit,
    onMaterialYouChange: (Boolean) -> Unit,
    onDashboardFont: () -> Unit,
    onNoteFont: () -> Unit,
    onNotePreview: () -> Unit,
    onFullNotes: () -> Unit,
    onFullFiles: () -> Unit,
    onDefaultView: () -> Unit,
    onNarration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrozenSettingsPage(
        title = "Settings",
        onNavigation = onMenu,
        modifier = modifier,
        navigationIcon = Icons.Rounded.Menu,
        navigationDescription = "Open navigation",
        subtitle = "Preferences & account",
    ) {
        item {
            FrozenProfileRow(workspaceLabel, accountEmail)
        }
        frozenSection("APPEARANCE") {
            FrozenSettingsRow(Icons.Rounded.DarkMode, "Theme", value = preferences.theme.displayName(), onClick = { onNavigate(FrozenSettingsDestination.Theme) })
            FrozenSettingsRow(Icons.Rounded.ColorLens, "Accent colour", value = if (preferences.materialYouEnabled) "Dynamic" else preferences.accentColor, onClick = { onNavigate(FrozenSettingsDestination.Theme) })
            FrozenSettingsRow(Icons.Rounded.Palette, "Material You", subtitle = "Use Android dynamic colour", switchValue = preferences.materialYouEnabled, onSwitch = onMaterialYouChange)
        }
        frozenSection("READING & DISPLAY") {
            FrozenSettingsRow(Icons.Rounded.TextFields, "Dashboard font size", value = preferences.dashboardFontSize.displayPreference(), onClick = onDashboardFont)
            FrozenSettingsRow(Icons.Rounded.EditNote, "Note editor font size", value = preferences.noteFontSize.displayPreference(), onClick = onNoteFont)
            FrozenSettingsRow(Icons.Rounded.Description, "Note preview", value = preferences.notePreview.displayNotePreview(), onClick = onNotePreview)
            FrozenSettingsRow(Icons.Rounded.ViewHeadline, "Show full note titles", switchValue = preferences.showFullNoteTitles, onSwitch = { onFullNotes() })
            FrozenSettingsRow(Icons.Rounded.ViewHeadline, "Show full file titles", switchValue = preferences.showFullFileTitles, onSwitch = { onFullFiles() })
            FrozenSettingsRow(Icons.Rounded.Visibility, "Default note view", value = preferences.defaultNoteView.displayPreference(), onClick = onDefaultView)
        }
        frozenSection("READING & LISTENING") {
            FrozenSettingsRow(Icons.Rounded.Headphones, "Default Listen provider", value = NarrationProvider.fromStoredValue(preferences.narrationProvider).label, onClick = onNarration)
            FrozenSettingsRow(
                Icons.Rounded.VolumeUp,
                "Azure Speech",
                value = if (azureSpeechSettings.apiKey.isBlank()) "Not configured" else "Configured",
                onClick = { onNavigate(FrozenSettingsDestination.AzureSpeech) },
            )
        }
        frozenSection("SECURITY & PRIVACY") {
            FrozenSettingsRow(Icons.Rounded.Security, "Security lock", value = if (preferences.securityLockEnabled) "On" else "Off", onClick = { onNavigate(FrozenSettingsDestination.Security) })
        }
        frozenSection("STORAGE & DATA") {
            FrozenSettingsRow(Icons.Rounded.Storage, "Storage usage", value = storageLabel, onClick = { onNavigate(FrozenSettingsDestination.Storage) })
            FrozenSettingsRow(Icons.Rounded.FolderDelete, "Recently Deleted", value = deletedCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "View", onClick = { onNavigate(FrozenSettingsDestination.RecentlyDeleted) })
        }
        frozenSection("VAULT & ACCOUNT") {
            FrozenSettingsRow(Icons.Rounded.Cloud, "Google Drive", value = preferences.googleDriveAccountEmail.ifBlank { "Not connected" }, onClick = { onNavigate(FrozenSettingsDestination.GoogleDrive) })
            FrozenSettingsRow(Icons.Rounded.SettingsBackupRestore, "Backup / Restore", value = preferences.backupSummary(), onClick = { onNavigate(FrozenSettingsDestination.BackupRestore) })
            FrozenSettingsRow(Icons.Rounded.ManageAccounts, "Formatting account", value = formattingAccountEmail.ifBlank { "Not signed in" }, onClick = { onNavigate(FrozenSettingsDestination.FormattingAccount) })
        }
    }
}

@Composable
private fun FrozenProfileRow(workspaceLabel: String, accountEmail: String) {
    val colors = VaultThemeTokens.colors
    val accountLabel = accountEmail
        .substringBefore('@')
        .takeIf { it.isNotBlank() }
        ?: "Your account"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = VaultShapes.md, color = colors.accent, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Person, null, modifier = Modifier.padding(9.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(accountLabel, style = FrozenSettingsTypography.rowTitle, color = colors.text)
                Text("$workspaceLabel · account and profile", style = FrozenSettingsTypography.rowSubtitle, color = colors.textSecondary)
            }
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
        }
    }
}

@Composable
private fun FrozenThemeSettings(
    preferences: VaultUserPreferences,
    onBack: () -> Unit,
    onThemeSelected: (VaultThemeMode) -> Unit,
    onMaterialYouChange: (Boolean) -> Unit,
    onAccentSelected: (String) -> Unit,
) {
    val accents = listOf("#4F88E6", "#2E9B7C", "#C98A2F", "#8463D6", "#D66B5F")
    FrozenSettingsPage("Theme settings", onBack, subtitle = "Style, mode & colour") {
        frozenSection("STYLE") {
            FrozenSettingsRow(
                Icons.Rounded.Palette,
                "Material You",
                subtitle = if (preferences.materialYouEnabled) "Android dynamic colour is active" else "Use Android dynamic colour",
                switchValue = preferences.materialYouEnabled,
                onSwitch = onMaterialYouChange,
            )
        }
        item { FrozenSectionLabel("THEME") }
        item {
            val colors = VaultThemeTokens.colors
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column {
                VaultThemeMode.entries.forEach { mode ->
                        FrozenThemeModeRow(
                            mode = mode,
                            selected = mode == preferences.theme,
                            onClick = { onThemeSelected(mode) },
                        )
                    }
                }
            }
        }
        item { FrozenSectionLabel("ACCENT") }
        item {
            val colors = VaultThemeTokens.colors
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (preferences.materialYouEnabled) "Controlled by Material You" else "MyVault accent",
                        style = FrozenSettingsTypography.rowTitle,
                        color = colors.text,
                    )
                    Text(
                        if (preferences.materialYouEnabled) "Turn Material You off to choose a MyVault accent." else "Choose a restrained accent for actions and selected states.",
                        style = FrozenSettingsTypography.rowSubtitle,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.alpha(if (preferences.materialYouEnabled) 0.38f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        accents.forEach { hex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Blue)
                            val selected = preferences.accentColor.equals(hex, true)
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .size(24.dp)
                                    .background(color, VaultShapes.sm)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) colors.text else colors.border,
                                        VaultShapes.sm,
                                    )
                                    .clickable(enabled = !preferences.materialYouEnabled) { onAccentSelected(hex) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrozenThemeModeRow(
    mode: VaultThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val description = when (mode) {
        VaultThemeMode.Light -> "Cool, soft and bright"
        VaultThemeMode.Dark -> "Deep charcoal surfaces"
        VaultThemeMode.Oled -> "True black for OLED displays"
        VaultThemeMode.FollowSystemDark -> "Android light · Dark after sunset"
        VaultThemeMode.FollowSystemOled -> "Android light · OLED after sunset"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.inset else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(shape = VaultShapes.sm, color = colors.inset, modifier = Modifier.size(28.dp)) {
            Icon(
                if (mode == VaultThemeMode.Light) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                null,
                modifier = Modifier.padding(6.dp),
                tint = colors.accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(mode.displayName(), style = FrozenSettingsTypography.rowTitle, color = colors.text)
            Text(description, style = FrozenSettingsTypography.rowSubtitle, color = colors.textSecondary)
        }
        if (selected) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.dp, colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun FrozenSecuritySettings(preferences: VaultUserPreferences, onBack: () -> Unit, onLockChange: (Boolean) -> Unit, onTimer: () -> Unit) {
    FrozenSettingsPage("Security & Privacy", onBack) {
        frozenSection("APP LOCK") {
            FrozenSettingsRow(Icons.Rounded.Lock, "Security lock", subtitle = "Require device authentication to open MyVault", switchValue = preferences.securityLockEnabled, onSwitch = onLockChange)
            FrozenSettingsRow(Icons.Rounded.Timer, "Auto-lock timer", value = preferences.securityLockTimeoutMs.displayLockTimeout(), enabled = preferences.securityLockEnabled, onClick = onTimer)
        }
    }
}

@Composable
private fun FrozenStorageSettings(storageLabel: String, deletedCount: Int?, onBack: () -> Unit, onRecentlyDeleted: () -> Unit) {
    FrozenSettingsPage("Storage & Data", onBack) {
        item { FrozenSectionLabel("STORAGE USAGE") }
        item {
            val colors = VaultThemeTokens.colors
            Surface(Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen), color = colors.surface, shape = VaultShapes.lg, border = BorderStroke(1.dp, colors.border)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.Storage, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Text(storageLabel, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    Text("Used by MyVault on this device", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
        frozenSection("DELETED ITEMS") {
            FrozenSettingsRow(Icons.Rounded.FolderDelete, "Recently Deleted", value = deletedCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Notes and folders", onClick = onRecentlyDeleted)
        }
    }
}

@Composable
private fun FrozenRecentlyDeletedSettings(
    state: RecentlyDeletedUiState,
    loaded: Boolean,
    onBack: () -> Unit,
    onRestoreNote: (String) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onDeleteNote: (DeletedItemUiState) -> Unit,
    onDeleteFolder: (DeletedItemUiState) -> Unit,
    onClearAll: () -> Unit,
) {
    val total = state.notes.size + state.folders.size
    FrozenSettingsPage("Recently Deleted", onBack, headerAction = if (loaded && total > 0) ({ TextButton(onClick = onClearAll) { Text("Clear all", color = MaterialTheme.colorScheme.error) } }) else null) {
        if (!loaded) item { FrozenEmptyState("Loading deleted items…") }
        else if (total == 0) item { FrozenEmptyState("Recently Deleted is empty") }
        else {
            if (state.folders.isNotEmpty()) item { FrozenSectionLabel("FOLDERS") }
            items(state.folders.size) { index ->
                FrozenDeletedRow(state.folders[index], onRestore = { onRestoreFolder(state.folders[index].id) }, onDelete = { onDeleteFolder(state.folders[index]) })
            }
            if (state.notes.isNotEmpty()) item { FrozenSectionLabel("NOTES") }
            items(state.notes.size) { index ->
                FrozenDeletedRow(state.notes[index], onRestore = { onRestoreNote(state.notes[index].id) }, onDelete = { onDeleteNote(state.notes[index]) })
            }
        }
    }
}

@Composable
private fun FrozenGoogleDriveSettings(preferences: VaultUserPreferences, state: DriveRestoreState, onBack: () -> Unit, onConnect: () -> Unit, onBackupRestore: () -> Unit) {
    FrozenSettingsPage("Google Drive", onBack) {
        item {
            val connected = preferences.googleDriveAccountEmail.isNotBlank()
            val colors = VaultThemeTokens.colors
            Surface(Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen), color = colors.surface, shape = VaultShapes.lg, border = BorderStroke(1.dp, colors.border)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Cloud, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Text(if (connected) "Google Drive connected" else "Connect Google Drive", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text(preferences.googleDriveAccountEmail.ifBlank { "Connect the Google account that holds your MyVault backup." }, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Button(onClick = onConnect) { Text(if (connected) "Change account" else "Connect") }
                }
            }
        }
        frozenSection("VAULT BACKUP") {
            FrozenSettingsRow(Icons.Rounded.SettingsBackupRestore, "Backup / Restore", value = if (state.active) "In progress" else preferences.backupSummary(), onClick = onBackupRestore)
        }
    }
}

@Composable
private fun FrozenBackupRestoreSettings(preferences: VaultUserPreferences, state: DriveRestoreState, onBack: () -> Unit, onLocalBackup: () -> Unit, onLocalRestore: () -> Unit, onDrivePush: () -> Unit, onDrivePull: () -> Unit) {
    val lastBackupAt = state.confirmedLastBackupAt(preferences.lastGoogleDriveSyncAt)
    FrozenSettingsPage("Backup / Restore", onBack) {
        frozenSection("GOOGLE DRIVE") {
            FrozenSettingsRow(Icons.Rounded.Backup, "Back up now", value = "Last backup: ${lastBackupAt.displayBackupTime()}", enabled = preferences.googleDriveAccountEmail.isNotBlank() && !state.active, onClick = onDrivePush)
            FrozenSettingsRow(Icons.Rounded.Restore, "Restore from Drive", value = if (state.active) "In progress" else "Latest Drive backup", enabled = preferences.googleDriveAccountEmail.isNotBlank() && !state.active, onClick = onDrivePull)
        }
        frozenSection("LOCAL BACKUP") {
            FrozenSettingsRow(Icons.Rounded.Backup, "Export backup file", value = preferences.lastLocalBackupAt.displayBackupTime(), onClick = onLocalBackup)
            FrozenSettingsRow(Icons.Rounded.Restore, "Import backup file", subtitle = "Choose a trusted .vaultbackup file", onClick = onLocalRestore)
        }
        if (state.active || state.isFinished) {
            item {
                DriveRestoreProgressCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                )
            }
        }
    }
}

@Composable
private fun FrozenFormattingAccountSettings(email: String, onBack: () -> Unit, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    FrozenSettingsPage("Formatting account", onBack) {
        frozenSection("ACCOUNT") {
            FrozenSettingsRow(Icons.Rounded.AccountCircle, if (email.isBlank()) "Sign in" else email, subtitle = if (email.isBlank()) "Connect formatting services" else "Formatting account connected", onClick = onSignIn)
            if (email.isNotBlank()) FrozenSettingsRow(Icons.Rounded.ManageAccounts, "Sign out", subtitle = "Remove this formatting session from this device", onClick = onSignOut)
        }
    }
}

@Composable
private fun FrozenAzureSpeechSettings(settings: AzureSpeechSettings, onBack: () -> Unit, onEdit: () -> Unit) {
    FrozenSettingsPage("Azure Speech", onBack) {
        frozenSection("CONFIGURATION") {
            FrozenSettingsRow(Icons.Rounded.Verified, "Connection", value = if (settings.apiKey.isBlank()) "Not configured" else "Configured", onClick = onEdit)
            FrozenSettingsRow(Icons.Rounded.Cloud, "Region", value = settings.region.ifBlank { "Not set" }, onClick = onEdit)
            FrozenSettingsRow(Icons.Rounded.VolumeUp, "English voice", value = settings.voice, onClick = onEdit)
            FrozenSettingsRow(Icons.Rounded.VolumeUp, "Arabic voice", value = settings.arabicVoice, onClick = onEdit)
        }
    }
}

@Composable
private fun FrozenSettingsPage(
    title: String,
    onNavigation: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector = Icons.Rounded.ArrowBack,
    navigationDescription: String = "Back",
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.bg) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigation, modifier = Modifier.size(48.dp)) {
                        Icon(
                            navigationIcon,
                            navigationDescription,
                            modifier = Modifier.size(18.dp),
                            tint = colors.text,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = FrozenSettingsTypography.pageTitle, color = colors.text)
                        if (subtitle != null) {
                            Text(subtitle, style = FrozenSettingsTypography.rowSubtitle, color = colors.textSecondary)
                        }
                    }
                    headerAction?.invoke()
                }
            }
            content()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.frozenSection(title: String, rows: @Composable ColumnScope.() -> Unit) {
    item { FrozenSectionLabel(title) }
    item {
        val colors = VaultThemeTokens.colors
        Surface(Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen), color = colors.surface, shape = VaultShapes.lg, border = BorderStroke(1.dp, colors.border)) {
            Column(content = rows)
        }
    }
}

@Composable
private fun FrozenSectionLabel(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = 1.dp), style = FrozenSettingsTypography.sectionLabel, color = VaultThemeTokens.colors.textSecondary)
}

@Composable
private fun FrozenSettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    switchValue: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = VaultThemeTokens.colors
    val action = onClick ?: if (switchValue != null && onSwitch != null) ({ onSwitch(!switchValue) }) else null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (action != null && enabled) Modifier.clickable(onClick = action) else Modifier).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(shape = VaultShapes.sm, color = colors.inset, modifier = Modifier.size(28.dp)) {
            Icon(icon, null, modifier = Modifier.padding(6.dp), tint = if (enabled) colors.accent else colors.textMuted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = FrozenSettingsTypography.rowTitle, color = if (enabled) colors.text else colors.textMuted)
            subtitle?.let { Text(it, style = FrozenSettingsTypography.rowSubtitle, color = colors.textSecondary, maxLines = 2) }
        }
        value?.let {
            Text(it, style = FrozenSettingsTypography.rowValue, color = if (enabled) colors.textSecondary else colors.textMuted, maxLines = 1)
        }
        if (switchValue != null && onSwitch != null) {
            Switch(
                checked = switchValue,
                onCheckedChange = if (enabled) onSwitch else null,
                modifier = Modifier.scale(0.78f),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent),
            )
        } else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
        }
    }
}

private object FrozenSettingsTypography {
    val pageTitle = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val rowTitle = TextStyle(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
    val rowSubtitle = TextStyle(fontSize = 9.8.sp, lineHeight = 12.sp, fontWeight = FontWeight.Normal)
    val rowValue = TextStyle(fontSize = 10.8.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
    val sectionLabel = TextStyle(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun FrozenDeletedRow(item: DeletedItemUiState, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen), color = colors.surface, shape = VaultShapes.md, border = BorderStroke(1.dp, colors.border)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (item.kind == "Folder") Icons.Rounded.FolderDelete else Icons.Rounded.Description, null, modifier = Modifier.size(18.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 2); Text(item.kind, style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
            TextButton(onClick = onRestore) { Text("Restore") }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FrozenEmptyState(message: String) {
    Text(message, modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 36.dp), style = MaterialTheme.typography.bodyMedium, color = VaultThemeTokens.colors.textSecondary)
}

@Composable
private fun FrozenChoiceDialog(title: String, options: List<SettingsChoice>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) = SettingsChoiceDialog(title, options, selected, onDismiss, onSelect)

@Composable
private fun FrozenConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, showCancel: Boolean = true) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } }, dismissButton = if (showCancel) ({ TextButton(onClick = onDismiss) { Text("Cancel") } }) else null)
}

private fun VaultThemeMode.displayName(): String = when (this) {
    VaultThemeMode.Light -> "Light"
    VaultThemeMode.Dark -> "Dark"
    VaultThemeMode.Oled -> "OLED"
    VaultThemeMode.FollowSystemDark -> "Follow system + Dark"
    VaultThemeMode.FollowSystemOled -> "Follow system + OLED"
}

@Composable
private fun AzureSpeechSettingsDialog(
    settings: AzureSpeechSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var region by remember(settings.region) { mutableStateOf(settings.region) }
    var voice by remember(settings.voice) { mutableStateOf(settings.voice) }
    var arabicVoice by remember(settings.arabicVoice) { mutableStateOf(settings.arabicVoice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Azure Speech") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Azure Speech API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Azure Region") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("ENGLISH VOICE", style = MaterialTheme.typography.labelSmall, color = VaultThemeTokens.colors.accent)
                AzureNarrationConfig.EnglishVoiceOptions.forEach { option ->
                    SettingsRow(
                        icon = if (option == voice) Icons.Rounded.VolumeUp else Icons.Rounded.Visibility,
                        label = option,
                        value = if (option == voice) "Selected" else "",
                        onClick = { voice = option },
                    )
                }
                Text("ARABIC VOICE", style = MaterialTheme.typography.labelSmall, color = VaultThemeTokens.colors.accent)
                AzureNarrationConfig.ArabicVoiceOptions.forEach { option ->
                    SettingsRow(
                        icon = if (option == arabicVoice) Icons.Rounded.VolumeUp else Icons.Rounded.Visibility,
                        label = option,
                        value = if (option == arabicVoice) "Selected" else "",
                        onClick = { arabicVoice = option },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey, region, voice, arabicVoice) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChatGptFormattingLoginDialog(
    currentEmail: String,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    var email by remember(currentEmail) { mutableStateOf(currentEmail) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ChatGPT formatting login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This only reconnects ChatGPT note formatting through Supabase. Google Drive backup stays separate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultThemeTokens.colors.textSecondary,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentEmail.isNotBlank()) {
                    Text(
                        text = "Currently connected: $currentEmail",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultThemeTokens.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onLogin(email, password) }) {
                Text("Login")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentEmail.isNotBlank()) {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun DriveRestoreProgressCard(
    state: DriveRestoreState,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val presentation = state.toDriveProgressPresentation()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (presentation.failed) MaterialTheme.colorScheme.error else colors.text,
                )
                presentation.percent?.let {
                    Text(
                        text = "$it%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            when {
                presentation.failed -> Unit
                presentation.percent != null -> LinearProgressIndicator(
                    progress = { presentation.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.border,
                )
                state.active -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
            Text(
                text = presentation.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (presentation.failed) MaterialTheme.colorScheme.error else colors.textSecondary,
            )
            presentation.itemProgress?.let { itemProgress ->
                Text(
                    text = itemProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

private sealed class DeletedTarget(open val id: String, open val title: String) {
    data class Note(override val id: String, override val title: String) : DeletedTarget(id, title)
    data class Folder(override val id: String, override val title: String) : DeletedTarget(id, title)
}

private data class SettingsChoice(
    val value: String,
    val label: String,
)

private data class SettingsLongChoice(
    val value: Long,
    val label: String,
)

@Composable
private fun SettingsChoiceDialog(
    title: String,
    options: List<SettingsChoice>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                options.forEach { option ->
                    SettingsRow(
                        icon = if (option.value == selectedValue) Icons.Rounded.Palette else Icons.Rounded.Visibility,
                        label = option.label,
                        value = if (option.value == selectedValue) "Selected" else "",
                        onClick = { onSelect(option.value) },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

private fun String.displayPreference(): String =
    when (this) {
        "medium_large" -> "Medium-Large"
        else -> replaceFirstChar { it.uppercase() }
    }

private fun String.displayNotePreview(): String = when (this) {
    "one" -> "1 line"
    "two" -> "2 lines"
    else -> "Off"
}

@Composable
private fun SettingsLongChoiceDialog(
    title: String,
    options: List<SettingsLongChoice>,
    selectedValue: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                options.forEach { option ->
                    SettingsRow(
                        icon = if (option.value == selectedValue) Icons.Rounded.Timer else Icons.Rounded.Visibility,
                        label = option.label,
                        value = if (option.value == selectedValue) "Selected" else "",
                        onClick = { onSelect(option.value) },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

private fun Long.displayLockTimeout(): String =
    when (this) {
        30_000L -> "30 seconds"
        60_000L -> "1 minute"
        5 * 60_000L -> "5 minutes"
        30 * 60_000L -> "30 minutes"
        60 * 60_000L -> "1 hour"
        else -> "${this / 1_000} seconds"
    }

private fun Long.displayBackupTime(): String =
    if (this <= 0L) {
        "Never"
    } else {
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(this))
    }

private fun VaultUserPreferences.backupSummary(): String =
    backupReminderText() ?: if (googleDriveAccountEmail.isNotBlank()) "Google Drive ready" else "Manual backup"

private fun VaultUserPreferences.backupReminderText(now: Long = System.currentTimeMillis()): String? {
    val mostRecent = maxOf(lastLocalBackupAt, lastGoogleDriveSyncAt)
    if (mostRecent <= 0L) return "No backup yet"
    val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
    return if (now - mostRecent > sevenDaysMs) "Backup recommended" else null
}
