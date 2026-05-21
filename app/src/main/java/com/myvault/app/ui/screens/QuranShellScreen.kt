package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun QuranShellScreen(
    workspaceTitle: String,
    workspaceOptions: List<String>,
    onWorkspaceSelected: (String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 118.dp),
        ) {
            VaultTopBar(
                title = workspaceTitle,
                titleContent = {
                    VaultWorkspaceSwitcher(
                        selectedLabel = workspaceTitle,
                        options = workspaceOptions,
                        onSelected = onWorkspaceSelected,
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
            }

            Spacer(Modifier.height(VaultSpacing.lg))

            Surface(
                modifier = Modifier
                    .padding(horizontal = VaultSpacing.screen)
                    .fillMaxWidth(),
                shape = VaultShapes.lg,
                color = colors.surface,
                contentColor = colors.text,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = VaultShapes.md,
                            color = colors.accentSoft,
                            border = BorderStroke(1.dp, colors.accentBorder),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(21.dp),
                                    tint = colors.accent,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Qur'an Reader",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                                color = colors.text,
                            )
                            Text(
                                text = "Foundation shell",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    Text(
                        text = "The reader will live here inside Islamic Corpus after the Qur'anic Threads migration is planned and moved in controlled passes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}
