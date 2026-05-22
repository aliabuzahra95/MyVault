package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun MemoriseShellScreen(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding(),
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
            IconBtn(Icons.Rounded.WbSunny, "Toggle theme", active = true, onClick = onThemeClick)
            IconBtn(Icons.Rounded.Backup, "Quick cloud backup", active = quickBackupRecommended, onClick = onQuickBackupClick)
            IconBtn(Icons.Rounded.Settings, "Settings", onClick = onSettingsClick)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp)
                .padding(top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Memorise",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Qur'anic Threads memorisation flow will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.accentBorder, RoundedCornerShape(14.dp))
                            .background(colors.accentSoft, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.accent,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Memorisation shell ready",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = "Next pass can safely port Surah selection, conceal/reveal, repeat modes, weak ayah marking, and progress tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
