package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.repository.AiPromptBuilder
import com.myvault.app.data.repository.AiSuggestion
import com.myvault.app.data.repository.NoteAiAction
import com.myvault.app.data.repository.NoteAiModel
import com.myvault.app.data.repository.NoteAiProvider
import com.myvault.app.data.repository.displayName
import com.myvault.app.data.repository.toRelativeTime
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.NoteAiChatMessage
import com.myvault.app.ui.viewmodel.NoteAiMessageRole
import com.myvault.app.ui.viewmodel.NoteAiUiState
import com.myvault.app.ui.viewmodel.NoteUiState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    uiState: NoteUiState,
    aiState: NoteAiUiState,
    selectedText: String?,
    onBackClick: () -> Unit,
    onRunAiTool: (action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel, title: String, body: String, question: String) -> Unit,
    onClearAiConversation: () -> Unit,
    onAiProviderSelected: (NoteAiProvider) -> Unit,
    onAiModelSelected: (NoteAiModel) -> Unit,
    onAiQuestionChange: (String) -> Unit,
    onOpenAiConversation: (String) -> Unit,
    onStartNewAiConversation: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val clipboardManager = LocalClipboardManager.current
    val conversationScrollState = rememberScrollState()
    var historyOpen by remember { mutableStateOf(false) }
    val noteTitle = uiState.note?.title?.takeIf { it.isNotBlank() } ?: "Untitled note"
    val noteBody = uiState.richText.text.ifBlank { uiState.note?.bodyPlainText.orEmpty() }
    val hasSelectedText = !selectedText.isNullOrBlank()

    LaunchedEffect(aiState.messages.size, aiState.loading, aiState.error) {
        conversationScrollState.animateScrollTo(conversationScrollState.maxValue)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = aiState.question,
                        onValueChange = onAiQuestionChange,
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                        placeholder = { Text("Ask about this note...") },
                        enabled = !aiState.loading,
                    )
                    TextButton(
                        onClick = { clipboardManager.setText(AnnotatedString(aiState.messages.lastOrNull { it.role == NoteAiMessageRole.Assistant }?.content.orEmpty())) },
                        enabled = aiState.messages.any { it.role == NoteAiMessageRole.Assistant },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = {
                            onRunAiTool(
                                NoteAiAction.Ask,
                                aiState.provider,
                                aiState.model,
                                noteTitle,
                                noteBody,
                                AiPromptBuilder.wrapSelectedTextQuestion(aiState.question, selectedText),
                            )
                        },
                        enabled = !aiState.loading && aiState.question.isNotBlank(),
                    ) {
                        Text("Send")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenTopBar(onBackClick = onBackClick) {
                IconButton(onClick = { historyOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "AI conversation history",
                        tint = colors.text,
                    )
                }
                TextButton(
                    onClick = onClearAiConversation,
                    enabled = !aiState.loading && aiState.messages.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaultSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        color = colors.accentSoft,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.accentBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(22.dp), tint = colors.accent)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = if (hasSelectedText) "Selected text focus" else "Current note companion",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                }

                Text(
                    text = noteTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    ) {
                        NoteAiProvider.entries.forEach { provider ->
                            AskAiChip(
                                label = provider.displayName,
                                active = aiState.provider == provider,
                                enabled = !aiState.loading,
                                onClick = { onAiProviderSelected(provider) },
                            )
                        }
                        NoteAiModel.entries.forEach { model ->
                            AskAiChip(
                                label = model.chipLabel(aiState.provider),
                                active = aiState.model == model,
                                enabled = !aiState.loading,
                                onClick = { onAiModelSelected(model) },
                            )
                        }
                    }

                    selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.elevated,
                            shape = VaultShapes.md,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 92.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }

                    AskAiSuggestionGrid(
                        enabled = !aiState.loading,
                        selectedTextMode = hasSelectedText,
                        onSuggestionClick = { suggestion ->
                            onAiQuestionChange(AiPromptBuilder.buildSuggestionPrefill(suggestion, selectedTextMode = hasSelectedText))
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = VaultSpacing.screen),
                color = colors.surface,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(conversationScrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    if (aiState.messages.isEmpty() && !aiState.loading) {
                        Text(
                            text = "Ask naturally about this note. Suggestions draft the prompt first, then you can edit and send.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                    aiState.messages.forEach { message ->
                        AskAiChatBubble(message = message)
                    }
                    if (aiState.loading) {
                        Surface(
                            color = colors.elevated,
                            shape = VaultShapes.md,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(aiState.progressLabel ?: "Thinking...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                    }
                    aiState.error?.takeIf { error ->
                        aiState.messages.lastOrNull()?.content != error
                    }?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                            color = colors.warning,
                        )
                    }
                }
            }

        }
    }

    if (historyOpen) {
        ModalBottomSheet(
            onDismissRequest = { historyOpen = false },
            containerColor = colors.elevated,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaultSpacing.screen)
                    .padding(bottom = VaultSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Conversation history",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W800),
                            color = colors.text,
                        )
                        Text(
                            text = "Saved for this note",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                    TextButton(
                        onClick = {
                            onStartNewAiConversation()
                            historyOpen = false
                        },
                        enabled = !aiState.loading,
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                        Text("New")
                    }
                }

                if (aiState.conversationHistory.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.lg,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Text(
                            text = "No AI conversations yet.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    ) {
                        aiState.conversationHistory.forEach { conversation ->
                            AiConversationHistoryRow(
                                title = conversation.title,
                                preview = conversation.preview,
                                meta = "${conversation.messageCount} messages · ${conversation.updatedAt.toRelativeTime()}",
                                active = conversation.id == aiState.activeConversationId,
                                onClick = {
                                    onOpenAiConversation(conversation.id)
                                    historyOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiConversationHistoryRow(
    title: String,
    preview: String,
    meta: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (active) colors.accentSoft else colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
            Text(
                text = preview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun AskAiChatBubble(message: NoteAiChatMessage) {
    val colors = VaultThemeTokens.colors
    val isUser = message.role == NoteAiMessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 560.dp),
            color = when (message.role) {
                NoteAiMessageRole.User -> colors.accentSoft
                NoteAiMessageRole.Assistant -> colors.elevated
                NoteAiMessageRole.Error -> colors.warningSoft
            },
            shape = VaultShapes.lg,
            border = BorderStroke(1.dp, if (isUser) colors.accentBorder else colors.border),
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                Text(
                    text = when (message.role) {
                        NoteAiMessageRole.User -> "You"
                        NoteAiMessageRole.Assistant -> "Ask AI"
                        NoteAiMessageRole.Error -> "Error"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                    color = if (message.role == NoteAiMessageRole.Error) colors.warning else colors.textMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = if (message.role == NoteAiMessageRole.Error) colors.warning else colors.text,
                )
            }
        }
    }
}

@Composable
private fun AskAiSuggestionGrid(
    enabled: Boolean,
    selectedTextMode: Boolean,
    onSuggestionClick: (AiSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AiSuggestion.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { suggestion ->
                    AskAiChip(
                        label = suggestion.displayName,
                        active = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSuggestionClick(suggestion) },
                    )
                }
            }
        }
        Text(
            text = if (selectedTextMode) {
                "Suggestions focus on the selected text."
            } else {
                "Suggestions prefill only. Edit the prompt, then press Send."
            },
            style = MaterialTheme.typography.labelSmall,
            color = VaultThemeTokens.colors.textMuted,
        )
    }
}

@Composable
private fun AskAiChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        color = if (active) colors.accentSoft else colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                color = if (active) colors.accent else colors.textSecondary,
            )
        }
    }
}

private fun NoteAiModel.chipLabel(provider: NoteAiProvider): String =
    when (this) {
        NoteAiModel.Gemini25Flash -> if (provider == NoteAiProvider.ChatGPT) "GPT Mini · Fast" else "Gemini 2.5 · Fast"
        NoteAiModel.Gemini3Pro -> if (provider == NoteAiProvider.ChatGPT) "GPT Full · Best" else "Gemini 3.1 · Deep"
    }
