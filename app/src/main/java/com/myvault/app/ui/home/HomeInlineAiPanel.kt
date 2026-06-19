package com.myvault.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import com.myvault.app.ai.home.HomeAiAttachableItem
import com.myvault.app.ai.home.HomeAiModelMode
import com.myvault.app.ai.home.HomeAiPanelMode
import com.myvault.app.ai.home.HomeAiProvider
import com.myvault.app.ai.home.HomeAiProviderStatus
import com.myvault.app.ai.home.HomeInlineAiHistoryItem
import com.myvault.app.ai.home.HomeInlineAiMessage
import com.myvault.app.ai.home.HomeInlineAiRole
import com.myvault.app.ai.home.HomeInlineAiState
import com.myvault.app.ui.components.SettingsRow
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay

@Composable
fun HomeInlineAiPanel(
    state: HomeInlineAiState,
    onInputChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSuggestionClick: (HomeAiAttachableItem) -> Unit,
    onDetachClick: (HomeAiAttachableItem) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onProviderSelected: (HomeAiProvider) -> Unit,
    onModelModeSelected: (HomeAiModelMode) -> Unit,
    onSettingsClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onDismissErrorClick: () -> Unit,
    onClose: () -> Unit,
    onPickerToggle: (HomeAiAttachableItem) -> Unit,
    onPickerClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val conversationScrollState = rememberScrollState()
    val aiBusy = state.isStreaming
    val trayOpen = state.panelMode == HomeAiPanelMode.AttachNotes
    val streamScrollBucket = state.currentStreamingAnswer.length / 320

    val closePanel = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onClose()
    }

    BackHandler(enabled = state.isPanelOpen) {
        if (state.panelMode == HomeAiPanelMode.Chat) closePanel() else onPickerClose()
    }

    LaunchedEffect(state.isPanelOpen) {
        if (state.isPanelOpen) {
            delay(360)
            runCatching {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(state.chatMessages.size, state.isStreaming, streamScrollBucket, state.error) {
        conversationScrollState.animateScrollTo(conversationScrollState.maxValue)
    }

    if (!state.isPanelOpen) return

    Dialog(
        onDismissRequest = closePanel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
        ),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it },
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                targetOffsetY = { it },
            ) + fadeOut(),
            modifier = modifier.fillMaxSize(),
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.bg,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                    if (state.attachedItems.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = colors.elevated,
                            shape = VaultShapes.sm,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            LazyRow(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(state.attachedItems, key = { "${it.type}:${it.id}" }) { item ->
                                    VaultAiChip(
                                        title = item.title,
                                        type = null,
                                        onRemove = { onDetachClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = trayOpen,
                        enter = expandVertically(
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                            expandFrom = Alignment.Bottom,
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                            shrinkTowards = Alignment.Bottom,
                        ) + fadeOut(),
                    ) {
                        HomeAiAttachmentPicker(
                            items = state.pickerItems,
                            selectedItems = state.attachedItems,
                            onToggle = onPickerToggle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(216.dp)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    HomeInlineAiBar(
                        input = state.chatInputText,
                        suggestions = state.suggestedTitles,
                        isStreaming = state.isStreaming,
                        attachmentTrayOpen = trayOpen,
                        focusRequester = focusRequester,
                        onInputChange = onInputChange,
                        onAttachClick = {
                            if (trayOpen) onPickerClose() else onAttachClick()
                        },
                        onSuggestionClick = onSuggestionClick,
                        onSendClick = onSendClick,
                        onStopClick = onStopClick,
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = closePanel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = colors.text)
                    }
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ask AI", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800), color = colors.text)
                        Text(
                            text = when {
                                state.attachedItems.isNotEmpty() -> state.attachedItems.first().title
                                else -> "Home"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI settings", tint = colors.textSecondary)
                    }
                    if (state.chatMessages.isNotEmpty()) {
                        TextButton(onClick = onClearHistoryClick, enabled = !aiBusy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("New", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (state.panelMode == HomeAiPanelMode.Settings) {
                    HomeInlineAiSettingsContent(
                        selectedProvider = state.selectedProvider,
                        selectedModelMode = state.selectedModelMode,
                        resolvedModelId = state.resolvedModelId,
                        providerStatuses = state.providerStatuses,
                        maskedKeyStatus = state.maskedKeyStatus,
                        historyItems = state.historyItems,
                        onProviderSelected = onProviderSelected,
                        onModelModeSelected = onModelModeSelected,
                        onClearHistoryClick = onClearHistoryClick,
                        onHistoryClick = onHistoryClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    return@Column
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeAiProvider.entries.forEach { provider ->
                        CompactHomeAiChip(
                            label = provider.label,
                            active = state.selectedProvider == provider,
                            enabled = !aiBusy,
                        ) { onProviderSelected(provider) }
                    }
                    Box(modifier = Modifier.height(14.dp).width(1.dp).background(colors.borderStrong))
                    HomeAiModelMode.entries.forEach { mode ->
                        CompactHomeAiChip(
                            label = mode.label,
                            active = state.selectedModelMode == mode,
                            enabled = !aiBusy,
                        ) { onModelModeSelected(mode) }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(conversationScrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.chatMessages.isEmpty() && !aiBusy && state.error == null) {
                        Text(
                            text = "Ask naturally. Use the prompt box below for custom questions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                    state.warning?.let { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                            color = colors.warning,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    state.chatMessages.forEach { message ->
                        HomeAskAiChatBubble(message = message)
                    }
                    if (state.isStreaming && state.currentStreamingAnswer.isNotBlank()) {
                        HomeAskAiStreamingBubble(content = state.currentStreamingAnswer)
                    }
                    if (aiBusy && state.currentStreamingAnswer.isBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
                            Text("Thinking...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                    state.error?.let { error ->
                        Text(
                            text = error.userMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                            color = colors.warning,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }
        }
    }
}


@Composable
private fun HomeAskAiStreamingBubble(content: String) {
    val colors = VaultThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(color = Color.Transparent) {
            Column(modifier = Modifier.padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = colors.accent)
                    Text("My AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800), color = colors.text)
                }
                Spacer(modifier = Modifier.height(6.dp))
                RichMarkdownText(
                    text = content,
                    color = colors.text,
                )
            }
        }
    }
}

@Composable
private fun HomeAskAiChatBubble(message: HomeInlineAiMessage) {
    val colors = VaultThemeTokens.colors
    val isUser = message.role == HomeInlineAiRole.User
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
                        Text("My AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800), color = colors.text)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                RichMarkdownText(
                    text = message.text,
                    color = if (message.role == HomeInlineAiRole.Error) colors.warning else colors.text,
                )
            }
        }
    }
}

@Composable
private fun CompactHomeAiChip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        color = if (active) colors.accentSoft else colors.elevated,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (active) colors.accentBorder else colors.border),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.W800 else FontWeight.W600),
                color = if (active) colors.accent else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PanelDragHandle(
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val dismissThreshold = 92.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag = (totalDrag + dragAmount).coerceAtLeast(0f)
                        onDragOffsetChange(totalDrag)
                    },
                    onDragEnd = {
                        if (totalDrag > dismissThreshold.toPx()) {
                            onClose()
                        } else {
                            onDragEnd()
                        }
                    },
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 4.dp)
                .background(colors.border.copy(alpha = 0.78f), VaultShapes.pill),
        )
    }
}

@Composable
private fun HomeInlineAiHeader(
    mode: HomeAiPanelMode,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    onClose: () -> Unit,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val dismissThreshold = 100.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag = (totalDrag + dragAmount).coerceAtLeast(0f)
                        onDragOffsetChange(totalDrag)
                    },
                    onDragEnd = {
                        if (totalDrag > dismissThreshold.toPx()) {
                            onClose()
                        } else {
                            onDragEnd()
                        }
                    },
                    onDragCancel = onDragEnd,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == HomeAiPanelMode.Settings) {
            VaultAiIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to chat",
                onClick = onBackClick,
                size = 48.dp,
                iconSize = 24.dp,
                shape = VaultShapes.pill,
            )
        }

        Text(
            text = if (mode == HomeAiPanelMode.Settings) "AI Settings" else "Ask AI",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900),
            color = colors.text,
            maxLines = 1,
        )

        Surface(
            modifier = Modifier.size(48.dp),
            color = colors.accentSoft,
            shape = VaultShapes.pill,
            border = BorderStroke(1.dp, colors.accentBorder),
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Ask AI",
                    modifier = Modifier.size(24.dp),
                    tint = colors.accent,
                )
            }
        }

        if (mode != HomeAiPanelMode.Settings) {
            VaultAiIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "AI settings",
                onClick = onSettingsClick,
                size = 48.dp,
                iconSize = 24.dp,
                shape = VaultShapes.pill,
            )
        }

        VaultAiIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Close AI screen",
            onClick = onClose,
            size = 48.dp,
            iconSize = 24.dp,
            shape = VaultShapes.pill,
        )
    }
}


@Composable
private fun HomeInlineAiChatContent(
    state: HomeInlineAiState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSuggestionClick: (HomeAiAttachableItem) -> Unit,
    onDetachClick: (HomeAiAttachableItem) -> Unit,
    onPickerToggle: (HomeAiAttachableItem) -> Unit,
    onPickerClose: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissErrorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trayOpen = state.panelMode == HomeAiPanelMode.AttachNotes
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.warning != null) {
            Banner(text = state.warning, warning = true)
        }
        if (state.error != null) {
            ErrorCard(
                message = state.error.userMessage,
                onRetry = onRetryClick,
                onDismiss = onDismissErrorClick,
            )
        }

        ConversationList(
            messages = state.chatMessages,
            isStreaming = state.isStreaming,
            currentStreamingAnswer = state.currentStreamingAnswer,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
        )

        HomeInlineAiAttachedChips(
            attachedItems = state.attachedItems,
            onDetachClick = onDetachClick,
        )

        AnimatedVisibility(
            visible = trayOpen,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(),
        ) {
            HomeAiAttachmentPicker(
                items = state.pickerItems,
                selectedItems = state.attachedItems,
                onToggle = onPickerToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp),
            )
        }

        HomeInlineAiBar(
            input = state.chatInputText,
            suggestions = state.suggestedTitles,
            isStreaming = state.isStreaming,
            attachmentTrayOpen = trayOpen,
            focusRequester = focusRequester,
            onInputChange = onInputChange,
            onAttachClick = {
                if (trayOpen) onPickerClose() else onAttachClick()
            },
            onSuggestionClick = onSuggestionClick,
            onSendClick = onSendClick,
            onStopClick = onStopClick,
        )
    }
}

@Composable
private fun HomeInlineAiAttachedChips(
    attachedItems: List<HomeAiAttachableItem>,
    onDetachClick: (HomeAiAttachableItem) -> Unit,
) {
    if (attachedItems.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(attachedItems, key = { "${it.type}:${it.id}" }) { item ->
            VaultAiChip(
                title = item.title,
                type = null,
                onRemove = { onDetachClick(item) },
            )
        }
    }
}

@Composable
private fun HomeInlineAiSettingsContent(
    selectedProvider: HomeAiProvider,
    selectedModelMode: HomeAiModelMode,
    resolvedModelId: String,
    providerStatuses: List<HomeAiProviderStatus>,
    maskedKeyStatus: String,
    historyItems: List<HomeInlineAiHistoryItem>,
    onProviderSelected: (HomeAiProvider) -> Unit,
    onModelModeSelected: (HomeAiModelMode) -> Unit,
    onClearHistoryClick: () -> Unit,
    onHistoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var providerDialogOpen by remember { mutableStateOf(false) }
    var modelDialogOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = "AI SETTINGS",
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        SettingsRow(
            icon = Icons.Rounded.AutoAwesome,
            label = "Provider",
            value = selectedProvider.label,
            onClick = { providerDialogOpen = true },
        )
        SettingsRow(
            icon = Icons.Rounded.Settings,
            label = "Model",
            value = selectedModelMode.label,
            onClick = { modelDialogOpen = true },
        )
        SettingsRow(
            icon = Icons.Rounded.Lock,
            label = "Key status",
            value = maskedKeyStatus.substringAfter(": ", maskedKeyStatus).ifBlank { "Not configured" },
        )
        SettingsRow(
            icon = Icons.Rounded.Add,
            label = "New conversation",
            value = if (confirmClear) "Tap again to clear screen" else "Keeps saved history",
            onClick = {
                if (confirmClear) {
                    onClearHistoryClick()
                    confirmClear = false
                } else {
                    confirmClear = true
                }
            },
        )
        if (resolvedModelId.isNotBlank()) {
            Text(
                text = "Model: $resolvedModelId",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "CONVERSATION HISTORY",
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        if (historyItems.isEmpty()) {
            Text(
                text = "No saved Home AI conversations yet.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = colors.textMuted,
            )
        } else {
            historyItems.forEach { item ->
                HomeAiHistoryCard(item = item, onClick = { onHistoryClick(item.id) })
            }
        }
    }

    if (providerDialogOpen) {
        HomeAiSettingsChoiceDialog(
            title = "Provider",
            options = HomeAiProvider.entries.map { provider ->
                val status = providerStatuses.firstOrNull { it.provider == provider }
                val label = if (status?.selectable == true) provider.label else "${provider.label} (${status?.statusLabel ?: "not configured"})"
                HomeAiSettingsChoice(provider.name, label, enabled = status?.selectable == true)
            },
            selectedValue = selectedProvider.name,
            onDismiss = { providerDialogOpen = false },
            onSelect = { value ->
                HomeAiProvider.entries.firstOrNull { it.name == value }?.let(onProviderSelected)
                providerDialogOpen = false
            },
        )
    }

    if (modelDialogOpen) {
        HomeAiSettingsChoiceDialog(
            title = "Model",
            options = HomeAiModelMode.entries.map { mode -> HomeAiSettingsChoice(mode.name, mode.label) },
            selectedValue = selectedModelMode.name,
            onDismiss = { modelDialogOpen = false },
            onSelect = { value ->
                HomeAiModelMode.entries.firstOrNull { it.name == value }?.let(onModelModeSelected)
                modelDialogOpen = false
            },
        )
    }
}


@Composable
private fun HomeAiHistoryCard(
    item: HomeInlineAiHistoryItem,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.title.ifBlank { "Saved conversation" },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800, lineHeight = 18.sp),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.assistantPreview.isNotBlank()) {
                Text(
                    text = item.assistantPreview,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.attachedTitles.isNotEmpty()) {
                Text(
                    text = item.attachedTitles.take(2).joinToString(", ") + if (item.attachedTitles.size > 2) " +${item.attachedTitles.size - 2}" else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700, lineHeight = 15.sp),
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class HomeAiSettingsChoice(
    val value: String,
    val label: String,
    val enabled: Boolean = true,
)

@Composable
private fun HomeAiSettingsChoiceDialog(
    title: String,
    options: List<HomeAiSettingsChoice>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                options.forEach { option ->
                    SettingsRow(
                        icon = if (option.value == selectedValue) Icons.Rounded.Palette else Icons.Rounded.Visibility,
                        label = option.label,
                        value = if (option.value == selectedValue) "Selected" else "",
                        onClick = {
                            if (option.enabled) onSelect(option.value)
                        },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, VaultShapes.lg, clip = false),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.W800,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp,
                ),
                color = colors.textMuted,
            )
            content()
        }
    }
}

@Composable
private fun ProviderRow(
    provider: HomeAiProvider,
    selected: Boolean,
    status: HomeAiProviderStatus?,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val selectable = status?.selectable == true
    Surface(
        onClick = onClick,
        enabled = selectable,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) colors.accentSoft else colors.inset,
        shape = VaultShapes.md,
        border = null,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .background(if (selected) colors.accent else Color.Transparent, VaultShapes.pill),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W900),
                    color = if (selected) colors.accent else colors.text,
                    maxLines = 1,
                )
                Text(
                    text = status?.statusLabel ?: "not configured",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    modifier = Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W900),
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun SettingsActionChip(
    text: String,
    warning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (warning) colors.warningSoft else colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, if (warning) colors.warning.copy(alpha = 0.35f) else colors.border),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
            color = if (warning) colors.warning else colors.text,
        )
    }
}

@Composable
private fun ModelModeToggle(
    selectedModelMode: HomeAiModelMode,
    onModelModeSelected: (HomeAiModelMode) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.lg,
        border = null,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeAiModelMode.entries.forEach { mode ->
                val selected = mode == selectedModelMode
                Surface(
                    onClick = { onModelModeSelected(mode) },
                    modifier = Modifier.weight(1f),
                    color = if (selected) colors.surface else Color.Transparent,
                    shape = VaultShapes.md,
                    border = null,
                    tonalElevation = 0.dp,
                    shadowElevation = if (selected) 2.dp else 0.dp,
                ) {
                    Text(
                        text = mode.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.W800 else FontWeight.W600,
                            textAlign = TextAlign.Center,
                        ),
                        color = if (selected) colors.text else colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    messages: List<HomeInlineAiMessage>,
    isStreaming: Boolean,
    currentStreamingAnswer: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isStreaming) {
        val target = messages.size + if (isStreaming) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messages.isEmpty() && !isStreaming) {
            item(key = "empty") {
                Surface(
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Ask AI...",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = colors.textMuted,
                    )
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }
        if (isStreaming) {
            item(key = "streaming") {
                MessageBubble(
                    message = HomeInlineAiMessage(
                        id = "streaming",
                        role = HomeInlineAiRole.Assistant,
                        text = currentStreamingAnswer.ifBlank { "Connecting..." },
                    ),
                    streaming = true,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.warningSoft,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.36f)),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700, lineHeight = 18.sp),
                    color = colors.warning,
                )
                VaultAiIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Dismiss error",
                    onClick = onDismiss,
                    size = 28.dp,
                    iconSize = 14.dp,
                    warning = true,
                    shape = VaultShapes.pill,
                )
            }
            SettingsActionChip(
                text = "Retry",
                warning = false,
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: HomeInlineAiMessage, streaming: Boolean = false) {
    val colors = VaultThemeTokens.colors
    val isUser = message.role == HomeInlineAiRole.User
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isUser) 34.dp else 0.dp, end = if (isUser) 0.dp else 14.dp),
        color = when (message.role) {
            HomeInlineAiRole.User -> colors.accentSoft
            HomeInlineAiRole.Assistant -> colors.surface
            HomeInlineAiRole.Error -> colors.warningSoft
        },
        shape = VaultShapes.md,
        border = BorderStroke(
            1.dp,
            when (message.role) {
                HomeInlineAiRole.User -> colors.accentBorder.copy(alpha = 0.75f)
                HomeInlineAiRole.Assistant -> colors.border.copy(alpha = 0.62f)
                HomeInlineAiRole.Error -> colors.warning.copy(alpha = 0.5f)
            },
        ),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                if (streaming) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = colors.accent)
                }
                RichMarkdownText(
                    text = message.text,
                    color = when (message.role) {
                        HomeInlineAiRole.User -> colors.text
                        HomeInlineAiRole.Assistant -> colors.text
                        HomeInlineAiRole.Error -> colors.warning
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RichMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(block)
                is MarkdownBlock.Paragraph -> MarkdownParagraph(block.text, color)
                is MarkdownBlock.Bullet -> MarkdownBullet(block.text, color)
                is MarkdownBlock.ArgumentCard -> MarkdownArgumentCard(block)
            }
        }
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading) {
    val colors = VaultThemeTokens.colors
    Text(
        text = inlineMarkdown(block.text),
        style = when (block.level) {
            1 -> MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.W700, lineHeight = 22.sp)
            2 -> MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.W700, lineHeight = 22.sp)
            else -> MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.W600, lineHeight = 16.sp)
        },
        color = if (block.level <= 2) colors.text else colors.accent,
    )
}

@Composable
private fun MarkdownParagraph(text: String, color: Color) {
    Text(
        text = inlineMarkdown(text),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 22.sp),
        color = color,
    )
}

@Composable
private fun MarkdownBullet(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W800, lineHeight = 22.sp),
            color = VaultThemeTokens.colors.accent,
        )
        Text(
            text = inlineMarkdown(text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 22.sp),
            color = color,
        )
    }
}

@Composable
private fun MarkdownArgumentCard(block: MarkdownBlock.ArgumentCard) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.elevated,
        shape = RoundedCornerShape(12.dp),
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = inlineMarkdown("${block.number}. ${block.title}"),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.W700, lineHeight = 22.sp),
                color = colors.text,
            )
            block.children.forEach { child ->
                when (child) {
                    is MarkdownBlock.Heading -> MarkdownHeading(child)
                    is MarkdownBlock.Paragraph -> MarkdownParagraph(child.text, colors.textSecondary)
                    is MarkdownBlock.Bullet -> MarkdownBullet(child.text, colors.textSecondary)
                    is MarkdownBlock.ArgumentCard -> MarkdownArgumentCard(child)
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class ArgumentCard(val number: Int, val title: String, val children: List<MarkdownBlock>) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var paragraph = mutableListOf<String>()
    var activeCardNumber: Int? = null
    var activeCardTitle = ""
    var activeCardChildren = mutableListOf<MarkdownBlock>()

    fun flushParagraph(target: MutableList<MarkdownBlock>) {
        if (paragraph.isNotEmpty()) {
            target += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph = mutableListOf()
        }
    }

    fun currentTarget(): MutableList<MarkdownBlock> = if (activeCardNumber != null) activeCardChildren else blocks

    fun flushCard() {
        val number = activeCardNumber ?: return
        flushParagraph(activeCardChildren)
        blocks += MarkdownBlock.ArgumentCard(number, activeCardTitle, activeCardChildren.toList())
        activeCardNumber = null
        activeCardTitle = ""
        activeCardChildren = mutableListOf()
    }

    lines.forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) {
            flushParagraph(currentTarget())
            return@forEach
        }

        val numbered = Regex("^(\\d+)\\.\\s+(.+)$").find(line)
        if (numbered != null) {
            flushCard()
            activeCardNumber = numbered.groupValues[1].toIntOrNull() ?: 1
            activeCardTitle = cleanMarkdownLine(numbered.groupValues[2])
            activeCardChildren = mutableListOf()
            return@forEach
        }

        val headerLevel = markdownHeaderLevel(line)
        if (headerLevel != null) {
            flushParagraph(currentTarget())
            if (activeCardNumber != null && headerLevel <= 2) flushCard()
            val cleanLine = line.trimStart().drop(headerLevel).trimStart()
            currentTarget() += MarkdownBlock.Heading(headerLevel, cleanMarkdownLine(cleanLine))
            return@forEach
        }

        val bullet = Regex("^([*\\-•])\\s+(.+)$").find(line)
        if (bullet != null) {
            flushParagraph(currentTarget())
            currentTarget() += MarkdownBlock.Bullet(cleanMarkdownLine(bullet.groupValues[2]))
            return@forEach
        }

        paragraph += cleanMarkdownLine(line)
    }

    flushCard()
    flushParagraph(blocks)
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(text.trim())) }
}


private fun markdownHeaderLevel(line: String): Int? {
    val trimmed = line.trimStart()
    val hashes = trimmed.takeWhile { it == '#' }.length
    if (hashes !in 1..3) return null
    return hashes.takeIf { trimmed.getOrNull(it) == ' ' }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    var bold = false
    while (index < text.length) {
        if (index + 1 < text.length && text[index] == '*' && text[index + 1] == '*') {
            bold = !bold
            index += 2
        } else {
            val next = text.indexOf("**", startIndex = index).let { if (it == -1) text.length else it }
            val segment = text.substring(index, next)
            if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.W900)) { append(segment) }
            } else {
                append(segment)
            }
            index = next
        }
    }
}

private fun cleanMarkdownLine(text: String): String = text
    .trim()
    .removePrefix("- ")
    .removePrefix("* ")
    .removePrefix("• ")

@Composable
private fun Banner(text: String, warning: Boolean) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (warning) colors.warningSoft else colors.accentSoft,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, if (warning) colors.warning.copy(alpha = 0.35f) else colors.accentBorder),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
            color = if (warning) colors.warning else colors.text,
        )
    }
}
