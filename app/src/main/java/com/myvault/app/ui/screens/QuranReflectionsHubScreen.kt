package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.QuranReflectionsUiState

@Composable
fun QuranReflectionsHubScreen(
    uiState: QuranReflectionsUiState,
    onBackClick: () -> Unit,
    onReflectionClick: (QuranReflectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = VaultSpacing.huge),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.text)
                    }
                    Column {
                        Text(
                            text = "Qur'an Reflections",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W900),
                            color = colors.text,
                        )
                        Text(
                            text = "${uiState.reflections.size} saved reflection${if (uiState.reflections.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            if (uiState.reflections.isEmpty()) {
                item {
                    EmptyQuranReflectionsState()
                }
            } else {
                items(uiState.reflections, key = { it.noteId }) { reflection ->
                    QuranReflectionCard(
                        reflection = reflection,
                        onClick = { onReflectionClick(reflection) },
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQuranReflectionsState() {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = "Reflections you save from the Qur'an reader will appear here.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuranReflectionCard(
    reflection: QuranReflectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${reflection.surahName} ${reflection.surahNumber}:${reflection.ayahNumber}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = reflection.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = reflection.updatedAt.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            if (reflection.arabicPreview.isNotBlank()) {
                Text(
                    text = reflection.arabicPreview,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (reflection.translationPreview.isNotBlank()) {
                Text(
                    text = reflection.translationPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = reflection.reflectionPreview.ifBlank { "Open attached reflection" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
