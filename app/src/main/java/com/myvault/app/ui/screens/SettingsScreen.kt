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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.myvault.app.data.sync.DriveRestoreState
import com.myvault.app.data.sync.DriveRestoreStage
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
    onRestoreDeletedNote: (String) -> Unit = {},
    onPermanentlyDeleteNote: (String) -> Unit = {},
    onRestoreDeletedFolder: (String) -> Unit = {},
    onPermanentlyDeleteFolder: (String) -> Unit = {},
    onPermanentlyDeleteAllDeleted: () -> Unit = {},
    googleDriveSignInIntent: Intent? = null,
    onGoogleDriveSignInResult: (Intent?) -> Unit = {},
    onGoogleDrivePush: () -> Unit = {},
    onGoogleDrivePull: () -> Unit = {},
    supabaseAiEmail: String = "",
    onSupabaseAiLogin: (String, String) -> Unit = { _, _ -> },
    onSupabaseAiLogout: () -> Unit = {},
    driveRestoreState: DriveRestoreState = DriveRestoreState(),
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
    var backupSettingsOpen by remember { mutableStateOf(false) }
    var aiLoginOpen by remember { mutableStateOf(false) }
    var driveRestoreConfirmOpen by remember { mutableStateOf(false) }
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
                    onSecurityLockClick = {
                        onSecurityLockChanged(!preferences.securityLockEnabled)
                    },
                    onBackupSettingsClick = { backupSettingsOpen = true },
                    onAiLoginClick = { aiLoginOpen = true },
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
            onDismiss = { backupSettingsOpen = false },
            onBackupClick = {
                backupSettingsOpen = false
                backupLauncher.launch("my-vault-${System.currentTimeMillis()}.vaultbackup")
            },
            onRestoreClick = {
                backupSettingsOpen = false
                restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
            },
            onGoogleDriveConnectClick = {
                googleDriveSignInIntent?.let { googleDriveSignInLauncher.launch(it) }
            },
            onGoogleDrivePushClick = onGoogleDrivePush,
            onGoogleDrivePullClick = { driveRestoreConfirmOpen = true },
            driveRestoreState = driveRestoreState,
        )
    }

    if (aiLoginOpen) {
        ChatGptAiLoginDialog(
            currentEmail = supabaseAiEmail,
            onDismiss = { aiLoginOpen = false },
            onLogin = { email, password ->
                aiLoginOpen = false
                onSupabaseAiLogin(email, password)
            },
            onLogout = {
                aiLoginOpen = false
                onSupabaseAiLogout()
            },
        )
    }

    if (driveRestoreConfirmOpen) {
        AlertDialog(
            onDismissRequest = { driveRestoreConfirmOpen = false },
            title = { Text("Restore from Drive?") },
            text = {
                Text(
                    "This will pull the latest MyVault backup from Google Drive and apply it to this device. A local emergency backup is created before restore, but you should only continue when this device is ready to receive the Drive version.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        driveRestoreConfirmOpen = false
                        onGoogleDrivePull()
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { driveRestoreConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
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
    onSecurityLockClick: () -> Unit,
    onBackupSettingsClick: () -> Unit,
    onAiLoginClick: () -> Unit,
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
            preferences.backupSummary(),
            onClick = onBackupSettingsClick,
        )
        SettingsRow(Icons.Rounded.Psychology, "ChatGPT AI login", "Supabase account", onClick = onAiLoginClick)
        SettingsRow(Icons.Rounded.RestoreFromTrash, "Recently Deleted", "$recentlyDeletedCount item${if (recentlyDeletedCount == 1) "" else "s"}", onClick = onRecentlyDeletedClick)
        SettingsRow(Icons.Rounded.Lock, "Security lock", if (preferences.securityLockEnabled) "On" else "Off", onClick = onSecurityLockClick)
        SettingsRow(Icons.Rounded.Timer, "Auto-lock timer", preferences.securityLockTimeoutMs.displayLockTimeout(), onClick = onLockTimerClick)
        SettingsRow(Icons.Rounded.Verified, "Release readiness", "Checklist", onClick = onReleaseReadinessClick)
        SettingsRow(Icons.Rounded.Storage, "Storage", storageLabel)
    }
}

@Composable
private fun ChatGptAiLoginDialog(
    currentEmail: String,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    var email by remember(currentEmail) { mutableStateOf(currentEmail) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ChatGPT AI login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This only reconnects ChatGPT AI through Supabase. Google Drive backup stays separate.",
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
private fun BackupSettingsDialog(
    preferences: VaultUserPreferences,
    onDismiss: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onGoogleDriveConnectClick: () -> Unit,
    onGoogleDrivePushClick: () -> Unit,
    onGoogleDrivePullClick: () -> Unit,
    driveRestoreState: DriveRestoreState,
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
                SettingsRow(Icons.Rounded.Restore, "Restore vault", "Import file", onClick = onRestoreClick)
                val driveConnected = preferences.googleDriveAccountEmail.isNotBlank()
                SettingsRow(Icons.Rounded.Storage, "Login", if (driveConnected) preferences.googleDriveAccountEmail else "Connect Google Drive", onClick = onGoogleDriveConnectClick)
                BackupHealthCard(preferences = preferences, driveRestoreState = driveRestoreState)
                SettingsRow(Icons.Rounded.Backup, "Push to Drive", if (driveConnected) "Incremental upload" else "Connect Drive first", onClick = onGoogleDrivePushClick)
                SettingsRow(Icons.Rounded.Restore, "Restore from Drive", if (driveConnected) "Pull latest vault" else "Connect Drive first", onClick = onGoogleDrivePullClick)
                if (driveRestoreState.active || driveRestoreState.isFinished) {
                    DriveRestoreProgressCard(driveRestoreState)
                }
                SettingsRow(Icons.Rounded.Storage, "Last Drive update", preferences.lastGoogleDriveSyncAt.displayBackupTime())
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
private fun DriveRestoreProgressCard(state: DriveRestoreState) {
    val colors = VaultThemeTokens.colors
    val progress = state.progress
    Surface(
        modifier = Modifier
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
                    text = if (progress.stage == DriveRestoreStage.Complete) "Drive sync complete" else progress.stage.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                )
                progress.percent?.let {
                    Text(
                        text = "$it%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress.percent?.div(100f) ?: if (state.active) 0.35f else 1f },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.border,
            )
            Text(
                text = progress.message.ifBlank { state.message.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (progress.total > 0) {
                Text(
                    text = "${progress.current.coerceAtMost(progress.total)} of ${progress.total} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ReleaseReadinessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Release readiness") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                Text("Before installing as your main notes app:", style = MaterialTheme.typography.bodyMedium)
                Text("• Export one manual backup file", style = MaterialTheme.typography.bodySmall)
                Text("• Push one Google Drive backup", style = MaterialTheme.typography.bodySmall)
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

private fun VaultUserPreferences.backupSummary(): String =
    backupReminderText() ?: if (googleDriveAccountEmail.isNotBlank()) "Google Drive ready" else "Manual backup"

@Composable
private fun BackupHealthCard(
    preferences: VaultUserPreferences,
    driveRestoreState: DriveRestoreState,
) {
    val colors = VaultThemeTokens.colors
    val driveConnected = preferences.googleDriveAccountEmail.isNotBlank()
    val lastBackup = maxOf(preferences.lastLocalBackupAt, preferences.lastGoogleDriveSyncAt)
    val status = when {
        driveRestoreState.active -> "Running"
        lastBackup <= 0L -> "No backup yet"
        System.currentTimeMillis() - lastBackup > 7L * 24L * 60L * 60L * 1000L -> "Backup due"
        else -> "Protected"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.HealthAndSafety, null, modifier = Modifier.size(18.dp), tint = colors.accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Backup health", style = MaterialTheme.typography.labelLarge, color = colors.text)
                Text(
                    "Notes, files, PDF annotations, Qur'an state, memorisation, settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            Text(
                if (driveConnected) status else "Drive off",
                style = MaterialTheme.typography.labelMedium,
                color = if (driveConnected && status == "Protected") colors.accent else colors.textSecondary,
            )
        }
    }
}

private fun VaultUserPreferences.backupReminderText(now: Long = System.currentTimeMillis()): String? {
    val mostRecent = maxOf(lastLocalBackupAt, lastGoogleDriveSyncAt)
    if (mostRecent <= 0L) return "No backup yet"
    val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
    return if (now - mostRecent > sevenDaysMs) "Backup recommended" else null
}
