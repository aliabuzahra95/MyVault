package com.myvault.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.myvault.app.data.ai.AiResearchProvider
import com.myvault.app.data.ai.ShamelaConnectionState
import com.myvault.app.data.ai.ShamelaMcpConnectionState
import com.myvault.app.data.ai.ResearchSource
import com.myvault.app.data.ai.ResearchContextPage
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.AiResearchMessage
import com.myvault.app.ui.viewmodel.AiResearchMessageRole
import com.myvault.app.ui.viewmodel.AiResearchViewModel
import com.myvault.app.ui.viewmodel.AiResearchMode
import com.myvault.app.ui.viewmodel.SourceDetailState

@Composable
fun AiResearchScreen(
    onMenuClick: () -> Unit,
    viewModel: AiResearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.completeShamelaAuthorization(result.data)
    }
    val connectShamela = {
        viewModel.createShamelaAuthorizationIntent().onSuccess(authorizationLauncher::launch)
        Unit
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling) followLatest = !canScrollForward
            }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (followLatest && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }
    LaunchedEffect(state.shamelaConnection, state.shamelaMcpConnection) {
        if (
            state.shamelaConnection == ShamelaConnectionState.Connected &&
            state.shamelaMcpConnection == ShamelaMcpConnectionState.Idle
        ) {
            viewModel.discoverShamelaMcp()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultThemeTokens.colors.bg)
            .imePadding(),
    ) {
        AiResearchHeader(
            provider = state.selectedProvider,
            shamelaConnection = state.shamelaConnection,
            shamelaMcpConnection = state.shamelaMcpConnection,
            onMenuClick = onMenuClick,
            onProviderSelected = viewModel::selectProvider,
            onConnectShamela = connectShamela,
            onDisconnectShamela = viewModel::disconnectShamela,
            onRetryShamela = viewModel::discoverShamelaMcp,
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
                    AiResearchEmptyState(
                        shamelaConnection = state.shamelaConnection,
                        shamelaMcpConnection = state.shamelaMcpConnection,
                        onConnectShamela = connectShamela,
                        onRetryShamela = viewModel::discoverShamelaMcp,
                    )
                }
            } else {
                items(state.messages, key = AiResearchMessage::id) { message ->
                    AiResearchMessageItem(message, viewModel::openSource)
                }
            }
        }
        AiResearchComposer(
            value = state.composer,
            mode = state.selectedMode,
            onValueChange = viewModel::updateComposer,
            onModeSelected = viewModel::selectMode,
            onSend = viewModel::submitQuestion,
            enabled = !state.isBusy,
        )
    }
    state.sourceDetail?.let { detail ->
        SourceDetailSheet(
            state = detail,
            onDismiss = viewModel::closeSource,
            onRetry = { viewModel.openSource(detail.source) },
        )
    }
}

@Composable
private fun AiResearchHeader(
    provider: AiResearchProvider,
    shamelaConnection: ShamelaConnectionState,
    shamelaMcpConnection: ShamelaMcpConnectionState,
    onMenuClick: () -> Unit,
    onProviderSelected: (AiResearchProvider) -> Unit,
    onConnectShamela: () -> Unit,
    onDisconnectShamela: () -> Unit,
    onRetryShamela: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var providerMenuOpen by remember { mutableStateOf(false) }
    var shamelaMenuOpen by remember { mutableStateOf(false) }
    val shamelaStatus = when (shamelaConnection) {
        ShamelaConnectionState.Disconnected -> "Sign in"
        ShamelaConnectionState.Connecting -> "Connecting"
        ShamelaConnectionState.Connected -> when (shamelaMcpConnection) {
            ShamelaMcpConnectionState.Connecting -> "Connecting"
            is ShamelaMcpConnectionState.Error -> "Retry"
            ShamelaMcpConnectionState.Idle -> "Connecting"
            is ShamelaMcpConnectionState.Ready -> "Connected"
        }
        is ShamelaConnectionState.Error -> "Reconnect"
    }
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
            Box {
                Text(
                    "Shamela · $shamelaStatus",
                    modifier = Modifier.clickable {
                        when (shamelaConnection) {
                            ShamelaConnectionState.Connected -> {
                                if (shamelaMcpConnection is ShamelaMcpConnectionState.Error) onRetryShamela()
                                else shamelaMenuOpen = true
                            }
                            ShamelaConnectionState.Connecting -> Unit
                            else -> onConnectShamela()
                        }
                    },
                    fontSize = 11.sp,
                    color = if (shamelaMcpConnection is ShamelaMcpConnectionState.Ready) colors.success else colors.textMuted,
                )
                DropdownMenu(expanded = shamelaMenuOpen, onDismissRequest = { shamelaMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Disconnect Shamela") },
                        onClick = {
                            shamelaMenuOpen = false
                            onDisconnectShamela()
                        },
                    )
                }
            }
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
private fun AiResearchEmptyState(
    shamelaConnection: ShamelaConnectionState,
    shamelaMcpConnection: ShamelaMcpConnectionState,
    onConnectShamela: () -> Unit,
    onRetryShamela: () -> Unit,
) {
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
        when (shamelaConnection) {
            ShamelaConnectionState.Disconnected, is ShamelaConnectionState.Error -> {
                TextButton(onClick = onConnectShamela) { Text("Connect Shamela") }
            }
            ShamelaConnectionState.Connecting -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp).size(20.dp), strokeWidth = 2.dp)
            }
            ShamelaConnectionState.Connected -> when (shamelaMcpConnection) {
                ShamelaMcpConnectionState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp).size(20.dp), strokeWidth = 2.dp)
                }
                is ShamelaMcpConnectionState.Error -> {
                    TextButton(onClick = onRetryShamela) { Text("Retry Shamela") }
                    Text(
                        shamelaMcpConnection.message,
                        modifier = Modifier.fillMaxWidth(0.82f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = colors.warning,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                ShamelaMcpConnectionState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp).size(20.dp), strokeWidth = 2.dp)
                }
                is ShamelaMcpConnectionState.Ready -> Unit
            }
        }
        if (shamelaConnection is ShamelaConnectionState.Error) {
            Text(
                shamelaConnection.message,
                modifier = Modifier.fillMaxWidth(0.82f),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = colors.warning,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AiResearchMessageItem(message: AiResearchMessage, onOpenSource: (ResearchSource) -> Unit) {
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
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = message.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = if (message.isError) colors.warning else colors.text,
                )
            }
            message.sources.forEach { source ->
                ShamelaSourceCard(source, onOpenSource)
            }
        }
    }
}

@Composable
private fun ShamelaSourceCard(source: ResearchSource, onOpenSource: (ResearchSource) -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Rounded.Book, null, Modifier.size(18.dp), tint = colors.textSecondary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        source.bookTitle,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.W700,
                        color = colors.text,
                    )
                    source.authorName?.let {
                        Text(it, fontSize = 11.5.sp, lineHeight = 16.sp, color = colors.textSecondary)
                    }
                }
            }
            Text(
                source.arabicPassage,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = colors.text,
                textAlign = TextAlign.End,
            )
            val location = listOfNotNull(
                source.part?.let { "Part $it" },
                source.printedPage?.let { "Page $it" },
            ).joinToString(" · ")
            Text(
                listOf(source.provenanceType.label, location).filter(String::isNotBlank).joinToString(" · "),
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = colors.textMuted,
            )
            TextButton(
                onClick = { onOpenSource(source) },
                modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp),
            ) {
                Text("Open source")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourceDetailSheet(
    state: SourceDetailState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shamela source", fontSize = 17.sp, fontWeight = FontWeight.W800, color = colors.text)
                    Text(
                        state.source.bookTitle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                }
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    color = Color.Transparent,
                    shape = VaultShapes.sm,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Close source", Modifier.size(20.dp), tint = colors.textSecondary)
                    }
                }
            }
            HorizontalDivider(color = colors.border)
            when (state) {
                is SourceDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                is SourceDetailState.Error -> Column(
                    modifier = Modifier.fillMaxWidth().padding(VaultSpacing.screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, color = colors.warning, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                is SourceDetailState.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = VaultSpacing.screen,
                        end = VaultSpacing.screen,
                        top = 16.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.source.authorName?.let {
                                Text(it, fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
                            }
                            Text(
                                state.source.provenanceType.label,
                                fontSize = 11.sp,
                                color = colors.textMuted,
                            )
                            state.context.citationText?.let {
                                Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = colors.textSecondary)
                            }
                        }
                    }
                    items(state.context.pages, key = ResearchContextPage::pageId) { page ->
                        SourceContextPage(page)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceContextPage(page: ResearchContextPage) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        val location = listOfNotNull(
            page.part?.let { "Part $it" },
            page.printedPage?.let { "Page $it" },
        ).joinToString(" · ")
        Text(
            if (page.isCurrent) listOf("Selected passage", location).filter(String::isNotBlank).joinToString(" · ")
            else listOf("Surrounding context", location).filter(String::isNotBlank).joinToString(" · "),
            fontSize = 11.sp,
            fontWeight = if (page.isCurrent) FontWeight.W700 else FontWeight.W600,
            color = if (page.isCurrent) colors.accent else colors.textMuted,
        )
        if (page.body.isNotBlank()) {
            Text(
                page.body,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 29.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = colors.text,
                textAlign = TextAlign.End,
            )
        }
        if (page.footnote.isNotBlank()) {
            Text("Footnotes", fontSize = 11.sp, fontWeight = FontWeight.W700, color = colors.textSecondary)
            Text(
                page.footnote,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp, textDirection = TextDirection.Rtl),
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
        if (page.comment.isNotBlank()) {
            Text("Commentary", fontSize = 11.sp, fontWeight = FontWeight.W700, color = colors.textSecondary)
            Text(
                page.comment,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp, textDirection = TextDirection.Rtl),
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AiResearchComposer(
    value: String,
    mode: AiResearchMode,
    onValueChange: (String) -> Unit,
    onModeSelected: (AiResearchMode) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val colors = VaultThemeTokens.colors
    var modeMenuOpen by remember { mutableStateOf(false) }
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
            Box {
                TextButton(
                    onClick = { modeMenuOpen = true },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(mode.label, fontSize = 11.5.sp, fontWeight = FontWeight.W700)
                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp))
                }
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    AiResearchMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                modeMenuOpen = false
                                onModeSelected(option)
                            },
                        )
                    }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
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
                            Text(mode.composerHint, fontSize = 13.sp, color = colors.textMuted)
                        }
                        inner()
                    }
                },
            )
            Surface(
                onClick = onSend,
                enabled = value.isNotBlank() && enabled,
                modifier = Modifier.size(38.dp),
                shape = VaultShapes.sm,
                color = if (value.isNotBlank() && enabled) colors.accent else colors.inset,
                contentColor = if (value.isNotBlank() && enabled) MaterialTheme.colorScheme.onPrimary else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, "Send", Modifier.size(19.dp))
                }
            }
        }
    }
}
