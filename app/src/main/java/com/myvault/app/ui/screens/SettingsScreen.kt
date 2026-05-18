package com.myvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.SettingsRow
import com.myvault.app.ui.components.ThemePreviewCard
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.ui.viewmodel.CloudBackupUiState
import com.myvault.app.ui.viewmodel.DeletedItemUiState
import com.myvault.app.ui.viewmodel.RecentlyDeletedUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    preferences: VaultUserPreferences,
    onBackClick: () -> Unit,
    onThemeSelected: (VaultThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    onAccentColorSelected: (String) -> Unit = {},
    onBackupSelected: (Uri) -> Unit = {},
    onRestoreSelected: (Uri) -> Unit = {},
    onDashboardFontSizeSelected: (String) -> Unit = {},
    onNoteFontSizeSelected: (String) -> Unit = {},
    onNotePreviewSelected: (String) -> Unit = {},
    onDefaultNoteViewSelected: (String) -> Unit = {},
    onSecurityLockChanged: (Boolean) -> Unit = {},
    onSecurityLockTimeoutSelected: (Long) -> Unit = {},
    storageLabel: String = "Calculating...",
    recentlyDeleted: RecentlyDeletedUiState = RecentlyDeletedUiState(),
    cloudBackup: CloudBackupUiState = CloudBackupUiState(),
    onRestoreDeletedNote: (String) -> Unit = {},
    onPermanentlyDeleteNote: (String) -> Unit = {},
    onRestoreDeletedFolder: (String) -> Unit = {},
    onPermanentlyDeleteFolder: (String) -> Unit = {},
    onPermanentlyDeleteAllDeleted: () -> Unit = {},
    onCloudSignUp: (email: String, password: String) -> Unit = { _, _ -> },
    onCloudSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    onCloudSignOut: () -> Unit = {},
    onCloudBackup: () -> Unit = {},
    onCloudRestore: () -> Unit = {},
    googleDriveSignInIntent: Intent? = null,
    onGoogleDriveSignInResult: (Intent?) -> Unit = {},
    onGoogleDrivePush: () -> Unit = {},
    onGoogleDrivePull: () -> Unit = {},
    onGoogleDriveCheck: () -> Unit = {},
    onVerifyBackup: () -> Unit = {},
    backupMessage: String? = null,
    onDismissBackupMessage: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    var restoreConfirmUri by remember { mutableStateOf<Uri?>(null) }
    var dashboardFontSizeDialogOpen by remember { mutableStateOf(false) }
    var noteFontSizeDialogOpen by remember { mutableStateOf(false) }
    var notePreviewDialogOpen by remember { mutableStateOf(false) }
    var defaultViewDialogOpen by remember { mutableStateOf(false) }
    var lockTimerDialogOpen by remember { mutableStateOf(false) }
    var recentlyDeletedOpen by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<DeletedTarget?>(null) }
    var deleteAllDeletedConfirmOpen by remember { mutableStateOf(false) }
    var cloudSignInOpen by remember { mutableStateOf(false) }
    var cloudRestoreConfirmOpen by remember { mutableStateOf(false) }
    var backupSettingsOpen by remember { mutableStateOf(false) }
    var releaseReadinessOpen by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(onBackupSelected)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreConfirmUri = uri
    }
    val googleDriveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onGoogleDriveSignInResult(result.data)
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.bg) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = VaultSpacing.huge),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            item { ScreenTopBar(onBackClick = onBackClick) }
            item {
                Text(
                    "Settings",
                    modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.text,
                )
            }
            item {
                AppearanceCard(
                    preferences = preferences,
                    onThemeSelected = onThemeSelected,
                    onAccentColorSelected = onAccentColorSelected,
                )
            }
            item {
                SettingsGroupEditor(
                    preferences = preferences,
                    onDashboardFontSizeClick = { dashboardFontSizeDialogOpen = true },
                    onNoteFontSizeClick = { noteFontSizeDialogOpen = true },
                    onNotePreviewClick = { notePreviewDialogOpen = true },
                    onDefaultViewClick = { defaultViewDialogOpen = true },
                )
            }
            item {
                SettingsGroupVault(
                    preferences = preferences,
                    storageLabel = storageLabel,
                    cloudBackup = cloudBackup,
                    onSecurityLockClick = {
                        onSecurityLockChanged(!preferences.securityLockEnabled)
                    },
                    onBackupSettingsClick = { backupSettingsOpen = true },
                    onLockTimerClick = { lockTimerDialogOpen = true },
                    onRecentlyDeletedClick = { recentlyDeletedOpen = true },
                    onReleaseReadinessClick = { releaseReadinessOpen = true },
                    recentlyDeletedCount = recentlyDeleted.notes.size + recentlyDeleted.folders.size,
                )
            }
        }
    }

    if (backupSettingsOpen) {
        BackupSettingsDialog(
            preferences = preferences,
            cloudBackup = cloudBackup,
            onDismiss = { backupSettingsOpen = false },
            onBackupClick = {
                backupSettingsOpen = false
                backupLauncher.launch("my-vault-${System.currentTimeMillis()}.vaultbackup")
            },
            onRestoreClick = {
                backupSettingsOpen = false
                restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
            },
            onCloudAccountClick = {
                if (cloudBackup.signedIn) {
                    onCloudSignOut()
                } else {
                    cloudSignInOpen = true
                }
            },
            onCloudBackupClick = onCloudBackup,
            onCloudRestoreClick = { cloudRestoreConfirmOpen = true },
                    onGoogleDriveConnectClick = {
                        googleDriveSignInIntent?.let { googleDriveSignInLauncher.launch(it) }
                    },
            onGoogleDrivePushClick = onGoogleDrivePush,
            onGoogleDrivePullClick = onGoogleDrivePull,
            onGoogleDriveCheckClick = onGoogleDriveCheck,
            onVerifyBackupClick = onVerifyBackup,
        )
    }

    if (releaseReadinessOpen) {
        ReleaseReadinessDialog(onDismiss = { releaseReadinessOpen = false })
    }

    if (dashboardFontSizeDialogOpen) {
        SettingsChoiceDialog(
            title = "Dashboard font size",
            options = listOf(
                SettingsChoice("small", "Small"),
                SettingsChoice("medium", "Medium"),
                SettingsChoice("large", "Large"),
            ),
            selectedValue = preferences.dashboardFontSize,
            onDismiss = { dashboardFontSizeDialogOpen = false },
            onSelect = {
                onDashboardFontSizeSelected(it)
                dashboardFontSizeDialogOpen = false
            },
        )
    }

    if (noteFontSizeDialogOpen) {
        SettingsChoiceDialog(
            title = "Note editor font size",
            options = listOf(
                SettingsChoice("small", "Small"),
                SettingsChoice("medium", "Medium"),
                SettingsChoice("large", "Large"),
            ),
            selectedValue = preferences.noteFontSize,
            onDismiss = { noteFontSizeDialogOpen = false },
            onSelect = {
                onNoteFontSizeSelected(it)
                noteFontSizeDialogOpen = false
            },
        )
    }

    if (defaultViewDialogOpen) {
        SettingsChoiceDialog(
            title = "Default note view",
            options = listOf(
                SettingsChoice("reading", "Reading"),
                SettingsChoice("editing", "Editing"),
            ),
            selectedValue = preferences.defaultNoteView,
            onDismiss = { defaultViewDialogOpen = false },
            onSelect = {
                onDefaultNoteViewSelected(it)
                defaultViewDialogOpen = false
            },
        )
    }

    if (notePreviewDialogOpen) {
        SettingsChoiceDialog(
            title = "Note Preview",
            options = listOf(
                SettingsChoice("off", "Off"),
                SettingsChoice("one", "1 line"),
                SettingsChoice("two", "2 lines"),
            ),
            selectedValue = preferences.notePreview,
            onDismiss = { notePreviewDialogOpen = false },
            onSelect = {
                onNotePreviewSelected(it)
                notePreviewDialogOpen = false
            },
        )
    }

    if (lockTimerDialogOpen) {
        SettingsLongChoiceDialog(
            title = "Auto-lock timer",
            options = listOf(
                SettingsLongChoice(30_000L, "30 seconds"),
                SettingsLongChoice(60_000L, "1 minute"),
                SettingsLongChoice(5 * 60_000L, "5 minutes"),
                SettingsLongChoice(30 * 60_000L, "30 minutes"),
                SettingsLongChoice(60 * 60_000L, "1 hour"),
            ),
            selectedValue = preferences.securityLockTimeoutMs,
            onDismiss = { lockTimerDialogOpen = false },
            onSelect = {
                onSecurityLockTimeoutSelected(it)
                lockTimerDialogOpen = false
            },
        )
    }

    restoreConfirmUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreConfirmUri = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This will merge the selected backup into your current vault. It will not clear your vault first, but matching notes, folders, tables, and attachments can be updated by the backup. Only continue if you trust this file.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreConfirmUri = null
                        onRestoreSelected(uri)
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmUri = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (recentlyDeletedOpen) {
        RecentlyDeletedDialog(
            state = recentlyDeleted,
            onDismiss = { recentlyDeletedOpen = false },
            onRestoreNote = onRestoreDeletedNote,
            onRestoreFolder = onRestoreDeletedFolder,
            onDeleteNote = { permanentDeleteTarget = DeletedTarget.Note(it.id, it.title) },
            onDeleteFolder = { permanentDeleteTarget = DeletedTarget.Folder(it.id, it.title) },
            onDeleteAll = { deleteAllDeletedConfirmOpen = true },
        )
    }

    if (deleteAllDeletedConfirmOpen) {
        val count = recentlyDeleted.notes.size + recentlyDeleted.folders.size
        AlertDialog(
            onDismissRequest = { deleteAllDeletedConfirmOpen = false },
            title = { Text("Delete all forever?") },
            text = { Text("$count item${if (count == 1) "" else "s"} in Recently Deleted will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentlyDeleteAllDeleted()
                        deleteAllDeletedConfirmOpen = false
                        recentlyDeletedOpen = false
                    },
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllDeletedConfirmOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }

    if (cloudSignInOpen) {
        CloudSignInDialog(
            onDismiss = { cloudSignInOpen = false },
            onSignIn = { email, password ->
                cloudSignInOpen = false
                onCloudSignIn(email, password)
            },
            onSignUp = { email, password ->
                cloudSignInOpen = false
                onCloudSignUp(email, password)
            },
        )
    }

    if (cloudRestoreConfirmOpen) {
        AlertDialog(
            onDismissRequest = { cloudRestoreConfirmOpen = false },
            title = { Text("Restore cloud backup?") },
            text = {
                Text(
                    "This will merge the latest Supabase backup into your current vault. It will not clear your vault first, but matching notes, folders, tables, and attachments can be updated by the backup.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cloudRestoreConfirmOpen = false
                        onCloudRestore()
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { cloudRestoreConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    permanentDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("Delete forever?") },
            text = { Text("${target.title} will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            is DeletedTarget.Note -> onPermanentlyDeleteNote(target.id)
                            is DeletedTarget.Folder -> onPermanentlyDeleteFolder(target.id)
                        }
                        permanentDeleteTarget = null
                    },
                ) {
                    Text("Delete forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) {
                    Text("Keep")
                }
            },
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissBackupMessage,
            title = { Text("Vault backup") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissBackupMessage) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun AppearanceCard(
    preferences: VaultUserPreferences,
    onThemeSelected: (VaultThemeMode) -> Unit,
    onAccentColorSelected: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val accentOptions = listOf(
        "#5B8DEF" to Color(0xFF5B8DEF),
        "#3F8C5C" to Color(0xFF3F8C5C),
        "#D4A24C" to Color(0xFFD4A24C),
        "#9B6BE8" to Color(0xFF9B6BE8),
        "#E06F5F" to Color(0xFFE06F5F),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.elevated,
        shape = VaultShapes.xl,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(VaultSpacing.md), verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
            Text("APPEARANCE", style = MaterialTheme.typography.labelSmall, color = colors.accent)
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                ThemePreviewCard("Light", "light", selected = preferences.theme == VaultThemeMode.Light, onClick = { onThemeSelected(VaultThemeMode.Light) })
                ThemePreviewCard("Dark", "dark", selected = preferences.theme == VaultThemeMode.Dark, onClick = { onThemeSelected(VaultThemeMode.Dark) })
                ThemePreviewCard("Auto", "auto", selected = preferences.theme == VaultThemeMode.Auto, onClick = { onThemeSelected(VaultThemeMode.Auto) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                accentOptions.forEach { (hex, value) ->
                    val selected = preferences.accentColor.equals(hex, ignoreCase = true)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(value, VaultShapes.sm)
                            .border(if (selected) 2.dp else 1.dp, if (selected) colors.text else colors.border, VaultShapes.sm)
                            .clickable { onAccentColorSelected(hex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupEditor(
    preferences: VaultUserPreferences,
    onDashboardFontSizeClick: () -> Unit,
    onNoteFontSizeClick: () -> Unit,
    onNotePreviewClick: () -> Unit,
    onDefaultViewClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        SettingsRow(Icons.Rounded.TextFields, "Dashboard font size", preferences.dashboardFontSize.displayPreference(), onClick = onDashboardFontSizeClick)
        SettingsRow(Icons.Rounded.TextFields, "Note editor font size", preferences.noteFontSize.displayPreference(), onClick = onNoteFontSizeClick)
        SettingsRow(Icons.Rounded.Visibility, "Note Preview", preferences.notePreview.displayNotePreview(), onClick = onNotePreviewClick)
        SettingsRow(Icons.Rounded.Visibility, "Default note view", preferences.defaultNoteView.displayPreference(), onClick = onDefaultViewClick)
    }
}

@Composable
private fun SettingsGroupVault(
    preferences: VaultUserPreferences,
    storageLabel: String,
    cloudBackup: CloudBackupUiState,
    onSecurityLockClick: () -> Unit,
    onBackupSettingsClick: () -> Unit,
    onLockTimerClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onReleaseReadinessClick: () -> Unit,
    recentlyDeletedCount: Int,
) {
    Column(
        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        SettingsRow(
            Icons.Rounded.Backup,
            "Backup & restore",
            preferences.backupSummary(cloudBackup),
            onClick = onBackupSettingsClick,
        )
        SettingsRow(Icons.Rounded.RestoreFromTrash, "Recently Deleted", "$recentlyDeletedCount item${if (recentlyDeletedCount == 1) "" else "s"}", onClick = onRecentlyDeletedClick)
        SettingsRow(Icons.Rounded.Lock, "Security lock", if (preferences.securityLockEnabled) "On" else "Off", onClick = onSecurityLockClick)
        SettingsRow(Icons.Rounded.Timer, "Auto-lock timer", preferences.securityLockTimeoutMs.displayLockTimeout(), onClick = onLockTimerClick)
        SettingsRow(Icons.Rounded.Verified, "Release readiness", "Checklist", onClick = onReleaseReadinessClick)
        SettingsRow(Icons.Rounded.Storage, "Storage", storageLabel)
    }
}

@Composable
private fun BackupSettingsDialog(
    preferences: VaultUserPreferences,
    cloudBackup: CloudBackupUiState,
    onDismiss: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onCloudAccountClick: () -> Unit,
    onCloudBackupClick: () -> Unit,
    onCloudRestoreClick: () -> Unit,
    onGoogleDriveConnectClick: () -> Unit,
    onGoogleDrivePushClick: () -> Unit,
    onGoogleDrivePullClick: () -> Unit,
    onGoogleDriveCheckClick: () -> Unit,
    onVerifyBackupClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup & restore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val reminder = preferences.backupReminderText()
                if (reminder != null) {
                    Surface(
                        color = VaultThemeTokens.colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, VaultThemeTokens.colors.accentBorder),
                    ) {
                        Text(
                            text = reminder,
                            modifier = Modifier.padding(VaultSpacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultThemeTokens.colors.text,
                        )
                    }
                }
                SettingsRow(Icons.Rounded.Backup, "Backup vault", "Export file", onClick = onBackupClick)
                SettingsRow(Icons.Rounded.Verified, "Check backup safety", "Run check", onClick = onVerifyBackupClick)
                SettingsRow(Icons.Rounded.Restore, "Restore vault", "Import file", onClick = onRestoreClick)
                SettingsRow(Icons.Rounded.Storage, "Supabase account", cloudBackup.statusLabel, onClick = onCloudAccountClick)
                SettingsRow(Icons.Rounded.Backup, "Cloud backup", if (cloudBackup.signedIn) "Upload now" else "Sign in first", onClick = onCloudBackupClick)
                SettingsRow(Icons.Rounded.Restore, "Cloud restore", if (cloudBackup.signedIn) "Download latest" else "Sign in first", onClick = onCloudRestoreClick)
                val driveConnected = preferences.googleDriveAccountEmail.isNotBlank()
                SettingsRow(Icons.Rounded.Storage, "Google Drive account", if (driveConnected) preferences.googleDriveAccountEmail else "Connect account", onClick = onGoogleDriveConnectClick)
                SettingsRow(Icons.Rounded.Verified, "Check Drive updates", if (driveConnected) "Compare manifest" else "Connect Drive first", onClick = onGoogleDriveCheckClick)
                SettingsRow(Icons.Rounded.Backup, "Push to Drive", if (driveConnected) "Incremental upload" else "Connect Drive first", onClick = onGoogleDrivePushClick)
                SettingsRow(Icons.Rounded.Restore, "Pull latest from Drive", if (driveConnected) "Incremental download" else "Connect Drive first", onClick = onGoogleDrivePullClick)
                SettingsRow(Icons.Rounded.Backup, "Last local backup", preferences.lastLocalBackupAt.displayBackupTime())
                SettingsRow(Icons.Rounded.Storage, "Last cloud backup", preferences.lastCloudBackupAt.displayBackupTime())
                SettingsRow(Icons.Rounded.Storage, "Last Drive sync", preferences.lastGoogleDriveSyncAt.displayBackupTime())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun ReleaseReadinessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Release readiness") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                Text("Before installing as your main notes app:", style = MaterialTheme.typography.bodyMedium)
                Text("• Run Check backup safety", style = MaterialTheme.typography.bodySmall)
                Text("• Export one manual backup file", style = MaterialTheme.typography.bodySmall)
                Text("• Upload one Supabase cloud backup", style = MaterialTheme.typography.bodySmall)
                Text("• Restore a backup on a test install", style = MaterialTheme.typography.bodySmall)
                Text("• Confirm the launcher icon and app name on your phone", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun RecentlyDeletedDialog(
    state: RecentlyDeletedUiState,
    onDismiss: () -> Unit,
    onRestoreNote: (String) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onDeleteNote: (DeletedItemUiState) -> Unit,
    onDeleteFolder: (DeletedItemUiState) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val allItems = state.folders + state.notes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recently Deleted") },
        text = {
            if (allItems.isEmpty()) {
                Text("Nothing is in Recently Deleted.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                ) {
                    allItems.sortedByDescending { it.deletedAt }.forEach { item ->
                        DeletedItemRow(
                            item = item,
                            onRestore = {
                                if (item.kind == "Note") onRestoreNote(item.id) else onRestoreFolder(item.id)
                            },
                            onDelete = {
                                if (item.kind == "Note") onDeleteNote(item) else onDeleteFolder(item)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (allItems.isNotEmpty()) {
                TextButton(onClick = onDeleteAll) {
                    Text("Delete all")
                }
            }
        },
    )
}

@Composable
private fun CloudSignInDialog(
    onDismiss: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.length >= 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supabase account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
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
                Text(
                    text = "Use the account you want this vault backup stored under.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultThemeTokens.colors.textMuted,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSignIn(email, password) },
            ) {
                Text("Sign in")
            }
        },
        dismissButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onSignUp(email, password) },
            ) {
                Text("Create account")
            }
        },
    )
}

@Composable
private fun DeletedItemRow(
    item: DeletedItemUiState,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VaultThemeTokens.colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, VaultThemeTokens.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(VaultSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = VaultThemeTokens.colors.text)
            Text(item.kind, style = MaterialTheme.typography.labelMedium, color = VaultThemeTokens.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                TextButton(onClick = onRestore) {
                    Text("Restore")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete forever")
                }
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
    replaceFirstChar { it.uppercase() }

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

private fun VaultUserPreferences.backupSummary(cloudBackup: CloudBackupUiState): String =
    backupReminderText() ?: if (cloudBackup.signedIn) "Cloud ready" else "Manual backup"

private fun VaultUserPreferences.backupReminderText(now: Long = System.currentTimeMillis()): String? {
    val mostRecent = maxOf(lastLocalBackupAt, lastCloudBackupAt)
    if (mostRecent <= 0L) return "No backup yet"
    val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
    return if (now - mostRecent > sevenDaysMs) "Backup recommended" else null
}
