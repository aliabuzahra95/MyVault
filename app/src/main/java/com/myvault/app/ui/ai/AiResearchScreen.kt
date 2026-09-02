package com.myvault.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myvault.app.data.ai.AiResearchProvider
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.AiResearchMessage
import com.myvault.app.ui.viewmodel.AiResearchMessageRole
import com.myvault.app.ui.viewmodel.AiResearchViewModel

@Composable
fun AiResearchScreen(
    onMenuClick: () -> Unit,
    viewModel: AiResearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultThemeTokens.colors.bg)
            .imePadding(),
    ) {
        AiResearchHeader(
            provider = state.selectedProvider,
            shamelaStatus = state.shamelaStatus,
            onMenuClick = onMenuClick,
            onProviderSelected = viewModel::selectProvider,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = VaultSpacing.screen,
                end = VaultSpacing.screen,
                top = 18.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    AiResearchEmptyState()
                }
            } else {
                items(state.messages, key = AiResearchMessage::id) { message ->
                    AiResearchMessageItem(message)
                }
            }
        }
        AiResearchComposer(
            value = state.composer,
            onValueChange = viewModel::updateComposer,
            onSend = viewModel::submitQuestion,
        )
    }
}

@Composable
private fun AiResearchHeader(
    provider: AiResearchProvider,
    shamelaStatus: String,
    onMenuClick: () -> Unit,
    onProviderSelected: (AiResearchProvider) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var providerMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Menu, "Open navigation", Modifier.size(20.dp), tint = colors.textSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text("AI", fontSize = 17.sp, fontWeight = FontWeight.W800, color = colors.text)
            Text("Shamela · $shamelaStatus", fontSize = 11.sp, color = colors.textMuted)
        }
        Box {
            Surface(
                onClick = { providerMenuOpen = true },
                color = colors.surface,
                shape = VaultShapes.sm,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(provider.label, fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = colors.text)
                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(17.dp), tint = colors.textMuted)
                }
            }
            DropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false }) {
                AiResearchProvider.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            providerMenuOpen = false
                            onProviderSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiResearchEmptyState() {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Research your library", fontSize = 18.sp, fontWeight = FontWeight.W700, color = colors.text)
        Text(
            "Ask a question, verify a quotation, or search verified Shamela sources.",
            modifier = Modifier.fillMaxWidth(0.82f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AiResearchMessageItem(message: AiResearchMessage) {
    val colors = VaultThemeTokens.colors
    if (message.role == AiResearchMessageRole.User) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                color = colors.bg,
                shape = VaultShapes.md,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
            }
        }
    } else {
        Text(
            text = message.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = colors.text,
        )
    }
}

@Composable
private fun AiResearchComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = VaultSpacing.screen, end = VaultSpacing.screen, bottom = 10.dp),
        color = colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp, max = 132.dp)
                    .padding(vertical = 7.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text, lineHeight = 20.sp),
                cursorBrush = SolidColor(colors.accent),
                minLines = 1,
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text("Ask AI or search Shamela", fontSize = 13.sp, color = colors.textMuted)
                        }
                        inner()
                    }
                },
            )
            Surface(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(38.dp),
                shape = VaultShapes.sm,
                color = if (value.isNotBlank()) colors.accent else colors.inset,
                contentColor = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, "Send", Modifier.size(19.dp))
                }
            }
        }
    }
}
