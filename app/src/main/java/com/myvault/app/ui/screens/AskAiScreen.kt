package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    onCancelAiGeneration: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val clipboardManager = LocalClipboardManager.current
    val conversationScrollState = rememberScrollState()
    var historyOpen by remember { mutableStateOf(false) }
    val noteTitle = uiState.note?.title?.takeIf { it.isNotBlank() } ?: "Untitled note"
    val noteBody = uiState.richText.text.ifBlank { uiState.note?.bodyPlainText.orEmpty() }
    val hasSelectedText = !selectedText.isNullOrBlank()
    val aiBusy = aiState.loading || aiState.isStreaming
    val streamScrollBucket = aiState.streamedText.length / 320

    LaunchedEffect(aiState.messages.size, aiBusy, aiState.isStreaming, streamScrollBucket, aiState.error) {
        conversationScrollState.animateScrollTo(conversationScrollState.maxValue)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                    Surface(
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen, vertical = 4.dp),
                        color = colors.elevated,
                        shape = VaultShapes.sm,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FormatQuote, null, modifier = Modifier.size(12.dp), tint = colors.textMuted)
                            Spacer(Modifier.width(6.dp))
                            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
                    color = colors.elevated,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(aiState.messages.lastOrNull { it.role == NoteAiMessageRole.Assistant }?.content.orEmpty())) },
                            enabled = aiState.messages.any { it.role == NoteAiMessageRole.Assistant },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy, 
                                null, 
                                modifier = Modifier.size(16.dp), 
                                tint = if (aiState.messages.any { it.role == NoteAiMessageRole.Assistant }) colors.textSecondary else colors.border
                            )
                        }

                        BasicTextField(
                            value = aiState.question,
                            onValueChange = onAiQuestionChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp, max = 120.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { innerTextField ->
                                if (aiState.question.isBlank()) {
                                    Text("Ask about this note...", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                                }
                                innerTextField()
                            }
                        )

                        if (aiBusy) {
                            Surface(
                                onClick = onCancelAiGeneration,
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(16.dp), tint = colors.text)
                                }
                            }
                        } else if (aiState.canContinue) {
                            Surface(
                                onClick = {
                                    onRunAiTool(
                                        NoteAiAction.Ask,
                                        aiState.provider,
                                        aiState.model,
                                        noteTitle,
                                        noteBody,
                                        "Continue from where you stopped. Do not restart the answer. Continue naturally and avoid repeating what you already said.",
                                    )
                                },
                                modifier = Modifier.height(36.dp),
                                shape = VaultShapes.pill,
                                color = colors.accent,
                                contentColor = Color.White
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Text("Continue", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800))
                                }
                            }
                        } else {
                            Surface(
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
                                enabled = aiState.question.isNotBlank(),
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = if (aiState.question.isNotBlank()) colors.accent else colors.surface,
                                contentColor = if (aiState.question.isNotBlank()) Color.White else colors.textMuted
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ArrowUpward, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
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
            // Sleek Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.text)
                }
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask AI", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800), color = colors.text)
                    Text(noteTitle, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { historyOpen = true }) {
                    Icon(Icons.Rounded.Menu, "History", tint = colors.textSecondary)
                }
                if (aiState.messages.isNotEmpty()) {
                    TextButton(onClick = onClearAiConversation, enabled = !aiBusy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Compact Selectors Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = VaultSpacing.screen, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoteAiProvider.entries.forEach { provider ->
                    CompactChip(label = provider.displayName, active = aiState.provider == provider, enabled = !aiBusy) { onAiProviderSelected(provider) }
                }
                Box(modifier = Modifier.height(14.dp).width(1.dp).background(colors.borderStrong))
                NoteAiModel.entries.forEach { model ->
                    CompactChip(label = model.chipLabel(aiState.provider), active = aiState.model == model, enabled = !aiBusy) { onAiModelSelected(model) }
                }
                Box(modifier = Modifier.height(14.dp).width(1.dp).background(colors.borderStrong))
                val visibleSuggestions = remember { listOf(AiSuggestion.Explain, AiSuggestion.Simplify) }
                visibleSuggestions.forEach { suggestion ->
                    CompactActionChip(label = suggestion.displayName, enabled = !aiBusy) {
                        onAiQuestionChange(AiPromptBuilder.buildSuggestionPrefill(suggestion, hasSelectedText))
                    }
                }
            }

            // Main Conversation Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(conversationScrollState)
                    .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.md),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (aiState.messages.isEmpty() && !aiBusy) {
                    Text(
                        text = "Ask naturally about this note. Use the prompt box below for custom questions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                aiState.messages.forEach { message ->
                    AskAiChatBubble(message = message)
                }
                if (aiState.isStreaming && aiState.streamedText.isNotBlank()) {
                    AskAiStreamingBubble(content = aiState.streamedText)
                }
                if (aiBusy && aiState.streamedText.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
                        Text(aiState.progressLabel ?: "Thinking...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                aiState.error?.takeIf { error ->
                    aiState.messages.lastOrNull()?.content != error
                }?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                        color = colors.warning,
                        modifier = Modifier.padding(4.dp)
                    )
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
                        enabled = !aiBusy,
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
private fun AskAiStreamingBubble(content: String) {
    val colors = VaultThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(color = Color.Transparent) {
            Column(modifier = Modifier.padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = colors.accent)
                    Text("My AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800), color = colors.text)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = colors.text,
                )
            }
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
            modifier = Modifier.widthIn(max = if (isUser) 280.dp else 600.dp),
            color = if (isUser) colors.accentSoft else Color.Transparent,
            shape = VaultShapes.lg,
            border = if (isUser) BorderStroke(1.dp, colors.accentBorder) else null,
        ) {
            Column(modifier = Modifier.padding(if (isUser) 12.dp else 4.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = colors.accent)
                        Text(message.action?.displayName ?: "My AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800), color = colors.text)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = if (message.role == NoteAiMessageRole.Error) colors.warning else colors.text,
                )
            }
        }
    }
}

@Composable
private fun CompactChip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        color = if (active) colors.accentSoft else colors.elevated,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.W800 else FontWeight.W600), color = if (active) colors.accent else colors.textSecondary)
        }
    }
}

@Composable
private fun CompactActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        color = colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600), color = colors.textSecondary)
        }
    }
}

private fun NoteAiModel.chipLabel(provider: NoteAiProvider): String =
    when (this) {
        NoteAiModel.Gemini25Flash -> if (provider == NoteAiProvider.ChatGPT) "GPT Mini" else "Gemini Flash"
        NoteAiModel.Gemini25Pro -> if (provider == NoteAiProvider.ChatGPT) "GPT Full" else "Gemini Pro"
    }
